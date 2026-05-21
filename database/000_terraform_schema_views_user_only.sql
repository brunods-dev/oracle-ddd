-- ============================================================
-- COPA TICKETING DEMO - SCHEMA, VIEWS E USUARIO APP
-- ============================================================
-- Arquivo unico para provisionamento por Terraform/CI-CD.
-- Conteudo: database, tabelas, constraints, FKs, indices, views base,
-- views analiticas HeatWave, carga das tabelas no motor secundario e usuario app.
-- Nao contem seed, carga demonstrativa, procedures de fluxo ou consultas de validacao.
-- Data de geracao: 2026-05-20
-- ============================================================



-- ============================================================
-- DATABASE
-- ============================================================

CREATE DATABASE IF NOT EXISTS copa_ticketing_demo
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE copa_ticketing_demo;

SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;
SET time_zone = '-03:00';
SET sql_mode = 'STRICT_TRANS_TABLES,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION';

-- ============================================================
-- TABLES
-- ============================================================

CREATE TABLE customers (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    full_name VARCHAR(160) NOT NULL,
    email VARCHAR(190) NOT NULL,
    document_type ENUM('CPF','PASSPORT','OTHER') NOT NULL DEFAULT 'CPF',
    document_number VARCHAR(40) NOT NULL,
    phone VARCHAR(40) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_customers PRIMARY KEY (id),
    CONSTRAINT uk_customers_email UNIQUE (email),
    CONSTRAINT uk_customers_document UNIQUE (document_type, document_number),
    KEY idx_customers_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE venues (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name VARCHAR(160) NOT NULL,
    city VARCHAR(100) NOT NULL,
    state VARCHAR(100) NULL,
    country VARCHAR(100) NOT NULL DEFAULT 'Brazil',
    time_zone VARCHAR(64) NOT NULL DEFAULT 'America/Sao_Paulo',
    capacity INT UNSIGNED NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_venues PRIMARY KEY (id),
    CONSTRAINT chk_venues_capacity_positive CHECK (capacity IS NULL OR capacity > 0),
    KEY idx_venues_city_country (city, country),
    KEY idx_venues_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Mapa estático de assentos numerados por estádio. Ele é reutilizável entre
-- jogos: o assento físico é definido uma única vez, e o estado de reserva ou
-- venda específico do jogo fica em match_seat_allocations.
CREATE TABLE venue_seats (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    venue_id BIGINT UNSIGNED NOT NULL,
    seat_ordinal INT UNSIGNED NOT NULL,
    sector_code VARCHAR(40) NOT NULL,
    block_code VARCHAR(40) NOT NULL,
    row_label VARCHAR(20) NOT NULL,
    seat_row_number INT UNSIGNED NOT NULL,
    seat_number INT UNSIGNED NOT NULL,
    seat_label VARCHAR(80) NOT NULL,
    map_x INT UNSIGNED NOT NULL,
    map_y INT UNSIGNED NOT NULL,
    gate VARCHAR(40) NULL,
    entrance VARCHAR(80) NULL,
    status ENUM('ACTIVE','BLOCKED') NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_venue_seats PRIMARY KEY (id),
    CONSTRAINT uk_venue_seats_ordinal UNIQUE (venue_id, seat_ordinal),
    CONSTRAINT uk_venue_seats_label UNIQUE (venue_id, seat_label),
    CONSTRAINT uk_venue_seats_position UNIQUE (venue_id, sector_code, seat_row_number, seat_number),
    CONSTRAINT chk_venue_seats_ordinal_positive CHECK (seat_ordinal > 0),
    CONSTRAINT chk_venue_seats_row_positive CHECK (seat_row_number > 0),
    CONSTRAINT chk_venue_seats_seat_positive CHECK (seat_number > 0),
    CONSTRAINT chk_venue_seats_map_positive CHECK (map_x > 0 AND map_y > 0),
    CONSTRAINT fk_venue_seats_venue FOREIGN KEY (venue_id)
        REFERENCES venues (id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    KEY idx_venue_seats_venue_sector (venue_id, sector_code),
    KEY idx_venue_seats_block (venue_id, sector_code, block_code),
    KEY idx_venue_seats_map (venue_id, sector_code, map_y, map_x),
    KEY idx_venue_seats_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Tabela adicional solicitada explicitamente para seleções. Ela mantém a
-- identidade da seleção normalizada e permite que os jogos referenciem
-- seleções por FKs.
CREATE TABLE national_teams (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    team_code VARCHAR(10) NOT NULL,
    team_name VARCHAR(120) NOT NULL,
    confederation ENUM('AFC','CAF','CONCACAF','CONMEBOL','OFC','UEFA') NOT NULL,
    group_name VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_national_teams PRIMARY KEY (id),
    CONSTRAINT uk_national_teams_code UNIQUE (team_code),
    CONSTRAINT uk_national_teams_name UNIQUE (team_name),
    KEY idx_national_teams_group (group_name),
    KEY idx_national_teams_confederation (confederation)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE matches (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    venue_id BIGINT UNSIGNED NOT NULL,
    home_team_id BIGINT UNSIGNED NOT NULL,
    away_team_id BIGINT UNSIGNED NOT NULL,
    match_number SMALLINT UNSIGNED NULL,
    competition_stage ENUM('GROUP_STAGE','ROUND_OF_32','ROUND_OF_16','QUARTER_FINAL','SEMI_FINAL','THIRD_PLACE','FINAL') NOT NULL DEFAULT 'GROUP_STAGE',
    group_name VARCHAR(20) NULL,
    match_at DATETIME NOT NULL,
    match_at_utc DATETIME NULL,
    venue_capacity_snapshot INT UNSIGNED NOT NULL,
    status ENUM('DRAFT','AVAILABLE','SOLD_OUT','CLOSED','CANCELLED') NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_matches PRIMARY KEY (id),
    CONSTRAINT uk_matches_match_number UNIQUE (match_number),
    CONSTRAINT chk_matches_number_positive CHECK (match_number IS NULL OR match_number > 0),
    CONSTRAINT chk_matches_group_stage_group CHECK (
        (competition_stage = 'GROUP_STAGE' AND group_name IS NOT NULL)
        OR (competition_stage <> 'GROUP_STAGE')
    ),
    CONSTRAINT chk_matches_capacity_snapshot_positive CHECK (venue_capacity_snapshot > 0),
    CONSTRAINT chk_matches_distinct_teams CHECK (home_team_id <> away_team_id),
    CONSTRAINT fk_matches_venue FOREIGN KEY (venue_id)
        REFERENCES venues (id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    CONSTRAINT fk_matches_home_team FOREIGN KEY (home_team_id)
        REFERENCES national_teams (id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,
    CONSTRAINT fk_matches_away_team FOREIGN KEY (away_team_id)
        REFERENCES national_teams (id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,
    KEY idx_matches_venue (venue_id),
    KEY idx_matches_home_team (home_team_id),
    KEY idx_matches_away_team (away_team_id),
    KEY idx_matches_status_date (status, match_at),
    KEY idx_matches_match_at (match_at),
    KEY idx_matches_stage_group (competition_stage, group_name, match_at),
    KEY idx_matches_match_number (match_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE match_sectors (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    match_id BIGINT UNSIGNED NOT NULL,
    sector_code VARCHAR(40) NOT NULL,
    sector_name VARCHAR(120) NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    total_quantity INT UNSIGNED NOT NULL,
    reserved_quantity INT UNSIGNED NOT NULL DEFAULT 0,
    sold_quantity INT UNSIGNED NOT NULL DEFAULT 0,
    status ENUM('AVAILABLE','SOLD_OUT','CLOSED') NOT NULL DEFAULT 'AVAILABLE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_match_sectors PRIMARY KEY (id),
    CONSTRAINT uk_match_sectors_match_code UNIQUE (match_id, sector_code),
    CONSTRAINT chk_match_sectors_price_nonnegative CHECK (price >= 0),
    CONSTRAINT chk_match_sectors_total_nonnegative CHECK (total_quantity >= 0),
    CONSTRAINT chk_match_sectors_reserved_nonnegative CHECK (reserved_quantity >= 0),
    CONSTRAINT chk_match_sectors_sold_nonnegative CHECK (sold_quantity >= 0),
    CONSTRAINT chk_match_sectors_stock_capacity CHECK (reserved_quantity + sold_quantity <= total_quantity),
    CONSTRAINT fk_match_sectors_match FOREIGN KEY (match_id)
        REFERENCES matches (id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    KEY idx_match_sectors_match_status (match_id, status),
    KEY idx_match_sectors_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE reservations (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    reservation_code VARCHAR(50) NOT NULL,
    customer_id BIGINT UNSIGNED NOT NULL,
    status ENUM('RESERVED','CANCELLED','EXPIRED','CONVERTED') NOT NULL DEFAULT 'RESERVED',
    total_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    expires_at DATETIME NOT NULL,
    idempotency_key VARCHAR(100) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_reservations PRIMARY KEY (id),
    CONSTRAINT uk_reservations_code UNIQUE (reservation_code),
    CONSTRAINT uk_reservations_idempotency UNIQUE (idempotency_key),
    CONSTRAINT chk_reservations_total_nonnegative CHECK (total_amount >= 0),
    CONSTRAINT chk_reservations_expires_after_created CHECK (expires_at > created_at),
    CONSTRAINT fk_reservations_customer FOREIGN KEY (customer_id)
        REFERENCES customers (id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    KEY idx_reservations_customer_status (customer_id, status),
    KEY idx_reservations_status_expires (status, expires_at),
    KEY idx_reservations_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE reservation_items (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    reservation_id BIGINT UNSIGNED NOT NULL,
    match_sector_id BIGINT UNSIGNED NOT NULL,
    quantity INT UNSIGNED NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    line_total DECIMAL(12,2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_reservation_items PRIMARY KEY (id),
    CONSTRAINT uk_reservation_items_reservation_sector UNIQUE (reservation_id, match_sector_id),
    CONSTRAINT chk_reservation_items_quantity_positive CHECK (quantity > 0),
    CONSTRAINT chk_reservation_items_unit_price_nonnegative CHECK (unit_price >= 0),
    CONSTRAINT chk_reservation_items_line_total_nonnegative CHECK (line_total >= 0),
    CONSTRAINT chk_reservation_items_line_total_math CHECK (line_total = quantity * unit_price),
    CONSTRAINT fk_reservation_items_reservation FOREIGN KEY (reservation_id)
        REFERENCES reservations (id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    CONSTRAINT fk_reservation_items_match_sector FOREIGN KEY (match_sector_id)
        REFERENCES match_sectors (id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    KEY idx_reservation_items_sector (match_sector_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE orders (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_code VARCHAR(50) NOT NULL,
    reservation_id BIGINT UNSIGNED NOT NULL,
    customer_id BIGINT UNSIGNED NOT NULL,
    status ENUM('PAYMENT_PENDING','PAID','CANCELLED','EXPIRED') NOT NULL DEFAULT 'PAYMENT_PENDING',
    total_amount DECIMAL(12,2) NOT NULL,
    idempotency_key VARCHAR(100) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_orders PRIMARY KEY (id),
    CONSTRAINT uk_orders_code UNIQUE (order_code),
    CONSTRAINT uk_orders_reservation UNIQUE (reservation_id),
    CONSTRAINT uk_orders_idempotency UNIQUE (idempotency_key),
    CONSTRAINT chk_orders_total_nonnegative CHECK (total_amount >= 0),
    CONSTRAINT fk_orders_reservation FOREIGN KEY (reservation_id)
        REFERENCES reservations (id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id)
        REFERENCES customers (id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    KEY idx_orders_customer_status (customer_id, status),
    KEY idx_orders_status_created (status, created_at),
    KEY idx_orders_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE payments (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_id BIGINT UNSIGNED NOT NULL,
    payment_method ENUM('CARD','DIGITAL_WALLET') NOT NULL DEFAULT 'CARD',
    status ENUM('PENDING','PAID','FAILED','EXPIRED') NOT NULL DEFAULT 'PENDING',
    amount DECIMAL(12,2) NOT NULL,
    payment_reference VARCHAR(120) NULL,
    expires_at DATETIME NOT NULL,
    paid_at DATETIME NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_payments PRIMARY KEY (id),
    CONSTRAINT uk_payments_order UNIQUE (order_id),
    CONSTRAINT uk_payments_reference UNIQUE (payment_reference),
    CONSTRAINT chk_payments_amount_nonnegative CHECK (amount >= 0),
    CONSTRAINT chk_payments_paid_at_status CHECK (
        (status = 'PAID' AND paid_at IS NOT NULL)
        OR (status <> 'PAID' AND paid_at IS NULL)
    ),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id)
        REFERENCES orders (id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    KEY idx_payments_status_created (status, created_at),
    KEY idx_payments_method_status (payment_method, status),
    KEY idx_payments_paid_at (paid_at),
    KEY idx_payments_expires (status, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Estado esparso de assento por jogo. Apenas assentos RESERVED, SOLD,
-- BLOCKED, EXPIRED ou CANCELLED para um jogo são gravados aqui. A coluna
-- nullable active_venue_seat_id é preenchida apenas para estados ativos, então
-- a chave única impede duas alocações ativas para o mesmo assento no mesmo
-- jogo e ainda permite reutilizar assentos expirados/cancelados.
CREATE TABLE match_seat_allocations (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    match_id BIGINT UNSIGNED NOT NULL,
    match_sector_id BIGINT UNSIGNED NOT NULL,
    venue_seat_id BIGINT UNSIGNED NOT NULL,
    active_venue_seat_id BIGINT UNSIGNED NULL,
    reservation_id BIGINT UNSIGNED NULL,
    reservation_item_id BIGINT UNSIGNED NULL,
    order_id BIGINT UNSIGNED NULL,
    status ENUM('RESERVED','SOLD','EXPIRED','CANCELLED','BLOCKED') NOT NULL,
    allocated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    released_at DATETIME NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_match_seat_allocations PRIMARY KEY (id),
    CONSTRAINT uk_match_seat_allocations_active UNIQUE (match_id, active_venue_seat_id),
    -- O MySQL não permite CHECK constraints sobre colunas que participam de
    -- FKs com ações referenciais. O vínculo reserva/pedido é validado nas
    -- consultas finais de consistência e reforçado pelas regras transacionais
    -- da aplicação Java.
    CONSTRAINT chk_match_seat_allocations_released CHECK (
        released_at IS NULL OR status IN ('EXPIRED','CANCELLED')
    ),
    CONSTRAINT fk_match_seat_allocations_match FOREIGN KEY (match_id)
        REFERENCES matches (id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    CONSTRAINT fk_match_seat_allocations_match_sector FOREIGN KEY (match_sector_id)
        REFERENCES match_sectors (id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    CONSTRAINT fk_match_seat_allocations_venue_seat FOREIGN KEY (venue_seat_id)
        REFERENCES venue_seats (id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    CONSTRAINT fk_match_seat_allocations_reservation FOREIGN KEY (reservation_id)
        REFERENCES reservations (id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    CONSTRAINT fk_match_seat_allocations_reservation_item FOREIGN KEY (reservation_item_id)
        REFERENCES reservation_items (id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    CONSTRAINT fk_match_seat_allocations_order FOREIGN KEY (order_id)
        REFERENCES orders (id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    KEY idx_match_seat_allocations_match_status (match_id, status),
    KEY idx_match_seat_allocations_sector_status (match_sector_id, status),
    KEY idx_match_seat_allocations_reservation (reservation_id),
    KEY idx_match_seat_allocations_order (order_id),
    KEY idx_match_seat_allocations_venue_seat (venue_seat_id),
    KEY idx_match_seat_allocations_active_seat (active_venue_seat_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tickets (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    ticket_code VARCHAR(60) NOT NULL,
    order_id BIGINT UNSIGNED NOT NULL,
    reservation_item_id BIGINT UNSIGNED NOT NULL,
    customer_id BIGINT UNSIGNED NOT NULL,
    match_id BIGINT UNSIGNED NOT NULL,
    match_sector_id BIGINT UNSIGNED NOT NULL,
    match_seat_allocation_id BIGINT UNSIGNED NULL,
    venue_seat_id BIGINT UNSIGNED NULL,
    seat_label VARCHAR(80) NULL,
    gate VARCHAR(40) NULL,
    entrance VARCHAR(80) NULL,
    qr_code VARCHAR(255) NOT NULL,
    status ENUM('ISSUED','CANCELLED','CHECKED_IN') NOT NULL DEFAULT 'ISSUED',
    issued_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    checked_in_at DATETIME NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_tickets PRIMARY KEY (id),
    CONSTRAINT uk_tickets_code UNIQUE (ticket_code),
    CONSTRAINT uk_tickets_qr_code UNIQUE (qr_code),
    CONSTRAINT uk_tickets_match_seat_allocation UNIQUE (match_seat_allocation_id),
    CONSTRAINT uk_tickets_match_venue_seat UNIQUE (match_id, venue_seat_id),
    CONSTRAINT chk_tickets_checked_in_status CHECK (
        (status = 'CHECKED_IN' AND checked_in_at IS NOT NULL)
        OR (status <> 'CHECKED_IN' AND checked_in_at IS NULL)
    ),
    CONSTRAINT fk_tickets_order FOREIGN KEY (order_id)
        REFERENCES orders (id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    CONSTRAINT fk_tickets_reservation_item FOREIGN KEY (reservation_item_id)
        REFERENCES reservation_items (id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    CONSTRAINT fk_tickets_customer FOREIGN KEY (customer_id)
        REFERENCES customers (id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    CONSTRAINT fk_tickets_match FOREIGN KEY (match_id)
        REFERENCES matches (id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    CONSTRAINT fk_tickets_match_sector FOREIGN KEY (match_sector_id)
        REFERENCES match_sectors (id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    CONSTRAINT fk_tickets_match_seat_allocation FOREIGN KEY (match_seat_allocation_id)
        REFERENCES match_seat_allocations (id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    CONSTRAINT fk_tickets_venue_seat FOREIGN KEY (venue_seat_id)
        REFERENCES venue_seats (id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    KEY idx_tickets_order (order_id),
    KEY idx_tickets_customer (customer_id),
    KEY idx_tickets_match_sector (match_id, match_sector_id),
    KEY idx_tickets_venue_seat (venue_seat_id),
    KEY idx_tickets_status (status),
    KEY idx_tickets_issued_at (issued_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE match_results (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    match_id BIGINT UNSIGNED NOT NULL,
    status ENUM('SCHEDULED','LIVE','FINISHED','POSTPONED') NOT NULL DEFAULT 'SCHEDULED',
    home_score INT UNSIGNED NULL,
    away_score INT UNSIGNED NULL,
    current_minute SMALLINT UNSIGNED NULL,
    notes VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_match_results PRIMARY KEY (id),
    CONSTRAINT uk_match_results_match UNIQUE (match_id),
    CONSTRAINT chk_match_results_home_score_nonnegative CHECK (home_score IS NULL OR home_score >= 0),
    CONSTRAINT chk_match_results_away_score_nonnegative CHECK (away_score IS NULL OR away_score >= 0),
    CONSTRAINT chk_match_results_current_minute CHECK (current_minute IS NULL OR current_minute <= 130),
    CONSTRAINT fk_match_results_match FOREIGN KEY (match_id)
        REFERENCES matches (id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    KEY idx_match_results_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================
-- VIEWS
-- ============================================================

CREATE OR REPLACE VIEW vw_sector_availability AS
SELECT
    ms.id AS match_sector_id,
    m.id AS match_id,
    ht.team_name AS home_team,
    at.team_name AS away_team,
    m.match_at,
    m.status AS match_status,
    v.name AS venue_name,
    v.city,
    v.country,
    v.time_zone AS venue_time_zone,
    v.capacity AS venue_capacity,
    m.venue_capacity_snapshot AS match_capacity,
    ms.sector_code,
    ms.sector_name,
    ms.status AS sector_status,
    ms.price,
    ms.total_quantity,
    ms.reserved_quantity,
    ms.sold_quantity,
    (ms.total_quantity - ms.reserved_quantity - ms.sold_quantity) AS available_quantity,
    ROUND(ms.sold_quantity / NULLIF(ms.total_quantity, 0) * 100, 2) AS sold_percent,
    ROUND(ms.reserved_quantity / NULLIF(ms.total_quantity, 0) * 100, 2) AS reserved_percent,
    ROUND((ms.reserved_quantity + ms.sold_quantity) / NULLIF(ms.total_quantity, 0) * 100, 2) AS occupancy_percent
FROM match_sectors ms
JOIN matches m ON m.id = ms.match_id
JOIN national_teams ht ON ht.id = m.home_team_id
JOIN national_teams at ON at.id = m.away_team_id
JOIN venues v ON v.id = m.venue_id;

CREATE OR REPLACE VIEW vw_match_revenue AS
SELECT
    m.id AS match_id,
    ht.team_name AS home_team,
    at.team_name AS away_team,
    m.match_at,
    v.name AS venue_name,
    v.capacity AS venue_capacity,
    m.venue_capacity_snapshot AS match_capacity,
    COUNT(DISTINCT o.id) AS paid_orders,
    COALESCE(SUM(ri.quantity), 0) AS tickets_sold,
    COALESCE(SUM(ri.line_total), 0.00) AS gross_revenue,
    ROUND(COALESCE(SUM(ri.line_total) / NULLIF(SUM(ri.quantity), 0), 0), 2) AS avg_ticket_price
FROM orders o
JOIN reservations r ON r.id = o.reservation_id
JOIN reservation_items ri ON ri.reservation_id = r.id
JOIN match_sectors ms ON ms.id = ri.match_sector_id
JOIN matches m ON m.id = ms.match_id
JOIN national_teams ht ON ht.id = m.home_team_id
JOIN national_teams at ON at.id = m.away_team_id
JOIN venues v ON v.id = m.venue_id
WHERE o.status = 'PAID'
GROUP BY m.id, ht.team_name, at.team_name, m.match_at, v.name, v.capacity, m.venue_capacity_snapshot;

CREATE OR REPLACE VIEW vw_daily_sales AS
SELECT
    DATE(o.created_at) AS sales_date,
    COUNT(DISTINCT o.id) AS paid_orders,
    COALESCE(SUM(ri.quantity), 0) AS tickets_sold,
    COALESCE(SUM(ri.line_total), 0.00) AS gross_revenue
FROM orders o
JOIN reservations r ON r.id = o.reservation_id
JOIN reservation_items ri ON ri.reservation_id = r.id
WHERE o.status = 'PAID'
GROUP BY DATE(o.created_at);

CREATE OR REPLACE VIEW vw_reservation_conversion AS
SELECT
    DATE(r.created_at) AS reservation_date,
    COUNT(*) AS reservations_created,
    SUM(CASE WHEN r.status = 'CONVERTED' THEN 1 ELSE 0 END) AS converted_reservations,
    SUM(CASE WHEN r.status = 'EXPIRED' THEN 1 ELSE 0 END) AS expired_reservations,
    SUM(CASE WHEN r.status = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelled_reservations,
    ROUND(SUM(CASE WHEN r.status = 'CONVERTED' THEN 1 ELSE 0 END) / NULLIF(COUNT(*), 0) * 100, 2) AS conversion_percent
FROM reservations r
GROUP BY DATE(r.created_at);

CREATE OR REPLACE VIEW vw_payment_status_summary AS
SELECT
    p.status AS payment_status,
    COUNT(*) AS payment_count,
    COALESCE(SUM(p.amount), 0.00) AS total_amount
FROM payments p
GROUP BY p.status;

CREATE OR REPLACE VIEW vw_match_sales_funnel AS
SELECT
    m.id AS match_id,
    ht.team_name AS home_team,
    at.team_name AS away_team,
    m.match_at,
    m.venue_capacity_snapshot AS match_capacity,
    COALESCE(res.total_reservations, 0) AS total_reservations,
    COALESCE(res.converted_reservations, 0) AS converted_reservations,
    COALESCE(paid.paid_orders, 0) AS paid_orders,
    COALESCE(paid.tickets_sold, 0) AS tickets_sold,
    COALESCE(paid.gross_revenue, 0.00) AS gross_revenue
FROM matches m
JOIN national_teams ht ON ht.id = m.home_team_id
JOIN national_teams at ON at.id = m.away_team_id
LEFT JOIN (
    SELECT
        ms.match_id,
        COUNT(DISTINCT r.id) AS total_reservations,
        COUNT(DISTINCT CASE WHEN r.status = 'CONVERTED' THEN r.id END) AS converted_reservations
    FROM reservations r
    JOIN reservation_items ri ON ri.reservation_id = r.id
    JOIN match_sectors ms ON ms.id = ri.match_sector_id
    GROUP BY ms.match_id
) res ON res.match_id = m.id
LEFT JOIN (
    SELECT
        ms.match_id,
        COUNT(DISTINCT o.id) AS paid_orders,
        COALESCE(SUM(ri.quantity), 0) AS tickets_sold,
        COALESCE(SUM(ri.line_total), 0.00) AS gross_revenue
    FROM orders o
    JOIN reservations r ON r.id = o.reservation_id
    JOIN reservation_items ri ON ri.reservation_id = r.id
    JOIN match_sectors ms ON ms.id = ri.match_sector_id
    WHERE o.status = 'PAID'
    GROUP BY ms.match_id
) paid ON paid.match_id = m.id;

CREATE OR REPLACE VIEW vw_seat_map_availability AS
SELECT
    m.id AS match_id,
    m.match_number,
    m.group_name,
    ht.team_name AS home_team,
    at.team_name AS away_team,
    m.match_at,
    v.id AS venue_id,
    v.name AS venue_name,
    v.city,
    v.country,
    v.time_zone AS venue_time_zone,
    ms.id AS match_sector_id,
    ms.sector_code,
    ms.sector_name,
    ms.price,
    vs.id AS venue_seat_id,
    vs.seat_ordinal,
    vs.seat_label,
    vs.block_code,
    vs.row_label,
    vs.seat_row_number,
    vs.seat_number,
    vs.map_x,
    vs.map_y,
    vs.gate,
    vs.entrance,
    CASE
        WHEN vs.status = 'BLOCKED' THEN 'BLOCKED'
        WHEN msa.status IS NULL THEN 'AVAILABLE'
        ELSE msa.status
    END AS seat_status,
    msa.id AS match_seat_allocation_id,
    msa.reservation_id,
    msa.reservation_item_id,
    msa.order_id
FROM matches m
JOIN venues v ON v.id = m.venue_id
JOIN national_teams ht ON ht.id = m.home_team_id
JOIN national_teams at ON at.id = m.away_team_id
JOIN venue_seats vs ON vs.venue_id = v.id
JOIN match_sectors ms
  ON ms.match_id = m.id
 AND ms.sector_code = vs.sector_code
LEFT JOIN match_seat_allocations msa
  ON msa.match_id = m.id
 AND msa.venue_seat_id = vs.id
 AND msa.status IN ('RESERVED','SOLD','BLOCKED');

CREATE OR REPLACE VIEW vw_seat_heatmap_by_block AS
SELECT
    match_id,
    match_number,
    group_name,
    home_team,
    away_team,
    match_at,
    venue_id,
    venue_name,
    city,
    country,
    match_sector_id,
    sector_code,
    sector_name,
    block_code,
    MIN(map_x) AS min_map_x,
    MAX(map_x) AS max_map_x,
    MIN(map_y) AS min_map_y,
    MAX(map_y) AS max_map_y,
    COUNT(*) AS total_seats,
    SUM(CASE WHEN seat_status = 'AVAILABLE' THEN 1 ELSE 0 END) AS available_seats,
    SUM(CASE WHEN seat_status = 'RESERVED' THEN 1 ELSE 0 END) AS reserved_seats,
    SUM(CASE WHEN seat_status = 'SOLD' THEN 1 ELSE 0 END) AS sold_seats,
    SUM(CASE WHEN seat_status = 'BLOCKED' THEN 1 ELSE 0 END) AS blocked_seats,
    ROUND(
        (
            SUM(CASE WHEN seat_status IN ('RESERVED','SOLD','BLOCKED') THEN 1 ELSE 0 END)
            / NULLIF(COUNT(*), 0)
        ) * 100,
        2
    ) AS heat_percent
FROM vw_seat_map_availability
GROUP BY
    match_id,
    match_number,
    group_name,
    home_team,
    away_team,
    match_at,
    venue_id,
    venue_name,
    city,
    country,
    match_sector_id,
    sector_code,
    sector_name,
    block_code;

-- ============================================================
-- HEATWAVE / RAPID SECONDARY ENGINE LOAD
-- ============================================================
-- Este bloco carrega as tabelas no motor analitico HeatWave/RAPID.
-- Execute em ambiente MySQL HeatWave com cluster analitico ativo.
-- Em ambientes sem HeatWave, remova ou comente este bloco antes do apply.

SET SESSION use_secondary_engine = ON;

ALTER TABLE venues SECONDARY_LOAD;
ALTER TABLE national_teams SECONDARY_LOAD;
ALTER TABLE matches SECONDARY_LOAD;
ALTER TABLE match_results SECONDARY_LOAD;
ALTER TABLE match_sectors SECONDARY_LOAD;
ALTER TABLE customers SECONDARY_LOAD;
ALTER TABLE reservations SECONDARY_LOAD;
ALTER TABLE reservation_items SECONDARY_LOAD;
ALTER TABLE orders SECONDARY_LOAD;
ALTER TABLE payments SECONDARY_LOAD;
ALTER TABLE venue_seats SECONDARY_LOAD;
ALTER TABLE match_seat_allocations SECONDARY_LOAD;
ALTER TABLE tickets SECONDARY_LOAD;

-- ============================================================
-- VISOES ANALITICAS DE NEGOCIO
-- ============================================================

CREATE OR REPLACE VIEW vw_hw_realtime_executive_dashboard AS
SELECT
    NOW() AS snapshot_at,
    match_summary.total_matches,
    match_summary.available_matches,
    match_summary.sold_out_matches,
    match_summary.closed_matches,
    match_summary.total_match_capacity,
    stock_summary.total_inventory_quantity,
    stock_summary.reserved_seats,
    stock_summary.sold_seats,
    stock_summary.available_seats,
    stock_summary.occupied_seats,
    ROUND(stock_summary.occupied_seats / NULLIF(stock_summary.total_inventory_quantity, 0) * 100, 2) AS occupancy_percent,
    paid_summary.paid_orders,
    paid_summary.tickets_issued,
    paid_summary.gross_revenue,
    ROUND(paid_summary.gross_revenue / NULLIF(paid_summary.tickets_issued, 0), 2) AS avg_ticket_price,
    pending_summary.payment_pending_orders,
    pending_summary.payment_pending_amount,
    reservation_summary.reservations_created,
    reservation_summary.active_reservations,
    reservation_summary.converted_reservations,
    reservation_summary.expired_reservations,
    reservation_summary.cancelled_reservations,
    ROUND(reservation_summary.converted_reservations / NULLIF(reservation_summary.reservations_created, 0) * 100, 2) AS conversion_percent
FROM
    (
        SELECT
            COUNT(*) AS total_matches,
            SUM(status = 'AVAILABLE') AS available_matches,
            SUM(status = 'SOLD_OUT') AS sold_out_matches,
            SUM(status = 'CLOSED') AS closed_matches,
            COALESCE(SUM(venue_capacity_snapshot), 0) AS total_match_capacity
        FROM matches
    ) AS match_summary
CROSS JOIN
    (
        SELECT
            COALESCE(SUM(total_quantity), 0) AS total_inventory_quantity,
            COALESCE(SUM(reserved_quantity), 0) AS reserved_seats,
            COALESCE(SUM(sold_quantity), 0) AS sold_seats,
            COALESCE(SUM(total_quantity - reserved_quantity - sold_quantity), 0) AS available_seats,
            COALESCE(SUM(reserved_quantity + sold_quantity), 0) AS occupied_seats
        FROM match_sectors
    ) AS stock_summary
CROSS JOIN
    (
        SELECT
            COUNT(DISTINCT o.id) AS paid_orders,
            COALESCE(SUM(ri.quantity), 0) AS tickets_issued,
            COALESCE(SUM(ri.line_total), 0) AS gross_revenue
        FROM orders o
        JOIN reservations r ON r.id = o.reservation_id
        JOIN reservation_items ri ON ri.reservation_id = r.id
        WHERE o.status = 'PAID'
    ) AS paid_summary
CROSS JOIN
    (
        SELECT
            COUNT(*) AS payment_pending_orders,
            COALESCE(SUM(total_amount), 0) AS payment_pending_amount
        FROM orders
        WHERE status = 'PAYMENT_PENDING'
    ) AS pending_summary
CROSS JOIN
    (
        SELECT
            COUNT(*) AS reservations_created,
            SUM(status = 'RESERVED') AS active_reservations,
            SUM(status = 'CONVERTED') AS converted_reservations,
            SUM(status = 'EXPIRED') AS expired_reservations,
            SUM(status = 'CANCELLED') AS cancelled_reservations
        FROM reservations
    ) AS reservation_summary;

CREATE OR REPLACE VIEW vw_hw_match_business_scorecard AS
SELECT
    m.id AS match_id,
    m.match_number,
    m.group_name,
    ht.team_name AS home_team,
    at.team_name AS away_team,
    m.match_at,
    m.match_at_utc,
    v.name AS venue_name,
    v.city,
    v.state,
    v.country,
    v.time_zone AS venue_time_zone,
    m.status AS match_status,
    m.venue_capacity_snapshot AS match_capacity,
    COALESCE(stock.total_quantity, 0) AS total_inventory_quantity,
    COALESCE(stock.reserved_quantity, 0) AS reserved_seats,
    COALESCE(stock.sold_quantity, 0) AS sold_seats,
    COALESCE(stock.available_quantity, 0) AS available_seats,
    ROUND((COALESCE(stock.reserved_quantity, 0) + COALESCE(stock.sold_quantity, 0)) / NULLIF(stock.total_quantity, 0) * 100, 2) AS occupancy_percent,
    COALESCE(paid.paid_orders, 0) AS paid_orders,
    COALESCE(paid.tickets_issued, 0) AS tickets_issued,
    COALESCE(paid.gross_revenue, 0) AS gross_revenue,
    ROUND(COALESCE(paid.gross_revenue, 0) / NULLIF(paid.tickets_issued, 0), 2) AS avg_ticket_price,
    COALESCE(pending.payment_pending_orders, 0) AS payment_pending_orders,
    COALESCE(pending.payment_pending_seats, 0) AS payment_pending_seats,
    COALESCE(pending.payment_pending_amount, 0) AS payment_pending_amount,
    COALESCE(reserved_only.reserved_only_reservations, 0) AS reserved_only_reservations,
    COALESCE(reserved_only.reserved_only_seats, 0) AS reserved_only_seats
FROM matches m
JOIN venues v ON v.id = m.venue_id
JOIN national_teams ht ON ht.id = m.home_team_id
JOIN national_teams at ON at.id = m.away_team_id
LEFT JOIN (
    SELECT
        match_id,
        SUM(total_quantity) AS total_quantity,
        SUM(reserved_quantity) AS reserved_quantity,
        SUM(sold_quantity) AS sold_quantity,
        SUM(total_quantity - reserved_quantity - sold_quantity) AS available_quantity
    FROM match_sectors
    GROUP BY match_id
) AS stock ON stock.match_id = m.id
LEFT JOIN (
    SELECT
        ms.match_id,
        COUNT(DISTINCT o.id) AS paid_orders,
        SUM(ri.quantity) AS tickets_issued,
        SUM(ri.line_total) AS gross_revenue
    FROM orders o
    JOIN reservations r ON r.id = o.reservation_id
    JOIN reservation_items ri ON ri.reservation_id = r.id
    JOIN match_sectors ms ON ms.id = ri.match_sector_id
    WHERE o.status = 'PAID'
    GROUP BY ms.match_id
) AS paid ON paid.match_id = m.id
LEFT JOIN (
    SELECT
        order_match.match_id,
        COUNT(*) AS payment_pending_orders,
        SUM(order_match.quantity) AS payment_pending_seats,
        SUM(order_match.total_amount) AS payment_pending_amount
    FROM (
        SELECT
            ms.match_id,
            o.id AS order_id,
            o.total_amount,
            SUM(ri.quantity) AS quantity
        FROM orders o
        JOIN reservations r ON r.id = o.reservation_id
        JOIN reservation_items ri ON ri.reservation_id = r.id
        JOIN match_sectors ms ON ms.id = ri.match_sector_id
        WHERE o.status = 'PAYMENT_PENDING'
        GROUP BY ms.match_id, o.id, o.total_amount
    ) AS order_match
    GROUP BY order_match.match_id
) AS pending ON pending.match_id = m.id
LEFT JOIN (
    SELECT
        ms.match_id,
        COUNT(DISTINCT r.id) AS reserved_only_reservations,
        SUM(ri.quantity) AS reserved_only_seats
    FROM reservations r
    JOIN reservation_items ri ON ri.reservation_id = r.id
    JOIN match_sectors ms ON ms.id = ri.match_sector_id
    LEFT JOIN orders o ON o.reservation_id = r.id
    WHERE r.status = 'RESERVED'
      AND o.id IS NULL
    GROUP BY ms.match_id
) AS reserved_only ON reserved_only.match_id = m.id;

CREATE OR REPLACE VIEW vw_hw_sector_business_demand AS
SELECT
    ms.id AS match_sector_id,
    m.id AS match_id,
    m.match_number,
    m.group_name,
    ht.team_name AS home_team,
    at.team_name AS away_team,
    v.name AS venue_name,
    v.city,
    v.country,
    ms.sector_code,
    ms.sector_name,
    ms.price,
    ms.status AS sector_status,
    ms.total_quantity,
    ms.reserved_quantity,
    ms.sold_quantity,
    ms.total_quantity - ms.reserved_quantity - ms.sold_quantity AS available_quantity,
    ROUND((ms.reserved_quantity + ms.sold_quantity) / NULLIF(ms.total_quantity, 0) * 100, 2) AS occupancy_percent,
    COALESCE(paid.paid_orders, 0) AS paid_orders,
    COALESCE(paid.tickets_issued, 0) AS tickets_issued,
    COALESCE(paid.gross_revenue, 0) AS gross_revenue,
    ROUND(COALESCE(paid.gross_revenue, 0) / NULLIF(paid.tickets_issued, 0), 2) AS avg_ticket_price
FROM match_sectors ms
JOIN matches m ON m.id = ms.match_id
JOIN venues v ON v.id = m.venue_id
JOIN national_teams ht ON ht.id = m.home_team_id
JOIN national_teams at ON at.id = m.away_team_id
LEFT JOIN (
    SELECT
        ri.match_sector_id,
        COUNT(DISTINCT o.id) AS paid_orders,
        SUM(ri.quantity) AS tickets_issued,
        SUM(ri.line_total) AS gross_revenue
    FROM orders o
    JOIN reservations r ON r.id = o.reservation_id
    JOIN reservation_items ri ON ri.reservation_id = r.id
    WHERE o.status = 'PAID'
    GROUP BY ri.match_sector_id
) AS paid ON paid.match_sector_id = ms.id;

CREATE OR REPLACE VIEW vw_hw_host_country_business_revenue AS
SELECT
    host.country,
    host.matches_count,
    host.total_match_capacity,
    COALESCE(paid.paid_orders, 0) AS paid_orders,
    COALESCE(paid.tickets_issued, 0) AS tickets_issued,
    COALESCE(paid.gross_revenue, 0) AS gross_revenue,
    ROUND(COALESCE(paid.gross_revenue, 0) / NULLIF(paid.tickets_issued, 0), 2) AS avg_ticket_price,
    ROUND(COALESCE(paid.tickets_issued, 0) / NULLIF(host.total_match_capacity, 0) * 100, 2) AS confirmed_occupancy_percent
FROM (
    SELECT
        v.country,
        COUNT(*) AS matches_count,
        SUM(m.venue_capacity_snapshot) AS total_match_capacity
    FROM matches m
    JOIN venues v ON v.id = m.venue_id
    GROUP BY v.country
) AS host
LEFT JOIN (
    SELECT
        v.country,
        COUNT(DISTINCT o.id) AS paid_orders,
        SUM(ri.quantity) AS tickets_issued,
        SUM(ri.line_total) AS gross_revenue
    FROM orders o
    JOIN reservations r ON r.id = o.reservation_id
    JOIN reservation_items ri ON ri.reservation_id = r.id
    JOIN match_sectors ms ON ms.id = ri.match_sector_id
    JOIN matches m ON m.id = ms.match_id
    JOIN venues v ON v.id = m.venue_id
    WHERE o.status = 'PAID'
    GROUP BY v.country
) AS paid ON paid.country = host.country;

CREATE OR REPLACE VIEW vw_hw_payment_method_business_summary AS
SELECT
    p.payment_method,
    p.status AS payment_status,
    COUNT(*) AS payment_count,
    SUM(p.amount) AS total_amount,
    ROUND(AVG(p.amount), 2) AS avg_payment_amount,
    MIN(p.created_at) AS first_payment_at,
    MAX(p.created_at) AS latest_payment_at
FROM payments p
GROUP BY p.payment_method, p.status;

CREATE OR REPLACE VIEW vw_hw_seat_heatmap_business_live AS
SELECT
    m.id AS match_id,
    m.match_number,
    ht.team_name AS home_team,
    at.team_name AS away_team,
    v.name AS venue_name,
    v.city,
    v.country,
    ms.sector_code,
    vs.block_code,
    MIN(vs.map_x) AS min_map_x,
    MAX(vs.map_x) AS max_map_x,
    MIN(vs.map_y) AS min_map_y,
    MAX(vs.map_y) AS max_map_y,
    COUNT(*) AS total_seats,
    SUM(CASE WHEN vs.status <> 'BLOCKED' AND msa.id IS NULL THEN 1 ELSE 0 END) AS available_seats,
    SUM(CASE WHEN msa.status = 'RESERVED' THEN 1 ELSE 0 END) AS reserved_seats,
    SUM(CASE WHEN msa.status = 'SOLD' THEN 1 ELSE 0 END) AS sold_seats,
    SUM(CASE WHEN vs.status = 'BLOCKED' OR msa.status = 'BLOCKED' THEN 1 ELSE 0 END) AS blocked_seats,
    ROUND(
        (
            SUM(
                CASE
                    WHEN vs.status = 'BLOCKED'
                      OR msa.status IN ('RESERVED','SOLD','BLOCKED')
                    THEN 1
                    ELSE 0
                END
            ) / NULLIF(COUNT(*), 0)
        ) * 100,
        2
    ) AS heat_percent,
    CASE
        WHEN ROUND(
            (
                SUM(
                    CASE
                        WHEN vs.status = 'BLOCKED'
                          OR msa.status IN ('RESERVED','SOLD','BLOCKED')
                        THEN 1
                        ELSE 0
                    END
                ) / NULLIF(COUNT(*), 0)
            ) * 100,
            2
        ) >= 90 THEN 'CRITICAL_DEMAND'
        WHEN ROUND(
            (
                SUM(
                    CASE
                        WHEN vs.status = 'BLOCKED'
                          OR msa.status IN ('RESERVED','SOLD','BLOCKED')
                        THEN 1
                        ELSE 0
                    END
                ) / NULLIF(COUNT(*), 0)
            ) * 100,
            2
        ) >= 70 THEN 'HIGH_DEMAND'
        WHEN ROUND(
            (
                SUM(
                    CASE
                        WHEN vs.status = 'BLOCKED'
                          OR msa.status IN ('RESERVED','SOLD','BLOCKED')
                        THEN 1
                        ELSE 0
                    END
                ) / NULLIF(COUNT(*), 0)
            ) * 100,
            2
        ) >= 40 THEN 'MEDIUM_DEMAND'
        ELSE 'LOW_DEMAND'
    END AS demand_band
FROM matches m
JOIN venues v ON v.id = m.venue_id
JOIN national_teams ht ON ht.id = m.home_team_id
JOIN national_teams at ON at.id = m.away_team_id
JOIN venue_seats vs ON vs.venue_id = v.id
JOIN match_sectors ms
  ON ms.match_id = m.id
 AND ms.sector_code = vs.sector_code
LEFT JOIN match_seat_allocations msa
  ON msa.match_id = m.id
 AND msa.venue_seat_id = vs.id
 AND msa.status IN ('RESERVED','SOLD','BLOCKED')
GROUP BY
    m.id,
    m.match_number,
    ht.team_name,
    at.team_name,
    v.name,
    v.city,
    v.country,
    ms.sector_code,
    vs.block_code;

-- ============================================================
-- USUÁRIO DA APLICAÇÃO
-- ============================================================
--
-- Usuário dedicado para a demonstração.
-- Permissão completa no esquema copa_ticketing_demo.
-- Permissões globais mínimas para metadata/diagrama e HeatWave.
-- Sem GRANT OPTION.
--
-- Observação HeatWave GenAI:
-- A tela de linguagem natural chama a rotina oficial sys.NL_SQL.
-- Essa rotina aciona rotinas internas do schema sys durante a geração do SQL.
-- Por isso, além das permissões no schema da Copa, o usuário app precisa de:
-- - SHOW DATABASES para ferramentas de diagrama enxergarem o schema;
-- - SELECT/EXECUTE/CREATE TEMPORARY TABLES no schema sys para NL_SQL/HeatWave;
-- - VECTOR_STORE_LOAD_EXEC para recursos HeatWave GenAI/vector quando disponíveis.
--
-- Observação da demo compartilhada:
-- Para evitar bloqueios em ferramentas externas durante a demonstração,
-- a role administrator também é atribuída ao app e definida como default.

CREATE USER IF NOT EXISTS 'app'@'%' IDENTIFIED BY 'CopaTicketing#2026_App!9Qx';

ALTER USER 'app'@'%' IDENTIFIED BY 'CopaTicketing#2026_App!9Qx';

GRANT ALL PRIVILEGES ON copa_ticketing_demo.* TO 'app'@'%';

GRANT SHOW DATABASES ON *.* TO 'app'@'%';

GRANT SELECT, EXECUTE, CREATE TEMPORARY TABLES ON sys.* TO 'app'@'%';

GRANT VECTOR_STORE_LOAD_EXEC ON *.* TO 'app'@'%';

GRANT `administrator`@`%` TO `app`@`%`;

SET DEFAULT ROLE `administrator`@`%` TO `app`@`%`;

FLUSH PRIVILEGES;
