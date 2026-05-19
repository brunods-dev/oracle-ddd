package com.copa.ticketing.db;

import com.copa.ticketing.config.AppConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

public final class DataSourceProvider {

    private DataSourceProvider() {}

    public static HikariDataSource create(AppConfig cfg) {
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(cfg.dbUrl());
        hikari.setUsername(cfg.dbUser());
        hikari.setPassword(cfg.dbPass());
        hikari.setPoolName("copa-pool");
        hikari.setMaximumPoolSize(cfg.dbPoolSize());
        hikari.setMinimumIdle(2);
        hikari.setConnectionTimeout(3_000);
        hikari.setIdleTimeout(600_000);
        hikari.setMaxLifetime(1_800_000);
        hikari.setConnectionTestQuery("SELECT 1");
        hikari.addDataSourceProperty("cachePrepStmts", "true");
        hikari.addDataSourceProperty("prepStmtCacheSize", "250");
        hikari.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikari.addDataSourceProperty("useServerPrepStmts", "true");
        hikari.addDataSourceProperty("rewriteBatchedStatements", "true");
        return new HikariDataSource(hikari);
    }
}
