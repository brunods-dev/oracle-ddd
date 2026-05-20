package com.copa.ticketing.ui.views.catalog;

import com.copa.ticketing.ui.client.BackendClient;
import com.copa.ticketing.ui.client.dto.MatchDto;
import com.copa.ticketing.ui.client.dto.MatchRecommendationDto;
import com.copa.ticketing.ui.client.dto.PagedResponse;
import com.copa.ticketing.ui.layout.MainLayout;
import com.copa.ticketing.ui.util.FlagEmoji;
import com.copa.ticketing.ui.views.match.MatchDetailView;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.CallbackDataProvider;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParam;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Route(value = "", layout = MainLayout.class)
@PageTitle("Partidas | Copa 2026")
@AnonymousAllowed
public class CatalogView extends VerticalLayout {

    private final BackendClient client;

    private final TextField teamFilter = new TextField();
    private final TextField cityFilter = new TextField();
    private final TextField dateFilter = new TextField();
    private final Grid<MatchDto> grid = new Grid<>();

    private final MultiSelectComboBox<String> teamSelect = new MultiSelectComboBox<>();
    private final MultiSelectComboBox<String> citySelect = new MultiSelectComboBox<>();
    private final Button recommendBtn = new Button("Recomendar com IA");
    private final Div recommendPanel = new Div();

    public CatalogView(BackendClient client) {
        this.client = client;
        setSizeFull();
        setPadding(true);
        setSpacing(false);
        getStyle().set("background", "var(--copa-surface)");

        add(buildHeader(), buildFilters(), buildRecommendPanel(), buildGrid());
    }

    private HorizontalLayout buildHeader() {
        H2 title = new H2("⚽ Partidas da Copa do Mundo 2026");
        title.addClassName("page-title");
        title.getStyle().set("margin-bottom", "4px");

        Paragraph sub = new Paragraph("Selecione uma partida para comprar seus ingressos");
        sub.addClassName("page-subtitle");

        VerticalLayout left = new VerticalLayout(title, sub);
        left.setPadding(false);
        left.setSpacing(false);

        HorizontalLayout header = new HorizontalLayout(left);
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.getStyle().set("margin-bottom", "var(--lumo-space-m)");
        return header;
    }

    private HorizontalLayout buildFilters() {
        teamFilter.setPlaceholder("Filtrar por seleção...");
        teamFilter.setPrefixComponent(VaadinIcon.SEARCH.create());
        teamFilter.setClearButtonVisible(true);
        teamFilter.setWidth("220px");
        teamFilter.addValueChangeListener(e -> grid.getDataProvider().refreshAll());

        cityFilter.setPlaceholder("Cidade...");
        cityFilter.setPrefixComponent(VaadinIcon.MAP_MARKER.create());
        cityFilter.setClearButtonVisible(true);
        cityFilter.setWidth("180px");
        cityFilter.addValueChangeListener(e -> grid.getDataProvider().refreshAll());

        dateFilter.setPlaceholder("Data (AAAA-MM-DD)...");
        dateFilter.setPrefixComponent(VaadinIcon.CALENDAR.create());
        dateFilter.setClearButtonVisible(true);
        dateFilter.setWidth("210px");
        dateFilter.addValueChangeListener(e -> grid.getDataProvider().refreshAll());

        Button clear = new Button("Limpar", VaadinIcon.CLOSE.create());
        clear.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        clear.addClickListener(e -> {
            teamFilter.clear();
            cityFilter.clear();
            dateFilter.clear();
        });

        HorizontalLayout filters = new HorizontalLayout(teamFilter, cityFilter, dateFilter, clear);
        filters.setAlignItems(FlexComponent.Alignment.END);
        filters.getStyle()
               .set("background", "var(--lumo-base-color)")
               .set("padding", "var(--lumo-space-m)")
               .set("border-radius", "var(--lumo-border-radius-l)")
               .set("box-shadow", "var(--lumo-box-shadow-s)")
               .set("margin-bottom", "var(--lumo-space-m)");
        return filters;
    }

    private VerticalLayout buildRecommendPanel() {
        teamSelect.setPlaceholder("Times favoritos...");
        teamSelect.setWidth("300px");
        teamSelect.setItems(List.of(
                "ALG", "ARG", "AUS", "AUT", "BEL", "BIH", "BRA", "CAN", "CIV", "COD",
                "COL", "CPV", "CRO", "CUW", "CZE", "ECU", "EGY", "ENG", "ESP", "FRA",
                "GER", "GHA", "HAI", "IRN", "IRQ", "JOR", "JPN", "KOR", "KSA", "MAR",
                "MEX", "NED", "NOR", "NZL", "PAN", "PAR", "POR", "QAT", "RSA", "SCO",
                "SEN", "SUI", "SWE", "TUN", "TUR", "URU", "USA", "UZB"
        ));
        teamSelect.setItemLabelGenerator(code -> FlagEmoji.of(code) + " " + code);

        citySelect.setPlaceholder("Cidades mais próximas de mim...");
        citySelect.setWidth("300px");
        citySelect.setItems(List.of(
                "Atlanta", "Boston", "Dallas", "Guadalajara", "Houston", "Kansas City",
                "Los Angeles", "Mexico City", "Miami", "Monterrey", "New York",
                "Philadelphia", "San Francisco", "Seattle", "Toronto", "Vancouver"
        ));
        citySelect.setItemLabelGenerator(city -> "📍 " + city);

        recommendBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        recommendBtn.setPrefixComponent(VaadinIcon.STAR.create());
        recommendBtn.addClickListener(e -> loadRecommendations());

        HorizontalLayout controls = new HorizontalLayout(teamSelect, citySelect, recommendBtn);
        controls.setAlignItems(FlexComponent.Alignment.END);
        controls.getStyle().set("flex-wrap", "wrap");

        Span label = new Span("✨ Recomendação IA — selecione seus times e cidades:");
        label.getStyle()
             .set("font-size", "var(--lumo-font-size-s)")
             .set("color", "var(--lumo-secondary-text-color)")
             .set("font-weight", "600");

        recommendPanel.setVisible(false);
        recommendPanel.getStyle()
                      .set("display", "flex")
                      .set("flex-wrap", "wrap")
                      .set("gap", "var(--lumo-space-m)")
                      .set("margin-top", "var(--lumo-space-m)");

        VerticalLayout wrapper = new VerticalLayout(label, controls, recommendPanel);
        wrapper.setPadding(true);
        wrapper.setSpacing(false);
        wrapper.getStyle()
               .set("background", "var(--lumo-base-color)")
               .set("border-radius", "var(--lumo-border-radius-l)")
               .set("box-shadow", "var(--lumo-box-shadow-s)")
               .set("margin-bottom", "var(--lumo-space-m)");
        return wrapper;
    }

    private void loadRecommendations() {
        List<String> teams = new java.util.ArrayList<>(teamSelect.getValue());
        List<String> cities = new java.util.ArrayList<>(citySelect.getValue());

        if (teams.isEmpty() && cities.isEmpty()) {
            Notification.show("Selecione ao menos um time ou cidade.", 3000, Notification.Position.TOP_END);
            return;
        }

        Icon spinnerIcon = VaadinIcon.REFRESH.create();
        spinnerIcon.addClassName("ai-loading-spinner");
        recommendBtn.setEnabled(false);
        recommendBtn.setText("Consultando IA...");
        recommendBtn.setPrefixComponent(spinnerIcon);
        recommendPanel.setVisible(false);
        recommendPanel.removeAll();

        UI ui = UI.getCurrent();
        CompletableFuture.supplyAsync(() -> client.getRecommendations(teams, cities))
                .thenAccept(recs -> ui.access(() -> {
                    recommendBtn.setEnabled(true);
                    recommendBtn.setText("Recomendar com IA");
                    recommendBtn.setPrefixComponent(VaadinIcon.STAR.create());
                    if (recs == null || recs.isEmpty()) {
                        Notification.show("Sem recomendações para essa seleção.", 4000, Notification.Position.TOP_END);
                        return;
                    }
                    for (MatchRecommendationDto rec : recs) {
                        recommendPanel.add(buildRecommendCard(rec));
                    }
                    recommendPanel.setVisible(true);
                }))
                .exceptionally(ex -> {
                    ui.access(() -> {
                        recommendBtn.setEnabled(true);
                        recommendBtn.setText("Recomendar com IA");
                        recommendBtn.setPrefixComponent(VaadinIcon.STAR.create());
                        Notification n = Notification.show(
                                "Erro ao consultar IA: " + ex.getCause().getMessage(),
                                5000, Notification.Position.TOP_END);
                        n.addThemeVariants(NotificationVariant.LUMO_ERROR);
                    });
                    return null;
                });
    }

    private Div buildRecommendCard(MatchRecommendationDto rec) {
        Span teams = new Span(
                FlagEmoji.of(rec.homeTeamCode()) + " " + rec.homeTeam()
                + " × " + rec.awayTeam() + " " + FlagEmoji.of(rec.awayTeamCode()));
        teams.getStyle().set("font-weight", "700").set("font-size", "var(--lumo-font-size-s)");

        Span venue = new Span("📍 " + rec.venueName());
        venue.getStyle().set("font-size", "var(--lumo-font-size-xs)")
             .set("color", "var(--lumo-secondary-text-color)");

        Span date = new Span("🗓 " + (rec.matchAt() != null ? formatDate(rec.matchAt()) : "A definir"));
        date.getStyle().set("font-size", "var(--lumo-font-size-xs)")
            .set("color", "var(--lumo-secondary-text-color)");

        Span reason = new Span("\"" + rec.reason() + "\"");
        reason.getStyle()
              .set("font-style", "italic")
              .set("font-size", "var(--lumo-font-size-xs)")
              .set("color", "var(--lumo-body-text-color)");

        Button ticketsBtn = new Button("Ver Ingressos", VaadinIcon.TICKET.create());
        ticketsBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        ticketsBtn.getStyle().set("margin-top", "var(--lumo-space-s)");
        ticketsBtn.addClickListener(e -> UI.getCurrent().navigate(MatchDetailView.class,
                new RouteParameters(new RouteParam("id", String.valueOf(rec.matchId())))));

        VerticalLayout card = new VerticalLayout(teams, venue, date, reason, ticketsBtn);
        card.setPadding(true);
        card.setSpacing(false);
        card.getStyle()
            .set("background", "var(--lumo-contrast-5pct)")
            .set("border-radius", "var(--lumo-border-radius-m)")
            .set("border-left", "3px solid var(--copa-gold)")
            .set("width", "280px")
            .set("gap", "2px");

        Div wrapper = new Div(card);
        wrapper.getStyle().set("display", "inline-block");
        return wrapper;
    }

    private Grid<MatchDto> buildGrid() {
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_NO_BORDER);
        grid.setSizeFull();
        grid.setSelectionMode(Grid.SelectionMode.NONE);

        grid.addColumn(new ComponentRenderer<>(match -> {
            HorizontalLayout teams = new HorizontalLayout();
            teams.setAlignItems(FlexComponent.Alignment.CENTER);
            teams.setSpacing(false);
            teams.getStyle().set("gap", "var(--lumo-space-s)");

            Span homeFlag = new Span(FlagEmoji.of(match.homeTeamCode()));
            homeFlag.addClassName("match-flag");

            VerticalLayout home = new VerticalLayout(homeFlag, new Span(match.homeTeamCode() != null ? match.homeTeamCode() : "TBD"));
            home.setPadding(false);
            home.setSpacing(false);
            home.setAlignItems(FlexComponent.Alignment.CENTER);
            home.setWidth("100px");
            ((Span) home.getComponentAt(1)).addClassName("match-team-name");

            Span vs = new Span("VS");
            vs.addClassName("match-vs");

            Span awayFlag = new Span(FlagEmoji.of(match.awayTeamCode()));
            awayFlag.addClassName("match-flag");

            VerticalLayout away = new VerticalLayout(awayFlag, new Span(match.awayTeamCode() != null ? match.awayTeamCode() : "TBD"));
            away.setPadding(false);
            away.setSpacing(false);
            away.setAlignItems(FlexComponent.Alignment.CENTER);
            away.setWidth("100px");
            ((Span) away.getComponentAt(1)).addClassName("match-team-name");

            teams.add(home, vs, away);
            return teams;
        })).setHeader("Confronto").setFlexGrow(2).setAutoWidth(false).setWidth("320px");

        grid.addColumn(new ComponentRenderer<>(match -> {
            Span stage = new Span(match.competitionStage() != null ? match.competitionStage() : "-");
            stage.addClassName("match-badge");
            if (match.groupName() != null && !match.groupName().isBlank()) {
                Span group = new Span(match.groupName());
                group.getStyle().set("margin-left", "4px").set("color", "var(--lumo-secondary-text-color)")
                     .set("font-size", "var(--lumo-font-size-xs)");
                return new HorizontalLayout(stage, group);
            }
            return stage;
        })).setHeader("Fase").setAutoWidth(true);

        grid.addColumn(new ComponentRenderer<>(match -> {
            VerticalLayout venue = new VerticalLayout();
            venue.setPadding(false);
            venue.setSpacing(false);
            Span name = new Span(match.venueName() != null ? match.venueName() : "-");
            name.getStyle().set("font-weight", "600").set("font-size", "var(--lumo-font-size-s)");
            Span city = new Span("📍 " + (match.venueCity() != null ? match.venueCity() : ""));
            city.addClassName("match-venue");
            venue.add(name, city);
            return venue;
        })).setHeader("Estádio").setFlexGrow(1).setAutoWidth(true);

        grid.addColumn(new ComponentRenderer<>(match -> {
            Span date = new Span(match.matchAt() != null ? formatDate(match.matchAt()) : "A definir");
            date.addClassName("match-date");
            return date;
        })).setHeader("Data e Hora").setAutoWidth(true);

        grid.addColumn(new ComponentRenderer<>(match -> {
            VerticalLayout occ = new VerticalLayout();
            occ.setPadding(false);
            occ.setSpacing(false);
            occ.setWidth("120px");

            Span pct = new Span(String.format("%.0f%%", match.occupancyPercent()));
            pct.getStyle().set("font-weight", "700").set("font-size", "var(--lumo-font-size-s)")
               .set("color", occupancyColor(match.occupancyPercent()));

            Div bar = new Div();
            bar.addClassName("occupancy-bar");
            Div fill = new Div();
            fill.addClassName("occupancy-fill");
            fill.getStyle().set("width", String.format("%.0f%%", Math.min(match.occupancyPercent(), 100)));
            bar.add(fill);

            Span avail = new Span(match.availableSeats() + " disponíveis");
            avail.getStyle().set("font-size", "var(--lumo-font-size-xxs)")
                 .set("color", "var(--lumo-secondary-text-color)");

            occ.add(pct, bar, avail);
            return occ;
        })).setHeader("Ocupação").setAutoWidth(true);

        grid.addColumn(new ComponentRenderer<>(match -> {
            String statusVal = match.status() != null ? match.status() : "SCHEDULED";
            Span badge = new Span(statusVal);
            badge.addClassName("status-badge");
            badge.addClassName(statusClass(statusVal));
            return badge;
        })).setHeader("Status").setAutoWidth(true);

        grid.addColumn(new ComponentRenderer<>(match -> {
            Button btn = new Button("Ver Ingressos", VaadinIcon.TICKET.create());
            btn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
            btn.addClickListener(e -> UI.getCurrent().navigate(MatchDetailView.class,
                    new RouteParameters(new RouteParam("id", String.valueOf(match.id())))));
            if (match.availableSeats() <= 0) {
                btn.setEnabled(false);
                btn.setText("Esgotado");
            }
            return btn;
        })).setHeader("").setAutoWidth(true).setFlexGrow(0);

        grid.setItems(new CallbackDataProvider<>(
            query -> {
                int page = query.getOffset() / Math.max(query.getLimit(), 1);
                int size = query.getLimit();
                try {
                    PagedResponse<MatchDto> resp = client.getMatches(page, size,
                            cityFilter.getValue(), dateFilter.getValue(), teamFilter.getValue());
                    return resp.items().stream();
                } catch (Exception e) {
                    return List.<MatchDto>of().stream();
                }
            },
            query -> {
                try {
                    PagedResponse<MatchDto> resp = client.getMatches(0, 1,
                            cityFilter.getValue(), dateFilter.getValue(), teamFilter.getValue());
                    return (int) resp.total();
                } catch (Exception e) {
                    return 0;
                }
            }
        ));

        return grid;
    }

    private String formatDate(String iso) {
        if (iso == null) return "-";
        try {
            String[] parts = iso.split("T");
            if (parts.length < 2) return iso;
            String[] dateParts = parts[0].split("-");
            String[] timeParts = parts[1].split(":");
            return dateParts[2] + "/" + dateParts[1] + "/" + dateParts[0]
                    + " " + timeParts[0] + ":" + timeParts[1];
        } catch (Exception e) {
            return iso;
        }
    }

    private String occupancyColor(double pct) {
        if (pct >= 90) return "var(--copa-red)";
        if (pct >= 60) return "var(--copa-gold)";
        return "var(--copa-green)";
    }

    private String statusClass(String status) {
        return switch (status) {
            case "SCHEDULED" -> "status-available";
            case "IN_PROGRESS" -> "status-paid";
            case "FINISHED" -> "status-cancelled";
            default -> "status-pending";
        };
    }
}
