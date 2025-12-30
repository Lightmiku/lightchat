package com.chat.server;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {
    private static final String DB_URL = "jdbc:sqlite:chat.db";

    public static void init() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            
            // Users table
            String sqlUsers = "CREATE TABLE IF NOT EXISTS users (" +
                              "username TEXT PRIMARY KEY, " +
                              "password TEXT NOT NULL, " +
                              "avatar_color TEXT, " + // Hex color code
                              "avatar_image BLOB, " + // Custom image
                              "is_banned INTEGER DEFAULT 0)"; // 0: false, 1: true
            stmt.execute(sqlUsers);
            
            // Try to add is_banned column if it doesn't exist (for existing DBs)
            try {
                stmt.execute("ALTER TABLE users ADD COLUMN is_banned INTEGER DEFAULT 0");
            } catch (SQLException e) {
                // Column likely exists, ignore
            }

            // Friends table
            String sqlFriends = "CREATE TABLE IF NOT EXISTS friends (" +
                                "user1 TEXT, " +
                                "user2 TEXT, " +
                                "status TEXT, " + // 'ACCEPTED', 'PENDING'
                                "PRIMARY KEY (user1, user2))";
            stmt.execute(sqlFriends);
            
            // Create Admin Account
            registerUser("mikulight", "tian20051008");

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static boolean registerUser(String username, String password) {
        String sql = "INSERT INTO users(username, password, avatar_color, is_banned) VALUES(?,?,?,0)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, password);
            // Default random color
            String color = String.format("#%06x", (int)(Math.random() * 0xFFFFFF));
            pstmt.setString(3, color);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false; // Username likely exists
        }
    }
    
    public static boolean isBanned(String username) {
        String sql = "SELECT is_banned FROM users WHERE username = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("is_banned") == 1;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
    
    public static void setBanned(String username, boolean banned) {
        String sql = "UPDATE users SET is_banned = ? WHERE username = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, banned ? 1 : 0);
            pstmt.setString(2, username);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    public static void deleteUser(String username) {
        String sqlUsers = "DELETE FROM users WHERE username = ?";
        String sqlFriends = "DELETE FROM friends WHERE user1 = ? OR user2 = ?";
        
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            // Delete from users
            try (PreparedStatement pstmt = conn.prepareStatement(sqlUsers)) {
                pstmt.setString(1, username);
                pstmt.executeUpdate();
            }
            
            // Delete from friends
            try (PreparedStatement pstmt = conn.prepareStatement(sqlFriends)) {
                pstmt.setString(1, username);
                pstmt.setString(2, username);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    // Returns list of "username:isBanned"
    public static List<String> getAllUsersStatus() {
        List<String> list = new ArrayList<>();
        String sql = "SELECT username, is_banned FROM users";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String u = rs.getString("username");
                int b = rs.getInt("is_banned");
                list.add(u + ":" + b);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public static boolean checkLogin(String username, String password) {
        String sql = "SELECT password FROM users WHERE username = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("password").equals(password);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static void updateAvatar(String username, byte[] image) {
        String sql = "UPDATE users SET avatar_image = ? WHERE username = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setBytes(1, image);
            pstmt.setString(2, username);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static byte[] getAvatar(String username) {
        String sql = "SELECT avatar_image FROM users WHERE username = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getBytes("avatar_image");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
    
    public static String getAvatarColor(String username) {
        String sql = "SELECT avatar_color FROM users WHERE username = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("avatar_color");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "#CCCCCC"; // Default fallback
    }

    public static boolean addFriendRequest(String fromUser, String toUser) {
        // Check if user exists
        if (!userExists(toUser)) return false;
        
        String sql = "INSERT OR IGNORE INTO friends(user1, user2, status) VALUES(?,?,?)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            // Store as (min, max) to avoid duplicates, or just directional?
            // Let's do directional for requests: user1 asks user2
            pstmt.setString(1, fromUser);
            pstmt.setString(2, toUser);
            pstmt.setString(3, "PENDING");
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public static void acceptFriend(String requester, String accepter) {
        String sqlUpdate = "UPDATE friends SET status = 'ACCEPTED' WHERE user1 = ? AND user2 = ?";
        // Also insert the reverse relationship for easier querying
        String sqlInsert = "INSERT OR IGNORE INTO friends(user1, user2, status) VALUES(?, ?, 'ACCEPTED')";
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmtUpdate = conn.prepareStatement(sqlUpdate);
             PreparedStatement pstmtInsert = conn.prepareStatement(sqlInsert)) {
            
            pstmtUpdate.setString(1, requester);
            pstmtUpdate.setString(2, accepter);
            pstmtUpdate.executeUpdate();
            
            pstmtInsert.setString(1, accepter);
            pstmtInsert.setString(2, requester);
            pstmtInsert.executeUpdate();
            
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void deleteFriend(String user1, String user2) {
        String sql = "DELETE FROM friends WHERE (user1 = ? AND user2 = ?) OR (user1 = ? AND user2 = ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, user1);
            pstmt.setString(2, user2);
            pstmt.setString(3, user2);
            pstmt.setString(4, user1);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static boolean isFriend(String user1, String user2) {
        String sql = "SELECT 1 FROM friends WHERE user1 = ? AND user2 = ? AND status = 'ACCEPTED'";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, user1);
            pstmt.setString(2, user2);
            return pstmt.executeQuery().next();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static List<String> getFriends(String username) {
        List<String> friends = new ArrayList<>();
        String sql = "SELECT user2 FROM friends WHERE user1 = ? AND status = 'ACCEPTED'";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                friends.add(rs.getString("user2"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return friends;
    }
    
    private static boolean userExists(String username) {
        String sql = "SELECT 1 FROM users WHERE username = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            return pstmt.executeQuery().next();
        } catch (SQLException e) {
            return false;
        }
    }
}
