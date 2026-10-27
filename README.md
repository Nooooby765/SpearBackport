# Spear Backport (长矛反向移植)

Faithfully backports the **Minecraft 1.21.11 Spear** to **Minecraft 1.12.2 Forge**.

![Spear item textures](docs/spear_textures_preview.png)

- **License:** MIT
- **Build:** RetroFuturaGradle 2.0 + JDK 25, Java 8 bytecode target
- **No mixins, no ASM, no coremod** — plain Forge events, one item-use callback, three client hooks
  and one tiny client-to-server jab packet.
- **Procedural in-hand animation** — the 1.21.11 charge and stab poses, replayed on the item itself
  (first and third person) from the held time and the swing progress. No GeckoLib, no keyframes.
- **No idle overhead:** no global tick handler, and no render work at all for an item that is not a
  spear. Work happens only while a player is actually jabbing with a spear, holding right-click to
  charge, or the client is processing an input/click.

## Tiers (5)

| Tier    | Jab damage | Attack speed | Charge multiplier | Durability | Enchantability |
|---------|-----------:|-------------:|------------------:|-----------:|---------------:|
| Wooden  | 1          | 1.54         | 0.700             | 59         | 15             |
| Golden  | 1          | 1.05         | 0.700             | 32         | 22             |
| Stone   | 2          | 1.33         | 0.820             | 131        | 5              |
| Iron    | 3          | 1.05         | 0.950             | 250        | 14             |
| Diamond | 4          | 0.95         | 1.075             | 1561       | 10             |

Durability and enchantability come straight from the vanilla tool materials, so they match
1.21.11 exactly. Gold is intentionally fragile and highly enchantable.

## Mechanics

### Reach and targeting

- **2.0–4.5 blocks** (2.0–6.5 in Creative), measured from the eyes to the inflated hitbox.
- Every target hitbox is inflated by **0.125 blocks**.
- The attack ray stops at the first block **with a collision box**. Walls, fences and slabs block
  the spear; cobwebs and tall grass do not, so they can be attacked through.
- Jabs and charge attacks hit **every valid living entity** along the ray, not just the first.
- No critical hits and no sprint-knockback attacks.
- Left-clicking a block with a spear never breaks or interacts with it; the click becomes a jab.

### Jab (left click)

- Requires a **fully charged attack meter** (`MINIMUM_ATTACK_CHARGE = 1.0`). An early click is
  refused and does **not** reset the meter.
- Damage = the player's `ATTACK_DAMAGE` attribute (so Strength/Weakness and the spear's own
  modifier apply) plus Sharpness / Smite / Bane of Arthropods.
- Applies Knockback, Fire Aspect, Looting (through the normal death path), thorns retaliation and
  Bane slowness. The spear knocks targets back even when they take no damage.
- **1 durability and 0.1 exhaustion per affected target**, not per swing, matching `Weapon(1)`
  and `Player.stabAttack`.
- Every valid jab runs `postPiercingAttack`, so Lunge also fires on a **whiff**.
- The client sends one lightweight jab packet for empty air, blocks, and targets in the
  3–4.5 block band that vanilla's picker cannot reach. Entity clicks inside vanilla reach still
  use `CPacketUseEntity`; the two paths never overlap.

### Charge (hold right click)

The spear goes through three stages. Values are the 1.21.11 release values, in ticks:

| Tier    | Activation | Engaged | Tired | Disengaged | Total | Dismount speed |
|---------|-----------:|--------:|------:|-----------:|------:|---------------:|
| Wooden  | 15         | 100     | 100   | 100        | 315   | 14.0 b/s       |
| Golden  | 14         | 70      | 100   | 105        | 289   | 10.0 b/s       |
| Stone   | 14         | 90      | 90    | 95         | 289   | 10.0 b/s       |
| Iron    | 12         | 50      | 85    | 90         | 237   | 8.0 b/s        |
| Diamond | 10         | 60      | 70    | 70         | 210   | 7.5 b/s        |

- **Engaged:** damage, knockback and dismount.
- **Tired:** damage and knockback.
- **Disengaged:** damage only.
- Damage = `base_attack_damage + floor(closing_speed * multiplier)` plus Sharpness/Smite/Bane,
  where closing speed is the attacker-to-target relative velocity in blocks/second projected onto
  the look vector. Base attack damage is 1.0 for players, so this is the familiar `+1`.
- The three effects are **independent conditions**: a contact below the 4.6 b/s damage threshold
  can still knock back (5.1 b/s) or dismount (per-tier speed).
- **10-tick contact cooldown per target** (`KineticWeapon.contactCooldownTicks`), so a target can
  be hit at most every 0.5 seconds even if it stays inside the spear.
- 1 durability and 0.1 exhaustion per affected target per contact.
- Charge damage ignores the attack-damage attribute, so Strength and Weakness do not affect it.
- The charge does not slow movement. On foot the 1.12.2 `isHandActive` 0.2x input penalty is
  cancelled in `InputUpdateEvent`; while riding vanilla skips it entirely.
- Maximum reach is extended by the attacker's forward movement, matching `getHitEntitiesAlong`.
- The in-hand spear plays the full 1.21.11 raise → sway → elastic lower → raise-back pose. See
  [In-hand animation](#in-hand-animation).

### Lunge (spear-only enchantment, max III)

- Every valid jab, including a **whiff**, adds a horizontal impulse of
  **0.458 × level blocks/tick** along the look direction; the vertical look component is not
  applied.
- Costs **4 × level exhaustion** (1/2/3 hunger points) and **1 extra durability**.
- 1.21.11 itself has **no hunger requirement**; only the vehicle / elytra / water checks apply.
  (The 26.3 data later adds a 7-hunger gate; this mod follows 1.21.11.)
- Weight 5, minimum cost `5 + 8 × (level - 1)`, maximum cost +20, compatible with Mending and
  obtainable on enchanted books.

## Crafting

Diagonal recipe, matching 1.21.11:

```
  M
 S
S
```

`M` is the head material and `S` is a stick. Recipes use ore dictionary names
(`plankWood`, `cobblestone`, `ingotGold`, `ingotIron`, `gemDiamond`) so modded materials work.

## In-hand animation

1.21.11's spear animation is not keyframed. `net.minecraft.client.model.effects.SpearAnimations`
derives every value from the held time and the swing progress, then applies it with `PoseStack`
calls. 1.12.2 has no comparable "before the item is drawn" hook, but `IBakedModel.handlePerspective`
is asked for the model transform in exactly the place where 1.21.11 applies its pose, so the same
calls can be replayed on top of the display matrix.

- **Charge (`SpearAnimations.firstPersonUse` / `thirdPersonUseItem`)** — `SpearUseParams` recomputes
  the official `UseParams` from the tier timings: `raise`, `raiseStart/Middle/End`, `sway`,
  `lower` and `raiseBack`, plus the slow and fast sine sway. The pose is therefore a pure function of
  `timeHeld`, with no stored state and nothing to desynchronise.
- **Stab (`firstPersonAttack` / `thirdPersonAttackItem`)** — driven by `getSwingProgress`, using the
  official `prepare` / `attack` / `retract` easing triple.
- **Easing** — `SpearEase` is a direct port of `net.minecraft.util.Ease` (`inQuad`, `inOutSine`,
  `inOutExpo`, `outBack`, `inOutBack`, `inOutElastic`, `outCubic`, `outCirc`, `inCirc`) so the curves
  are identical rather than approximate.
- **Composition** — the pose is multiplied onto the cached display transform, matching the
  `animation -> display -> translate(-0.5)` order of the two renderers.

Two consequences are worth knowing:

- 1.12.2 applies a horizontal WHACK to the first person hand *before* the item transform is
  consulted, so a spear jab would otherwise be a -80° chop with a thrust added on top.
  `RenderSpecificHandEvent` re-submits that hand with zero swing progress, which puts the item in
  the neutral hand pose the 1.21.11 stab expects. Nothing is drawn twice and no GL state is touched.
- Third person needs to know which player is being drawn, which 1.12.2 does not provide. The player
  is published around `RenderPlayerEvent.Pre/Post` (four field writes per rendered player) and
  re-validated against the hand and the item before use.

## Sounds

1.21.11 uses dedicated `item.spear.{attack,hit,use}` samples plus `wood_` variants. Those assets do
not exist in 1.12.2, so this mod ships its own generated set with the same structure and the same
triggers:

| Event | When | 1.21.11 equivalent |
|-------|------|--------------------|
| `item.spear.attack` | every valid jab, hit or whiff | `PiercingWeapon.makeSound` |
| `item.spear.hit` | jab that connected, and each charge contact | `PiercingWeapon.makeHitSound` / `KineticWeapon.makeLocalHitSound` |
| `item.spear.use` | right-click, i.e. the charge starts | `KineticWeapon.makeSound` |

The sounds are synthesised by `tools/make_spear_sounds.py` (numpy + soundfile), so they are original
assets and reproducible: a band-passed noise thrust for the attack, a pitch-gliding body plus a
high crack for the impact, and a slow low sweep for raising the spear. The wooden tier uses the
`wood_` variants, exactly like the material switch in `Item.Properties#spear`.

## Implementation notes

- Combat is server-authoritative. The client sends one tiny `MessageSpearJab` for clicks that
  vanilla cannot turn into an entity attack (air, blocks, 3–4.5 block targets); the server
  re-validates the held item, cooldown, range and line of sight.
- Charge speed is sampled from per-tick position deltas, because 1.12.2 servers do not maintain
  `motionX` for players from movement packets. The per-attacker contact map lives in the same
  `WeakHashMap` sample and only grows while a charge is in use.
- `SpearItem` extends `ItemSword` for free sword-enchantment compatibility and correct repair
  behaviour; it overrides attributes, destroy speed and the use lifecycle.
- Item models use a 16×16 icon for GUI/ground/fixed and a longer 32×32 in-hand model for the
  hand, selected by a cached `IBakedModel` wrapper. The display transforms are the official
  `item/spear_in_hand` values; the texture and art are original.
- Item sprites are stitched by the normal item-model loader (no manual atlas registration), which
  keeps the texture pipeline identical to vanilla items.
- The animation adds no per-tick cost. Display pairs are cached per transform type, every non-hand
  transform and every idle frame returns a cached pair without allocating, and the pose builder
  writes straight into one `Matrix4f` with the rotations inlined (no temporary matrices).

## Known limitations

- Mounted AI and mob spear spawning are intentionally out of scope.
- Only the **item** is animated. In 1.21.11 the arm and body follow the spear (`HumanoidModel`'s
  SPEAR pose plus `thirdPersonAttackHand`); 1.12.2 gives no hook between the arm rotation and the
  item transform, and doing it properly needs a custom `RenderPlayer`, an access transformer or a
  mixin. None of those were added.
- Because the arm is left alone, a first person jab holds the arm still and lets the spear thrust,
  and a third person jab keeps vanilla's WHACK arm swing underneath the stab.
- The kinetic hit feedback (`ticksSinceKineticHitFeedback`, a 10 tick recoil) needs a server to
  client packet for every hit. It is omitted; the animation is time-driven only.
- Lunge fires on every valid jab, including a whiff, matching `postPiercingAttack`.

## Build

```powershell
$env:JAVA_HOME = "F:\jdk25\jdk-25.0.4.1+1"   # any JDK 25 install
.\gradlew.bat build
```

Output: `build/libs/spearbackport-1.0.0.jar`

The sound assets are committed, so building does not need Python. To regenerate them:

```powershell
python tools/make_spear_sounds.py src/main/resources/assets/spearbackport/sounds/item
```

Requires `numpy` and `soundfile` (which bundles a libsndfile with Vorbis support).

## Credits

Mojang for the original 1.21.11 spear design and data. Textures are the user's old-style
16×16 and 32×32 redraws from `自绘旧版风格长矛材质/`, installed by `make_textures.py`; they are
not copied vanilla assets. The sound set is synthesised from scratch by `tools/make_spear_sounds.py`
and is not taken from the game. The combat values were cross-checked against the decompiled
1.21.11/26.3 `KineticWeapon`, `PiercingWeapon`, `AttackRange` and spear item components, and the
animation against `SpearAnimations`, `FirstPersonHandsAndItemsRenderer`, `ItemInHandLayer`,
`HumanoidModel`, `Ease` and `Mth`.

