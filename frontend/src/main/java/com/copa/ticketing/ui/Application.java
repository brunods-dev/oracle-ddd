package com.copa.ticketing.ui;

import com.copa.ticketing.ui.config.DotEnvLoader;
import com.copa.ticketing.ui.config.NativeRuntimeHints;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.server.PWA;
import com.vaadin.flow.theme.Theme;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
@ImportRuntimeHints(NativeRuntimeHints.class)
@Theme("copa2026")
@PWA(name = "Copa Ticketing 2026", shortName = "Copa26")
@Push
public class Application implements AppShellConfigurator {

    public static void main(String[] args) {
        DotEnvLoader.load();
        SpringApplication.run(Application.class, args);
    }
}
