package com.copa.ticketing.repository;

import com.copa.ticketing.domain.DashboardSummary;
import com.copa.ticketing.domain.DashboardSummary.DailySale;
import com.copa.ticketing.domain.DashboardSummary.TopMatch;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DashboardRepository {

    private final DataSource ds;

    public DashboardRepository(DataSource ds) {
        this.ds = ds;
    }

    public DashboardSummary getSummary() throws SQLException {
        String kpiSql = """
                SELECT
                    COUNT(*) AS total_orders,
                    SUM(CASE WHEN status = 'PAID' THEN 1 ELSE 0 END) AS paid_orders,
                    COALESCE(SUM(CASE WHEN status = 'PAID' THEN total_amount ELSE 0 END), 0) AS gross_revenue,
                    (SELECT COUNT(*) FROM tickets WHERE status = 'ISSUED') AS tickets_sold,
                    (SELECT COUNT(*) FROM reservations WHERE status = 'RESERVED' AND expires_at > NOW()) AS active_reservations
                FROM orders
                """;

        String convSql = "SELECT conversion_percent FROM vw_reservation_conversion LIMIT 1";

        List<TopMatch> topMatches = getTopMatches();
        List<DailySale> dailySales = getDailySales();

        try (Connection conn = ds.getConnection()) {
            long total = 0, paid = 0, tickets = 0, activeRes = 0;
            double revenue = 0;
            try (PreparedStatement ps = conn.prepareStatement(kpiSql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    total = rs.getLong("total_orders");
                    paid = rs.getLong("paid_orders");
                    revenue = rs.getDouble("gross_revenue");
                    tickets = rs.getLong("tickets_sold");
                    activeRes = rs.getLong("active_reservations");
                }
            }

            double conversion = 0;
            try (PreparedStatement ps = conn.prepareStatement(convSql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) conversion = rs.getDouble("conversion_percent");
            } catch (SQLException ignored) {}

            return new DashboardSummary(total, paid, revenue, tickets, activeRes, conversion, topMatches, dailySales);
        }
    }

    private List<TopMatch> getTopMatches() throws SQLException {
        String sql = """
                SELECT match_id, match_number, home_team, away_team, venue_name,
                       city AS venue_city,
                       sold_seats AS tickets_sold,
                       match_capacity AS total_match_capacity,
                       gross_revenue,
                       occupancy_percent AS confirmed_occupancy_percent
                FROM vw_hw_match_business_scorecard
                ORDER BY sold_seats DESC
                LIMIT 10
                """;
        List<TopMatch> result = new ArrayList<>();
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new TopMatch(
                        rs.getLong("match_id"),
                        rs.getString("match_number"),
                        rs.getString("home_team"),
                        rs.getString("away_team"),
                        rs.getString("venue_name"),
                        rs.getString("venue_city"),
                        rs.getLong("tickets_sold"),
                        rs.getLong("total_match_capacity"),
                        rs.getDouble("gross_revenue"),
                        rs.getDouble("confirmed_occupancy_percent")
                ));
            }
        }
        return result;
    }

    private List<DailySale> getDailySales() throws SQLException {
        String sql = """
                SELECT DATE(created_at) AS sale_date,
                       COUNT(*) AS orders_created,
                       SUM(CASE WHEN status = 'PAID' THEN 1 ELSE 0 END) AS paid_orders,
                       COALESCE(SUM(CASE WHEN status = 'PAID' THEN total_amount ELSE 0 END), 0) AS gross_revenue
                FROM orders
                GROUP BY DATE(created_at)
                ORDER BY sale_date DESC
                LIMIT 30
                """;
        List<DailySale> result = new ArrayList<>();
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new DailySale(
                        rs.getString("sale_date"),
                        rs.getLong("orders_created"),
                        rs.getLong("paid_orders"),
                        rs.getDouble("gross_revenue")
                ));
            }
        }
        return result;
    }

    public Object getInventory(Long matchId) throws SQLException {
        String baseSql = """
                SELECT match_id, match_number, home_team, away_team, venue_name,
                       match_capacity AS total_match_capacity,
                       sold_seats AS tickets_sold,
                       reserved_only_seats AS active_reservations,
                       occupancy_percent AS confirmed_occupancy_percent,
                       gross_revenue
                FROM vw_hw_match_business_scorecard
                """;
        String sql = matchId != null ? baseSql + " WHERE match_id = ?" : baseSql;

        List<java.util.Map<String, Object>> result = new ArrayList<>();
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (matchId != null) ps.setLong(1, matchId);
            ResultSet rs = ps.executeQuery();
            ResultSetMetaData meta = rs.getMetaData();
            int cols = meta.getColumnCount();
            while (rs.next()) {
                java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
                for (int i = 1; i <= cols; i++) {
                    row.put(meta.getColumnLabel(i), rs.getObject(i));
                }
                result.add(row);
            }
        }
        return result;
    }

    public Object getPaymentSummary() throws SQLException {
        List<java.util.Map<String, Object>> result = new ArrayList<>();
        String sql = "SELECT * FROM vw_payment_status_summary";
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            ResultSetMetaData meta = rs.getMetaData();
            int cols = meta.getColumnCount();
            while (rs.next()) {
                java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
                for (int i = 1; i <= cols; i++) row.put(meta.getColumnLabel(i), rs.getObject(i));
                result.add(row);
            }
        }
        return result;
    }
}
