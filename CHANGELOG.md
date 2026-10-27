# Changelog

## [1.0.0] - 2026-10-27

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

### Removed

- Unused perspective-model wrapper, old client event handler and proxies.
- Debug model-bake output.
