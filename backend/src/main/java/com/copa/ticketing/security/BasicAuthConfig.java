package com.copa.ticketing.security;

import com.copa.ticketing.config.AppConfig;
import io.helidon.security.Security;
import io.helidon.security.providers.httpauth.HttpBasicAuthProvider;
import io.helidon.security.providers.httpauth.SecureUserStore;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public final class BasicAuthConfig {

    private BasicAuthConfig() {}

    public static Security build(AppConfig cfg) {
        var store = buildStore(cfg);
        var basicAuth = HttpBasicAuthProvider.builder()
                .realm("Copa Ticketing")
                .userStore(store)
                .build();

        return Security.builder()
                .addProvider(basicAuth)
                .build();
    }

    private static SecureUserStore buildStore(AppConfig cfg) {
        var admin = new SimpleUser(cfg.adminUser(), cfg.adminPass(), List.of("ADMIN", "CUSTOMER"));
        var customer = new SimpleUser(cfg.customerUser(), cfg.customerPass(), List.of("CUSTOMER"));

        return login -> {
            if (cfg.adminUser().equals(login)) return Optional.of(admin);
            if (cfg.customerUser().equals(login)) return Optional.of(customer);
            return Optional.empty();
        };
    }

    private record SimpleUser(String login, String password, Collection<String> roles)
            implements SecureUserStore.User {

        @Override
        public boolean isPasswordValid(char[] pass) {
            return password.equals(new String(pass));
        }
    }
}
