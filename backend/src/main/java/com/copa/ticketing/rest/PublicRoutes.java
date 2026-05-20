package com.copa.ticketing.rest;

import com.copa.ticketing.config.AppConfig;
import com.copa.ticketing.domain.Customer;
import com.copa.ticketing.domain.Reservation;
import com.copa.ticketing.pagination.Page;
import com.copa.ticketing.repository.*;
import com.copa.ticketing.service.GenAiRecommendationService;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class PublicRoutes implements HttpService {

    private static final Logger LOG = Logger.getLogger(PublicRoutes.class.getName());

    private final MatchRepository matchRepo;
    private final CustomerRepository customerRepo;
    private final ReservationRepository reservationRepo;
    private final OrderRepository orderRepo;
    private final AppConfig cfg;
    private final GenAiRecommendationService genAiService;

    public PublicRoutes(MatchRepository matchRepo, CustomerRepository customerRepo,
                        ReservationRepository reservationRepo, OrderRepository orderRepo,
                        AppConfig cfg) {
        this(matchRepo, customerRepo, reservationRepo, orderRepo, cfg, null);
    }

    public PublicRoutes(MatchRepository matchRepo, CustomerRepository customerRepo,
                        ReservationRepository reservationRepo, OrderRepository orderRepo,
                        AppConfig cfg, GenAiRecommendationService genAiService) {
        this.matchRepo = matchRepo;
        this.customerRepo = customerRepo;
        this.reservationRepo = reservationRepo;
        this.orderRepo = orderRepo;
        this.cfg = cfg;
        this.genAiService = genAiService;
    }

    @Override
    public void routing(HttpRules rules) {
        rules
            .get("/matches", this::listMatches)
            .get("/matches/{id}", this::getMatch)
            .get("/matches/{id}/sectors", this::listSectors)
            .get("/matches/{id}/seat-map/rows", this::getSeatMapRows)
            .get("/matches/{id}/seat-map", this::getSeatMap)
            .post("/reservations", this::createReservation)
            .post("/reservations/{code}/checkout", this::checkout)
            .post("/payments/{ref}/confirm", this::confirmPayment)
            .get("/customers/{doc}/tickets", this::getTickets)
            .post("/recommendations", this::getRecommendations);
    }

    private void listMatches(ServerRequest req, ServerResponse res) {
        try {
            String city = JsonUtil.queryStr(req, "city");
            String date = JsonUtil.queryStr(req, "date");
            String team = JsonUtil.queryStr(req, "team");
            int page = JsonUtil.queryInt(req, "page", 0);
            int size = Page.clampSize(JsonUtil.queryInt(req, "size", 0), cfg.defaultPageSize(), cfg.maxPageSize());
            JsonUtil.okCached(res, matchRepo.findAll(city, date, team, page, size));
        } catch (SQLException e) {
            JsonUtil.error(res, 500, "Database error: " + e.getMessage());
        }
    }

    private void getMatch(ServerRequest req, ServerResponse res) {
        try {
            long id = Long.parseLong(req.path().pathParameters().get("id"));
            matchRepo.findById(id)
                     .ifPresentOrElse(
                         m -> JsonUtil.okCached(res, m),
                         () -> JsonUtil.error(res, 404, "Match not found")
                     );
        } catch (NumberFormatException e) {
            JsonUtil.error(res, 400, "Invalid match id");
        } catch (SQLException e) {
            JsonUtil.error(res, 500, "Database error: " + e.getMessage());
        }
    }

    private void listSectors(ServerRequest req, ServerResponse res) {
        try {
            long matchId = Long.parseLong(req.path().pathParameters().get("id"));
            JsonUtil.okCached(res, matchRepo.findSectorsByMatch(matchId));
        } catch (NumberFormatException e) {
            JsonUtil.error(res, 400, "Invalid match id");
        } catch (SQLException e) {
            JsonUtil.error(res, 500, "Database error: " + e.getMessage());
        }
    }

    private void getSeatMapRows(ServerRequest req, ServerResponse res) {
        try {
            long matchId = Long.parseLong(req.path().pathParameters().get("id"));
            String sector = JsonUtil.queryStr(req, "sector");
            if (sector == null) {
                JsonUtil.error(res, 400, "sector query param is required");
                return;
            }
            JsonUtil.ok(res, matchRepo.findSeatMapRows(matchId, sector));
        } catch (NumberFormatException e) {
            JsonUtil.error(res, 400, "Invalid match id");
        } catch (SQLException e) {
            JsonUtil.error(res, 500, "Database error: " + e.getMessage());
        }
    }

    private void getSeatMap(ServerRequest req, ServerResponse res) {
        try {
            long matchId = Long.parseLong(req.path().pathParameters().get("id"));
            String sector = JsonUtil.queryStr(req, "sector");
            if (sector == null) {
                JsonUtil.error(res, 400, "sector query param is required");
                return;
            }
            String rowParam = JsonUtil.queryStr(req, "row");
            if (rowParam != null && !rowParam.isBlank()) {
                var items = matchRepo.findSeatMapByRow(matchId, sector, rowParam);
                JsonUtil.ok(res, new Page<>(items, 0, items.size(), items.size()));
                return;
            }
            String allParam = JsonUtil.queryStr(req, "all");
            if ("true".equalsIgnoreCase(allParam)) {
                var items = matchRepo.findSeatMapAll(matchId, sector);
                JsonUtil.ok(res, new Page<>(items, 0, items.size(), items.size()));
                return;
            }
            int page = JsonUtil.queryInt(req, "page", 0);
            int size = Page.clampSize(JsonUtil.queryInt(req, "size", 0), 50, 200);
            JsonUtil.ok(res, matchRepo.findSeatMap(matchId, sector, page, size));
        } catch (NumberFormatException e) {
            JsonUtil.error(res, 400, "Invalid match id");
        } catch (SQLException e) {
            JsonUtil.error(res, 500, "Database error: " + e.getMessage());
        }
    }

    private void createReservation(ServerRequest req, ServerResponse res) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = JsonUtil.MAPPER.readValue(req.content().as(byte[].class), Map.class);

            String fullName = (String) body.get("fullName");
            String email = (String) body.get("email");
            String docType = (String) body.getOrDefault("documentType", "CPF");
            String docNumber = (String) body.get("documentNumber");
            String phone = (String) body.getOrDefault("phone", "");
            long matchId = toLong(body.get("matchId"));
            long matchSectorId = toLong(body.get("matchSectorId"));
            double unitPrice = toDouble(body.get("unitPrice"));

            @SuppressWarnings("unchecked")
            List<Object> rawSeats = (List<Object>) body.get("seatIds");
            if (rawSeats == null || rawSeats.isEmpty()) {
                JsonUtil.error(res, 400, "seatIds is required");
                return;
            }
            List<Long> seatIds = rawSeats.stream().map(PublicRoutes::toLong).filter(id -> id > 0).toList();
            if (seatIds.isEmpty()) {
                JsonUtil.error(res, 400, "seatIds must contain valid seat ids");
                return;
            }

            if (fullName == null || email == null || docNumber == null) {
                JsonUtil.error(res, 400, "fullName, email, documentNumber are required");
                return;
            }

            Customer customer = customerRepo.upsert(fullName, email, docType, docNumber, phone);
            Reservation reservation = reservationRepo.create(matchId, customer.id(), matchSectorId,
                    seatIds, unitPrice, cfg.reservationExpiryMinutes());

            JsonUtil.created(res, Map.of(
                    "reservationCode", reservation.reservationCode(),
                    "expiresAt", reservation.expiresAt().toString(),
                    "totalAmount", reservation.totalAmount(),
                    "status", reservation.status()
            ));
        } catch (Exception e) {
            JsonUtil.error(res, 500, "Error creating reservation: " + e.getMessage());
        }
    }

    private void checkout(ServerRequest req, ServerResponse res) {
        try {
            String code = req.path().pathParameters().get("code");
            var reservation = reservationRepo.findByCode(code);
            if (reservation.isEmpty()) {
                JsonUtil.error(res, 404, "Reservation not found");
                return;
            }
            var r = reservation.get();
            if (!"RESERVED".equals(r.status())) {
                JsonUtil.error(res, 409, "Reservation is not active (status: " + r.status() + ")");
                return;
            }
            if (r.expiresAt() != null && r.expiresAt().isBefore(java.time.LocalDateTime.now())) {
                JsonUtil.error(res, 410, "Reservation has expired");
                return;
            }
            var order = orderRepo.createFromReservation(r.id(), r.customerId(), r.totalAmount(), r.expiresAt());
            JsonUtil.created(res, Map.of(
                    "orderCode", order.orderCode(),
                    "paymentReference", order.paymentReference(),
                    "amount", order.amount(),
                    "status", order.status(),
                    "pixQrCode", "00020126580014BR.GOV.BCB.PIX0136" + order.paymentReference()
            ));
        } catch (Exception e) {
            JsonUtil.error(res, 500, "Checkout error: " + e.getMessage());
        }
    }

    private void confirmPayment(ServerRequest req, ServerResponse res) {
        try {
            String ref = req.path().pathParameters().get("ref");
            var order = orderRepo.findByPaymentReference(ref);
            if (order.isEmpty()) {
                JsonUtil.error(res, 404, "Payment reference not found");
                return;
            }
            var o = order.get();
            if (!"PAYMENT_PENDING".equals(o.status())) {
                JsonUtil.error(res, 409, "Order already processed (status: " + o.status() + ")");
                return;
            }
            orderRepo.confirmPayment(o.id());
            var tickets = orderRepo.issueTickets(o.id(), o.customerId());
            JsonUtil.ok(res, Map.of(
                    "orderCode", o.orderCode(),
                    "status", "PAID",
                    "ticketsIssued", tickets.size(),
                    "tickets", tickets
            ));
        } catch (Exception e) {
            JsonUtil.error(res, 500, "Payment confirm error: " + e.getMessage());
        }
    }

    private void getTickets(ServerRequest req, ServerResponse res) {
        try {
            String doc = req.path().pathParameters().get("doc");
            var tickets = orderRepo.findTicketsByCustomerDocument(doc);
            JsonUtil.ok(res, tickets);
        } catch (SQLException e) {
            JsonUtil.error(res, 500, "Database error: " + e.getMessage());
        }
    }

    private void getRecommendations(ServerRequest req, ServerResponse res) {
        LOG.info("[recommendations] handler reached, genAiService=" + (genAiService != null ? "configured" : "null"));
        if (genAiService == null) {
            JsonUtil.error(res, 501, "GenAI not configured — set OCI_GENAI_API_KEY in .env");
            return;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = JsonUtil.MAPPER.readValue(req.content().as(byte[].class), Map.class);
            @SuppressWarnings("unchecked")
            List<String> favoriteTeams = (List<String>) body.getOrDefault("favoriteTeams", List.of());
            @SuppressWarnings("unchecked")
            List<String> cities = (List<String>) body.getOrDefault("cities", List.of());

            if (favoriteTeams.isEmpty() && cities.isEmpty()) {
                JsonUtil.error(res, 400, "Informe ao menos um time ou cidade");
                return;
            }

            var recommendations = genAiService.recommend(favoriteTeams, cities);
            JsonUtil.ok(res, recommendations);
        } catch (Exception e) {
            JsonUtil.error(res, 500, "Recommendation error: " + e.getMessage());
        }
    }

    private static long toLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) return Long.parseLong(s);
        throw new IllegalArgumentException("Cannot convert to long: " + v);
    }

    private static double toDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) return Double.parseDouble(s);
        return 0.0;
    }
}
