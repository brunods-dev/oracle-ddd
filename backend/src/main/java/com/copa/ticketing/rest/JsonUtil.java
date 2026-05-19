package com.copa.ticketing.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.helidon.http.Status;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class JsonUtil {

    private static final Logger LOG = Logger.getLogger(JsonUtil.class.getName());

    public static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private JsonUtil() {}

    public static void ok(ServerResponse res, Object body) {
        writeJson(res, Status.OK_200, body, "no-cache");
    }

    public static void okCached(ServerResponse res, Object body) {
        writeJson(res, Status.OK_200, body, "max-age=30");
    }

    public static void created(ServerResponse res, Object body) {
        writeJson(res, Status.CREATED_201, body, null);
    }

    public static void error(ServerResponse res, int code, String message) {
        if (res.isSent()) {
            return;
        }
        String msg = message != null ? message : "Error";
        try {
            res.status(Status.create(code))
               .header("Content-Type", "application/json")
               .send(MAPPER.writeValueAsBytes(Map.of("error", msg)));
        } catch (Exception ex) {
            logResponseFailure("Failed to write error response", ex);
            if (!res.isSent() && !isClientDisconnect(ex)) {
                try {
                    res.status(Status.create(code)).send();
                } catch (Exception ignored) {
                    // connection closed
                }
            }
        }
    }

    private static void writeJson(ServerResponse res, Status status, Object body, String cacheControl) {
        if (res.isSent()) {
            return;
        }
        final byte[] payload;
        try {
            payload = MAPPER.writeValueAsBytes(body);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "JSON serialization failed", e);
            error(res, 500, "Serialization error");
            return;
        }
        if (res.isSent()) {
            return;
        }
        try {
            var response = res.status(status).header("Content-Type", "application/json");
            if (cacheControl != null) {
                response.header("Cache-Control", cacheControl);
            }
            response.send(payload);
        } catch (Exception e) {
            logResponseFailure("Failed to send JSON response", e);
            if (!res.isSent() && !isClientDisconnect(e)) {
                error(res, 500, "Response error");
            }
        }
    }

    private static void logResponseFailure(String message, Exception e) {
        if (!isClientDisconnect(e)) {
            LOG.log(Level.WARNING, message, e);
        }
    }

    private static boolean isClientDisconnect(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof EOFException) {
                return true;
            }
            if (t instanceof SocketException se) {
                String msg = se.getMessage();
                if (msg != null) {
                    String lower = msg.toLowerCase();
                    if (lower.contains("broken pipe") || lower.contains("connection reset")) {
                        return true;
                    }
                }
            }
            if (t instanceof IOException ioe) {
                String msg = ioe.getMessage();
                if (msg != null) {
                    String lower = msg.toLowerCase();
                    if (lower.contains("broken pipe") || lower.contains("connection reset")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static int queryInt(ServerRequest req, String name, int def) {
        String v = req.query().first(name).orElse(null);
        if (v == null || v.isBlank()) return def;
        try { return Integer.parseInt(v); } catch (NumberFormatException e) { return def; }
    }

    public static String queryStr(ServerRequest req, String name) {
        String v = req.query().first(name).orElse(null);
        return (v != null && !v.isBlank()) ? v : null;
    }
}
