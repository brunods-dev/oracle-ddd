package com.copa.ticketing.ui.views.match;

import com.copa.ticketing.ui.client.BackendClient;
import com.copa.ticketing.ui.client.dto.MatchDto;
import com.copa.ticketing.ui.client.dto.SectorDto;
import com.copa.ticketing.ui.layout.MainLayout;
import com.copa.ticketing.ui.util.FlagEmoji;
import com.copa.ticketing.ui.views.seatmap.SeatMapView;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import java.util.List;

@Route(value = "match/:id", layout = MainLayout.class)
@PageTitle("Detalhe da Partida | Copa 2026")
@AnonymousAllowed
public class MatchDetailView extends VerticalLayout implements BeforeEnterObserver {

    private final BackendClient client;

    public MatchDetailView(BackendClient client) {
        this.client = client;
        setSizeFull();
        setPadding(true);
        setSpacing(false);
        getStyle().set("background", "var(--copa-surface)");
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String idStr = event.getRouteParameters().get("id").orElse("0");
        try {
            long matchId = Long.parseLong(idStr);
            MatchDto match = client.getMatch(matchId);
            List<SectorDto> sectors = client.getSectors(matchId);
            buildUI(match, sectors);
        } catch (Exception e) {
            Notification.show("Erro ao carregar partida: " + e.getMessage(), 3000, Notification.Position.TOP_END)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void buildUI(MatchDto match, List<SectorDto> sectors) {
        removeAll();

        Div matchCard = buildMatchHeader(match);
        matchCard.getStyle().set("margin-bottom", "0").set("flex-shrink", "0").set("width", "320px");

        Div stadiumMap = buildStadiumMap(match, sectors);
        stadiumMap.getStyle().set("margin-bottom", "0").set("flex", "1").set("min-width", "0");

        HorizontalLayout topRow = new HorizontalLayout(matchCard, stadiumMap);
        topRow.setWidthFull();
        topRow.setPadding(false);
        topRow.setSpacing(false);
        topRow.getStyle()
            .set("gap", "var(--lumo-space-l)")
            .set("margin-bottom", "var(--lumo-space-l)")
            .set("align-items", "flex-start");

        add(topRow);
        add(buildSectorsSection(match, sectors));
    }

    private Div buildMatchHeader(MatchDto match) {
        Div card = new Div();
        card.getStyle()
            .set("background", "var(--copa-gradient)")
            .set("border-radius", "var(--lumo-border-radius-l)")
            .set("padding", "var(--lumo-space-xl)")
            .set("color", "#fff")
            .set("margin-bottom", "var(--lumo-space-l)");

        HorizontalLayout teams = new HorizontalLayout();
        teams.setAlignItems(FlexComponent.Alignment.CENTER);
        teams.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        teams.setWidthFull();
        teams.getStyle().set("gap", "var(--lumo-space-xl)");

        VerticalLayout home = teamBlock(
                FlagEmoji.of(match.homeTeamCode()),
                match.homeTeam() != null ? match.homeTeam() : "TBD",
                match.homeTeamCode());
        Span vs = new Span("VS");
        vs.getStyle().set("font-size", "2rem").set("font-weight", "900").set("opacity", "0.8");

        VerticalLayout away = teamBlock(
                FlagEmoji.of(match.awayTeamCode()),
                match.awayTeam() != null ? match.awayTeam() : "TBD",
                match.awayTeamCode());

        teams.add(home, vs, away);

        Div meta = new Div();
        meta.getStyle().set("text-align", "center").set("margin-top", "var(--lumo-space-m)").set("opacity", "0.9");

        Span venue = new Span("🏟 " + match.venueName() + " – " + match.venueCity() + ", " + match.venueCountry());
        venue.getStyle().set("display", "block").set("font-size", "var(--lumo-font-size-s)").set("margin-bottom", "4px");
        Span date = new Span("📅 " + (match.matchAt() != null ? match.matchAt().replace("T", " ").substring(0, 16) : "A definir"));
        date.getStyle().set("display", "block").set("font-size", "var(--lumo-font-size-s)");

        meta.add(venue, date);

        Button back = new Button("← Voltar às partidas");
        back.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        back.getStyle().set("color", "#fff").set("margin-bottom", "var(--lumo-space-m)");
        back.addClickListener(e -> UI.getCurrent().navigate(""));

        card.add(back, teams, meta);
        return card;
    }

    private VerticalLayout teamBlock(String flag, String name, String code) {
        Span flagSpan = new Span(flag);
        flagSpan.getStyle().set("font-size", "3rem");
        Span nameSpan = new Span(name);
        nameSpan.getStyle().set("font-weight", "700").set("font-size", "var(--lumo-font-size-l)");
        Span codeSpan = new Span(code != null ? code : "");
        codeSpan.getStyle().set("font-size", "var(--lumo-font-size-xs)").set("opacity", "0.7");
        VerticalLayout vl = new VerticalLayout(flagSpan, nameSpan, codeSpan);
        vl.setPadding(false);
        vl.setAlignItems(FlexComponent.Alignment.CENTER);
        return vl;
    }

    // ─── Stadium Map ────────────────────────────────────────────────────────────

    private Div buildStadiumMap(MatchDto match, List<SectorDto> sectors) {
        Div wrapper = new Div();
        wrapper.getStyle()
            .set("background", "var(--copa-card-bg)")
            .set("border-radius", "var(--lumo-border-radius-l)")
            .set("box-shadow", "var(--lumo-box-shadow-m)")
            .set("padding", "var(--lumo-space-l)")
            .set("border", "1px solid var(--lumo-contrast-10pct)")
            .set("margin-bottom", "var(--lumo-space-l)")
            .set("overflow-x", "auto");

        H3 mapTitle = new H3("🏟 Mapa do Estádio");
        mapTitle.getStyle()
            .set("color", "var(--copa-blue)")
            .set("font-weight", "800")
            .set("margin", "0 0 4px 0")
            .set("font-size", "var(--lumo-font-size-l)");

        Span mapSubtitle = new Span("Clique em um setor para selecionar seus assentos");
        mapSubtitle.getStyle()
            .set("font-size", "var(--lumo-font-size-xs)")
            .set("color", "var(--lumo-secondary-text-color)")
            .set("display", "block")
            .set("margin-bottom", "var(--lumo-space-m)");

        SectorDto north = findSectorByPos(sectors, "NORTH");
        SectorDto south = findSectorByPos(sectors, "SOUTH");
        SectorDto east  = findSectorByPos(sectors, "EAST");
        SectorDto west  = findSectorByPos(sectors, "WEST", "VIP", "LOUNGE");

        Div grid = new Div();
        grid.addClassName("stadium-map-grid");
        grid.getStyle()
            .set("max-width", "520px")
            .set("margin", "0 auto")
            .set("width", "100%");

        grid.add(new Div());
        grid.add(north != null ? buildSectorTab(match, north, "north") : new Div());
        grid.add(new Div());

        grid.add(west != null ? buildSectorTab(match, west, "west") : new Div());
        grid.add(buildFieldImage(false));
        grid.add(east != null ? buildSectorTab(match, east, "east") : new Div());

        grid.add(new Div());
        grid.add(south != null ? buildSectorTab(match, south, "south") : new Div());
        grid.add(new Div());

        wrapper.add(mapTitle, mapSubtitle, grid);
        return wrapper;
    }

    private Div buildSectorTab(MatchDto match, SectorDto sector, String position) {
        Div tab = new Div();
        tab.addClassName("sector-tab");
        tab.addClassName("sector-tab-" + position);

        boolean available = sector.availableQuantity() > 0;
        if (!available) tab.addClassName("sector-tab-soldout");

        String arrow = switch (position) {
            case "north" -> "↓";
            case "south" -> "↑";
            case "east"  -> "←";
            case "west"  -> "→";
            default      -> "";
        };

        Span arrowSpan = new Span(arrow);
        arrowSpan.getStyle()
            .set("font-size", "var(--lumo-font-size-xs)")
            .set("opacity", "0.5")
            .set("display", "block")
            .set("color", "var(--copa-green)");

        Span nameSpan = new Span(sector.sectorName());
        nameSpan.getStyle()
            .set("font-weight", "700")
            .set("font-size", "var(--lumo-font-size-s)")
            .set("display", "block")
            .set("line-height", "1.2")
            .set("color", "var(--copa-blue)");

        Span priceSpan = new Span(String.format("USD %.0f", sector.price()));
        priceSpan.getStyle()
            .set("font-weight", "800")
            .set("font-size", "var(--lumo-font-size-m)")
            .set("display", "block")
            .set("color", available ? "var(--copa-green)" : "var(--lumo-secondary-text-color)");

        Span availSpan = new Span(available ? sector.availableQuantity() + " disp." : "Esgotado");
        availSpan.getStyle()
            .set("font-size", "var(--lumo-font-size-xxs)")
            .set("display", "block")
            .set("color", available ? "var(--lumo-secondary-text-color)" : "var(--copa-red)");

        tab.add(arrowSpan, nameSpan, priceSpan, availSpan);

        if (available) {
            tab.getElement().addEventListener("click", e ->
                UI.getCurrent().navigate(SeatMapView.class,
                    new RouteParameters(new RouteParam("id", String.valueOf(match.id())),
                                        new RouteParam("sector", sector.sectorCode()))));
        }

        return tab;
    }

    private Div buildFieldImage(boolean mini) {
        Image img = new Image("/images/stadium.jpg", "Campo");
        img.getStyle()
            .set("width", "100%")
            .set("height", "100%")
            .set("object-fit", "cover")
            .set("display", "block");

        Div field = new Div(img);
        field.getStyle()
            .set("border-radius", mini ? "8px" : "12px")
            .set("overflow", "hidden")
            .set("box-shadow", mini ? "0 2px 12px rgba(0,40,104,0.15)" : "0 4px 20px rgba(0,40,104,0.18)")
            .set("border", mini ? "2px solid #fff" : "3px solid #fff")
            .set("aspect-ratio", "16/10")
            .set("min-width", mini ? "120px" : "200px")
            .set("width", "100%");
        return field;
    }

    private SectorDto findSectorByPos(List<SectorDto> sectors, String... keywords) {
        for (SectorDto s : sectors) {
            String code = s.sectorCode() != null ? s.sectorCode().toUpperCase() : "";
            String name = s.sectorName() != null ? s.sectorName().toUpperCase() : "";
            for (String kw : keywords) {
                if (code.contains(kw) || name.contains(kw)) return s;
            }
        }
        return null;
    }

    // ─── Sectors Section ────────────────────────────────────────────────────────

    private VerticalLayout buildSectorsSection(MatchDto match, List<SectorDto> sectors) {
        H3 title = new H3("Setores Disponíveis");
        title.getStyle().set("color", "var(--copa-blue)").set("font-weight", "800")
             .set("margin", "0 0 var(--lumo-space-m) 0");

        FlexLayout grid = new FlexLayout();
        grid.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        grid.getStyle().set("gap", "var(--lumo-space-m)");

        for (SectorDto sector : sectors) {
            grid.add(buildSectorCard(match, sector));
        }

        if (sectors.isEmpty()) {
            Span empty = new Span("Nenhum setor disponível para esta partida.");
            empty.getStyle().set("color", "var(--lumo-secondary-text-color)");
            return new VerticalLayout(title, empty);
        }

        VerticalLayout section = new VerticalLayout(title, grid);
        section.setPadding(false);
        return section;
    }

    private Div buildSectorCard(MatchDto match, SectorDto sector) {
        Div card = new Div();
        card.addClassName("copa-card");
        card.getStyle().set("min-width", "260px").set("max-width", "300px").set("flex", "1");

        Span name = new Span(sector.sectorName());
        name.getStyle().set("font-weight", "700").set("font-size", "var(--lumo-font-size-l)")
            .set("color", "var(--copa-blue)");

        Span price = new Span(String.format("USD %.2f", sector.price()));
        price.getStyle().set("font-weight", "800").set("font-size", "var(--lumo-font-size-xl)")
             .set("color", "var(--copa-green)");

        Div bar = new Div();
        bar.addClassName("occupancy-bar");
        Div fill = new Div();
        fill.addClassName("occupancy-fill");
        double pct = sector.totalQuantity() > 0
                ? (double) (sector.soldQuantity() + sector.reservedQuantity()) / sector.totalQuantity() * 100
                : 0;
        fill.getStyle().set("width", String.format("%.0f%%", Math.min(pct, 100)));
        bar.add(fill);

        Span avail = new Span(sector.availableQuantity() + " de " + sector.totalQuantity() + " disponíveis");
        avail.getStyle().set("font-size", "var(--lumo-font-size-xs)").set("color", "var(--lumo-secondary-text-color)");

        Span statusBadge = new Span(sector.status() != null ? sector.status() : "AVAILABLE");
        statusBadge.addClassName("status-badge");
        statusBadge.addClassName("AVAILABLE".equals(sector.status()) ? "status-available" : "status-sold-out");

        Button selectBtn = new Button("Selecionar Assentos", VaadinIcon.TICKET.create());
        selectBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        selectBtn.setWidthFull();
        selectBtn.setEnabled(sector.availableQuantity() > 0);
        if (sector.availableQuantity() <= 0) selectBtn.setText("Esgotado");
        selectBtn.addClickListener(e ->
            UI.getCurrent().navigate(SeatMapView.class,
                    new RouteParameters(new RouteParam("id", String.valueOf(match.id())),
                                        new RouteParam("sector", sector.sectorCode()))));

        VerticalLayout content = new VerticalLayout(name, price, bar, avail, statusBadge, selectBtn);
        content.setPadding(false);
        content.setSpacing(false);
        content.getStyle().set("gap", "var(--lumo-space-s)");

        card.add(content);
        return card;
    }
}
