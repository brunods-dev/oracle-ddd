package com.copa.ticketing;

import com.copa.ticketing.config.AppConfig;
import com.copa.ticketing.config.DotEnvLoader;
import com.copa.ticketing.db.DataSourceProvider;
import com.copa.ticketing.repository.*;
import com.copa.ticketing.rest.AdminRoutes;
import com.copa.ticketing.rest.LiveDemoRoutes;
import com.copa.ticketing.rest.PublicRoutes;
import com.copa.ticketing.security.BasicAuthConfig;
import com.copa.ticketing.service.MatchSelloutSimulator;
import com.copa.ticketing.service.SelloutJobManager;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.security.SecurityFeature;

import java.util.logging.Logger;

public class Main {

    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
        DotEnvLoader.load();

        Config config = Config.builder()
                .addSource(ConfigSources.classpath("application.yaml").optional())
                .addSource(ConfigSources.environmentVariables())
                .build();

        AppConfig cfg = AppConfig.from(config);
        LOG.info("Database user=" + cfg.dbUser() + ", password length=" + cfg.dbPass().length());

        var ds = DataSourceProvider.create(cfg);

        var matchRepo = new MatchRepository(ds);
        var customerRepo = new CustomerRepository(ds);
        var reservationRepo = new ReservationRepository(ds);
        var orderRepo = new OrderRepository(ds);
        var dashboardRepo = new DashboardRepository(ds);
        var hwRepo = new HeatwaveRepository(ds);
        var selloutRepo = new SelloutRepository(ds);
        var simulator = new MatchSelloutSimulator(ds);
        var jobManager = new SelloutJobManager(selloutRepo, simulator);

        var security = BasicAuthConfig.build(cfg);
        var secFeature = SecurityFeature.builder()
                .security(security)
                .build();

        var publicRoutes = new PublicRoutes(matchRepo, customerRepo, reservationRepo, orderRepo, cfg);
        var adminRoutes = new AdminRoutes(dashboardRepo, orderRepo, cfg);
        var liveDemoRoutes = new LiveDemoRoutes(hwRepo, jobManager);

        var server = WebServer.builder()
                .port(cfg.serverPort())
                .host("0.0.0.0")
                .addFeature(secFeature)
                .routing(HttpRouting.builder()
                        .register("/api/public", publicRoutes)
                        .any("/api/admin/{+}", SecurityFeature.rolesAllowed("ADMIN"))
                        .register("/api/admin", adminRoutes)
                        .register("/api/admin", liveDemoRoutes)
                        .get("/health", (req, res) -> res.send("{\"status\":\"UP\"}"))
                        .options("/{+}", (req, res) -> {
                            res.header("Access-Control-Allow-Origin", "*");
                            res.header("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
                            res.header("Access-Control-Allow-Headers", "Authorization,Content-Type");
                            res.send();
                        })
                )
                .build();

        server.start();
        LOG.info("Copa Ticketing Backend started on port " + cfg.serverPort());
        LOG.info("Health: http://localhost:" + cfg.serverPort() + "/health");
    }
}
