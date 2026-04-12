package com.chatloggerstarlight.chatlogger;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ChatLoggerMod implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("chatlogger");
    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FILE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private static File logFile;

    @Override
    public void onInitializeClient() {

        // When we join a server/world, set up the log file for that server
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            File gameDir = Minecraft.getInstance().gameDirectory;

            // Figure out the server name/IP
            ServerData serverData = Minecraft.getInstance().getCurrentServer();
            String serverFolder;
            if (serverData != null) {
                // Online server — use the IP, sanitized for use as a folder name
                serverFolder = serverData.ip.replaceAll("[^a-zA-Z0-9._\\-]", "_");
            } else {
                // Singleplayer
                serverFolder = "singleplayer";
            }

            // Create folder: chat_logs/<server-ip>/
            File logDir = new File(gameDir, "chat_logs" + File.separator + serverFolder);
            if (!logDir.exists()) {
                boolean created = logDir.mkdirs();
                if (!created) { 
                    LOGGER.error("[ChatLogger] Could not create log directory: {}", logDir.getAbsolutePath());
                    return;
                }   
            }

            // New timestamped file per session
            String sessionName = LocalDateTime.now().format(FILE_FMT);
            logFile = new File(logDir, "chat_" + sessionName + ".txt");

            LOGGER.info("[ChatLogger] Logging to: {}", logFile.getAbsolutePath());
        });

        // Player chat messages
        ClientReceiveMessageEvents.CHAT.register(
            (message, signedMessage, sender, params, receptionTimestamp) -> {
                logMessage("CHAT", message);
            }
        );

        // System/server messages (join/leave, deaths, broadcasts)
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) {
                logMessage("SYSTEM", message);
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (logFile != null) {
                String timestamp = LocalDateTime.now().format(TIMESTAMP_FMT);
                String username = Minecraft.getInstance().getUser().getName();
                String line = String.format("[%s] [SYSTEM] %s left the server%n", timestamp, username);

                try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
                    writer.write(line);
                } catch (IOException e) {
                    LOGGER.error("[ChatLogger] Could not write disconnect message file: {}", e.getMessage());
                }
                logFile = null; 
            }
        });
    }

    private static void logMessage(String type, Component message) {
        if (logFile == null) return; // not connected yet

        String timestamp = LocalDateTime.now().format(TIMESTAMP_FMT);
        String plain = message.getString();
        String line = String.format("[%s] [%s] %s%n", timestamp, type, plain);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
            writer.write(line);
        } catch (IOException e) {
            LOGGER.error("[ChatLogger] Could not write to log file: {}", e.getMessage());
        }
    }
}