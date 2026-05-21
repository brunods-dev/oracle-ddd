package com.copa.ticketing.service;

import com.copa.ticketing.config.AppConfig;
import com.copa.ticketing.domain.HeatwaveNlSqlResult;
import com.copa.ticketing.rest.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class HeatwaveNlSqlService {

    private static final int MAX_QUESTION_LENGTH = 500;
    private static final int MAX_ROWS = 100;
    private static final String SCHEMA_NAME = "copa_ticketing_demo";
    private static final String OUTPUT_VAR = "@copa_ticketing_nl_sql_output";
    private static final String HEATWAVE_HINT = "/*+ SET_VAR(use_secondary_engine=FORCED) */";
    private static final String FORCED_HINT_LABEL = "SET_VAR(use_secondary_engine=FORCED)";

    private static final List<String> ALL_VIEWS = List.of(
            "vw_hw_realtime_executive_dashboard",
            "vw_hw_match_business_scorecard",
            "vw_hw_sector_business_demand",
            "vw_hw_host_country_business_revenue",
            "vw_hw_payment_method_business_summary",
            "vw_hw_seat_heatmap_business_live"
    );

    private static final Map<String, List<String>> QUESTION_VIEWS = Map.ofEntries(
            Map.entry("receita_total", List.of("vw_hw_realtime_executive_dashboard")),
            Map.entry("jogos_receita", List.of("vw_hw_match_business_scorecard")),
            Map.entry("ocupacao_jogos", List.of("vw_hw_match_business_scorecard")),
            Map.entry("pendencia_pagamento", List.of("vw_hw_match_business_scorecard")),
            Map.entry("demanda_setores", List.of("vw_hw_sector_business_demand")),
            Map.entry("paises_sede", List.of("vw_hw_host_country_business_revenue")),
            Map.entry("blocos_quentes", List.of("vw_hw_seat_heatmap_business_live")),
            Map.entry("status_pagamento", List.of("vw_hw_payment_method_business_summary"))
    );

    private static final Set<String> DANGEROUS_KEYWORDS = Set.of(
            "INSERT", "UPDATE", "DELETE", "REPLACE", "DROP", "TRUNCATE", "ALTER", "CREATE",
            "GRANT", "REVOKE", "CALL", "SET", "USE", "LOAD", "OUTFILE", "INFILE", "HANDLER",
            "LOCK", "UNLOCK"
    );

    private static final Pattern SELECT_PATTERN = Pattern.compile("(?is)^\\s*SELECT\\b");

    private final DataSource ds;
    private final AppConfig cfg;

    public HeatwaveNlSqlService(DataSource ds, AppConfig cfg) {
        this.ds = ds;
        this.cfg = cfg;
    }

    public HeatwaveNlSqlResult ask(String question, String questionId) throws SQLException {
        String cleanQuestion = validateQuestion(question);
        String cleanQuestionId = normalizeQuestionId(questionId);
        List<String> consideredViews = selectViews(cleanQuestionId);
        String prompt = buildPrompt(cleanQuestion);
        String requestedModelId = cfg.heatwaveNlSqlModelId();

        try (Connection conn = ds.getConnection()) {
            conn.setCatalog(SCHEMA_NAME);

            String output = callNlSql(conn, prompt, consideredViews, requestedModelId);
            JsonNode node = parseOutput(output);
            String generatedSql = readRequiredText(node, "sql_query");
            if (node.path("is_sql_valid").asInt(1) == 0) {
                throw new IllegalArgumentException("NL_SQL returned is_sql_valid=0");
            }

            String executedSql = prepareExecutableSql(generatedSql, consideredViews);
            QueryRows queryRows = executeQuery(conn, executedSql);

            return new HeatwaveNlSqlResult(
                    cleanQuestion,
                    cleanQuestionId,
                    node.path("model_id").asText(null),
                    requestedModelId,
                    qualifyViews(consideredViews),
                    generatedSql,
                    executedSql,
                    FORCED_HINT_LABEL,
                    queryRows.columns(),
                    queryRows.rows(),
                    queryRows.rows().size()
            );
        }
    }

    private static String validateQuestion(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("Question is required");
        }
        String clean = question.trim();
        if (clean.length() > MAX_QUESTION_LENGTH) {
            throw new IllegalArgumentException("Question must have at most " + MAX_QUESTION_LENGTH + " characters");
        }
        return clean;
    }

    private static String normalizeQuestionId(String questionId) {
        if (questionId == null || questionId.isBlank()) {
            return null;
        }
        return questionId.trim();
    }

    private static List<String> selectViews(String questionId) {
        if (questionId != null && QUESTION_VIEWS.containsKey(questionId)) {
            return QUESTION_VIEWS.get(questionId);
        }
        return ALL_VIEWS;
    }

    private static List<String> qualifyViews(List<String> views) {
        return views.stream().map(v -> SCHEMA_NAME + "." + v).toList();
    }

    private static String buildPrompt(String question) {
        return """
                Generate exactly one MySQL SELECT statement for the copa_ticketing_demo FIFA ticketing database.
                Prefer the provided HeatWave analytical views and do not use base transactional tables unless the provided views require it.
                Important enum literals are uppercase: PAID, PAYMENT_PENDING, PENDING, RESERVED, CONVERTED, EXPIRED, CANCELLED, ISSUED, SOLD, AVAILABLE, SOLD_OUT, CLOSED.
                Do not generate INSERT, UPDATE, DELETE, TRUNCATE, ALTER, DROP, CREATE, CALL, SET, USE, GRANT, REVOKE, LOAD DATA, OUTFILE, or INFILE.
                Business question in Portuguese: %s
                """.formatted(question);
    }

    private String callNlSql(Connection conn, String prompt, List<String> consideredViews, String modelId) throws SQLException {
        String callSql = buildNlSqlCall(consideredViews);
        try (PreparedStatement ps = conn.prepareStatement(callSql)) {
            int idx = 1;
            ps.setString(idx++, prompt);
            for (String view : consideredViews) {
                ps.setString(idx++, SCHEMA_NAME);
                ps.setString(idx++, view);
            }
            ps.setString(idx, modelId);
            ps.execute();
        }

        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT " + OUTPUT_VAR + " AS output")) {
            if (rs.next()) {
                return rs.getString("output");
            }
        }
        throw new SQLException("NL_SQL did not return " + OUTPUT_VAR);
    }

    private static String buildNlSqlCall(List<String> consideredViews) {
        String tablesJson = consideredViews.stream()
                .map(v -> "JSON_OBJECT('schema_name', ?, 'table_name', ?)")
                .collect(Collectors.joining(", "));
        return """
                CALL sys.NL_SQL(
                  ?,
                  %s,
                  JSON_OBJECT(
                    'tables', JSON_ARRAY(%s),
                    'model_id', ?,
                    'execute', false,
                    'verbose', 0,
                    'include_comments', true,
                    'use_retry', true
                  )
                )
                """.formatted(OUTPUT_VAR, tablesJson);
    }

    private static JsonNode parseOutput(String output) {
        if (output == null || output.isBlank()) {
            throw new IllegalArgumentException("NL_SQL returned empty output");
        }
        try {
            return JsonUtil.MAPPER.readTree(output);
        } catch (Exception e) {
            throw new IllegalArgumentException("NL_SQL returned invalid JSON: " + e.getMessage(), e);
        }
    }

    private static String readRequiredText(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("NL_SQL output missing " + field);
        }
        return value;
    }

    private static String prepareExecutableSql(String generatedSql, List<String> consideredViews) {
        String withoutComments = stripSqlComments(generatedSql);
        String sql = stripSingleTrailingSemicolon(withoutComments);

        if (hasSemicolonOutsideQuotes(sql)) {
            throw new IllegalArgumentException("Generated SQL must contain exactly one SELECT statement");
        }
        if (!SELECT_PATTERN.matcher(sql).find()) {
            throw new IllegalArgumentException("Generated SQL must start with SELECT");
        }

        List<SqlToken> tokens = tokenizeSql(sql);
        Optional<String> dangerous = firstDangerousKeyword(tokens);
        if (dangerous.isPresent()) {
            throw new IllegalArgumentException("Generated SQL contains blocked keyword: " + dangerous.get());
        }

        validateReferencedViews(tokens, consideredViews);

        String withHint = addHeatwaveHint(sql);
        if (!hasTopLevelLimit(tokenizeSql(withHint))) {
            withHint = withHint.trim() + "\nLIMIT " + MAX_ROWS;
        }
        return withHint;
    }

    private static String addHeatwaveHint(String sql) {
        return SELECT_PATTERN.matcher(sql).replaceFirst("SELECT " + HEATWAVE_HINT);
    }

    private static void validateReferencedViews(List<SqlToken> tokens, List<String> consideredViews) {
        Set<String> allowed = consideredViews.stream()
                .map(v -> v.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        List<TableRef> refs = extractTableRefs(tokens);
        if (refs.isEmpty()) {
            throw new IllegalArgumentException("Generated SQL must read one of the authorized analytical views");
        }
        for (TableRef ref : refs) {
            if (ref.schema() != null && !SCHEMA_NAME.equalsIgnoreCase(ref.schema())) {
                throw new IllegalArgumentException("Generated SQL references unauthorized schema: " + ref.schema());
            }
            if (!allowed.contains(ref.table().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Generated SQL references unauthorized table/view: " + ref.displayName());
            }
        }
    }

    private static Optional<String> firstDangerousKeyword(List<SqlToken> tokens) {
        return tokens.stream()
                .filter(SqlToken::identifier)
                .map(t -> t.value().toUpperCase(Locale.ROOT))
                .filter(DANGEROUS_KEYWORDS::contains)
                .findFirst();
    }

    private static boolean hasTopLevelLimit(List<SqlToken> tokens) {
        int depth = 0;
        for (SqlToken token : tokens) {
            if ("(".equals(token.value())) {
                depth++;
            } else if (")".equals(token.value())) {
                depth = Math.max(0, depth - 1);
            } else if (depth == 0 && token.identifier() && "LIMIT".equalsIgnoreCase(token.value())) {
                return true;
            }
        }
        return false;
    }

    private static List<TableRef> extractTableRefs(List<SqlToken> tokens) {
        List<TableRef> refs = new ArrayList<>();
        int depth = 0;
        boolean inFromList = false;
        for (int i = 0; i < tokens.size(); i++) {
            SqlToken token = tokens.get(i);
            if ("(".equals(token.value())) {
                depth++;
                continue;
            }
            if (")".equals(token.value())) {
                depth = Math.max(0, depth - 1);
                continue;
            }
            if (!token.identifier()) {
                if (depth == 0 && inFromList && ",".equals(token.value())) {
                    Relation relation = readRelationSkippingPrefixes(tokens, i + 1);
                    if (relation != null) {
                        refs.add(new TableRef(relation.schema(), relation.table()));
                    }
                }
                continue;
            }
            String upper = token.value().toUpperCase(Locale.ROOT);
            if (depth == 0 && isClauseTerminator(upper)) {
                inFromList = false;
                continue;
            }
            if (!"FROM".equals(upper) && !"JOIN".equals(upper)) {
                continue;
            }
            inFromList = true;
            Relation relation = readRelationSkippingPrefixes(tokens, i + 1);
            if (relation != null) {
                refs.add(new TableRef(relation.schema(), relation.table()));
            }
        }
        return refs;
    }

    private static boolean isClauseTerminator(String upper) {
        return Set.of("WHERE", "GROUP", "HAVING", "ORDER", "LIMIT", "UNION", "EXCEPT", "INTERSECT").contains(upper);
    }

    private static Relation readRelationSkippingPrefixes(List<SqlToken> tokens, int start) {
        int j = start;
        while (j < tokens.size() && tokens.get(j).identifier()
                && "LATERAL".equalsIgnoreCase(tokens.get(j).value())) {
            j++;
        }
        if (j >= tokens.size() || "(".equals(tokens.get(j).value())) {
            return null;
        }
        return readRelation(tokens, j);
    }

    private static Relation readRelation(List<SqlToken> tokens, int start) {
        List<String> parts = new ArrayList<>();
        int i = start;
        if (i >= tokens.size() || !tokens.get(i).identifier()) {
            return null;
        }
        parts.add(tokens.get(i).value());
        i++;
        while (i + 1 < tokens.size()
                && ".".equals(tokens.get(i).value())
                && tokens.get(i + 1).identifier()) {
            parts.add(tokens.get(i + 1).value());
            i += 2;
        }
        if (parts.isEmpty()) {
            return null;
        }
        String table = parts.get(parts.size() - 1);
        String schema = parts.size() >= 2 ? parts.get(parts.size() - 2) : null;
        return new Relation(schema, table);
    }

    private static QueryRows executeQuery(Connection conn, String sql) throws SQLException {
        List<String> columns = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();
            for (int i = 1; i <= columnCount; i++) {
                String label = meta.getColumnLabel(i);
                columns.add((label != null && !label.isBlank()) ? label : meta.getColumnName(i));
            }

            while (rs.next() && rows.size() < MAX_ROWS) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.put(columns.get(i - 1), normalizeJdbcValue(rs.getObject(i)));
                }
                rows.add(row);
            }
        }
        return new QueryRows(columns, rows);
    }

    private static Object normalizeJdbcValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.sql.Date d) {
            LocalDate localDate = d.toLocalDate();
            return localDate;
        }
        if (value instanceof Timestamp ts) {
            LocalDateTime localDateTime = ts.toLocalDateTime();
            return localDateTime;
        }
        if (value instanceof Time t) {
            LocalTime localTime = t.toLocalTime();
            return localTime;
        }
        if (value instanceof byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        return value;
    }

    private static String stripSingleTrailingSemicolon(String sql) {
        String trimmed = sql.trim();
        if (trimmed.endsWith(";")) {
            return trimmed.substring(0, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private static boolean hasSemicolonOutsideQuotes(String sql) {
        boolean single = false;
        boolean dbl = false;
        boolean backtick = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (single) {
                if (c == '\'' && i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                    i++;
                } else if (c == '\'') {
                    single = false;
                }
                continue;
            }
            if (dbl) {
                if (c == '"' && i + 1 < sql.length() && sql.charAt(i + 1) == '"') {
                    i++;
                } else if (c == '"') {
                    dbl = false;
                }
                continue;
            }
            if (backtick) {
                if (c == '`') {
                    backtick = false;
                }
                continue;
            }
            if (c == '\'') {
                single = true;
            } else if (c == '"') {
                dbl = true;
            } else if (c == '`') {
                backtick = true;
            } else if (c == ';') {
                return true;
            }
        }
        return false;
    }

    private static String stripSqlComments(String sql) {
        StringBuilder out = new StringBuilder(sql.length());
        boolean single = false;
        boolean dbl = false;
        boolean backtick = false;

        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            char n = (i + 1 < sql.length()) ? sql.charAt(i + 1) : '\0';

            if (single) {
                out.append(c);
                if (c == '\'' && n == '\'') {
                    out.append(n);
                    i++;
                } else if (c == '\'') {
                    single = false;
                }
                continue;
            }
            if (dbl) {
                out.append(c);
                if (c == '"' && n == '"') {
                    out.append(n);
                    i++;
                } else if (c == '"') {
                    dbl = false;
                }
                continue;
            }
            if (backtick) {
                out.append(c);
                if (c == '`') {
                    backtick = false;
                }
                continue;
            }

            if (c == '\'') {
                single = true;
                out.append(c);
            } else if (c == '"') {
                dbl = true;
                out.append(c);
            } else if (c == '`') {
                backtick = true;
                out.append(c);
            } else if (c == '-' && n == '-' && (i + 2 >= sql.length() || Character.isWhitespace(sql.charAt(i + 2)))) {
                out.append(' ');
                i += 2;
                while (i < sql.length() && sql.charAt(i) != '\n' && sql.charAt(i) != '\r') {
                    i++;
                }
                if (i < sql.length()) {
                    out.append(sql.charAt(i));
                }
            } else if (c == '#') {
                out.append(' ');
                while (i < sql.length() && sql.charAt(i) != '\n' && sql.charAt(i) != '\r') {
                    i++;
                }
                if (i < sql.length()) {
                    out.append(sql.charAt(i));
                }
            } else if (c == '/' && n == '*') {
                out.append(' ');
                i += 2;
                while (i + 1 < sql.length() && !(sql.charAt(i) == '*' && sql.charAt(i + 1) == '/')) {
                    i++;
                }
                i = Math.min(i + 1, sql.length() - 1);
                out.append(' ');
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static List<SqlToken> tokenizeSql(String sql) {
        List<SqlToken> tokens = new ArrayList<>();
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (Character.isWhitespace(c)) {
                continue;
            }
            if (c == '\'' || c == '"') {
                char quote = c;
                i++;
                while (i < sql.length()) {
                    char current = sql.charAt(i);
                    if (current == quote && i + 1 < sql.length() && sql.charAt(i + 1) == quote) {
                        i += 2;
                        continue;
                    }
                    if (current == quote) {
                        break;
                    }
                    i++;
                }
                continue;
            }
            if (c == '`') {
                int start = ++i;
                while (i < sql.length() && sql.charAt(i) != '`') {
                    i++;
                }
                tokens.add(new SqlToken(sql.substring(start, Math.min(i, sql.length())), true));
                continue;
            }
            if (Character.isLetter(c) || c == '_' || c == '$') {
                int start = i;
                i++;
                while (i < sql.length()) {
                    char current = sql.charAt(i);
                    if (Character.isLetterOrDigit(current) || current == '_' || current == '$') {
                        i++;
                    } else {
                        break;
                    }
                }
                tokens.add(new SqlToken(sql.substring(start, i), true));
                i--;
                continue;
            }
            if (c == '.' || c == '(' || c == ')' || c == ',') {
                tokens.add(new SqlToken(String.valueOf(c), false));
            }
        }
        return tokens;
    }

    private record SqlToken(String value, boolean identifier) {}
    private record Relation(String schema, String table) {}
    private record TableRef(String schema, String table) {
        String displayName() {
            return schema == null ? table : schema + "." + table;
        }
    }
    private record QueryRows(List<String> columns, List<Map<String, Object>> rows) {}
}
