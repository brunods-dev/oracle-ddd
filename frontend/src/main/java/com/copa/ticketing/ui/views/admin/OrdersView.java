package com.copa.ticketing.ui.views.admin;

import com.copa.ticketing.ui.client.BackendClient;
import com.copa.ticketing.ui.client.dto.OrderDto;
import com.copa.ticketing.ui.client.dto.PagedResponse;
import com.copa.ticketing.ui.layout.MainLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.provider.CallbackDataProvider;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import java.util.List;

@Route(value = "admin/orders", layout = MainLayout.class)
@PageTitle("Pedidos | Copa 2026 Admin")
@RolesAllowed("ADMIN")
public class OrdersView extends VerticalLayout {

    private final BackendClient client;
    private final Grid<OrderDto> grid = new Grid<>();
    private final ComboBox<String> statusFilter = new ComboBox<>();

    public OrdersView(BackendClient client) {
        this.client = client;
        setSizeFull();
        setPadding(true);
        setSpacing(false);
        getStyle().set("background", "var(--copa-surface)");
        add(buildHeader(), buildFilters(), buildGrid());
    }

    private HorizontalLayout buildHeader() {
        H2 title = new H2("📋 Pedidos");
        title.addClassName("page-title");
        Paragraph sub = new Paragraph("Gerenciar todos os pedidos do sistema");
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
        statusFilter.setPlaceholder("Filtrar por status...");
        statusFilter.setItems("PENDING_PAYMENT", "PAID", "CANCELLED", "REFUNDED");
        statusFilter.setClearButtonVisible(true);
        statusFilter.setWidth("200px");
        statusFilter.addValueChangeListener(e -> grid.getDataProvider().refreshAll());

        Button refresh = new Button("Atualizar", VaadinIcon.REFRESH.create());
        refresh.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        refresh.addClickListener(e -> grid.getDataProvider().refreshAll());

        HorizontalLayout row = new HorizontalLayout(statusFilter, refresh);
        row.setAlignItems(FlexComponent.Alignment.END);
        row.getStyle()
           .set("background", "var(--lumo-base-color)")
           .set("padding", "var(--lumo-space-m)")
           .set("border-radius", "var(--lumo-border-radius-l)")
           .set("box-shadow", "var(--lumo-box-shadow-s)")
           .set("margin-bottom", "var(--lumo-space-m)");
        return row;
    }

    private Grid<OrderDto> buildGrid() {
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_NO_BORDER);
        grid.setSizeFull();

        grid.addColumn(OrderDto::orderCode).setHeader("Código").setAutoWidth(true);
        grid.addColumn(OrderDto::customerName).setHeader("Cliente").setFlexGrow(1);
        grid.addColumn(OrderDto::customerEmail).setHeader("E-mail").setFlexGrow(1);
        grid.addColumn(OrderDto::paymentMethod).setHeader("Método").setAutoWidth(true);

        grid.addColumn(new ComponentRenderer<>(order -> {
            Span badge = new Span(order.status() != null ? order.status() : "-");
            badge.addClassName("status-badge");
            badge.addClassName(statusClass(order.status()));
            return badge;
        })).setHeader("Status").setAutoWidth(true);

        grid.addColumn(o -> String.format("USD %.2f", o.amount())).setHeader("Valor").setAutoWidth(true);

        grid.addColumn(o -> {
            if (o.createdAt() == null) return "-";
            String s = o.createdAt();
            try { return s.replace("T", " ").substring(0, 16); } catch (Exception e) { return s; }
        }).setHeader("Data").setAutoWidth(true);

        grid.setItems(new CallbackDataProvider<>(
            query -> {
                int page = query.getOffset() / Math.max(query.getLimit(), 1);
                int size = query.getLimit();
                try {
                    PagedResponse<OrderDto> resp = client.getOrders(page, size, statusFilter.getValue());
                    return resp.items().stream();
                } catch (Exception e) {
                    Notification.show("Erro: " + e.getMessage(), 3000, Notification.Position.TOP_END)
                                .addThemeVariants(NotificationVariant.LUMO_ERROR);
                    return List.<OrderDto>of().stream();
                }
            },
            query -> {
                try {
                    return (int) client.getOrders(0, 1, statusFilter.getValue()).total();
                } catch (Exception e) {
                    return 0;
                }
            }
        ));
        return grid;
    }

    private String statusClass(String status) {
        if (status == null) return "status-pending";
        return switch (status) {
            case "PAID" -> "status-paid";
            case "PENDING_PAYMENT" -> "status-pending";
            case "CANCELLED", "REFUNDED" -> "status-cancelled";
            default -> "status-pending";
        };
    }
}
