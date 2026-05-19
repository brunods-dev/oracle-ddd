package com.copa.ticketing.repository;

import com.copa.ticketing.domain.Order;
import com.copa.ticketing.domain.Ticket;
import com.copa.ticketing.util.DocumentNumbers;
import com.copa.ticketing.pagination.Page;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class OrderRepository {

    private final DataSource ds;

    public OrderRepository(DataSource ds) {
        this.ds = ds;
    }

    public Order createFromReservation(long reservationId, long customerId, double amount,
                                       LocalDateTime paymentExpiresAt) throws SQLException {
        String code = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String pixRef = "PIX-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        LocalDateTime expiresAt = paymentExpiresAt != null
                ? paymentExpiresAt
                : LocalDateTime.now().plusMinutes(15);

        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);
            try {
                long orderId;
                String orderSql = """
                        INSERT INTO orders (order_code, reservation_id, customer_id, status,
                            total_amount, created_at, updated_at)
                        VALUES (?, ?, ?, 'PAYMENT_PENDING', ?, NOW(), NOW())
                        """;
                try (PreparedStatement ps = conn.prepareStatement(orderSql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, code);
                    ps.setLong(2, reservationId);
                    ps.setLong(3, customerId);
                    ps.setDouble(4, amount);
                    ps.executeUpdate();
                    ResultSet keys = ps.getGeneratedKeys();
                    keys.next();
                    orderId = keys.getLong(1);
                }

                String paymentSql = """
                        INSERT INTO payments (order_id, payment_method, status, amount,
                            payment_reference, expires_at, created_at, updated_at)
                        VALUES (?, 'DIGITAL_WALLET', 'PENDING', ?, ?, ?, NOW(), NOW())
                        """;
                try (PreparedStatement ps = conn.prepareStatement(paymentSql)) {
                    ps.setLong(1, orderId);
                    ps.setDouble(2, amount);
                    ps.setString(3, pixRef);
                    ps.setTimestamp(4, Timestamp.valueOf(expiresAt));
                    ps.executeUpdate();
                }

                conn.commit();
                return new Order(orderId, code, customerId, null, null, "PIX", "PAYMENT_PENDING",
                        amount, pixRef, LocalDateTime.now());
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public Optional<Order> findByPaymentReference(String ref) throws SQLException {
        String sql = """
                SELECT o.id, o.order_code, o.customer_id, c.full_name, c.email,
                       p.payment_method, o.status, p.amount, p.payment_reference, o.created_at
                FROM payments p
                JOIN orders o ON o.id = p.order_id
                JOIN customers c ON o.customer_id = c.id
                WHERE p.payment_reference = ?
                """;
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ref);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(map(rs));
        }
        return Optional.empty();
    }

    public void confirmPayment(long orderId) throws SQLException {
        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE orders SET status = 'PAID', updated_at = NOW() WHERE id = ?")) {
                    ps.setLong(1, orderId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE payments SET status = 'PAID', paid_at = NOW(), updated_at = NOW() WHERE order_id = ?")) {
                    ps.setLong(1, orderId);
                    ps.executeUpdate();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public Page<Order> findAll(String status, int page, int size) throws SQLException {
        String dbStatus = mapOrderStatusFilter(status);
        String whereClause = (dbStatus != null) ? " WHERE o.status = ?" : "";
        String countSql = "SELECT COUNT(*) FROM orders o" + whereClause;
        String dataSql = """
                SELECT o.id, o.order_code, o.customer_id, c.full_name, c.email,
                       p.payment_method, o.status, o.total_amount AS amount, p.payment_reference, o.created_at
                FROM orders o
                JOIN customers c ON o.customer_id = c.id
                LEFT JOIN payments p ON p.order_id = o.id
                """ + whereClause + " ORDER BY o.created_at DESC LIMIT ? OFFSET ?";

        try (Connection conn = ds.getConnection()) {
            long total;
            try (PreparedStatement ps = conn.prepareStatement(countSql)) {
                if (dbStatus != null) ps.setString(1, dbStatus);
                ResultSet rs = ps.executeQuery();
                rs.next();
                total = rs.getLong(1);
            }
            List<Order> items = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(dataSql)) {
                int idx = 1;
                if (dbStatus != null) ps.setString(idx++, dbStatus);
                ps.setInt(idx++, size);
                ps.setInt(idx, page * size);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) items.add(map(rs));
            }
            return new Page<>(items, page, size, total);
        }
    }

    public List<Ticket> issueTickets(long orderId, long customerId) throws SQLException {
        String seatsSql = """
                SELECT msa.id AS allocation_id, msa.venue_seat_id, msa.match_sector_id, msa.match_id,
                       msa.reservation_item_id, vs.seat_label, vs.gate, vs.entrance, ri.unit_price
                FROM match_seat_allocations msa
                JOIN orders o ON o.reservation_id = msa.reservation_id
                JOIN venue_seats vs ON vs.id = msa.venue_seat_id
                JOIN reservation_items ri ON ri.id = msa.reservation_item_id
                WHERE o.id = ? AND msa.status = 'RESERVED'
                """;
        String ticketSql = """
                INSERT INTO tickets (ticket_code, order_id, reservation_item_id, customer_id, match_id,
                    match_sector_id, match_seat_allocation_id, venue_seat_id, seat_label, gate, entrance,
                    qr_code, status, issued_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ISSUED', NOW(), NOW(), NOW())
                """;

        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);
            try {
                List<Object[]> seatData = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement(seatsSql)) {
                    ps.setLong(1, orderId);
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) {
                        seatData.add(new Object[]{
                                rs.getLong("allocation_id"),
                                rs.getLong("venue_seat_id"),
                                rs.getLong("match_sector_id"),
                                rs.getLong("match_id"),
                                rs.getLong("reservation_item_id"),
                                rs.getString("seat_label"),
                                rs.getString("gate"),
                                rs.getString("entrance"),
                                rs.getDouble("unit_price")
                        });
                    }
                }

                if (seatData.isEmpty()) {
                    throw new SQLException("No reserved seats found for order " + orderId);
                }

                try (PreparedStatement ps = conn.prepareStatement(ticketSql)) {
                    for (Object[] row : seatData) {
                        String tCode = "TKT-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();
                        String qrCode = "QR-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
                        ps.setString(1, tCode);
                        ps.setLong(2, orderId);
                        ps.setLong(3, (Long) row[4]);
                        ps.setLong(4, customerId);
                        ps.setLong(5, (Long) row[3]);
                        ps.setLong(6, (Long) row[2]);
                        ps.setLong(7, (Long) row[0]);
                        ps.setLong(8, (Long) row[1]);
                        ps.setString(9, (String) row[5]);
                        ps.setString(10, (String) row[6]);
                        ps.setString(11, (String) row[7]);
                        ps.setString(12, qrCode);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                String allocUpdate = """
                        UPDATE match_seat_allocations msa
                        JOIN orders o ON o.reservation_id = msa.reservation_id
                        SET msa.status = 'SOLD', msa.order_id = ?, msa.updated_at = NOW()
                        WHERE o.id = ? AND msa.status = 'RESERVED'
                        """;
                try (PreparedStatement ps = conn.prepareStatement(allocUpdate)) {
                    ps.setLong(1, orderId);
                    ps.setLong(2, orderId);
                    ps.executeUpdate();
                }

                String sectorUpdate = """
                        UPDATE match_sectors ms
                        JOIN reservation_items ri ON ri.match_sector_id = ms.id
                        JOIN orders o ON o.reservation_id = ri.reservation_id
                        SET ms.reserved_quantity = GREATEST(0, ms.reserved_quantity - ri.quantity),
                            ms.sold_quantity = ms.sold_quantity + ri.quantity,
                            ms.updated_at = NOW()
                        WHERE o.id = ?
                        """;
                try (PreparedStatement ps = conn.prepareStatement(sectorUpdate)) {
                    ps.setLong(1, orderId);
                    ps.executeUpdate();
                }

                String resUpdate = """
                        UPDATE reservations r
                        JOIN orders o ON o.reservation_id = r.id
                        SET r.status = 'CONVERTED', r.updated_at = NOW()
                        WHERE o.id = ?
                        """;
                try (PreparedStatement ps = conn.prepareStatement(resUpdate)) {
                    ps.setLong(1, orderId);
                    ps.executeUpdate();
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }

        return findTicketsByOrder(orderId);
    }

    public List<Ticket> findTicketsByCustomerDocument(String lookupRaw) throws SQLException {
        if (lookupRaw == null || lookupRaw.isBlank()) return List.of();

        if (DocumentNumbers.isEmailLookup(lookupRaw)) {
            return findTicketsByCustomerEmail(DocumentNumbers.normalizeEmailForLookup(lookupRaw));
        }

        String lookup = DocumentNumbers.normalizeForLookup(lookupRaw);
        if (lookup.isBlank()) return List.of();

        String cpfDigitsExpr = "REPLACE(REPLACE(REPLACE(c.document_number, '.', ''), '-', ''), ' ', '')";
        String where = DocumentNumbers.isCpfLookup(lookup)
                ? "c.document_number = ? OR (c.document_type = 'CPF' AND " + cpfDigitsExpr + " = ?)"
                : "c.document_number = ?";

        String sql = ticketListSql(where);

        if (DocumentNumbers.isCpfLookup(lookup)) {
            return queryTickets(sql, lookup, lookup);
        }
        return queryTickets(sql, lookup);
    }

    private List<Ticket> findTicketsByCustomerEmail(String email) throws SQLException {
        if (email.isBlank()) return List.of();
        return queryTickets(ticketListSql("LOWER(c.email) = ?"), email);
    }

    private static String ticketListSql(String customerWhere) {
        return """
                SELECT t.id, t.ticket_code, t.order_id, t.customer_id,
                       t.match_id, m.match_number,
                       ht.team_name AS home_team, at.team_name AS away_team,
                       v.name AS venue_name, v.city,
                       m.match_at,
                       ms.sector_name,
                       t.seat_label, t.gate,
                       ri.unit_price, t.status, t.issued_at
                FROM tickets t
                JOIN customers c ON t.customer_id = c.id
                JOIN matches m ON t.match_id = m.id
                JOIN venues v ON m.venue_id = v.id
                LEFT JOIN national_teams ht ON m.home_team_id = ht.id
                LEFT JOIN national_teams at ON m.away_team_id = at.id
                JOIN match_sectors ms ON t.match_sector_id = ms.id
                LEFT JOIN reservation_items ri ON ri.id = t.reservation_item_id
                WHERE %s
                ORDER BY m.match_at ASC
                """.formatted(customerWhere);
    }

    private List<Ticket> findTicketsByOrder(long orderId) throws SQLException {
        String sql = """
                SELECT t.id, t.ticket_code, t.order_id, t.customer_id,
                       t.match_id, m.match_number,
                       ht.team_name AS home_team, at.team_name AS away_team,
                       v.name AS venue_name, v.city,
                       m.match_at,
                       ms.sector_name,
                       t.seat_label, t.gate,
                       ri.unit_price, t.status, t.issued_at
                FROM tickets t
                JOIN matches m ON t.match_id = m.id
                JOIN venues v ON m.venue_id = v.id
                LEFT JOIN national_teams ht ON m.home_team_id = ht.id
                LEFT JOIN national_teams at ON m.away_team_id = at.id
                JOIN match_sectors ms ON t.match_sector_id = ms.id
                LEFT JOIN reservation_items ri ON ri.id = t.reservation_item_id
                WHERE t.order_id = ?
                ORDER BY t.id ASC
                """;
        List<Ticket> result = new ArrayList<>();
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, orderId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) result.add(mapTicket(rs));
        }
        return result;
    }

    private List<Ticket> queryTickets(String sql, String... params) throws SQLException {
        List<Ticket> result = new ArrayList<>();
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setString(i + 1, params[i]);
            }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) result.add(mapTicket(rs));
        }
        return result;
    }

    private static String mapOrderStatusFilter(String status) {
        if (status == null || status.isBlank()) return null;
        if ("PENDING_PAYMENT".equals(status)) return "PAYMENT_PENDING";
        return status;
    }

    private Order map(ResultSet rs) throws SQLException {
        Timestamp ts = rs.getTimestamp("created_at");
        String method = rs.getString("payment_method");
        return new Order(
                rs.getLong("id"),
                rs.getString("order_code"),
                rs.getLong("customer_id"),
                rs.getString("full_name"),
                rs.getString("email"),
                "DIGITAL_WALLET".equals(method) ? "PIX" : method,
                rs.getString("status"),
                rs.getDouble("amount"),
                rs.getString("payment_reference"),
                ts != null ? ts.toLocalDateTime() : null
        );
    }

    private Ticket mapTicket(ResultSet rs) throws SQLException {
        Timestamp mat = rs.getTimestamp("match_at");
        Timestamp iss = rs.getTimestamp("issued_at");
        return new Ticket(
                rs.getLong("id"),
                rs.getString("ticket_code"),
                rs.getLong("order_id"),
                rs.getLong("customer_id"),
                rs.getLong("match_id"),
                rs.getString("match_number"),
                rs.getString("home_team"),
                rs.getString("away_team"),
                rs.getString("venue_name"),
                rs.getString("city"),
                mat != null ? mat.toLocalDateTime() : null,
                rs.getString("sector_name"),
                rs.getString("seat_label"),
                rs.getString("gate"),
                rs.getDouble("unit_price"),
                rs.getString("status"),
                iss != null ? iss.toLocalDateTime() : null
        );
    }
}
