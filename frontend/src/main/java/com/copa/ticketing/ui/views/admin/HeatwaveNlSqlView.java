package com.copa.ticketing.ui.views.admin;

import com.copa.ticketing.ui.client.BackendClient;
import com.copa.ticketing.ui.client.dto.HeatwaveNlSqlResponseDto;
import com.copa.ticketing.ui.layout.MainLayout;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Route(value = "admin/nl-sql", layout = MainLayout.class)
@PageTitle("HeatWave NL_SQL | Copa 2026 Admin")
@RolesAllowed("ADMIN")
public class HeatwaveNlSqlView extends VerticalLayout {

    private static final Executor ASYNC = Executors.newVirtualThreadPerTaskExecutor();

    private static final List<PresetQuestion> PRESETS = List.of(
            new PresetQuestion("receita_total", "Receita paga agora",
                    "Qual é a receita total paga, quantos pedidos foram pagos e quantos ingressos foram emitidos?",
                    List.of("vw_hw_realtime_executive_dashboard")),
            new PresetQuestion("jogos_receita", "Jogos que mais vendem",
                    "Quais são os 10 jogos com maior receita paga, mostrando jogo, estádio, cidade, ocupação, ingressos emitidos e receita?",
                    List.of("vw_hw_match_business_scorecard")),
            new PresetQuestion("ocupacao_jogos", "Maior ocupação",
                    "Quais são os 10 jogos com maior ocupação considerando assentos reservados e vendidos?",
                    List.of("vw_hw_match_business_scorecard")),
            new PresetQuestion("pendencia_pagamento", "Dinheiro pendente",
                    "Quais são os 10 jogos com maior valor pendente de pagamento e quantos pedidos ainda aguardam confirmação?",
                    List.of("vw_hw_match_business_scorecard")),
            new PresetQuestion("demanda_setores", "Setores mais quentes",
                    "Quais são os 10 setores com maior demanda por receita e ocupação?",
                    List.of("vw_hw_sector_business_demand")),
            new PresetQuestion("paises_sede", "Países sede",
                    "Qual país sede concentra mais receita paga, ingressos emitidos e ocupação confirmada?",
                    List.of("vw_hw_host_country_business_revenue")),
            new PresetQuestion("blocos_quentes", "Blocos do estádio",
                    "Quais são os 10 blocos de assentos mais ocupados, mostrando jogo, setor, bloco, vendidos, reservados e percentual de calor?",
                    List.of("vw_hw_seat_heatmap_business_live")),
            new PresetQuestion("status_pagamento", "Status de pagamento",
                    "Como está distribuído o valor total por status de pagamento?",
                    List.of("vw_hw_payment_method_business_summary"))
    );

    private final BackendClient client;
    private final TextArea question = new TextArea("Pergunta em português");
    private final Button execute = new Button("Executar", VaadinIcon.PLAY.create());
    private final Span selectedPreset = new Span("receita_total");
    private final Span status = new Span("Pronto");
    private final Div selectedViews = new Div();
    private final Div meta = new Div();
    private final Div generatedSql = new Div();
    private final Div executedSql = new Div();
    private final Grid<Map<String, Object>> grid = new Grid<>();

    private String currentQuestionId = "receita_total";

    public HeatwaveNlSqlView(BackendClient client) {
        this.client = client;
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        addClassName("nl-sql-view");

        configureQuestionInput();
        configureResultGrid();

        add(buildHeader(), buildWorkbench(), buildResults());
        selectPreset(PRESETS.get(0));
    }

    private Div buildHeader() {
        Div header = new Div();
        header.addClassName("nl-header");
        H2 title = new H2("HeatWave NL_SQL");
        Paragraph subtitle = new Paragraph("Perguntas de negócio em português sobre as views analíticas da copa_ticketing_demo.");
        header.add(title, subtitle);
        return header;
    }

    private Div buildWorkbench() {
        Div workbench = new Div();
        workbench.addClassName("nl-workbench");

        Div presets = new Div();
        presets.addClassName("nl-preset-grid");
        for (PresetQuestion preset : PRESETS) {
            Button button = new Button(preset.title(), VaadinIcon.SEARCH.create());
            button.addClassName("nl-preset-button");
            button.addClickListener(e -> selectPreset(preset));
            presets.add(button);
        }

        Div editor = new Div();
        editor.addClassName("nl-editor");

        HorizontalLayout context = new HorizontalLayout(
                metaPill("Question ID", selectedPreset),
                metaPill("Execução", status)
        );
        context.addClassName("nl-context");
        context.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        context.setPadding(false);
        context.setSpacing(true);

        selectedViews.addClassName("nl-view-chips");

        execute.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        execute.addClickListener(e -> executeQuestion());

        Button clear = new Button("Limpar", VaadinIcon.CLOSE_SMALL.create());
        clear.addClickListener(e -> {
            currentQuestionId = null;
            selectedPreset.setText("personalizada");
            question.clear();
            renderViewChips(List.of());
            clearResults();
        });

        HorizontalLayout actions = new HorizontalLayout(execute, clear);
        actions.addClassName("nl-actions");
        actions.setPadding(false);

        editor.add(context, question, selectedViews, actions);
        workbench.add(presets, editor);
        return workbench;
    }

    private Div buildResults() {
        Div results = new Div();
        results.addClassName("nl-results");

        meta.addClassName("nl-meta-grid");
        generatedSql.addClassName("nl-sql-code");
        executedSql.addClassName("nl-sql-code");

        Div sqlPanels = new Div();
        sqlPanels.addClassName("nl-sql-panels");
        sqlPanels.add(sqlPanel("SQL gerado", generatedSql), sqlPanel("SQL executado", executedSql));

        results.add(meta, sqlPanels, grid);
        return results;
    }

    private Div sqlPanel(String title, Div code) {
        Div panel = new Div();
        panel.addClassName("nl-sql-panel");
        panel.add(new H3(title), code);
        return panel;
    }

    private Div metaPill(String label, Span value) {
        Div pill = new Div();
        pill.addClassName("nl-pill");
        Span lbl = new Span(label);
        lbl.addClassName("nl-pill-label");
        pill.add(lbl, value);
        return pill;
    }

    private void configureQuestionInput() {
        question.setWidthFull();
        question.setMaxLength(500);
        question.setMinHeight("130px");
        question.setClearButtonVisible(true);
        question.setValueChangeMode(com.vaadin.flow.data.value.ValueChangeMode.EAGER);
        question.setHelperText("0/500");
        question.addValueChangeListener(e -> {
            String value = e.getValue() != null ? e.getValue() : "";
            question.setHelperText(value.length() + "/500");
            PresetQuestion preset = presetById(currentQuestionId);
            if (preset != null && !preset.question().equals(value)) {
                currentQuestionId = null;
                selectedPreset.setText("personalizada");
                renderViewChips(List.of());
            }
        });
    }

    private void configureResultGrid() {
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_NO_BORDER);
        grid.setHeight("420px");
        grid.setWidthFull();
    }

    private void selectPreset(PresetQuestion preset) {
        currentQuestionId = preset.id();
        selectedPreset.setText(preset.id());
        question.setValue(preset.question());
        renderViewChips(preset.views());
        clearResults();
    }

    private void executeQuestion() {
        String value = question.getValue() != null ? question.getValue().trim() : "";
        if (value.isBlank()) {
            Notification.show("Informe uma pergunta.", 3000, Notification.Position.TOP_END)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }
        if (value.length() > 500) {
            Notification.show("A pergunta deve ter no máximo 500 caracteres.", 3000, Notification.Position.TOP_END)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }

        UI ui = UI.getCurrent();
        String questionId = currentQuestionId;
        execute.setEnabled(false);
        status.setText("Executando NL_SQL");
        clearResults();

        CompletableFuture.supplyAsync(() -> client.askHeatwaveNlSql(value, questionId), ASYNC)
                .whenComplete((response, error) -> {
                    if (ui == null) {
                        return;
                    }
                    ui.access(() -> {
                        execute.setEnabled(true);
                        if (error != null) {
                            status.setText("Erro");
                            String message = error.getCause() != null ? error.getCause().getMessage() : error.getMessage();
                            Notification.show(message, 6000, Notification.Position.TOP_END)
                                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
                            return;
                        }
                        renderResponse(response);
                    });
                });
    }

    private void renderResponse(HeatwaveNlSqlResponseDto response) {
        status.setText(response.rowCount() + " linhas");
        selectedPreset.setText(response.questionId() != null ? response.questionId() : "personalizada");
        renderViewChips(response.consideredTables() != null ? response.consideredTables() : List.of());

        meta.removeAll();
        meta.add(
                metric("Modelo solicitado", response.requestedModelId()),
                metric("Modelo usado", response.modelId()),
                metric("Hint", response.forcedHint()),
                metric("Linhas", String.valueOf(response.rowCount()))
        );

        setCode(generatedSql, response.generatedSql());
        setCode(executedSql, response.executedSql());
        renderGrid(response);
    }

    private Div metric(String label, String value) {
        Div item = new Div();
        item.addClassName("nl-metric");
        Span lbl = new Span(label);
        Span val = new Span(value != null ? value : "-");
        item.add(lbl, val);
        return item;
    }

    private void setCode(Div target, String sql) {
        target.removeAll();
        target.setText(sql != null ? sql : "");
    }

    private void renderGrid(HeatwaveNlSqlResponseDto response) {
        grid.removeAllColumns();
        List<String> columns = response.columns() != null ? response.columns() : List.of();
        List<Map<String, Object>> rows = response.rows() != null ? response.rows() : List.of();
        for (String column : columns) {
            grid.addColumn(row -> formatCell(row.get(column)))
                    .setHeader(column)
                    .setAutoWidth(true)
                    .setResizable(true);
        }
        grid.setItems(rows);
    }

    private void renderViewChips(List<String> views) {
        selectedViews.removeAll();
        if (views == null || views.isEmpty()) {
            selectedViews.add(chip("todas as views autorizadas"));
            return;
        }
        views.forEach(view -> selectedViews.add(chip(view)));
    }

    private Span chip(String text) {
        Span chip = new Span(text);
        chip.addClassName("nl-chip");
        return chip;
    }

    private void clearResults() {
        meta.removeAll();
        generatedSql.removeAll();
        executedSql.removeAll();
        grid.removeAllColumns();
        grid.setItems(List.of());
    }

    private static String formatCell(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Map<?, ?> map) {
            return map.toString();
        }
        if (value instanceof List<?> list) {
            return list.toString();
        }
        return Objects.toString(value, "");
    }

    private static PresetQuestion presetById(String questionId) {
        if (questionId == null) {
            return null;
        }
        return PRESETS.stream()
                .filter(preset -> preset.id().equals(questionId))
                .findFirst()
                .orElse(null);
    }

    private record PresetQuestion(String id, String title, String question, List<String> views) {}
}
