package com.copa.ticketing.ui.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "backend")
public record BackendProperties(
        String url,
        String customerUser,
        String customerPass,
        String adminUser,
        String adminPass
) {}
