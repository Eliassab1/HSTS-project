package com.testify.client;

import com.testify.common.Exam;
import com.testify.common.User;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Consumer;

/**
 * Admin – Approve Exams screen (lilac theme, sidebar navigation).
 */
public class ApproveExamView extends HBox {

    private final Consumer<Exam> approveAction;
    private final Consumer<Exam> rejectAction;
    private final Runnable backAction;

    private final ObservableList<Exam> pendingExams = FXCollections.observableArrayList();
    private TableView<Exam> examTable;
    private Label statusLabel;

    public ApproveExamView(
            User principal,
            VBox sidebar,
            Consumer<Exam> approveAction,
            Consumer<Exam> rejectAction,
            Runnable backAction
    ) {
        this.approveAction = approveAction;
        this.rejectAction  = rejectAction;
        this.backAction    = backAction;
        buildUI(principal, sidebar);
    }

    // ── Build ─────────────────────────────────────────────────────────────────

    private void buildUI(User principal, VBox sidebar) {
        getStyleClass().add("login-background");
        setPadding(new Insets(22));
        setSpacing(16);

        VBox main = buildMain(principal);
        HBox.setHgrow(main, Priority.ALWAYS);
        getChildren().addAll(sidebar, main);
    }

    // ── Main content ──────────────────────────────────────────────────────────

    private VBox buildMain(User principal) {

        // Search bar
        TextField searchBox = new TextField();
        searchBox.setPromptText("Search anything...");
        searchBox.getStyleClass().add("search-field");
        HBox topBar = new HBox(searchBox);
        topBar.getStyleClass().add("top-bar");
        topBar.setAlignment(Pos.CENTER_LEFT);

        // Title
        String name = principal != null && principal.getFullName() != null && !principal.getFullName().isBlank()
                ? principal.getFullName() : (principal != null ? principal.getUsername() : "Admin");
        Label title = new Label("Approve Exams — " + name + " 👋");
        title.getStyleClass().add("welcome-label");

        // Table
        examTable = buildTable();
        examTable.setItems(pendingExams);
        VBox.setVgrow(examTable, Priority.ALWAYS);

        // Buttons
        Button approveBtn = new Button("✓  Approve");
        approveBtn.getStyleClass().add("success-button");
        approveBtn.setPrefWidth(140);
        approveBtn.setOnAction(e -> {
            Exam sel = examTable.getSelectionModel().getSelectedItem();
            if (sel == null) { statusLabel.setText("Select an exam first."); return; }
            approveAction.accept(sel);
        });

        Button rejectBtn = new Button("✗  Reject");
        rejectBtn.getStyleClass().add("danger-button");
        rejectBtn.setPrefWidth(140);
        rejectBtn.setOnAction(e -> {
            Exam sel = examTable.getSelectionModel().getSelectedItem();
            if (sel == null) { statusLabel.setText("Select an exam first."); return; }

            TextInputDialog reasonDialog = new TextInputDialog();
            reasonDialog.setTitle("Reject Exam");
            reasonDialog.setHeaderText("Reject \"" + sel.getTitle() + "\"");
            reasonDialog.setContentText("Reason (shown to the teacher):");

            reasonDialog.showAndWait().ifPresentOrElse(reason -> {
                if (reason.isBlank()) {
                    statusLabel.setText("A rejection reason is required.");
                    return;
                }
                sel.setRejectionReason(reason.trim());
                rejectAction.accept(sel);
            }, () -> { /* dialog cancelled — no-op */ });
        });

        Button backBtn = new Button("Back to Dashboard");
        backBtn.getStyleClass().add("secondary-button");
        backBtn.setOnAction(e -> backAction.run());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        statusLabel = new Label();
        statusLabel.getStyleClass().add("status-label");
        statusLabel.setWrapText(true);

        HBox actions = new HBox(12, approveBtn, rejectBtn, spacer, backBtn);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox main = new VBox(16, topBar, title, examTable, actions, statusLabel);
        main.setPadding(new Insets(0));
        return main;
    }

    // ── Table ─────────────────────────────────────────────────────────────────

    private TableView<Exam> buildTable() {
        TableView<Exam> table = new TableView<>();
        table.setPlaceholder(new Label("No exams are pending approval."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Exam, Integer> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("examId"));
        idCol.setPrefWidth(60);

        TableColumn<Exam, String> titleCol = new TableColumn<>("Title");
        titleCol.setCellValueFactory(new PropertyValueFactory<>("title"));
        titleCol.setPrefWidth(260);

        TableColumn<Exam, String> courseCol = new TableColumn<>("Course");
        courseCol.setCellValueFactory(new PropertyValueFactory<>("course"));
        courseCol.setPrefWidth(160);

        TableColumn<Exam, Integer> durationCol = new TableColumn<>("Duration (min)");
        durationCol.setCellValueFactory(new PropertyValueFactory<>("durationMinutes"));
        durationCol.setPrefWidth(120);

        TableColumn<Exam, Integer> teacherCol = new TableColumn<>("Teacher ID");
        teacherCol.setCellValueFactory(new PropertyValueFactory<>("teacherId"));
        teacherCol.setPrefWidth(100);

        TableColumn<Exam, Integer> questionsCol = new TableColumn<>("Questions");
        questionsCol.setCellValueFactory(new PropertyValueFactory<>("questionCount"));
        questionsCol.setPrefWidth(100);

        table.getColumns().addAll(idCol, titleCol, courseCol, durationCol, teacherCol, questionsCol);
        return table;
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    public void setPendingExams(List<Exam> exams) {
        pendingExams.setAll(exams);
        if (statusLabel != null)
            statusLabel.setText("Showing " + exams.size() + " pending exam(s).");
    }

    public void removeExam(int examId) {
        pendingExams.removeIf(e -> e.getExamId() == examId);
    }

    public void setStatusMessage(String message) {
        if (statusLabel != null) statusLabel.setText(message == null ? "" : message);
    }
}
