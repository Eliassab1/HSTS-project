package com.testify.client;

import com.testify.common.Exam;
import com.testify.common.ExamResult;
import com.testify.common.User;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

import java.util.List;
import java.util.function.Consumer;

/**
 * Student Dashboard – lilac theme (Page 2 of the UI spec).
 * Sidebar navigation + stat cards + performance chart + quick-action buttons.
 */
public class StudentDashboardView extends HBox {

    private final User currentStudent;
    private final Runnable activeExamsAction;
    private Runnable botAction = () -> {};
    private String botLockedReason;
    private final Consumer<Exam> startExamAction;
    private final Runnable resultsAction;
    private final Runnable settingsAction;

    private final ObservableList<Exam> availableExams = FXCollections.observableArrayList();

    private Label statusLabel;
    private VBox upcomingCard;
    private VBox chartCard;

    // Stat card value labels — updated by setStudentStats()
    private Label gpaLabel;
    private Label successLabel;
    private Label completedLabel;
    private Label upcomingStatLabel;

    /**
     * @param botAction opens the learning bot chat
     * @param botLockedReason why the bot is unavailable right now, or null
     *                        when it is available. Purely a courtesy: the
     *                        server refuses ASK_BOT mid-exam regardless of
     *                        what this screen shows, and remains the only
     *                        thing actually enforcing the lockout (spec 14).
     */
    public StudentDashboardView(
            User currentStudent,
            VBox sidebar,
            List<Exam> exams,
            Runnable activeExamsAction,
            Consumer<Exam> startExamAction,
            Runnable resultsAction,
            Runnable settingsAction,
            Runnable botAction,
            String botLockedReason
    ) {
        this.currentStudent   = currentStudent;
        this.activeExamsAction = activeExamsAction;
        this.startExamAction  = startExamAction;
        this.resultsAction   = resultsAction;
        this.settingsAction  = settingsAction != null ? settingsAction : () -> {};
        this.botAction       = botAction != null ? botAction : () -> {};
        this.botLockedReason = botLockedReason;

        if (exams != null) availableExams.addAll(exams);
        buildUI(sidebar);
    }

    // ── Build ─────────────────────────────────────────────────────────────────

    private void buildUI(VBox sidebar) {
        getStyleClass().add("login-background");
        setPadding(new Insets(22));
        setSpacing(16);

        VBox mainContent = buildMainContent();
        HBox.setHgrow(mainContent, Priority.ALWAYS);
        getChildren().addAll(sidebar, mainContent);
    }

    // ── Main content ──────────────────────────────────────────────────────────

    private VBox buildMainContent() {

        // Search bar
        TextField searchBox = new TextField();
        searchBox.setPromptText("Search anything...");
        searchBox.getStyleClass().add("search-field");
        HBox topBar = new HBox(searchBox);
        topBar.getStyleClass().add("top-bar");
        topBar.setAlignment(Pos.CENTER_LEFT);

        // Welcome
        String name = currentStudent.getFullName() != null
                && !currentStudent.getFullName().isBlank()
                ? currentStudent.getFullName()
                : currentStudent.getUsername();
        Label welcome = new Label("Welcome back, " + name + " 👋");
        welcome.getStyleClass().add("welcome-label");

        // Stat cards
        gpaLabel         = new Label("—");
        successLabel     = new Label("—%");
        completedLabel   = new Label("—");
        upcomingStatLabel = new Label("—");
        HBox statsRow = new HBox(14,
                statCard("GPA",         gpaLabel,         "all time"),
                statCard("Success Rate", successLabel,     "passed"),
                statCard("Completed",   completedLabel,   "exams done"),
                statCard("Upcoming",    upcomingStatLabel, "this week"));
        statsRow.setAlignment(Pos.CENTER_LEFT);

        // Chart (populated by setPerformanceChart() once results arrive)
        chartCard = new VBox(8);
        chartCard.getStyleClass().add("section-card");
        Label chartTitle = new Label("Performance / analytics chart");
        chartTitle.getStyleClass().add("section-title");
        Label chartPlaceholder = new Label("Loading performance data...");
        chartPlaceholder.setStyle("-fx-font-size: 12px; -fx-text-fill: -hsts-muted;");
        chartCard.getChildren().addAll(chartTitle, chartPlaceholder);
        HBox.setHgrow(chartCard, Priority.ALWAYS);

        // Upcoming exams card (stores reference so setAvailableExams can repopulate it)
        upcomingCard = new VBox(6);
        upcomingCard.getStyleClass().add("section-card");
        upcomingCard.setPrefWidth(240);
        Label upcomingTitle = new Label("Active Exams");
        upcomingTitle.getStyleClass().add("section-title");
        statusLabel = new Label("Loading available exams...");
        statusLabel.setWrapText(true);
        statusLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: -hsts-muted;");
        upcomingCard.getChildren().addAll(upcomingTitle, statusLabel);

        HBox midRow = new HBox(14, chartCard, upcomingCard);
        midRow.setAlignment(Pos.TOP_LEFT);

        // Quick-action buttons
        Button gradesBtn  = quickBtn("Grades",       "go to grades",       "quick-btn-pink",   resultsAction);
        Button examsBtn   = quickBtn("Active Exams", "start an exam",      "quick-btn-sky",    activeExamsAction);
        Button reviewBtn  = quickBtn("Review Exams", "see solved exams",   "quick-btn-green",  resultsAction);

        // The bot entry point disables itself while an exam is open. This is
        // courtesy only -- it explains rather than enforces. A student who
        // reaches ASK_BOT another way is refused by the server.
        boolean botLocked = botLockedReason != null && !botLockedReason.isBlank();
        Button botBtn = quickBtn("Learning Bot",
                botLocked ? botLockedReason : "ask about your courses",
                "quick-btn-peach", botAction);
        botBtn.setDisable(botLocked);

        Button settingsBtn = quickBtn("Settings",    "account & theme",    "quick-btn-purple", settingsAction);
        HBox quickRow = new HBox(14, gradesBtn, examsBtn, reviewBtn, botBtn, settingsBtn);
        quickRow.setAlignment(Pos.CENTER_LEFT);

        VBox main = new VBox(16, topBar, welcome, statsRow, midRow, quickRow);
        main.setPadding(new Insets(0, 0, 0, 0));
        VBox.setVgrow(midRow, Priority.ALWAYS);
        return main;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private VBox statCard(String label, String value, String sub) {
        Label val = new Label(value);
        return statCard(label, val, sub);
    }

    private VBox statCard(String label, Label valueLabel, String sub) {
        valueLabel.getStyleClass().add("stat-value");
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

    private Button quickBtn(String title, String subtitle, String styleClass, Runnable action) {
        Button btn = new Button(title + "\n" + subtitle);
        btn.getStyleClass().add(styleClass);
        btn.setWrapText(true);
        btn.setPrefWidth(175);
        btn.setOnAction(e -> action.run());
        return btn;
    }

    private Canvas buildLineChart(double[] pts, String hexColor, double w, double h) {
        return buildLineChart(pts, null, hexColor, w, h);
    }

    /**
     * Draws a line chart, optionally with one axis label printed under each
     * data point. Handles any point count &gt;= 1 — a single point is drawn
     * as a lone dot (no line) instead of being dropped.
     */
    private Canvas buildLineChart(double[] pts, String[] labels, String hexColor, double w, double h) {
        double labelAreaHeight = labels != null ? 16 : 0;
        Canvas canvas = new Canvas(w, h + labelAreaHeight);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setStroke(Color.web("#E9D5FF"));
        gc.setLineWidth(1);
        for (int i = 1; i <= 4; i++) gc.strokeLine(0, h * i / 5.0, w, h * i / 5.0);

        int n = pts.length;
        double stepX = n > 1 ? w / (n - 1.0) : 0;

        if (n > 1) {
            gc.setStroke(Color.web(hexColor));
            gc.setLineWidth(2.5);
            for (int i = 0; i < n - 1; i++) {
                double x1 = i * stepX,       y1 = h - (pts[i]     / 100.0 * (h - 10)) - 5;
                double x2 = (i + 1) * stepX, y2 = h - (pts[i + 1] / 100.0 * (h - 10)) - 5;
                gc.strokeLine(x1, y1, x2, y2);
            }
        }

        gc.setFill(Color.web(hexColor));
        for (int i = 0; i < n; i++) {
            double x = n > 1 ? i * stepX : w / 2.0;
            double y = h - (pts[i] / 100.0 * (h - 10)) - 5;
            gc.fillOval(x - 4, y - 4, 8, 8);
        }

        if (labels != null) {
            gc.setFill(Color.web("#6B7280"));
            gc.setFont(Font.font(10));
            gc.setTextAlign(TextAlignment.CENTER);
            for (int i = 0; i < n && i < labels.length; i++) {
                double x = n > 1 ? i * stepX : w / 2.0;
                gc.fillText(labels[i], x, h + 13);
            }
        }

        return canvas;
    }

    /**
     * Shortens a date string (e.g. "2026-08-06" or a full timestamp like
     * "2026-08-06 10:15:30.0") to a compact "MM-dd" label for chart axes.
     */
    private String formatShortDate(String rawDate) {
        if (rawDate == null || rawDate.length() < 10) {
            return rawDate == null ? "" : rawDate;
        }
        return rawDate.substring(5, 10);
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    public void setAvailableExams(List<Exam> exams) {
        availableExams.clear();
        if (exams != null) availableExams.addAll(exams);

        if (upcomingCard == null) return;

        // Keep title (first child) and remove old exam buttons/status
        while (upcomingCard.getChildren().size() > 1) {
            upcomingCard.getChildren().remove(1);
        }

        if (exams == null || exams.isEmpty()) {
            statusLabel = new Label("No active exams available.");
            statusLabel.setWrapText(true);
            statusLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: -hsts-muted;");
            upcomingCard.getChildren().add(statusLabel);
        } else {
            statusLabel = null;
            for (Exam exam : exams) {
                Button examBtn = new Button("▶  " + exam.getTitle());
                examBtn.setWrapText(true);
                examBtn.setMaxWidth(Double.MAX_VALUE);
                examBtn.setStyle("-fx-background-color: #F3E8FF; -fx-text-fill: #5B21B6;"
                        + " -fx-font-size: 12px; -fx-background-radius: 10px;"
                        + " -fx-padding: 8 10 8 10; -fx-cursor: hand; -fx-border-width: 0;");
                examBtn.setOnMouseEntered(e -> examBtn.setStyle(
                        "-fx-background-color: #A855F7; -fx-text-fill: white;"
                        + " -fx-font-size: 12px; -fx-background-radius: 10px;"
                        + " -fx-padding: 8 10 8 10; -fx-cursor: hand; -fx-border-width: 0;"));
                examBtn.setOnMouseExited(e -> examBtn.setStyle(
                        "-fx-background-color: #F3E8FF; -fx-text-fill: #5B21B6;"
                        + " -fx-font-size: 12px; -fx-background-radius: 10px;"
                        + " -fx-padding: 8 10 8 10; -fx-cursor: hand; -fx-border-width: 0;"));
                examBtn.setOnAction(e -> startExamAction.accept(exam));
                upcomingCard.getChildren().add(examBtn);
            }
        }
    }

    public void setStatusMessage(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message == null ? "" : message);
        }
    }

    /**
     * Renders the performance chart from the student's exam results,
     * plotting the last 10 exams taken (oldest to newest, left to right).
     * {@code results} is expected most-recent-first (server order).
     */
    public void setPerformanceChart(List<ExamResult> results) {
        if (chartCard == null) return;

        // Keep title (first child) and remove any previously rendered chart/placeholder
        while (chartCard.getChildren().size() > 1) {
            chartCard.getChildren().remove(1);
        }

        if (results == null || results.isEmpty()) {
            Label empty = new Label("No exams taken yet.");
            empty.setWrapText(true);
            empty.setStyle("-fx-font-size: 12px; -fx-text-fill: -hsts-muted;");
            chartCard.getChildren().add(empty);
            return;
        }

        int count = Math.min(10, results.size());
        double[] pts = new double[count];
        String[] labels = new String[count];
        for (int i = 0; i < count; i++) {
            // results are most-recent-first; reverse into chronological order
            ExamResult r = results.get(count - 1 - i);
            pts[i] = r.calculatePercentage();
            labels[i] = formatShortDate(r.getSubmissionDate());
        }

        chartCard.getChildren().add(buildLineChart(pts, labels, "#A855F7", 480, 150));
    }

    public void setStudentStats(List<ExamResult> results) {
        if (results == null || results.isEmpty()) {
            if (gpaLabel != null)          gpaLabel.setText("—");
            if (successLabel != null)      successLabel.setText("—%");
            if (completedLabel != null)    completedLabel.setText("0");
            if (upcomingStatLabel != null) upcomingStatLabel.setText("—");
            return;
        }
        double sum = 0;
        int passed = 0;
        for (ExamResult r : results) {
            double pct = r.calculatePercentage();
            sum += pct;
            if ("PASSED".equalsIgnoreCase(r.getStatus())) passed++;
        }
        double avg  = sum / results.size();
        double rate = (passed * 100.0) / results.size();
        if (gpaLabel != null)          gpaLabel.setText(String.format("%.1f%%", avg));
        if (successLabel != null)      successLabel.setText(String.format("%.0f%%", rate));
        if (completedLabel != null)    completedLabel.setText(String.valueOf(results.size()));
        if (upcomingStatLabel != null) upcomingStatLabel.setText("—");
    }
}
