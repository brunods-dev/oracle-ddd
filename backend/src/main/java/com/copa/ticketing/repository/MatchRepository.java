package com.copa.ticketing.repository;

import com.copa.ticketing.domain.Match;
import com.copa.ticketing.domain.Seat;
import com.copa.ticketing.domain.SeatRowSummary;
import com.copa.ticketing.domain.Sector;
import com.copa.ticketing.pagination.Page;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MatchRepository {

    private final DataSource ds;

    public MatchRepository(DataSource ds) {
        this.ds = ds;
    }

    public Page<Match> findAll(String city, String date, String team, int page, int size) throws SQLException {
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1=1");

        if (city != null && !city.isBlank()) {
            where.append(" AND v.city = ?");
            params.add(city);
        }
        if (date != null && !date.isBlank()) {
            where.append(" AND DATE(m.match_at) = ?");
            params.add(date);
        }
        if (team != null && !team.isBlank()) {
            where.append(" AND (ht.team_name LIKE ? OR aw.team_name LIKE ?)");
            params.add("%" + team + "%");
            params.add("%" + team + "%");
        }

        String countSql = """
                SELECT COUNT(*) FROM matches m
                JOIN venues v ON m.venue_id = v.id
                LEFT JOIN national_teams ht ON m.home_team_id = ht.id
                LEFT JOIN national_teams aw ON m.away_team_id = aw.id
                """ + where;

        String dataSql = """
                SELECT m.id, m.match_number,
                       ht.team_name  AS home_team,  aw.team_name  AS away_team,
                       ht.team_code  AS home_code,  aw.team_code  AS away_code,
                       v.name AS venue_name, v.city, v.country,
                       m.match_at, m.status, m.competition_stage, m.group_name,
                       m.venue_capacity_snapshot AS total_capacity,
                       (SELECT COUNT(*) FROM tickets t
                        WHERE t.match_id = m.id AND t.status = 'ISSUED') AS tickets_sold
                FROM matches m
                JOIN venues v ON m.venue_id = v.id
                LEFT JOIN national_teams ht ON m.home_team_id = ht.id
                LEFT JOIN national_teams aw ON m.away_team_id = aw.id
                """ + where + " ORDER BY m.match_at ASC LIMIT ? OFFSET ?";

        try (Connection conn = ds.getConnection()) {
            long total;
            try (PreparedStatement ps = conn.prepareStatement(countSql)) {
                bind(ps, params, 1);
                ResultSet rs = ps.executeQuery();
                rs.next();
                total = rs.getLong(1);
            }

            List<Match> items = new ArrayList<>();
            params.add(size);
            params.add((long) page * size);
            try (PreparedStatement ps = conn.prepareStatement(dataSql)) {
                bind(ps, params, 1);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    items.add(mapMatch(rs));
                }
            }
            return new Page<>(items, page, size, total);
        }
    }

    public Optional<Match> findById(long id) throws SQLException {
        String sql = """
                SELECT m.id, m.match_number,
                       ht.team_name  AS home_team,  aw.team_name  AS away_team,
                       ht.team_code  AS home_code,  aw.team_code  AS away_code,
                       v.name AS venue_name, v.city, v.country,
                       m.match_at, m.status, m.competition_stage, m.group_name,
                       m.venue_capacity_snapshot AS total_capacity,
                       (SELECT COUNT(*) FROM tickets t
                        WHERE t.match_id = m.id AND t.status = 'ISSUED') AS tickets_sold
                FROM matches m
                JOIN venues v ON m.venue_id = v.id
                LEFT JOIN national_teams ht ON m.home_team_id = ht.id
                LEFT JOIN national_teams aw ON m.away_team_id = aw.id
                WHERE m.id = ?
                """;
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(mapMatch(rs));
        }
        return Optional.empty();
    }

    public List<Sector> findSectorsByMatch(long matchId) throws SQLException {
        String sql = """
                SELECT match_sector_id, sector_code, sector_name, match_id,
                       price, total_quantity, sold_quantity, reserved_quantity,
                       available_quantity, occupancy_percent
                FROM vw_sector_availability
                WHERE match_id = ?
                ORDER BY price ASC
                """;
        List<Sector> result = new ArrayList<>();
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, matchId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                long available = rs.getLong("available_quantity");
                result.add(new Sector(
                        rs.getLong("match_sector_id"),
                        rs.getString("sector_code"),
                        rs.getString("sector_name"),
                        rs.getLong("match_id"),
                        rs.getDouble("price"),
                        rs.getLong("total_quantity"),
                        rs.getLong("sold_quantity"),
                        rs.getLong("reserved_quantity"),
                        available,
                        rs.getDouble("occupancy_percent"),
                        Sector.computeStatus(available)
                ));
            }
        }
        return result;
    }

    public List<SeatRowSummary> findSeatMapRows(long matchId, String sectorCode) throws SQLException {
        String sql = """
                SELECT row_label,
                       COUNT(*) AS total_seats,
                       SUM(CASE WHEN seat_status = 'AVAILABLE' THEN 1 ELSE 0 END) AS available_count,
                       SUM(CASE WHEN seat_status = 'RESERVED'  THEN 1 ELSE 0 END) AS reserved_count,
                       SUM(CASE WHEN seat_status NOT IN ('AVAILABLE','RESERVED') THEN 1 ELSE 0 END) AS sold_count
                FROM vw_seat_map_availability
                WHERE match_id = ? AND sector_code = ?
                GROUP BY row_label
                ORDER BY row_label
                """;
        List<SeatRowSummary> result = new ArrayList<>();
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, matchId);
            ps.setString(2, sectorCode);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                result.add(new SeatRowSummary(
                        rs.getString("row_label"),
                        rs.getLong("total_seats"),
                        rs.getLong("available_count"),
                        rs.getLong("reserved_count"),
                        rs.getLong("sold_count")
                ));
            }
        }
        return result;
    }

    public List<Seat> findSeatMapByRow(long matchId, String sectorCode, String rowLabel) throws SQLException {
        String sql = """
                SELECT venue_seat_id, match_id, match_sector_id, sector_code, sector_name,
                       row_label, seat_number, seat_label, block_code, entrance,
                       price, seat_status AS status
                FROM vw_seat_map_availability
                WHERE match_id = ? AND sector_code = ? AND row_label = ?
                ORDER BY seat_number
                """;
        List<Seat> items = new ArrayList<>();
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, matchId);
            ps.setString(2, sectorCode);
            ps.setString(3, rowLabel);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                items.add(mapSeat(rs));
            }
        }
        return items;
    }

    public List<Seat> findSeatMapAll(long matchId, String sectorCode) throws SQLException {
        String sql = """
                SELECT venue_seat_id, match_id, match_sector_id, sector_code, sector_name,
                       row_label, seat_number, seat_label, block_code, entrance,
                       price, seat_status AS status
                FROM vw_seat_map_availability
                WHERE match_id = ? AND sector_code = ?
                ORDER BY row_label, seat_number
                """;
        List<Seat> items = new ArrayList<>();
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, matchId);
            ps.setString(2, sectorCode);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                items.add(mapSeat(rs));
            }
        }
        return items;
    }

    public Page<Seat> findSeatMap(long matchId, String sectorCode, int page, int size) throws SQLException {
        String countSql = """
                SELECT COUNT(*) FROM vw_seat_map_availability
                WHERE match_id = ? AND sector_code = ?
                """;
        String dataSql = """
                SELECT venue_seat_id, match_id, match_sector_id, sector_code, sector_name,
                       row_label, seat_number, seat_label, block_code, entrance,
                       price, seat_status AS status
                FROM vw_seat_map_availability
                WHERE match_id = ? AND sector_code = ?
                ORDER BY row_label, seat_number
                LIMIT ? OFFSET ?
                """;
        try (Connection conn = ds.getConnection()) {
            long total;
            try (PreparedStatement ps = conn.prepareStatement(countSql)) {
                ps.setLong(1, matchId);
                ps.setString(2, sectorCode);
                ResultSet rs = ps.executeQuery();
                rs.next();
                total = rs.getLong(1);
            }
            List<Seat> items = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(dataSql)) {
                ps.setLong(1, matchId);
                ps.setString(2, sectorCode);
                ps.setInt(3, size);
                ps.setInt(4, page * size);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    items.add(mapSeat(rs));
                }
            }
            return new Page<>(items, page, size, total);
        }
    }

    private Seat mapSeat(ResultSet rs) throws SQLException {
        return new Seat(
                rs.getLong("venue_seat_id"),
                rs.getLong("match_id"),
                rs.getLong("match_sector_id"),
                rs.getString("sector_code"),
                rs.getString("sector_name"),
                rs.getString("row_label"),
                rs.getInt("seat_number"),
                rs.getString("seat_label"),
                rs.getString("block_code"),
                rs.getString("entrance"),
                rs.getDouble("price"),
                rs.getString("status"),
                false
        );
    }

    private Match mapMatch(ResultSet rs) throws SQLException {
        Timestamp ts = rs.getTimestamp("match_at");
        long capacity  = rs.getLong("total_capacity");
        long sold      = rs.getLong("tickets_sold");
        long available = Math.max(0, capacity - sold);
        double occupancy = capacity > 0 ? (double) sold / capacity * 100.0 : 0.0;

        return new Match(
                rs.getLong("id"),
                rs.getString("match_number"),
                rs.getString("home_team"),
                rs.getString("away_team"),
                rs.getString("home_code"),
                rs.getString("away_code"),
                rs.getString("venue_name"),
                rs.getString("city"),
                rs.getString("country"),
                ts != null ? ts.toLocalDateTime() : null,
                rs.getString("status"),
                rs.getString("competition_stage"),
                rs.getString("group_name"),
                capacity,
                sold,
                available,
                occupancy
        );
    }

    private void bind(PreparedStatement ps, List<Object> params, int start) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object p = params.get(i);
            if (p instanceof String s)  ps.setString(start + i, s);
            else if (p instanceof Integer n) ps.setInt(start + i, n);
            else if (p instanceof Long n)    ps.setLong(start + i, n);
            else ps.setObject(start + i, p);
        }
    }
}
