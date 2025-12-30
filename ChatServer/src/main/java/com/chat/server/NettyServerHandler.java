package com.chat.server;

import com.chat.common.Message;
import com.chat.common.MessageType;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.util.List;
import java.util.ArrayList;

public class NettyServerHandler extends SimpleChannelInboundHandler<Message> {

    private String username;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) throws Exception {
        switch (msg.getType()) {
            case REGISTER:
                boolean regSuccess = DatabaseManager.registerUser(msg.getSender(), msg.getPassword());
                Message regResponse = new Message();
                regResponse.setType(regSuccess ? MessageType.REGISTER_SUCCESS : MessageType.REGISTER_FAIL);
                regResponse.setContent(regSuccess ? "Registration successful" : "Username already exists");
                ctx.writeAndFlush(regResponse);
                break;

            case LOGIN:
                // Check if banned first
                if (DatabaseManager.isBanned(msg.getSender())) {
                    Message banMsg = new Message();
                    banMsg.setType(MessageType.LOGIN_FAIL);
                    banMsg.setContent("Your account has been banned.");
                    ctx.writeAndFlush(banMsg);
                    break;
                }

                boolean loginSuccess = DatabaseManager.checkLogin(msg.getSender(), msg.getPassword());
                Message loginResponse = new Message();
                if (loginSuccess) {
                    this.username = msg.getSender();
                    ChatServer.addClient(username, ctx.channel());
                    
                    loginResponse.setType(MessageType.LOGIN_SUCCESS);
                    loginResponse.setSender(username); // IMPORTANT: Send back the username
                    loginResponse.setContent("Login successful");
                    // Send avatar color as extra info
                    loginResponse.setExtraInfo(DatabaseManager.getAvatarColor(username));
                    ctx.writeAndFlush(loginResponse);

                    // Send Friend List
                    List<String> friends = DatabaseManager.getFriends(username);
                    Message friendListMsg = new Message();
                    friendListMsg.setType(MessageType.FRIEND_LIST);
                    friendListMsg.setOnlineUsers(friends); // Reusing onlineUsers field for friend list
                    ctx.writeAndFlush(friendListMsg);
                    
                    System.out.println("User logged in: " + username);
                } else {
                    loginResponse.setType(MessageType.LOGIN_FAIL);
                    loginResponse.setContent("Invalid username or password");
                    ctx.writeAndFlush(loginResponse);
                }
                break;

            case ADMIN_GET_USERS:
                if ("mikulight".equals(this.username)) {
                    List<String> allUsers = DatabaseManager.getAllUsersStatus();
                    // Append online status
                    List<String> finalData = new ArrayList<>();
                    for (String u : allUsers) {
                        // format: username:isBanned
                        String[] parts = u.split(":");
                        String name = parts[0];
                        String banned = parts[1];
                        String online = ChatServer.isUserOnline(name) ? "1" : "0";
                        finalData.add(name + ":" + online + ":" + banned);
                    }
                    
                    Message listMsg = new Message();
                    listMsg.setType(MessageType.ADMIN_USER_LIST);
                    listMsg.setOnlineUsers(finalData); // Reuse this list field
                    ctx.writeAndFlush(listMsg);
                }
                break;
                
            case ADMIN_BAN_USER:
                if ("mikulight".equals(this.username)) {
                    String target = msg.getRecipient();
                    DatabaseManager.setBanned(target, true);
                    ChatServer.kickClient(target); // Kick if online
                    
                    // Refresh list
                    List<String> allUsers = DatabaseManager.getAllUsersStatus();
                    List<String> finalData = new ArrayList<>();
                    for (String u : allUsers) {
                        String[] parts = u.split(":");
                        String name = parts[0];
                        String banned = parts[1];
                        String online = ChatServer.isUserOnline(name) ? "1" : "0";
                        finalData.add(name + ":" + online + ":" + banned);
                    }
                    Message listMsg = new Message();
                    listMsg.setType(MessageType.ADMIN_USER_LIST);
                    listMsg.setOnlineUsers(finalData);
                    ctx.writeAndFlush(listMsg);
                }
                break;

            case ADMIN_UNBAN_USER:
                if ("mikulight".equals(this.username)) {
                    String target = msg.getRecipient();
                    DatabaseManager.setBanned(target, false);
                    
                    // Refresh list
                    List<String> allUsers = DatabaseManager.getAllUsersStatus();
                    List<String> finalData = new ArrayList<>();
                    for (String u : allUsers) {
                        String[] parts = u.split(":");
                        String name = parts[0];
                        String banned = parts[1];
                        String online = ChatServer.isUserOnline(name) ? "1" : "0";
                        finalData.add(name + ":" + online + ":" + banned);
                    }
                    Message listMsg = new Message();
                    listMsg.setType(MessageType.ADMIN_USER_LIST);
                    listMsg.setOnlineUsers(finalData);
                    ctx.writeAndFlush(listMsg);
                }
                break;
                
            case ADMIN_DELETE_USER:
                if ("mikulight".equals(this.username)) {
                    String target = msg.getRecipient();
                    
                    // 1. Get friends before deletion to notify them later
                    List<String> friendsToNotify = DatabaseManager.getFriends(target);
                    
                    // 2. Delete user and friend relationships
                    DatabaseManager.deleteUser(target);
                    
                    // 3. Kick the user if online
                    ChatServer.kickClient(target); 
                    
                    // 4. Notify all friends to update their friend list
                    for (String friendName : friendsToNotify) {
                        io.netty.channel.Channel friendChannel = ChatServer.getClient(friendName);
                        if (friendChannel != null && friendChannel.isActive()) {
                            // Fetch new friend list (which excludes the deleted user)
                            List<String> newFriendList = DatabaseManager.getFriends(friendName);
                            Message updateMsg = new Message();
                            updateMsg.setType(MessageType.FRIEND_LIST);
                            updateMsg.setOnlineUsers(newFriendList);
                            friendChannel.writeAndFlush(updateMsg);
                        }
                    }
                    
                    // Refresh list for admin
                    List<String> allUsers = DatabaseManager.getAllUsersStatus();
                    List<String> finalData = new ArrayList<>();
                    for (String u : allUsers) {
                        String[] parts = u.split(":");
                        String name = parts[0];
                        String banned = parts[1];
                        String online = ChatServer.isUserOnline(name) ? "1" : "0";
                        finalData.add(name + ":" + online + ":" + banned);
                    }
                    Message listMsg = new Message();
                    listMsg.setType(MessageType.ADMIN_USER_LIST);
                    listMsg.setOnlineUsers(finalData);
                    ctx.writeAndFlush(listMsg);
                }
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

            case ADD_FRIEND_REQUEST:
                String targetUser = msg.getRecipient();
                if (DatabaseManager.addFriendRequest(this.username, targetUser)) {
                    // Forward request if user is online
                    Message reqMsg = new Message();
                    reqMsg.setType(MessageType.ADD_FRIEND_REQUEST);
                    reqMsg.setSender(this.username);
                    reqMsg.setRecipient(targetUser);
                    reqMsg.setContent("Friend request from " + this.username);
                    
                    // Send ONLY to target, do not echo back to sender
                    io.netty.channel.Channel targetChannel = ChatServer.getClient(targetUser);
                    if (targetChannel != null) {
                        targetChannel.writeAndFlush(reqMsg);
                    }
                }
                break;

            case DELETE_FRIEND:
                String friendToDelete = msg.getRecipient(); // The friend to delete
                DatabaseManager.deleteFriend(this.username, friendToDelete);
                
                // 1. Update sender's friend list
                Message updateSender = new Message();
                updateSender.setType(MessageType.FRIEND_LIST);
                updateSender.setOnlineUsers(DatabaseManager.getFriends(this.username));
                ctx.writeAndFlush(updateSender);
                
                // 2. Update ex-friend's friend list (if online)
                io.netty.channel.Channel exFriendChannel = ChatServer.getClient(friendToDelete);
                if (exFriendChannel != null) {
                    Message updateExFriend = new Message();
                    updateExFriend.setType(MessageType.FRIEND_LIST);
                    updateExFriend.setOnlineUsers(DatabaseManager.getFriends(friendToDelete));
                    exFriendChannel.writeAndFlush(updateExFriend);
                }
                break;

            case ADD_FRIEND_RESPONSE:
                String requester = msg.getRecipient(); // The one who sent the request
                boolean accepted = "ACCEPTED".equals(msg.getContent());
                if (accepted) {
                    DatabaseManager.acceptFriend(requester, this.username);
                    // Notify both to update friend list
                    // 1. Notify requester
                    Message notify1 = new Message();
                    notify1.setType(MessageType.ADD_FRIEND_RESPONSE);
                    notify1.setSender(this.username);
                    notify1.setRecipient(requester);
                    notify1.setContent("ACCEPTED");
                    
                    // Send ONLY to requester
                    io.netty.channel.Channel reqChannel = ChatServer.getClient(requester);
                    if (reqChannel != null) {
                        reqChannel.writeAndFlush(notify1);
                    }
                    
                    // 2. Notify self (accepter) - Client should handle this update locally or we send a new list
                    Message notify2 = new Message();
                    notify2.setType(MessageType.FRIEND_LIST);
                    notify2.setOnlineUsers(DatabaseManager.getFriends(this.username));
                    ctx.writeAndFlush(notify2);
                }
                break;
                
            case AVATAR_UPDATE:
                if (msg.getFileData() != null) {
                    DatabaseManager.updateAvatar(this.username, msg.getFileData());
                    // Broadcast to update others' view? Or just let them fetch on demand?
                    // For now, maybe just acknowledge or do nothing.
                }
                break;

            case LOGOUT:
                ChatServer.removeClient(username);
                ctx.close();
                break;
            default:
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
