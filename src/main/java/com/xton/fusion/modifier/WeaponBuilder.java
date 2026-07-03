package com.xton.fusion.modifier;

/**
 * Compiles a modifier stack into a {@link ProjectileSpec}, RPN-style: the stack
 * is walked left→right and each modifier acts on the element currently being
 * built. Transforms bind to the <em>nearest preceding emitter</em> — an AOE
 * transform mutates {@link #topAoe()} (the last AOE element), a flight transform
 * mutates {@link #projectile()} (the current projectile). A transform with no
 * matching preceding emitter is inert.
 *
 * <p>Emitters seed their element from the configured {@link Defaults}; the
 * transforms that follow scale or decorate it. For now there is a single
 * (implicit) root projectile — the swing/bow always launches one — so flight
 * transforms always have a target. A future spawn-projectile emitter would push
 * a new current projectile here, which is where cluster/nesting slots in.
 */
public final class WeaponBuilder {

    /** Base values an emitter/flight starts from, resolved from config. */
    public record Defaults(double baseSpeed, int baseLifetimeTicks, double pierceMaxHardness,
                           double pushRadius, double pushPower,
                           double damageRadius, double damagePower) {
    }

    private final Defaults defaults;
    private final ProjectileSpec root;

    public WeaponBuilder(Defaults defaults) {
        this.defaults = defaults;
        this.root = new ProjectileSpec();
        root.setSpeed(defaults.baseSpeed());
        root.setLifetimeTicks(defaults.baseLifetimeTicks());
        root.setPierceMaxHardness(defaults.pierceMaxHardness());
    }

    public Defaults defaults() {
        return defaults;
    }

    /** The current projectile — the target of flight transforms. */
    public ProjectileSpec projectile() {
        return root;
    }

    /** The nearest preceding AOE emitter — the target of AOE transforms. */
    public AoeSpec topAoe() {
        return root.topAoe();
    }

    /** Emitter: add a PUSH burst (base size) to the current projectile's payload. */
    public AoeSpec emitPush() {
        return root.addAoe(new AoeSpec(AoeKind.PUSH, defaults.pushRadius(), defaults.pushPower()));
    }

    /** Emitter: add a DAMAGE burst (base size) to the current projectile's payload. */
    public AoeSpec emitDamage() {
        return root.addAoe(new AoeSpec(AoeKind.DAMAGE, defaults.damageRadius(), defaults.damagePower()));
    }

    /**
     * Emitter: add a MINING element — a block-breaking tunnel of the given base
     * radius. EXPAND scales its radius (a wider bore); it is carved along the
     * flight, not delivered as a terminus burst.
     */
    public AoeSpec emitMining(double radius) {
        return root.addAoe(new AoeSpec(AoeKind.MINING, radius, 0));
    }

    /** Walk the stack in order, letting each modifier act, and return the spec. */
    public ProjectileSpec compile(ModifierStack stack) {
        for (Modifier modifier : stack.modifiers()) {
            modifier.apply(this);
        }
        return root;
    }
}
