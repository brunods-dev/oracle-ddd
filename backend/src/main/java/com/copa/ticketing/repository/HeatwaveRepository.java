package com.copa.ticketing.repository;

import com.copa.ticketing.domain.HeatwaveAnalytics;
import com.copa.ticketing.domain.HeatwaveAnalytics.*;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

public class HeatwaveRepository {

    private static final String HINT = "/*+ SET_VAR(use_secondary_engine=FORCED) */";

    private final DataSource ds;

    public HeatwaveRepository(DataSource ds) {
        this.ds = ds;
    }

    public HeatwaveAnalytics getAll() throws SQLException {
        try (Connection conn = ds.getConnection()) {
            Summary summary = querySummary(conn);
            List<TopMatch> topMatches = queryTopMatches(conn);
            List<HostCountry> hostCountries = queryHostCountries(conn);
            List<SectorDemand> sectorDemand = querySectorDemand(conn);
            List<PaymentStatus> paymentStatus = queryPaymentStatus(conn);
            List<HeatBlock> heatmap = sampleHeatmapRows(queryHeatmap(conn));
            LoadStatus loadStatus = queryLoadStatus(conn);
            return new HeatwaveAnalytics(true, "HeatWave/RAPID",
                    "SET_VAR(use_secondary_engine=FORCED)",
                    summary, topMatches, hostCountries, sectorDemand, paymentStatus, heatmap, loadStatus);
        }
    }

    private Summary querySummary(Connection conn) throws SQLException {
        String sql = "SELECT " + HINT + " * FROM vw_hw_realtime_executive_dashboard";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return new Summary(
                        rs.getDouble("gross_revenue"),
                        rs.getLong("tickets_issued"),
                        rs.getLong("paid_orders"),
                        rs.getLong("active_reservations"),
                        rs.getLong("reservations_created"),
                        rs.getLong("converted_reservations"),
                        rs.getDouble("payment_pending_amount"),
                        rs.getDouble("conversion_percent"),
                        rs.getDouble("occupancy_percent")
                );
            }
        }
        return new Summary(0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private List<TopMatch> queryTopMatches(Connection conn) throws SQLException {
        String sql = """
                SELECT %s
                  match_number, home_team, away_team, venue_name, city, country,
                  occupancy_percent, gross_revenue, tickets_issued,
                  payment_pending_orders, payment_pending_amount
                FROM vw_hw_match_business_scorecard
                ORDER BY gross_revenue DESC, occupancy_percent DESC, match_number
                LIMIT 8
                """.formatted(HINT);
        List<TopMatch> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new TopMatch(
                        rs.getString("match_number"),
                        rs.getString("home_team"),
                        rs.getString("away_team"),
                        rs.getString("venue_name"),
                        rs.getString("city"),
                        rs.getString("country"),
                        rs.getDouble("occupancy_percent"),
                        rs.getDouble("gross_revenue"),
                        rs.getLong("tickets_issued"),
                        rs.getLong("payment_pending_orders"),
                        rs.getDouble("payment_pending_amount")
                ));
            }
        }
        return result;
    }

    private List<HostCountry> queryHostCountries(Connection conn) throws SQLException {
        String sql = """
                SELECT %s
                  country, matches_count, total_match_capacity, paid_orders,
                  tickets_issued, gross_revenue, confirmed_occupancy_percent
                FROM vw_hw_host_country_business_revenue
                ORDER BY gross_revenue DESC, country
                """.formatted(HINT);
        List<HostCountry> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new HostCountry(
                        rs.getString("country"),
                        rs.getLong("matches_count"),
                        rs.getLong("total_match_capacity"),
                        rs.getLong("paid_orders"),
                        rs.getLong("tickets_issued"),
                        rs.getDouble("gross_revenue"),
                        rs.getDouble("confirmed_occupancy_percent")
                ));
            }
        }
        return result;
    }

    private List<SectorDemand> querySectorDemand(Connection conn) throws SQLException {
        String sql = """
                SELECT %s
                  match_number, home_team, away_team, venue_name,
                  sector_code, occupancy_percent, gross_revenue, tickets_issued, available_quantity
                FROM vw_hw_sector_business_demand
                ORDER BY gross_revenue DESC, occupancy_percent DESC
                LIMIT 12
                """.formatted(HINT);
        List<SectorDemand> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new SectorDemand(
                        rs.getString("match_number"),
                        rs.getString("home_team"),
                        rs.getString("away_team"),
                        rs.getString("venue_name"),
                        rs.getString("sector_code"),
                        rs.getDouble("occupancy_percent"),
                        rs.getDouble("gross_revenue"),
                        rs.getLong("tickets_issued"),
                        rs.getLong("available_quantity")
                ));
            }
        }
        return result;
    }

    private List<PaymentStatus> queryPaymentStatus(Connection conn) throws SQLException {
        String sql = """
                SELECT %s
                  payment_method, payment_status, payment_count, total_amount, avg_payment_amount
                FROM vw_hw_payment_method_business_summary
                ORDER BY payment_method, payment_status
                """.formatted(HINT);
        List<PaymentStatus> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new PaymentStatus(
                        rs.getString("payment_method"),
                        rs.getString("payment_status"),
                        rs.getLong("payment_count"),
                        rs.getDouble("total_amount"),
                        rs.getDouble("avg_payment_amount")
                ));
            }
        }
        return result;
    }

    private List<HeatBlock> queryHeatmap(Connection conn) throws SQLException {
        String sql = """
                SELECT %s
                  match_number, home_team, away_team, sector_code, block_code,
                  heat_percent, demand_band, total_seats, reserved_seats, sold_seats
                FROM vw_hw_seat_heatmap_business_live
                ORDER BY heat_percent DESC, sold_seats DESC, reserved_seats DESC
                """.formatted(HINT);
        List<HeatBlock> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new HeatBlock(
                        rs.getString("match_number"),
                        rs.getString("home_team"),
                        rs.getString("away_team"),
                        rs.getString("sector_code"),
                        rs.getString("block_code"),
                        rs.getDouble("heat_percent"),
                        rs.getString("demand_band"),
                        rs.getLong("total_seats"),
                        rs.getLong("reserved_seats"),
                        rs.getLong("sold_seats")
                ));
            }
        }
        return result;
    }

    private LoadStatus queryLoadStatus(Connection conn) throws SQLException {
        String sql = """
                SELECT
                  COUNT(*) AS base_tables,
                  SUM(create_options LIKE '%SECONDARY_ENGINE="RAPID"%') AS rapid_tables,
                  SUM(create_options LIKE '%SECONDARY_LOAD="1"%') AS loaded_tables
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_type = 'BASE TABLE'
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return new LoadStatus(
                        rs.getLong("base_tables"),
                        rs.getLong("rapid_tables"),
                        rs.getLong("loaded_tables")
                );
            }
        }
        return new LoadStatus(0, 0, 0);
    }

    private static List<HeatBlock> sampleHeatmapRows(List<HeatBlock> rows) {
        String[] bandOrder = {"CRITICAL_DEMAND", "HIGH_DEMAND", "MEDIUM_DEMAND", "LOW_DEMAND"};
        List<HeatBlock> selected = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (String band : bandOrder) {
            rows.stream()
                    .filter(r -> band.equals(r.demandBand()))
                    .sorted(Comparator
                            .comparingDouble(HeatBlock::heatPercent).reversed()
                            .thenComparingLong(HeatBlock::soldSeats).reversed()
                            .thenComparingLong(HeatBlock::reservedSeats).reversed())
                    .limit(2)
                    .forEach(r -> {
                        String key = r.matchNumber() + ":" + r.sectorCode() + ":" + r.blockCode();
                        if (seen.add(key)) selected.add(r);
                    });
        }
        return selected.subList(0, Math.min(selected.size(), 8));
    }
}
