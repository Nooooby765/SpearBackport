package com.spearbackport.client;

import com.spearbackport.SpearBackportMod;
import com.spearbackport.combat.SpearJabState;
import com.spearbackport.item.SpearItem;
import com.spearbackport.network.MessageSpearJab;
import com.spearbackport.network.SpearNetwork;

import net.minecraft.client.Minecraft;
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
            return;
        }
        lastAttackTick = player.ticksExisted;
        SpearJabState.approve(player.ticksExisted);
    }

    private static void tryAttack(EntityPlayer player) {
        if (!isSpear(player)) {
            return;
        }
        if (player.isSpectator() || !ready(player)) {
            SpearJabState.refuse();
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.getConnection() == null) {
            SpearJabState.refuse();
            return;
        }

        lastAttackTick = player.ticksExisted;
        SpearJabState.approve(player.ticksExisted);
        SpearNetwork.CHANNEL.sendToServer(new MessageSpearJab());
    }

    /**
     * The spear's own attack cooldown.
     *
     * <p>1.21.11 gives the stab a {@code SwingAnimation} whose duration is
     * {@code attackDuration * 20} ticks and never resets the attack strength
     * meter, so the weapon's real cooldown is exactly its attack speed. The
     * vanilla attack meter is deliberately left untouched here: resetting it is
     * what makes 1.12.2 slide the spear down and slowly raise it again while the
     * indicator refills, which a 1.21.11 spear never does.
     */
    private static boolean ready(EntityPlayer player) {
        double speed = player.getEntityAttribute(SharedMonsterAttributes.ATTACK_SPEED).getAttributeValue();
        int period = speed <= 0.0D ? 20 : (int) Math.round(20.0D / speed);
        return player.ticksExisted - lastAttackTick >= period;
    }

    private static boolean isSpear(EntityPlayer player) {
        ItemStack held = player.getHeldItemMainhand();
        return held.getItem() instanceof SpearItem;
    }
}
