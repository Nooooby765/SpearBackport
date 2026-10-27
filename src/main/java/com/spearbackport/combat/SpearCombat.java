package com.spearbackport.combat;

import com.google.common.base.Predicate;
import com.spearbackport.SpearBackportMod;
import com.spearbackport.init.ModEnchantments;
import com.spearbackport.init.ModSounds;
import com.spearbackport.item.SpearItem;
import com.spearbackport.item.SpearMaterial;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IEntityMultiPart;
import net.minecraft.entity.MultiPartEntityPart;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.StatList;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * All spear combat logic.
 *
 * <p>The behaviour mirrors the decompiled 1.21.11/26.3 spear components:
 * <ul>
 *   <li>jab damage is the player's {@code ATTACK_DAMAGE} plus Sharpness-family
 *       enchantments, the jab has a forced 100% charge and no crit/sweep, and it
 *       applies knockback even when the target takes no damage;</li>
 *   <li>charge damage is {@code base_attack_damage + floor(closing_speed * mult)}
 *       and is independent of the attack cooldown and of Strength;</li>
 *   <li>each charge target has a <b>10 tick contact cooldown</b>, exactly like
 *       {@code KineticWeapon.contactCooldownTicks};</li>
 *   <li>dismount, knockback and damage are three independent conditions, so a
 *       slow contact can still dismount or knock back without hurting;</li>
 *   <li>Lunge is applied by {@code postPiercingAttack}, i.e. on every valid jab,
 *       including a whiff.</li>
 * </ul>
 *
 * <p>There is no global tick loop. The only entry points are an attack event, a
 * block-click check and the item-use tick of a spear that is actually held.
 */
@Mod.EventBusSubscriber(modid = SpearBackportMod.MOD_ID)
public final class SpearCombat {

    /** Blocks closer than this cannot be hit (the spear's dead zone). */
    public static final double MIN_REACH = 2.0D;
    /** Survival attack range. */
    public static final double MAX_REACH = 4.5D;
    /** Creative mode adds two blocks, exactly like other weapons. */
    public static final double CREATIVE_REACH = 6.5D;
    /** Both jab and charge attacks inflate target hitboxes by this much. */
    public static final double HITBOX_MARGIN = 0.125D;

    /** Minimum closing speed (blocks/second) for a charge attack to deal damage. */
    public static final double CHARGE_DAMAGE_MIN_SPEED = 4.6D;
    /** Minimum forward speed (blocks/second) for a charge attack to knock back. */
    public static final double CHARGE_KNOCKBACK_MIN_SPEED = 5.1D;
    /** Ticks between two charge contacts with the same target. */
    public static final int CHARGE_CONTACT_COOLDOWN = 10;
    /** Base charge knockback strength; the Knockback enchantment adds to it. */
    public static final float CHARGE_KNOCKBACK_STRENGTH = 0.4F;
    /** Sanity cap for teleports / broken velocity packets. */
    private static final double MAX_TRACKED_SPEED = 100.0D;

    /** Lunge impulse per level, in blocks per tick (1.21.11 data). */
    private static final double LUNGE_IMPULSE = 0.458D;

    /**
     * Server-side attack grace, matching the five-tick tolerance used by
     * {@code ServerGamePacketListenerImpl} for the STAB packet.
     */
    private static final float SERVER_ATTACK_TOLERANCE = 5.0F;

    private static final Predicate<Entity> TARGET_FILTER = new Predicate<Entity>() {
        @Override
        public boolean apply(@Nullable Entity entity) {
            return entity != null && !entity.isDead && entity.canBeCollidedWith();
        }
    };

    /**
     * Last sampled position (and, for the attacker, contact timestamps) per
     * entity. Weak keys and lazy contact maps keep this tiny: entries only exist
     * while a spear is actually being used.
     */
    private static final Map<Entity, Motion> MOTIONS = new WeakHashMap<>();

    private SpearCombat() {}

    // ------------------------------------------------------------------
    // Jab attack (left click)
    // ------------------------------------------------------------------

    /**
     * Entity clicks still travel through vanilla's {@code CPacketUseEntity}, so
     * this event is the server-side entry point for a jab that the client
     * actually aimed at an entity. The event is cancelled on both sides: the
     * client must not predict crits, sweep or durability.
     */
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        EntityPlayer player = event.getEntityPlayer();
        ItemStack stack = player.getHeldItemMainhand();
        if (!(stack.getItem() instanceof SpearItem)) {
            return;
        }

        event.setCanceled(true);
        if (player.world.isRemote || player.isSpectator()) {
            return;
        }

        // An early click must not reset the meter: 1.21.11 simply refuses the
        // attack and lets the existing charge continue.
        if (!isAttackReady(player)) {
            return;
        }
        player.resetCooldown();
        performJab(player, stack);
    }

    /**
     * Entry point for the lightweight client packet. It covers empty air and
     * blocks (where vanilla never fires an entity attack) and targets 3..4.5
     * blocks away (where vanilla's picker cannot reach).
     */
    public static void handleJabPacket(EntityPlayer player) {
        if (player.world.isRemote || player.isSpectator()) {
            return;
        }
        ItemStack stack = player.getHeldItemMainhand();
        if (!(stack.getItem() instanceof SpearItem)) {
            return;
        }
        if (!isAttackReady(player)) {
            return;
        }
        player.resetCooldown();
        performJab(player, stack);
    }

    private static boolean isAttackReady(EntityPlayer player) {
        return player.getCooledAttackStrength(SERVER_ATTACK_TOLERANCE) >= 1.0F;
    }

    /**
     * One jab. Hits every target along the spear ray, consumes one durability
     * and 0.1 exhaustion per affected target, then runs the Lunge effect even if
     * nothing was hit (post_piercing_attack).
     */
    private static void performJab(EntityPlayer player, ItemStack stack) {
        DamageSource source = DamageSource.causePlayerDamage(player);
        List<Entity> targets = collectTargets(player, 0.0D);
        SpearMaterial tier = ((SpearItem) stack.getItem()).getTier();
        float baseDamage = (float) player.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getAttributeValue();
        int fireAspect = EnchantmentHelper.getFireAspectModifier(player);
        int knockback = EnchantmentHelper.getKnockbackModifier(player);
        float yaw = player.rotationYaw * 0.017453292F;
        double knockX = MathHelper.sin(yaw);
        double knockZ = -MathHelper.cos(yaw);
        int lungeLevel = lungeLevel(stack);

        boolean hit = false;
        for (Entity target : targets) {
            EntityLivingBase living = asLiving(target);
            if (living == null || !living.canBeAttackedWithItem()) {
                continue;
            }

            float damage = baseDamage
                    + EnchantmentHelper.getModifierForCreature(stack, living.getCreatureAttribute());
            float healthBefore = living.getHealth();
            boolean hurt = living.attackEntityFrom(source, damage);

            // PiercingWeapon(deals_knockback = true): even a blocked or
            // invulnerable target is pushed back.
            if (hurt) {
                if (knockback > 0) {
                    living.knockBack(player, knockback * 0.5F, knockX, knockZ);
                }
            } else {
                living.knockBack(player, CHARGE_KNOCKBACK_STRENGTH + knockback * 0.5F, knockX, knockZ);
            }

            if (hurt) {
                applyOnHitEffects(player, living, healthBefore, fireAspect);
            } else {
                player.setLastAttackedEntity(living);
            }

            // Weapon(1): one durability per affected target, not per swing.
            stack.damageItem(1, player);
            player.addExhaustion(0.1F);
            hit = true;

            if (stack.isEmpty()) {
                break;
            }
        }

        // PiercingWeapon.attack: the hit sound first, then the thrust whoosh, so
        // that a jab that connects always carries both. A whiff still plays the
        // whoosh, just like 1.21.11.
        if (hit) {
            play(player, ModSounds.hit(tier), 1.0F, 1.0F);
        }
        play(player, ModSounds.attack(tier), 1.0F, 1.0F);

        // postPiercingAttack is unconditional: Lunge fires on a whiff too.
        if (player.isEntityAlive()) {
            applyLunge(player, stack, lungeLevel);
        }
    }

    // ------------------------------------------------------------------
    // Charge attack (right hold)
    // ------------------------------------------------------------------

    public static void tickCharge(ItemStack stack, EntityPlayer player, int elapsed) {
        if (player.world.isRemote || player.isSpectator()) {
            return;
        }
        if (!(stack.getItem() instanceof SpearItem)) {
            return;
        }

        SpearMaterial tier = ((SpearItem) stack.getItem()).getTier();
        if (elapsed >= tier.expiresAt) {
            return;
        }

        int now = player.ticksExisted;
        Vec3d look = player.getLookVec();
        Motion self = motion(player, now);
        double selfSpeed = clampSpeed(self.vx * look.x + self.vy * look.y + self.vz * look.z);

        int used = elapsed - tier.activationDelay;
        if (used < 0) {
            return;
        }

        // getHitEntitiesAlong adds the attacker's forward movement to max reach.
        double extraReach = Math.max(0.0D, selfSpeed / 20.0D);
        List<Entity> targets = collectTargets(player, extraReach);
        if (targets.isEmpty()) {
            return;
        }

        DamageSource source = DamageSource.causePlayerDamage(player);
        float baseDamage = (float) player.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getBaseValue();
        int fireAspect = EnchantmentHelper.getFireAspectModifier(player);
        int knockback = EnchantmentHelper.getKnockbackModifier(player);
        float yaw = player.rotationYaw * 0.017453292F;
        double knockX = MathHelper.sin(yaw);
        double knockZ = -MathHelper.cos(yaw);
        int totalTicks = tier.engagedDuration + tier.tiredDuration + tier.disengagedDuration;
        boolean hit = false;

        for (Entity target : targets) {
            EntityLivingBase living = asLiving(target);
            if (living == null || !living.canBeAttackedWithItem()) {
                continue;
            }

            // Sample the target every tick so the velocity stays fresh even
            // while its contact cooldown is running.
            Motion other = motion(living, now);
            if (!self.markContact(target, now)) {
                continue;
            }

            double targetSpeed = other.vx * look.x + other.vy * look.y + other.vz * look.z;
            double closingSpeed = clampSpeed(Math.max(0.0D, selfSpeed - targetSpeed));

            boolean dismount = used <= tier.engagedDuration
                    && selfSpeed >= tier.dismountSpeed;
            boolean knock = used <= tier.engagedDuration + tier.tiredDuration
                    && selfSpeed >= CHARGE_KNOCKBACK_MIN_SPEED;
            boolean damage = used <= totalTicks
                    && closingSpeed >= CHARGE_DAMAGE_MIN_SPEED;

            if (!dismount && !knock && !damage) {
                continue;
            }

            boolean hurt = false;
            if (damage) {
                float amount = baseDamage
                        + (float) Math.floor(closingSpeed * tier.chargeMultiplier)
                        + EnchantmentHelper.getModifierForCreature(stack, living.getCreatureAttribute());
                float healthBefore = living.getHealth();
                hurt = living.attackEntityFrom(source, amount);
                if (hurt) {
                    applyOnHitEffects(player, living, healthBefore, fireAspect);
                }
            }

            boolean affected = hurt || knock || (dismount && living.isRiding());
            if (!affected) {
                continue;
            }

            if (knock) {
                if (hurt) {
                    if (knockback > 0) {
                        living.knockBack(player, knockback * 0.5F, knockX, knockZ);
                    }
                } else {
                    living.knockBack(player, CHARGE_KNOCKBACK_STRENGTH + knockback * 0.5F, knockX, knockZ);
                }
            }
            if (dismount) {
                living.dismountRidingEntity();
            }
            if (!hurt) {
                player.setLastAttackedEntity(living);
            }

            stack.damageItem(1, player);
            player.addExhaustion(0.1F);
            hit = true;

            if (stack.isEmpty()) {
                break;
            }
        }

        if (hit) {
            play(player, ModSounds.hit(tier), 1.0F, 1.0F);
        }
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    /**
     * Every living entity hit by the spear ray between {@link #MIN_REACH} and
     * the current maximum reach plus {@code extraReach}. Only blocks with a
     * collision box stop the ray, so cobwebs and tall grass can be pierced.
     */
    public static List<Entity> collectTargets(EntityPlayer player, double extraReach) {
        double reach = (player.capabilities.isCreativeMode ? CREATIVE_REACH : MAX_REACH) + extraReach;
        Vec3d eye = player.getPositionEyes(1.0F);
        Vec3d look = player.getLookVec();
        Vec3d end = new Vec3d(eye.x + look.x * reach, eye.y + look.y * reach, eye.z + look.z * reach);

        RayTraceResult blockHit = player.world.rayTraceBlocks(eye, end, false, true, false);
        if (blockHit != null && blockHit.typeOfHit == RayTraceResult.Type.BLOCK) {
            end = blockHit.hitVec;
            reach = eye.distanceTo(end);
        }
        if (reach < MIN_REACH) {
            return Collections.emptyList();
        }

        AxisAlignedBB searchBox = new AxisAlignedBB(
                Math.min(eye.x, end.x) - 1.0D, Math.min(eye.y, end.y) - 1.0D, Math.min(eye.z, end.z) - 1.0D,
                Math.max(eye.x, end.x) + 1.0D, Math.max(eye.y, end.y) + 1.0D, Math.max(eye.z, end.z) + 1.0D);

        List<Entity> candidates = player.world.getEntitiesInAABBexcluding(player, searchBox, TARGET_FILTER);
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        List<Entity> result = new ArrayList<>(candidates.size());
        Entity lowestRiding = player.getLowestRidingEntity();
        for (Entity candidate : candidates) {
            if (candidate == player || candidate.getLowestRidingEntity() == lowestRiding) {
                continue;
            }

            AxisAlignedBB bounds = candidate.getEntityBoundingBox().grow(HITBOX_MARGIN);
            RayTraceResult hit = bounds.calculateIntercept(eye, end);
            if (hit == null) {
                continue;
            }

            double distance = eye.distanceTo(hit.hitVec);
            if (distance < MIN_REACH || distance > reach) {
                continue;
            }

            Entity target = resolveTarget(candidate);
            if (target != null && target != player && !result.contains(target)) {
                result.add(target);
            }
        }
        return result;
    }

    /**
     * Maps the ender dragon's multi-part hitboxes back to the damageable parent.
     */
    @Nullable
    private static Entity resolveTarget(Entity entity) {
        if (entity instanceof EntityLivingBase) {
            return entity;
        }
        if (entity instanceof MultiPartEntityPart) {
            IEntityMultiPart parent = ((MultiPartEntityPart) entity).parent;
            if (parent instanceof EntityLivingBase) {
                return (EntityLivingBase) parent;
            }
        }
        return null;
    }

    @Nullable
    private static EntityLivingBase asLiving(Entity entity) {
        return entity instanceof EntityLivingBase ? (EntityLivingBase) entity : null;
    }

    /** Fire Aspect, thorns, Bane slowness, statistics and damage particles. */
    private static void applyOnHitEffects(EntityPlayer player, EntityLivingBase target,
                                          float healthBefore, int fireAspect) {
        if (fireAspect > 0 && !target.isBurning()) {
            target.setFire(fireAspect * 4);
        }

        EnchantmentHelper.applyThornEnchantments(target, player);
        EnchantmentHelper.applyArthropodEnchantments(player, target);
        player.setLastAttackedEntity(target);

        float dealt = healthBefore - target.getHealth();
        if (dealt <= 0.0F) {
            return;
        }

        player.addStat(StatList.DAMAGE_DEALT, Math.round(dealt * 10.0F));
        if (dealt > 2.0F && player.world instanceof WorldServer) {
            ((WorldServer) player.world).spawnParticle(EnumParticleTypes.DAMAGE_INDICATOR,
                    target.posX, target.posY + target.height * 0.5D, target.posZ,
                    (int) (dealt * 0.5F), 0.1D, 0.0D, 0.1D, 0.2D);
        }
    }

    /**
     * Lunge: a horizontal impulse paid for with hunger and one durability.
     * 1.21.11 requires no minimum hunger; that requirement only appears in 26.3.
     */
    private static void applyLunge(EntityPlayer player, ItemStack stack, int level) {
        if (level <= 0 || player.isRiding() || player.isElytraFlying() || player.isInWater()) {
            return;
        }

        double impulse = LUNGE_IMPULSE * level;
        Vec3d look = player.getLookVec();
        player.motionX += look.x * impulse;
        player.motionZ += look.z * impulse;
        player.velocityChanged = true;

        player.addExhaustion(4.0F * level);
        stack.damageItem(1, player);
        play(player, ModSounds.lunge(), 1.0F, 1.0F);
    }

    private static int lungeLevel(ItemStack stack) {
        return ModEnchantments.LUNGE == null ? 0 : EnchantmentHelper.getEnchantmentLevel(ModEnchantments.LUNGE, stack);
    }

    private static void play(EntityPlayer player, SoundEvent sound, float volume, float pitch) {
        player.world.playSound(null, player.posX, player.posY, player.posZ,
                sound, SoundCategory.PLAYERS, volume, pitch);
    }

    private static double clampSpeed(double speed) {
        return Math.min(speed, MAX_TRACKED_SPEED);
    }

    /**
     * Samples an entity once per tick. Velocity is in blocks/second; the
     * attacker's sample also owns the per-target charge contact timestamps.
     */
    private static Motion motion(Entity entity, int tick) {
        Motion motion = MOTIONS.get(entity);
        if (motion == null) {
            motion = new Motion();
            motion.sample(entity, tick, false);
            MOTIONS.put(entity, motion);
            return motion;
        }

        if (motion.tick == tick) {
            return motion;
        }

        motion.sample(entity, tick, tick - motion.tick <= 2);
        return motion;
    }

    /** Mutable one-entity sample; no allocation after the first tick. */
    private static final class Motion {
        double x;
        double y;
        double z;
        double vx;
        double vy;
        double vz;
        int tick;
        /** Target -> last contact tick. Only used by the charging attacker. */
        Map<Entity, Integer> contacts;

        void sample(Entity entity, int tick, boolean measure) {
            if (measure) {
                this.vx = (entity.posX - this.x) * 20.0D;
                this.vy = (entity.posY - this.y) * 20.0D;
                this.vz = (entity.posZ - this.z) * 20.0D;
            } else {
                this.vx = 0.0D;
                this.vy = 0.0D;
                this.vz = 0.0D;
            }
            this.x = entity.posX;
            this.y = entity.posY;
            this.z = entity.posZ;
            this.tick = tick;
        }

        /** Returns false when this target is still cooling down; otherwise marks the contact. */
        boolean markContact(Entity target, int now) {
            if (this.contacts == null) {
                this.contacts = new WeakHashMap<>();
            }
            Integer last = this.contacts.get(target);
            if (last != null && now - last < CHARGE_CONTACT_COOLDOWN) {
                return false;
            }
            this.contacts.put(target, now);
            return true;
        }
    }

    // ------------------------------------------------------------------
    // Block protection: a spear always jabs, never mines
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        EntityPlayer player = event.getEntityPlayer();
        if (player.world.isRemote) {
            return;
        }
        if (player.getHeldItemMainhand().getItem() instanceof SpearItem) {
            event.setCanceled(true);
        }
    }
}
