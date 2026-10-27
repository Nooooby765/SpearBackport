package com.spearbackport.client;

import com.spearbackport.SpearBackportMod;
import com.spearbackport.item.SpearItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
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
 *   <li>{@link RenderSpecificHandEvent} re-submits the first person spear hand.
 *       Two things need correcting there, and both come straight from the
 *       1.21.11 renderer and item components.</li>
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
        // A jab must not keep the vanilla WHACK. 1.12.2 has no per-item swing
        // type, so the hand is already rotated by up to -80 degrees before the
        // item transform is consulted; submitting zero swing progress is the
        // neutral hand frame the 1.21.11 stab pose is written for.
        float swing = player.swingingHand == hand
                && (player.isSwingInProgress || event.getSwingProgress() > 0.0F)
                ? 0.0F : event.getSwingProgress();

        event.setCanceled(true);
        minecraft.getItemRenderer().renderItemInFirstPerson(player, event.getPartialTicks(),
                event.getInterpolatedPitch(), hand, swing, event.getItemStack(),
                armHeight(player, hand, event.getEquipProgress()));
    }

    /**
     * Removes the attack-cooldown part of the vanilla hand height, which a spear
     * must not have.
     *
     * <p>1.12.2 moves the first person item down by
     * {@code 1 - equippedProgress} blocks and refills {@code equippedProgress}
     * towards {@code cooledAttackStrength³}, which is the "tool slowly rises
     * while the attack indicator recharges" motion. 1.21.11 does the same in
     * {@code ItemInHandRenderer.tick()}, but its stab goes through
     * {@code PiercingWeapon#attack}, which calls
     * {@code swingAndResetAttackStrength(..., resetAttackStrength = false)}: a
     * spear jab never resets the attack meter, so the spear never sees that
     * motion. Swords and axes do reset it, which is why they still bob.
     *
     * <p>Subtracting the attack term leaves only the item-swap animation, so
     * switching to a spear still plays vanilla's equip dip.
     */
    private static float armHeight(EntityPlayer player, EnumHand hand, float inverseArmHeight) {
        if (hand != EnumHand.MAIN_HAND) {
            // Vanilla drives the off hand towards a constant 1.0, so it has no
            // attack-cooldown component to remove.
            return inverseArmHeight;
        }
        float charge = player.getCooledAttackStrength(1.0F);
        float recover = 1.0F - charge * charge * charge;
        return Math.max(0.0F, inverseArmHeight - recover);
    }
}
