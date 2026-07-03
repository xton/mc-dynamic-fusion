'use strict';
// End-to-end tests for DynamicFusion, driven by a real Mineflayer bot.
//
// These cover the *input path* the in-process `/fusion test` can't: a real held
// fused item plus a real client event (arm-swing, bow draw/release) flowing
// through our Bukkit listeners. We assert on block changes — the most reliable
// thing a bot observes — by pointing a MINING weapon at a dirt wall and checking
// the wall gets carved.
//
// node-minecraft-protocol only speaks a curated set of protocol versions (up to
// 1.21.x), not the server's newer build — so the bot connects as 1.21.x and
// ViaBackwards on the server bridges it up (an older client joining a newer
// server; ViaVersion alone only does the reverse). Env: MC_HOST, MC_PORT,
// MC_BOT_USER, MC_BOT_VERSION.

const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');

const HOST = process.env.MC_HOST || 'localhost';
const PORT = parseInt(process.env.MC_PORT || '25565', 10);
const USER = process.env.MC_BOT_USER || 'FusionBot';
// The newest version node-minecraft-protocol speaks (protocol ~773) — as close
// to the server (775) as we can get, so ViaVersion has the smallest gap to
// bridge and the client stays inside Via's supported range. Override with
// MC_BOT_VERSION if needed.
const VERSION = process.env.MC_BOT_VERSION || '1.21.11';
const WALL_DZ = -2; // wall is 2 blocks north — within a melee swing's arm's-length reach

const results = [];
const recentChat = []; // last few server/system messages, for diagnostics
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// Poll the bot's inventory until an item matches or we time out.
function waitForItem(bot, matcher, timeoutMs = 6000) {
  return waitForCondition(() => bot.inventory.items().find(matcher) || null, timeoutMs);
}

// Poll fn() every 200ms until it returns truthy or we time out.
function waitForCondition(fn, timeoutMs = 5000) {
  return new Promise((resolve) => {
    const start = Date.now();
    const tick = () => {
      const v = fn();
      if (v) return resolve(v);
      if (Date.now() - start >= timeoutMs) return resolve(v || null);
      setTimeout(tick, 200);
    };
    tick();
  });
}

// The slot index of the first item named `name` in an open window, or -1.
function findWindowSlot(window, name) {
  for (let i = 0; i < window.slots.length; i++) {
    if (window.slots[i] && window.slots[i].name === name) return i;
  }
  return -1;
}

function record(name, pass, detail) {
  results.push({ name, pass, detail });
  console.log(`[e2e] ${name}: ${pass ? 'PASS' : 'FAIL'} (${detail})`);
}

function finish(code) {
  const passed = results.filter((r) => r.pass).length;
  const ok = results.length > 0 && passed === results.length && code === 0;
  console.log(`[e2e] RESULT: ${ok ? 'PASS' : 'FAIL'} (${passed}/${results.length})`);
  process.exit(ok ? 0 : 1);
}

function cmd(bot, c) {
  bot.chat('/' + c);
  return sleep(300);
}

// Count dirt blocks in the 3x3 wall in front of base position `p`.
function countDirt(bot, p) {
  let n = 0;
  for (let dx = -1; dx <= 1; dx++) {
    for (let dy = 0; dy <= 2; dy++) {
      const b = bot.blockAt(new Vec3(p.x + dx, p.y + dy, p.z + WALL_DZ));
      if (b && b.name === 'dirt') n++;
    }
  }
  return n;
}

async function buildWall(bot) {
  const p = bot.entity.position.floored();
  // Clear the corridor between bot and wall, then raise a fresh dirt wall.
  await cmd(bot, `fill ${p.x - 1} ${p.y} ${p.z - 3} ${p.x + 1} ${p.y + 2} ${p.z - 1} air`);
  await cmd(bot, `fill ${p.x - 1} ${p.y} ${p.z + WALL_DZ} ${p.x + 1} ${p.y + 2} ${p.z + WALL_DZ} dirt`);
  await sleep(600); // let block updates reach the bot
  return p;
}

async function aimAtWall(bot, p) {
  const target = new Vec3(p.x + 0.5, p.y + 1.5, p.z + WALL_DZ + 0.5);
  await bot.lookAt(target, true);
  await sleep(300);
}

async function giveAndEquip(bot, base, mods, matcher, label) {
  await cmd(bot, `fusion give ${USER} ${base} ${mods}`);
  const item = await waitForItem(bot, matcher);
  if (!item) {
    const inv = bot.inventory.items().map((i) => i.name).join(', ') || '(empty)';
    record(label, false, `no ${base} after give; inv=[${inv}]; chat=[${recentChat.join(' | ')}]`);
    return null;
  }
  await bot.equip(item, 'hand');
  await sleep(300);
  return item;
}

// Scenario A: a real arm-swing of a fused mining sword carves the wall.
async function swingMining(bot) {
  const p = await buildWall(bot);
  const sword = await giveAndEquip(bot, 'DIAMOND_SWORD', 'MINING',
    (i) => i.name.includes('sword'), 'swing-mining');
  if (!sword) return;
  await aimAtWall(bot, p);
  const before = countDirt(bot, p);
  bot.swingArm('right'); // fires PlayerAnimationEvent(ARM_SWING) on the server
  await sleep(1500);
  const after = countDirt(bot, p);
  record('swing-mining', after < before, `dirt ${before} -> ${after}`);
}

// Scenario B: a fused mining bow carves the wall AND spawns no vanilla arrow.
async function bowMining(bot) {
  const p = await buildWall(bot);
  const bow = await giveAndEquip(bot, 'BOW', 'MINING',
    (i) => i.name === 'bow', 'bow-mining-breaks');
  if (!bow) {
    record('bow-no-vanilla-arrow', false, 'no bow');
    return;
  }
  await aimAtWall(bot, p);
  const before = countDirt(bot, p);
  bot.activateItem();   // draw
  await sleep(1300);    // ~full draw (creative fires without arrows)
  bot.deactivateItem(); // release -> EntityShootBowEvent
  await sleep(1500);
  const after = countDirt(bot, p);
  const arrows = Object.values(bot.entities).filter(
    (e) => e && (e.name === 'arrow' || e.displayName === 'Arrow'));
  record('bow-mining-breaks', after < before, `dirt ${before} -> ${after}`);
  record('bow-no-vanilla-arrow', arrows.length === 0, `arrows=${arrows.length}`);
}

// Clear inventory, get a Fusion Machine, and place it beside the bot (a real
// BlockPlaceEvent — only that tags it as a machine; /setblock leaves a plain,
// "dead" enchanting table). Returns the placed block, or null with the failure
// already recorded under `label`.
async function giveAndPlaceMachine(bot, label) {
  await cmd(bot, `clear ${USER}`); // clean slate — earlier scenarios left items
  await cmd(bot, 'fusion machine');
  const machineItem = await waitForItem(bot, (i) => i.name === 'enchanting_table');
  if (!machineItem) {
    record(label, false, 'no Fusion Machine item after /fusion machine');
    return null;
  }
  const p = bot.entity.position.floored();
  const rx = p.x + 1;
  const ry = p.y - 1;
  const rz = p.z;
  await cmd(bot, `setblock ${rx} ${ry} ${rz} minecraft:stone`);   // reference
  await cmd(bot, `setblock ${rx} ${ry + 1} ${rz} minecraft:air`); // clear target cell
  await sleep(600);
  await bot.equip(machineItem, 'hand');
  await bot.lookAt(new Vec3(rx + 0.5, ry + 1.5, rz + 0.5), true);
  await sleep(300);
  try {
    await bot.placeBlock(bot.blockAt(new Vec3(rx, ry, rz)), new Vec3(0, 1, 0));
  } catch (e) {
    record(label, false, 'placeBlock failed: ' + (e && e.message));
    return null;
  }
  await sleep(500);
  const block = bot.blockAt(new Vec3(rx, ry + 1, rz));
  if (!block || block.name !== 'enchanting_table') {
    record(label, false, `machine not placed (got ${block && block.name})`);
    return null;
  }
  return block;
}

// Scenario C: the full Fusion Machine (anvil) GUI — place the machine, open it,
// load Target + Ingredient, and take the fused result. Guards the historical
// failure spots: dead (untagged) machines, no result preview, "Too Expensive"
// blocking the take, and the result snapping back.
async function machineFusion(bot) {
  const label = 'machine-fusion';
  const machineBlock = await giveAndPlaceMachine(bot, label);
  if (!machineBlock) return;
  await cmd(bot, `give ${USER} minecraft:diamond_sword`);
  await cmd(bot, `give ${USER} minecraft:nether_star`);
  const haveSword = await waitForItem(bot, (i) => i.name === 'diamond_sword');
  const haveStar = await waitForItem(bot, (i) => i.name === 'nether_star');
  if (!haveSword || !haveStar) {
    return record(label, false, `missing inputs (sword=${!!haveSword} star=${!!haveStar})`);
  }

  // Open the anvil GUI (right-click the machine).
  let window;
  try {
    window = await bot.openBlock(machineBlock);
  } catch (e) {
    return record(label, false, 'openBlock (right-click machine) failed: ' + (e && e.message));
  }

  // The GUI is titled "✦ Fusion", not the vanilla "Repair & Name". The title
  // arrives as a parsed NBT/JSON component, so match against its serialized form.
  record('machine-anvil-title', JSON.stringify(window.title || '').includes('Fusion'),
    `title=${JSON.stringify(window.title)}`);

  // Load Target (slot 0) + Ingredient (slot 1).
  const swordSlot = findWindowSlot(window, 'diamond_sword');
  const starSlot = findWindowSlot(window, 'nether_star');
  if (swordSlot < 0 || starSlot < 0) {
    return record(label, false, `inputs not in window (sword=${swordSlot} star=${starSlot})`);
  }
  await bot.moveSlotItem(swordSlot, 0);
  await sleep(400);
  await bot.moveSlotItem(starSlot, 1);

  // The result slot should show the fused preview (dead machine / no-result bug).
  const result = await waitForCondition(() => window.slots[2], 5000);
  record('machine-shows-result', !!result, result ? `result=${result.name}` : 'result slot empty');

  // Take it — our onClick delivers the fused item and messages "Fusion complete!".
  // If "Too Expensive" (repair cost) or the snap-back bug regressed, no take, no message.
  recentChat.length = 0;
  try {
    await bot.clickWindow(2, 0, 0);
  } catch (_) { /* server may desync on our custom take; the chat check is authoritative */ }
  const completed = await waitForCondition(
    () => (recentChat.some((m) => m.includes('Fusion complete')) ? true : null), 5000);
  record('machine-take-fusion', !!completed,
    completed ? 'got "Fusion complete!"' : `chat=[${recentChat.join(' | ')}]`);

  try { await bot.closeWindow(window); } catch (_) { /* best-effort */ }
}

// Scenario D: a junk ingredient (dirt, no latent magic) yields a non-takeable
// red BARRIER in the result slot naming the reason — never a fused weapon. This
// is the in-GUI "why can't I fuse this" feedback.
async function machineRejectsJunk(bot) {
  const label = 'machine-rejects-junk';
  const block = await giveAndPlaceMachine(bot, label);
  if (!block) return;
  await cmd(bot, `give ${USER} minecraft:diamond_sword`);
  await cmd(bot, `give ${USER} minecraft:dirt`);
  const haveSword = await waitForItem(bot, (i) => i.name === 'diamond_sword');
  const haveDirt = await waitForItem(bot, (i) => i.name === 'dirt');
  if (!haveSword || !haveDirt) {
    return record(label, false, `missing inputs (sword=${!!haveSword} dirt=${!!haveDirt})`);
  }
  let window;
  try {
    window = await bot.openBlock(block);
  } catch (e) {
    return record(label, false, 'openBlock failed: ' + (e && e.message));
  }
  const swordSlot = findWindowSlot(window, 'diamond_sword');
  const dirtSlot = findWindowSlot(window, 'dirt');
  if (swordSlot < 0 || dirtSlot < 0) {
    return record(label, false, `inputs not in window (sword=${swordSlot} dirt=${dirtSlot})`);
  }
  await bot.moveSlotItem(swordSlot, 0);
  await sleep(400);
  await bot.moveSlotItem(dirtSlot, 1);
  // A barrier should appear in the result slot; a weapon must NOT.
  const res = await waitForCondition(
    () => (window.slots[2] && window.slots[2].name === 'barrier' ? window.slots[2] : null), 5000);
  record(label, !!res, res ? `result=${res.name} (barrier)` : `result=${window.slots[2] && window.slots[2].name}`);
  try { await bot.closeWindow(window); } catch (_) { /* best-effort */ }
}

// Scenario E: closing the machine with items still in the input slots returns
// them to the player — nothing is lost. (Item-safety: the most costly class of
// bug on a family server.)
async function machineCloseReturnsInputs(bot) {
  const label = 'machine-close-returns-inputs';
  const block = await giveAndPlaceMachine(bot, label);
  if (!block) return;
  await cmd(bot, `give ${USER} minecraft:diamond_sword`);
  await cmd(bot, `give ${USER} minecraft:nether_star`);
  const haveSword = await waitForItem(bot, (i) => i.name === 'diamond_sword');
  const haveStar = await waitForItem(bot, (i) => i.name === 'nether_star');
  if (!haveSword || !haveStar) {
    return record(label, false, `missing inputs (sword=${!!haveSword} star=${!!haveStar})`);
  }
  let window;
  try {
    window = await bot.openBlock(block);
  } catch (e) {
    return record(label, false, 'openBlock failed: ' + (e && e.message));
  }
  const swordSlot = findWindowSlot(window, 'diamond_sword');
  const starSlot = findWindowSlot(window, 'nether_star');
  if (swordSlot < 0 || starSlot < 0) {
    return record(label, false, `inputs not in window (sword=${swordSlot} star=${starSlot})`);
  }
  await bot.moveSlotItem(swordSlot, 0);
  await sleep(400);
  await bot.moveSlotItem(starSlot, 1);
  await sleep(400);
  try { await bot.closeWindow(window); } catch (_) { /* best-effort */ }
  await sleep(800);
  // Both inputs must be back in the player inventory after the close.
  const backSword = await waitForItem(bot, (i) => i.name === 'diamond_sword', 3000);
  const backStar = await waitForItem(bot, (i) => i.name === 'nether_star', 3000);
  record(label, !!backSword && !!backStar,
    `after close: sword=${!!backSword} star=${!!backStar}`);
}

// Scenario F: rename-only — a Target with no Ingredient is a valid rename, so
// the result slot echoes the item back (never a barrier, never empty) and is
// takeable. Guards the "offer a rename field but no output" dead end.
async function machineRenameOnly(bot) {
  const label = 'machine-rename-only';
  const block = await giveAndPlaceMachine(bot, label);
  if (!block) return;
  await cmd(bot, `give ${USER} minecraft:diamond_sword`);
  const haveSword = await waitForItem(bot, (i) => i.name === 'diamond_sword');
  if (!haveSword) return record(label, false, 'no sword');
  let window;
  try {
    window = await bot.openBlock(block);
  } catch (e) {
    return record(label, false, 'openBlock failed: ' + (e && e.message));
  }
  const swordSlot = findWindowSlot(window, 'diamond_sword');
  if (swordSlot < 0) return record(label, false, 'sword not in window');
  await bot.moveSlotItem(swordSlot, 0); // Target only, no Ingredient
  await sleep(600);
  // The result should echo the item back (a takeable rename), not a barrier.
  const res = await waitForCondition(
    () => (window.slots[2] && window.slots[2].name === 'diamond_sword' ? window.slots[2] : null), 4000);
  record(label, !!res,
    res ? `result=${res.name}` : `result=${window.slots[2] && window.slots[2].name}`);
  try { await bot.closeWindow(window); } catch (_) { /* best-effort */ }
}

async function main() {
  console.log(`[e2e] connecting to ${HOST}:${PORT} as ${USER} (protocol ${VERSION})`);
  const bot = mineflayer.createBot({
    host: HOST, port: PORT, username: USER, auth: 'offline', version: VERSION,
  });

  // Overall watchdog so a stuck connection fails the job instead of hanging.
  const watchdog = setTimeout(() => {
    record('watchdog', false, 'timed out before scenarios finished');
    finish(1);
  }, 120000);

  bot.on('kicked', (reason) => console.error('[e2e] kicked:', JSON.stringify(reason)));
  bot.on('error', (err) => console.error('[e2e] error:', err && err.message));
  bot.on('end', (reason) => console.error('[e2e] disconnected:', reason));
  bot.on('messagestr', (m) => {
    recentChat.push(m);
    if (recentChat.length > 8) recentChat.shift();
  });

  bot.once('spawn', async () => {
    try {
      record('connect', true, 'bot spawned (Via bridge OK)');
      // The runner ops us by name over RCON once we connect (the OPS env grants
      // the wrong UUID in offline mode). Wait for that to land before running
      // any op-only command.
      await sleep(7000);
      // Deterministic sandbox: no wandering mobs, no damage, daylight, creative
      // so the bow draws without arrows.
      await cmd(bot, 'gamerule doMobSpawning false');
      await cmd(bot, 'gamerule doDaylightCycle false');
      await cmd(bot, 'difficulty peaceful');
      await cmd(bot, 'time set day');
      await cmd(bot, `gamemode creative ${USER}`);
      await sleep(500);

      await swingMining(bot);
      await bowMining(bot);
      await machineFusion(bot);
      await machineRejectsJunk(bot);
      await machineCloseReturnsInputs(bot);
      await machineRenameOnly(bot);
    } catch (err) {
      record('harness', false, 'exception: ' + (err && err.message));
    } finally {
      clearTimeout(watchdog);
      bot.quit();
      finish(0);
    }
  });
}

main();
