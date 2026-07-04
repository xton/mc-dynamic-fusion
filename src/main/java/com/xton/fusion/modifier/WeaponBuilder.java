package com.xton.fusion.modifier;

import java.util.ArrayDeque;
import java.util.Deque;

import org.bukkit.Material;

/**
 * Compiles a modifier stack into a {@link ProjectileSpec}, RPN-style: the stack
 * is walked left→right and each modifier acts on the element currently being
 * built. Transforms bind to the <em>nearest preceding emitter</em> — an AOE
 * transform mutates {@link #topAoe()} (the last AOE element), a flight transform
 * mutates {@link #projectile()} (the current projectile). A transform with no
 * matching preceding emitter is inert.
 *
 * <p><b>SPAWN pushes a fresh child projectile</b> as the new current one, so
 * every modifier after SPAWN targets the child (that's cluster / nesting). The
 * child is registered on its parent's spawn list and launched at the parent's
 * terminus. {@link #compile} always returns the root (the launched projectile).
 */
public final class WeaponBuilder {

    /** Base values an emitter/flight starts from, resolved from config. */
    public record Defaults(double baseSpeed, int baseLifetimeTicks, double pierceMaxHardness,
                           double pushRadius, double pushPower,
                           double damageRadius, double damagePower,
                           double fireRadius, double iceRadius, double depositRadius) {
    }

    private final Defaults defaults;
    private final ProjectileSpec root;
    private final Deque<ProjectileSpec> stack = new ArrayDeque<>();

    public WeaponBuilder(Defaults defaults) {
        this.defaults = defaults;
        this.root = newProjectile();
        stack.push(root);
    }

    private ProjectileSpec newProjectile() {
        ProjectileSpec p = new ProjectileSpec();
        p.setSpeed(defaults.baseSpeed());
        p.setLifetimeTicks(defaults.baseLifetimeTicks());
        p.setPierceMaxHardness(defaults.pierceMaxHardness());
        return p;
    }

    public Defaults defaults() {
        return defaults;
    }

    /** The current projectile — the target of flight transforms (the child after SPAWN). */
    public ProjectileSpec projectile() {
        return stack.peek();
    }

    /** The nearest preceding AOE emitter on the current projectile, or null. */
    public AoeSpec topAoe() {
        return stack.peek().topAoe();
    }

    /** Emitter: add a PUSH burst (base size) to the current projectile's payload. */
    public AoeSpec emitPush() {
        return stack.peek().addAoe(new AoeSpec(AoeKind.PUSH, defaults.pushRadius(), defaults.pushPower()));
    }

    /** Emitter: add a DAMAGE burst (base size) to the current projectile's payload. */
    public AoeSpec emitDamage() {
        return stack.peek().addAoe(new AoeSpec(AoeKind.DAMAGE, defaults.damageRadius(), defaults.damagePower()));
    }

    /** Emitter: add a MINING element — a block-breaking bore of the given base radius. */
    public AoeSpec emitMining(double radius) {
        return stack.peek().addAoe(new AoeSpec(AoeKind.MINING, radius, 0));
    }

    /** Emitter: add a FIRE element — ignites blocks/mobs and melts snow/ice in radius. */
    public AoeSpec emitFire() {
        return stack.peek().addAoe(new AoeSpec(AoeKind.FIRE, defaults.fireRadius(), 0));
    }

    /** Emitter: add an ICE element — freezes water/fire and chills mobs in radius. */
    public AoeSpec emitIce() {
        return stack.peek().addAoe(new AoeSpec(AoeKind.ICE, defaults.iceRadius(), 0));
    }

    /** Emitter: add a DEPOSIT element — fills the empty cells in radius with {@code material}. */
    public AoeSpec emitDeposit(Material material) {
        return stack.peek().addAoe(new AoeSpec(AoeKind.DEPOSIT, defaults.depositRadius(), 0, material));
    }

    /**
     * Emitter: spawn a fresh child projectile at the current one's terminus and
     * make the child current, so subsequent modifiers build it. Nothing is
     * inherited — the child starts from base flight with an empty payload.
     */
    public void emitSpawn() {
        ProjectileSpec child = newProjectile();
        stack.peek().addSpawn(child);
        stack.push(child);
    }

    /** Walk the stack in order, letting each modifier act, and return the root. */
    public ProjectileSpec compile(ModifierStack stack) {
        for (Modifier modifier : stack.modifiers()) {
            modifier.apply(this);
        }
        return root;
    }
}
