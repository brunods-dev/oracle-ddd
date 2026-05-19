package com.copa.ticketing.service;

import javax.sql.DataSource;
import java.security.SecureRandom;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.BiConsumer;

public class MatchSelloutSimulator {

    private final DataSource ds;

    public MatchSelloutSimulator(DataSource ds) {
        this.ds = ds;
    }

    public void run(SelloutConfig cfg, BiConsumer<String, SelloutTotals> onBatch) throws Exception {
        SecureRandom rng = new SecureRandom();
        String runId = buildRunId(rng);
        String codeRunId = runId.replace("SELLOUT-", "SO-").replace("-", "");

        MatchInfo match = loadMatch(cfg.matchNumber());
        Availability avail = loadAvailability(match.id());
        int targetSeats = cfg.maxSeats() > 0
                ? Math.min(cfg.maxSeats(), (int) avail.availableSeats())
                : (int) avail.availableSeats();

        if (targetSeats <= 0) return;

        List<SectorSeats> sectors = loadAvailableSeats(match.id(), targetSeats);

        SelloutTotals totals = new SelloutTotals();
        int[] orderSeq = {0};
        int[] ticketSeq = {0};

        for (SectorSeats sector : sectors) {
            sellSector(match, sector, cfg, totals, rng, codeRunId, orderSeq, ticketSeq, onBatch);
        }

        markSoldOutIfComplete(match.id());
    }

    private void sellSector(MatchInfo match, SectorSeats sector, SelloutConfig cfg,
                             SelloutTotals totals, SecureRandom rng, String codeRunId,
                             int[] orderSeq, int[] ticketSeq,
                             BiConsumer<String, SelloutTotals> onBatch) throws SQLException {
        int maxSeatsPerBatch = cfg.batchOrders() * cfg.orderSize();
        int offset = 0;
        while (offset < sector.seats().size()) {
            List<VenueSeat> batchSeats = sector.seats().subList(offset, Math.min(offset + maxSeatsPerBatch, sector.seats().size()));
            offset += batchSeats.size();
            sellBatch(match, sector, batchSeats, cfg, totals, rng, codeRunId, orderSeq, ticketSeq);
            totals.batches++;
            onBatch.accept(totals.toBatchMessage(sector.sectorCode()), totals);
        }
    }

    private void sellBatch(MatchInfo match, SectorSeats sector, List<VenueSeat> batchSeats,
                            SelloutConfig cfg, SelloutTotals totals, SecureRandom rng, String codeRunId,
                            int[] orderSeq, int[] ticketSeq) throws SQLException {
        List<List<VenueSeat>> groups = chunk(batchSeats, cfg.orderSize());
        int[] stageCounts = purchaseStageCounts(groups.size(), cfg);

        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Lock sector stock
                try (PreparedStatement ps = conn.prepareStatement("""
                        UPDATE match_sectors
                        SET reserved_quantity = reserved_quantity + ?
                        WHERE id = ?
                          AND status = 'AVAILABLE'
                          AND total_quantity - reserved_quantity - sold_quantity >= ?
                        """)) {
                    ps.setInt(1, batchSeats.size());
                    ps.setLong(2, sector.matchSectorId());
                    ps.setInt(3, batchSeats.size());
                    int rows = ps.executeUpdate();
                    if (rows != 1) throw new SQLException("Insufficient availability in sector " + sector.sectorCode());
                }

                // Build order plans
                List<OrderPlan> plans = new ArrayList<>();
                List<String> customerEmails = new ArrayList<>();
                List<Object[]> customerRows = new ArrayList<>();

                for (int i = 0; i < groups.size(); i++) {
                    orderSeq[0]++;
                    String token = codeRunId + "-" + Integer.toString(orderSeq[0], 36).toUpperCase();
                    String email = "sellout.buyer+" + token + "@example.com";
                    String phone = "+1-555-" + String.format("%07d", 1000000 + orderSeq[0]).substring(1);
                    customerEmails.add(email);
                    customerRows.add(new Object[]{
                            "Comprador Simulado " + token, email, "OTHER", "SELL-" + token, phone
                    });
                    int qty = groups.get(i).size();
                    double total = Math.round(qty * sector.price() * 100.0) / 100.0;
                    String stage = purchaseStageForIndex(i, stageCounts);
                    plans.add(new OrderPlan(
                            orderSeq[0], stage, email, groups.get(i), qty, total,
                            buildCode("RSV", orderSeq[0], codeRunId, rng),
                            buildCode("ORD", orderSeq[0], codeRunId, rng),
                            buildCode("PAYREF", orderSeq[0], codeRunId, rng),
                            Math.random() < cfg.paymentMethodCardRate() ? "CARD" : "DIGITAL_WALLET"
                    ));
                }

                // Insert customers
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO customers (full_name, email, document_type, document_number, phone) VALUES (?,?,?,?,?)")) {
                    for (Object[] row : customerRows) {
                        ps.setString(1, (String) row[0]);
                        ps.setString(2, (String) row[1]);
                        ps.setString(3, (String) row[2]);
                        ps.setString(4, (String) row[3]);
                        ps.setString(5, (String) row[4]);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                // Fetch customer IDs
                Map<String, Long> customerIdByEmail = fetchCustomerIds(conn, customerEmails);
                for (OrderPlan plan : plans) {
                    plan.customerId = customerIdByEmail.get(plan.email);
                }

                // Insert reservations
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO reservations (reservation_code, customer_id, status, total_amount, expires_at, idempotency_key) VALUES (?,?,?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    for (OrderPlan plan : plans) {
                        ps.setString(1, plan.reservationCode);
                        ps.setLong(2, plan.customerId);
                        ps.setString(3, "RESERVED");
                        ps.setDouble(4, plan.totalAmount);
                        ps.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now().plusMinutes(15)));
                        ps.setString(6, "IDEMP-RSV-" + plan.reservationCode);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                // Fetch reservation IDs
                Map<String, Long> reservationIdByCode = fetchReservationIds(conn,
                        plans.stream().map(p -> p.reservationCode).toList());
                for (OrderPlan plan : plans) {
                    plan.reservationId = reservationIdByCode.get(plan.reservationCode);
                }

                // Insert reservation_items
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO reservation_items (reservation_id, match_sector_id, quantity, unit_price, line_total) VALUES (?,?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    for (OrderPlan plan : plans) {
                        ps.setLong(1, plan.reservationId);
                        ps.setLong(2, sector.matchSectorId());
                        ps.setInt(3, plan.quantity);
                        ps.setDouble(4, sector.price());
                        ps.setDouble(5, plan.totalAmount);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                // Fetch reservation item IDs
                Map<Long, Long> itemIdByReservation = fetchReservationItemIds(conn,
                        plans.stream().map(p -> p.reservationId).toList());
                for (OrderPlan plan : plans) {
                    plan.reservationItemId = itemIdByReservation.get(plan.reservationId);
                }

                // Insert match_seat_allocations
                try (PreparedStatement ps = conn.prepareStatement("""
                        INSERT INTO match_seat_allocations
                          (match_id, match_sector_id, venue_seat_id, active_venue_seat_id,
                           reservation_id, reservation_item_id, status, allocated_at)
                        VALUES (?,?,?,?,?,?,?,?)
                        """)) {
                    String now = mysqlNow();
                    for (OrderPlan plan : plans) {
                        for (VenueSeat seat : plan.seats) {
                            ps.setLong(1, match.id());
                            ps.setLong(2, sector.matchSectorId());
                            ps.setLong(3, seat.venueSeatId());
                            ps.setLong(4, seat.venueSeatId());
                            ps.setLong(5, plan.reservationId);
                            ps.setLong(6, plan.reservationItemId);
                            ps.setString(7, "RESERVED");
                            ps.setString(8, now);
                            ps.addBatch();
                        }
                    }
                    ps.executeBatch();
                }

                // Plans that need orders (PAYMENT_PENDING + ISSUED)
                List<OrderPlan> plansWithOrders = plans.stream()
                        .filter(p -> "PAYMENT_PENDING".equals(p.stage) || "ISSUED".equals(p.stage))
                        .toList();
                List<OrderPlan> issuedPlans = plans.stream()
                        .filter(p -> "ISSUED".equals(p.stage)).toList();
                List<OrderPlan> reservedOnlyPlans = plans.stream()
                        .filter(p -> "RESERVED".equals(p.stage)).toList();
                List<OrderPlan> paymentPendingPlans = plans.stream()
                        .filter(p -> "PAYMENT_PENDING".equals(p.stage)).toList();

                if (!plansWithOrders.isEmpty()) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO orders (order_code, reservation_id, customer_id, status, total_amount, idempotency_key) VALUES (?,?,?,'PAYMENT_PENDING',?,?)",
                            Statement.RETURN_GENERATED_KEYS)) {
                        for (OrderPlan plan : plansWithOrders) {
                            ps.setString(1, plan.orderCode);
                            ps.setLong(2, plan.reservationId);
                            ps.setLong(3, plan.customerId);
                            ps.setDouble(4, plan.totalAmount);
                            ps.setString(5, "IDEMP-ORD-" + plan.orderCode);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }

                    Map<String, Long> orderIdByCode = fetchOrderIds(conn,
                            plansWithOrders.stream().map(p -> p.orderCode).toList());
                    for (OrderPlan plan : plansWithOrders) {
                        plan.orderId = orderIdByCode.get(plan.orderCode);
                    }

                    try (PreparedStatement ps = conn.prepareStatement("""
                            INSERT INTO payments (order_id, payment_method, status, amount, payment_reference, expires_at)
                            VALUES (?,?,'PENDING',?,?,?)
                            """)) {
                        for (OrderPlan plan : plansWithOrders) {
                            ps.setLong(1, plan.orderId);
                            ps.setString(2, plan.paymentMethod);
                            ps.setDouble(3, plan.totalAmount);
                            ps.setString(4, plan.paymentReference);
                            ps.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now().plusMinutes(10)));
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }

                    // Link allocations to orders
                    if (!plansWithOrders.isEmpty()) {
                        String inPlaceholders = String.join(",", Collections.nCopies(plansWithOrders.size(), "?"));
                        String updateAllocSql = """
                                UPDATE match_seat_allocations msa
                                JOIN orders o ON o.reservation_id = msa.reservation_id
                                SET msa.order_id = o.id
                                WHERE msa.match_id = ?
                                  AND msa.match_sector_id = ?
                                  AND msa.reservation_id IN (%s)
                                  AND msa.status = 'RESERVED'
                                """.formatted(inPlaceholders);
                        try (PreparedStatement ps = conn.prepareStatement(updateAllocSql)) {
                            ps.setLong(1, match.id());
                            ps.setLong(2, sector.matchSectorId());
                            for (int i = 0; i < plansWithOrders.size(); i++) {
                                ps.setLong(3 + i, plansWithOrders.get(i).reservationId);
                            }
                            ps.executeUpdate();
                        }
                    }
                }

                if (!issuedPlans.isEmpty()) {
                    // Pay orders
                    String inP = String.join(",", Collections.nCopies(issuedPlans.size(), "?"));
                    Object[] orderIds = issuedPlans.stream().map(p -> p.orderId).toArray();

                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE payments SET status = 'PAID', paid_at = NOW() WHERE order_id IN (" + inP + ") AND status = 'PENDING'")) {
                        for (int i = 0; i < issuedPlans.size(); i++) ps.setLong(i + 1, issuedPlans.get(i).orderId);
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE orders SET status = 'PAID' WHERE id IN (" + inP + ") AND status = 'PAYMENT_PENDING'")) {
                        for (int i = 0; i < issuedPlans.size(); i++) ps.setLong(i + 1, issuedPlans.get(i).orderId);
                        ps.executeUpdate();
                    }

                    String inRsv = String.join(",", Collections.nCopies(issuedPlans.size(), "?"));
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE reservations SET status = 'CONVERTED' WHERE id IN (" + inRsv + ") AND status = 'RESERVED'")) {
                        for (int i = 0; i < issuedPlans.size(); i++) ps.setLong(i + 1, issuedPlans.get(i).reservationId);
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE match_seat_allocations SET status = 'SOLD' WHERE match_id = ? AND match_sector_id = ? AND reservation_id IN (" + inRsv + ") AND status = 'RESERVED'")) {
                        ps.setLong(1, match.id());
                        ps.setLong(2, sector.matchSectorId());
                        for (int i = 0; i < issuedPlans.size(); i++) ps.setLong(3 + i, issuedPlans.get(i).reservationId);
                        ps.executeUpdate();
                    }

                    int issuedSeatCount = issuedPlans.stream().mapToInt(p -> p.quantity).sum();
                    try (PreparedStatement ps = conn.prepareStatement("""
                            UPDATE match_sectors
                            SET reserved_quantity = reserved_quantity - ?,
                                sold_quantity = sold_quantity + ?
                            WHERE id = ? AND reserved_quantity >= ?
                            """)) {
                        ps.setInt(1, issuedSeatCount);
                        ps.setInt(2, issuedSeatCount);
                        ps.setLong(3, sector.matchSectorId());
                        ps.setInt(4, issuedSeatCount);
                        int rows = ps.executeUpdate();
                        if (rows != 1) throw new SQLException("Failed to move reserved->sold stock for sector " + sector.sectorCode());
                    }

                    // Fetch allocations for ticket insertion
                    String inRsv2 = String.join(",", Collections.nCopies(issuedPlans.size(), "?"));
                    String allocSql = """
                            SELECT
                              msa.id AS allocation_id,
                              msa.reservation_item_id,
                              msa.order_id,
                              msa.venue_seat_id,
                              vs.seat_label,
                              vs.gate,
                              vs.entrance,
                              o.customer_id
                            FROM match_seat_allocations msa
                            JOIN venue_seats vs ON vs.id = msa.venue_seat_id
                            JOIN orders o ON o.id = msa.order_id
                            WHERE msa.reservation_id IN (%s)
                              AND msa.status = 'SOLD'
                            ORDER BY msa.id
                            """.formatted(inRsv2);

                    List<AllocationRow> allocations = new ArrayList<>();
                    try (PreparedStatement ps = conn.prepareStatement(allocSql)) {
                        for (int i = 0; i < issuedPlans.size(); i++) ps.setLong(i + 1, issuedPlans.get(i).reservationId);
                        ResultSet rs = ps.executeQuery();
                        while (rs.next()) {
                            allocations.add(new AllocationRow(
                                    rs.getLong("allocation_id"),
                                    rs.getLong("reservation_item_id"),
                                    rs.getLong("order_id"),
                                    rs.getLong("venue_seat_id"),
                                    rs.getString("seat_label"),
                                    rs.getString("gate"),
                                    rs.getString("entrance"),
                                    rs.getLong("customer_id"),
                                    match.id(),
                                    sector.matchSectorId()
                            ));
                        }
                    }

                    // Insert tickets
                    if (!allocations.isEmpty()) {
                        try (PreparedStatement ps = conn.prepareStatement("""
                                INSERT INTO tickets
                                  (ticket_code, order_id, reservation_item_id, customer_id, match_id,
                                   match_sector_id, match_seat_allocation_id, venue_seat_id, seat_label,
                                   gate, entrance, qr_code, status, issued_at)
                                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,'ISSUED',?)
                                """)) {
                            String now = mysqlNow();
                            for (AllocationRow alloc : allocations) {
                                ticketSeq[0]++;
                                ps.setString(1, buildCode("TCK", ticketSeq[0], "", rng));
                                ps.setLong(2, alloc.orderId());
                                ps.setLong(3, alloc.reservationItemId());
                                ps.setLong(4, alloc.customerId());
                                ps.setLong(5, alloc.matchId());
                                ps.setLong(6, alloc.matchSectorId());
                                ps.setLong(7, alloc.allocationId());
                                ps.setLong(8, alloc.venueSeatId());
                                ps.setString(9, alloc.seatLabel());
                                ps.setString(10, alloc.gate());
                                ps.setString(11, alloc.entrance());
                                ps.setString(12, buildCode("QR", ticketSeq[0], "", rng));
                                ps.setString(13, now);
                                ps.addBatch();
                            }
                            ps.executeBatch();
                        }
                    }

                    totals.seatsSold += issuedSeatCount;
                    totals.paymentsPaid += issuedPlans.size();
                    totals.tickets += allocations.size();
                    totals.revenue += issuedPlans.stream().mapToDouble(p -> p.totalAmount).sum();
                }

                conn.commit();

                totals.reservations += plans.size();
                totals.orders += plansWithOrders.size();
                totals.customers += plans.size();
                totals.seatsReservedOnly += reservedOnlyPlans.stream().mapToInt(p -> p.quantity).sum();
                totals.seatsPaymentPending += paymentPendingPlans.stream().mapToInt(p -> p.quantity).sum();
                totals.paymentsPending += paymentPendingPlans.size();

            } catch (Exception e) {
                conn.rollback();
                throw e instanceof SQLException ? (SQLException) e : new SQLException(e);
            }
        }
    }

    private void markSoldOutIfComplete(long matchId) throws SQLException {
        try (Connection conn = ds.getConnection()) {
            conn.createStatement().execute("""
                    UPDATE match_sectors
                    SET status = 'SOLD_OUT'
                    WHERE match_id = %d
                      AND total_quantity - reserved_quantity - sold_quantity = 0
                      AND status = 'AVAILABLE'
                    """.formatted(matchId));
            conn.createStatement().execute("""
                    UPDATE matches m
                    SET status = 'SOLD_OUT'
                    WHERE m.id = %d
                      AND NOT EXISTS (
                        SELECT 1 FROM match_sectors ms
                        WHERE ms.match_id = m.id
                          AND ms.total_quantity - ms.reserved_quantity - ms.sold_quantity > 0
                      )
                    """.formatted(matchId));
        }
    }

    private MatchInfo loadMatch(int matchNumber) throws SQLException {
        String sql = """
                SELECT m.id, m.match_number, m.venue_capacity_snapshot, m.status,
                       v.id AS venue_id, v.name AS venue_name, v.city, v.country,
                       ht.team_name AS home_team, at.team_name AS away_team
                FROM matches m
                JOIN venues v ON v.id = m.venue_id
                JOIN national_teams ht ON ht.id = m.home_team_id
                JOIN national_teams at ON at.id = m.away_team_id
                WHERE m.match_number = ?
                """;
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, matchNumber);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) throw new IllegalArgumentException("Match " + matchNumber + " not found");
            return new MatchInfo(rs.getLong("id"), rs.getString("match_number"),
                    rs.getLong("venue_capacity_snapshot"), rs.getString("status"));
        }
    }

    private Availability loadAvailability(long matchId) throws SQLException {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                     SELECT SUM(total_quantity) AS totalSeats,
                            SUM(reserved_quantity) AS reservedSeats,
                            SUM(sold_quantity) AS soldSeats,
                            SUM(total_quantity - reserved_quantity - sold_quantity) AS availableSeats
                     FROM match_sectors WHERE match_id = ?
                     """)) {
            ps.setLong(1, matchId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return new Availability(0, 0, 0, 0);
            return new Availability(rs.getLong("totalSeats"), rs.getLong("reservedSeats"),
                    rs.getLong("soldSeats"), rs.getLong("availableSeats"));
        }
    }

    private List<SectorSeats> loadAvailableSeats(long matchId, int limit) throws SQLException {
        String sql = """
                SELECT
                  ms.id AS match_sector_id,
                  ms.sector_code,
                  ms.sector_name,
                  ms.price,
                  vs.id AS venue_seat_id,
                  vs.seat_label,
                  vs.gate,
                  vs.entrance
                FROM matches m
                JOIN match_sectors ms ON ms.match_id = m.id
                JOIN venue_seats vs
                  ON vs.venue_id = m.venue_id
                 AND vs.sector_code = ms.sector_code
                LEFT JOIN match_seat_allocations msa
                  ON msa.match_id = m.id
                 AND msa.venue_seat_id = vs.id
                 AND msa.status IN ('RESERVED','SOLD','BLOCKED')
                WHERE m.id = ?
                  AND ms.status = 'AVAILABLE'
                  AND vs.status = 'ACTIVE'
                  AND msa.id IS NULL
                ORDER BY
                  FIELD(ms.sector_code, 'VIP', 'EAST', 'NORTH', 'SOUTH'),
                  vs.seat_ordinal
                LIMIT %d
                """.formatted(limit);

        List<SectorSeats> sectors = new ArrayList<>();
        Map<Long, SectorSeats> bySector = new LinkedHashMap<>();

        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, matchId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                long msId = rs.getLong("match_sector_id");
                SectorSeats sector = bySector.get(msId);
                if (sector == null) {
                    sector = new SectorSeats(msId, rs.getString("sector_code"),
                            rs.getString("sector_name"), rs.getDouble("price"), new ArrayList<>());
                    bySector.put(msId, sector);
                    sectors.add(sector);
                }
                sector.seats().add(new VenueSeat(rs.getLong("venue_seat_id"),
                        rs.getString("seat_label"), rs.getString("gate"), rs.getString("entrance")));
            }
        }
        return sectors;
    }

    private Map<String, Long> fetchCustomerIds(Connection conn, List<String> emails) throws SQLException {
        if (emails.isEmpty()) return Map.of();
        String in = String.join(",", Collections.nCopies(emails.size(), "?"));
        Map<String, Long> map = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT id, email FROM customers WHERE email IN (" + in + ")")) {
            for (int i = 0; i < emails.size(); i++) ps.setString(i + 1, emails.get(i));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) map.put(rs.getString("email"), rs.getLong("id"));
        }
        return map;
    }

    private Map<String, Long> fetchReservationIds(Connection conn, List<String> codes) throws SQLException {
        if (codes.isEmpty()) return Map.of();
        String in = String.join(",", Collections.nCopies(codes.size(), "?"));
        Map<String, Long> map = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT id, reservation_code FROM reservations WHERE reservation_code IN (" + in + ")")) {
            for (int i = 0; i < codes.size(); i++) ps.setString(i + 1, codes.get(i));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) map.put(rs.getString("reservation_code"), rs.getLong("id"));
        }
        return map;
    }

    private Map<Long, Long> fetchReservationItemIds(Connection conn, List<Long> reservationIds) throws SQLException {
        if (reservationIds.isEmpty()) return Map.of();
        String in = String.join(",", Collections.nCopies(reservationIds.size(), "?"));
        Map<Long, Long> map = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT id, reservation_id FROM reservation_items WHERE reservation_id IN (" + in + ")")) {
            for (int i = 0; i < reservationIds.size(); i++) ps.setLong(i + 1, reservationIds.get(i));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) map.put(rs.getLong("reservation_id"), rs.getLong("id"));
        }
        return map;
    }

    private Map<String, Long> fetchOrderIds(Connection conn, List<String> codes) throws SQLException {
        if (codes.isEmpty()) return Map.of();
        String in = String.join(",", Collections.nCopies(codes.size(), "?"));
        Map<String, Long> map = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT id, order_code FROM orders WHERE order_code IN (" + in + ")")) {
            for (int i = 0; i < codes.size(); i++) ps.setString(i + 1, codes.get(i));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) map.put(rs.getString("order_code"), rs.getLong("id"));
        }
        return map;
    }

    private int[] purchaseStageCounts(int orderCount, SelloutConfig cfg) {
        double[] percents = {cfg.reservedPercent(), cfg.paymentPendingPercent(), cfg.issuedPercent()};
        int[] counts = new int[3];
        double[] fractions = new double[3];
        int total = 0;
        for (int i = 0; i < 3; i++) {
            double exact = orderCount * percents[i] / 100.0;
            counts[i] = (int) exact;
            fractions[i] = exact - counts[i];
            total += counts[i];
        }
        int remaining = orderCount - total;
        Integer[] indices = {0, 1, 2};
        Arrays.sort(indices, (a, b) -> Double.compare(fractions[b], fractions[a]));
        for (int i = 0; i < remaining; i++) counts[indices[i]]++;
        return counts;
    }

    private String purchaseStageForIndex(int index, int[] counts) {
        if (index < counts[0]) return "RESERVED";
        if (index < counts[0] + counts[1]) return "PAYMENT_PENDING";
        return "ISSUED";
    }

    private static <T> List<List<T>> chunk(List<T> list, int size) {
        List<List<T>> chunks = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            chunks.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return chunks;
    }

    private static String buildCode(String prefix, int seq, String runId, SecureRandom rng) {
        byte[] bytes = new byte[4];
        rng.nextBytes(bytes);
        String hex = HexFormat.of().formatHex(bytes).toUpperCase();
        if (runId.isBlank()) {
            return prefix + "-" + Integer.toString(seq, 36).toUpperCase().formatted("%6s").replace(' ', '0') + "-" + hex;
        }
        return prefix + "-" + runId + "-" + Integer.toString(seq, 36).toUpperCase() + "-" + hex;
    }

    private static String buildRunId(SecureRandom rng) {
        byte[] bytes = new byte[3];
        rng.nextBytes(bytes);
        String ts = java.time.LocalDateTime.now().toString()
                .replaceAll("[^0-9]", "").substring(0, 14);
        return "SELLOUT-" + ts + "-" + HexFormat.of().formatHex(bytes);
    }

    private static String mysqlNow() {
        return java.time.LocalDateTime.now().toString().replace("T", " ").substring(0, 19);
    }

    private record MatchInfo(long id, String matchNumber, long capacity, String status) {}
    private record Availability(long totalSeats, long reservedSeats, long soldSeats, long availableSeats) {}
    private record SectorSeats(long matchSectorId, String sectorCode, String sectorName, double price, List<VenueSeat> seats) {}
    private record VenueSeat(long venueSeatId, String seatLabel, String gate, String entrance) {}
    private record AllocationRow(long allocationId, long reservationItemId, long orderId, long venueSeatId,
                                  String seatLabel, String gate, String entrance, long customerId,
                                  long matchId, long matchSectorId) {}

    private static class OrderPlan {
        final int sequence;
        final String stage;
        final String email;
        final List<VenueSeat> seats;
        final int quantity;
        final double totalAmount;
        final String reservationCode;
        final String orderCode;
        final String paymentReference;
        final String paymentMethod;
        long customerId;
        long reservationId;
        long reservationItemId;
        long orderId;

        OrderPlan(int sequence, String stage, String email, List<VenueSeat> seats, int quantity,
                  double totalAmount, String reservationCode, String orderCode,
                  String paymentReference, String paymentMethod) {
            this.sequence = sequence;
            this.stage = stage;
            this.email = email;
            this.seats = seats;
            this.quantity = quantity;
            this.totalAmount = totalAmount;
            this.reservationCode = reservationCode;
            this.orderCode = orderCode;
            this.paymentReference = paymentReference;
            this.paymentMethod = paymentMethod;
        }
    }
}
