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
const WALL_DZ = -4; // wall is 4 blocks north of the bot

const results = [];
const recentChat = []; // last few server/system messages, for diagnostics
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// Poll the bot's inventory until an item matches or we time out.
function waitForItem(bot, matcher, timeoutMs = 6000) {
  return new Promise((resolve) => {
    const start = Date.now();
    const tick = () => {
      const item = bot.inventory.items().find(matcher);
      if (item) return resolve(item);
      if (Date.now() - start >= timeoutMs) return resolve(null);
      setTimeout(tick, 200);
    };
    tick();
  });
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

async function main() {
  console.log(`[e2e] connecting to ${HOST}:${PORT} as ${USER} (protocol ${VERSION})`);
  const bot = mineflayer.createBot({
    host: HOST, port: PORT, username: USER, auth: 'offline', version: VERSION,
  });

  // Overall watchdog so a stuck connection fails the job instead of hanging.
  const watchdog = setTimeout(() => {
    record('watchdog', false, 'timed out before scenarios finished');
    finish(1);
  }, 90000);

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
