package com.copa.ticketing.service;

import com.copa.ticketing.domain.SelloutEvent;
import com.copa.ticketing.domain.SelloutStatus;
import com.copa.ticketing.repository.SelloutRepository;

import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SelloutJobManager {

    private static final Logger LOG = Logger.getLogger(SelloutJobManager.class.getName());
    private static final int EVENT_BUFFER_SIZE = 24;
    private static final long STATUS_CACHE_TTL_MS = 2_000;
    private static final long MATCH_OPTIONS_CACHE_TTL_MS = 5 * 60 * 1_000;

    private final SelloutRepository selloutRepo;
    private final MatchSelloutSimulator simulator;
    private final ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "sellout-sim");
        t.setDaemon(true);
        return t;
    });

    private final Deque<SelloutEvent> events = new ArrayDeque<>(EVENT_BUFFER_SIZE);
    private final Map<Integer, CachedStatus> statusCache = new ConcurrentHashMap<>();
    private final AtomicReference<Future<?>> activeJob = new AtomicReference<>();
    private final AtomicInteger activeMatchNumber = new AtomicInteger(-1);

    private volatile long matchOptionsCacheExpiry = 0;
    private volatile List<?> matchOptionsPayload = null;

    private volatile Runnable onBatchCallback = () -> {};
    private final Map<Integer, Long> lastOccupiedByMatch = new ConcurrentHashMap<>();
    private final Map<Integer, Long> lastSoldByMatch = new ConcurrentHashMap<>();

    public SelloutJobManager(SelloutRepository selloutRepo, MatchSelloutSimulator simulator) {
        this.selloutRepo = selloutRepo;
        this.simulator = simulator;
    }

    public void setOnBatchCallback(Runnable callback) {
        this.onBatchCallback = callback != null ? callback : () -> {};
    }

    public synchronized void start(int matchNumber, SelloutConfig cfg) throws SQLException {
        if (activeJob.get() != null && !activeJob.get().isDone()) {
            throw new IllegalStateException("A carga real já está rodando.");
        }

        SelloutStatus current = loadStatusFresh(matchNumber, List.of());
        if (current.totals().available() <= 0) {
            throw new IllegalStateException("O jogo número " + matchNumber + " já está esgotado.");
        }

        clearEvents();
        statusCache.clear();
        lastOccupiedByMatch.remove(matchNumber);
        lastSoldByMatch.remove(matchNumber);
        activeMatchNumber.set(matchNumber);

        pushEvent("Iniciando carga real no MySQL para match_number=" + matchNumber +
                ". Distribuição: RESERVED=" + cfg.reservedPercent() +
                "% PAYMENT_PENDING=" + cfg.paymentPendingPercent() +
                "% ISSUED=" + cfg.issuedPercent() + "%.");

        Future<?> job = exec.submit(() -> {
            try {
                simulator.run(cfg, (msg, totals) -> {
                    pushEvent(msg);
                    statusCache.remove(matchNumber);
                    onBatchCallback.run();
                });
                pushEvent("Carga real concluída.");
            } catch (Exception e) {
                pushEvent("Erro na carga: " + e.getMessage());
                LOG.log(Level.WARNING, "Sellout job failed", e);
            } finally {
                activeJob.set(null);
                activeMatchNumber.set(-1);
                statusCache.remove(matchNumber);
            }
        });
        activeJob.set(job);
    }

    public synchronized void reset(int matchNumber) throws SQLException {
        Future<?> job = activeJob.get();
        if (job != null && !job.isDone()) {
            throw new IllegalStateException("A carga real ainda está rodando. Aguarde finalizar antes de apagar os dados.");
        }
        selloutRepo.resetDemoTransactions();
        clearEvents();
        statusCache.clear();
        lastOccupiedByMatch.clear();
        lastSoldByMatch.clear();
        matchOptionsCacheExpiry = 0;
        matchOptionsPayload = null;
        pushEvent("TRUNCATE executado nas tabelas transacionais.");
        pushEvent("Dados de teste apagados. Estoque, setores e partida liberados para iniciar o fluxo novamente.");
    }

    public SelloutStatus getStatus(int matchNumber) throws SQLException {
        CachedStatus cached = statusCache.get(matchNumber);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiresAt > now) {
            return enrichWithRuntime(cached.status);
        }

        SelloutStatus fresh = loadStatusFresh(matchNumber, snapshotEvents());
        fresh = applyDeltas(matchNumber, fresh);
        statusCache.put(matchNumber, new CachedStatus(fresh, now + STATUS_CACHE_TTL_MS));
        return enrichWithRuntime(fresh);
    }

    public boolean isRunning() {
        Future<?> job = activeJob.get();
        return job != null && !job.isDone();
    }

    public int getActiveMatchNumber() {
        return activeMatchNumber.get();
    }

    public List<SelloutEvent> snapshotEvents() {
        synchronized (events) {
            return new ArrayList<>(events);
        }
    }

    @SuppressWarnings("unchecked")
    public List<?> getMatchOptions() throws SQLException {
        if (matchOptionsPayload != null && System.currentTimeMillis() < matchOptionsCacheExpiry) {
            return matchOptionsPayload;
        }
        List<?> options = selloutRepo.loadMatchOptions();
        matchOptionsPayload = options;
        matchOptionsCacheExpiry = System.currentTimeMillis() + MATCH_OPTIONS_CACHE_TTL_MS;
        return options;
    }

    public void shutdown() {
        exec.shutdownNow();
    }

    private SelloutStatus loadStatusFresh(int matchNumber, List<SelloutEvent> currentEvents) throws SQLException {
        SelloutStatus raw = selloutRepo.loadStatus(matchNumber);
        boolean running = isRunning();
        Integer actMatch = activeMatchNumber.get() < 0 ? null : activeMatchNumber.get();
        return new SelloutStatus(
                raw.matchId(), raw.matchNumber(), raw.matchStatus(), raw.capacity(),
                raw.homeTeam(), raw.awayTeam(), raw.venueName(), raw.city(), raw.country(), raw.venueTimeZone(),
                raw.sectors(), raw.totals(), raw.statusMix(), raw.paidOrders(), raw.revenue(),
                raw.deltaSold(), raw.deltaOccupied(),
                running, actMatch, raw.progressPercent(), currentEvents
        );
    }

    private SelloutStatus applyDeltas(int matchNumber, SelloutStatus status) {
        if (status.totals() == null) return status;
        long sold = status.totals().sold();
        long occupied = sold + status.totals().reserved();
        long prevSold = lastSoldByMatch.getOrDefault(matchNumber, 0L);
        long prevOccupied = lastOccupiedByMatch.getOrDefault(matchNumber, 0L);
        long deltaSold = Math.max(0, sold - prevSold);
        long deltaOccupied = Math.max(0, occupied - prevOccupied);
        lastSoldByMatch.put(matchNumber, sold);
        lastOccupiedByMatch.put(matchNumber, occupied);
        return new SelloutStatus(
                status.matchId(), status.matchNumber(), status.matchStatus(), status.capacity(),
                status.homeTeam(), status.awayTeam(), status.venueName(), status.city(),
                status.country(), status.venueTimeZone(),
                status.sectors(), status.totals(), status.statusMix(), status.paidOrders(), status.revenue(),
                deltaSold, deltaOccupied,
                status.running(), status.activeMatchNumber(), status.progressPercent(), status.events()
        );
    }

    private SelloutStatus enrichWithRuntime(SelloutStatus status) {
        boolean running = isRunning();
        Integer actMatch = activeMatchNumber.get() < 0 ? null : activeMatchNumber.get();
        if (status.running() == running && Objects.equals(status.activeMatchNumber(), actMatch)
                && status.events().size() == snapshotEvents().size()) {
            return status;
        }
        return new SelloutStatus(
                status.matchId(), status.matchNumber(), status.matchStatus(), status.capacity(),
                status.homeTeam(), status.awayTeam(), status.venueName(), status.city(),
                status.country(), status.venueTimeZone(),
                status.sectors(), status.totals(), status.statusMix(), status.paidOrders(), status.revenue(),
                status.deltaSold(), status.deltaOccupied(),
                running, actMatch, status.progressPercent(), snapshotEvents()
        );
    }

    private void pushEvent(String message) {
        SelloutEvent ev = new SelloutEvent(Instant.now().toString(), message);
        synchronized (events) {
            events.addFirst(ev);
            while (events.size() > EVENT_BUFFER_SIZE) events.removeLast();
        }
    }

    private void clearEvents() {
        synchronized (events) {
            events.clear();
        }
    }

    private record CachedStatus(SelloutStatus status, long expiresAt) {}
}
