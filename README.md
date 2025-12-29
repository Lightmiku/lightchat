# Java Network Chat Room

This project consists of two parts: a Server and a Client (JavaFX).
It is set up as two Maven projects.

## Project Structure

- `ChatServer`: Server-side code (Maven project).
- `ChatClient`: Client-side code (Maven project, JavaFX).

## Prerequisites

- JDK 11 or higher.
- Maven (optional, if using VS Code or IntelliJ, it's built-in).

## How to Build and Run

### 1. Using VS Code (Recommended)

1.  Open the `ChatServer` folder in VS Code.
2.  Wait for the Java extension to load the project.
3.  Run `src/main/java/com/chat/server/ChatServer.java`.

1.  Open the `ChatClient` folder in VS Code (File -> Add Folder to Workspace, or open separately).
2.  Wait for the Java extension to load dependencies.
3.  Run `src/main/java/com/chat/client/ChatClientApp.java`.

### 2. Using Maven (Command Line)

#### Server

Build:
```bash
cd ChatServer
mvn clean package
```

Run:
```bash
java -jar target/chat-server-1.0-SNAPSHOT.jar
```

#### Client

Build:
```bash
cd ChatClient
mvn clean package
```

Run:
```bash
java -jar target/chat-client-1.0-SNAPSHOT.jar
```

## Deployment on Public Server

1.  **Upload Server Jar**: Copy `ChatServer/target/chat-server-1.0-SNAPSHOT.jar` to your remote server.
2.  **Open Firewall Port**: Allow TCP port **8888**.
3.  **Run Server**:
    ```bash
    nohup java -jar chat-server-1.0-SNAPSHOT.jar &
    ```
4.  **Connect Client**: Run the client on your local machine and enter the server's IP.

## Troubleshooting

- **"All errors" in VS Code**:
    - Make sure you have the "Extension Pack for Java" installed.
    - Right-click on `pom.xml` in each folder and select "Update Project" or "Reload Project".
    - Ensure you are using JDK 11 or higher for the Client (JavaFX requirement).

## Features

- **Login**: Enter a unique username.
- **Online Users**: View the list of currently connected users on the right.
- **Group Chat**: Select "All" in the user list to send messages to everyone.
- **Private Chat**: Select a specific user in the list to send a private message.
