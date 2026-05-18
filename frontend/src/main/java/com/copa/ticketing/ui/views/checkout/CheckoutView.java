package com.copa.ticketing.ui.views.checkout;

import com.copa.ticketing.ui.client.BackendClient;
import com.copa.ticketing.ui.layout.MainLayout;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import java.util.*;

@Route(value = "checkout", layout = MainLayout.class)
@PageTitle("Checkout | Copa 2026")
@AnonymousAllowed
public class CheckoutView extends VerticalLayout implements HasUrlParameter<String> {

    public static final String SESSION_SEAT_IDS = "checkout.seatIds";

    private final BackendClient client;

    private long matchId;
    private long sectorId;
    private double price;
    private List<Long> seatIds = new ArrayList<>();

    private VerticalLayout formSection;
    private VerticalLayout pixSection;
    private VerticalLayout successSection;

    private String reservationCode;
    private String paymentRef;
    private String pixQr;

    public CheckoutView(BackendClient client) {
        this.client = client;
        setSizeFull();
        setPadding(true);
        setMaxWidth("720px");
        getStyle().set("margin", "0 auto").set("background", "var(--copa-surface)");
    }

    @Override
    public void setParameter(BeforeEvent event, @OptionalParameter String parameter) {
        Location location = event.getLocation();
        QueryParameters qp = location.getQueryParameters();
        matchId = longParam(qp, "matchId");
        sectorId = longParam(qp, "sectorId");
        price = doubleParam(qp, "price");
        seatIds = parseSeatIds(qp);
        buildUI();
    }

    private void buildUI() {
        removeAll();

        H2 title = new H2("🎟 Checkout");
        title.addClassName("page-title");
        title.getStyle().set("margin-bottom", "var(--lumo-space-m)");

        Div summary = buildSummaryCard();
        formSection = buildFormSection();
        pixSection = buildPixSection();
        pixSection.setVisible(false);
        successSection = buildSuccessSection();
        successSection.setVisible(false);

        add(title, summary, formSection, pixSection, successSection);
    }

    private Div buildSummaryCard() {
        Div card = new Div();
        card.addClassName("copa-card");
        card.getStyle().set("margin-bottom", "var(--lumo-space-l)");

        H3 h = new H3("Resumo do Pedido");
        h.getStyle().set("margin", "0 0 var(--lumo-space-s) 0").set("color", "var(--copa-blue)");

        Span seatsInfo = new Span("🪑 " + seatIds.size() + (seatIds.size() == 1 ? " assento" : " assentos") + " selecionado(s)");
        seatsInfo.getStyle().set("display", "block").set("font-size", "var(--lumo-font-size-s)");

        Span priceInfo = new Span("💰 USD " + String.format("%.2f", price) + " / assento");
        priceInfo.getStyle().set("display", "block").set("font-size", "var(--lumo-font-size-s)");

        Span total = new Span("Total: USD " + String.format("%.2f", price * seatIds.size()));
        total.getStyle().set("display", "block").set("font-size", "var(--lumo-font-size-xl)")
             .set("font-weight", "800").set("color", "var(--copa-green)")
             .set("margin-top", "var(--lumo-space-s)");

        card.add(h, seatsInfo, priceInfo, total);
        return card;
    }

    private VerticalLayout buildFormSection() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(false);
        layout.addClassName("copa-card");

        H3 h = new H3("Dados do Comprador");
        h.getStyle().set("color", "var(--copa-blue)").set("margin", "0 0 var(--lumo-space-m) 0");

        TextField fullName = new TextField("Nome Completo");
        fullName.setWidthFull();
        fullName.setRequired(true);
        fullName.setPlaceholder("Ex: João Silva");

        TextField email = new TextField("E-mail");
        email.setWidthFull();
        email.setRequired(true);
        email.setPlaceholder("joao@exemplo.com");

        TextField docNumber = new TextField("CPF / Passaporte");
        docNumber.setWidthFull();
        docNumber.setRequired(true);
        docNumber.setPlaceholder("000.000.000-00");

        TextField phone = new TextField("Telefone");
        phone.setWidthFull();
        phone.setPlaceholder("+55 (11) 99999-9999");

        Button reserveBtn = new Button("Reservar e Gerar PIX", VaadinIcon.LOCK.create());
        reserveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        reserveBtn.setWidthFull();
        reserveBtn.getStyle().set("margin-top", "var(--lumo-space-m)");
        reserveBtn.addClickListener(e -> {
            if (seatIds.isEmpty()) {
                Notification.show("Nenhum assento selecionado. Volte ao mapa e escolha os assentos.",
                        4000, Notification.Position.TOP_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_WARNING);
                return;
            }
            if (fullName.getValue().isBlank() || email.getValue().isBlank() || docNumber.getValue().isBlank()) {
                Notification.show("Preencha todos os campos obrigatórios", 3000, Notification.Position.TOP_CENTER)
                            .addThemeVariants(NotificationVariant.LUMO_WARNING);
                return;
            }
            doReservation(fullName.getValue(), email.getValue(), "CPF", docNumber.getValue(), phone.getValue());
        });

        layout.add(h, fullName, email, docNumber, phone, reserveBtn);
        return layout;
    }

    private void doReservation(String fullName, String email, String docType, String doc, String phone) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("fullName", fullName);
            body.put("email", email);
            body.put("documentType", docType);
            body.put("documentNumber", doc);
            body.put("phone", phone);
            body.put("matchId", matchId);
            body.put("matchSectorId", sectorId);
            body.put("unitPrice", price);
            body.put("seatIds", seatIds);

            Map<String, Object> res = client.createReservation(body);
            reservationCode = (String) res.get("reservationCode");

            Map<String, Object> checkout = client.checkout(reservationCode);
            paymentRef = (String) checkout.get("paymentReference");
            pixQr = (String) checkout.getOrDefault("pixQrCode", "");

            formSection.setVisible(false);
            buildPixSection();
            pixSection.setVisible(true);

        } catch (Exception e) {
            Notification.show("Erro ao criar reserva: " + e.getMessage(), 4000, Notification.Position.TOP_END)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private VerticalLayout buildPixSection() {
        if (pixSection == null) pixSection = new VerticalLayout();
        pixSection.removeAll();
        pixSection.setPadding(false);

        Div container = new Div();
        container.addClassName("pix-container");

        H3 h = new H3("💸 Pagamento via PIX");
        h.getStyle().set("color", "var(--copa-blue)");

        Span instr = new Span("Escaneie o QR Code ou copie o código abaixo para pagar");
        instr.getStyle().set("display", "block").set("font-size", "var(--lumo-font-size-s)")
             .set("margin-bottom", "var(--lumo-space-m)");

        Div qrPlaceholder = new Div();
        qrPlaceholder.getStyle()
                     .set("width", "200px").set("height", "200px")
                     .set("background", "var(--lumo-contrast-10pct)")
                     .set("border-radius", "8px")
                     .set("display", "flex").set("align-items", "center").set("justify-content", "center")
                     .set("margin", "0 auto var(--lumo-space-m) auto")
                     .set("font-size", "64px");
        qrPlaceholder.add(new Span("📱"));

        Div codeBox = new Div(new Span(pixQr != null ? pixQr : paymentRef));
        codeBox.addClassName("pix-code");

        Span ref = new Span("Referência: " + paymentRef);
        ref.getStyle().set("font-size", "var(--lumo-font-size-xs)").set("color", "var(--lumo-secondary-text-color)")
           .set("display", "block").set("margin-top", "var(--lumo-space-s)");

        Button confirmBtn = new Button("✅ Confirmar Pagamento (Simulado)", VaadinIcon.CHECK.create());
        confirmBtn.addThemeVariants(ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_LARGE);
        confirmBtn.setWidthFull();
        confirmBtn.getStyle().set("margin-top", "var(--lumo-space-l)");
        confirmBtn.addClickListener(e -> doConfirmPayment());

        container.add(h, instr, qrPlaceholder, codeBox, ref);
        pixSection.add(container, confirmBtn);
        return pixSection;
    }

    private void doConfirmPayment() {
        try {
            Map<String, Object> result = client.confirmPayment(paymentRef);
            pixSection.setVisible(false);
            buildSuccessSection(result);
            successSection.setVisible(true);
        } catch (Exception e) {
            Notification.show("Erro ao confirmar: " + e.getMessage(), 4000, Notification.Position.TOP_END)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private VerticalLayout buildSuccessSection() {
        if (successSection == null) successSection = new VerticalLayout();
        return successSection;
    }

    private void buildSuccessSection(Map<String, Object> result) {
        successSection.removeAll();
        successSection.setPadding(false);

        Div card = new Div();
        card.addClassName("copa-card");
        card.getStyle()
            .set("text-align", "center")
            .set("background", "linear-gradient(135deg, #f0fdf4, #dbeafe)");

        Span icon = new Span("🎉");
        icon.getStyle().set("font-size", "4rem").set("display", "block");

        H2 congrats = new H2("Pagamento Confirmado!");
        congrats.getStyle().set("color", "var(--copa-green)").set("margin", "var(--lumo-space-s) 0");

        int ticketsCount = result.get("ticketsIssued") instanceof Number n ? n.intValue() : 0;
        Span tktInfo = new Span("🎟 " + ticketsCount + " ingresso(s) emitido(s) com sucesso!");
        tktInfo.getStyle().set("font-size", "var(--lumo-font-size-l)").set("font-weight", "600")
               .set("display", "block").set("margin-bottom", "var(--lumo-space-l)");

        Button viewTickets = new Button("Ver Meus Ingressos", VaadinIcon.TICKET.create());
        viewTickets.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        viewTickets.addClickListener(e -> UI.getCurrent().navigate("tickets"));

        Button home = new Button("Voltar ao Catálogo", VaadinIcon.HOME.create());
        home.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        home.addClickListener(e -> UI.getCurrent().navigate(""));

        card.add(icon, congrats, tktInfo, viewTickets, home);
        successSection.add(card);
    }

    private List<Long> parseSeatIds(QueryParameters qp) {
        List<Long> ids = new ArrayList<>();

        @SuppressWarnings("unchecked")
        List<Long> fromSession = (List<Long>) VaadinSession.getCurrent().getAttribute(SESSION_SEAT_IDS);
        if (fromSession != null) {
            for (Long id : fromSession) {
                if (id != null && id > 0) ids.add(id);
            }
            VaadinSession.getCurrent().setAttribute(SESSION_SEAT_IDS, null);
        }

        List<String> seatParams = qp.getParameters().get("seats");
        if (seatParams != null) {
            for (String param : seatParams) {
                if (param == null || param.isBlank()) continue;
                for (String part : param.split(",")) {
                    try {
                        long id = Long.parseLong(part.trim());
                        if (id > 0 && !ids.contains(id)) ids.add(id);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return ids;
    }

    private long longParam(QueryParameters qp, String name) {
        try { return Long.parseLong(qp.getParameters().getOrDefault(name, List.of("0")).get(0)); }
        catch (Exception e) { return 0; }
    }

    private double doubleParam(QueryParameters qp, String name) {
        try { return Double.parseDouble(qp.getParameters().getOrDefault(name, List.of("0")).get(0)); }
        catch (Exception e) { return 0; }
    }
}
