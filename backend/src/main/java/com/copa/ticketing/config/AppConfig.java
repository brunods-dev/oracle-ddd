package com.copa.ticketing.config;

import io.helidon.config.Config;

public record AppConfig(
        int serverPort,
        String dbUrl,
        String dbUser,
        String dbPass,
        int dbPoolSize,
        String adminUser,
        String adminPass,
        String customerUser,
        String customerPass,
        int defaultPageSize,
        int maxPageSize,
        int reservationExpiryMinutes,
        String ociGenAiApiKey,
        String ociGenAiModelId,
        String heatwaveNlSqlModelId
) {
    public static AppConfig from(Config config) {
        String dbUrl = required("DB_URL", "db.url", config);
        String dbUser = required("DB_USER", "db.username", config);
        String dbPass = required("DB_PASS", "db.password", config);
        String adminUser = required("ADMIN_USER", "security.admin-user", config);
        String adminPass = required("ADMIN_PASS", "security.admin-pass", config);
        String customerUser = required("CUSTOMER_USER", "security.customer-user", config);
        String customerPass = required("CUSTOMER_PASS", "security.customer-pass", config);

        String ociGenAiApiKey = envOrConfig("OCI_GENAI_API_KEY", "oci.genai.api-key", config);
        String ociGenAiModelId = envOrConfig("OCI_GENAI_MODEL_ID", "oci.genai.model-id", config);
        String heatwaveNlSqlModelId = envOrConfig("HEATWAVE_NL_SQL_MODEL_ID",
                "heatwave.nl-sql.model-id", config);
        if (heatwaveNlSqlModelId == null || heatwaveNlSqlModelId.isBlank()) {
            heatwaveNlSqlModelId = "cohere.command-r-plus-08-2024";
        }

        return new AppConfig(
                envOrConfigInt("BACKEND_PORT", "server.port", config, 8080),
                dbUrl, dbUser, dbPass,
                envOrConfigInt("DB_POOL_SIZE", "db.pool-size", config, 20),
                adminUser, adminPass, customerUser, customerPass,
                config.get("pagination.default-size").asInt().orElse(20),
                config.get("pagination.max-size").asInt().orElse(100),
                config.get("reservation.expiry-minutes").asInt().orElse(10),
                ociGenAiApiKey, ociGenAiModelId,
                heatwaveNlSqlModelId
        );
    }

    private static String required(String envKey, String configKey, Config config) {
        String value = envOrConfig(envKey, configKey, config);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(envKey + " env var / " + configKey + " config is required");
        }
        return value;
    }

    private static String envOrConfig(String envKey, String configKey, Config config) {
        String prop = System.getProperty(envKey);
        if (prop != null && !prop.isBlank()) {
            return prop;
        }
        String env = System.getenv(envKey);
        if (env != null && !env.isBlank()) {
            return env;
        }
        return config.get(configKey).asString().orElse(null);
    }

    private static int envOrConfigInt(String envKey, String configKey, Config config, int defaultValue) {
        String raw = envOrConfig(envKey, configKey, config);
        if (raw != null && !raw.isBlank()) {
            return Integer.parseInt(raw);
        }
        return defaultValue;
    }
}
