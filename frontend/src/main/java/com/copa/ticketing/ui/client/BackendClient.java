package com.copa.ticketing.ui.client;

import com.copa.ticketing.ui.client.dto.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class BackendClient {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private final HttpClient http;
    private final BackendProperties props;
    private final String customerAuth;
    private final String adminAuth;

    public BackendClient(BackendProperties props) {
        this.props = props;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.customerAuth = basicAuth(props.customerUser(), props.customerPass());
        this.adminAuth = basicAuth(props.adminUser(), props.adminPass());
    }

    // ---- Matches ----

    public PagedResponse<MatchDto> getMatches(int page, int size, String city, String date, String team) {
        StringBuilder url = new StringBuilder(props.url() + "/api/public/matches?page=" + page + "&size=" + size);
        if (city != null && !city.isBlank()) url.append("&city=").append(city);
        if (date != null && !date.isBlank()) url.append("&date=").append(date);
        if (team != null && !team.isBlank()) url.append("&team=").append(team);
        return get(url.toString(), customerAuth, new TypeReference<>() {});
    }

    public MatchDto getMatch(long id) {
        return get(props.url() + "/api/public/matches/" + id, customerAuth, new TypeReference<>() {});
    }

    public List<SectorDto> getSectors(long matchId) {
        return get(props.url() + "/api/public/matches/" + matchId + "/sectors", customerAuth, new TypeReference<>() {});
    }

    public List<SeatRowSummaryDto> getSeatMapRows(long matchId, String sectorCode) {
        String url = props.url() + "/api/public/matches/" + matchId + "/seat-map/rows?sector="
                + URLEncoder.encode(sectorCode, StandardCharsets.UTF_8);
        return get(url, customerAuth, new TypeReference<>() {});
    }

    public List<SeatDto> getSeatMapByRow(long matchId, String sectorCode, String rowLabel) {
        String url = props.url() + "/api/public/matches/" + matchId + "/seat-map?sector="
                + URLEncoder.encode(sectorCode, StandardCharsets.UTF_8)
                + "&row=" + URLEncoder.encode(rowLabel, StandardCharsets.UTF_8);
        return get(url, customerAuth, new TypeReference<PagedResponse<SeatDto>>() {}).items();
    }

    public List<SeatDto> getAllSeatMap(long matchId, String sectorCode) {
        String url = props.url() + "/api/public/matches/" + matchId + "/seat-map?sector="
                + URLEncoder.encode(sectorCode, StandardCharsets.UTF_8) + "&all=true";
        return get(url, customerAuth, new TypeReference<PagedResponse<SeatDto>>() {}).items();
    }

    public PagedResponse<SeatDto> getSeatMap(long matchId, String sectorCode, int page, int size) {
        String url = props.url() + "/api/public/matches/" + matchId + "/seat-map?sector="
                + URLEncoder.encode(sectorCode, StandardCharsets.UTF_8) + "&page=" + page + "&size=" + size;
        return get(url, customerAuth, new TypeReference<>() {});
    }

    // ---- Reservation & Checkout ----

    public Map<String, Object> createReservation(Map<String, Object> body) {
        return post(props.url() + "/api/public/reservations", customerAuth, body, new TypeReference<>() {});
    }

    public Map<String, Object> checkout(String reservationCode) {
        return post(props.url() + "/api/public/reservations/" + reservationCode + "/checkout",
                customerAuth, Map.of(), new TypeReference<>() {});
    }

    public Map<String, Object> confirmPayment(String paymentRef) {
        return post(props.url() + "/api/public/payments/" + paymentRef + "/confirm",
                customerAuth, Map.of(), new TypeReference<>() {});
    }

    public List<TicketDto> getTickets(String lookup) {
        String encoded = URLEncoder.encode(lookup, StandardCharsets.UTF_8);
        return get(props.url() + "/api/public/customers/" + encoded + "/tickets",
                customerAuth, new TypeReference<>() {});
    }

    // ---- Admin ----

    public DashboardDto getDashboard() {
        return get(props.url() + "/api/admin/dashboard", adminAuth, new TypeReference<>() {});
    }

    public PagedResponse<OrderDto> getOrders(int page, int size, String status) {
        StringBuilder url = new StringBuilder(props.url() + "/api/admin/orders?page=" + page + "&size=" + size);
        if (status != null && !status.isBlank()) url.append("&status=").append(status);
        return get(url.toString(), adminAuth, new TypeReference<>() {});
    }

    public List<Map<String, Object>> getInventory(Long matchId) {
        String url = props.url() + "/api/admin/inventory" + (matchId != null ? "?matchId=" + matchId : "");
        return get(url, adminAuth, new TypeReference<>() {});
    }

    // ---- Live Demo (Admin) ----

    public DashboardStatusDto getLiveDashboard() {
        return get(props.url() + "/api/admin/dashboard/status", adminAuth,
                new TypeReference<>() {}, Duration.ofSeconds(25));
    }

    public HeatwaveAnalyticsDto getHeatwaveAnalytics() {
        return get(props.url() + "/api/admin/heatwave/analytics", adminAuth, new TypeReference<>() {});
    }

    public List<MatchOptionDto> getMatchOptions() {
        var wrapper = get(props.url() + "/api/admin/matches/options", adminAuth,
                new TypeReference<Map<String, Object>>() {});
        @SuppressWarnings("unchecked")
        var raw = (List<Map<String, Object>>) wrapper.get("matches");
        if (raw == null) return List.of();
        return raw.stream().map(m -> MAPPER.convertValue(m, MatchOptionDto.class)).toList();
    }

    public SelloutStatusDto getSelloutStatus(int matchNumber) {
        return get(props.url() + "/api/admin/sellout/status?matchNumber=" + matchNumber,
                adminAuth, new TypeReference<>() {});
    }

    public void startSellout(SelloutStartRequest request) {
        post(props.url() + "/api/admin/sellout/start", adminAuth, request, new TypeReference<Map<String, Object>>() {});
    }

    public SelloutStatusDto resetSellout(int matchNumber) {
        post(props.url() + "/api/admin/sellout/reset", adminAuth,
                Map.of("matchNumber", matchNumber), new TypeReference<Map<String, Object>>() {});
        try {
            return getSelloutStatus(matchNumber);
        } catch (Exception e) {
            return null;
        }
    }

    // ---- HTTP helpers ----

    private <T> T get(String url, String auth, TypeReference<T> type) {
        return get(url, auth, type, Duration.ofSeconds(10));
    }

    private <T> T get(String url, String auth, TypeReference<T> type, Duration timeout) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", auth)
                    .header("Accept", "application/json")
                    .timeout(timeout)
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                throw new RuntimeException("Backend error " + resp.statusCode() + ": " + resp.body());
            }
            return MAPPER.readValue(resp.body(), type);
        } catch (Exception e) {
            throw new RuntimeException("Backend GET " + url + " failed: " + e.getMessage(), e);
        }
    }

    private <T> T post(String url, String auth, Object body, TypeReference<T> type) {
        try {
            byte[] payload = MAPPER.writeValueAsBytes(body);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", auth)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                throw new RuntimeException("Backend error " + resp.statusCode() + ": " + resp.body());
            }
            return MAPPER.readValue(resp.body(), type);
        } catch (Exception e) {
            throw new RuntimeException("Backend POST " + url + " failed: " + e.getMessage(), e);
        }
    }

    private static String basicAuth(String user, String pass) {
        String credentials = user + ":" + pass;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }
}
