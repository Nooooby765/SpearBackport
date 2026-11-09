# Spear Backport (长矛反向移植)

Faithfully backports the **Minecraft 1.21.11 Spear** to **Minecraft 1.12.2 Forge**.

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
  is published around `RenderPlayerEvent.Pre/Post` (seven static writes per rendered player: four to
  save and publish, three to restore) and re-validated against the hand and the item before use.

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
original assets and fully reproducible — no game audio is used. Every voice is a
sum of physically motivated layers, all rendered at 48 kHz mono:

- **Air / whoosh** — white noise through an STFT time-varying band mask. The band *sweeps* during
  the sample (a fast rise to a bright centre for a thrust, a downward drift for a hit), and a
  sparse impulse train convolves the noise with an exponential kernel to give the grainy flutter
  of real airflow instead of flat hiss.
- **Material layer** — the two families are built differently, because an impact on wood and an
  impact on metal are different physical events:
  - **Metal (every non-wooden tier)**: a very short broadband burst (10-30 ms, high-frequency
    emphasised) *drives* a handful of damped second-order resonators. A mode is (frequency, Q),
    its bandwidth is `f/Q` and its decay `τ = Q/(πf)`; at Q = 9-16 the resonators colour the
    crack (bandwidth of tens to a couple of hundred Hz, gone in ~3-10 ms) instead of adding a
    pitch. Free-running high-Q sines are never used — that is what makes an impact sound like a
    struck glass jar. A little granular "foil" noise and a delayed low thump complete the clank.
  - **Wood**: a sparse crackle train (splintering ticks at 0.4/0.7/1.1 kHz, ~10-25 ms) driven by
    the contact transient, plus friction noise. Dry and dead, with no ring.
- **Low impact** — a downward-gliding sine (e.g. 92 Hz → 50 Hz for a metal hit, 90 → 52 Hz for
  wood) on a delay of 6-8 ms for metal so the transient leads, so a hit reads as weight rather
  than a click.
- **Tail** — the master stage trims the decay at −40 dB relative to peak and appends only 20 ms,
  so a file never occupies more timeline than it sounds.

The metal voices therefore finish in 0.14–0.21 s: the jab is 0.14–0.17 s and the impact 0.20–0.21 s,
i.e. **shorter than the 200-250 ms limit**, and every resonance is already below −30 dB long before
that. The three variants inside one event differ in resonator frequencies, band sweep, timing
offset and pitch (low / mid / high flavoured), so a jab-along-the-row never replays the same file.

Wooden spears stay dry and splintery (spectral centroid ≈ 2.3 kHz for attack, 3.0–4.0 kHz for hit);
the shared metal set sits at ≈ 4.0–4.9 kHz for the thrust and ≈ 6.2–6.5 kHz for the impact, with a
broadband slope rather than a peak. `attack`, `hit`, `use` and `lunge` all belong to the metal
family; only the seven `wood/` samples use the wooden voice.

Levels are matched on K-weighted loudness of the audible region (a linear measure, unlike the
gated BS.1770 integration, which would drop a short sample's quiet tail and bias the normaliser),
then a soft-knee limiter holds the peak below 0.85–0.90. Targets are −19.3 dB (attack), −18.3 dB
(hit), −28.3 dB (use) and −21.0 dB (lunge), which keeps `attack` + `hit` (played together on a
connect) and `lunge` (played on top of both) from ever clipping or stacking into a shout.

Regenerate and validate with:

```
python tools/make_spear_sounds.py <output-dir>
python tools/make_spear_sounds.py --verify <output-dir>
```

The script prints seconds, peak, RMS, K-loudness, integrated LUFS, spectral centroid and crest
factor for every file it writes, and `--verify` re-decodes each Ogg and fails on a wrong sample
rate, more than one channel, clipping, DC offset, a missing target, or residual head/tail silence.

`--verify` also reports a **tonality** column pair (`tonal ms`, `share`, `tail250`) computed from a
21 ms STFT: `share` is the fraction of the 200 Hz-12 kHz power that sits in the single strongest
bin of a frame. Broadband noise spreads that over hundreds of bins (share ≈ 0.05-0.10); a struck
sine piles it into one (share > 0.3), which is the objective signature of the "glass jar" ring.
For the metal `attack` and `hit` voices the build **fails** if a narrowband component is still
audible after 200 ms, if the strongest post-transient bin holds more than 25 % of the band power,
or if any bin after 250 ms holds more than 25 %. The wooden samples are reported but not gated,
because their short splinter ticks are intentional.

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
nothing at all. The only per-tick work is one client-tick anchor (one null check and two field
reads); there is no server tick handler and no polling loop.

Always-on work, in full:

| Hook | Cost when nothing is happening |
|------|--------------------------------|
| `TickEvent.ClientTickEvent` (START) | a phase check, a player null check and five field reads per client tick (re-anchors the cooldown clock) |
| `RenderPlayerEvent.Pre/Post` | seven static writes per rendered player per frame: four to save and publish, three to restore (see above) |
| `RenderSpecificHandEvent` | two field reads and one `instanceof` per rendered hand per frame; a held spear adds about a dozen field reads and four compares, and only takes the hand over when the swing must be hidden or the meter is refilling, so an idle spear is drawn by vanilla itself |
| `InputUpdateEvent` | three field reads per client tick |
| `Item.onEntitySwing` | one `instanceof`, one `isUser()` and one int compare per swing |
| `AttackEntityEvent` / `LeftClickBlock` | only on an actual interaction |

The animation itself only runs while a spear is charging or jabbing:

- Display pairs are cached per transform type. GUI, ground, fixed, head and every non-hand transform
  return the cached pair directly, as does every idle frame.
- `SpearUseParams.isNeutral()` short-circuits the two states where the pose is the identity (before
  the rise starts and after the spear settles back), so the long tail of a charge allocates nothing
  and does no matrix work.
- The pose builder writes into a single reusable `Matrix4f`; the rotations are inlined on the raw
  fields, so there are no temporary matrices. The charge parameters and the returned `Pair` are
  reused too, so an animated frame allocates nothing at all.
- `SpearItem` builds its attribute modifiers once in the constructor instead of per tooltip frame.
- The charge tick leaves before sampling motion or building a look vector until the activation delay
  has passed, and again once the charge expires, so a held right click costs one comparison per tick
  in those two windows.

The reused pose, parameters and pair are safe because Forge's `handleCameraTransforms` copies the
matrix into GL and reads the model before `handlePerspective` returns, so the returned pair never
outlives the call. That assumption is what the zero-allocation frame rests on.

The remaining trade-off is kept because it would not be free:

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

Output: `build/libs/spearbackport-<mod_version>.jar`

The sound assets are committed, so building does not need Python. To regenerate them:

```powershell
python tools/make_spear_sounds.py src/main/resources/assets/spearbackport/sounds/item/spear
```

Requires `numpy` and `soundfile` (which bundles a libsndfile with Vorbis support). The script prints
the measured loudness and spectral centroid of each file so the result can be checked against the
targets in its docstring.

## Credits

Mojang for the original 1.21.11 spear design and data. Textures are the user's old-style
16×16 and 32×32 redraws from `自绘旧版风格长矛材质/`; they are
not copied vanilla assets. The sound set is synthesised from scratch by `tools/make_spear_sounds.py`
and contains no game audio; only its duration, loudness and spectral targets were measured from the
official samples. The combat values were cross-checked against the decompiled 1.21.11/26.3
`KineticWeapon`, `PiercingWeapon`, `AttackRange` and spear item components, and the animation
against `SpearAnimations`, `FirstPersonHandsAndItemsRenderer`, `ItemInHandLayer`, `HumanoidModel`,
`Ease` and `Mth`, plus `Minecraft#startAttack` and `ItemInHandRenderer#tick` for the attack gate and
the first person hand height.

