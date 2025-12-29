package com.chat.server;

import com.chat.common.Message;
import com.chat.common.MessageType;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServer {
    private static final int PORT = 8888;
    private static final Map<String, Channel> clients = new ConcurrentHashMap<>();

    public static void main(String[] args) throws InterruptedException {
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
                             new ObjectDecoder(ClassResolvers.cacheDisabled(null)),
                             new ObjectEncoder(),
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

    public static void broadcastMessage(Message message) {
        for (Channel channel : clients.values()) {
            channel.writeAndFlush(message);
        }
    }

    public static void sendPrivateMessage(Message message) {
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

    private static void broadcastUserList() {
        Message updateMsg = new Message();
        updateMsg.setType(MessageType.UPDATE_USERS);
        List<String> userList = new ArrayList<>(clients.keySet());
        updateMsg.setOnlineUsers(userList);
        broadcastMessage(updateMsg);
    }
}
