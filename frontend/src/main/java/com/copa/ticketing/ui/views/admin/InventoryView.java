package com.copa.ticketing.ui.views.admin;

import com.copa.ticketing.ui.client.BackendClient;
import com.copa.ticketing.ui.layout.MainLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import java.util.List;
import java.util.Map;

@Route(value = "admin/inventory", layout = MainLayout.class)
@PageTitle("Estoque | Copa 2026 Admin")
@RolesAllowed("ADMIN")
public class InventoryView extends VerticalLayout {

    private final BackendClient client;
    private final Grid<Map<String, Object>> grid = new Grid<>();
    private final TextField matchIdField = new TextField();

    public InventoryView(BackendClient client) {
        this.client = client;
        setSizeFull();
        setPadding(true);
        setSpacing(false);
        getStyle().set("background", "var(--copa-surface)");
        add(buildHeader(), buildFilters(), buildGrid());
        loadData(null);
    }

    private HorizontalLayout buildHeader() {
        H2 title = new H2("📦 Estoque por Partida");
        title.addClassName("page-title");
        Paragraph sub = new Paragraph("Visão de capacidade, vendas e reservas por partida");
        sub.addClassName("page-subtitle");
        VerticalLayout left = new VerticalLayout(title, sub);
        left.setPadding(false);
        left.setSpacing(false);
        HorizontalLayout h = new HorizontalLayout(left);
        h.setWidthFull();
        h.getStyle().set("margin-bottom", "var(--lumo-space-m)");
        return h;
    }

    private HorizontalLayout buildFilters() {
        matchIdField.setPlaceholder("ID da partida (opcional)...");
        matchIdField.setPrefixComponent(VaadinIcon.FILTER.create());
        matchIdField.setWidth("220px");

        Button search = new Button("Filtrar", VaadinIcon.SEARCH.create());
        search.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        search.addClickListener(e -> {
            String v = matchIdField.getValue().trim();
            Long id = null;
            if (!v.isBlank()) {
                try { id = Long.parseLong(v); }
                catch (NumberFormatException ex) {
                    Notification.show("ID inválido", 2000, Notification.Position.TOP_CENTER);
                    return;
                }
            }
            loadData(id);
        });

        Button all = new Button("Ver Todas", VaadinIcon.LIST.create());
        all.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        all.addClickListener(e -> { matchIdField.clear(); loadData(null); });

        HorizontalLayout row = new HorizontalLayout(matchIdField, search, all);
        row.setAlignItems(FlexComponent.Alignment.END);
        row.getStyle()
           .set("background", "var(--lumo-base-color)")
           .set("padding", "var(--lumo-space-m)")
           .set("border-radius", "var(--lumo-border-radius-l)")
           .set("box-shadow", "var(--lumo-box-shadow-s)")
           .set("margin-bottom", "var(--lumo-space-m)");
        return row;
    }

    @SuppressWarnings("unchecked")
    private Grid<Map<String, Object>> buildGrid() {
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_NO_BORDER);
        grid.setSizeFull();
        grid.setSelectionMode(Grid.SelectionMode.NONE);

        grid.addColumn(row -> row.getOrDefault("match_number", "-")).setHeader("Partida").setAutoWidth(true);
        grid.addColumn(row -> row.getOrDefault("home_team", "-") + " vs " + row.getOrDefault("away_team", "-"))
                .setHeader("Times").setFlexGrow(1);
        grid.addColumn(row -> row.getOrDefault("venue_name", "-")).setHeader("Estádio").setFlexGrow(1);
        grid.addColumn(row -> row.getOrDefault("total_match_capacity", "-")).setHeader("Capacidade").setAutoWidth(true);
        grid.addColumn(row -> row.getOrDefault("tickets_sold", 0)).setHeader("Vendidos").setAutoWidth(true);
        grid.addColumn(row -> row.getOrDefault("active_reservations", 0)).setHeader("Reservas Ativas").setAutoWidth(true);

        grid.addColumn(new ComponentRenderer<>(row -> {
            Object pct = row.getOrDefault("confirmed_occupancy_percent", 0);
            double val = pct instanceof Number n ? n.doubleValue() : 0;
            Div bar = new Div();
            bar.addClassName("occupancy-bar");
            bar.getStyle().set("width", "100px");
            Div fill = new Div();
            fill.addClassName("occupancy-fill");
            fill.getStyle().set("width", String.format("%.0f%%", Math.min(val, 100)));
            bar.add(fill);
            Span label = new Span(String.format("%.0f%%", val));
            label.getStyle().set("font-size", "var(--lumo-font-size-xs)").set("font-weight", "600");
            VerticalLayout vl = new VerticalLayout(label, bar);
            vl.setPadding(false);
            vl.setSpacing(false);
            return vl;
        })).setHeader("Ocupação").setAutoWidth(true);

        grid.addColumn(row -> {
            Object revenue = row.getOrDefault("gross_revenue", 0);
            if (revenue instanceof Number n) return String.format("USD %,.2f", n.doubleValue());
            return String.valueOf(revenue);
        }).setHeader("Receita Bruta").setAutoWidth(true);

        return grid;
    }

    private void loadData(Long matchId) {
        try {
            List<Map<String, Object>> data = client.getInventory(matchId);
            grid.setItems(data);
        } catch (Exception e) {
            Notification.show("Erro ao carregar estoque: " + e.getMessage(), 4000, Notification.Position.TOP_END)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }
}
