package com.spearbackport.client;

import com.spearbackport.SpearBackportMod;
import com.spearbackport.combat.SpearCombat;
import com.spearbackport.combat.SpearJabState;
import com.spearbackport.item.SpearItem;
import com.spearbackport.network.MessageSpearJab;
import com.spearbackport.network.SpearNetwork;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Every left click sends a {@link MessageSpearJab} because 1.12.2 only attacks
 * what its three-block picker found; entity clicks ride vanilla's packet into the
 * same routine, so a click never attacks twice. Both paths record the swing
 * decision in {@link SpearJabState}.
 */
@Mod.EventBusSubscriber(modid = SpearBackportMod.MOD_ID, value = Side.CLIENT)
public final class ClientAttackHandler {

    /** Tick of the last jab; {@code -1000} predates any tick so the first jab is always ready. */
    private static int lastAttackTick = -1000;

    private ClientAttackHandler() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player != null) {
            syncCooldown(player);
        }
    }

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
     * Vanilla sends the entity packet itself, so this only mirrors the swing
     * decision and blocks a second jab from the air/block path.
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

    private static void accept(EntityPlayer player) {
        lastAttackTick = player.ticksExisted;
        player.resetCooldown();
        SpearJabState.approve(player.ticksExisted);
    }

    /**
     * Must not read {@code ticksSinceLastSwing}: {@code Minecraft#clickMouse} calls
     * {@code resetCooldown()} at L1641 before firing {@code LeftClickEmpty} at
     * L1642, so an air click would be refused forever. The anchor is the same
     * counter sampled at tick start.
     */
    private static boolean ready(EntityPlayer player) {
        return player.ticksExisted - lastAttackTick >= SpearCombat.cooldownTicks(player);
    }

    /**
     * {@code Phase.START} runs before the mouse section of {@code Minecraft#runTick}
     * (L1924-1936) and before the entity update (L1974), so a click sees the
     * previous tick's state; the item-swap meter wipe at L318 therefore lands first.
     */
    public static void syncCooldown(EntityPlayer player) {
        if (player.ticksExisted - lastAttackTick != player.ticksSinceLastSwing) {
            lastAttackTick = player.ticksExisted - player.ticksSinceLastSwing;
        }
    }

    /**
     * Restores the meter {@code clickMouse} emptied at L1641 for a refused click,
     * using the AT-opened {@code EntityLivingBase.ticksSinceLastSwing}.
     */
    private static void restoreMeter(EntityPlayer player) {
        EntityLivingBase base = player;
        if (base.ticksSinceLastSwing != 0) {
            // Only a refused click leaves the meter at 0.
            return;
        }
        int elapsed = player.ticksExisted - lastAttackTick;
        base.ticksSinceLastSwing = Math.max(0, Math.min(elapsed, SpearCombat.cooldownTicks(player)));
    }

    private static boolean isSpear(EntityPlayer player) {
        ItemStack held = player.getHeldItemMainhand();
        return held.getItem() instanceof SpearItem;
    }
}
