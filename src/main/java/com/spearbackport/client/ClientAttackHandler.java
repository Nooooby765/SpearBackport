package com.spearbackport.client;

import com.spearbackport.SpearBackportMod;
import com.spearbackport.combat.SpearJabState;
import com.spearbackport.item.SpearItem;
import com.spearbackport.network.MessageSpearJab;
import com.spearbackport.network.SpearNetwork;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Client-side jab plumbing.
 *
 * <p>Vanilla 1.12.2 only sends {@code CPacketUseEntity} when its three-block
 * picker found an entity. A spear must be able to jab empty air (to trigger
 * Lunge), blocks, and targets up to 4.5 blocks away. Every left click with a
 * spear therefore sends one tiny {@link MessageSpearJab}; the server owns the
 * ray test, the range check, the multi-target damage and the cooldown.
 *
 * <p>Entity clicks still go through vanilla's own attack packet. That packet
 * triggers {@code AttackEntityEvent} on the server, which runs the same jab
 * routine, so a click never produces two attacks.
 *
 * <p>Both paths also decide whether the swing that vanilla is about to play
 * belongs to a real jab (see {@link SpearJabState}); a refused click must not
 * animate anything, which is what 1.21.11 does with
 * {@code MINIMUM_ATTACK_CHARGE}.
 */
@Mod.EventBusSubscriber(modid = SpearBackportMod.MOD_ID, value = Side.CLIENT)
public final class ClientAttackHandler {

    /** Tick of the last jab, used as the spear's own attack cooldown. */
    private static int lastAttackTick = -1000;

    private ClientAttackHandler() {}

    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        tryAttack(event.getEntityPlayer());
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        EntityPlayer player = event.getEntityPlayer();
        if (!isSpear(player)) {
            return;
        }

        // No block breaking and no block interaction: the click becomes a jab.
        event.setCanceled(true);
        tryAttack(player);
    }

    /**
     * Vanilla sends the entity attack packet itself, so this only mirrors the
     * decision for the swing. It also keeps the manual air/block path from
     * firing a second jab before the cooldown is ready.
     */
    @SubscribeEvent(receiveCanceled = true)
    public static void onAttackEntity(AttackEntityEvent event) {
        EntityPlayer player = event.getEntityPlayer();
        if (!player.world.isRemote || !isSpear(player)) {
            return;
        }
        if (player.isSpectator() || !ready(player)) {
            SpearJabState.refuse();
            restoreMeter(player);
            return;
        }
        accept(player);
    }

    private static void tryAttack(EntityPlayer player) {
        if (!isSpear(player)) {
            return;
        }
        if (player.isSpectator() || !ready(player)) {
            SpearJabState.refuse();
            restoreMeter(player);
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.getConnection() == null) {
            SpearJabState.refuse();
            restoreMeter(player);
            return;
        }

        accept(player);
        SpearNetwork.CHANNEL.sendToServer(new MessageSpearJab());
    }

    /** The click became a jab: let it swing and start the cooldown. */
    private static void accept(EntityPlayer player) {
        lastAttackTick = player.ticksExisted;
        player.resetCooldown();
        SpearJabState.approve(player.ticksExisted);
    }

    /**
     * The spear's own attack cooldown: its attack speed, which is also the length
     * of the 1.21.11 {@code SwingAnimation(STAB, attackDuration * 20)}.
     */
    private static int period(EntityPlayer player) {
        double speed = player.getEntityAttribute(SharedMonsterAttributes.ATTACK_SPEED).getAttributeValue();
        return speed <= 0.0D ? 20 : (int) Math.round(20.0D / speed);
    }

    private static boolean ready(EntityPlayer player) {
        return player.ticksExisted - lastAttackTick >= period(player);
    }

    /**
     * Undoes the attack meter that 1.12.2 emptied for a click that did nothing.
     *
     * <p>{@code Minecraft#clickMouse} calls {@code Player#resetCooldown()}
     * unconditionally for a click at empty air, before the item is even
     * consulted, so the HUD bar would restart as if the spear had swung. A spear
     * jab must not cost anything, so the value is put back — from
     * {@link #lastAttackTick}, which is the authority, so the bar lands exactly
     * where it was instead of jumping.
     *
     * <p>The write needs {@code EntityLivingBase.ticksSinceLastSwing}, which has
     * no public setter; {@code spearbackport_at.cfg} opens it up.
     */
    private static void restoreMeter(EntityPlayer player) {
        EntityLivingBase base = player;
        if (base.ticksSinceLastSwing != 0) {
            // Nothing was consumed by this click, so there is nothing to restore.
            return;
        }
        int elapsed = player.ticksExisted - lastAttackTick;
        base.ticksSinceLastSwing = Math.max(0, Math.min(elapsed, period(player)));
    }

    private static boolean isSpear(EntityPlayer player) {
        ItemStack held = player.getHeldItemMainhand();
        return held.getItem() instanceof SpearItem;
    }
}
