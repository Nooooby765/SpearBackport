# Spear Backport (长矛反向移植)

Faithfully backports the **Minecraft 1.21.11 Spear** to **Minecraft 1.12.2 Forge**.

![Spear item textures](docs/spear_textures_preview.png)

- **License:** MIT
- **Build:** RetroFuturaGradle 2.0 + JDK 25, Java 8 bytecode target
- **No mixins, no ASM, no coremod** — plain Forge events, one access transformer (a single field),
  one item-use callback, three client hooks and one tiny client-to-server jab packet.
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
  refused and does **not** reset the meter. 1.21.11 returns before the swing when
  `cannotAttackWithItem` rejects the attack, so a refused click here does nothing at all: no arm
  swing, no stab animation, no sound, no damage (`SpearItem.onEntitySwing`).
- The spear's own cooldown is its attack speed, which is also the length of the 1.21.11
  `SwingAnimation(STAB, attackDuration × 20)`. The attack meter tracks that cooldown so the vanilla
  attack indicator doubles as the spear's cooldown bar, and the in-hand rise that the meter
  normally drives is suppressed (see [In-hand animation](#in-hand-animation)), so the bar moves
  without the weapon twitching.
- **A refused click is free.** 1.12.2 empties the meter from `Minecraft#clickMouse` before the item
  is even consulted, so the value is put back from the authoritative jab tick. That is the only
  reason this mod ships an access transformer: `EntityLivingBase.ticksSinceLastSwing` has no public
  setter.
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

Three consequences are worth knowing:

- 1.12.2 applies a horizontal WHACK to the first person hand *before* the item transform is
  consulted, so a spear jab would otherwise be a -80° chop with a thrust added on top.
  `RenderSpecificHandEvent` re-submits that hand with zero swing progress, which puts the item in
  the neutral hand pose the 1.21.11 stab expects. Nothing is drawn twice and no GL state is touched.
- The same hook removes the **attack cooldown rise**. 1.12.2 lowers the hand item towards
  `1 - cooledAttackStrength³` and raises it again as the meter refills. The height is replaced with
  a constant while the meter is refilling rather than subtracting the analytic term: the vanilla
  value is a per-tick figure interpolated with partial ticks while the analytic term is a per-tick
  step, and their difference wobbles by a fraction of a block, which reads as a twitch. A constant
  is exact; the only thing lost is the item-swap dip during the few ticks a refill lasts, and idle
  frames never take the hand over at all.
- Third person needs to know which player is being drawn, which 1.12.2 does not provide. The player
  is published around `RenderPlayerEvent.Pre/Post` (four field writes per rendered player) and
  re-validated against the hand and the item before use.

## Sounds

1.21.11 registers exactly seven spear sound events in `assets/minecraft/sounds.json`, and this mod
mirrors them one for one — same IDs, same variant counts, same subtitles:

| Event | Variants | When | 1.21.11 equivalent |
|-------|---------:|------|--------------------|
| `item.spear.attack` | 3 | every valid jab, hit or whiff | `PiercingWeapon.makeSound` |
| `item.spear.hit` | 3 | jab that connected, and each charge contact | `PiercingWeapon.makeHitSound` / `KineticWeapon.makeLocalHitSound` |
| `item.spear.use` | 1 | right-click, i.e. the charge starts | `KineticWeapon.makeSound` |
| `item.spear.lunge` | 3 | each jab that applies Lunge | the Lunge enchantment effect |
| `item.spear_wood.attack` | 3 | same, wooden spear | wood material switch |
| `item.spear_wood.hit` | 3 | same, wooden spear | wood material switch |
| `item.spear_wood.use` | 1 | same, wooden spear | wood material switch |

So there are two families, not five: the **wooden** spear has its own set and every other tier shares
the default one, exactly like `Item.Properties#spear` picking `SPEAR_WOOD_*` only for
`ToolMaterial.WOOD`. Note the underscore: the wooden IDs are `item.spear_wood.*`, not
`item.spear.wood_*`, and the subtitles follow the official `subtitles.item.spear*` keys.

The only deliberate deviation is Lunge: the official data splits it into three separate events
(`item.spear.lunge_1`, `_2`, `_3`, one sample each) that the game picks between. 1.12.2 randomises
between the samples of a single event, so one `item.spear.lunge` with three variants produces the
same audible variation with no extra code.

The samples are synthesised by `tools/make_spear_sounds.py` (numpy + soundfile), so they are
original assets and fully reproducible — no game audio is used. Each voice is built from
physically motivated layers (transient, gate of air, body resonance, low thock, creak train) and
then mastered against measurements of the official files:

- **Tone** — a static magnitude EQ solves the layer mix onto the eight band energy split measured
  from the official samples, so the metal thrust gets the official 8–16 kHz shelf that makes it
  crisp, the wooden thrust keeps its energy between 40 Hz and 1 kHz, the impact stays a bright
  crack with almost no body, and Lunge is a 500–1000 Hz wind with air on top. Phase is untouched,
  so every transient and every grain survives.
- **Time** — audible length, 5%-to-peak attack, decay to −30 dB, crest factor and the envelope
  modulation rate are matched to the same measurements (the official thrusts peak in 4–28 ms and
  decay in 114–196 ms; the stance sound is a single creak with a crest factor around 18; Lunge is
  a short gust rather than a long swell).
- **Level** — normalisation is done on the *audible* region, because the official files carry long
  silent tails and a whole-file RMS would make the set several dB too loud in game.

The script prints all of those features for every file it writes, so the result can be checked
against the targets recorded in its docstring.

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

## Performance

The mod is written so that holding a spear costs almost nothing and holding anything else costs
nothing at all. There is no tick handler, no scheduled task and no polling of any kind.

Always-on work, in full:

| Hook | Cost when nothing is happening |
|------|--------------------------------|
| `RenderPlayerEvent.Pre/Post` | four field writes per rendered player per frame (publishes the player being drawn, see above) |
| `RenderSpecificHandEvent` | one `instanceof` and one float compare per rendered hand per frame; the hand is only taken over when the swing must be hidden or the meter is refilling, so an idle spear is drawn by vanilla itself |
| `InputUpdateEvent` | two field reads per client tick |
| `Item.onEntitySwing` | one `instanceof`, one `isUser()` and one int compare per swing |
| `AttackEntityEvent` / `LeftClickBlock` | only on an actual interaction |

The animation itself only runs while a spear is charging or jabbing:

- Display pairs are cached per transform type. GUI, ground, fixed, head and every non-hand transform
  return the cached pair directly, as does every idle frame.
- `SpearUseParams.isNeutral()` short-circuits the two states where the pose is the identity (before
  the rise starts and after the spear settles back), so the long tail of a charge allocates nothing
  and does no matrix work.
- The pose builder writes into a single `Matrix4f`; the rotations are inlined on the raw fields, so
  there are no temporary matrices, and the only allocations per animated frame are that matrix, one
  small immutable parameter object and the `Pair` Forge requires.
- `SpearItem` builds its attribute modifiers once in the constructor instead of per tooltip frame.
- The charge tick leaves before sampling motion or building a look vector until the activation delay
  has passed, and again once the charge expires, so a held right click costs one comparison per tick
  in those two windows.

Deliberately not changed, because the trade-off would not be free:

- The `Pair`/`Matrix4f` returned by `handlePerspective` is not cached and mutated: Forge copies it,
  but the `IBakedModel` contract allows a caller to keep it, so mutating a shared instance would be a
  silent correctness hazard for a saving of one 64-byte object per animated frame.
- Spear sounds are 0.5-1.6 s samples with the silent head and tail trimmed, so decode memory stays
  small without changing the audible part.

## Known limitations

- Mounted AI and mob spear spawning are intentionally out of scope.
- Only the **item** is animated. In 1.21.11 the arm and body follow the spear (`HumanoidModel`'s
  SPEAR pose plus `thirdPersonAttackHand`); 1.12.2 gives no hook between the arm rotation and the
  item transform, and doing it properly needs a custom `RenderPlayer` or a mixin. Neither was added.
- Because the arm is left alone, a first person jab holds the arm still and lets the spear thrust,
  and a third person jab keeps vanilla's WHACK arm swing underneath the stab.
- The kinetic hit feedback (`ticksSinceKineticHitFeedback`, a 10 tick recoil) needs a server to
  client packet for every hit. It is omitted; the animation is time-driven only.
- A refused click is invisible to the player, but 1.12.2 sends `CPacketAnimation` from
  `EntityPlayerSP#swingArm` unconditionally and after the item has already been consulted. The
  server therefore still broadcasts the arm swing to observers; only the local arm, the spear pose,
  the meter and the sound are suppressed. Hiding it for observers too would need a hook 1.12.2 does
  not have.
- Lunge fires on every valid jab, including a whiff, matching `postPiercingAttack`.

## Build

```powershell
$env:JAVA_HOME = "F:\jdk25\jdk-25.0.4.1+1"   # any JDK 25 install
.\gradlew.bat build
```

Output: `build/libs/spearbackport-0.4.0.jar`

The sound assets are committed, so building does not need Python. To regenerate them:

```powershell
python tools/make_spear_sounds.py src/main/resources/assets/spearbackport/sounds/item/spear
```

Requires `numpy` and `soundfile` (which bundles a libsndfile with Vorbis support). The script prints
the measured loudness and spectral centroid of each file so the result can be checked against the
targets in its docstring.

## Credits

Mojang for the original 1.21.11 spear design and data. Textures are the user's old-style
16×16 and 32×32 redraws from `自绘旧版风格长矛材质/`, installed by `make_textures.py`; they are
not copied vanilla assets. The sound set is synthesised from scratch by `tools/make_spear_sounds.py`
and contains no game audio; only its duration, loudness and spectral targets were measured from the
official samples. The combat values were cross-checked against the decompiled 1.21.11/26.3
`KineticWeapon`, `PiercingWeapon`, `AttackRange` and spear item components, and the animation
against `SpearAnimations`, `FirstPersonHandsAndItemsRenderer`, `ItemInHandLayer`, `HumanoidModel`,
`Ease` and `Mth`, plus `Minecraft#startAttack` and `ItemInHandRenderer#tick` for the attack gate and
the first person hand height.

