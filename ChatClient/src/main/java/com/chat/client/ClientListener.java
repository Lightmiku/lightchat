package com.chat.client;

import com.chat.common.Message;
import com.chat.common.MessageType;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;

import java.util.function.Consumer;

public class ClientListener {
    private String host;
    private int port;
    private String username;
    private Channel channel;
    private Consumer<Message> onMessageReceived;
    private EventLoopGroup group;

    public ClientListener(String host, int port, String username, Consumer<Message> onMessageReceived) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.onMessageReceived = onMessageReceived;
    }

    public boolean connect() {
        group = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
             .channel(NioSocketChannel.class)
             .handler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 protected void initChannel(SocketChannel ch) throws Exception {
                     ch.pipeline().addLast(
                             new ObjectDecoder(ClassResolvers.cacheDisabled(null)),
                             new ObjectEncoder(),
                             new NettyClientHandler(onMessageReceived)
                     );
                 }
             });

            ChannelFuture f = b.connect(host, port).sync();
            channel = f.channel();

            // Send Login Message
            Message loginMsg = new Message(MessageType.LOGIN, username, "Hello");
            channel.writeAndFlush(loginMsg);

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public void sendMessage(String content, String recipient) {
        if (channel != null && channel.isActive()) {
            Message msg = new Message();
            msg.setSender(username);
            msg.setContent(content);
            
            if (recipient == null || recipient.equals("All")) {
                msg.setType(MessageType.CHAT_ALL);
            } else {
                msg.setType(MessageType.CHAT_PRIVATE);
                msg.setRecipient(recipient);
            }
            
            channel.writeAndFlush(msg);
        }
    }

    public void sendFile(java.io.File file, String recipient) {
        if (channel != null && channel.isActive()) {
            try {
                byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
                Message msg = new Message();
                msg.setSender(username);
                msg.setRecipient(recipient);
                msg.setFileName(file.getName());
                msg.setFileData(bytes);
                
                String fileName = file.getName().toLowerCase();
                if (fileName.endsWith(".png") || fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || fileName.endsWith(".gif")) {
                    msg.setType(MessageType.IMAGE);
                } else {
                    msg.setType(MessageType.FILE);
                }
                
                channel.writeAndFlush(msg);
            } catch (java.io.IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void disconnect() {
        if (channel != null) {
            Message msg = new Message();
            msg.setType(MessageType.LOGOUT);
            msg.setSender(username);
            channel.writeAndFlush(msg).addListener(ChannelFutureListener.CLOSE);
        }
        if (group != null) {
            group.shutdownGracefully();
        }
    }
}
