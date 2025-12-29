package com.chat.server;

import com.chat.common.Message;
import com.chat.common.MessageType;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class NettyServerHandler extends SimpleChannelInboundHandler<Message> {

    private String username;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) throws Exception {
        switch (msg.getType()) {
            case LOGIN:
                String requestedName = msg.getSender();
                // Simple logic: if exists, maybe we should reject, but for now we overwrite or accept
                this.username = requestedName;
                ChatServer.addClient(username, ctx.channel());
                System.out.println("User logged in: " + username);
                break;
            case CHAT_ALL:
                ChatServer.broadcastMessage(msg);
                break;
            case CHAT_PRIVATE:
                ChatServer.sendPrivateMessage(msg);
                break;
            case FILE:
            case IMAGE:
                if (msg.getRecipient() == null || "All".equals(msg.getRecipient())) {
                    ChatServer.broadcastMessage(msg);
                } else {
                    ChatServer.sendPrivateMessage(msg);
                }
                break;
            case LOGOUT:
                ChatServer.removeClient(username);
                ctx.close();
                break;
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (username != null) {
            ChatServer.removeClient(username);
            System.out.println("User disconnected: " + username);
        }
        super.channelInactive(ctx);
    }
}
