package com.copa.ticketing.ui.views.admin;

import com.copa.ticketing.ui.client.BackendClient;
import com.copa.ticketing.ui.client.dto.DashboardDto;
import com.copa.ticketing.ui.client.dto.DashboardDto.DailySaleDto;
import com.copa.ticketing.ui.client.dto.DashboardDto.TopMatchDto;
import com.copa.ticketing.ui.layout.MainLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import java.util.List;

@Route(value = "admin/dashboard", layout = MainLayout.class)
@PageTitle("Dashboard | Copa 2026 Admin")
@RolesAllowed("ADMIN")
public class DashboardView extends VerticalLayout {

    private final BackendClient client;

    public DashboardView(BackendClient client) {
        this.client = client;
        setSizeFull();
        setPadding(true);
        setSpacing(false);
        getStyle().set("background", "var(--copa-surface)");
        loadData();
    }

    private void loadData() {
        try {
            DashboardDto dash = client.getDashboard();
            add(buildHeader());
            add(buildKpiRow(dash));
            add(buildTopMatchesSection(dash.topMatches()));
            add(buildSalesSection(dash.dailySales()));
        } catch (Exception e) {
            Notification.show("Erro ao carregar dashboard: " + e.getMessage(), 4000, Notification.Position.TOP_END)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private HorizontalLayout buildHeader() {
        H2 title = new H2("📊 Dashboard Operacional");
        title.addClassName("page-title");
        Paragraph sub = new Paragraph("Copa do Mundo 2026 – Visão geral de vendas e ocupação");
        sub.addClassName("page-subtitle");
        VerticalLayout left = new VerticalLayout(title, sub);
        left.setPadding(false);
        left.setSpacing(false);
        HorizontalLayout h = new HorizontalLayout(left);
        h.setWidthFull();
        h.getStyle().set("margin-bottom", "var(--lumo-space-m)");
        return h;
    }

    private FlexLayout buildKpiRow(DashboardDto dash) {
        FlexLayout row = new FlexLayout(
                kpiCard("Total Pedidos",    String.valueOf(dash.totalOrders()),       "orders",      "📋"),
                kpiCard("Pedidos Pagos",    String.valueOf(dash.paidOrders()),        "",            "✅"),
                kpiCard("Receita Bruta",    formatMoney(dash.grossRevenue()),         "revenue",     "💰"),
                kpiCard("Ingressos Emitidos", String.valueOf(dash.ticketsSold()),     "tickets",     "🎟"),
                kpiCard("Reservas Ativas",  String.valueOf(dash.activeReservations()), "reservations","⏳"),
                kpiCard("Conversão",        String.format("%.1f%%", dash.conversionPercent()), "conversion","📈")
        );
        row.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        row.getStyle().set("gap", "var(--lumo-space-m)").set("margin-bottom", "var(--lumo-space-l)");
        return row;
    }

    private Div kpiCard(String label, String value, String colorClass, String icon) {
        Div card = new Div();
        card.addClassName("kpi-card");
        if (!colorClass.isBlank()) card.addClassName(colorClass);
        card.getStyle().set("flex", "1").set("min-width", "150px");

        Span ico = new Span(icon);
        ico.getStyle().set("font-size", "1.5rem").set("display", "block");

        Span val = new Span(value);
        val.addClassName("kpi-value");

        Span lbl = new Span(label);
        lbl.addClassName("kpi-label");

        card.add(ico, val, lbl);
        return card;
    }

    private VerticalLayout buildTopMatchesSection(List<TopMatchDto> matches) {
        H3 title = new H3("🏆 Top Partidas por Venda");
        title.getStyle().set("color", "var(--copa-blue)").set("font-weight", "800")
             .set("margin", "0 0 var(--lumo-space-s) 0");

        Grid<TopMatchDto> grid = new Grid<>(TopMatchDto.class, false);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_NO_BORDER);
        grid.setHeight("320px");

        grid.addColumn(m -> m.homeTeam() + " vs " + m.awayTeam()).setHeader("Partida").setFlexGrow(2);
        grid.addColumn(m -> m.venueName() + ", " + m.venueCity()).setHeader("Estádio").setFlexGrow(2);
        grid.addColumn(TopMatchDto::ticketsSold).setHeader("Vendidos").setAutoWidth(true);
        grid.addColumn(m -> String.format("%.0f%%", m.occupancyPercent())).setHeader("Ocupação").setAutoWidth(true);
        grid.addColumn(m -> formatMoney(m.grossRevenue())).setHeader("Receita").setAutoWidth(true);

        grid.setItems(matches != null ? matches : List.of());

        VerticalLayout section = new VerticalLayout(title, grid);
        section.setPadding(false);
        section.addClassName("copa-card");
        section.getStyle().set("margin-bottom", "var(--lumo-space-l)");
        return section;
    }

    private VerticalLayout buildSalesSection(List<DailySaleDto> sales) {
        H3 title = new H3("📅 Vendas Diárias (últimos 30 dias)");
        title.getStyle().set("color", "var(--copa-blue)").set("font-weight", "800")
             .set("margin", "0 0 var(--lumo-space-s) 0");

        Grid<DailySaleDto> grid = new Grid<>(DailySaleDto.class, false);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_NO_BORDER);
        grid.setHeight("280px");

        grid.addColumn(DailySaleDto::saleDate).setHeader("Data").setAutoWidth(true);
        grid.addColumn(DailySaleDto::ordersCreated).setHeader("Pedidos Criados").setAutoWidth(true);
        grid.addColumn(DailySaleDto::paidOrders).setHeader("Pedidos Pagos").setAutoWidth(true);
        grid.addColumn(s -> formatMoney(s.grossRevenue())).setHeader("Receita").setAutoWidth(true);

        grid.setItems(sales != null ? sales : List.of());

        VerticalLayout section = new VerticalLayout(title, grid);
        section.setPadding(false);
        section.addClassName("copa-card");
        return section;
    }

    private String formatMoney(double value) {
        return String.format("USD %,.2f", value);
    }
}
