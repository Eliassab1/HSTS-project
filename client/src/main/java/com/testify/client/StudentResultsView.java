package com.testify.client;

import com.testify.common.ExamResult;
import com.testify.common.User;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Consumer;

/**
 * Student Grades / Results screen – lilac theme (Page 3 of UI spec).
 * Sidebar navigation + stat cards + results table.
 */
public class StudentResultsView extends HBox {

    private final User currentStudent;
    private final Runnable loadResultsAction;
    private final Consumer<ExamResult> reviewAction;
    private final Runnable backAction;

    private final ObservableList<ExamResult> results = FXCollections.observableArrayList();

    // Stat card labels (updated after data loads)
    private Label avgLabel;
    private Label highLabel;
    private Label lowLabel;
    private Label coursesLabel;

    private TableView<ExamResult> resultsTable;
    private Label statusLabel;

    public StudentResultsView(
            User currentStudent,
            VBox sidebar,
            List<ExamResult> initialResults,
            Runnable loadResultsAction,
            Consumer<ExamResult> reviewAction,
            Runnable backAction
    ) {
        if (currentStudent == null)   throw new IllegalArgumentException("Current student cannot be null.");
        if (loadResultsAction == null) throw new IllegalArgumentException("Load results action cannot be null.");
        if (reviewAction == null)      throw new IllegalArgumentException("Review action cannot be null.");
        if (backAction == null)        throw new IllegalArgumentException("Back action cannot be null.");

        this.currentStudent    = currentStudent;
        this.loadResultsAction = loadResultsAction;
        this.reviewAction      = reviewAction;
        this.backAction        = backAction;

        if (initialResults != null) results.addAll(initialResults);
        buildUI(sidebar);
        refreshStatCards();
    }

    // ── Build ─────────────────────────────────────────────────────────────────

    private void buildUI(VBox sidebar) {
        getStyleClass().add("login-background");
        setPadding(new Insets(22));
        setSpacing(16);

        VBox main = buildMain();
        HBox.setHgrow(main, Priority.ALWAYS);
        getChildren().addAll(sidebar, main);
    }

    // ── Main content ──────────────────────────────────────────────────────────

    private VBox buildMain() {

        // Search bar
        TextField searchBox = new TextField();
        searchBox.setPromptText("Search anything...");
        searchBox.getStyleClass().add("search-field");
        HBox topBar = new HBox(searchBox);
        topBar.getStyleClass().add("top-bar");
        topBar.setAlignment(Pos.CENTER_LEFT);

        // Page title
        String name = currentStudent.getFullName() != null && !currentStudent.getFullName().isBlank()
                ? currentStudent.getFullName() : currentStudent.getUsername();
        Label title = new Label("My Grades — " + name);
        title.getStyleClass().add("welcome-label");

        // Stat cards
        avgLabel     = new Label("—");  avgLabel.getStyleClass().add("stat-value");
        highLabel    = new Label("—");  highLabel.getStyleClass().add("stat-value");
        lowLabel     = new Label("—");  lowLabel.getStyleClass().add("stat-value");
        coursesLabel = new Label("—");  coursesLabel.getStyleClass().add("stat-value");

        HBox statsRow = new HBox(14,
                wrapStatCard("Average Score", avgLabel,     "all exams"),
                wrapStatCard("Highest",       highLabel,    "best result"),
                wrapStatCard("Lowest",        lowLabel,     "needs work"),
                wrapStatCard("Courses Taken", coursesLabel, "enrolled"));
        statsRow.setAlignment(Pos.CENTER_LEFT);

        // Table — a row is also the way into the checked exam form, so a
        // double-click opens the review the same as the button does.
        resultsTable = buildTable();
        resultsTable.setItems(results);
        resultsTable.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) openSelectedReview();
        });
        VBox.setVgrow(resultsTable, Priority.ALWAYS);

        // Buttons
        Button loadBtn = new Button("Load Results");
        loadBtn.getStyleClass().add("primary-button");
        loadBtn.setOnAction(e -> {
            setStatusMessage("Loading results...");
            loadResultsAction.run();
        });

        Button reviewBtn = new Button("View Checked Exam");
        reviewBtn.getStyleClass().add("primary-button");
        reviewBtn.setOnAction(e -> openSelectedReview());

        Button backBtn = new Button("Back to Dashboard");
        backBtn.getStyleClass().add("secondary-button");
        backBtn.setOnAction(e -> backAction.run());

        statusLabel = new Label();
        statusLabel.getStyleClass().add("status-label");
        statusLabel.setWrapText(true);

        HBox buttons = new HBox(12, loadBtn, reviewBtn, backBtn);
        buttons.setAlignment(Pos.CENTER_LEFT);

        VBox main = new VBox(16, topBar, title, statsRow, resultsTable, buttons, statusLabel);
        main.setPadding(new Insets(0));
        return main;
    }

    // ── Table ─────────────────────────────────────────────────────────────────

    private TableView<ExamResult> buildTable() {
        TableView<ExamResult> table = new TableView<>();
        table.setPlaceholder(new Label("No exam results loaded yet."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<ExamResult, Integer> idCol = col("ID", "resultId", 70);
        TableColumn<ExamResult, String>  titleCol = col("Exam Title", "examTitle", 220);
        TableColumn<ExamResult, String>  dateCol  = col("Date", "submissionDate", 150);
        TableColumn<ExamResult, Double>  gradeCol = col("Grade", "grade", 90);
        TableColumn<ExamResult, Double>  maxCol   = col("Max", "maximumGrade", 80);

        TableColumn<ExamResult, Double> pctCol = new TableColumn<>("Score %");
        pctCol.setPrefWidth(100);
        pctCol.setCellValueFactory(cd ->
                new SimpleDoubleProperty(cd.getValue().calculatePercentage()).asObject());

        TableColumn<ExamResult, String> statusCol = col("Status", "status", 110);

        table.getColumns().addAll(idCol, titleCol, dateCol, gradeCol, maxCol, pctCol, statusCol);
        return table;
    }

    @SuppressWarnings("unchecked")
    private <T> TableColumn<ExamResult, T> col(String header, String property, double width) {
        TableColumn<ExamResult, T> c = new TableColumn<>(header);
        c.setCellValueFactory(new PropertyValueFactory<>(property));
        c.setPrefWidth(width);
        return c;
    }

    /**
     * Opens the checked exam form for the selected result. Only the request
     * is made here — the server still decides whether the submission may be
     * shown at all.
     */
    private void openSelectedReview() {
        ExamResult selected = resultsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            setStatusMessage("Select a result first.");
            return;
        }
        setStatusMessage("Opening checked exam...");
        reviewAction.accept(selected);
    }

    // ── Stat card helper ──────────────────────────────────────────────────────

    private VBox wrapStatCard(String label, Label valueLabel, String sub) {
        Label lbl = new Label(label); lbl.getStyleClass().add("stat-label");
        Label sl  = new Label(sub);   sl.getStyleClass().add("stat-sub");
        Label dot = new Label("●");
        dot.setStyle("-fx-font-size: 26px; -fx-text-fill: #DDD6FE;");
        HBox top = new HBox(10, dot, new VBox(2, lbl, valueLabel, sl));
        top.setAlignment(Pos.CENTER_LEFT);
        VBox card = new VBox(top);
        card.getStyleClass().add("stat-card");
        return card;
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    public void setResults(List<ExamResult> newResults) {
        results.clear();
        if (newResults != null) results.addAll(newResults);
        if (resultsTable != null) resultsTable.refresh();
        refreshStatCards();
        setStatusMessage(results.size() + " result(s) loaded.");
    }

    public void setStatusMessage(String message) {
        if (statusLabel != null) statusLabel.setText(message == null ? "" : message);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private void refreshStatCards() {
        if (avgLabel == null) return;
        if (results.isEmpty()) {
            avgLabel.setText("—"); highLabel.setText("—");
            lowLabel.setText("—"); coursesLabel.setText("0");
            return;
        }

        double sum = 0, max = Double.MIN_VALUE, min = Double.MAX_VALUE;
        for (ExamResult r : results) {
            double pct = r.calculatePercentage();
            sum += pct;
            if (pct > max) max = pct;
            if (pct < min) min = pct;
        }
        double avg = sum / results.size();

        avgLabel.setText(String.format("%.1f%%", avg));
        highLabel.setText(String.format("%.1f%%", max));
        lowLabel.setText(String.format("%.1f%%", min));
        coursesLabel.setText(String.valueOf(results.size()));
    }
}
