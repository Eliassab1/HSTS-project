package com.testify.client;

import com.testify.common.Course;
import com.testify.common.EngineStatus;
import com.testify.common.Exam;
import com.testify.common.ExamQuestion;
import com.testify.common.GenerationRequest;
import com.testify.common.Question;
import com.testify.common.User;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.RadioButton;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Teacher - Generate Content (Claude-backed drafting).
 *
 * One rule governs this whole screen: <b>generated content is a draft.</b>
 * Claude proposes, the teacher disposes. Nothing here writes to the question
 * bank or reaches a student without a human approving it on screen, and the
 * two modes reach the existing save paths rather than inventing new ones:
 * questions go through SAVE_GENERATED_QUESTIONS, and a generated exam is
 * handed to the normal exam builder so it is created, validated and approved
 * exactly like a hand-written one.
 *
 * Degraded mode is decided before the teacher touches anything. The screen
 * asks the server what the engine can do and, when the offline fallback is
 * active, arrives with both generate buttons already disabled and the reason
 * on screen - rather than failing on click or quietly producing nothing.
 */
public class GenerateContentView extends HBox {

    /** Matches {@code GenerationRequest.MAX_COUNT} and the server's validation. */
    private static final int MAX_COUNT = GenerationRequest.MAX_COUNT;

    private final Consumer<GenerationRequest> generateQuestionsAction;
    private final Consumer<GenerationRequest> generateExamAction;
    private final Consumer<List<Question>> saveQuestionsAction;
    private final Consumer<Exam> acceptExamAction;
    private final Runnable backAction;

    // Question mode
    private ComboBox<Course> questionCourse;
    private TextField questionTopic;
    private ComboBox<String> questionDifficulty;
    private Spinner<Integer> questionCount;
    private Button generateQuestionsBtn;
    private ProgressIndicator questionSpinner;
    private VBox draftList;
    private Button saveDraftsBtn;
    private Label draftSummary;

    // Exam mode
    private ComboBox<Course> examCourse;
    private TextField examTopic;
    private Spinner<Integer> examCount;
    private TextField examDuration;
    private Button generateExamBtn;
    private ProgressIndicator examSpinner;
    private VBox examPreview;
    private Button acceptExamBtn;
    private Label examSummary;

    private Label statusLabel;
    private Label engineBanner;

    /** The editable draft rows currently on screen, in generated order. */
    private final List<DraftRow> draftRows = new ArrayList<>();

    /** The generated exam awaiting the teacher's acceptance, or null. */
    private Exam pendingExam;

    public GenerateContentView(
            User teacher,
            VBox sidebar,
            Consumer<GenerationRequest> generateQuestionsAction,
            Consumer<GenerationRequest> generateExamAction,
            Consumer<List<Question>> saveQuestionsAction,
            Consumer<Exam> acceptExamAction,
            Runnable backAction
    ) {
        if (generateQuestionsAction == null) throw new IllegalArgumentException("Generate questions action cannot be null.");
        if (generateExamAction == null)      throw new IllegalArgumentException("Generate exam action cannot be null.");
        if (saveQuestionsAction == null)     throw new IllegalArgumentException("Save questions action cannot be null.");
        if (acceptExamAction == null)        throw new IllegalArgumentException("Accept exam action cannot be null.");
        if (backAction == null)              throw new IllegalArgumentException("Back action cannot be null.");

        this.generateQuestionsAction = generateQuestionsAction;
        this.generateExamAction      = generateExamAction;
        this.saveQuestionsAction     = saveQuestionsAction;
        this.acceptExamAction        = acceptExamAction;
        this.backAction              = backAction;

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

        Label title = new Label("Generate Content - " + name);
        title.getStyleClass().add("page-title");

        Label hint = new Label(
                "Everything generated here is a draft. Nothing is saved, and no student sees "
                        + "anything, until you review it and choose to keep it.");
        hint.setWrapText(true);
        hint.setStyle("-fx-text-fill: -hsts-muted;");

        engineBanner = new Label("Checking what the server's engine can do...");
        engineBanner.setWrapText(true);
        engineBanner.setStyle("-fx-text-fill: -hsts-muted;");

        Button backBtn = new Button("Back to Dashboard");
        backBtn.getStyleClass().add("secondary-button");
        backBtn.setOnAction(e -> backAction.run());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox topRow = new HBox(12, spacer, backBtn);

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(
                new Tab("Generate questions", buildQuestionPane()),
                new Tab("Generate exam", buildExamPane()));
        VBox.setVgrow(tabs, Priority.ALWAYS);

        statusLabel = new Label();
        statusLabel.getStyleClass().add("status-label");
        statusLabel.setWrapText(true);

        return new VBox(14, title, hint, engineBanner, topRow, tabs, statusLabel);
    }

    // -- Question mode --------------------------------------------------------

    private VBox buildQuestionPane() {

        questionCourse = new ComboBox<>();
        questionCourse.setPrefWidth(240);
        questionCourse.setPromptText("Your course");

        questionTopic = new TextField();
        questionTopic.setPromptText("e.g. quadratic equations");
        questionTopic.setPrefWidth(260);

        questionDifficulty = new ComboBox<>();
        questionDifficulty.getItems().addAll(
                GenerationRequest.DIFFICULTY_EASY,
                GenerationRequest.DIFFICULTY_MEDIUM,
                GenerationRequest.DIFFICULTY_HARD);
        questionDifficulty.setValue(GenerationRequest.DIFFICULTY_MEDIUM);

        questionCount = new Spinner<>(1, MAX_COUNT, 3);
        questionCount.setPrefWidth(90);

        generateQuestionsBtn = new Button("Generate");
        generateQuestionsBtn.getStyleClass().add("primary-button");
        generateQuestionsBtn.setPrefWidth(140);
        generateQuestionsBtn.setDisable(true);
        generateQuestionsBtn.setOnAction(e -> generateQuestions());

        questionSpinner = new ProgressIndicator();
        questionSpinner.setPrefSize(24, 24);
        questionSpinner.setVisible(false);
        questionSpinner.setManaged(false);

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        form.add(new Label("Course:"), 0, 0);
        form.add(questionCourse, 1, 0);
        form.add(new Label("Topic:"), 2, 0);
        form.add(questionTopic, 3, 0);
        form.add(new Label("Difficulty:"), 0, 1);
        form.add(questionDifficulty, 1, 1);
        form.add(new Label("How many (max " + MAX_COUNT + "):"), 2, 1);
        form.add(questionCount, 3, 1);
        form.add(new HBox(10, generateQuestionsBtn, questionSpinner), 4, 1);

        VBox formCard = new VBox(form);
        formCard.getStyleClass().add("section-card");

        draftSummary = new Label("No drafts yet. Generate some to review them here.");
        draftSummary.setWrapText(true);
        draftSummary.setStyle("-fx-text-fill: -hsts-muted;");

        draftList = new VBox(12);
        draftList.setPadding(new Insets(12));

        ScrollPane scroll = new ScrollPane(draftList);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("section-card");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        saveDraftsBtn = new Button("Save kept questions");
        saveDraftsBtn.getStyleClass().add("success-button");
        saveDraftsBtn.setPrefWidth(200);
        saveDraftsBtn.setDisable(true);
        saveDraftsBtn.setOnAction(e -> saveKeptDrafts());

        VBox pane = new VBox(12, formCard, draftSummary, scroll, saveDraftsBtn);
        pane.setPadding(new Insets(14));
        return pane;
    }

    private void generateQuestions() {

        Course course = questionCourse.getSelectionModel().getSelectedItem();
        if (course == null) {
            setStatusMessage("Choose one of your courses first.");
            return;
        }
        String topic = questionTopic.getText() == null ? "" : questionTopic.getText().trim();
        if (topic.isBlank()) {
            setStatusMessage("Enter a topic to generate from.");
            return;
        }

        setQuestionWaiting(true);
        setStatusMessage("Generating " + questionCount.getValue() + " question(s)...");
        generateQuestionsAction.accept(new GenerationRequest(
                course.getId(), topic, questionDifficulty.getValue(), questionCount.getValue()));
    }

    private void setQuestionWaiting(boolean waiting) {
        generateQuestionsBtn.setDisable(waiting || !generationEnabled);
        questionSpinner.setVisible(waiting);
        questionSpinner.setManaged(waiting);
    }

    /**
     * Sends only the drafts still marked "keep", carrying whatever edits the
     * teacher made to them.
     */
    private void saveKeptDrafts() {

        List<Question> kept = new ArrayList<>();
        for (DraftRow row : draftRows) {
            if (!row.isKept()) continue;
            Question edited = row.toQuestion();
            if (edited == null) {
                setStatusMessage("One draft is incomplete: every question needs text, "
                        + "four options and a correct answer. Nothing was saved.");
                return;
            }
            kept.add(edited);
        }

        if (kept.isEmpty()) {
            setStatusMessage("No drafts are marked to keep, so there is nothing to save.");
            return;
        }

        saveDraftsBtn.setDisable(true);
        setStatusMessage("Saving " + kept.size() + " question(s)...");
        saveQuestionsAction.accept(kept);
    }

    // -- Exam mode ------------------------------------------------------------

    private VBox buildExamPane() {

        examCourse = new ComboBox<>();
        examCourse.setPrefWidth(240);
        examCourse.setPromptText("Your course");

        examTopic = new TextField();
        examTopic.setPromptText("e.g. mechanics");
        examTopic.setPrefWidth(260);

        examCount = new Spinner<>(1, MAX_COUNT, 5);
        examCount.setPrefWidth(90);

        examDuration = new TextField("60");
        examDuration.setPrefWidth(90);

        generateExamBtn = new Button("Generate exam");
        generateExamBtn.getStyleClass().add("primary-button");
        generateExamBtn.setPrefWidth(160);
        generateExamBtn.setDisable(true);
        generateExamBtn.setOnAction(e -> generateExam());

        examSpinner = new ProgressIndicator();
        examSpinner.setPrefSize(24, 24);
        examSpinner.setVisible(false);
        examSpinner.setManaged(false);

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        form.add(new Label("Course:"), 0, 0);
        form.add(examCourse, 1, 0);
        form.add(new Label("Topic:"), 2, 0);
        form.add(examTopic, 3, 0);
        form.add(new Label("Questions (max " + MAX_COUNT + "):"), 0, 1);
        form.add(examCount, 1, 1);
        form.add(new Label("Duration (minutes):"), 2, 1);
        form.add(examDuration, 3, 1);
        form.add(new HBox(10, generateExamBtn, examSpinner), 4, 1);

        VBox formCard = new VBox(form);
        formCard.getStyleClass().add("section-card");

        examSummary = new Label("No draft exam yet.");
        examSummary.setWrapText(true);
        examSummary.setStyle("-fx-text-fill: -hsts-muted;");

        examPreview = new VBox(12);
        examPreview.setPadding(new Insets(12));

        ScrollPane scroll = new ScrollPane(examPreview);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("section-card");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        acceptExamBtn = new Button("Accept & open in exam builder");
        acceptExamBtn.getStyleClass().add("success-button");
        acceptExamBtn.setPrefWidth(280);
        acceptExamBtn.setDisable(true);
        acceptExamBtn.setOnAction(e -> {
            if (pendingExam == null) {
                setStatusMessage("Generate a draft exam first.");
                return;
            }
            acceptExamBtn.setDisable(true);
            setStatusMessage("Saving the generated questions, then opening the exam builder...");
            acceptExamAction.accept(pendingExam);
        });

        VBox pane = new VBox(12, formCard, examSummary, scroll, acceptExamBtn);
        pane.setPadding(new Insets(14));
        return pane;
    }

    private void generateExam() {

        Course course = examCourse.getSelectionModel().getSelectedItem();
        if (course == null) {
            setStatusMessage("Choose one of your courses first.");
            return;
        }
        String topic = examTopic.getText() == null ? "" : examTopic.getText().trim();
        if (topic.isBlank()) {
            setStatusMessage("Enter a topic to generate from.");
            return;
        }
        int duration;
        try {
            duration = Integer.parseInt(examDuration.getText().trim());
        } catch (NumberFormatException ex) {
            setStatusMessage("Enter the duration as a whole number of minutes.");
            return;
        }
        if (duration <= 0) {
            setStatusMessage("The duration must be greater than zero.");
            return;
        }

        setExamWaiting(true);
        setStatusMessage("Generating a draft exam...");
        generateExamAction.accept(new GenerationRequest(
                course.getId(), topic, GenerationRequest.DIFFICULTY_MEDIUM,
                examCount.getValue(), duration));
    }

    private void setExamWaiting(boolean waiting) {
        generateExamBtn.setDisable(waiting || !generationEnabled);
        examSpinner.setVisible(waiting);
        examSpinner.setManaged(waiting);
    }

    // -- Public API -----------------------------------------------------------

    /**
     * Whether the server said generation can run at all.
     *
     * Starts FALSE and both buttons start disabled: until GET_ENGINE_STATUS
     * answers, the client does not know whether a key is configured, and
     * guessing "yes" leaves a window in which a click fails for a reason the
     * teacher cannot see.
     */
    private boolean generationEnabled;

    /**
     * Applies the server's engine status.
     *
     * When generation is unavailable both buttons are disabled and the reason
     * is on screen from the moment the teacher arrives. The alternative -
     * letting them fill in a form and click - wastes their time and teaches
     * them the feature is broken rather than unconfigured.
     *
     * @param status what the server reported
     */
    public void setEngineStatus(EngineStatus status) {

        generationEnabled = status != null && status.isGenerationAvailable();

        if (status == null) {
            engineBanner.setText("Could not read the engine status; generation is disabled.");
        } else {
            engineBanner.setText(status.describe());
        }
        engineBanner.setStyle(generationEnabled
                ? "-fx-text-fill: -hsts-muted;"
                : "-fx-font-weight: bold; -fx-text-fill: -hsts-accent;");

        generateQuestionsBtn.setDisable(!generationEnabled);
        generateExamBtn.setDisable(!generationEnabled);
    }

    /**
     * Fills both course pickers with the teacher's own courses.
     *
     * @param courses courses this teacher is attached to
     */
    public void setCourses(List<Course> courses) {
        List<Course> list = courses == null ? List.<Course>of() : courses;
        questionCourse.getItems().setAll(list);
        examCourse.getItems().setAll(list);
        if (list.isEmpty()) {
            setStatusMessage("You are not assigned to any course yet, so there is nothing to "
                    + "generate for.");
            return;
        }
        questionCourse.getSelectionModel().select(0);
        examCourse.getSelectionModel().select(0);
    }

    /**
     * Renders generated questions as an editable, keep/discard review list.
     *
     * @param drafts the drafts, unsaved
     */
    public void setDrafts(List<Question> drafts) {

        setQuestionWaiting(false);
        draftRows.clear();
        draftList.getChildren().clear();

        if (drafts == null || drafts.isEmpty()) {
            draftSummary.setText("Nothing was generated. Try a narrower topic.");
            saveDraftsBtn.setDisable(true);
            return;
        }

        int index = 1;
        for (Question draft : drafts) {
            DraftRow row = new DraftRow(index++, draft);
            draftRows.add(row);
            draftList.getChildren().add(row.getNode());
        }

        draftSummary.setText("Review " + draftRows.size() + " draft(s). Edit anything that needs "
                + "it, untick what you do not want, then save. Nothing is stored until you do.");
        saveDraftsBtn.setDisable(false);
    }

    /**
     * Reports the outcome of a save and clears the reviewed drafts.
     *
     * @param savedCount how many rows the server stored
     */
    public void draftsSaved(int savedCount) {
        draftRows.clear();
        draftList.getChildren().clear();
        draftSummary.setText("Saved " + savedCount + " question(s) to the question bank.");
        saveDraftsBtn.setDisable(true);
        setStatusMessage("Saved " + savedCount + " question(s).");
    }

    /**
     * Renders a generated draft paper for review.
     *
     * @param exam the draft exam, unsaved
     */
    public void setDraftExam(Exam exam) {

        setExamWaiting(false);
        examPreview.getChildren().clear();
        pendingExam = exam;

        if (exam == null || exam.getExamQuestions() == null || exam.getExamQuestions().isEmpty()) {
            examSummary.setText("Nothing was generated. Try a narrower topic.");
            acceptExamBtn.setDisable(true);
            return;
        }

        int total = 0;
        for (ExamQuestion eq : exam.getExamQuestions()) {
            total += eq.getPoints();
        }

        Label header = new Label(exam.getTitle() + "  -  " + exam.getCourse()
                + ", " + exam.getDurationMinutes() + " minutes");
        header.getStyleClass().add("section-title");
        header.setWrapText(true);
        examPreview.getChildren().add(header);

        int index = 1;
        for (ExamQuestion eq : exam.getExamQuestions()) {
            Question q = eq.getQuestion();
            if (q == null) continue;

            Label number = new Label("Q" + index++ + "  (" + eq.getPoints() + " points)");
            number.setStyle("-fx-font-weight: bold; -fx-text-fill: -hsts-accent;");

            Label text = new Label(q.getQuestionText());
            text.setWrapText(true);
            text.setStyle("-fx-text-fill: -hsts-strong;");

            VBox card = new VBox(4, number, text,
                    option("A", q.getOptionA(), q.getCorrectAnswer()),
                    option("B", q.getOptionB(), q.getCorrectAnswer()),
                    option("C", q.getOptionC(), q.getCorrectAnswer()),
                    option("D", q.getOptionD(), q.getCorrectAnswer()));
            card.getStyleClass().add("section-card");
            examPreview.getChildren().add(card);
        }

        // The 100-point rule is the builder's and the server's, not Claude's.
        // The server distributes the points, so this should always hold - but
        // saying so plainly beats handing over a paper that will be rejected
        // three screens later with no explanation.
        if (total == 100) {
            examSummary.setText("Draft exam with " + exam.getExamQuestions().size()
                    + " question(s), totalling exactly 100 points. Accepting opens it in the "
                    + "exam builder, where it is saved and sent for the principal's approval "
                    + "like any other exam.");
            acceptExamBtn.setDisable(false);
        } else {
            examSummary.setText("Draft exam totals " + total + " points, not 100. Accepting will "
                    + "open it in the exam builder, but you must adjust the point distribution "
                    + "there before it can be saved.");
            acceptExamBtn.setDisable(false);
        }
    }

    /** One option line, marking the proposed correct answer. */
    private Label option(String letter, String text, String correct) {
        boolean isCorrect = letter.equalsIgnoreCase(correct);
        Label label = new Label((isCorrect ? "  * " : "    ") + letter + ") "
                + (text == null ? "" : text));
        label.setWrapText(true);
        label.setStyle(isCorrect
                ? "-fx-font-weight: bold; -fx-text-fill: -hsts-accent;"
                : "-fx-text-fill: -hsts-strong;");
        return label;
    }

    /** Re-enables the generate buttons after a refusal or failure. */
    public void generationFailed(String message) {
        setQuestionWaiting(false);
        setExamWaiting(false);
        saveDraftsBtn.setDisable(draftRows.isEmpty());
        acceptExamBtn.setDisable(pendingExam == null);
        setStatusMessage(message == null ? "Generation failed." : message);
    }

    public void setStatusMessage(String message) {
        if (statusLabel != null) statusLabel.setText(message == null ? "" : message);
    }

    // -- Draft row ------------------------------------------------------------

    /**
     * One reviewable draft: every field editable, with a keep/discard tick
     * that defaults to keep.
     *
     * It edits a copy rather than the object the server sent, so discarding a
     * draft genuinely discards it and a half-finished edit cannot leak into
     * what gets saved.
     */
    private static final class DraftRow {

        private final CheckBox keep;
        private final TextArea text;
        private final TextField optionA;
        private final TextField optionB;
        private final TextField optionC;
        private final TextField optionD;
        private final ToggleGroup correct;
        private final RadioButton correctA;
        private final RadioButton correctB;
        private final RadioButton correctC;
        private final RadioButton correctD;
        private final TextField topic;
        private final ComboBox<String> difficulty;
        private final int courseId;
        private final VBox node;

        DraftRow(int index, Question draft) {

            this.courseId = draft.getCourseId();

            keep = new CheckBox("Keep draft " + index);
            keep.setSelected(true);
            keep.setStyle("-fx-font-weight: bold;");

            text = new TextArea(draft.getQuestionText());
            text.setWrapText(true);
            text.setPrefRowCount(2);

            optionA = new TextField(draft.getOptionA());
            optionB = new TextField(draft.getOptionB());
            optionC = new TextField(draft.getOptionC());
            optionD = new TextField(draft.getOptionD());

            correct = new ToggleGroup();
            correctA = radio("A");
            correctB = radio("B");
            correctC = radio("C");
            correctD = radio("D");
            switch (draft.getCorrectAnswer() == null ? "A" : draft.getCorrectAnswer().toUpperCase()) {
                case "B" -> correctB.setSelected(true);
                case "C" -> correctC.setSelected(true);
                case "D" -> correctD.setSelected(true);
                default  -> correctA.setSelected(true);
            }

            topic = new TextField(draft.getTopic() == null ? "" : draft.getTopic());
            topic.setPrefWidth(200);

            difficulty = new ComboBox<>();
            difficulty.getItems().addAll(
                    GenerationRequest.DIFFICULTY_EASY,
                    GenerationRequest.DIFFICULTY_MEDIUM,
                    GenerationRequest.DIFFICULTY_HARD);
            difficulty.setValue(draft.getDifficultyLevel() == null
                    ? GenerationRequest.DIFFICULTY_MEDIUM
                    : draft.getDifficultyLevel());

            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(8);
            grid.add(new Label("A:"), 0, 0); grid.add(optionA, 1, 0); grid.add(correctA, 2, 0);
            grid.add(new Label("B:"), 0, 1); grid.add(optionB, 1, 1); grid.add(correctB, 2, 1);
            grid.add(new Label("C:"), 0, 2); grid.add(optionC, 1, 2); grid.add(correctC, 2, 2);
            grid.add(new Label("D:"), 0, 3); grid.add(optionD, 1, 3); grid.add(correctD, 2, 3);
            optionA.setPrefWidth(420);
            optionB.setPrefWidth(420);
            optionC.setPrefWidth(420);
            optionD.setPrefWidth(420);

            HBox meta = new HBox(10,
                    new Label("Topic:"), topic,
                    new Label("Difficulty:"), difficulty);
            meta.setAlignment(Pos.CENTER_LEFT);

            Label correctHint = new Label("Tick the correct answer on the right.");
            correctHint.setStyle("-fx-font-size: 11px; -fx-text-fill: -hsts-muted;");

            node = new VBox(8, keep, text, grid, correctHint, meta);
            node.getStyleClass().add("section-card");

            // Discarding greys the row out, so the review list shows at a
            // glance what is about to be saved.
            keep.selectedProperty().addListener((obs, was, now) -> node.setOpacity(now ? 1.0 : 0.45));
        }

        private RadioButton radio(String letter) {
            RadioButton button = new RadioButton(letter);
            button.setToggleGroup(correct);
            return button;
        }

        boolean isKept() {
            return keep.isSelected();
        }

        VBox getNode() {
            return node;
        }

        /**
         * The edited draft as a Question, or null when the teacher has left a
         * required field empty.
         *
         * Returning null rather than saving a blank is deliberate: the server
         * would store an unanswerable question quite happily.
         */
        Question toQuestion() {

            String questionText = trim(text.getText());
            String a = trim(optionA.getText());
            String b = trim(optionB.getText());
            String c = trim(optionC.getText());
            String d = trim(optionD.getText());
            if (questionText.isBlank() || a.isBlank() || b.isBlank()
                    || c.isBlank() || d.isBlank()) {
                return null;
            }

            Question q = new Question();
            q.setId(0);
            q.setCourseId(courseId);
            q.setQuestionText(questionText);
            q.setOptionA(a);
            q.setOptionB(b);
            q.setOptionC(c);
            q.setOptionD(d);
            q.setCorrectAnswer(selectedLetter());
            q.setTopic(trim(topic.getText()));
            q.setDifficultyLevel(difficulty.getValue());
            return q;
        }

        private String selectedLetter() {
            if (correctB.isSelected()) return "B";
            if (correctC.isSelected()) return "C";
            if (correctD.isSelected()) return "D";
            return "A";
        }

        private String trim(String value) {
            return value == null ? "" : value.trim();
        }
    }
}
