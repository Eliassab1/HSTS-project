package com.testify.client;

import com.testify.common.GradeOverride;
import com.testify.common.PendingGrade;
import com.testify.common.User;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Consumer;

/**
 * Teacher – Grade Approval screen (lilac theme, sidebar navigation).
 *
 * Computerised grading stops at AWAITING_APPROVAL; nothing reaches the
 * student until a teacher acts here. Approve releases the computed grade
 * unchanged; Override replaces it, and the server refuses an override with
 * no justification.
 */
public class GradeApprovalView extends HBox {

    private final Consumer<PendingGrade> approveAction;
    private final Consumer<GradeOverride> overrideAction;
    private final Runnable refreshAction;
    private final Runnable backAction;

    private final ObservableList<PendingGrade> pendingGrades = FXCollections.observableArrayList();
    private TableView<PendingGrade> gradeTable;
    private Label statusLabel;

    public GradeApprovalView(
            User teacher,
            VBox sidebar,
            Consumer<PendingGrade> approveAction,
            Consumer<GradeOverride> overrideAction,
            Runnable refreshAction,
            Runnable backAction
    ) {
        if (approveAction == null)  throw new IllegalArgumentException("Approve action cannot be null.");
        if (overrideAction == null) throw new IllegalArgumentException("Override action cannot be null.");
        if (refreshAction == null)  throw new IllegalArgumentException("Refresh action cannot be null.");
        if (backAction == null)     throw new IllegalArgumentException("Back action cannot be null.");

        this.approveAction  = approveAction;
        this.overrideAction = overrideAction;
        this.refreshAction  = refreshAction;
        this.backAction     = backAction;

        buildUI(teacher, sidebar);
    }

    // ── Build ─────────────────────────────────────────────────────────────────

    private void buildUI(User teacher, VBox sidebar) {
        getStyleClass().add("login-background");
        setPadding(new Insets(22));
        setSpacing(16);

        VBox main = buildMain(teacher);
        HBox.setHgrow(main, Priority.ALWAYS);
        getChildren().addAll(sidebar, main);
    }

    // ── Main content ──────────────────────────────────────────────────────────

    private VBox buildMain(User teacher) {

        TextField searchBox = new TextField();
        searchBox.setPromptText("Search anything...");
        searchBox.getStyleClass().add("search-field");
        HBox topBar = new HBox(searchBox);
        topBar.getStyleClass().add("top-bar");
        topBar.setAlignment(Pos.CENTER_LEFT);

        String name = teacher != null && teacher.getFullName() != null && !teacher.getFullName().isBlank()
                ? teacher.getFullName() : (teacher != null ? teacher.getUsername() : "Teacher");
        Label title = new Label("Grade Approval — " + name);
        title.getStyleClass().add("welcome-label");

        Label hint = new Label(
                "Grades below were calculated automatically. Students cannot see them "
                        + "until you approve or change them.");
        hint.setWrapText(true);
        hint.setStyle("-fx-text-fill: -hsts-muted;");

        gradeTable = buildTable();
        gradeTable.setItems(pendingGrades);
        VBox.setVgrow(gradeTable, Priority.ALWAYS);

        Button approveBtn = new Button("✓  Approve");
        approveBtn.getStyleClass().add("success-button");
        approveBtn.setPrefWidth(150);
        approveBtn.setOnAction(e -> {
            PendingGrade selected = requireSelection();
            if (selected != null) approveAction.accept(selected);
        });

        Button overrideBtn = new Button("✎  Change Grade");
        overrideBtn.getStyleClass().add("primary-button");
        overrideBtn.setPrefWidth(170);
        overrideBtn.setOnAction(e -> {
            PendingGrade selected = requireSelection();
            if (selected != null) promptForOverride(selected);
        });

        Button refreshBtn = new Button("Refresh");
        refreshBtn.getStyleClass().add("secondary-button");
        refreshBtn.setOnAction(e -> {
            setStatusMessage("Loading grades awaiting approval...");
            refreshAction.run();
        });

        Button backBtn = new Button("Back to Dashboard");
        backBtn.getStyleClass().add("secondary-button");
        backBtn.setOnAction(e -> backAction.run());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        statusLabel = new Label();
        statusLabel.getStyleClass().add("status-label");
        statusLabel.setWrapText(true);

        HBox actions = new HBox(12, approveBtn, overrideBtn, refreshBtn, spacer, backBtn);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox main = new VBox(16, topBar, title, hint, gradeTable, actions, statusLabel);
        main.setPadding(new Insets(0));
        return main;
    }

    // ── Table ─────────────────────────────────────────────────────────────────

    private TableView<PendingGrade> buildTable() {
        TableView<PendingGrade> table = new TableView<>();
        table.setPlaceholder(new Label("No grades are awaiting your approval."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<PendingGrade, Integer> idCol = new TableColumn<>("Submission");
        idCol.setCellValueFactory(new PropertyValueFactory<>("submissionId"));
        idCol.setPrefWidth(100);

        TableColumn<PendingGrade, String> examCol = new TableColumn<>("Exam");
        examCol.setCellValueFactory(new PropertyValueFactory<>("examTitle"));
        examCol.setPrefWidth(260);

        TableColumn<PendingGrade, String> studentCol = new TableColumn<>("Student");
        studentCol.setCellValueFactory(new PropertyValueFactory<>("studentName"));
        studentCol.setPrefWidth(200);

        TableColumn<PendingGrade, Double> scoreCol = new TableColumn<>("Computed Grade");
        scoreCol.setCellValueFactory(new PropertyValueFactory<>("score"));
        scoreCol.setPrefWidth(140);

        TableColumn<PendingGrade, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setPrefWidth(150);

        table.getColumns().addAll(idCol, examCol, studentCol, scoreCol, statusCol);
        return table;
    }

    // ── Override dialog ───────────────────────────────────────────────────────

    /**
     * Collects a new grade and the justification for it. Both are required —
     * the dialog refuses to submit without them, and the server refuses again
     * on arrival.
     */
    private void promptForOverride(PendingGrade selected) {

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Change Grade");
        dialog.setHeaderText("Change " + selected.getStudentName() + "'s grade for \""
                + selected.getExamTitle() + "\"");

        ButtonType saveType = new ButtonType("Save Grade", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        TextField scoreField = new TextField(String.format("%.1f", selected.getScore()));
        scoreField.setPromptText("0 - 100");

        TextArea reasonArea = new TextArea();
        reasonArea.setPromptText("Why is this grade being changed?");
        reasonArea.setPrefRowCount(4);
        reasonArea.setWrapText(true);

        Label computed = new Label(String.format(
                "Computer-calculated grade: %.1f (kept on record)", selected.getScore()));

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(14));
        grid.add(computed, 0, 0, 2, 1);
        grid.add(new Label("New grade:"), 0, 1);
        grid.add(scoreField, 1, 1);
        grid.add(new Label("Justification:"), 0, 2);
        grid.add(reasonArea, 1, 2);
        dialog.getDialogPane().setContent(grid);

        dialog.showAndWait().ifPresent(result -> {
            if (result != saveType) return;

            double newScore;
            try {
                newScore = Double.parseDouble(scoreField.getText().trim());
            } catch (NumberFormatException ex) {
                setStatusMessage("Enter the new grade as a number between 0 and 100.");
                return;
            }
            if (newScore < 0 || newScore > 100) {
                setStatusMessage("The new grade must be between 0 and 100.");
                return;
            }
            String reason = reasonArea.getText() == null ? "" : reasonArea.getText().trim();
            if (reason.isBlank()) {
                setStatusMessage("A justification is required to change a grade.");
                return;
            }
            overrideAction.accept(
                    new GradeOverride(selected.getSubmissionId(), newScore, reason));
        });
    }

    /** Returns the selected row, or null after reporting that nothing is selected. */
    private PendingGrade requireSelection() {
        PendingGrade selected = gradeTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            setStatusMessage("Select a submission first.");
        }
        return selected;
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    public void setPendingGrades(List<PendingGrade> grades) {
        pendingGrades.setAll(grades == null ? List.of() : grades);
        setStatusMessage("Showing " + pendingGrades.size() + " grade(s) awaiting approval.");
    }

    /** Drops a row once its grade has been released to the student. */
    public void removeGrade(int submissionId) {
        pendingGrades.removeIf(g -> g.getSubmissionId() == submissionId);
    }

    public void setStatusMessage(String message) {
        if (statusLabel != null) statusLabel.setText(message == null ? "" : message);
    }
}
