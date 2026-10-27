package com.spearbackport.client;

import com.spearbackport.SpearBackportMod;
import com.spearbackport.item.SpearItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
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
 *   <li>{@link RenderSpecificHandEvent} removes vanilla's horizontal WHACK from
 *       a spear jab. 1.12.2 has no per-item swing type, so the arm and item are
 *       swung like a sword before the item transform is ever consulted; the
 *       item animation would then be added on top of a -80 degree chop. The
 *       hand is re-submitted with zero swing progress, which is the neutral
 *       hand pose the 1.21.11 stab pose expects, and leaves the arm static
 *       (arms are deliberately out of scope for this backport).</li>
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
        if (player.swingingHand != hand) {
            return;
        }
        if (!player.isSwingInProgress && event.getSwingProgress() <= 0.0F) {
            return;
        }
        // Holding right click wins over the jab, exactly like the 1.21.11
        // renderer, which checks isUsingItem before the swing animation.
        if (player.isHandActive() && player.getActiveHand() == hand) {
            return;
        }

        event.setCanceled(true);
        minecraft.getItemRenderer().renderItemInFirstPerson(player, event.getPartialTicks(),
                event.getInterpolatedPitch(), hand, 0.0F, event.getItemStack(), event.getEquipProgress());
    }
}
