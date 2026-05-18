package com.copa.ticketing.rest;

import com.copa.ticketing.config.AppConfig;
import com.copa.ticketing.pagination.Page;
import com.copa.ticketing.repository.DashboardRepository;
import com.copa.ticketing.repository.OrderRepository;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import java.sql.SQLException;

public class AdminRoutes implements HttpService {

    private final DashboardRepository dashboardRepo;
    private final OrderRepository orderRepo;
    private final AppConfig cfg;

    public AdminRoutes(DashboardRepository dashboardRepo, OrderRepository orderRepo, AppConfig cfg) {
        this.dashboardRepo = dashboardRepo;
        this.orderRepo = orderRepo;
        this.cfg = cfg;
    }

    @Override
    public void routing(HttpRules rules) {
        rules
            .get("/dashboard", this::getDashboard)
            .get("/orders", this::listOrders)
            .get("/inventory", this::getInventory)
            .get("/payment-summary", this::getPaymentSummary);
    }

    private void getDashboard(ServerRequest req, ServerResponse res) {
        try {
            JsonUtil.ok(res, dashboardRepo.getSummary());
        } catch (SQLException e) {
            JsonUtil.error(res, 500, "Database error: " + e.getMessage());
        }
    }

    private void listOrders(ServerRequest req, ServerResponse res) {
        try {
            String status = JsonUtil.queryStr(req, "status");
            int page = JsonUtil.queryInt(req, "page", 0);
            int size = Page.clampSize(JsonUtil.queryInt(req, "size", 0), cfg.defaultPageSize(), cfg.maxPageSize());
            JsonUtil.ok(res, orderRepo.findAll(status, page, size));
        } catch (SQLException e) {
            JsonUtil.error(res, 500, "Database error: " + e.getMessage());
        }
    }

    private void getInventory(ServerRequest req, ServerResponse res) {
        try {
            String matchIdStr = JsonUtil.queryStr(req, "matchId");
            Long matchId = matchIdStr != null ? Long.parseLong(matchIdStr) : null;
            JsonUtil.ok(res, dashboardRepo.getInventory(matchId));
        } catch (NumberFormatException e) {
            JsonUtil.error(res, 400, "Invalid matchId");
        } catch (SQLException e) {
            JsonUtil.error(res, 500, "Database error: " + e.getMessage());
        }
    }

    private void getPaymentSummary(ServerRequest req, ServerResponse res) {
        try {
            JsonUtil.ok(res, dashboardRepo.getPaymentSummary());
        } catch (SQLException e) {
            JsonUtil.error(res, 500, "Database error: " + e.getMessage());
        }
    }
}
