package com.chat.client;

import com.chat.common.Message;
import com.chat.common.MessageType;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class ChatClientApp extends Application {

    private ClientListener client;
    private VBox chatBox;
    private ScrollPane chatScroll;
    private ListView<String> userList;
    private TextField inputField;
    private String username;
    private String serverIp = "127.0.0.1";
    private Map<String, PrivateChatWindow> privateChats = new HashMap<>();
    private Stage primaryStage;
    private Stage loginStage;
    
    // User data
    private String myAvatarColor = "#CCCCCC";
    private Map<String, String> friendList = new HashMap<>(); // username -> status

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        showLoginWindow();
    }

    private void showLoginWindow() {
        loginStage = new Stage();
        loginStage.setTitle("Login - LightChat");

        GridPane grid = new GridPane();
        grid.setAlignment(Pos.CENTER);
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(25, 25, 25, 25));

        TextField userField = new TextField();
        userField.setPromptText("Username");
        PasswordField passField = new PasswordField();
        passField.setPromptText("Password");
        TextField ipField = new TextField("127.0.0.1");
        ipField.setPromptText("Server IP");

        Button btnLogin = new Button("Login");
        Button btnRegister = new Button("Register");
        Label statusLabel = new Label();
        statusLabel.setTextFill(Color.RED);

        grid.add(new Label("Server IP:"), 0, 0);
        grid.add(ipField, 1, 0);
        grid.add(new Label("Username:"), 0, 1);
        grid.add(userField, 1, 1);
        grid.add(new Label("Password:"), 0, 2);
        grid.add(passField, 1, 2);
        
        HBox btnBox = new HBox(10, btnLogin, btnRegister);
        btnBox.setAlignment(Pos.CENTER_RIGHT);
        grid.add(btnBox, 1, 3);
        grid.add(statusLabel, 1, 4);

        btnLogin.setOnAction(e -> {
            String u = userField.getText();
            String p = passField.getText();
            String ip = ipField.getText();
            if (u.isEmpty() || p.isEmpty()) {
                statusLabel.setText("Please enter username and password");
                return;
            }
            connectAndAction(ip, u, p, true, statusLabel);
        });

        btnRegister.setOnAction(e -> {
            String u = userField.getText();
            String p = passField.getText();
            String ip = ipField.getText();
            if (u.isEmpty() || p.isEmpty()) {
                statusLabel.setText("Please enter username and password");
                return;
            }
            connectAndAction(ip, u, p, false, statusLabel);
        });

        Scene scene = new Scene(grid, 350, 250);
        loginStage.setScene(scene);
        loginStage.show();
    }

    private void connectAndAction(String ip, String user, String pass, boolean isLogin, Label statusLabel) {
        if (client == null || !serverIp.equals(ip)) {
            serverIp = ip;
            client = new ClientListener(serverIp, 8888, this::handleMessage);
            if (!client.connect()) {
                statusLabel.setText("Connection failed!");
                client = null;
                return;
            }
        }
        
        if (isLogin) {
            client.login(user, pass);
        } else {
            client.register(user, pass);
        }
    }

    private void initMainUI() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        // Center: Chat Area
        chatBox = new VBox(10);
        chatBox.setPadding(new Insets(10));
        chatScroll = new ScrollPane(chatBox);
        chatScroll.setFitToWidth(true);
        root.setCenter(chatScroll);

        // Right: User List & Profile
        VBox rightBox = new VBox(10);
        rightBox.setPadding(new Insets(0, 0, 0, 10));
        rightBox.setPrefWidth(300);

        // Profile Section
        HBox profileBox = new HBox(10);
        profileBox.setAlignment(Pos.CENTER_LEFT);
        profileBox.setPadding(new Insets(5));
        profileBox.setStyle("-fx-border-color: #ccc; -fx-border-radius: 5;");
        
        StackPane myAvatar = createAvatar(username, myAvatarColor);
        Label myNameLabel = new Label(username);
        myNameLabel.setStyle("-fx-font-weight: bold;");
        Button settingsBtn = new Button("⚙");
        settingsBtn.setOnAction(e -> showSettingsDialog());
        
        profileBox.getChildren().addAll(myAvatar, myNameLabel, settingsBtn);
        
        // Admin Button
        if ("mikulight".equals(username)) {
            Button adminBtn = new Button("Admin");
            adminBtn.setStyle("-fx-background-color: red; -fx-text-fill: white;");
            adminBtn.setOnAction(e -> showAdminPanel());
            profileBox.getChildren().add(adminBtn);
        }
        
        // Friend List
        userList = new ListView<>();
        userList.getItems().add("Global Chat Room");
        userList.setCellFactory(param -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    HBox box = new HBox(10);
                    box.setAlignment(Pos.CENTER_LEFT);
                    if (item.equals("Global Chat Room")) {
                        box.getChildren().add(new Label("🌐"));
                        box.getChildren().add(new Label(item));
                    } else {
                        box.getChildren().add(createAvatar(item, "#CCCCCC")); // Default color for friends for now
                        box.getChildren().add(new Label(item));
                    }
                    setGraphic(box);
                }
            }
        });
        
        userList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                String selected = userList.getSelectionModel().getSelectedItem();
                if (selected != null && !selected.equals("Global Chat Room")) {
                    openPrivateChat(selected);
                }
            }
        });

        Button addFriendBtn = new Button("Add Friend");
        addFriendBtn.setMaxWidth(Double.MAX_VALUE);
        addFriendBtn.setOnAction(e -> showAddFriendDialog());

        rightBox.getChildren().addAll(profileBox, new Label("Contacts"), userList, addFriendBtn);
        root.setRight(rightBox);

        // Bottom: Input
        inputField = new TextField();
        Button sendBtn = new Button("Send");
        Button fileBtn = new Button("File");
        Button imgBtn = new Button("Image");

        sendBtn.setOnAction(e -> sendMessage());
        inputField.setOnAction(e -> sendMessage());
        fileBtn.setOnAction(e -> chooseAndSendFile(false));
        imgBtn.setOnAction(e -> chooseAndSendFile(true));
        
        HBox bottomBox = new HBox(10, inputField, sendBtn, fileBtn, imgBtn);
        bottomBox.setPadding(new Insets(10, 0, 0, 0));
        HBox.setHgrow(inputField, Priority.ALWAYS);
        root.setBottom(bottomBox);
        
        Scene scene = new Scene(root, 800, 500);
        try {
            scene.getStylesheets().add(getClass().getResource("styles.css").toExternalForm());
        } catch (Exception e) {
            // Ignore if css missing
        }
        
        primaryStage.setTitle("LightChat - " + username);
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> {
            if (client != null) client.disconnect();
            Platform.exit();
            System.exit(0);
        });
        primaryStage.show();
    }

    private void showSettingsDialog() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Avatar Image");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg"));
        File file = fileChooser.showOpenDialog(primaryStage);
        if (file != null) {
            try {
                byte[] data = Files.readAllBytes(file.toPath());
                client.updateAvatar(data);
                Alert alert = new Alert(Alert.AlertType.INFORMATION, "Avatar updated!");
                alert.show();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void showAddFriendDialog() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add Friend");
        dialog.setHeaderText("Enter username to add:");
        dialog.showAndWait().ifPresent(target -> {
            if (!target.isEmpty() && !target.equals(username)) {
                client.sendFriendRequest(target);
            }
        });
    }
    
    private Stage adminStage;
    private ListView<String> adminUserList;
    
    private void showAdminPanel() {
        if (adminStage != null && adminStage.isShowing()) {
            adminStage.toFront();
            return;
        }
        
        adminStage = new Stage();
        adminStage.setTitle("Admin Panel");
        
        VBox root = new VBox(10);
        root.setPadding(new Insets(10));
        
        adminUserList = new ListView<>();
        Button refreshBtn = new Button("Refresh List");
        refreshBtn.setOnAction(e -> client.requestAdminUserList());
        
        Button banBtn = new Button("Ban/Unban");
        banBtn.setStyle("-fx-background-color: orange;");
        banBtn.setOnAction(e -> {
            String selectedFull = adminUserList.getSelectionModel().getSelectedItem();
            if (selectedFull != null) {
                String[] parts = selectedFull.split(":");
                if (parts.length >= 3) {
                    String target = parts[0].trim();
                    String isBanned = parts[2].trim();
                    if ("1".equals(isBanned)) {
                        client.unbanUser(target);
                    } else {
                        client.banUser(target);
                    }
                }
            }
        });
        
        Button deleteBtn = new Button("Delete User");
        deleteBtn.setStyle("-fx-background-color: red; -fx-text-fill: white;");
        deleteBtn.setOnAction(e -> {
            String selected = getSelectedAdminUser();
            if (selected != null) client.deleteUser(selected);
        });
        
        HBox btnBox = new HBox(10, refreshBtn, banBtn, deleteBtn);
        root.getChildren().addAll(new Label("User List (Format: Name : Online : Banned)"), adminUserList, btnBox);
        
        Scene scene = new Scene(root, 400, 500);
        adminStage.setScene(scene);
        adminStage.show();
        
        client.requestAdminUserList();
    }
    
    private String getSelectedAdminUser() {
        String item = adminUserList.getSelectionModel().getSelectedItem();
        if (item == null) return null;
        return item.split(":")[0].trim();
    }

    private void handleMessage(Message msg) {
        Platform.runLater(() -> {
            switch (msg.getType()) {
                case FORCE_LOGOUT:
                    Alert forceAlert = new Alert(Alert.AlertType.WARNING, msg.getContent());
                    forceAlert.showAndWait();
                    System.exit(0);
                    break;
                    
                case ADMIN_USER_LIST:
                    if (adminUserList != null) {
                        adminUserList.getItems().setAll(msg.getOnlineUsers());
                    }
                    break;

                case LOGIN_SUCCESS:
                    if (loginStage != null) loginStage.close();
                    if (msg.getSender() != null) {
                        this.username = msg.getSender();
                    }
                    if (msg.getExtraInfo() != null) {
                        this.myAvatarColor = msg.getExtraInfo();
                    }
                    initMainUI();
                    break;
                    
                case LOGIN_FAIL:
                    Alert alert = new Alert(Alert.AlertType.ERROR, msg.getContent());
                    alert.show();
                    break;

                case REGISTER_SUCCESS:
                    Alert regAlert = new Alert(Alert.AlertType.INFORMATION, "Registration successful! Please login.");
                    regAlert.show();
                    break;

                case REGISTER_FAIL:
                    Alert regFailAlert = new Alert(Alert.AlertType.ERROR, msg.getContent());
                    regFailAlert.show();
                    break;

                case FRIEND_LIST:
                    ObservableList<String> items = FXCollections.observableArrayList("Global Chat Room");
                    if (msg.getOnlineUsers() != null) {
                        items.addAll(msg.getOnlineUsers());
                    }
                    userList.setItems(items);
                    break;

                case ADD_FRIEND_REQUEST:
                    Alert reqAlert = new Alert(Alert.AlertType.CONFIRMATION);
                    reqAlert.setTitle("Friend Request");
                    reqAlert.setHeaderText("Friend Request from " + msg.getSender());
                    reqAlert.setContentText("Do you want to accept?");
                    reqAlert.showAndWait().ifPresent(response -> {
                        if (response == ButtonType.OK) {
                            client.acceptFriendRequest(msg.getSender());
                            // Optimistically add to list
                            userList.getItems().add(msg.getSender());
                        }
                    });
                    break;
                    
                case ADD_FRIEND_RESPONSE:
                    if ("ACCEPTED".equals(msg.getContent())) {
                        userList.getItems().add(msg.getSender()); // The sender is the one who accepted
                        new Alert(Alert.AlertType.INFORMATION, msg.getSender() + " accepted your friend request!").show();
                    }
                    break;

                case CHAT_ALL:
                    addMessage(msg.getSender(), msg.getContent(), MessageType.CHAT_ALL, null, null);
                    break;
                    
                case CHAT_PRIVATE:
                case FILE:
                case IMAGE:
                    handlePrivateOrFileMessage(msg);
                    break;
                default:
                    break;
            }
        });
    }
    
    private void handlePrivateOrFileMessage(Message msg) {
        boolean isPrivate = msg.getType() == MessageType.CHAT_PRIVATE || 
                           (msg.getRecipient() != null && !"All".equals(msg.getRecipient()));
        
        if (isPrivate) {
            String otherParty = msg.getSender().equals(username) ? msg.getRecipient() : msg.getSender();
            
            if (!privateChats.containsKey(otherParty)) {
                 PrivateChatWindow w = new PrivateChatWindow(otherParty, client, username);
                 privateChats.put(otherParty, w);
            }
            
            PrivateChatWindow w = privateChats.get(otherParty);
            if (!msg.getSender().equals(username)) {
                w.show();
            }
            w.appendMessage(msg);
        } else {
            addMessage(msg.getSender(), msg.getContent(), msg.getType(), msg.getFileData(), msg.getFileName());
        }
    }

    private void sendMessage() {
        String content = inputField.getText();
        if (content.isEmpty()) return;
        client.sendMessage(content, "All");
        inputField.clear();
    }
    
    private void chooseAndSendFile(boolean isImage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(isImage ? "Select Image" : "Select File");
        if (isImage) {
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif"));
        }
        File file = fileChooser.showOpenDialog(primaryStage);
        if (file != null) {
            client.sendFile(file, "All");
        }
    }

    private void addMessage(String sender, String content, MessageType type, byte[] data, String fileName) {
        boolean isSelf = sender.equals(username);
        
        HBox row = new HBox(10);
        row.setPadding(new Insets(5, 0, 5, 0));
        
        // Avatar
        StackPane avatar = createAvatar(sender, "#CCCCCC"); // Use default color for others for now
        
        // Click avatar to show info (only for others)
        if (!isSelf) {
            avatar.setOnMouseClicked(e -> showUserInfoPopup(sender));
        }

        VBox msgContainer = new VBox(2);
        msgContainer.setMaxWidth(400);
        msgContainer.getStyleClass().add("chat-bubble");
        msgContainer.getStyleClass().add(isSelf ? "chat-bubble-self" : "chat-bubble-other");

        Label senderLabel = new Label(sender);
        senderLabel.getStyleClass().add("sender-name");
        msgContainer.getChildren().add(senderLabel);

        if (type == MessageType.IMAGE && data != null) {
            try {
                Image img = new Image(new ByteArrayInputStream(data));
                ImageView imageView = new ImageView(img);
                imageView.setFitWidth(200);
                imageView.setPreserveRatio(true);
                msgContainer.getChildren().add(imageView);
            } catch (Exception e) { }
        } else if (type == MessageType.FILE) {
            Label fileLabel = new Label("File: " + fileName);
            fileLabel.setStyle("-fx-text-fill: blue; -fx-underline: true; -fx-cursor: hand;");
            fileLabel.setOnMouseClicked(e -> saveFile(fileName, data));
            msgContainer.getChildren().add(fileLabel);
        } else {
            Label contentLabel = new Label(content);
            contentLabel.setWrapText(true);
            contentLabel.getStyleClass().add(isSelf ? "chat-text-self" : "chat-text-other");
            msgContainer.getChildren().add(contentLabel);
        }
        
        if (isSelf) {
            row.setAlignment(Pos.CENTER_RIGHT);
            row.getChildren().addAll(msgContainer, avatar);
        } else {
            row.setAlignment(Pos.CENTER_LEFT);
            row.getChildren().addAll(avatar, msgContainer);
        }
        
        chatBox.getChildren().add(row);
        chatScroll.setVvalue(1.0);
    }
    
    private StackPane createAvatar(String name, String colorHex) {
        StackPane stack = new StackPane();
        Circle circle = new Circle(20);
        circle.setFill(Color.web(colorHex));
        circle.setStroke(Color.WHITE);
        
        String initial = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
        Label letter = new Label(initial);
        letter.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
        
        stack.getChildren().addAll(circle, letter);
        return stack;
    }
    
    private void showUserInfoPopup(String user) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("User Info");
        alert.setHeaderText(user);
        
        ButtonType addFriend = new ButtonType("Add Friend");
        alert.getButtonTypes().add(addFriend);
        
        alert.showAndWait().ifPresent(type -> {
            if (type == addFriend) {
                client.sendFriendRequest(user);
            }
        });
    }

    private void saveFile(String fileName, byte[] data) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setInitialFileName(fileName);
        File file = fileChooser.showSaveDialog(primaryStage);
        if (file != null) {
            try {
                Files.write(file.toPath(), data);
            } catch (Exception e) { e.printStackTrace(); }
        }
    }
    
    private void openPrivateChat(String targetUser) {
        if (targetUser.equals(username)) return;
        PrivateChatWindow window = privateChats.get(targetUser);
        if (window == null) {
            window = new PrivateChatWindow(targetUser, client, username);
            privateChats.put(targetUser, window);
        }
        window.show();
    }
}
