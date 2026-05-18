package com.copa.ticketing.ui.views.seatmap;

import com.copa.ticketing.ui.client.BackendClient;
import com.copa.ticketing.ui.client.dto.MatchDto;
import com.copa.ticketing.ui.client.dto.SeatDto;
import com.copa.ticketing.ui.layout.MainLayout;
import com.copa.ticketing.ui.views.checkout.CheckoutView;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import java.util.*;

@Route(value = "match/:id/seats/:sector", layout = MainLayout.class)
@PageTitle("Mapa de Assentos | Copa 2026")
@AnonymousAllowed
public class SeatMapView extends VerticalLayout implements BeforeEnterObserver {

    private final BackendClient client;
    private final Set<Long> selectedSeats = new LinkedHashSet<>();
    private final Map<Long, SeatDto> seatDtoMap = new HashMap<>();
    private MatchDto match;
    private String sectorCode;
    private long matchSectorId;
    private double unitPrice;

    private Div seatGrid;
    private Span selectedCount;
    private Span totalPrice;
    private Button proceedBtn;

    public SeatMapView(BackendClient client) {
        this.client = client;
        setWidthFull();
        setPadding(true);
        getStyle()
            .set("background", "var(--copa-surface)")
            .set("overflow-x", "hidden")
            .set("box-sizing", "border-box");
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String idStr = event.getRouteParameters().get("id").orElse("0");
        sectorCode = event.getRouteParameters().get("sector").orElse("");
        seatDtoMap.clear();
        selectedSeats.clear();
        try {
            long matchId = Long.parseLong(idStr);
            match = client.getMatch(matchId);
            loadSeats(matchId, sectorCode);
            if (seatDtoMap.isEmpty()) {
                Notification.show("Nenhum assento encontrado para este setor.", 4000, Notification.Position.TOP_CENTER)
                            .addThemeVariants(NotificationVariant.LUMO_WARNING);
            }
            buildUI();
        } catch (Exception e) {
            Notification.show("Erro ao carregar mapa: " + e.getMessage(), 3000, Notification.Position.TOP_END)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void loadSeats(long matchId, String sector) {
        for (SeatDto s : client.getAllSeatMap(matchId, sector)) {
            if (s.venueSeatId() <= 0) continue;
            seatDtoMap.put(s.venueSeatId(), s);
            if (matchSectorId == 0) matchSectorId = s.matchSectorId();
            unitPrice = s.price();
        }
    }

    private void buildUI() {
        removeAll();

        Button back = new Button("Voltar", VaadinIcon.ARROW_LEFT.create());
        back.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        back.addClickListener(e -> UI.getCurrent().navigate("match/" + match.id()));

        H2 title = new H2("🪑 Mapa de Assentos – " + sectorCode);
        title.addClassName("page-title");

        Span matchInfo = new Span((match.homeTeam() != null ? match.homeTeam() : "TBD")
                + " vs " + (match.awayTeam() != null ? match.awayTeam() : "TBD")
                + " | " + match.venueName());
        matchInfo.addClassName("page-subtitle");

        add(back, title, matchInfo, buildSectorOrientation(), buildLegend(), buildSeatGrid(), buildBottomBar());
    }

    // ─── Sector Orientation Mini-Map ───────────────────────────────────────────

    private Div buildSectorOrientation() {
        Div wrapper = new Div();
        wrapper.getStyle()
            .set("background", "var(--copa-card-bg)")
            .set("border-radius", "var(--lumo-border-radius-l)")
            .set("padding", "var(--lumo-space-m)")
            .set("margin-bottom", "var(--lumo-space-m)")
            .set("box-shadow", "var(--lumo-box-shadow-s)")
            .set("border", "1px solid var(--lumo-contrast-10pct)")
            .set("width", "100%")
            .set("box-sizing", "border-box");

        String sc = sectorCode != null ? sectorCode.toUpperCase() : "";
        boolean isNorth     = sc.contains("NORTH");
        boolean isSouth     = sc.contains("SOUTH");
        boolean isEast      = sc.contains("EAST");
        boolean isWestOrVip = sc.contains("WEST") || sc.contains("VIP") || sc.contains("LOUNGE");

        Div grid = new Div();
        grid.addClassName("stadium-map-grid");
        grid.getStyle()
            .set("max-width", "380px")
            .set("margin", "0 auto")
            .set("width", "100%");

        grid.add(new Div());
        grid.add(buildOrientationBadge("NORTH", isNorth));
        grid.add(new Div());

        grid.add(buildOrientationBadge("VIP", isWestOrVip));
        grid.add(buildMiniFieldImage());
        grid.add(buildOrientationBadge("EAST", isEast));

        grid.add(new Div());
        grid.add(buildOrientationBadge("SOUTH", isSouth));
        grid.add(new Div());

        Span currentLabel = new Span("Setor atual: " + sectorCode);
        currentLabel.getStyle()
            .set("font-size", "var(--lumo-font-size-xs)")
            .set("color", "var(--lumo-secondary-text-color)")
            .set("display", "block")
            .set("text-align", "center")
            .set("margin-top", "var(--lumo-space-s)")
            .set("font-weight", "600");

        wrapper.add(grid, currentLabel);
        return wrapper;
    }

    private Span buildOrientationBadge(String label, boolean active) {
        Span badge = new Span(label);
        badge.getStyle()
            .set("font-size", "var(--lumo-font-size-xxs)")
            .set("font-weight", "700")
            .set("padding", "4px 10px")
            .set("border-radius", "6px")
            .set("text-align", "center")
            .set("white-space", "nowrap")
            .set("letter-spacing", "0.5px")
            .set("display", "block");
        if (active) {
            badge.getStyle()
                .set("background", "var(--copa-green)")
                .set("color", "#fff")
                .set("box-shadow", "0 0 0 3px rgba(0,104,71,0.25)");
        } else {
            badge.getStyle()
                .set("background", "var(--lumo-contrast-5pct)")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("opacity", "0.45");
        }
        return badge;
    }

    private Div buildMiniFieldImage() {
        Image img = new Image("/images/stadium.jpg", "Campo");
        img.getStyle()
            .set("width", "100%")
            .set("height", "100%")
            .set("object-fit", "cover")
            .set("display", "block");

        Div field = new Div(img);
        field.getStyle()
            .set("border-radius", "8px")
            .set("overflow", "hidden")
            .set("box-shadow", "0 2px 12px rgba(0,40,104,0.15)")
            .set("border", "2px solid #fff")
            .set("aspect-ratio", "16/10")
            .set("min-width", "120px")
            .set("width", "100%");
        return field;
    }

    // ─── Legend ────────────────────────────────────────────────────────────────

    private HorizontalLayout buildLegend() {
        HorizontalLayout legend = new HorizontalLayout();
        legend.getStyle().set("gap", "var(--lumo-space-m)").set("flex-wrap", "wrap")
              .set("margin", "var(--lumo-space-s) 0");

        legend.add(legendItem("Disponível", "var(--copa-green)"));
        legend.add(legendItem("Reservado", "var(--copa-gold)"));
        legend.add(legendItem("Vendido", "#e2e8f0"));
        legend.add(legendItem("Selecionado", "var(--copa-blue)"));
        return legend;
    }

    private Div legendItem(String label, String color) {
        Div dot = new Div();
        dot.getStyle().set("width", "20px").set("height", "20px")
           .set("border-radius", "4px").set("background", color)
           .set("flex-shrink", "0");
        Span text = new Span(label);
        text.getStyle().set("font-size", "var(--lumo-font-size-xs)");
        Div item = new Div(dot, text);
        item.getStyle().set("display", "flex").set("align-items", "center").set("gap", "6px");
        return item;
    }

    // ─── Seat Grid ─────────────────────────────────────────────────────────────

    private Div buildSeatGrid() {
        seatGrid = new Div();
        seatGrid.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("padding", "var(--lumo-space-l)")
                .set("box-shadow", "var(--lumo-box-shadow-m)")
                .set("width", "100%")
                .set("min-width", "0")
                .set("box-sizing", "border-box")
                .set("max-height", "480px")
                .set("overflow-y", "auto")
                .set("overflow-x", "auto")
                .set("margin-bottom", "var(--lumo-space-m)");

        seatGrid.add(buildFieldBanner());

        Map<String, List<SeatDto>> byRow = new TreeMap<>();
        for (SeatDto seat : seatDtoMap.values()) {
            byRow.computeIfAbsent(seat.rowLabel(), k -> new ArrayList<>()).add(seat);
        }

        for (Map.Entry<String, List<SeatDto>> entry : byRow.entrySet()) {
            Div row = new Div();
            row.getStyle()
               .set("display", "flex")
               .set("align-items", "center")
               .set("gap", "2px")
               .set("margin-bottom", "2px")
               .set("width", "100%");

            Span rowLabel = new Span(entry.getKey());
            rowLabel.getStyle()
                    .set("width", "24px")
                    .set("min-width", "24px")
                    .set("flex-shrink", "0")
                    .set("font-size", "10px")
                    .set("font-weight", "600")
                    .set("color", "var(--lumo-secondary-text-color)")
                    .set("text-align", "center");
            row.add(rowLabel);

            List<SeatDto> seats = entry.getValue();
            seats.sort(Comparator.comparingInt(SeatDto::seatNumber));

            for (SeatDto seat : seats) {
                row.add(buildSeatButton(seat));
            }
            seatGrid.add(row);
        }
        return seatGrid;
    }

    private Div buildFieldBanner() {
        Image img = new Image("/images/stadium.jpg", "Campo");
        img.getStyle()
            .set("position", "absolute")
            .set("inset", "0")
            .set("width", "100%")
            .set("height", "100%")
            .set("object-fit", "cover");

        Div overlay = new Div();
        overlay.getStyle()
            .set("position", "absolute")
            .set("inset", "0")
            .set("display", "flex")
            .set("align-items", "center")
            .set("justify-content", "center")
            .set("background", "rgba(0,104,71,0.60)");

        Span label = new Span("⚽  CAMPO / FIELD");
        label.getStyle()
            .set("color", "#fff")
            .set("font-weight", "800")
            .set("font-size", "var(--lumo-font-size-s)")
            .set("letter-spacing", "3px")
            .set("text-shadow", "0 1px 4px rgba(0,0,0,0.4)");

        overlay.add(label);

        Div banner = new Div( overlay);
        banner.getStyle()
            .set("border-radius", "10px")
            .set("height", "72px")
            .set("position", "relative")
            .set("overflow", "hidden")
            .set("margin-bottom", "var(--lumo-space-m)");
        return banner;
    }

    private Button buildSeatButton(SeatDto seat) {
        Button btn = new Button(String.valueOf(seat.seatNumber()));
        btn.addClassName("seat-btn");
        btn.addClassName(seatClass(seat.status()));
        if (seat.isOptimal()) btn.addClassName("optimal");
        btn.getElement().setProperty("title", seat.seatLabel() + " – " + seat.entrance());

        boolean canSelect = "AVAILABLE".equalsIgnoreCase(seat.status());
        btn.setEnabled(canSelect);

        if (canSelect) {
            btn.addClickListener(e -> toggleSeat(seat, btn));
        }
        return btn;
    }

    private void toggleSeat(SeatDto seat, Button btn) {
        long id = seat.venueSeatId();
        if (id <= 0) {
            Notification.show("Assento inválido no mapa. Recarregue a página.", 3000, Notification.Position.TOP_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }
        if (selectedSeats.contains(id)) {
            selectedSeats.remove(id);
            btn.removeClassName("selected");
            btn.addClassName("available");
        } else {
            if (selectedSeats.size() >= 8) {
                Notification.show("Máximo de 8 assentos por pedido", 2000, Notification.Position.TOP_CENTER);
                return;
            }
            selectedSeats.add(id);
            btn.removeClassName("available");
            btn.addClassName("selected");
        }
        updateBottomBar();
    }

    // ─── Bottom Bar ────────────────────────────────────────────────────────────

    private Div buildBottomBar() {
        Div bar = new Div();
        bar.getStyle()
           .set("position", "sticky")
           .set("bottom", "0")
           .set("background", "var(--lumo-base-color)")
           .set("border-top", "2px solid var(--copa-green)")
           .set("padding", "var(--lumo-space-m)")
           .set("border-radius", "var(--lumo-border-radius-l)")
           .set("box-shadow", "0 -4px 16px rgba(0,40,104,0.10)")
           .set("display", "flex")
           .set("align-items", "center")
           .set("justify-content", "space-between")
           .set("flex-wrap", "wrap")
           .set("gap", "var(--lumo-space-m)")
           .set("width", "100%")
           .set("box-sizing", "border-box");

        selectedCount = new Span("0 assentos selecionados");
        selectedCount.getStyle().set("font-weight", "600");

        totalPrice = new Span("Total: USD 0,00");
        totalPrice.getStyle().set("font-size", "var(--lumo-font-size-l)").set("font-weight", "800")
                  .set("color", "var(--copa-green)");

        proceedBtn = new Button("Continuar para Checkout", VaadinIcon.ARROW_RIGHT.create());
        proceedBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        proceedBtn.setEnabled(false);
        proceedBtn.addClickListener(e -> navigateToCheckout());

        bar.add(selectedCount, totalPrice, proceedBtn);
        return bar;
    }

    private void updateBottomBar() {
        int count = selectedSeats.size();
        selectedCount.setText(count + (count == 1 ? " assento selecionado" : " assentos selecionados"));
        totalPrice.setText(String.format("Total: USD %.2f", count * unitPrice));
        proceedBtn.setEnabled(count > 0);
    }

    private void navigateToCheckout() {
        if (selectedSeats.isEmpty()) return;
        if (matchSectorId <= 0 || match.id() <= 0) {
            Notification.show("Dados da partida incompletos. Volte e tente novamente.", 3000, Notification.Position.TOP_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }
        VaadinSession.getCurrent().setAttribute(CheckoutView.SESSION_SEAT_IDS, new ArrayList<>(selectedSeats));
        String seats = String.join(",", selectedSeats.stream().map(String::valueOf).toList());
        QueryParameters qp = QueryParameters.simple(Map.of(
                "matchId", String.valueOf(match.id()),
                "sectorId", String.valueOf(matchSectorId),
                "price", String.valueOf(unitPrice),
                "seats", seats
        ));
        UI.getCurrent().navigate("checkout", qp);
    }

    private String seatClass(String status) {
        if (status == null) return "available";
        return switch (status.toUpperCase()) {
            case "AVAILABLE" -> "available";
            case "RESERVED"  -> "reserved";
            case "SOLD"      -> "sold";
            default          -> "sold";
        };
    }
}
