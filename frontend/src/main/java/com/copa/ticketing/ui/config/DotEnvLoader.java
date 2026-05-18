package com.copa.ticketing.ui.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

public final class DotEnvLoader {

    private static final Logger LOG = Logger.getLogger(DotEnvLoader.class.getName());

    private DotEnvLoader() {}

    public static void load() {
        Path file = resolveEnvFile();
        if (file == null) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(file);
            int loaded = 0;
            for (String line : lines) {
                String entry = parseLine(line);
                if (entry == null) {
                    continue;
                }
                int eq = entry.indexOf('=');
                String key = entry.substring(0, eq).trim();
                String value = unquote(entry.substring(eq + 1).trim());
                if (System.getProperty(key) == null && System.getenv(key) == null) {
                    System.setProperty(key, value);
                    loaded++;
                }
            }
            applyAliases();
            LOG.info("Loaded " + loaded + " entries from " + file.toAbsolutePath());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + file.toAbsolutePath(), e);
        }
    }

    private static void applyAliases() {
        alias("BACKEND_ADMIN_USER", "ADMIN_USER");
        alias("BACKEND_ADMIN_PASS", "ADMIN_PASS");
        alias("BACKEND_CUSTOMER_USER", "CUSTOMER_USER");
        alias("BACKEND_CUSTOMER_PASS", "CUSTOMER_PASS");
        alias("FRONTEND_PORT", "SERVER_PORT");
    }

    private static void alias(String target, String source) {
        if (isBlank(System.getProperty(target))) {
            String value = System.getProperty(source);
            if (isBlank(value)) {
                value = System.getenv(source);
            }
            if (!isBlank(value)) {
                System.setProperty(target, value);
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static Path resolveEnvFile() {
        String explicit = System.getenv("COPA_ENV_FILE");
        if (explicit != null && !explicit.isBlank()) {
            return Path.of(explicit);
        }
        Path cwd = Path.of(System.getProperty("user.dir"));
        Path direct = cwd.resolve(".env");
        if (Files.isRegularFile(direct)) {
            return direct;
        }
        Path nested = cwd.resolve("frontend").resolve(".env");
        if (Files.isRegularFile(nested)) {
            return nested;
        }
        return null;
    }

    private static String parseLine(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return null;
        }
        if (trimmed.startsWith("export ")) {
            trimmed = trimmed.substring(7).trim();
        }
        return trimmed.contains("=") ? trimmed : null;
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}
