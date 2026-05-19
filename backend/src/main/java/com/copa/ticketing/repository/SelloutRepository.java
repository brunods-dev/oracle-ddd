package com.copa.ticketing.repository;

import com.copa.ticketing.domain.MatchOption;
import com.copa.ticketing.domain.SelloutStatus;
import com.copa.ticketing.domain.SelloutStatus.*;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SelloutRepository {

    private static final String[] RESET_TABLES = {
            "tickets", "payments", "orders",
            "match_seat_allocations", "reservation_items", "reservations"
    };

    private final DataSource ds;

    public SelloutRepository(DataSource ds) {
        this.ds = ds;
    }

    public List<MatchOption> loadMatchOptions() throws SQLException {
        String sql = """
                SELECT
                  m.id AS match_id,
                  m.match_number,
                  m.group_name,
                  m.match_at,
                  m.status AS match_status,
                  m.venue_capacity_snapshot AS capacity,
                  ht.team_name AS home_team,
                  at.team_name AS away_team,
                  v.name AS venue_name,
                  v.city,
                  v.country,
                  v.time_zone AS venue_time_zone,
                  COALESCE(SUM(ms.total_quantity), 0) AS total_quantity,
                  COALESCE(SUM(ms.reserved_quantity), 0) AS reserved_quantity,
                  COALESCE(SUM(ms.sold_quantity), 0) AS sold_quantity,
                  COALESCE(SUM(ms.total_quantity - ms.reserved_quantity - ms.sold_quantity), 0) AS available_quantity
                FROM matches m
                JOIN national_teams ht ON ht.id = m.home_team_id
                JOIN national_teams at ON at.id = m.away_team_id
                JOIN venues v ON v.id = m.venue_id
                LEFT JOIN match_sectors ms ON ms.match_id = m.id
                GROUP BY
                  m.id, m.match_number, m.group_name, m.match_at, m.status,
                  m.venue_capacity_snapshot, ht.team_name, at.team_name,
                  v.name, v.city, v.country, v.time_zone
                ORDER BY m.match_number
                """;
        List<MatchOption> result = new ArrayList<>();
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new MatchOption(
                        rs.getLong("match_id"),
                        rs.getString("match_number"),
                        rs.getString("group_name"),
                        rs.getString("match_at"),
                        rs.getString("match_status"),
                        rs.getLong("capacity"),
                        rs.getString("home_team"),
                        rs.getString("away_team"),
                        rs.getString("venue_name"),
                        rs.getString("city"),
                        rs.getString("country"),
                        rs.getString("venue_time_zone"),
                        rs.getLong("total_quantity"),
                        rs.getLong("reserved_quantity"),
                        rs.getLong("sold_quantity"),
                        rs.getLong("available_quantity")
                ));
            }
        }
        return result;
    }

    public SelloutStatus loadStatus(int matchNumber) throws SQLException {
        String matchSql = """
                SELECT
                  m.id AS match_id,
                  m.match_number,
                  m.status AS match_status,
                  m.venue_capacity_snapshot AS capacity,
                  ht.team_name AS home_team,
                  at.team_name AS away_team,
                  v.name AS venue_name,
                  v.city,
                  v.country,
                  v.time_zone AS venue_time_zone
                FROM matches m
                JOIN national_teams ht ON ht.id = m.home_team_id
                JOIN national_teams at ON at.id = m.away_team_id
                JOIN venues v ON v.id = m.venue_id
                WHERE m.match_number = ?
                """;

        String sectorSql = """
                SELECT
                  sector_code,
                  total_quantity,
                  reserved_quantity,
                  sold_quantity,
                  total_quantity - reserved_quantity - sold_quantity AS available_quantity,
                  status
                FROM match_sectors
                WHERE match_id = ?
                ORDER BY FIELD(sector_code, 'VIP','EAST','NORTH','SOUTH')
                """;

        String mixSql = """
                SELECT
                  COALESCE(SUM(CASE WHEN r.status = 'RESERVED' AND o.id IS NULL THEN ri.quantity ELSE 0 END), 0) AS reserved_only_seats,
                  COALESCE(SUM(CASE WHEN o.status = 'PAYMENT_PENDING' THEN ri.quantity ELSE 0 END), 0) AS payment_pending_seats,
                  COALESCE(SUM(CASE WHEN o.status = 'PAID' THEN ri.quantity ELSE 0 END), 0) AS issued_seats,
                  COUNT(DISTINCT CASE WHEN r.status = 'RESERVED' AND o.id IS NULL THEN r.id END) AS reserved_only_reservations,
                  COUNT(DISTINCT CASE WHEN o.status = 'PAYMENT_PENDING' THEN o.id END) AS payment_pending_orders,
                  COUNT(DISTINCT CASE WHEN o.status = 'PAID' THEN o.id END) AS paid_orders,
                  COALESCE(SUM(CASE WHEN o.status = 'PAID' THEN ri.line_total ELSE 0 END), 0) AS revenue
                FROM match_sectors ms
                STRAIGHT_JOIN reservation_items ri FORCE INDEX (idx_reservation_items_sector)
                  ON ri.match_sector_id = ms.id
                STRAIGHT_JOIN reservations r
                  ON r.id = ri.reservation_id
                LEFT JOIN orders o FORCE INDEX (uk_orders_reservation)
                  ON o.reservation_id = r.id
                WHERE ms.match_id = ?
                """;

        try (Connection conn = ds.getConnection()) {
            long matchId;
            String matchStatus;
            long capacity;
            String homeTeam, awayTeam, venueName, city, country, venueTimeZone;
            String matchNum;

            try (PreparedStatement ps = conn.prepareStatement(matchSql)) {
                ps.setInt(1, matchNumber);
                ResultSet rs = ps.executeQuery();
                if (!rs.next()) throw new IllegalArgumentException("Match " + matchNumber + " not found");
                matchId = rs.getLong("match_id");
                matchNum = rs.getString("match_number");
                matchStatus = rs.getString("match_status");
                capacity = rs.getLong("capacity");
                homeTeam = rs.getString("home_team");
                awayTeam = rs.getString("away_team");
                venueName = rs.getString("venue_name");
                city = rs.getString("city");
                country = rs.getString("country");
                venueTimeZone = rs.getString("venue_time_zone");
            }

            List<SectorStatus> sectors = new ArrayList<>();
            long totalTotal = 0, totalReserved = 0, totalSold = 0, totalAvailable = 0;
            try (PreparedStatement ps = conn.prepareStatement(sectorSql)) {
                ps.setLong(1, matchId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    long tot = rs.getLong("total_quantity");
                    long res = rs.getLong("reserved_quantity");
                    long sol = rs.getLong("sold_quantity");
                    long ava = rs.getLong("available_quantity");
                    totalTotal += tot;
                    totalReserved += res;
                    totalSold += sol;
                    totalAvailable += ava;
                    sectors.add(new SectorStatus(
                            rs.getString("sector_code"),
                            tot, res, sol, ava, rs.getString("status")
                    ));
                }
            }

            Totals totals = new Totals(totalTotal, totalReserved, totalSold, totalAvailable);
            StatusMix mix = new StatusMix(0, 0, 0, 0, 0, 0);
            long paidOrders = 0;
            double revenue = 0;

            if (totalReserved > 0 || totalSold > 0) {
                try (PreparedStatement ps = conn.prepareStatement(mixSql)) {
                    ps.setLong(1, matchId);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        mix = new StatusMix(
                                rs.getLong("reserved_only_seats"),
                                rs.getLong("payment_pending_seats"),
                                rs.getLong("issued_seats"),
                                rs.getLong("reserved_only_reservations"),
                                rs.getLong("payment_pending_orders"),
                                rs.getLong("paid_orders")
                        );
                        paidOrders = rs.getLong("paid_orders");
                        revenue = rs.getDouble("revenue");
                    }
                } catch (SQLException ignored) {}
            }

            double progressPercent = totalTotal > 0
                    ? ((double) (totalSold + totalReserved) / totalTotal) * 100.0 : 0.0;

            return new SelloutStatus(
                    matchId, matchNum, matchStatus, capacity,
                    homeTeam, awayTeam, venueName, city, country, venueTimeZone,
                    sectors, totals, mix, paidOrders, revenue,
                    0, 0,
                    false, null,
                    progressPercent, List.of()
            );
        }
    }

    public void resetDemoTransactions() throws SQLException {
        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);
            try {
                conn.createStatement().execute("SET FOREIGN_KEY_CHECKS = 0");
                for (String table : RESET_TABLES) {
                    conn.createStatement().execute("TRUNCATE TABLE " + table);
                }
                conn.createStatement().execute("SET FOREIGN_KEY_CHECKS = 1");

                conn.createStatement().execute("""
                        DELETE FROM customers
                        WHERE email LIKE 'simulated.buyer+%@example.com'
                           OR email LIKE 'sellout.buyer+%@example.com'
                        """);

                conn.createStatement().execute("""
                        UPDATE match_sectors
                        SET reserved_quantity = 0,
                            sold_quantity = 0,
                            status = 'AVAILABLE'
                        """);

                conn.createStatement().execute("""
                        UPDATE matches
                        SET status = 'AVAILABLE'
                        WHERE status = 'SOLD_OUT'
                        """);

                conn.commit();
            } catch (SQLException e) {
                conn.createStatement().execute("SET FOREIGN_KEY_CHECKS = 1");
                conn.rollback();
                throw e;
            }
        }
    }
}
