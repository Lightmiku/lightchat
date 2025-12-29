package com.chat.client;

import com.chat.common.Message;
import com.chat.common.MessageType;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ChatClientApp extends Application {

    private ClientListener client;
    private VBox chatBox;
    private ScrollPane chatScroll;
    private ListView<String> userList;
    private TextField inputField;
    private String username;
    private String serverIp = "127.0.0.1"; // Default
    private Map<String, PrivateChatWindow> privateChats = new HashMap<>();
    private Stage primaryStage;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        // Login Dialog
        if (!showLoginDialog()) {
            return;
        }

        // Main UI
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        // Center: Chat Area
        chatBox = new VBox(5);
        chatBox.setPadding(new Insets(10));
        chatScroll = new ScrollPane(chatBox);
        chatScroll.setFitToWidth(true);
        root.setCenter(chatScroll);

        // Right: User List
        userList = new ListView<>();
        userList.setPrefWidth(150);
        userList.getItems().add("All");
        userList.getSelectionModel().select(0);
        userList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                String selectedUser = userList.getSelectionModel().getSelectedItem();
                if (selectedUser != null && !selectedUser.equals("All") && !selectedUser.equals(username)) {
                    openPrivateChat(selectedUser);
                }
            }
        });
        VBox rightBox = new VBox(new Label("Online Users"), userList);
        rightBox.setSpacing(5);
        rightBox.setPadding(new Insets(0, 0, 0, 10));
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
        root.setBottom(bottomBox);
        
        // Layout adjustments
        inputField.setPrefWidth(300);
        sendBtn.getStyleClass().add("button");
        fileBtn.getStyleClass().add("secondary-button");
        imgBtn.getStyleClass().add("secondary-button");

        Scene scene = new Scene(root, 600, 400);
        scene.getStylesheets().add(getClass().getResource("styles.css").toExternalForm());
        
        primaryStage.setTitle("Chat Room - " + username);
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> {
            if (client != null) client.disconnect();
            Platform.exit();
            System.exit(0);
        });
        primaryStage.show();

        // Connect
        connectToServer();
    }

    private boolean showLoginDialog() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Login");
        dialog.setHeaderText("Enter your details");

        ButtonType loginButtonType = new ButtonType("Login", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(loginButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField usernameField = new TextField();
        usernameField.setPromptText("Username");
        TextField ipField = new TextField();
        ipField.setPromptText("Server IP");
        ipField.setText("127.0.0.1");

        grid.add(new Label("Username:"), 0, 0);
        grid.add(usernameField, 1, 0);
        grid.add(new Label("Server IP:"), 0, 1);
        grid.add(ipField, 1, 1);

        dialog.getDialogPane().setContent(grid);

        Platform.runLater(() -> usernameField.requestFocus());

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == loginButtonType) {
                serverIp = ipField.getText();
                return usernameField.getText();
            }
            return null;
        });

        Optional<String> result = dialog.showAndWait();
        if (result.isPresent()) {
            username = result.get();
            return true;
        }
        return false;
    }

    private void connectToServer() {
        client = new ClientListener(serverIp, 8888, username, this::handleMessage);
        if (!client.connect()) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Connection Failed");
            alert.setContentText("Could not connect to server at " + serverIp);
            alert.showAndWait();
            System.exit(1);
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

    private void sendMessage() {
        String content = inputField.getText();
        if (content.isEmpty()) return;

        // Main window always sends to All
        client.sendMessage(content, "All");
        inputField.clear();
    }

    private void handleMessage(Message msg) {
        Platform.runLater(() -> {
            switch (msg.getType()) {
                case CHAT_ALL:
                    addMessage("[All] " + msg.getSender(), msg.getContent(), MessageType.CHAT_ALL, null, null);
                    break;
                case CHAT_PRIVATE:
                case FILE:
                case IMAGE:
                    boolean isPrivate = msg.getType() == MessageType.CHAT_PRIVATE || 
                                       (msg.getRecipient() != null && !"All".equals(msg.getRecipient()));
                    
                    if (isPrivate) {
                        String otherParty;
                        if (msg.getSender().equals(username)) {
                            otherParty = msg.getRecipient();
                        } else {
                            otherParty = msg.getSender();
                        }
                        
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
                        // Public file/image
                        addMessage("[All] " + msg.getSender(), msg.getContent(), msg.getType(), msg.getFileData(), msg.getFileName());
                    }
                    break;
                case UPDATE_USERS:
                    ObservableList<String> users = FXCollections.observableArrayList("All");
                    users.addAll(msg.getOnlineUsers());
                    userList.setItems(users);
                    userList.getSelectionModel().select(0);
                    break;
            }
        });
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
        boolean isSelf = sender.contains(username); // Simple check, might need refinement
        
        VBox msgContainer = new VBox(2);
        msgContainer.setMaxWidth(400);
        
        // Bubble style
        msgContainer.getStyleClass().add("chat-bubble");
        if (isSelf) {
            msgContainer.getStyleClass().add("chat-bubble-self");
            msgContainer.setAlignment(javafx.geometry.Pos.TOP_RIGHT);
        } else {
            msgContainer.getStyleClass().add("chat-bubble-other");
            msgContainer.setAlignment(javafx.geometry.Pos.TOP_LEFT);
        }

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
            } catch (Exception e) {
                msgContainer.getChildren().add(new Label("[Error loading image]"));
            }
        } else if (type == MessageType.FILE) {
            Label fileLabel = new Label("File: " + fileName + " (Click to Download)");
            fileLabel.setStyle("-fx-text-fill: blue; -fx-underline: true; -fx-cursor: hand;");
            fileLabel.setOnMouseClicked(e -> saveFile(fileName, data));
            msgContainer.getChildren().add(fileLabel);
        } else {
            Label contentLabel = new Label(content);
            contentLabel.setWrapText(true);
            if (isSelf) {
                contentLabel.getStyleClass().add("chat-text-self");
            } else {
                contentLabel.getStyleClass().add("chat-text-other");
            }
            msgContainer.getChildren().add(contentLabel);
        }
        
        HBox row = new HBox();
        row.setPadding(new Insets(5, 0, 5, 0));
        if (isSelf) {
            row.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
            row.getChildren().add(msgContainer);
        } else {
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            row.getChildren().add(msgContainer);
        }
        
        chatBox.getChildren().add(row);
        chatScroll.setVvalue(1.0);
    }

    private void saveFile(String fileName, byte[] data) {
        if (data == null) return;
        
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save File");
        fileChooser.setInitialFileName(fileName);
        File file = fileChooser.showSaveDialog(primaryStage);
        
        if (file != null) {
            try {
                java.nio.file.Files.write(file.toPath(), data);
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Success");
                alert.setHeaderText(null);
                alert.setContentText("File saved successfully!");
                alert.showAndWait();
            } catch (Exception e) {
                e.printStackTrace();
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText("Save Failed");
                alert.setContentText("Could not save file: " + e.getMessage());
                alert.showAndWait();
            }
        }
    }
}
}
