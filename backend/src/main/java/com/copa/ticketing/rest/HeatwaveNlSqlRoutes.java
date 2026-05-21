package com.copa.ticketing.rest;

import com.copa.ticketing.service.HeatwaveNlSqlService;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import java.sql.SQLException;
import java.util.Map;

public class HeatwaveNlSqlRoutes implements HttpService {

    private final HeatwaveNlSqlService nlSqlService;

    public HeatwaveNlSqlRoutes(HeatwaveNlSqlService nlSqlService) {
        this.nlSqlService = nlSqlService;
    }

    @Override
    public void routing(HttpRules rules) {
        rules.post("/nl-sql", this::ask);
    }

    private void ask(ServerRequest req, ServerResponse res) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = JsonUtil.MAPPER.readValue(req.content().as(byte[].class), Map.class);
            String question = stringValue(body.get("question"));
            String questionId = stringValue(body.get("questionId"));
            JsonUtil.ok(res, nlSqlService.ask(question, questionId));
        } catch (IllegalArgumentException e) {
            JsonUtil.error(res, 400, e.getMessage());
        } catch (SQLException e) {
            JsonUtil.error(res, 500, "HeatWave/MySQL error: " + e.getMessage());
        } catch (Exception e) {
            JsonUtil.error(res, 500, "NL_SQL error: " + e.getMessage());
        }
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof String s ? s : String.valueOf(value);
    }
}
