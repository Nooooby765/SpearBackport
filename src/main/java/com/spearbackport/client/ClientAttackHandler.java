package com.spearbackport.client;

import com.spearbackport.SpearBackportMod;
import com.spearbackport.item.SpearItem;
import com.spearbackport.network.MessageSpearJab;
import com.spearbackport.network.SpearNetwork;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
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
 */
@Mod.EventBusSubscriber(modid = SpearBackportMod.MOD_ID, value = Side.CLIENT)
public final class ClientAttackHandler {

    /** Tick of the last jab, used to mirror the forced 100% attack charge. */
    private static int lastAttackTick = -1000;

    private ClientAttackHandler() {}

    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        tryAttack(event.getEntityPlayer());
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        EntityPlayer player = event.getEntityPlayer();
        if (!(player.getHeldItemMainhand().getItem() instanceof SpearItem)) {
            return;
        }

        // No block breaking and no block interaction: the click becomes a jab.
        event.setCanceled(true);
        tryAttack(player);
    }

    /**
     * Vanilla entity clicks reset the attack meter. Remember them so the manual
     * air/block path cannot fire a second jab before the cooldown is ready.
     */
    @SubscribeEvent(receiveCanceled = true)
    public static void onAttackEntity(AttackEntityEvent event) {
        EntityPlayer player = event.getEntityPlayer();
        if (player.world.isRemote && player.getHeldItemMainhand().getItem() instanceof SpearItem) {
            lastAttackTick = player.ticksExisted;
        }
    }

    private static void tryAttack(EntityPlayer player) {
        if (!(player.getHeldItemMainhand().getItem() instanceof SpearItem)) {
            return;
        }
        if (player.isSpectator() || !ready(player)) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.getConnection() == null) {
            return;
        }

        lastAttackTick = player.ticksExisted;
        SpearNetwork.CHANNEL.sendToServer(new MessageSpearJab());
        player.resetCooldown();
    }

    /** True once the spear's forced 100% jab cooldown has elapsed. */
    private static boolean ready(EntityPlayer player) {
        double speed = player.getEntityAttribute(SharedMonsterAttributes.ATTACK_SPEED).getAttributeValue();
        int period = speed <= 0.0D ? 20 : (int) Math.ceil(20.0D / speed);
        return player.ticksExisted - lastAttackTick >= period;
    }
}
