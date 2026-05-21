package com.copa.ticketing.ui.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HeatwaveNlSqlResponseDto(
        @JsonProperty("question") String question,
        @JsonProperty("questionId") String questionId,
        @JsonProperty("model_id") String modelId,
        @JsonProperty("requested_model_id") String requestedModelId,
        @JsonProperty("considered_tables") List<String> consideredTables,
        @JsonProperty("generated_sql") String generatedSql,
        @JsonProperty("executed_sql") String executedSql,
        @JsonProperty("forcedHint") String forcedHint,
        @JsonProperty("columns") List<String> columns,
        @JsonProperty("rows") List<Map<String, Object>> rows,
        @JsonProperty("row_count") int rowCount
) {}
