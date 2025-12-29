package com.chat.client;

import com.chat.common.Message;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.util.function.Consumer;

public class NettyClientHandler extends SimpleChannelInboundHandler<Message> {
    private Consumer<Message> onMessageReceived;

    public NettyClientHandler(Consumer<Message> onMessageReceived) {
        this.onMessageReceived = onMessageReceived;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) throws Exception {
        if (onMessageReceived != null) {
            onMessageReceived.accept(msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
