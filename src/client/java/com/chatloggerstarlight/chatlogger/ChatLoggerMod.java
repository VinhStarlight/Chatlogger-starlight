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

    // Keep the writer open for the whole session instead of opening/closing every message
    private static BufferedWriter writer;

    @Override
    public void onInitializeClient() {

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            File gameDir = Minecraft.getInstance().gameDirectory;

            ServerData serverData = Minecraft.getInstance().getCurrentServer();
            String serverFolder;
            if (serverData != null) {
                serverFolder = serverData.ip.replaceAll("[^a-zA-Z0-9._\\-]", "_");
            } else {
                serverFolder = "singleplayer";
            }

            File logDir = new File(gameDir, "chat_logs" + File.separator + serverFolder);
            if (!logDir.exists()) {
                boolean created = logDir.mkdirs();
                if (!created) {
                    LOGGER.error("[ChatLogger] Could not create log directory: {}", logDir.getAbsolutePath());
                    return;
                }
            }

            String sessionName = LocalDateTime.now().format(FILE_FMT);
            File logFile = new File(logDir, "chat_" + sessionName + ".txt");

            try {
                writer = new BufferedWriter(new FileWriter(logFile, true));
                LOGGER.info("[ChatLogger] Logging to: {}", logFile.getAbsolutePath());
            } catch (IOException e) {
                LOGGER.error("[ChatLogger] Could not open log file: {}", e.getMessage());
            }
        });

        ClientReceiveMessageEvents.CHAT.register(
            (message, signedMessage, sender, params, receptionTimestamp) -> {
                logMessage("CHAT", message);
            }
        );

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) {
                logMessage("SYSTEM", message);
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (writer != null) {
                try {
                    String timestamp = LocalDateTime.now().format(TIMESTAMP_FMT);
                    String username = Minecraft.getInstance().getUser().getName();
                    writer.write(String.format("[%s] [SESSION] %s left the server%n", timestamp, username));
                    writer.write("=".repeat(50) + System.lineSeparator());
                    writer.close();
                } catch (IOException e) {
                    LOGGER.error("[ChatLogger] Could not write disconnect message: {}", e.getMessage());
                } finally {
                    writer = null;
                }
            }
        });
    }

    private static void logMessage(String type, Component message) {
        if (writer == null) return;

        try {
            String timestamp = LocalDateTime.now().format(TIMESTAMP_FMT);
            String plain = message.getString();
            writer.write(String.format("[%s] [%s] %s%n", timestamp, type, plain));
            writer.flush(); // make sure it's written immediately
        } catch (IOException e) {
            LOGGER.error("[ChatLogger] Could not write to log file: {}", e.getMessage());
        }
    }
}