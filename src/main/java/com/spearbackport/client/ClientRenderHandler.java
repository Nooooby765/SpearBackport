package com.spearbackport.client;

import com.spearbackport.SpearBackportMod;
import com.spearbackport.combat.SpearCombat;
import com.spearbackport.item.SpearItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.EnumHand;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.RenderSpecificHandEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Publishes the drawn player ({@link RenderPlayerEvent}) and re-submits the first
 * person spear hand on frames where vanilla would differ.
 */
@Mod.EventBusSubscriber(modid = SpearBackportMod.MOD_ID, value = Side.CLIENT)
public final class ClientRenderHandler {

    private ClientRenderHandler() {}

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        SpearRenderContext.begin(event.getEntityPlayer(), event.getPartialRenderTick());
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        SpearRenderContext.end();
    }

    @SubscribeEvent
    public static void onRenderSpecificHand(RenderSpecificHandEvent event) {
        if (!(event.getItemStack().getItem() instanceof SpearItem)) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayerSP player = minecraft.player;
        if (player == null) {
            return;
        }

        EnumHand hand = event.getHand();
        float equip = event.getEquipProgress();
        // 1.12.2 has no per-item swing type and rotates the hand by up to -80
        // degrees, so a stab must submit zero swing progress.
        boolean jab = player.swingingHand == hand
                && (player.isSwingInProgress || event.getSwingProgress() > 0.0F);
        // Vanilla lowers only the main hand, towards f³ (ItemRenderer L634), so
        // take over while it is still raising; the extra tick (<=) is harmless
        // because vanilla reads exactly 0.0F there (L341).
        boolean cooldownDriven = hand == EnumHand.MAIN_HAND && equip > 0.0F
                && player.ticksSinceLastSwing <= SpearCombat.cooldownTicks(player);
        // 1.21.11 holds the spear at a constant height (renderer L447, literal
        // -0.52); 1.12.2 resets equip progress per right click (L1714), so pass 0.
        boolean charging = player.getItemInUseCount() > 0
                && player.getActiveItemStack().getItem() instanceof SpearItem
                && player.getActiveHand() == hand;
        if (!jab && !cooldownDriven && !charging) {
            return;
        }

        event.setCanceled(true);
        minecraft.getItemRenderer().renderItemInFirstPerson(player, event.getPartialTicks(),
                event.getInterpolatedPitch(), hand, jab ? 0.0F : event.getSwingProgress(),
                event.getItemStack(), (cooldownDriven || charging) ? 0.0F : equip);
    }
}
