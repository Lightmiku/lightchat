package com.chat.client;

import com.chat.common.Message;
import com.chat.common.MessageType;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.ByteArrayInputStream;
import java.io.File;

public class PrivateChatWindow {
    private Stage stage;
    private VBox chatBox;
    private ScrollPane chatScroll;
    private TextField inputField;
    private ClientListener client;
    private String targetUser;
    private String currentUser;

    public PrivateChatWindow(String targetUser, ClientListener client, String currentUser) {
        this.targetUser = targetUser;
        this.client = client;
        this.currentUser = currentUser;
        initUI();
    }

    private void initUI() {
        stage = new Stage();
        stage.setTitle("Private Chat with " + targetUser);

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        chatBox = new VBox(5);
        chatBox.setPadding(new Insets(10));
        chatScroll = new ScrollPane(chatBox);
        chatScroll.setFitToWidth(true);
        root.setCenter(chatScroll);

        inputField = new TextField();
        inputField.setPrefWidth(200);
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

        // Styles
        sendBtn.getStyleClass().add("button");
        fileBtn.getStyleClass().add("secondary-button");
        imgBtn.getStyleClass().add("secondary-button");

        Scene scene = new Scene(root, 500, 400);
        scene.getStylesheets().add(getClass().getResource("styles.css").toExternalForm());
        stage.setScene(scene);
    }

    public void show() {
        if (!stage.isShowing()) {
            stage.show();
        }
        stage.toFront();
    }

    public void appendMessage(Message msg) {
        boolean isSelf = msg.getSender().equals(currentUser);
        
        VBox msgContainer = new VBox(2);
        msgContainer.setMaxWidth(350);
        
        // Bubble style
        msgContainer.getStyleClass().add("chat-bubble");
        if (isSelf) {
            msgContainer.getStyleClass().add("chat-bubble-self");
            msgContainer.setAlignment(javafx.geometry.Pos.TOP_RIGHT);
        } else {
            msgContainer.getStyleClass().add("chat-bubble-other");
            msgContainer.setAlignment(javafx.geometry.Pos.TOP_LEFT);
        }

        Label senderLabel = new Label(msg.getSender());
        senderLabel.getStyleClass().add("sender-name");
        msgContainer.getChildren().add(senderLabel);

        if (msg.getType() == MessageType.IMAGE && msg.getFileData() != null) {
            try {
                Image img = new Image(new ByteArrayInputStream(msg.getFileData()));
                ImageView imageView = new ImageView(img);
                imageView.setFitWidth(200);
                imageView.setPreserveRatio(true);
                msgContainer.getChildren().add(imageView);
            } catch (Exception e) {
                msgContainer.getChildren().add(new Label("[Error loading image]"));
            }
        } else if (msg.getType() == MessageType.FILE) {
            Label fileLabel = new Label("File: " + msg.getFileName() + " (Click to Download)");
            fileLabel.setStyle("-fx-text-fill: blue; -fx-underline: true; -fx-cursor: hand;");
            fileLabel.setOnMouseClicked(e -> saveFile(msg.getFileName(), msg.getFileData()));
            msgContainer.getChildren().add(fileLabel);
        } else {
            Label contentLabel = new Label(msg.getContent());
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
        File file = fileChooser.showSaveDialog(stage);
        
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

    private void sendMessage() {
        String content = inputField.getText();
        if (content.isEmpty()) return;

        client.sendMessage(content, targetUser);
        inputField.clear();
    }

    private void chooseAndSendFile(boolean isImage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(isImage ? "Select Image" : "Select File");
        if (isImage) {
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif"));
        }
        File file = fileChooser.showOpenDialog(stage);
        if (file != null) {
            client.sendFile(file, targetUser);
        }
    }
}
