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

    public ClientListener(String host, int port, Consumer<Message> onMessageReceived) {
        this.host = host;
        this.port = port;
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
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public void login(String username, String password) {
        this.username = username;
        Message msg = new Message();
        msg.setType(MessageType.LOGIN);
        msg.setSender(username);
        msg.setPassword(password);
        channel.writeAndFlush(msg);
    }

    public void register(String username, String password) {
        Message msg = new Message();
        msg.setType(MessageType.REGISTER);
        msg.setSender(username);
        msg.setPassword(password);
        channel.writeAndFlush(msg);
    }
    
    public void sendFriendRequest(String targetUser) {
        Message msg = new Message();
        msg.setType(MessageType.ADD_FRIEND_REQUEST);
        msg.setSender(username);
        msg.setRecipient(targetUser);
        channel.writeAndFlush(msg);
    }

    public void deleteFriend(String targetUser) {
        Message msg = new Message();
        msg.setType(MessageType.DELETE_FRIEND);
        msg.setSender(username);
        msg.setRecipient(targetUser);
        channel.writeAndFlush(msg);
    }
    
    public void acceptFriendRequest(String requester) {
        Message msg = new Message();
        msg.setType(MessageType.ADD_FRIEND_RESPONSE);
        msg.setSender(username);
        msg.setRecipient(requester);
        msg.setContent("ACCEPTED");
        channel.writeAndFlush(msg);
    }
    
    public void updateAvatar(byte[] imageData) {
        Message msg = new Message();
        msg.setType(MessageType.AVATAR_UPDATE);
        msg.setSender(username);
        msg.setFileData(imageData);
        channel.writeAndFlush(msg);
    }
    
    public void requestAdminUserList() {
        Message msg = new Message();
        msg.setType(MessageType.ADMIN_GET_USERS);
        msg.setSender(username);
        channel.writeAndFlush(msg);
    }
    
    public void banUser(String target) {
        Message msg = new Message();
        msg.setType(MessageType.ADMIN_BAN_USER);
        msg.setSender(username);
        msg.setRecipient(target);
        channel.writeAndFlush(msg);
    }

    public void unbanUser(String target) {
        Message msg = new Message();
        msg.setType(MessageType.ADMIN_UNBAN_USER);
        msg.setSender(username);
        msg.setRecipient(target);
        channel.writeAndFlush(msg);
    }
    
    public void deleteUser(String target) {
        Message msg = new Message();
        msg.setType(MessageType.ADMIN_DELETE_USER);
        msg.setSender(username);
        msg.setRecipient(target);
        channel.writeAndFlush(msg);
    }

    public void sendCollabRequest(String target) {
        Message msg = new Message();
        msg.setType(MessageType.COLLAB_REQUEST);
        msg.setSender(username);
        msg.setRecipient(target);
        channel.writeAndFlush(msg);
    }

    public void sendCollabResponse(String target, boolean accept) {
        Message msg = new Message();
        msg.setType(accept ? MessageType.COLLAB_ACCEPT : MessageType.COLLAB_DENY);
        msg.setSender(username);
        msg.setRecipient(target);
        channel.writeAndFlush(msg);
    }

    public void sendCollabSync(String target, String content) {
        Message msg = new Message();
        msg.setType(MessageType.COLLAB_SYNC);
        msg.setSender(username);
        msg.setRecipient(target);
        msg.setContent(content);
        channel.writeAndFlush(msg);
    }

    public void sendCollabUpdate(String target, String content) {
        Message msg = new Message();
        msg.setType(MessageType.COLLAB_UPDATE);
        msg.setSender(username);
        msg.setRecipient(target);
        msg.setContent(content);
        channel.writeAndFlush(msg);
    }

    public void sendCollabEnd(String target) {
        Message msg = new Message();
        msg.setType(MessageType.COLLAB_END);
        msg.setSender(username);
        msg.setRecipient(target);
        channel.writeAndFlush(msg);
    }

    public void sendCollabLock(String target) {
        Message msg = new Message();
        msg.setType(MessageType.COLLAB_LOCK);
        msg.setSender(username);
        msg.setRecipient(target);
        channel.writeAndFlush(msg);
    }

    public void sendCollabUnlock(String target) {
        Message msg = new Message();
        msg.setType(MessageType.COLLAB_UNLOCK);
        msg.setSender(username);
        msg.setRecipient(target);
        channel.writeAndFlush(msg);
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
