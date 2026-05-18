package com.copa.ticketing.ui.views.seatmap;

import com.copa.ticketing.ui.client.BackendClient;
import com.copa.ticketing.ui.client.dto.MatchDto;
import com.copa.ticketing.ui.client.dto.SeatDto;
import com.copa.ticketing.ui.client.dto.SeatRowSummaryDto;
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
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import java.util.*;

@Route(value = "match/:id/seats/:sector", layout = MainLayout.class)
@PageTitle("Mapa de Assentos | Copa 2026")
@AnonymousAllowed
public class SeatMapView extends VerticalLayout implements BeforeEnterObserver {

    private final BackendClient client;

    // State
    private final Set<Long> selectedSeats = new LinkedHashSet<>();
    private final Map<Long, SeatDto> selectedSeatDetails = new HashMap<>();
    private MatchDto match;
    private String sectorCode;
    private long matchId;
    private long matchSectorId;
    private double unitPrice;
    private List<SeatRowSummaryDto> rows = new ArrayList<>();
    private String activeRow;

    // UI references updated at runtime
    private Div rowPicker;
    private Div seatArea;
    private ProgressBar loadingBar;
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
        selectedSeats.clear();
        selectedSeatDetails.clear();
        matchSectorId = 0;
        unitPrice = 0;
        activeRow = null;

        try {
            matchId = Long.parseLong(idStr);
            match = client.getMatch(matchId);
            rows = client.getSeatMapRows(matchId, sectorCode);
        } catch (Exception e) {
            Notification.show("Erro ao carregar mapa: " + e.getMessage(), 3000, Notification.Position.TOP_END)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }

        buildUI();

        // Auto-select first row with available seats, or just the first row
        if (!rows.isEmpty()) {
            String first = rows.stream()
                .filter(r -> r.availableCount() > 0)
                .map(SeatRowSummaryDto::rowLabel)
                .findFirst()
                .orElse(rows.get(0).rowLabel());
            selectRow(first);
        } else {
            Notification.show("Nenhum assento encontrado para este setor.", 4000, Notification.Position.TOP_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_WARNING);
        }
    }

    private void buildUI() {
        removeAll();

        Button back = new Button("Voltar", VaadinIcon.ARROW_LEFT.create());
        back.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        back.addClickListener(e -> UI.getCurrent().navigate("match/" + match.id()));

        H2 title = new H2("🪑 Mapa de Assentos – " + sectorCode);
        title.addClassName("page-title");

        Span matchInfo = new Span(
            (match.homeTeam() != null ? match.homeTeam() : "TBD")
            + " vs " + (match.awayTeam() != null ? match.awayTeam() : "TBD")
            + " | " + match.venueName());
        matchInfo.addClassName("page-subtitle");

        add(back, title, matchInfo,
            buildSectorOrientation(),
            buildLegend(),
            buildSeatAreaContainer(),
            buildRowPicker(),
            buildBottomBar());
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
        legend.getStyle()
              .set("gap", "var(--lumo-space-m)")
              .set("flex-wrap", "wrap")
              .set("margin", "var(--lumo-space-s) 0");

        legend.add(legendItem("Disponível", "var(--copa-green)"));
        legend.add(legendItem("Reservado", "var(--copa-gold)"));
        legend.add(legendItem("Vendido", "#e2e8f0"));
        legend.add(legendItem("Selecionado", "var(--copa-blue)"));
        return legend;
    }

    private Div legendItem(String label, String color) {
        Div dot = new Div();
        dot.getStyle()
           .set("width", "20px").set("height", "20px")
           .set("border-radius", "4px").set("background", color)
           .set("flex-shrink", "0");
        Span text = new Span(label);
        text.getStyle().set("font-size", "var(--lumo-font-size-xs)");
        Div item = new Div(dot, text);
        item.getStyle().set("display", "flex").set("align-items", "center").set("gap", "6px");
        return item;
    }

    // ─── Row Picker ────────────────────────────────────────────────────────────

    private Div buildRowPicker() {
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

        Span heading = new Span("Selecione a fileira:");
        heading.getStyle()
            .set("font-size", "var(--lumo-font-size-xs)")
            .set("font-weight", "700")
            .set("color", "var(--lumo-secondary-text-color)")
            .set("display", "block")
            .set("margin-bottom", "var(--lumo-space-s)")
            .set("letter-spacing", "0.5px");

        rowPicker = new Div();
        rowPicker.addClassName("seat-row-picker");

        refreshRowPicker();

        wrapper.add(heading, rowPicker);
        return wrapper;
    }

    private void refreshRowPicker() {
        rowPicker.removeAll();
        int total = rows.size();
        for (int i = 0; i < total; i++) {
            rowPicker.add(buildRowChip(rows.get(i), i, total));
        }
    }

    private Div buildRowChip(SeatRowSummaryDto row, int index, int total) {
        boolean isActive   = row.rowLabel().equals(activeRow);
        boolean hasAvail   = row.availableCount() > 0;
        // first 20% of rows are closest to the field
        boolean nearField  = total > 0 && index < Math.max(1, (int) Math.ceil(total * 0.20));

        Span label = new Span(row.rowLabel());
        label.getStyle().set("font-weight", "700").set("font-size", "var(--lumo-font-size-s)");

        Span badge = new Span(row.availableCount() + " livre" + (row.availableCount() == 1 ? "" : "s"));
        badge.addClassName("row-chip-badge");
        if (!hasAvail) badge.getStyle().set("opacity", "0.5");

        Div chip = new Div(label, badge);
        chip.addClassName("seat-row-chip");
        if (isActive)  chip.addClassName("active");
        if (!hasAvail) chip.addClassName("full");
        if (nearField) chip.addClassName("near-field");
        if (nearField) chip.getElement().setAttribute("title", "Próximo ao campo ⚽");

        chip.addClickListener(e -> selectRow(row.rowLabel()));
        return chip;
    }

    // ─── Seat Area ─────────────────────────────────────────────────────────────

    private Div buildSeatAreaContainer() {
        Div container = new Div();
        container.getStyle()
            .set("background", "var(--lumo-base-color)")
            .set("border-radius", "var(--lumo-border-radius-l)")
            .set("padding", "var(--lumo-space-l)")
            .set("box-shadow", "var(--lumo-box-shadow-m)")
            .set("width", "100%")
            .set("box-sizing", "border-box")
            .set("margin-bottom", "var(--lumo-space-m)");

        loadingBar = new ProgressBar();
        loadingBar.setIndeterminate(true);
        loadingBar.setVisible(false);
        loadingBar.getStyle()
            .set("width", "100%")
            .set("margin-bottom", "var(--lumo-space-s)");

        seatArea = new Div();
        seatArea.getStyle()
            .set("overflow-x", "auto")
            .set("max-height", "420px")
            .set("overflow-y", "auto");

        container.add(buildFieldDirectionBanner(), loadingBar, seatArea);
        return container;
    }

    private Div buildFieldDirectionBanner() {
        Span arrow = new Span("↑");
        arrow.getStyle()
            .set("font-size", "1.1em")
            .set("font-weight", "900")
            .set("color", "var(--copa-green)");

        Span text = new Span("CAMPO / FIELD — fileiras mais próximas ao topo");
        text.getStyle()
            .set("font-size", "var(--lumo-font-size-xs)")
            .set("font-weight", "700")
            .set("color", "var(--copa-green)")
            .set("letter-spacing", "0.5px");

        Div banner = new Div(arrow, text);
        banner.getStyle()
            .set("display", "flex")
            .set("align-items", "center")
            .set("gap", "8px")
            .set("background", "linear-gradient(90deg, rgba(0,104,71,0.10) 0%, transparent 100%)")
            .set("border-left", "3px solid var(--copa-green)")
            .set("border-radius", "0 6px 6px 0")
            .set("padding", "6px 14px")
            .set("margin-bottom", "var(--lumo-space-m)");
        return banner;
    }

    private void selectRow(String row) {
        activeRow = row;
        refreshRowPicker();
        seatArea.removeAll();
        loadingBar.setVisible(true);

        try {
            List<SeatDto> seats = client.getSeatMapByRow(matchId, sectorCode, row);
            renderSeats(seats);
        } catch (Exception ex) {
            Notification.show("Erro ao carregar fileira: " + ex.getMessage(),
                3000, Notification.Position.TOP_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        } finally {
            loadingBar.setVisible(false);
        }
    }

    private void renderSeats(List<SeatDto> seats) {
        seatArea.removeAll();

        if (seats.isEmpty()) {
            Span empty = new Span("Nenhum assento nesta fileira.");
            empty.getStyle().set("color", "var(--lumo-secondary-text-color)")
                 .set("font-size", "var(--lumo-font-size-s)");
            seatArea.add(empty);
            return;
        }

        // Capture matchSectorId / unitPrice from first seat
        SeatDto first = seats.get(0);
        if (matchSectorId == 0) matchSectorId = first.matchSectorId();
        if (unitPrice == 0) unitPrice = first.price();

        // Compute proximity label for this row
        int rowIndex = -1;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).rowLabel().equals(activeRow)) { rowIndex = i; break; }
        }
        int total = rows.size();
        String proximityLabel = "";
        if (total > 1 && rowIndex >= 0) {
            double pct = (double) rowIndex / (total - 1);
            if (pct < 0.20)      proximityLabel = "⚽ Próximo ao campo";
            else if (pct > 0.80) proximityLabel = "🔝 Setor superior";
        }

        Div rowHeader = new Div();
        rowHeader.getStyle()
            .set("display", "flex")
            .set("align-items", "center")
            .set("gap", "var(--lumo-space-s)")
            .set("font-size", "var(--lumo-font-size-xs)")
            .set("font-weight", "600")
            .set("margin-bottom", "var(--lumo-space-s)")
            .set("letter-spacing", "0.5px");

        Span rowTitle = new Span("Fileira " + activeRow + " — " + seats.size() + " assentos");
        rowTitle.getStyle().set("color", "var(--lumo-secondary-text-color)");
        rowHeader.add(rowTitle);

        if (!proximityLabel.isEmpty()) {
            Span prox = new Span(proximityLabel);
            prox.getStyle()
                .set("font-size", "var(--lumo-font-size-xxs)")
                .set("background", "rgba(0,104,71,0.10)")
                .set("color", "var(--copa-green)")
                .set("border-radius", "999px")
                .set("padding", "2px 8px")
                .set("font-weight", "700")
                .set("white-space", "nowrap");
            rowHeader.add(prox);
        }

        Div seatsRow = new Div();
        seatsRow.addClassName("seat-row-flex");

        Span rowLabelSpan = new Span(activeRow);
        rowLabelSpan.getStyle()
            .set("width", "24px")
            .set("min-width", "24px")
            .set("flex-shrink", "0")
            .set("font-size", "10px")
            .set("font-weight", "600")
            .set("color", "var(--lumo-secondary-text-color)")
            .set("text-align", "center")
            .set("align-self", "center");
        seatsRow.add(rowLabelSpan);

        for (SeatDto seat : seats) {
            seatsRow.add(buildSeatCell(seat));
        }

        seatArea.add(rowHeader, seatsRow);
    }

    private Div buildSeatCell(SeatDto seat) {
        String status = seat.status() == null ? "available" : seat.status().toUpperCase();
        boolean canSelect = "AVAILABLE".equals(status);
        boolean isSelected = selectedSeats.contains(seat.venueSeatId());

        Div cell = new Div();
        cell.addClassName("seat-btn");
        cell.addClassName(isSelected ? "selected" : seatCssClass(status));
        if (seat.isOptimal()) cell.addClassName("optimal");
        cell.setText(String.valueOf(seat.seatNumber()));
        cell.getElement().setAttribute("title", seat.seatLabel() + " – " + seat.entrance());

        if (!canSelect) {
            cell.getStyle().set("cursor", "not-allowed");
        } else {
            cell.addClickListener(e -> toggleSeat(seat, cell));
        }

        return cell;
    }

    private void toggleSeat(SeatDto seat, Div cell) {
        long id = seat.venueSeatId();
        if (id <= 0) {
            Notification.show("Assento inválido no mapa. Recarregue a página.", 3000, Notification.Position.TOP_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }
        if (selectedSeats.contains(id)) {
            selectedSeats.remove(id);
            selectedSeatDetails.remove(id);
            cell.removeClassName("selected");
            cell.addClassName("available");
        } else {
            if (selectedSeats.size() >= 8) {
                Notification.show("Máximo de 8 assentos por pedido", 2000, Notification.Position.TOP_CENTER);
                return;
            }
            selectedSeats.add(id);
            selectedSeatDetails.put(id, seat);
            cell.removeClassName("available");
            cell.addClassName("selected");
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
        totalPrice.getStyle()
                  .set("font-size", "var(--lumo-font-size-l)")
                  .set("font-weight", "800")
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

    private String seatCssClass(String status) {
        return switch (status) {
            case "AVAILABLE" -> "available";
            case "RESERVED"  -> "reserved";
            default          -> "sold";
        };
    }
}
