package com.copa.ticketing.ui.views.tickets;

import com.copa.ticketing.ui.client.BackendClient;
import com.copa.ticketing.ui.client.dto.TicketDto;
import com.copa.ticketing.ui.layout.MainLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import java.util.List;

@Route(value = "tickets", layout = MainLayout.class)
@PageTitle("Meus Ingressos | Copa 2026")
@AnonymousAllowed
public class MyTicketsView extends VerticalLayout {

    private final BackendClient client;
    private final FlexLayout ticketsContainer = new FlexLayout();

    public MyTicketsView(BackendClient client) {
        this.client = client;
        setSizeFull();
        setPadding(true);
        getStyle().set("background", "var(--copa-surface)");

        ticketsContainer.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        ticketsContainer.getStyle().set("gap", "var(--lumo-space-m)");

        add(buildHeader(), buildSearchSection(), ticketsContainer);
    }

    private HorizontalLayout buildHeader() {
        H2 title = new H2("🎟 Meus Ingressos");
        title.addClassName("page-title");

        Paragraph sub = new Paragraph("Consulte seus ingressos pelo e-mail, CPF ou passaporte");
        sub.addClassName("page-subtitle");

        VerticalLayout left = new VerticalLayout(title, sub);
        left.setPadding(false);
        left.setSpacing(false);

        HorizontalLayout header = new HorizontalLayout(left);
        header.setWidthFull();
        return header;
    }

    private HorizontalLayout buildSearchSection() {
        TextField lookupField = new TextField();
        lookupField.setPlaceholder("E-mail, CPF ou passaporte...");
        lookupField.setPrefixComponent(VaadinIcon.SEARCH.create());
        lookupField.setWidth("320px");

        Button searchBtn = new Button("Buscar Ingressos", VaadinIcon.TICKET.create());
        searchBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        searchBtn.addClickListener(e -> loadTickets(lookupField.getValue().trim()));
        lookupField.addKeyDownListener(com.vaadin.flow.component.Key.ENTER, e -> loadTickets(lookupField.getValue().trim()));

        HorizontalLayout row = new HorizontalLayout(lookupField, searchBtn);
        row.setAlignItems(com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.END);
        row.getStyle()
           .set("background", "var(--lumo-base-color)")
           .set("padding", "var(--lumo-space-m)")
           .set("border-radius", "var(--lumo-border-radius-l)")
           .set("box-shadow", "var(--lumo-box-shadow-s)")
           .set("margin-bottom", "var(--lumo-space-m)");
        return row;
    }

    private void loadTickets(String lookup) {
        if (lookup.isBlank()) {
            Notification.show("Informe seu e-mail, CPF ou passaporte", 2000, Notification.Position.TOP_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_WARNING);
            return;
        }
        try {
            List<TicketDto> tickets = client.getTickets(lookup);
            ticketsContainer.removeAll();
            if (tickets.isEmpty()) {
                Span empty = new Span("Nenhum ingresso encontrado para os dados informados.");
                empty.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "var(--lumo-font-size-m)");
                ticketsContainer.add(empty);
                return;
            }
            for (TicketDto t : tickets) {
                ticketsContainer.add(buildTicketCard(t));
            }
        } catch (Exception e) {
            Notification.show("Erro ao buscar ingressos: " + e.getMessage(), 4000, Notification.Position.TOP_END)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private Div buildTicketCard(TicketDto ticket) {
        Div card = new Div();
        card.addClassName("ticket-card");
        card.getStyle().set("min-width", "280px").set("max-width", "340px").set("flex", "1");

        Span matchTitle = new Span(
                (ticket.homeTeam() != null ? ticket.homeTeam() : "TBD")
                + " vs " + (ticket.awayTeam() != null ? ticket.awayTeam() : "TBD"));
        matchTitle.getStyle().set("font-size", "var(--lumo-font-size-l)").set("font-weight", "800")
                  .set("display", "block").set("margin-bottom", "var(--lumo-space-xs)");

        Span venue = new Span("🏟 " + (ticket.venueName() != null ? ticket.venueName() : "-")
                + " – " + (ticket.venueCity() != null ? ticket.venueCity() : ""));
        venue.getStyle().set("font-size", "var(--lumo-font-size-xs)").set("opacity", ".85")
             .set("display", "block").set("margin-bottom", "var(--lumo-space-xs)");

        Span date = new Span("📅 " + (ticket.matchAt() != null ? ticket.matchAt().replace("T", " ").substring(0, 16) : "A definir"));
        date.getStyle().set("font-size", "var(--lumo-font-size-xs)").set("opacity", ".85")
            .set("display", "block").set("margin-bottom", "var(--lumo-space-s)");

        Hr separator = new Hr();
        separator.getStyle().set("border-color", "rgba(255,255,255,0.2)").set("margin", "var(--lumo-space-s) 0");

        Span sector = new Span("🪑 Setor: " + (ticket.sectorName() != null ? ticket.sectorName() : "-"));
        sector.getStyle().set("font-size", "var(--lumo-font-size-s)").set("display", "block");

        Span seat = new Span("💺 Assento: " + (ticket.seatLabel() != null ? ticket.seatLabel() : "-"));
        seat.getStyle().set("font-size", "var(--lumo-font-size-s)").set("display", "block");

        Span gate = new Span("🚪 Entrada: " + (ticket.gate() != null ? ticket.gate() : "-"));
        gate.getStyle().set("font-size", "var(--lumo-font-size-s)").set("display", "block");

        Hr sep2 = new Hr();
        sep2.getStyle().set("border-color", "rgba(255,255,255,0.2)").set("margin", "var(--lumo-space-s) 0");

        Span code = new Span(ticket.ticketCode() != null ? ticket.ticketCode() : "-");
        code.addClassName("ticket-code");
        code.getStyle().set("display", "block").set("text-align", "center")
            .set("margin-bottom", "var(--lumo-space-s)");

        Span price = new Span("USD " + String.format("%.2f", ticket.unitPrice()));
        price.getStyle().set("font-size", "var(--lumo-font-size-m)").set("font-weight", "700")
             .set("display", "block").set("text-align", "right");

        card.add(matchTitle, venue, date, separator, sector, seat, gate, sep2, code, price);
        return card;
    }
}
