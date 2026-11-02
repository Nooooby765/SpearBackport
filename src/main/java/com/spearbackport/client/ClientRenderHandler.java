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
 * The two client render hooks the item animation needs. Neither does any work
 * per tick; both only run while something is actually being drawn.
 *
 * <ul>
 *   <li>{@link RenderPlayerEvent} publishes the player being drawn so third
 *       person items can read their charge and stab state (see
 *       {@link SpearRenderContext}).</li>
 *   <li>{@link RenderSpecificHandEvent} re-submits the first person spear hand,
 *       but only on the frames where it would actually differ from vanilla.
 *       Idle frames cost one {@code instanceof} and one int compare.</li>
 * </ul>
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
        // A jab must not keep the vanilla WHACK. 1.12.2 has no per-item swing
        // type, so the hand is already rotated by up to -80 degrees before the
        // item transform is consulted; submitting zero swing progress is the
        // neutral hand frame the 1.21.11 stab pose is written for.
        boolean jab = player.swingingHand == hand
                && (player.isSwingInProgress || event.getSwingProgress() > 0.0F);
        // Vanilla only lowers the main hand for the attack meter; the off hand
        // always recovers to a constant height. A spear must not be lowered for
        // the meter at all, so while vanilla would still be raising it, the hand
        // is submitted at full height instead.
        //   ItemRenderer#updateEquippedItem moves the hand towards f³ by at most
        // ±0.4 per frame, and the largest single step is the last one (0.214 for
        // the fastest tier). That step lands on 1.0 at ticksSinceLastSwing ==
        // cooldownTicks - 1, where only the first frame of the tick still shows
        // the interpolation; afterwards vanilla already draws at full height.
        // Cutting the window one tick shorter let vanilla draw that last 0.214
        // dip itself — the one-frame flicker when the meter filled.
        //   The window ends one tick past the step (<=). Vanilla reads exactly
        // 0.0F on that tick, so both branches submit the same transform; the
        // extra tick only matters when a single frame straddles the boundary
        // (a hitch below 20 fps), where the tick counter has already jumped past
        // the step while the frame still interpolates it.
        boolean cooldownDriven = hand == EnumHand.MAIN_HAND && equip > 0.0F
                && player.ticksSinceLastSwing <= SpearCombat.cooldownTicks(player);
        // 1.21.11 keeps the using spear at a constant height: its SPEAR branch
        // never applies inverseArmHeight (FirstPersonHandsAndItemsRenderer L447
        // uses the literal -0.52). 1.12.2 resets the equip progress on every
        // successful right click (Minecraft#rightClickMouse L1714), so the hand
        // sinks for the three ticks it takes to lerp back, right as the charge
        // starts. Submitting equip = 0 for the whole use keeps the constant
        // height instead of trading it for that dip.
        boolean charging = player.getItemInUseCount() > 0
                && player.getActiveItemStack().getItem() instanceof SpearItem
                && player.getActiveHand() == hand;
        if (!jab && !cooldownDriven && !charging) {
            // Vanilla would draw exactly this frame: do not take it over.
            return;
        }

        event.setCanceled(true);
        minecraft.getItemRenderer().renderItemInFirstPerson(player, event.getPartialTicks(),
                event.getInterpolatedPitch(), hand, jab ? 0.0F : event.getSwingProgress(),
                event.getItemStack(), (cooldownDriven || charging) ? 0.0F : equip);
    }
}
