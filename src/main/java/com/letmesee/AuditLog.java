package com.letmesee;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** Structured audit sink for console and an optional append-only file. */
public final class AuditLog {

    private static final Object FILE_LOCK = new Object();
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter
        .ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private AuditLog() {
    }

    /** Formats one stable, UTC-timestamped audit record for persistent storage. */
    public static String format(String message, Instant timestamp) {
        return TIMESTAMP.format(timestamp) + " " + singleLine(message);
    }

    /** Prevent names or custom labels from forging extra persistent log records. */
    private static String singleLine(String message) {
        StringBuilder safe = new StringBuilder(message.length());
        for (int i = 0; i < message.length(); i++) {
            char ch = message.charAt(i);
            safe.append(Character.isISOControl(ch) || ch == '\u2028' || ch == '\u2029'
                ? ' ' : ch);
        }
        return safe.toString();
    }

    /** Sends a preformatted audit message to enabled sinks. */
    public static void write(JavaPlugin plugin, String message,
            boolean consoleEnabled, boolean fileEnabled) {
        if (!consoleEnabled && !fileEnabled) {
            return;
        }
        String safeMessage = singleLine(message);
        if (consoleEnabled) {
            plugin.getLogger().info(safeMessage);
        }
        if (!fileEnabled) {
            return;
        }

        Path file = plugin.getDataFolder().toPath().resolve("audit.log");
        synchronized (FILE_LOCK) {
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, format(message, Instant.now()) + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                plugin.getLogger().warning("[LetMeSee] 无法写入审计文件 " + file + ": " + e);
            }
        }
    }
}
