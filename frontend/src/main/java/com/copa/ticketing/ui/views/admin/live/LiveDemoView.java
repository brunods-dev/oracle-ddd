package com.copa.ticketing.ui.views.admin.live;

import com.copa.ticketing.ui.client.BackendClient;
import com.copa.ticketing.ui.client.dto.*;
import com.copa.ticketing.ui.client.dto.SelloutStatusDto.SectorStatus;
import com.copa.ticketing.ui.layout.MainLayout;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.shared.Registration;
import jakarta.annotation.security.RolesAllowed;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Route(value = "admin/live", layout = MainLayout.class)
@PageTitle("Demonstração ao Vivo | Copa 2026 Admin")
@RolesAllowed("ADMIN")
public class LiveDemoView extends VerticalLayout {

    private static final Executor ASYNC = Executors.newVirtualThreadPerTaskExecutor();

    private final BackendClient client;

    // HeatWave section state
    private boolean hwTimerOn = false;
    private Registration pollRegistration;

    // HeatWave hero widgets
    private final Span hwHeroRevenue = new Span("$0");
    private final Span hwHeroSubtitle = new Span("Leitura parada. Clique em Iniciar leitura HeatWave.");
    private final Div hwHeroMeter = new Div();
    private final Button hwToggle = new Button("Iniciar leitura HeatWave");
    private final Span hwStatus = new Span("Nenhuma leitura automática em execução");

    // HeatWave KPI spans
    private final Span hwRevenue = new Span("$0");
    private final Span hwTickets = new Span("0");
    private final Span hwOccupancy = new Span("0%");
    private final Span hwPendingOrders = new Span("0");
    private final Span hwPendingAmount = new Span("$0 em aberto");
    private final Span hwConversion = new Span("0%");

    // HeatWave insight spans
    private final Span hwInsightTopMatch = new Span("Aguardando vendas");
    private final Span hwInsightTopMatchDetail = new Span("O ranking aparece após os pedidos pagos.");
    private final Span hwInsightCountry = new Span("Aguardando receita");
    private final Span hwInsightCountryDetail = new Span("Concentração por país aparece em tempo real.");
    private final Span hwInsightPending = new Span("Aguardando pedidos");
    private final Span hwInsightPendingDetail = new Span("Mostra dinheiro ainda pendente de confirmação.");

    // HeatWave bar panels
    private final Div hwMatchBars = new Div();
    private final Div hwCountryBars = new Div();
    private final Div hwSectorBars = new Div();
    private final Div hwPaymentSummary = new Div();
    private final Div hwHeatmap = new Div();

    // Sellout section state
    private int selectedMatchNumber = 68;
    private List<MatchOptionDto> matchOptions = List.of();
    private boolean selloutRunning = false;

    // Sellout widgets
    private final H3 selloutMatchTitle = new H3("Escolha um jogo para iniciar a carga real");
    private final Span selloutModeNote = new Span("verificando conexão real");
    private final ComboBox<MatchOptionDto> matchSelect = new ComboBox<>();
    private final NumberField mixReserved = new NumberField();
    private final NumberField mixPaymentPending = new NumberField();
    private final NumberField mixIssued = new NumberField();
    private final Span statusMixHint = new Span("Total: 100%. A carga vai gravar reservas, pedidos pendentes e ingressos emitidos conforme esses percentuais.");
    private final Span selloutMatchMeta = new Span("Aguardando leitura da tabela matches.");

    // Sellout KPI spans
    private final Span selloutSold = new Span("0");
    private final Span selloutSoldDelta = new Span("+0 ocupados no lote atual");
    private final Span selloutOccupancy = new Span("0%");
    private final Span selloutCapacityLabel = new Span("capacidade aguardando jogo");
    private final Span selloutOrders = new Span("0");
    private final Span selloutRevenue = new Span("$0");
    private final Span selloutBatch = new Span("0/12");
    private final Span selloutBatchLabel = new Span("aguardando início");

    // Stadium zones
    private final Div northDots = new Div();
    private final Span northLabel = new Span("0 emitidos");
    private final Div southDots = new Div();
    private final Span southLabel = new Span("0 emitidos");
    private final Div vipDots = new Div();
    private final Span vipLabel = new Span("0 emitidos");
    private final Div eastDots = new Div();
    private final Span eastLabel = new Span("0 emitidos");

    // Sellout side
    private final Div selloutProgress = new Div();
    private final Div selloutSectorBars = new Div();
    private final Div selloutFeed = new Div();

    // Buttons
    private final Button hwRefreshBtn = new Button("Ler agora");
    private final Button selloutReset = new Button("Apagar dados");
    private final Button selloutPlay = new Button("Iniciar");

    private final Div selloutSection = new Div();

    public LiveDemoView(BackendClient client) {
        this.client = client;
        setSizeFull();
        setPadding(false);
        setSpacing(false);

        add(buildHeatwaveSection(), buildSelloutSection());

        loadMatchOptionsAsync();
    }

    @Override
    protected void onAttach(AttachEvent event) {
        UI ui = event.getUI();
        ui.setPollInterval(2000);
        pollRegistration = ui.addPollListener(e -> onPoll(ui));
    }

    @Override
    protected void onDetach(DetachEvent event) {
        if (pollRegistration != null) {
            pollRegistration.remove();
            pollRegistration = null;
        }
        UI ui = event.getUI();
        ui.setPollInterval(-1);
    }

    private void onPoll(UI ui) {
        if (hwTimerOn || selloutRunning) {
            CompletableFuture.supplyAsync(() -> {
                try { return client.getLiveDashboard(); } catch (Exception ex) { return ex; }
            }, ASYNC).thenAccept(result -> {
                if (result instanceof DashboardStatusDto dash) {
                    ui.access(() -> updateHeatwaveUI(dash));
                } else if (result instanceof Exception ex) {
                    String msg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
                    ui.access(() -> hwStatus.setText("Erro HeatWave: " + msg));
                }
            });
        }
        CompletableFuture.supplyAsync(() -> {
            try { return client.getSelloutStatus(selectedMatchNumber); } catch (Exception ex) { return null; }
        }, ASYNC).thenAccept(status -> {
            if (status != null) ui.access(() -> updateSelloutUI(status));
        });
    }

    // ---- HeatWave section ----

    private Div buildHeatwaveSection() {
        Div section = new Div();
        section.addClassName("bi-dashboard");

        section.add(buildHero(), buildExplainer(), buildKpiStrip(), buildInsights(), buildBiLayout());
        return section;
    }

    private Div buildHero() {
        Div hero = new Div();
        hero.addClassName("bi-hero");

        Div copy = new Div();
        copy.addClassName("bi-copy");
        Span eyebrow = new Span("Cluster analítico em tempo real");
        eyebrow.addClassName("bi-eyebrow");
        H3 heading = new H3("Vendas, demanda e ocupação em uma leitura executiva");
        Paragraph desc = new Paragraph(
                "Esta visão mostra o comportamento da compra enquanto as inserções continuam acontecendo. " +
                "A leitura é orientada para negócio: onde a receita está concentrada, quais jogos estão " +
                "puxando demanda, quanto dinheiro ainda está pendente e quais áreas do estádio estão aquecendo.");
        copy.add(eyebrow, heading, desc);

        Div card = new Div();
        card.addClassName("bi-live-card");
        Span lbl = new Span("Receita paga");
        hwHeroRevenue.addClassName("bi-hero-revenue");
        Div meter = new Div();
        meter.addClassName("bi-live-meter");
        meter.add(hwHeroMeter);

        hwToggle.addClassName("dash-button");
        hwToggle.addClassName("primary");
        hwToggle.addClickListener(e -> toggleHwTimer());

        hwRefreshBtn.addClassName("dash-button");
        hwRefreshBtn.addClickListener(e -> readHeatwaveNow());

        Button scrollBtn = new Button("Ir para carga real");
        scrollBtn.addClassName("dash-button");
        scrollBtn.addClickListener(e -> selloutSection.scrollIntoView());

        Div actions = new Div(hwToggle, hwRefreshBtn, scrollBtn);
        actions.addClassName("bi-actions");
        hwStatus.addClassName("analytics-status");

        card.add(lbl, hwHeroRevenue, hwHeroSubtitle, meter, actions, hwStatus);
        hero.add(copy, card);
        return hero;
    }

    private Div buildExplainer() {
        Div explainer = new Div();
        explainer.addClassName("heatwave-explainer");

        explainer.add(
                explainerCard("Compra no MySQL transacional",
                        "Reservas, pedidos, pagamentos, assentos e tickets são gravados no MySQL com transações reais. Esse é o caminho operacional da compra."),
                explainerCard("Analítico no cluster HeatWave",
                        "A leitura executiva fica no cluster analítico. O painel consulta receita, ocupação, funil e mapa de calor sem colocar esse peso no fluxo de venda."),
                explainerCard("Demonstração ao vivo",
                        "Enquanto a carga insere compras, o painel pode atualizar os indicadores pelo HeatWave. Assim a história mostra OLTP e análises trabalhando juntos.")
        );
        return explainer;
    }

    private Div explainerCard(String title, String text) {
        Div card = new Div();
        card.addClassName("heatwave-explainer-card");
        card.add(new H4(title), new Paragraph(text));
        return card;
    }

    private Div buildKpiStrip() {
        Div strip = new Div();
        strip.addClassName("bi-kpi-strip");
        strip.add(
                biKpi("Receita paga", hwRevenue, "pagamentos confirmados", true),
                biKpi("Tickets emitidos", hwTickets, "assentos vendidos", false),
                biKpi("Ocupação total", hwOccupancy, "reservado + vendido", false),
                biKpi("Pedidos pendentes", hwPendingOrders, null, false),
                biKpi("Conversão", hwConversion, "reserva para compra", false),
                biKpi("Cluster analítico", new Span("HeatWave"), "leituras executivas separadas", false)
        );
        hwPendingOrders.getElement().appendChild(new Paragraph().getElement());
        strip.getChildren()
                .filter(c -> c instanceof Div)
                .map(c -> (Div) c)
                .filter(c -> c.getChildren().anyMatch(ch -> ch == hwPendingOrders))
                .findFirst()
                .ifPresent(card -> card.add(hwPendingAmount));
        return strip;
    }

    private Div biKpi(String label, Span value, String subtext, boolean primary) {
        Div kpi = new Div();
        kpi.addClassName("bi-kpi");
        if (primary) kpi.addClassName("primary");
        Span lbl = new Span(label);
        kpi.add(lbl, value);
        if (subtext != null) kpi.add(new Span(subtext));
        return kpi;
    }

    private Div buildInsights() {
        Div insights = new Div();
        insights.addClassName("bi-insights");
        insights.add(
                insightCard("Jogo líder", hwInsightTopMatch, hwInsightTopMatchDetail),
                insightCard("Mercado sede", hwInsightCountry, hwInsightCountryDetail),
                insightCard("Demanda aberta", hwInsightPending, hwInsightPendingDetail)
        );
        return insights;
    }

    private Div insightCard(String label, Span strong, Span detail) {
        Div card = new Div();
        card.addClassName("bi-insight");
        Span lbl = new Span(label);
        card.add(lbl, strong, detail);
        return card;
    }

    private Div buildBiLayout() {
        Div layout = new Div();
        layout.addClassName("bi-layout");

        hwMatchBars.addClassName("bi-bars-container");
        hwCountryBars.addClassName("bi-bars-container");
        hwSectorBars.addClassName("bi-bars-container");
        hwPaymentSummary.addClassName("bi-bars-container");
        hwHeatmap.addClassName("mini-heatmap");

        layout.add(
                biPanel("Principais jogos por receita", "Ranking executivo para entender onde a venda está performando melhor.", hwMatchBars),
                biPanel("Receita por país sede", "Mostra concentração comercial entre Canadá, México e Estados Unidos.", hwCountryBars),
                biPanel("Demanda por setor", "Ajuda a enxergar setores que aquecem primeiro e setores ainda disponíveis.", hwSectorBars),
                biPanel("Status de pagamento", "Leitura de receita confirmada contra dinheiro ainda pendente na etapa de pagamento.", hwPaymentSummary),
                biPanel("Mapa de calor dos blocos", "Amostra compacta por faixa de demanda.", hwHeatmap)
        );
        return layout;
    }

    private Div biPanel(String title, String subtitle, Div content) {
        Div panel = new Div();
        panel.addClassName("bi-panel");
        panel.add(new H3(title), new Paragraph(subtitle), content);
        return panel;
    }

    // ---- Sellout section ----

    private Div buildSelloutSection() {
        selloutSection.addClassName("live-load-stage");
        selloutSection.setId("sellout");

        Div header = new Div();
        header.addClassName("live-stage-header");
        Div headerLeft = new Div();
        Span eyebrow = new Span("Carga real no MySQL");
        eyebrow.addClassName("bi-eyebrow");
        Paragraph desc = new Paragraph(
                "A parte de cima mostra a leitura analítica no HeatWave. A parte de baixo executa a carga real: " +
                "seleciona jogo, apaga os dados daquele teste, grava reservas, pedidos, pagamentos e ingressos, " +
                "e atualiza o mapa de assentos conforme as confirmações entram.");
        headerLeft.add(eyebrow, new H3("Escolha o jogo e veja a ocupação crescer junto com o painel"), desc);

        Div routeCard = new Div();
        routeCard.addClassName("live-route-card");
        Span routeTitle = new Span("Fluxo da demonstração");
        routeTitle.getStyle().set("font-weight", "700").set("color", "var(--copa-blue)");
        routeCard.add(routeTitle,
                new Span("Vaadin → BackendClient → Helidon → MySQL → painel HeatWave"));

        header.add(headerLeft, routeCard);

        Div sellout = new Div();
        sellout.addClassName("sellout");
        sellout.add(buildDashboardTop(), buildSelector(), buildKpiGrid(), buildSelloutLayout());

        selloutSection.add(header, sellout);
        return selloutSection;
    }

    private Div buildDashboardTop() {
        Div top = new Div();
        top.addClassName("dashboard-top");

        Div left = new Div();
        left.add(selloutMatchTitle, new Paragraph(
                "Esta etapa não tem modo visual simulado. Ela só funciona com o backend Helidon, " +
                "chamando uma API que executa inserções reais no MySQL e atualiza os assentos conforme as confirmações acontecem."));

        selloutReset.addClassName("dash-button");
        selloutReset.addClickListener(e -> doReset());

        selloutPlay.addClassName("dash-button");
        selloutPlay.addClassName("primary");
        selloutPlay.addClickListener(e -> doStartStop());

        Div actions = new Div(selloutReset, selloutPlay);
        actions.addClassName("dashboard-actions");

        top.add(left, actions);
        return top;
    }

    private Div buildSelector() {
        Div sel = new Div();
        sel.addClassName("sellout-selector");

        Label lbl = new Label("Jogo da primeira fase");
        matchSelect.setPlaceholder("Digite seleções: Brasil, Marrocos, Gana, Panamá...");
        matchSelect.setItemLabelGenerator(m -> m.homeTeam() + " x " + m.awayTeam() + " — " + m.venueName() + ", " + m.city());
        matchSelect.setWidthFull();
        matchSelect.addValueChangeListener(e -> {
            MatchOptionDto m = e.getValue();
            if (m != null) {
                selectedMatchNumber = Integer.parseInt(m.matchNumber());
                selloutMatchTitle.setText(m.homeTeam() + " x " + m.awayTeam());
                selloutMatchMeta.setText(m.venueName() + ", " + m.city() + " · capacidade " + m.capacity());
                selloutCapacityLabel.setText("capacidade " + m.capacity());
            }
        });

        Span hint = new Span("Digite uma ou duas seleções para filtrar os jogos.");
        hint.addClassName("sellout-search-hint");

        // Status mix
        mixReserved.setLabel("RESERVED (%)");
        mixReserved.setValue(25.0);
        mixReserved.setMin(0);
        mixReserved.setMax(100);

        mixPaymentPending.setLabel("PAYMENT_PENDING (%)");
        mixPaymentPending.setValue(50.0);
        mixPaymentPending.setMin(0);
        mixPaymentPending.setMax(100);

        mixIssued.setLabel("ISSUED (%)");
        mixIssued.setValue(25.0);
        mixIssued.setMin(0);
        mixIssued.setMax(100);

        Div mixRow = new Div(mixReserved, mixPaymentPending, mixIssued);
        mixRow.addClassName("status-mix");

        statusMixHint.addClassName("sellout-search-hint");

        Div explainer = new Div();
        explainer.addClassName("status-mix-explainer");
        explainer.add(
                statusNote("RESERVED", "Segura assento e estoque por tempo limitado, sem pedido criado."),
                statusNote("PAYMENT_PENDING", "Cria pedido e pagamento pendente; o assento continua reservado."),
                statusNote("ISSUED", "Confirma pagamento, move estoque para vendido e emite ticket digital.")
        );

        selloutMatchMeta.addClassName("sellout-selected");

        sel.add(lbl, matchSelect, hint, mixRow, statusMixHint, explainer, selloutMatchMeta);
        return sel;
    }

    private Div statusNote(String status, String text) {
        Div note = new Div();
        note.addClassName("status-note");
        Span statusLabel = new Span(status);
        statusLabel.getStyle().set("font-weight", "700").set("display", "block");
        note.add(statusLabel, new Span(text));
        return note;
    }

    private Div buildKpiGrid() {
        Div grid = new Div();
        grid.addClassName("kpi-grid");

        grid.add(
                selloutKpi("Tickets emitidos", selloutSold, selloutSoldDelta),
                selloutKpi("Ocupação do estádio", selloutOccupancy, selloutCapacityLabel),
                selloutKpi("Pedidos pagos", selloutOrders, new Span("média 4 assentos por pedido")),
                selloutKpi("Receita real da carga", selloutRevenue, new Span("pagamentos CARD / DIGITAL_WALLET")),
                selloutKpi("Lote atual", selloutBatch, selloutBatchLabel),
                selloutKpi("Tempo da carga real", new Span("ao vivo"), new Span("varia por capacidade e rede"))
        );
        return grid;
    }

    private Div selloutKpi(String label, Span value, Span sub) {
        Div kpi = new Div();
        kpi.addClassName("kpi");
        Span lbl = new Span(label);
        kpi.add(lbl, value, sub);
        return kpi;
    }

    private Div buildSelloutLayout() {
        Div layout = new Div();
        layout.addClassName("sellout-layout");

        // Stadium visual
        Div diagram = new Div();
        diagram.addClassName("diagram");

        Div stadium = new Div();
        stadium.addClassName("stadium-visual");

        Div pitch = new Div("Campo");
        pitch.addClassName("pitch");

        stadium.add(pitch,
                buildZone("NORTH", "zone-north", "#0f766e", northLabel, northDots, 28),
                buildZone("SOUTH", "zone-south", "#1d4ed8", southLabel, southDots, 28),
                buildZone("VIP", "zone-vip", "#b45309", vipLabel, vipDots, 8),
                buildZone("EAST", "zone-east", "#7c3aed", eastLabel, eastDots, 14)
        );

        Div legend = new Div();
        legend.addClassName("seat-legend");
        legend.add(
                legendItem("Livre", ""),
                legendItem("Reservado ou pagamento pendente", "pending"),
                legendItem("Emitido NORTH", "sold-north"),
                legendItem("Emitido SOUTH", "sold-south"),
                legendItem("Emitido EAST", "sold-east"),
                legendItem("Emitido VIP", "sold-vip")
        );

        diagram.add(stadium, legend);

        // Side panel
        Div side = new Div();
        side.addClassName("sellout-side");

        Div progressPanel = new Div();
        progressPanel.addClassName("sellout-panel");
        Div progressBar = new Div();
        progressBar.addClassName("sellout-progress");
        progressBar.add(selloutProgress);
        selloutSectorBars.addClassName("bi-bars-container");
        progressPanel.add(new H3("Progresso geral"), progressBar, selloutSectorBars);

        Div feedPanel = new Div();
        feedPanel.addClassName("sellout-panel");
        feedPanel.add(new H3("Operação gravada por lote"), selloutFeed);
        selloutFeed.addClassName("batch-feed");

        Div callout = new Div();
        callout.addClassNames("callout", "amber");
        Span calloutTitle = new Span("O que isso representa");
        calloutTitle.getStyle().set("font-weight", "700").set("display", "block");
        Span detail = new Span(
                "Cada avanço visual corresponde a confirmações reais no MySQL. RESERVED grava cliente, reserva, " +
                "item e assento ocupado; PAYMENT_PENDING também grava pedido e pagamento pendente; " +
                "ISSUED confirma pagamento e grava ingressos digitais.");
        callout.add(calloutTitle, detail);

        side.add(progressPanel, feedPanel, callout);

        layout.add(diagram, side);
        return layout;
    }

    private Div buildZone(String name, String cssClass, String color, Span label, Div dots, int cols) {
        Div zone = new Div();
        zone.addClassName("seat-zone");
        zone.addClassName(cssClass);
        zone.getStyle().set("--sector-color", color);

        Span sectorName = new Span(name);
        sectorName.addClassName("sector-name");
        label.addClassName("sector-summary");

        Header header = new Header(sectorName, label);
        dots.addClassName("seat-dots");
        dots.getStyle().set("--cols", String.valueOf(cols));

        zone.add(header, dots);
        return zone;
    }

    private Span legendItem(String text, String extraClass) {
        Span item = new Span();
        Span dot = new Span();
        dot.addClassName("legend-dot");
        if (!extraClass.isBlank()) dot.addClassName(extraClass);
        Span lbl = new Span(text);
        item.add(dot, lbl);
        return item;
    }

    // ---- Data loading & UI update ----

    private void loadMatchOptionsAsync() {
        UI ui = UI.getCurrent();
        CompletableFuture.supplyAsync(() -> {
            try { return client.getMatchOptions(); } catch (Exception e) { return List.<MatchOptionDto>of(); }
        }, ASYNC).thenAccept(options -> {
            if (ui != null) {
                ui.access(() -> {
                    matchOptions = options;
                    matchSelect.setItems(options);
                    if (!options.isEmpty()) {
                        matchSelect.setValue(options.get(0));
                    }
                    selloutModeNote.setText("conectado ao backend");
                });
            }
        });
    }

    private void toggleHwTimer() {
        hwTimerOn = !hwTimerOn;
        if (hwTimerOn) {
            hwToggle.setText("Parar leitura HeatWave");
            hwStatus.setText("Leitura automática a cada 2s");
            readHeatwaveNow();
        } else {
            hwToggle.setText("Iniciar leitura HeatWave");
            hwStatus.setText("Nenhuma leitura automática em execução");
        }
    }

    private void readHeatwaveNow() {
        UI ui = UI.getCurrent();
        CompletableFuture.supplyAsync(() -> {
            try { return client.getLiveDashboard(); } catch (Exception e) { return null; }
        }, ASYNC).thenAccept(dash -> {
            if (dash != null && ui != null) ui.access(() -> updateHeatwaveUI(dash));
        });
    }

    private void updateHeatwaveUI(DashboardStatusDto dash) {
        DashboardStatusDto.Summary sum = dash.summary();
        if (sum == null) return;

        hwHeroRevenue.setText(formatMoney(sum.revenue()));
        hwHeroSubtitle.setText(sum.tickets() + " ingressos · " + String.format("%.1f%%", sum.occupancyPercent()) + " ocupação");

        double maxRev = dash.matches().stream().mapToDouble(DashboardStatusDto.MatchBar::value).max().orElse(1);
        hwHeroMeter.getStyle().set("width", String.format("%.0f%%", Math.min(100, sum.occupancyPercent())));

        hwRevenue.setText(formatMoney(sum.revenue()));
        hwTickets.setText(String.valueOf(sum.tickets()));
        hwOccupancy.setText(String.format("%.1f%%", sum.occupancyPercent()));
        hwPendingOrders.setText(String.valueOf(sum.activeReservations()));
        hwPendingAmount.setText(formatMoney(sum.pendingAmount()) + " em aberto");
        hwConversion.setText(String.format("%.1f%%", sum.conversionPercent()));

        if (!dash.matches().isEmpty()) {
            DashboardStatusDto.MatchBar top = dash.matches().get(0);
            hwInsightTopMatch.setText(top.label());
            hwInsightTopMatchDetail.setText(top.tickets() + " ingressos · " + formatMoney(top.value()));
        }
        if (!dash.countries().isEmpty()) {
            DashboardStatusDto.LabelValue topC = dash.countries().get(0);
            hwInsightCountry.setText(topC.label());
            hwInsightCountryDetail.setText(formatMoney(topC.value()));
        }
        hwInsightPending.setText(formatMoney(sum.pendingAmount()));
        hwInsightPendingDetail.setText(sum.activeReservations() + " reservas em aberto");

        renderBars(hwMatchBars, dash.matches().stream()
                .map(m -> new BarEntry(m.label(), m.value())).toList(), maxRev);
        double maxC = dash.countries().stream().mapToDouble(DashboardStatusDto.LabelValue::value).max().orElse(1);
        renderBars(hwCountryBars, dash.countries().stream()
                .map(c -> new BarEntry(c.label(), c.value())).toList(), maxC);
        double maxS = dash.sectors().stream().mapToDouble(DashboardStatusDto.LabelValue::value).max().orElse(1);
        renderBars(hwSectorBars, dash.sectors().stream()
                .map(s -> new BarEntry(s.label(), s.value())).toList(), maxS);
        double maxP = dash.payments().stream().mapToDouble(DashboardStatusDto.LabelValue::value).max().orElse(1);
        renderBars(hwPaymentSummary, dash.payments().stream()
                .map(p -> new BarEntry(p.label(), p.value())).toList(), maxP);
        renderHeatmap(hwHeatmap, dash.heatBlocks());
    }

    private void renderBars(Div container, List<BarEntry> entries, double max) {
        container.removeAll();
        for (BarEntry entry : entries) {
            double pct = max > 0 ? Math.min(100, entry.value() / max * 100) : 0;
            Div row = new Div();
            row.addClassName("bi-bar-row");
            Span label = new Span(entry.label());
            label.addClassName("bi-bar-label");
            Div track = new Div();
            track.addClassName("bi-bar-track");
            Div fill = new Div();
            fill.addClassName("bi-bar-fill");
            fill.getStyle().set("width", String.format(Locale.US, "%.1f%%", pct));
            track.add(fill);
            Span val = new Span(formatMoney(entry.value()));
            val.addClassName("bi-bar-value");
            row.add(label, track, val);
            container.add(row);
        }
    }

    private void renderHeatmap(Div container, List<DashboardStatusDto.HeatBlockItem> blocks) {
        container.removeAll();
        for (DashboardStatusDto.HeatBlockItem block : blocks) {
            Div cell = new Div();
            cell.addClassName("heat-cell");
            int heat = (int) Math.min(100, block.heat());
            cell.getStyle().set("--heat", String.valueOf(heat));
            Span lbl = new Span(block.label());
            Span val = new Span(heat + "%");
            cell.add(lbl, val);
            container.add(cell);
        }
    }

    private void updateSelloutUI(SelloutStatusDto status) {
        boolean wasRunning = selloutRunning;
        selloutRunning = status.running();

        selloutPlay.setText(selloutRunning ? "Pausar" : "Iniciar");
        selloutModeNote.setText(selloutRunning ? "carga rodando" : "pronto");

        if (status.totals() != null) {
            long sold = status.totals().sold();
            long total = status.totals().total();
            long reserved = status.totals().reserved();

            selloutSold.setText(String.valueOf(sold));
            double occ = total > 0 ? (double) (sold + reserved) / total * 100 : 0;
            selloutOccupancy.setText(String.format("%.1f%%", occ));
            selloutOrders.setText(String.valueOf(status.paidOrders()));
            selloutRevenue.setText(formatMoney(status.revenue()));

            selloutProgress.getStyle().set("width", String.format(Locale.US, "%.1f%%", status.progressPercent()));
        }

        if (status.statusMix() != null) {
            renderSectorBars(selloutSectorBars, status);
        }

        // Update sector dots
        if (status.sectors() != null) {
            for (SectorStatus s : status.sectors()) {
                switch (s.sectorCode()) {
                    case "NORTH" -> updateZoneDots(northDots, northLabel, s, "sold-north");
                    case "SOUTH" -> updateZoneDots(southDots, southLabel, s, "sold-south");
                    case "VIP" -> updateZoneDots(vipDots, vipLabel, s, "sold-vip");
                    case "EAST" -> updateZoneDots(eastDots, eastLabel, s, "sold-east");
                }
            }
        }

        // Feed events
        if (status.events() != null && !status.events().isEmpty()) {
            selloutFeed.removeAll();
            for (SelloutEventDto ev : status.events()) {
                Div entry = new Div();
                entry.addClassName("batch-entry");
                Span ts = new Span(ev.at() != null ? ev.at().substring(11, 19) : "");
                ts.addClassName("batch-ts");
                Span msg = new Span(ev.message());
                entry.add(ts, msg);
                selloutFeed.add(entry);
            }
        }

        if (!wasRunning && selloutRunning && !hwTimerOn) {
            readHeatwaveNow();
        }
    }

    private void updateZoneDots(Div dotsContainer, Span label, SectorStatus sector, String soldClass) {
        label.setText(sector.soldQuantity() + " emitidos");
        dotsContainer.removeAll();

        long total = sector.totalQuantity();
        long reserved = sector.reservedQuantity();
        long sold = sector.soldQuantity();
        long available = sector.availableQuantity();

        int MAX_DOTS = 80;
        if (total == 0) return;

        int dotCount = (int) Math.min(MAX_DOTS, total);
        double scale = (double) dotCount / total;

        int soldDots = (int) Math.round(sold * scale);
        int reservedDots = (int) Math.round(reserved * scale);
        int freeDots = dotCount - soldDots - reservedDots;

        for (int i = 0; i < soldDots; i++) {
            Span dot = new Span();
            dot.addClassName("seat-dot");
            dot.addClassName(soldClass);
            dotsContainer.add(dot);
        }
        for (int i = 0; i < reservedDots; i++) {
            Span dot = new Span();
            dot.addClassName("seat-dot");
            dot.addClassName("pending");
            dotsContainer.add(dot);
        }
        for (int i = 0; i < freeDots; i++) {
            Span dot = new Span();
            dot.addClassName("seat-dot");
            dotsContainer.add(dot);
        }
    }

    private void renderSectorBars(Div container, SelloutStatusDto status) {
        container.removeAll();
        if (status.sectors() == null) return;
        for (SectorStatus s : status.sectors()) {
            double pct = s.totalQuantity() > 0
                    ? (double) (s.soldQuantity() + s.reservedQuantity()) / s.totalQuantity() * 100 : 0;
            Div row = new Div();
            row.addClassName("bi-bar-row");
            Span lbl = new Span(s.sectorCode());
            lbl.addClassName("bi-bar-label");
            Div track = new Div();
            track.addClassName("bi-bar-track");
            Div fill = new Div();
            fill.addClassName("bi-bar-fill");
            fill.getStyle().set("width", String.format(Locale.US, "%.1f%%", Math.min(100, pct)));
            track.add(fill);
            Span val = new Span(String.format("%.0f%%", pct));
            val.addClassName("bi-bar-value");
            row.add(lbl, track, val);
            container.add(row);
        }
    }

    private void doReset() {
        selloutPlay.setEnabled(false);
        selloutReset.setEnabled(false);
        UI ui = UI.getCurrent();
        CompletableFuture.runAsync(() -> {
            try {
                client.resetSellout(selectedMatchNumber);
            } catch (Exception e) {
                if (ui != null) ui.access(() -> showError("Erro ao apagar dados: " + e.getMessage()));
            }
        }, ASYNC).thenRun(() -> {
            if (ui != null) ui.access(() -> {
                selloutPlay.setEnabled(true);
                selloutReset.setEnabled(true);
                showInfo("Dados apagados. Estoque liberado para nova carga.");
            });
        });
    }

    private void doStartStop() {
        if (selloutRunning) {
            showInfo("Aguarde a carga concluir naturalmente ou reinicie o backend para interromper.");
            return;
        }
        double res = nvl(mixReserved.getValue());
        double pend = nvl(mixPaymentPending.getValue());
        double issued = nvl(mixIssued.getValue());
        if (Math.round((res + pend + issued) * 100) != 10000) {
            showError("A soma dos percentuais de status deve ser 100%.");
            return;
        }

        SelloutStartRequest req = SelloutStartRequest.of(selectedMatchNumber, res, pend, issued);
        selloutPlay.setEnabled(false);
        UI ui = UI.getCurrent();
        CompletableFuture.runAsync(() -> {
            try {
                client.startSellout(req);
            } catch (Exception e) {
                if (ui != null) ui.access(() -> {
                    showError("Erro ao iniciar carga: " + e.getMessage());
                    selloutPlay.setEnabled(true);
                });
                return;
            }
            if (ui != null) ui.access(() -> selloutPlay.setEnabled(true));
        }, ASYNC);
    }

    private static double nvl(Double v) { return v != null ? v : 0.0; }

    private static String formatMoney(double value) {
        return String.format(Locale.US, "$%,.0f", value);
    }

    private void showError(String msg) {
        Notification n = Notification.show(msg, 5000, Notification.Position.TOP_END);
        n.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    private void showInfo(String msg) {
        Notification.show(msg, 4000, Notification.Position.TOP_END);
    }

    private record BarEntry(String label, double value) {}
}
