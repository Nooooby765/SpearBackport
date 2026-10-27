package com.spearbackport.network;

import com.spearbackport.combat.SpearCombat;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * "I performed a spear jab." No payload: the server re-validates the held
 * item, the attack cooldown and every target anyway.
 */
public class MessageSpearJab implements IMessage {

    public MessageSpearJab() {}

    @Override
    public void fromBytes(ByteBuf buf) {}

    @Override
    public void toBytes(ByteBuf buf) {}

    public static final class Handler implements IMessageHandler<MessageSpearJab, IMessage> {
        @Override
        public IMessage onMessage(MessageSpearJab message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.server.addScheduledTask(() -> SpearCombat.handleJabPacket(player));
            return null;
        }
    }
}
