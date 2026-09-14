package com.testify.client;

import com.testify.common.Exam;
import com.testify.common.ExamExtension;
import com.testify.common.User;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.function.Consumer;

/**
 * Teacher – Exam Bank screen (lilac theme, sidebar navigation).
 * Lists all created exams; allows toggling active status or deleting.
 */
public class ExamBankView extends HBox {

    private final ObservableList<Exam> exams = FXCollections.observableArrayList();
    private TableView<Exam> examTable;
    private Label statusLabel;

    public ExamBankView(
            User currentUser,
            VBox sidebar,
            Runnable loadAction,
            Consumer<Exam> toggleStatusAction,
            Consumer<Exam> deleteAction,
            Consumer<Exam> scheduleAction,
            Consumer<Exam> editAction,
            Consumer<Exam> resultsAction,
            Consumer<ExamExtension> extendAction,
            Runnable backAction,
            Runnable logoutAction
    ) {
        buildUI(sidebar, loadAction, toggleStatusAction, deleteAction, scheduleAction,
                editAction, resultsAction, extendAction, backAction);
    }

    // ── Build ─────────────────────────────────────────────────────────────────

    private void buildUI(
            VBox sidebar,
            Runnable loadAction,
            Consumer<Exam> toggleStatusAction,
            Consumer<Exam> deleteAction,
            Consumer<Exam> scheduleAction,
            Consumer<Exam> editAction,
            Consumer<Exam> resultsAction,
            Consumer<ExamExtension> extendAction,
            Runnable backAction
    ) {
        getStyleClass().add("login-background");
        setPadding(new Insets(22));
        setSpacing(16);

        VBox main = buildMain(loadAction, toggleStatusAction, deleteAction, scheduleAction,
                editAction, resultsAction, extendAction, backAction);
        HBox.setHgrow(main, Priority.ALWAYS);
        getChildren().addAll(sidebar, main);
    }

    // ── Main content ──────────────────────────────────────────────────────────

    private VBox buildMain(
            Runnable loadAction,
            Consumer<Exam> toggleStatusAction,
            Consumer<Exam> deleteAction,
            Consumer<Exam> scheduleAction,
            Consumer<Exam> editAction,
            Consumer<Exam> resultsAction,
            Consumer<ExamExtension> extendAction,
            Runnable backAction
    ) {
        // Search bar
        TextField searchBox = new TextField();
        searchBox.setPromptText("Search anything...");
        searchBox.getStyleClass().add("search-field");
        HBox topBar = new HBox(searchBox);
        topBar.getStyleClass().add("top-bar");
        topBar.setAlignment(Pos.CENTER_LEFT);

        // Title
        Label title = new Label("Exam Bank");
        title.getStyleClass().add("welcome-label");

        // Table
        examTable = buildTable();
        examTable.setItems(exams);
        VBox.setVgrow(examTable, Priority.ALWAYS);

        // Buttons
        Button loadBtn = new Button("Load Exams");
        loadBtn.getStyleClass().add("primary-button");
        loadBtn.setOnAction(e -> loadAction.run());

        Button toggleBtn = new Button("Toggle Active");
        toggleBtn.getStyleClass().add("success-button");
        toggleBtn.setOnAction(e -> {
            Exam sel = examTable.getSelectionModel().getSelectedItem();
            if (sel != null) toggleStatusAction.accept(sel);
            else statusLabel.setText("Select an exam first.");
        });

        Button deleteBtn = new Button("Delete Exam");
        deleteBtn.getStyleClass().add("danger-button");
        deleteBtn.setOnAction(e -> {
            Exam sel = examTable.getSelectionModel().getSelectedItem();
            if (sel != null) deleteAction.accept(sel);
            else statusLabel.setText("Select an exam first.");
        });

        Button scheduleBtn = new Button("Schedule");
        scheduleBtn.getStyleClass().add("primary-button");
        scheduleBtn.setOnAction(e -> {
            Exam sel = examTable.getSelectionModel().getSelectedItem();
            if (sel == null) { statusLabel.setText("Select an exam first."); return; }
            showScheduleDialog(sel, scheduleAction);
        });

        Button editBtn = new Button("Edit Exam");
        editBtn.getStyleClass().add("primary-button");
        editBtn.setOnAction(e -> {
            Exam sel = examTable.getSelectionModel().getSelectedItem();
            if (sel != null) editAction.accept(sel);
            else statusLabel.setText("Select an exam first.");
        });

        Button resultsBtn = new Button("View Results");
        resultsBtn.getStyleClass().add("success-button");
        resultsBtn.setOnAction(e -> {
            Exam sel = examTable.getSelectionModel().getSelectedItem();
            if (sel != null) resultsAction.accept(sel);
            else statusLabel.setText("Select an exam first.");
        });

        Button extendBtn = new Button("Extend Time");
        extendBtn.getStyleClass().add("primary-button");
        extendBtn.setOnAction(e -> {
            Exam sel = examTable.getSelectionModel().getSelectedItem();
            if (sel == null) { statusLabel.setText("Select an exam first."); return; }
            if (!sel.isActive()) {
                // Extending a dormant exam would change its duration with
                // nobody sitting it — allowed by the server, but almost
                // certainly not what the teacher meant to click.
                statusLabel.setText("Only an active exam can be extended while it runs.");
                return;
            }
            showExtendDialog(sel, extendAction);
        });

        Button backBtn = new Button("Back to Dashboard");
        backBtn.getStyleClass().add("secondary-button");
        backBtn.setOnAction(e -> backAction.run());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        statusLabel = new Label();
        statusLabel.getStyleClass().add("status-label");
        statusLabel.setWrapText(true);

        HBox actions = new HBox(12, loadBtn, editBtn, resultsBtn, extendBtn, toggleBtn, deleteBtn,
                scheduleBtn, spacer, backBtn);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox main = new VBox(16, topBar, title, examTable, actions, statusLabel);
        main.setPadding(new Insets(0));
        return main;
    }

    // ── Extend dialog ─────────────────────────────────────────────────────────

    /**
     * Prompts for the number of extra minutes and hands the request to
     * {@code extendAction} (spec 7). Students sitting the exam are told by a
     * server push, so nothing here has to refresh anyone's screen.
     */
    private void showExtendDialog(Exam exam, Consumer<ExamExtension> extendAction) {

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Extend Exam");
        dialog.setHeaderText("Add time to \"" + exam.getTitle() + "\"");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField minutesField = new TextField("10");
        minutesField.setPromptText("1-120");

        Label hint = new Label(
                "Students currently sitting this exam will see their timer go up straight away.");
        hint.setWrapText(true);
        hint.setStyle("-fx-font-size: 12px; -fx-text-fill: #6B7280;");

        Label dialogError = new Label();
        dialogError.setStyle("-fx-text-fill: -hsts-negative; -fx-font-size: 12px;");
        dialogError.setWrapText(true);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));
        grid.add(new Label("Extra minutes:"), 0, 0);
        grid.add(minutesField, 1, 0);
        grid.add(hint, 0, 1, 2, 1);
        grid.add(dialogError, 0, 2, 2, 1);

        dialog.getDialogPane().setContent(grid);

        // Keep the dialog open when the entered value does not validate.
        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.addEventFilter(ActionEvent.ACTION, event -> {
            int minutes;
            try {
                minutes = Integer.parseInt(minutesField.getText().trim());
            } catch (NumberFormatException e) {
                dialogError.setText("Enter a whole number of minutes.");
                event.consume();
                return;
            }
            if (minutes < 1 || minutes > 120) {
                dialogError.setText("Extra time must be between 1 and 120 minutes.");
                event.consume();
            }
        });

        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        extendAction.accept(new ExamExtension(
                exam.getExamId(),
                Integer.parseInt(minutesField.getText().trim())));
    }

    // ── Schedule dialog ───────────────────────────────────────────────────────

    /**
     * Prompts for an opening date/time, a closing date/time and a 4-digit
     * code, then hands the populated exam to {@code scheduleAction}. Times are
     * entered as HH:mm alongside a date picker; the pair is combined into a
     * {@link Timestamp}. Server-side validation is authoritative — this only
     * catches obvious mistakes early.
     */
    private void showScheduleDialog(Exam exam, Consumer<Exam> scheduleAction) {

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Schedule Exam");
        dialog.setHeaderText("Schedule \"" + exam.getTitle() + "\"");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        DatePicker openDate = new DatePicker(LocalDate.now());
        TextField openTime = new TextField("08:00");
        openTime.setPromptText("HH:mm");

        DatePicker closeDate = new DatePicker(LocalDate.now());
        TextField closeTime = new TextField("10:00");
        closeTime.setPromptText("HH:mm");

        TextField codeField = new TextField(
                exam.getExamCode() == null ? "" : exam.getExamCode());
        codeField.setPromptText("4-digit code");

        if (exam.getOpenAt() != null) {
            LocalDateTime o = exam.getOpenAt().toLocalDateTime();
            openDate.setValue(o.toLocalDate());
            openTime.setText(String.format("%02d:%02d", o.getHour(), o.getMinute()));
        }
        if (exam.getCloseAt() != null) {
            LocalDateTime c = exam.getCloseAt().toLocalDateTime();
            closeDate.setValue(c.toLocalDate());
            closeTime.setText(String.format("%02d:%02d", c.getHour(), c.getMinute()));
        }

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));
        grid.add(new Label("Opens:"), 0, 0);
        grid.add(openDate, 1, 0);
        grid.add(openTime, 2, 0);
        grid.add(new Label("Closes:"), 0, 1);
        grid.add(closeDate, 1, 1);
        grid.add(closeTime, 2, 1);
        grid.add(new Label("Code:"), 0, 2);
        grid.add(codeField, 1, 2);

        Label dialogError = new Label();
        dialogError.setStyle("-fx-text-fill: -hsts-negative; -fx-font-size: 12px;");
        dialogError.setWrapText(true);
        grid.add(dialogError, 0, 3, 3, 1);

        dialog.getDialogPane().setContent(grid);

        // Keep the dialog open when the entered values don't validate.
        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.addEventFilter(ActionEvent.ACTION, event -> {
            String code = codeField.getText() == null ? "" : codeField.getText().trim();
            if (!code.matches("^[0-9]{4}$")) {
                dialogError.setText("The exam code must be exactly 4 digits.");
                event.consume();
                return;
            }
            Timestamp open = toTimestamp(openDate.getValue(), openTime.getText());
            Timestamp close = toTimestamp(closeDate.getValue(), closeTime.getText());
            if (open == null || close == null) {
                dialogError.setText("Enter both dates and times as HH:mm.");
                event.consume();
                return;
            }
            if (!close.after(open)) {
                dialogError.setText("The closing time must be after the opening time.");
                event.consume();
            }
        });

        dialog.showAndWait().ifPresent(result -> {
            if (result != ButtonType.OK) return;
            exam.setOpenAt(toTimestamp(openDate.getValue(), openTime.getText()));
            exam.setCloseAt(toTimestamp(closeDate.getValue(), closeTime.getText()));
            exam.setExamCode(codeField.getText().trim());
            scheduleAction.accept(exam);
        });
    }

    /**
     * Combines a date and an "HH:mm" string into a Timestamp.
     *
     * @return the combined timestamp, or null if either part is missing or malformed
     */
    private Timestamp toTimestamp(LocalDate date, String time) {
        if (date == null || time == null || !time.trim().matches("^\\d{1,2}:\\d{2}$")) {
            return null;
        }
        try {
            String[] parts = time.trim().split(":");
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            if (hour > 23 || minute > 59) return null;
            return Timestamp.valueOf(LocalDateTime.of(date, LocalTime.of(hour, minute)));
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ── Table ─────────────────────────────────────────────────────────────────

    private TableView<Exam> buildTable() {
        TableView<Exam> table = new TableView<>();
        table.setPlaceholder(new Label("Click Load Exams to display your exams."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Exam, Integer> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("examId"));
        idCol.setPrefWidth(60);

        TableColumn<Exam, String> titleCol = new TableColumn<>("Title");
        titleCol.setCellValueFactory(new PropertyValueFactory<>("title"));
        titleCol.setPrefWidth(220);

        TableColumn<Exam, String> courseCol = new TableColumn<>("Course");
        courseCol.setCellValueFactory(new PropertyValueFactory<>("course"));
        courseCol.setPrefWidth(160);

        TableColumn<Exam, Integer> durationCol = new TableColumn<>("Duration (min)");
        durationCol.setCellValueFactory(new PropertyValueFactory<>("durationMinutes"));
        durationCol.setPrefWidth(120);

        TableColumn<Exam, Integer> questionsCol = new TableColumn<>("Questions");
        questionsCol.setCellValueFactory(cell ->
                new SimpleIntegerProperty(cell.getValue().getQuestionCount()).asObject());
        questionsCol.setPrefWidth(100);

        TableColumn<Exam, Boolean> activeCol = new TableColumn<>("Status");
        activeCol.setCellValueFactory(cell ->
                new SimpleBooleanProperty(cell.getValue().isActive()).asObject());
        activeCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Boolean val, boolean empty) {
                super.updateItem(val, empty);
                if (empty || val == null) { setText(""); setStyle(""); }
                else {
                    setText(val ? "Active" : "Inactive");
                    setStyle(val
                            ? "-fx-text-fill: -hsts-positive; -fx-font-weight: bold;"
                            : "-fx-text-fill: -hsts-negative; -fx-font-weight: bold;");
                }
            }
        });
        activeCol.setPrefWidth(100);

        TableColumn<Exam, String> approvalCol = new TableColumn<>("Approval");
        approvalCol.setCellValueFactory(new PropertyValueFactory<>("approvalStatus"));
        approvalCol.setPrefWidth(100);

        TableColumn<Exam, String> reasonCol = new TableColumn<>("Reason");
        reasonCol.setCellValueFactory(cell -> {
            String reason = cell.getValue().getRejectionReason();
            return new javafx.beans.property.SimpleStringProperty(reason == null ? "" : reason);
        });
        reasonCol.setPrefWidth(220);

        table.getColumns().addAll(idCol, titleCol, courseCol, durationCol, questionsCol, activeCol, approvalCol, reasonCol);
        return table;
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    public void setExams(List<Exam> newExams) {
        exams.setAll(newExams);
    }

    public void updateExam(Exam updated) {
        for (int i = 0; i < exams.size(); i++) {
            if (exams.get(i).getExamId() == updated.getExamId()) {
                exams.set(i, updated);
                return;
            }
        }
    }

    public void removeExam(int examId) {
        exams.removeIf(e -> e.getExamId() == examId);
    }

    public void setStatusMessage(String message) {
        if (statusLabel != null) statusLabel.setText(message == null ? "" : message);
    }
}
