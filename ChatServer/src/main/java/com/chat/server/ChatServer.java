package com.chat.server;

import com.chat.common.Message;
import com.chat.common.MessageType;
import com.google.gson.Gson;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServer {
    private static final int PORT = 8888;
    private static final Map<String, Channel> clients = new ConcurrentHashMap<>();

    public static void main(String[] args) throws InterruptedException {
        // Initialize Database
        DatabaseManager.init();
        System.out.println("Database initialized.");

        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
             .channel(NioServerSocketChannel.class)
             .childHandler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 protected void initChannel(SocketChannel ch) throws Exception {
                     ch.pipeline().addLast(
                             new LengthFieldBasedFrameDecoder(10485760, 0, 4, 0, 4),
                             new LengthFieldPrepender(4),
                             new StringDecoder(CharsetUtil.UTF_8),
                             new StringEncoder(CharsetUtil.UTF_8),
                             new MessageToMessageEncoder<Message>() {
                                 @Override
                                 protected void encode(ChannelHandlerContext ctx, Message msg, List<Object> out) {
                                     out.add(new Gson().toJson(msg));
                                 }
                             },
                             new MessageToMessageDecoder<String>() {
                                 @Override
                                 protected void decode(ChannelHandlerContext ctx, String msg, List<Object> out) {
                                     out.add(new Gson().fromJson(msg, Message.class));
                                 }
                             },
                             new NettyServerHandler()
                     );
                 }
             });

            System.out.println("Netty Server starting on port " + PORT + "...");
            ChannelFuture f = b.bind(PORT).sync();
            f.channel().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    public static synchronized void addClient(String username, Channel channel) {
        clients.put(username, channel);
        broadcastUserList();
    }

    public static synchronized void removeClient(String username) {
        if (username != null && clients.containsKey(username)) {
            clients.remove(username);
            broadcastUserList();
        }
    }
    
    public static Channel getClient(String username) {
        return clients.get(username);
    }

    public static void broadcastMessage(Message message) {
        for (Channel channel : clients.values()) {
            channel.writeAndFlush(message);
        }
    }

    public static void sendPrivateMessage(Message message) {
        // Check friendship
        if (!DatabaseManager.isFriend(message.getSender(), message.getRecipient())) {
            Channel sender = clients.get(message.getSender());
            if (sender != null) {
                Message errorMsg = new Message();
                errorMsg.setType(MessageType.CHAT_PRIVATE);
                errorMsg.setSender("System");
                errorMsg.setRecipient(message.getSender());
                errorMsg.setContent("Message failed: You are not friends with " + message.getRecipient());
                sender.writeAndFlush(errorMsg);
            }
            return;
        }

        Channel recipient = clients.get(message.getRecipient());
        if (recipient != null) {
            recipient.writeAndFlush(message);
            // Send back to sender
            Channel sender = clients.get(message.getSender());
            if (sender != null && !sender.equals(recipient)) {
                sender.writeAndFlush(message);
            }
        }
    }
    
    public static void kickClient(String username) {
        Channel ch = clients.get(username);
        if (ch != null) {
            Message msg = new Message();
            msg.setType(MessageType.FORCE_LOGOUT);
            msg.setContent("You have been banned/kicked by admin.");
            ch.writeAndFlush(msg);
            ch.close();
            removeClient(username);
        }
    }
    
    public static boolean isUserOnline(String username) {
        return clients.containsKey(username);
    }

    private static void broadcastUserList() {
        Message updateMsg = new Message();
        updateMsg.setType(MessageType.UPDATE_USERS);
        List<String> userList = new ArrayList<>(clients.keySet());
        updateMsg.setOnlineUsers(userList);
        broadcastMessage(updateMsg);
    }
}
