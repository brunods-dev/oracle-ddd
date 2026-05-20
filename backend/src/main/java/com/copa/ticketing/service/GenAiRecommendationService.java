package com.copa.ticketing.service;

import com.copa.ticketing.config.AppConfig;
import com.copa.ticketing.domain.Match;
import com.copa.ticketing.domain.MatchRecommendation;
import com.copa.ticketing.repository.MatchRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class GenAiRecommendationService {

    private static final Logger LOG = Logger.getLogger(GenAiRecommendationService.class.getName());
    private static final String ENDPOINT =
            "https://inference.generativeai.us-chicago-1.oci.oraclecloud.com/20231130/actions/v1/chat/completions";

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final String apiKey;
    private final String modelId;
    private final MatchRepository matchRepo;

    public GenAiRecommendationService(AppConfig cfg, MatchRepository matchRepo) {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.apiKey = cfg.ociGenAiApiKey();
        this.modelId = cfg.ociGenAiModelId() != null ? cfg.ociGenAiModelId() : "cohere.command-r-plus";
        this.matchRepo = matchRepo;
    }

    public List<MatchRecommendation> recommend(List<String> favoriteTeams, List<String> preferredCities) throws Exception {
        var page = matchRepo.findAll(null, null, null, 0, 50);
        List<Match> matches = page.items();
        if (matches.isEmpty()) return List.of();

        String prompt = buildPrompt(matches, favoriteTeams, preferredCities);

        Map<String, Object> requestBody = Map.of(
                "model", modelId,
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "temperature", 0.3,
                "max_tokens", 512
        );

        byte[] payload = mapper.writeValueAsBytes(requestBody);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) {
            throw new RuntimeException("OCI GenAI error " + resp.statusCode() + ": " + resp.body());
        }
        String content = extractContent(resp.body());
        return parseResponse(content, matches);
    }

    String buildPrompt(List<Match> matches, List<String> favoriteTeams, List<String> preferredCities) {
        var sb = new StringBuilder();
        sb.append("Você é um assistente de ingressos da Copa do Mundo FIFA 2026.\n");

        if (favoriteTeams != null && !favoriteTeams.isEmpty()) {
            sb.append("Times favoritos do usuário: ").append(String.join(", ", favoriteTeams)).append(".\n");
        }
        if (preferredCities != null && !preferredCities.isEmpty()) {
            sb.append("Cidades preferidas (próximas ao usuário): ").append(String.join(", ", preferredCities))
              .append(". Priorize partidas nessas cidades, mas inclua outras se necessário.\n");
        }

        sb.append("Recomende de 3 a 5 partidas da lista abaixo com um motivo narrativo de 1 frase cada.");
        sb.append("\nResponda APENAS com um array JSON em português do Brasil: [{\"matchId\":N,\"reason\":\"...\"}]");
        sb.append("\nPartidas disponíveis (id|casa|fora|estádio|cidade|data):\n");
        for (Match m : matches) {
            if (m.availableSeats() > 0) {
                sb.append(m.id()).append("|")
                  .append(m.homeTeamCode() != null ? m.homeTeamCode() : "TBD").append("|")
                  .append(m.awayTeamCode() != null ? m.awayTeamCode() : "TBD").append("|")
                  .append(m.venueName()).append("|")
                  .append(m.venueCity() != null ? m.venueCity() : "").append("|")
                  .append(m.matchAt() != null ? m.matchAt().toLocalDate() : "TBD")
                  .append("\n");
            }
        }
        return sb.toString();
    }

    private String extractContent(String responseBody) throws Exception {
        JsonNode root = mapper.readTree(responseBody);
        JsonNode choices = root.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            return choices.get(0).path("message").path("content").asText("");
        }
        return root.path("text").asText(responseBody);
    }

    private List<MatchRecommendation> parseResponse(String text, List<Match> matches) {
        Map<Long, Match> byId = new HashMap<>();
        for (Match m : matches) byId.put(m.id(), m);

        // Extract JSON array, tolerating markdown code blocks
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        String json = (start >= 0 && end > start) ? text.substring(start, end + 1) : "[]";

        List<MatchRecommendation> result = new ArrayList<>();
        try {
            JsonNode arr = mapper.readTree(json);
            for (JsonNode item : arr) {
                long matchId = item.path("matchId").asLong(0);
                String reason = item.path("reason").asText("").trim();
                Match m = byId.get(matchId);
                if (m != null && !reason.isEmpty()) {
                    result.add(new MatchRecommendation(
                            m.id(), m.homeTeam(), m.awayTeam(),
                            m.homeTeamCode(), m.awayTeamCode(),
                            m.venueName(),
                            m.matchAt() != null ? m.matchAt().toString() : null,
                            reason
                    ));
                }
            }
        } catch (Exception e) {
            LOG.warning("Failed to parse GenAI response: " + e.getMessage());
        }
        return result;
    }
}
