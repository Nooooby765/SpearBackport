# Changelog

Pre-1.0: the backport is feature complete but still being polished, so minor versions may still
change behaviour, feel or assets.

## [0.4.0] - 2026-10-27

### Added

- Wooden, stone, iron, golden and diamond spears with 1.21.11 jab damage, attack speed, charge
  multipliers, durability and enchantability.
- 2–4.5 block reach, 0.125 hitbox margin, multi-target piercing, non-solid block piercing and
  creative-mode extended reach.
- Three-stage charge attack (engaged / tired / disengaged) with the 1.21.11 timing table and the
  decompiled `KineticWeapon` rules:
  - **10-tick contact cooldown per target**;
  - damage, knockback and dismount are independent conditions;
  - damage is `base_attack_damage + floor(closing_speed * multiplier)`.
- Lunge enchantment (max III) with the 1.21.11 impulse, exhaustion and durability values. It fires
  on every valid jab, including a whiff, matching `postPiercingAttack`.
- 1.21.11-style jab flow: forced 100% attack charge, early clicks do not reset the meter,
  per-target durability/exhaustion, knockback even without damage, and one lightweight client
  jab packet for air, blocks and 3–4.5 block targets.
- A 32×32 in-hand spear model selected through a cached display-context wrapper, using the
  official `item/spear_in_hand` transforms.
- Procedural in-hand animation for the spear itself, first and third person, ported from the
  1.21.11 `SpearAnimations` code:
  - the charge pose (`raise`, `raiseStart/Middle/End`, `sway`, elastic `lower`, `raiseBack` and the
    two sine sway terms) recomputed from the tier timings and the held time, exactly like
    `UseParams.fromKineticWeapon`;
  - the jab pose (`prepare` / `attack` / `retract`) driven by the swing progress;
  - the official `Ease` curves (`inQuad`, `inOutSine`, `inOutExpo`, `outBack`, `inOutBack`,
    `inOutElastic`, `outCubic`, `outCirc`, `inCirc`) ported one for one.
- Vanilla's first-person WHACK is replaced with the neutral hand pose for spear jabs, so the item
  animation is not stacked on top of an −80° chop.
- The attack-cooldown hand rise is removed for spears, matching
  `swingAndResetAttackStrength(..., resetAttackStrength = false)`: 1.12.2 no longer resets the attack
  meter on a jab, and the attack part of the hand height is subtracted as a safeguard. The item-swap
  dip is preserved.
- A left click that is not fully charged now does nothing at all, like `Minecraft#startAttack`
  returning before the swing when `MINIMUM_ATTACK_CHARGE` rejects the attack
  (`SpearItem.onEntitySwing` + `SpearJabState`).
- 17 generated spear sounds matching the official `sounds.json` set one for one
  (`item.spear.{attack,hit,use,lunge}` plus the `item.spear_wood.*` twins the wooden tier uses,
  three randomised variants for attack/hit/lunge and one for use), replacing the borrowed sword
  sweep and strong-attack sounds and the ender pearl throw on Lunge. Each voice is layered
  (transient, air, body resonance, low thock, creak train) and then mastered onto the eight band
  energy split, the decay, the attack time, the crest factor and the audible loudness measured from
  the official files, so the metal thrust is crisp, the wooden one sits between 40 Hz and 1 kHz and
  Lunge is a short gust. Regenerable with `tools/make_spear_sounds.py`, which prints every measured
  feature it matches.
- Diagonal crafting recipes using ore dictionary ingredients.

### Fixed

- Adopted the user's old-style 16×16 icons and 32×32 in-hand textures for all five tiers
  (`自绘旧版风格长矛材质/`), and rebuilt the docs preview from that set.
- Removed the manual texture-atlas registration and debug model-bake logging from the texture
  pipeline.
- Jab damage now comes from the player's `ATTACK_DAMAGE` attribute, so Strength/Weakness and the
  spear modifier apply as intended.
- Fixed charge knockback/dismount being tied to damage and being skipped in the tired stage.
- Fixed the oversized charge hit frequency by adding the per-target 10-tick contact cooldown.
- Fixed the enchantment language key (`enchantment.lunge`) and added reach/charge tooltips.
- Fixed the crafting recipe orientation (diagonal, matching 1.21.11).
- Replaced the ineffective end-of-tick movement restore with an `InputUpdateEvent` correction.
- Fixed the spear slowly rising while the attack indicator refilled after every jab, which a
  1.21.11 spear never does. The hand height is now a constant while the meter refills instead of a
  difference of two differently interpolated values, which left a visible twitch.
- Fixed uncharged left clicks still swinging the arm and playing the stab animation.
- Fixed a refused click still emptying the attack meter, so the HUD bar restarted as if the spear
  had swung. The value is restored from the authoritative jab tick; this is the one reason the mod
  now ships a single access transformer (`EntityLivingBase.ticksSinceLastSwing`).

### Changed

- Version reset to `0.3.0`: the backport is feature complete but still being polished, so minor
  versions may still change behaviour, feel or assets. The mod metadata now reads the version from
  the generated `Tags` class, so `gradle.properties` is the single source of truth.
- `SpearItem` builds its attribute modifiers once instead of once per tooltip or equipment update.
- The animation skips the identity pose states (start of a charge and the settled tail) and bails
  out of the render path before any hand lookup when the spear is idle.
- The first person hand takeover is skipped entirely when nothing needs correcting, so a held spear
  costs vanilla's own draw in the steady state, and the cooldown probe is skipped with it.
- The attack meter now mirrors the spear's own cooldown, so the vanilla attack indicator doubles as
  the cooldown bar that the deliberately short stab animation cannot provide. A refused click still
  refunds it.
- The charge tick leaves before sampling motion once the charge has expired or before it activates.

### Removed

- Unused perspective-model wrapper, old client event handler and proxies.
- Debug model-bake output.
