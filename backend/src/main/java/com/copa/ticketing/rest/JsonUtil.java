package com.copa.ticketing.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.helidon.http.Status;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import java.util.Map;

public final class JsonUtil {

    public static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private JsonUtil() {}

    public static void ok(ServerResponse res, Object body) {
        try {
            res.status(Status.OK_200)
               .header("Content-Type", "application/json")
               .header("Cache-Control", "no-cache")
               .send(MAPPER.writeValueAsBytes(body));
        } catch (Exception e) {
            error(res, 500, "Serialization error");
        }
    }

    public static void okCached(ServerResponse res, Object body) {
        try {
            res.status(Status.OK_200)
               .header("Content-Type", "application/json")
               .header("Cache-Control", "max-age=30")
               .send(MAPPER.writeValueAsBytes(body));
        } catch (Exception e) {
            error(res, 500, "Serialization error");
        }
    }

    public static void created(ServerResponse res, Object body) {
        try {
            res.status(Status.CREATED_201)
               .header("Content-Type", "application/json")
               .send(MAPPER.writeValueAsBytes(body));
        } catch (Exception e) {
            error(res, 500, "Serialization error");
        }
    }

    public static void error(ServerResponse res, int code, String message) {
        try {
            res.status(Status.create(code))
               .header("Content-Type", "application/json")
               .send(MAPPER.writeValueAsBytes(Map.of("error", message)));
        } catch (Exception ex) {
            res.status(Status.create(code)).send();
        }
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
