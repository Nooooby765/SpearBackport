package com.spearbackport.client;

import com.spearbackport.SpearBackportMod;
import com.spearbackport.item.SpearItem;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovementInput;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * 1.12.2 multiplies movement by 0.2 while {@code isHandActive()} and offers no
 * item callback in that window ({@code EntityPlayerSP} L989-992), so this hook
 * must stay always-subscribed; pre-scaling cancels the factor. Riding is exempt.
 */
@Mod.EventBusSubscriber(modid = SpearBackportMod.MOD_ID, value = Side.CLIENT)
public final class ClientInputHandler {

    private static final float INVERSE_SLOWDOWN = 5.0F;

    private ClientInputHandler() {}

    @SubscribeEvent
    public static void onInputUpdate(InputUpdateEvent event) {
        EntityPlayer player = event.getEntityPlayer();
        if (!player.isHandActive() || player.isRiding()) {
            return;
        }

        ItemStack active = player.getActiveItemStack();
        if (!(active.getItem() instanceof SpearItem)) {
            return;
        }

        MovementInput input = event.getMovementInput();
        if (input.moveForward != 0.0F) {
            input.moveForward *= INVERSE_SLOWDOWN;
        }
        if (input.moveStrafe != 0.0F) {
            input.moveStrafe *= INVERSE_SLOWDOWN;
        }
    }
}
