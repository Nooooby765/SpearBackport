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
 *   <li>{@link RenderSpecificHandEvent} re-submits the first person spear hand,
 *       but only on the frames where it would actually differ from vanilla.
 *       Idle frames cost one {@code instanceof} and one float compare.</li>
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
        // the meter at all, so when that is the only reason it is down, the hand
        // is submitted at full height instead.
        boolean cooldownDriven = hand == EnumHand.MAIN_HAND && equip > 0.0F
                && meterRefilling(player);
        if (!jab && !cooldownDriven) {
            // Vanilla would draw exactly this frame: do not take it over.
            return;
        }

        event.setCanceled(true);
        minecraft.getItemRenderer().renderItemInFirstPerson(player, event.getPartialTicks(),
                event.getInterpolatedPitch(), hand, jab ? 0.0F : event.getSwingProgress(),
                event.getItemStack(), cooldownDriven ? 0.0F : equip);
    }

    /**
     * True while the vanilla attack meter is refilling, i.e. while 1.12.2 would
     * be lowering and then raising the held item.
     *
     * <p>1.12.2 drives the hand height towards {@code cooledAttackStrength³} and
     * 1.21.11 does the same in {@code ItemInHandRenderer#tick}, but a 1.21.11
     * stab goes through {@code PiercingWeapon#attack}, which calls
     * {@code swingAndResetAttackStrength(..., resetAttackStrength = false)}: a
     * spear never consumes the meter, so its spear never shows that motion.
     *
     * <p>The height is replaced with a constant rather than subtracting the
     * analytic term: {@code equipProgress} is a per-tick value interpolated with
     * partial ticks, while the analytic term is a per-tick step, and their
     * difference wobbles by a fraction of a block — visible as a twitch. A
     * constant is exact, and the only thing lost is the item-swap dip during the
     * few ticks a refill lasts.
     */
    private static boolean meterRefilling(EntityPlayer player) {
        return player.getCooledAttackStrength(1.0F) < 1.0F;
    }
}
