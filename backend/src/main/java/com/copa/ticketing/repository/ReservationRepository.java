package com.copa.ticketing.repository;

import com.copa.ticketing.domain.Reservation;

import javax.sql.DataSource;
import java.sql.*;
import java.util.List;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public class ReservationRepository {

    private final DataSource ds;

    public ReservationRepository(DataSource ds) {
        this.ds = ds;
    }

    public Reservation create(long matchId, long customerId, long matchSectorId,
                              List<Long> seatIds, double unitPrice, int expiryMinutes) throws SQLException {
        String code = "RES-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(expiryMinutes);
        double total = unitPrice * seatIds.size();

        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);
            try {
                long reservationId;
                String resSql = """
                        INSERT INTO reservations (reservation_code, customer_id, status,
                            total_amount, expires_at, created_at, updated_at)
                        VALUES (?, ?, 'RESERVED', ?, ?, NOW(), NOW())
                        """;
                try (PreparedStatement ps = conn.prepareStatement(resSql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, code);
                    ps.setLong(2, customerId);
                    ps.setDouble(3, total);
                    ps.setTimestamp(4, Timestamp.valueOf(expiresAt));
                    ps.executeUpdate();
                    ResultSet keys = ps.getGeneratedKeys();
                    keys.next();
                    reservationId = keys.getLong(1);
                }

                long reservationItemId;
                String itemSql = """
                        INSERT INTO reservation_items (reservation_id, match_sector_id,
                            quantity, unit_price, line_total, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, NOW(), NOW())
                        """;
                try (PreparedStatement ps = conn.prepareStatement(itemSql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setLong(1, reservationId);
                    ps.setLong(2, matchSectorId);
                    ps.setInt(3, seatIds.size());
                    ps.setDouble(4, unitPrice);
                    ps.setDouble(5, total);
                    ps.executeUpdate();
                    ResultSet keys = ps.getGeneratedKeys();
                    keys.next();
                    reservationItemId = keys.getLong(1);
                }

                String allocSql = """
                        INSERT INTO match_seat_allocations (match_id, match_sector_id, venue_seat_id,
                            active_venue_seat_id, reservation_id, reservation_item_id, status,
                            allocated_at, created_at, updated_at)
                        SELECT ?, ?, vs.id, vs.id, ?, ?, 'RESERVED', NOW(), NOW(), NOW()
                        FROM venue_seats vs
                        WHERE vs.id = ?
                          AND NOT EXISTS (
                              SELECT 1 FROM match_seat_allocations msa
                              WHERE msa.match_id = ? AND msa.active_venue_seat_id = vs.id
                                AND msa.status IN ('RESERVED', 'SOLD', 'BLOCKED')
                          )
                        """;
                try (PreparedStatement ps = conn.prepareStatement(allocSql)) {
                    for (Long seatId : seatIds) {
                        ps.setLong(1, matchId);
                        ps.setLong(2, matchSectorId);
                        ps.setLong(3, reservationId);
                        ps.setLong(4, reservationItemId);
                        ps.setLong(5, seatId);
                        ps.setLong(6, matchId);
                        if (ps.executeUpdate() != 1) {
                            throw new SQLException("Seat " + seatId + " is not available");
                        }
                    }
                }

                String sectorSql = """
                        UPDATE match_sectors
                        SET reserved_quantity = reserved_quantity + ?, updated_at = NOW()
                        WHERE id = ?
                        """;
                try (PreparedStatement ps = conn.prepareStatement(sectorSql)) {
                    ps.setInt(1, seatIds.size());
                    ps.setLong(2, matchSectorId);
                    ps.executeUpdate();
                }

                conn.commit();
                return new Reservation(reservationId, code, matchId, customerId, "RESERVED", total, expiresAt, LocalDateTime.now());
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public Optional<Reservation> findByCode(String code) throws SQLException {
        String sql = """
                SELECT r.id, r.reservation_code,
                       (SELECT ms.match_id FROM reservation_items ri
                        JOIN match_sectors ms ON ri.match_sector_id = ms.id
                        WHERE ri.reservation_id = r.id LIMIT 1) AS match_id,
                       r.customer_id, r.status, r.total_amount, r.expires_at, r.created_at
                FROM reservations r
                WHERE r.reservation_code = ?
                """;
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(map(rs));
        }
        return Optional.empty();
    }

    public void expireStale() throws SQLException {
        String sql = """
                UPDATE reservations SET status = 'EXPIRED', updated_at = NOW()
                WHERE status = 'RESERVED' AND expires_at < NOW()
                """;
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
        }
    }

    private Reservation map(ResultSet rs) throws SQLException {
        Timestamp exp = rs.getTimestamp("expires_at");
        Timestamp cre = rs.getTimestamp("created_at");
        return new Reservation(
                rs.getLong("id"),
                rs.getString("reservation_code"),
                rs.getLong("match_id"),
                rs.getLong("customer_id"),
                rs.getString("status"),
                rs.getDouble("total_amount"),
                exp != null ? exp.toLocalDateTime() : null,
                cre != null ? cre.toLocalDateTime() : null
        );
    }
}
