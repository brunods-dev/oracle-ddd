package com.copa.ticketing.rest;

import com.copa.ticketing.domain.*;
import com.copa.ticketing.domain.DashboardStatus.*;
import com.copa.ticketing.domain.HeatwaveAnalytics.PaymentStatus;
import com.copa.ticketing.domain.HeatwaveAnalytics.SectorDemand;
import com.copa.ticketing.domain.HeatwaveAnalytics.TopMatch;
import com.copa.ticketing.repository.HeatwaveRepository;
import com.copa.ticketing.service.SelloutConfig;
import com.copa.ticketing.service.SelloutJobManager;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class LiveDemoRoutes implements HttpService {

    private static final long HW_CACHE_TTL_MS = 6_000;

    private final HeatwaveRepository hwRepo;
    private final SelloutJobManager jobManager;

    private volatile HeatwaveAnalytics hwCached;
    private volatile long hwCacheExpiry = 0;
    private final ReentrantLock hwLock = new ReentrantLock();

    public LiveDemoRoutes(HeatwaveRepository hwRepo, SelloutJobManager jobManager) {
        this.hwRepo = hwRepo;
        this.jobManager = jobManager;
    }

    @Override
    public void routing(HttpRules rules) {
        rules
            .get("/heatwave/analytics", this::getHeatwaveAnalytics)
            .get("/matches/options", this::getMatchOptions)
            .get("/sellout/status", this::getSelloutStatus)
            .get("/dashboard/status", this::getDashboardStatus)
            .post("/sellout/start", this::startSellout)
            .post("/sellout/reset", this::resetSellout);
    }

    private void getHeatwaveAnalytics(ServerRequest req, ServerResponse res) {
        try {
            JsonUtil.ok(res, cachedHeatwave());
        } catch (SQLException e) {
            JsonUtil.error(res, 500, "Database error: " + e.getMessage());
        }
    }

    private void getMatchOptions(ServerRequest req, ServerResponse res) {
        try {
            JsonUtil.ok(res, Map.of("ok", true, "matches", jobManager.getMatchOptions()));
        } catch (SQLException e) {
            JsonUtil.error(res, 500, "Database error: " + e.getMessage());
        }
    }

    private void getSelloutStatus(ServerRequest req, ServerResponse res) {
        try {
            String p = JsonUtil.queryStr(req, "matchNumber");
            int matchNumber = (p != null) ? Integer.parseInt(p) : 68;
            JsonUtil.ok(res, jobManager.getStatus(matchNumber));
        } catch (IllegalArgumentException e) {
            JsonUtil.error(res, 400, e.getMessage());
        } catch (SQLException e) {
            JsonUtil.error(res, 500, "Database error: " + e.getMessage());
        }
    }

    private void getDashboardStatus(ServerRequest req, ServerResponse res) {
        try {
            HeatwaveAnalytics hw = cachedHeatwave();
            HeatwaveAnalytics.Summary sum = hw.summary();

            DashboardStatus.Summary summary = new DashboardStatus.Summary(
                    sum.grossRevenue(), sum.ticketsIssued(), sum.paidOrders(),
                    sum.activeReservations(), sum.reservationsCreated(), sum.convertedReservations(),
                    sum.paymentPendingAmount(), sum.conversionPercent(), sum.occupancyPercent()
            );

            List<LabelValue> countries = hw.hostCountries().stream()
                    .map(c -> new LabelValue(c.country(), c.grossRevenue())).toList();

            Map<String, Double> sectorTotals = new java.util.LinkedHashMap<>();
            for (SectorDemand sd : hw.sectorDemand()) {
                sectorTotals.merge(sd.sectorCode(), sd.grossRevenue(), Double::sum);
            }
            List<LabelValue> sectors = sectorTotals.entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .map(e -> new LabelValue(e.getKey(), e.getValue())).toList();

            Map<String, Double> paymentTotals = new java.util.LinkedHashMap<>();
            for (PaymentStatus ps : hw.paymentStatus()) {
                paymentTotals.merge(ps.paymentMethod(), ps.totalAmount(), Double::sum);
            }
            List<LabelValue> payments = paymentTotals.entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .map(e -> new LabelValue(e.getKey(), e.getValue())).toList();

            List<MatchBar> matches = hw.topMatches().stream().limit(5)
                    .map(m -> new MatchBar(m.homeTeam() + " x " + m.awayTeam(), m.grossRevenue(), m.ticketsIssued()))
                    .toList();

            List<HeatBlockItem> heatBlocks = hw.heatmap().stream()
                    .map(h -> new HeatBlockItem(h.sectorCode() + "-" + h.blockCode(), h.heatPercent()))
                    .toList();

            List<Event> events = new ArrayList<>();
            events.add(new Event("Leitura analítica em HeatWave",
                    hw.engine() + "; " + hw.loadStatus().loadedTables() + "/" + hw.loadStatus().baseTables() + " tabelas carregadas."));
            if (!hw.topMatches().isEmpty()) {
                TopMatch top = hw.topMatches().get(0);
                events.add(new Event("Jogo líder: " + top.homeTeam() + " x " + top.awayTeam(),
                        top.ticketsIssued() + " ingressos, $" + String.format("%,.0f", top.grossRevenue()) + "."));
            }

            DashboardStatus status = new DashboardStatus(
                    true, jobManager.isRunning(),
                    summary, countries, sectors, payments, matches, heatBlocks, events
            );
            JsonUtil.ok(res, status);
        } catch (SQLException e) {
            JsonUtil.error(res, 500, "Database error: " + e.getMessage());
        } catch (Exception e) {
            JsonUtil.error(res, 500, "Error loading dashboard: " + e.getMessage());
        }
    }

    private void startSellout(ServerRequest req, ServerResponse res) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = JsonUtil.MAPPER.readValue(req.content().as(byte[].class), Map.class);
            int matchNumber = toInt(body.getOrDefault("matchNumber", 68));
            int orderSize = toInt(body.getOrDefault("orderSize", 4));
            int batchOrders = toInt(body.getOrDefault("batchOrders", 500));
            int maxSeats = toInt(body.getOrDefault("maxSeats", 0));

            @SuppressWarnings("unchecked")
            Map<String, Object> mix = body.containsKey("statusMix")
                    ? (Map<String, Object>) body.get("statusMix") : Map.of();
            double reserved = toDouble(mix.getOrDefault("reservedPercent", 0));
            double pending = toDouble(mix.getOrDefault("paymentPendingPercent", 0));
            double issued = toDouble(mix.getOrDefault("issuedPercent", 100));

            SelloutConfig cfg = new SelloutConfig(matchNumber, orderSize, batchOrders, maxSeats,
                    reserved, pending, issued, 0.74);
            jobManager.start(matchNumber, cfg);
            JsonUtil.ok(res, Map.of("ok", true, "started", true));
        } catch (IllegalStateException e) {
            JsonUtil.error(res, 409, e.getMessage());
        } catch (IllegalArgumentException e) {
            JsonUtil.error(res, 400, e.getMessage());
        } catch (SQLException e) {
            JsonUtil.error(res, 500, "Database error: " + e.getMessage());
        } catch (Exception e) {
            JsonUtil.error(res, 500, "Error starting sellout: " + e.getMessage());
        }
    }

    private void resetSellout(ServerRequest req, ServerResponse res) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = JsonUtil.MAPPER.readValue(req.content().as(byte[].class), Map.class);
            int matchNumber = toInt(body.getOrDefault("matchNumber", 68));
            jobManager.reset(matchNumber);
            JsonUtil.ok(res, Map.of("ok", true));
        } catch (IllegalStateException e) {
            JsonUtil.error(res, 409, e.getMessage());
        } catch (SQLException e) {
            JsonUtil.error(res, 500, "Database error: " + e.getMessage());
        } catch (Exception e) {
            JsonUtil.error(res, 500, "Error resetting: " + e.getMessage());
        }
    }

    private HeatwaveAnalytics cachedHeatwave() throws SQLException {
        long now = System.currentTimeMillis();
        HeatwaveAnalytics cached = hwCached;
        if (cached != null && hwCacheExpiry > now) return cached;

        if (hwLock.tryLock()) {
            try {
                now = System.currentTimeMillis();
                cached = hwCached;
                if (cached != null && hwCacheExpiry > now) return cached;
                hwCached = hwRepo.getAll();
                hwCacheExpiry = System.currentTimeMillis() + HW_CACHE_TTL_MS;
                return hwCached;
            } finally {
                hwLock.unlock();
            }
        }

        if (cached != null) return cached;

        hwLock.lock();
        try {
            cached = hwCached;
            if (cached != null) return cached;
            hwCached = hwRepo.getAll();
            hwCacheExpiry = System.currentTimeMillis() + HW_CACHE_TTL_MS;
            return hwCached;
        } finally {
            hwLock.unlock();
        }
    }

    private static int toInt(Object v) {
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) return Integer.parseInt(s);
        return 0;
    }

    private static double toDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) return Double.parseDouble(s);
        return 0.0;
    }
}
