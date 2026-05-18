package com.copa.ticketing.ui.layout;

import com.copa.ticketing.ui.views.admin.DashboardView;
import com.copa.ticketing.ui.views.admin.InventoryView;
import com.copa.ticketing.ui.views.admin.OrdersView;
import com.copa.ticketing.ui.views.catalog.CatalogView;
import com.copa.ticketing.ui.views.tickets.MyTicketsView;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.theme.lumo.LumoUtility;

@AnonymousAllowed
public class MainLayout extends AppLayout {

    public MainLayout() {
        setPrimarySection(Section.DRAWER);
        addHeaderContent();
        addDrawerContent();
    }

    private void addHeaderContent() {
        DrawerToggle toggle = new DrawerToggle();
        toggle.addClassName("app-header-toggle");
        toggle.setAriaLabel("Abrir menu");

        Icon logo = VaadinIcon.MEDAL.create();
        logo.addClassName("app-header-logo");

        H1 title = new H1("Copa 2026");
        title.addClassNames("app-header-title", LumoUtility.Margin.NONE);

        Span subtitle = new Span("Ingressos & partidas");
        subtitle.addClassName("app-header-subtitle");

        Div brandText = new Div(title, subtitle);
        brandText.addClassName("app-header-brand-text");

        Div brand = new Div(logo, brandText);
        brand.addClassName("app-header-brand");

        Span badge = new Span("FIFA World Cup 2026");
        badge.addClassName("app-header-badge");

        HorizontalLayout left = new HorizontalLayout(toggle, brand);
        left.addClassName("app-header-left");
        left.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        left.setSpacing(false);

        HorizontalLayout header = new HorizontalLayout(left, badge);
        header.addClassName("app-header");
        header.setWidthFull();
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        header.setPadding(false);
        header.setSpacing(false);

        addToNavbar(true, header);
    }

    private void addDrawerContent() {
        Span appName = new Span("Copa Ticketing");
        appName.addClassNames(LumoUtility.FontWeight.SEMIBOLD, LumoUtility.FontSize.MEDIUM);
        appName.getStyle().set("color", "var(--lumo-secondary-text-color)");

        Header header = new Header(appName);
        header.addClassNames(LumoUtility.Display.FLEX, LumoUtility.AlignItems.CENTER,
                LumoUtility.Padding.Horizontal.MEDIUM, LumoUtility.BoxSizing.BORDER);
        header.setHeight("var(--lumo-size-xl)");

        Scroller scroller = new Scroller(createNavigation());

        Hr separator = new Hr();
        Span adminLabel = new Span("Administração");
        adminLabel.addClassNames(LumoUtility.FontSize.XXSMALL, LumoUtility.FontWeight.SEMIBOLD,
                LumoUtility.TextColor.SECONDARY, LumoUtility.Padding.Horizontal.MEDIUM);
        adminLabel.getStyle().set("text-transform", "uppercase").set("letter-spacing", "1px");

        Scroller adminScroller = new Scroller(createAdminNavigation());

        addToDrawer(header, scroller, separator, adminLabel, adminScroller);
    }

    private SideNav createNavigation() {
        SideNav nav = new SideNav();
        nav.addItem(new SideNavItem("Partidas", CatalogView.class, VaadinIcon.CALENDAR.create()));
        nav.addItem(new SideNavItem("Meus Ingressos", MyTicketsView.class, VaadinIcon.TICKET.create()));
        return nav;
    }

    private SideNav createAdminNavigation() {
        SideNav nav = new SideNav();
        nav.addItem(new SideNavItem("Dashboard", DashboardView.class, VaadinIcon.DASHBOARD.create()));
        nav.addItem(new SideNavItem("Pedidos", OrdersView.class, VaadinIcon.LIST.create()));
        nav.addItem(new SideNavItem("Estoque", InventoryView.class, VaadinIcon.CHART.create()));
        return nav;
    }
}
