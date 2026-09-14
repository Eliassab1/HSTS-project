package com.testify.client;

import com.testify.common.BotQuestion;
import com.testify.common.BotSource;
import com.testify.common.Course;
import com.testify.common.CourseBot;
import com.testify.common.User;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Consumer;

/**
 * Teacher - Learning Bot editor (spec 13).
 *
 * The bot belongs to the <i>course</i>, not to whoever created it, so the
 * course picker is filled from GET_MY_COURSES (the teacher's own courses via
 * {@code course_teachers}) rather than from every course in the school. Any
 * teacher of a course edits the same bot, which is why every source row shows
 * who last edited it - otherwise two teachers overwrite each other with no
 * trace on screen.
 *
 * The question-history tab is anonymised at the source: the server does not
 * select the student ID, so there is nothing here to hide. The banner on that
 * tab says so, because a teacher who assumes the rows are identified would
 * read them very differently.
 */
public class BotEditorView extends HBox {

    private final Consumer<Course> courseSelectedAction;
    private final Consumer<CourseBot> saveBotAction;
    private final Consumer<BotSource> saveSourceAction;
    private final Consumer<Integer> deleteSourceAction;
    private final Consumer<CourseBot> historyAction;
    private final Runnable backAction;

    private final ObservableList<BotSource> sources = FXCollections.observableArrayList();

    private ComboBox<Course> coursePicker;
    private TextField botNameField;
    private CheckBox availableToggle;
    private TableView<BotSource> sourceTable;
    private Label statusLabel;
    private Label botHeaderLabel;
    private VBox historyList;
    private Label historyStatus;

    /** The bot currently loaded, or null before a course is chosen. */
    private CourseBot currentBot;

    public BotEditorView(
            User teacher,
            VBox sidebar,
            Consumer<Course> courseSelectedAction,
            Consumer<CourseBot> saveBotAction,
            Consumer<BotSource> saveSourceAction,
            Consumer<Integer> deleteSourceAction,
            Consumer<CourseBot> historyAction,
            Runnable backAction
    ) {
        if (courseSelectedAction == null) throw new IllegalArgumentException("Course action cannot be null.");
        if (saveBotAction == null)        throw new IllegalArgumentException("Save bot action cannot be null.");
        if (saveSourceAction == null)     throw new IllegalArgumentException("Save source action cannot be null.");
        if (deleteSourceAction == null)   throw new IllegalArgumentException("Delete source action cannot be null.");
        if (historyAction == null)        throw new IllegalArgumentException("History action cannot be null.");
        if (backAction == null)           throw new IllegalArgumentException("Back action cannot be null.");

        this.courseSelectedAction = courseSelectedAction;
        this.saveBotAction        = saveBotAction;
        this.saveSourceAction     = saveSourceAction;
        this.deleteSourceAction   = deleteSourceAction;
        this.historyAction        = historyAction;
        this.backAction           = backAction;

        buildUI(teacher, sidebar);
    }

    // -- Build ----------------------------------------------------------------

    private void buildUI(User teacher, VBox sidebar) {
        getStyleClass().add("login-background");
        setPadding(new Insets(22));
        setSpacing(16);

        VBox main = buildMain(teacher);
        HBox.setHgrow(main, Priority.ALWAYS);
        getChildren().addAll(sidebar, main);
    }

    private VBox buildMain(User teacher) {

        String name = teacher != null && teacher.getFullName() != null
                && !teacher.getFullName().isBlank()
                ? teacher.getFullName()
                : (teacher != null ? teacher.getUsername() : "Teacher");

        Label title = new Label("Learning Bot - " + name);
        title.getStyleClass().add("page-title");

        Label hint = new Label(
                "The bot answers students only from the material you add here. "
                        + "A course with no sources has a bot that can only say it does not know.");
        hint.setWrapText(true);
        hint.setStyle("-fx-text-fill: -hsts-muted;");

        coursePicker = new ComboBox<>();
        coursePicker.setPrefWidth(320);
        coursePicker.setPromptText("Choose one of your courses");
        coursePicker.setOnAction(e -> {
            Course selected = coursePicker.getSelectionModel().getSelectedItem();
            if (selected != null) {
                setStatusMessage("Loading the bot for " + selected.getCourseName() + "...");
                courseSelectedAction.accept(selected);
            }
        });

        Button backBtn = new Button("Back to Dashboard");
        backBtn.getStyleClass().add("secondary-button");
        backBtn.setOnAction(e -> backAction.run());

        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);

        HBox pickerRow = new HBox(12, new Label("Course:"), coursePicker, topSpacer, backBtn);
        pickerRow.setAlignment(Pos.CENTER_LEFT);

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(
                new Tab("Bot & sources", buildEditorPane()),
                new Tab("Question history", buildHistoryPane()));
        VBox.setVgrow(tabs, Priority.ALWAYS);

        statusLabel = new Label();
        statusLabel.getStyleClass().add("status-label");
        statusLabel.setWrapText(true);

        return new VBox(14, title, hint, pickerRow, tabs, statusLabel);
    }

    private VBox buildEditorPane() {

        botHeaderLabel = new Label("Choose a course above to edit its bot.");
        botHeaderLabel.getStyleClass().add("section-title");

        botNameField = new TextField();
        botNameField.setPromptText("Bot name, e.g. \"Maths Helper\"");
        botNameField.setPrefWidth(300);

        availableToggle = new CheckBox("Available to students");

        Button saveBotBtn = new Button("Save Bot");
        saveBotBtn.getStyleClass().add("primary-button");
        saveBotBtn.setPrefWidth(140);
        saveBotBtn.setOnAction(e -> saveBot());

        HBox botRow = new HBox(12, new Label("Name:"), botNameField,
                availableToggle, saveBotBtn);
        botRow.setAlignment(Pos.CENTER_LEFT);

        VBox botCard = new VBox(10, botHeaderLabel, botRow);
        botCard.getStyleClass().add("section-card");

        sourceTable = buildSourceTable();
        sourceTable.setItems(sources);
        VBox.setVgrow(sourceTable, Priority.ALWAYS);

        Button addBtn = new Button("+  Add Source");
        addBtn.getStyleClass().add("success-button");
        addBtn.setPrefWidth(150);
        addBtn.setOnAction(e -> promptForSource(null));

        Button editBtn = new Button("Edit Source");
        editBtn.getStyleClass().add("primary-button");
        editBtn.setPrefWidth(140);
        editBtn.setOnAction(e -> {
            BotSource selected = requireSelection();
            if (selected != null) promptForSource(selected);
        });

        Button deleteBtn = new Button("Delete Source");
        deleteBtn.getStyleClass().add("secondary-button");
        deleteBtn.setPrefWidth(150);
        deleteBtn.setOnAction(e -> {
            BotSource selected = requireSelection();
            if (selected != null) confirmDelete(selected);
        });

        HBox sourceActions = new HBox(12, addBtn, editBtn, deleteBtn);
        sourceActions.setAlignment(Pos.CENTER_LEFT);

        Label sourcesTitle = new Label("Information sources");
        sourcesTitle.getStyleClass().add("section-title");

        VBox pane = new VBox(12, botCard, sourcesTitle, sourceTable, sourceActions);
        pane.setPadding(new Insets(14));
        return pane;
    }

    private VBox buildHistoryPane() {

        Label banner = new Label(
                "This view is anonymised: it shows what was asked, not who asked it. "
                        + "The student identity is never sent to this screen.");
        banner.setWrapText(true);
        banner.setStyle("-fx-font-weight: bold; -fx-text-fill: -hsts-accent;");

        Button refreshBtn = new Button("Refresh history");
        refreshBtn.getStyleClass().add("secondary-button");
        refreshBtn.setOnAction(e -> {
            if (currentBot == null || currentBot.getId() <= 0) {
                setStatusMessage("Choose a course with a saved bot first.");
                return;
            }
            historyAction.accept(currentBot);
        });

        historyStatus = new Label("Choose a course to see what students have asked.");
        historyStatus.setWrapText(true);
        historyStatus.setStyle("-fx-text-fill: -hsts-muted;");

        historyList = new VBox(10);
        historyList.setPadding(new Insets(14));

        ScrollPane scroll = new ScrollPane(historyList);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("section-card");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox pane = new VBox(12, banner, refreshBtn, historyStatus, scroll);
        pane.setPadding(new Insets(14));
        return pane;
    }

    private TableView<BotSource> buildSourceTable() {
        TableView<BotSource> table = new TableView<>();
        table.setPlaceholder(new Label("This bot has no material yet."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<BotSource, String> titleCol = new TableColumn<>("Title");
        titleCol.setCellValueFactory(new PropertyValueFactory<>("title"));
        titleCol.setPrefWidth(240);

        TableColumn<BotSource, String> previewCol = new TableColumn<>("Content");
        previewCol.setCellValueFactory(cell -> {
            String content = cell.getValue().getContent();
            String preview = content == null ? "" : content.replaceAll("\\s+", " ").trim();
            if (preview.length() > 90) preview = preview.substring(0, 90) + "...";
            return new javafx.beans.property.SimpleStringProperty(preview);
        });
        previewCol.setPrefWidth(360);

        TableColumn<BotSource, String> editorCol = new TableColumn<>("Last edited by");
        editorCol.setCellValueFactory(new PropertyValueFactory<>("updatedByName"));
        editorCol.setPrefWidth(160);

        TableColumn<BotSource, String> whenCol = new TableColumn<>("When");
        whenCol.setCellValueFactory(new PropertyValueFactory<>("updatedAt"));
        whenCol.setPrefWidth(170);

        table.getColumns().addAll(titleCol, previewCol, editorCol, whenCol);
        return table;
    }

    // -- Actions --------------------------------------------------------------

    private void saveBot() {

        Course course = coursePicker.getSelectionModel().getSelectedItem();
        if (course == null) {
            setStatusMessage("Choose a course first.");
            return;
        }
        String name = botNameField.getText() == null ? "" : botNameField.getText().trim();
        if (name.isBlank()) {
            setStatusMessage("Give the bot a name before saving.");
            return;
        }

        CourseBot bot = new CourseBot(
                currentBot != null ? currentBot.getId() : 0,
                course.getId(),
                name,
                availableToggle.isSelected());
        setStatusMessage("Saving bot...");
        saveBotAction.accept(bot);
    }

    /**
     * Collects a source's title and content.
     *
     * @param existing the source being edited, or null to add a new one
     */
    private void promptForSource(BotSource existing) {

        if (currentBot == null || currentBot.getId() <= 0) {
            setStatusMessage("Save the bot first - material has to belong to a bot.");
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Add Source" : "Edit Source");
        dialog.setHeaderText(existing == null
                ? "Add material for " + currentBot.getBotName() + " to answer from"
                : "Edit \"" + existing.getTitle() + "\"");

        ButtonType saveType = new ButtonType("Save Source", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        TextField titleField = new TextField(existing == null ? "" : existing.getTitle());
        titleField.setPromptText("e.g. \"Quadratic equations\"");

        TextArea contentArea = new TextArea(existing == null ? "" : existing.getContent());
        contentArea.setPromptText("Paste or write the course material. Separate topics with a "
                + "blank line - the offline fallback matches a question paragraph by paragraph.");
        contentArea.setPrefRowCount(16);
        contentArea.setPrefColumnCount(60);
        contentArea.setWrapText(true);

        VBox content = new VBox(10,
                new Label("Title:"), titleField,
                new Label("Content:"), contentArea);
        content.setPadding(new Insets(14));
        dialog.getDialogPane().setContent(content);
        dialog.setResizable(true);

        dialog.showAndWait().ifPresent(result -> {
            if (result != saveType) return;

            String title = titleField.getText() == null ? "" : titleField.getText().trim();
            String body  = contentArea.getText() == null ? "" : contentArea.getText().trim();
            if (title.isBlank()) {
                setStatusMessage("A source needs a title.");
                return;
            }
            if (body.isBlank()) {
                setStatusMessage("A source needs some content.");
                return;
            }

            BotSource source = new BotSource(
                    existing == null ? 0 : existing.getId(),
                    currentBot.getId(),
                    title,
                    body);
            setStatusMessage("Saving source...");
            saveSourceAction.accept(source);
        });
    }

    /** Deleting material a bot depends on is worth a confirmation. */
    private void confirmDelete(BotSource source) {

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Source");
        confirm.setHeaderText("Delete \"" + source.getTitle() + "\"?");
        confirm.setContentText("The bot will no longer be able to answer from this material. "
                + "This cannot be undone.");

        confirm.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                setStatusMessage("Deleting source...");
                deleteSourceAction.accept(source.getId());
            }
        });
    }

    /** Returns the selected row, or null after reporting that nothing is selected. */
    private BotSource requireSelection() {
        BotSource selected = sourceTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            setStatusMessage("Select a source first.");
        }
        return selected;
    }

    // -- Public API -----------------------------------------------------------

    /**
     * Fills the course picker with the teacher's own courses.
     *
     * @param courses courses this teacher is attached to
     */
    public void setCourses(List<Course> courses) {
        coursePicker.getItems().setAll(courses == null ? List.<Course>of() : courses);
        if (coursePicker.getItems().isEmpty()) {
            setStatusMessage("You are not assigned to any course yet, so there is no bot to edit.");
            return;
        }
        if (coursePicker.getSelectionModel().getSelectedItem() == null) {
            coursePicker.getSelectionModel().select(0);
        }
    }

    /**
     * Loads a bot into the editor.
     *
     * A course with no bot yet arrives as a shell with id 0 - that is the
     * empty editor, not an error, and Save creates the row.
     *
     * @param bot the bot record, sources included
     */
    public void setBot(CourseBot bot) {
        this.currentBot = bot;

        if (bot == null) {
            botHeaderLabel.setText("Choose a course above to edit its bot.");
            botNameField.clear();
            availableToggle.setSelected(false);
            sources.clear();
            return;
        }

        botHeaderLabel.setText(bot.getId() > 0
                ? "Editing the bot for " + bot.getCourseName()
                : "No bot exists for " + bot.getCourseName() + " yet - name it and save to create one.");
        botNameField.setText(bot.getBotName() == null ? "" : bot.getBotName());
        availableToggle.setSelected(bot.isAvailable());
        sources.setAll(bot.getSources() == null ? List.<BotSource>of() : bot.getSources());
    }

    /**
     * Inserts or replaces one source row after a save, without re-fetching
     * the whole bot.
     *
     * @param saved the source as stored
     */
    public void setSourceSaved(BotSource saved) {
        if (saved == null) return;
        for (int i = 0; i < sources.size(); i++) {
            if (sources.get(i).getId() == saved.getId()) {
                sources.set(i, saved);
                setStatusMessage("Source \"" + saved.getTitle() + "\" saved.");
                return;
            }
        }
        sources.add(saved);
        if (currentBot != null) {
            currentBot.setSourceCount(sources.size());
        }
        setStatusMessage("Source \"" + saved.getTitle() + "\" added.");
    }

    /**
     * Drops a deleted source row.
     *
     * @param sourceId identifier of the removed source
     */
    public void removeSource(int sourceId) {
        sources.removeIf(s -> s.getId() == sourceId);
        if (currentBot != null) {
            currentBot.setSourceCount(sources.size());
        }
        setStatusMessage("Source deleted.");
    }

    /**
     * Fills the anonymised question-history tab.
     *
     * @param history exchanges with this bot, carrying no student identity
     */
    public void setHistory(List<BotQuestion> history) {

        historyList.getChildren().clear();

        if (history == null || history.isEmpty()) {
            historyStatus.setText("No questions have been asked of this bot yet.");
            return;
        }

        historyStatus.setText("Showing " + history.size() + " question(s), anonymised.");
        for (BotQuestion exchange : history) {
            Label when = new Label(exchange.getAskedAt());
            when.setStyle("-fx-font-size: 11px; -fx-text-fill: -hsts-muted;");

            Label question = new Label("Q: " + exchange.getQuestionText());
            question.setWrapText(true);
            question.setStyle("-fx-font-weight: bold; -fx-text-fill: -hsts-strong;");

            Label answer = new Label("A: " + exchange.getAnswerText());
            answer.setWrapText(true);
            answer.setStyle("-fx-text-fill: -hsts-strong;");

            VBox card = new VBox(4, when, question, answer);
            card.getStyleClass().add("section-card");
            historyList.getChildren().add(card);
        }
    }

    /** The bot currently loaded, or null. */
    public CourseBot getCurrentBot() {
        return currentBot;
    }

    /** The course currently chosen, or null. */
    public Course getSelectedCourse() {
        return coursePicker == null ? null : coursePicker.getSelectionModel().getSelectedItem();
    }

    public void setStatusMessage(String message) {
        if (statusLabel != null) statusLabel.setText(message == null ? "" : message);
    }
}
