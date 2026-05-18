package com.copa.ticketing.ui.views.admin;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route("admin-login")
@PageTitle("Login Admin | Copa 2026")
@AnonymousAllowed
public class AdminLoginView extends VerticalLayout implements BeforeEnterObserver {

    private final LoginForm loginForm = new LoginForm();

    public AdminLoginView() {
        setSizeFull();
        setAlignItems(FlexComponent.Alignment.CENTER);
        setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        getStyle().set("background", "var(--copa-gradient)");

        Div card = new Div();
        card.getStyle()
            .set("background", "var(--lumo-base-color)")
            .set("border-radius", "var(--lumo-border-radius-l)")
            .set("padding", "var(--lumo-space-xl)")
            .set("box-shadow", "var(--lumo-box-shadow-l)")
            .set("max-width", "420px")
            .set("width", "100%");

        Span icon = new Span("⚽");
        icon.getStyle().set("font-size", "3rem").set("display", "block").set("text-align", "center");

        H1 title = new H1("Copa Ticketing");
        title.getStyle()
             .set("color", "var(--copa-blue)")
             .set("font-size", "var(--lumo-font-size-2xl)")
             .set("font-weight", "900")
             .set("text-align", "center")
             .set("margin", "var(--lumo-space-s) 0 var(--lumo-space-xs) 0");

        Paragraph sub = new Paragraph("Painel Administrativo");
        sub.getStyle().set("color", "var(--lumo-secondary-text-color)").set("text-align", "center")
           .set("margin", "0 0 var(--lumo-space-l) 0");

        LoginI18n i18n = LoginI18n.createDefault();
        i18n.getForm().setTitle("");
        i18n.getForm().setUsername("Usuário");
        i18n.getForm().setPassword("Senha");
        i18n.getForm().setSubmit("Entrar");
        i18n.getErrorMessage().setTitle("Credenciais inválidas");
        i18n.getErrorMessage().setMessage("Verifique usuário e senha.");
        loginForm.setI18n(i18n);
        loginForm.setAction("admin-login");
        loginForm.getStyle().set("width", "100%");

        card.add(icon, title, sub, loginForm);
        add(card);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (event.getLocation().getQueryParameters().getParameters().containsKey("error")) {
            loginForm.setError(true);
        }
    }
}
