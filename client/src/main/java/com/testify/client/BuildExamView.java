package com.testify.client;

import com.testify.common.Course;
import com.testify.common.Exam;
import com.testify.common.ExamQuestion;
import com.testify.common.Question;
import com.testify.common.User;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Represents the Build Exam screen of the HSTS client.
 *
 * The teacher can:
 * 1. Enter the exam details.
 * 2. Browse available questions.
 * 3. Add questions to the exam.
 * 4. Assign points to every selected question.
 * 5. Remove questions.
 * 6. Save an exam whose total score is exactly 100 points.
 */
public class BuildExamView extends HBox {

    /**
     * Number of available questions displayed on one page.
     */
    private static final int QUESTIONS_PER_PAGE = 6;

    /**
     * The teacher who creates the exam.
     */
    private final User currentTeacher;

    /**
     * Function called when the teacher saves the exam.
     */
    private final Consumer<Exam> saveExamAction;

    /**
     * Function called when the teacher returns to the dashboard.
     */
    private final Runnable backAction;

    /**
     * Complete list of questions received from the server.
     */
    private final ObservableList<Question> allQuestions;

    /**
     * Questions displayed on the current page.
     */
    private final ObservableList<Question> displayedQuestions;

    /**
     * Questions selected for the exam.
     */
    private final ObservableList<ExamQuestion> selectedExamQuestions;

    /**
     * The exam being edited, or null when a new exam is being built. It is
     * what tells this screen apart from its create mode: the header wording,
     * the save button and the identifier put on the saved exam all follow
     * from it.
     */
    private final Exam examBeingEdited;

    /**
     * True only when the pre-loaded exam is one that already exists in the
     * database.
     *
     * A generated exam (spec B3) is also handed in through {@code examToEdit}
     * so the teacher finishes it through this one authoring path rather than
     * a second save path -- but it carries {@code examId == 0}, because it has
     * never been saved. Keying the wording on non-null alone would label a
     * brand-new draft "Edit Exam 0" and its save button "Update Exam", which
     * is wrong in both directions: nothing is being updated, and there is no
     * exam 0. {@code saveExam()} already routes on the same id, so this keeps
     * the labels honest with what the button actually does.
     */
    private boolean isEditingSavedExam() {
        return examBeingEdited != null && examBeingEdited.getExamId() > 0;
    }

    private TableView<Question> availableQuestionsTable;
    private TableView<ExamQuestion> selectedQuestionsTable;

    private TextField titleField;
    private ComboBox<String> courseComboBox;
    private TextField durationField;
    private TextArea instructionsArea;
    private TextField pointsField;

    private Label pageLabel;
    private Label totalPointsLabel;
    private Label statusLabel;

    private Button previousButton;
    private Button nextButton;

    /**
     * Current question-page index.
     */
    private int currentPage;

    /**
     * Creates the Build Exam screen.
     *
     * @param currentTeacher currently logged-in teacher
     * @param questions available questions
     * @param saveExamAction function used for saving an exam
     * @param backAction function used for returning to the dashboard
     */
    public BuildExamView(
            User currentTeacher,
            VBox sidebar,
            List<Question> questions,
            Consumer<Exam> saveExamAction,
            Runnable backAction
    ) {
        this(currentTeacher, sidebar, questions, saveExamAction, backAction, null);
    }

    /**
     * Creates the Build Exam screen, either empty or pre-populated with an
     * existing exam to edit (spec 3.5).
     *
     * @param currentTeacher currently logged-in teacher
     * @param sidebar navigation sidebar for this role
     * @param questions available questions
     * @param saveExamAction function used for saving an exam; it receives an
     *                       exam whose identifier is 0 for a new exam and the
     *                       edited exam's identifier otherwise
     * @param backAction function used for returning to the dashboard
     * @param examToEdit exam to load into the form, or null to start empty
     */
    public BuildExamView(
            User currentTeacher,
            VBox sidebar,
            List<Question> questions,
            Consumer<Exam> saveExamAction,
            Runnable backAction,
            Exam examToEdit
    ) {

        if (currentTeacher == null) {
            throw new IllegalArgumentException(
                    "Current teacher cannot be null."
            );
        }

        if (saveExamAction == null) {
            throw new IllegalArgumentException(
                    "Save exam action cannot be null."
            );
        }

        if (backAction == null) {
            throw new IllegalArgumentException(
                    "Back action cannot be null."
            );
        }

        this.currentTeacher = currentTeacher;
        this.saveExamAction = saveExamAction;
        this.backAction = backAction;
        this.examBeingEdited = examToEdit;

        this.allQuestions =
                FXCollections.observableArrayList();

        if (questions != null) {
            this.allQuestions.addAll(questions);
        }

        this.displayedQuestions =
                FXCollections.observableArrayList();

        this.selectedExamQuestions =
                FXCollections.observableArrayList();

        this.currentPage = 0;

        createScreen(sidebar);
        displayCurrentQuestionPage();
        loadExamBeingEdited();
    }

    /**
     * Copies the exam under edit into the form. Does nothing in create mode.
     *
     * The course is set on the dropdown even though the course list has not
     * arrived from the server yet: {@link #setCourses(List)} preserves the
     * current value when it repopulates, so the selection survives.
     */
    private void loadExamBeingEdited() {

        if (examBeingEdited == null) {
            return;
        }

        titleField.setText(
                examBeingEdited.getTitle() != null
                        ? examBeingEdited.getTitle()
                        : ""
        );

        courseComboBox.setValue(
                examBeingEdited.getCourse()
        );

        durationField.setText(
                String.valueOf(examBeingEdited.getDurationMinutes())
        );

        instructionsArea.setText(
                examBeingEdited.getInstructions() != null
                        ? examBeingEdited.getInstructions()
                        : ""
        );

        if (examBeingEdited.getExamQuestions() != null) {
            selectedExamQuestions.addAll(
                    examBeingEdited.getExamQuestions()
            );
        }

        updateTotalPointsLabel();

        statusLabel.setText(
                isEditingSavedExam()
                        ? "Editing exam " + examBeingEdited.getExamId()
                                + ". Saving sends it back for the principal's approval."
                        : "Generated draft loaded. Review it, adjust anything you like, then "
                                + "save -- it is created like any other exam and goes to the "
                                + "principal for approval."
        );
    }

    /**
     * Creates all components of the Build Exam screen.
     */
    private void createScreen(VBox sidebar) {
        getStyleClass().add("login-background");
        setPadding(new Insets(22));
        setSpacing(16);

        BorderPane inner = new BorderPane();
        inner.setTop(createNavigationBar());
        inner.setLeft(createExamDetailsPanel());
        inner.setCenter(createAvailableQuestionsPanel());
        inner.setRight(createSelectedQuestionsPanel());

        BorderPane.setMargin(inner.getLeft(),   new Insets(20, 18, 0, 0));
        BorderPane.setMargin(inner.getCenter(), new Insets(20, 18, 0, 0));
        BorderPane.setMargin(inner.getRight(),  new Insets(20, 0,  0, 0));

        HBox.setHgrow(inner, Priority.ALWAYS);
        getChildren().addAll(sidebar, inner);
    }

    /**
     * Creates the top navigation bar.
     *
     * @return navigation bar
     */
    private HBox createNavigationBar() {

        Label titleLabel =
                new Label(
                        !isEditingSavedExam()
                                ? "HSTS - Build Exam"
                                : "HSTS - Edit Exam " + examBeingEdited.getExamId()
                );

        titleLabel.getStyleClass().add(
                "page-title"
        );

        Region spacer =
                new Region();

        HBox.setHgrow(
                spacer,
                Priority.ALWAYS
        );

        Button backButton =
                new Button("Back to Dashboard");

        backButton.getStyleClass().add(
                "secondary-button"
        );

        backButton.setOnAction(
                event -> backAction.run()
        );

        HBox navigationBar =
                new HBox(
                        12,
                        titleLabel,
                        spacer,
                        backButton
                );

        navigationBar.setAlignment(
                Pos.CENTER_LEFT
        );

        navigationBar.getStyleClass().add(
                "navigation-bar"
        );

        return navigationBar;
    }

    /**
     * Creates the exam-details panel.
     *
     * @return details panel
     */
    private VBox createExamDetailsPanel() {

        Label panelTitle =
                new Label("Exam Details");

        panelTitle.getStyleClass().add(
                "page-title"
        );

        titleField =
                new TextField();

        titleField.setPromptText(
                "Exam title"
        );

        courseComboBox = new ComboBox<>();
        courseComboBox.setPromptText("Select course");
        courseComboBox.setMaxWidth(Double.MAX_VALUE);

        durationField =
                new TextField();

        durationField.setPromptText(
                "Duration in minutes"
        );

        instructionsArea =
                new TextArea();

        instructionsArea.setPromptText(
                "Instructions for students"
        );

        instructionsArea.setWrapText(true);
        instructionsArea.setPrefRowCount(5);

        GridPane form =
                new GridPane();

        form.setHgap(10);
        form.setVgap(12);

        addFormRow(
                form,
                0,
                "Title",
                titleField
        );

        addFormRow(
                form,
                1,
                "Course",
                courseComboBox
        );

        addFormRow(
                form,
                2,
                "Duration",
                durationField
        );

        addFormRow(
                form,
                3,
                "Instructions",
                instructionsArea
        );

        Label teacherLabel =
                new Label(
                        "Teacher: "
                                + getTeacherDisplayName()
                );

        teacherLabel.getStyleClass().add(
                "app-subtitle"
        );

        VBox panel =
                new VBox(
                        16,
                        panelTitle,
                        teacherLabel,
                        form
                );

        panel.getStyleClass().add(
                "question-panel"
        );

        panel.setPrefWidth(320);

        return panel;
    }

    /**
     * Creates the available-questions panel.
     *
     * @return available questions panel
     */
    private VBox createAvailableQuestionsPanel() {

        Label panelTitle =
                new Label("Available Questions");

        panelTitle.getStyleClass().add(
                "page-title"
        );

        availableQuestionsTable =
                createAvailableQuestionsTable();

        availableQuestionsTable.setItems(
                displayedQuestions
        );

        pointsField =
                new TextField();

        pointsField.setPromptText(
                "Points"
        );

        pointsField.setPrefWidth(90);

        Button addButton =
                new Button("Add to Exam");

        addButton.getStyleClass().add(
                "primary-button"
        );

        addButton.setOnAction(
                event -> addSelectedQuestion()
        );

        HBox addQuestionBar =
                new HBox(
                        10,
                        new Label("Points:"),
                        pointsField,
                        addButton
                );

        addQuestionBar.setAlignment(
                Pos.CENTER_LEFT
        );

        previousButton =
                new Button("Previous");

        previousButton.getStyleClass().add(
                "secondary-button"
        );

        previousButton.setOnAction(
                event -> showPreviousPage()
        );

        nextButton =
                new Button("Next");

        nextButton.getStyleClass().add(
                "primary-button"
        );

        nextButton.setOnAction(
                event -> showNextPage()
        );

        pageLabel =
                new Label("Page 0 of 0");

        pageLabel.getStyleClass().add(
                "form-label"
        );

        HBox paginationBar =
                new HBox(
                        14,
                        previousButton,
                        pageLabel,
                        nextButton
                );

        paginationBar.setAlignment(
                Pos.CENTER
        );

        VBox panel =
                new VBox(
                        14,
                        panelTitle,
                        availableQuestionsTable,
                        paginationBar,
                        addQuestionBar
                );

        VBox.setVgrow(
                availableQuestionsTable,
                Priority.ALWAYS
        );

        panel.getStyleClass().add(
                "question-panel"
        );

        panel.setPrefWidth(500);

        return panel;
    }

    /**
     * Creates the selected-questions panel.
     *
     * @return selected questions panel
     */
    private VBox createSelectedQuestionsPanel() {

        Label panelTitle =
                new Label("Selected Questions");

        panelTitle.getStyleClass().add(
                "page-title"
        );

        selectedQuestionsTable =
                createSelectedQuestionsTable();

        selectedQuestionsTable.setItems(
                selectedExamQuestions
        );

        Button removeButton =
                new Button("Remove Question");

        removeButton.getStyleClass().add(
                "danger-button"
        );

        removeButton.setOnAction(
                event -> removeSelectedQuestion()
        );

        totalPointsLabel =
                new Label("Total Points: 0 / 100");

        totalPointsLabel.getStyleClass().add(
                "form-label"
        );

        Button saveButton =
                new Button(
                        !isEditingSavedExam()
                                ? "Save Exam"
                                : "Update Exam"
                );

        saveButton.getStyleClass().add(
                "success-button"
        );

        saveButton.setPrefWidth(180);

        saveButton.setOnAction(
                event -> saveExam()
        );

        statusLabel =
                new Label();

        statusLabel.getStyleClass().add(
                "status-label"
        );

        statusLabel.setWrapText(true);

        HBox actions =
                new HBox(
                        10,
                        removeButton,
                        saveButton
                );

        actions.setAlignment(
                Pos.CENTER
        );

        VBox panel =
                new VBox(
                        14,
                        panelTitle,
                        selectedQuestionsTable,
                        totalPointsLabel,
                        actions,
                        statusLabel
                );

        VBox.setVgrow(
                selectedQuestionsTable,
                Priority.ALWAYS
        );

        panel.getStyleClass().add(
                "question-panel"
        );

        panel.setPrefWidth(440);

        return panel;
    }

    /**
     * Creates the available-questions table.
     *
     * @return available questions table
     */
    private TableView<Question> createAvailableQuestionsTable() {

        TableView<Question> table =
                new TableView<>();

        TableColumn<Question, Integer> idColumn =
                new TableColumn<>("ID");

        idColumn.setCellValueFactory(
                new PropertyValueFactory<>(
                        "id"
                )
        );

        TableColumn<Question, Integer> courseColumn =
                new TableColumn<>("Course");

        courseColumn.setCellValueFactory(
                new PropertyValueFactory<>(
                        "courseId"
                )
        );

        TableColumn<Question, String> textColumn =
                new TableColumn<>("Question");

        textColumn.setCellValueFactory(
                new PropertyValueFactory<>(
                        "questionText"
                )
        );

        idColumn.setPrefWidth(55);
        courseColumn.setPrefWidth(110);
        textColumn.setPrefWidth(300);

        table.getColumns().addAll(
                idColumn,
                courseColumn,
                textColumn
        );

        table.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY
        );

        table.setPlaceholder(
                new Label(
                        "No questions were loaded."
                )
        );

        return table;
    }

    /**
     * Creates the selected-questions table.
     *
     * @return selected questions table
     */
    private TableView<ExamQuestion> createSelectedQuestionsTable() {

        TableView<ExamQuestion> table =
                new TableView<>();

        TableColumn<ExamQuestion, Integer> idColumn =
                new TableColumn<>("ID");

        idColumn.setCellValueFactory(
                new PropertyValueFactory<>(
                        "questionId"
                )
        );

        TableColumn<ExamQuestion, String> textColumn =
                new TableColumn<>("Question");

        textColumn.setCellValueFactory(
                new PropertyValueFactory<>(
                        "questionText"
                )
        );

        TableColumn<ExamQuestion, Integer> pointsColumn =
                new TableColumn<>("Points");

        pointsColumn.setCellValueFactory(
                new PropertyValueFactory<>(
                        "points"
                )
        );

        idColumn.setPrefWidth(55);
        textColumn.setPrefWidth(270);
        pointsColumn.setPrefWidth(75);

        table.getColumns().addAll(
                idColumn,
                textColumn,
                pointsColumn
        );

        table.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY
        );

        table.setPlaceholder(
                new Label(
                        "No questions were selected."
                )
        );

        return table;
    }

    /**
     * Adds one labeled field to the form.
     *
     * @param form form grid
     * @param row row number
     * @param text label text
     * @param control form component
     */
    private void addFormRow(
            GridPane form,
            int row,
            String text,
            javafx.scene.Node control
    ) {

        Label label =
                new Label(text);

        label.getStyleClass().add(
                "form-label"
        );

        form.add(
                label,
                0,
                row
        );

        form.add(
                control,
                1,
                row
        );
    }

    /**
     * Adds the selected question to the exam.
     */
    private void addSelectedQuestion() {

        Question selectedQuestion =
                availableQuestionsTable
                        .getSelectionModel()
                        .getSelectedItem();

        if (selectedQuestion == null) {

            statusLabel.setText(
                    "Please select a question first."
            );

            return;
        }

        if (containsQuestion(
                selectedQuestion.getId()
        )) {

            statusLabel.setText(
                    "This question is already included in the exam."
            );

            return;
        }

        int points;

        try {

            points =
                    Integer.parseInt(
                            pointsField
                                    .getText()
                                    .trim()
                    );

        } catch (NumberFormatException exception) {

            statusLabel.setText(
                    "Points must be a valid whole number."
            );

            return;
        }

        if (points <= 0) {

            statusLabel.setText(
                    "Points must be greater than zero."
            );

            return;
        }

        int newTotal =
                calculateTotalPoints()
                        + points;

        if (newTotal > 100) {

            statusLabel.setText(
                    "The total exam score cannot exceed 100 points."
            );

            return;
        }

        selectedExamQuestions.add(
                new ExamQuestion(
                        selectedQuestion,
                        points
                )
        );

        availableQuestionsTable
                .getSelectionModel()
                .clearSelection();

        pointsField.clear();

        selectedQuestionsTable.refresh();

        updateTotalPointsLabel();

        statusLabel.setText(
                "Question added successfully."
        );
    }

    /**
     * Removes the selected question from the exam.
     */
    private void removeSelectedQuestion() {

        ExamQuestion selectedExamQuestion =
                selectedQuestionsTable
                        .getSelectionModel()
                        .getSelectedItem();

        if (selectedExamQuestion == null) {

            statusLabel.setText(
                    "Please select a question to remove."
            );

            return;
        }

        selectedExamQuestions.remove(
                selectedExamQuestion
        );

        selectedQuestionsTable.refresh();

        updateTotalPointsLabel();

        statusLabel.setText(
                "Question removed successfully."
        );
    }

    /**
     * Validates and saves the exam.
     */
    private void saveExam() {

        String title =
                titleField
                        .getText()
                        .trim();

        String course =
                courseComboBox.getValue();

        String instructions =
                instructionsArea
                        .getText()
                        .trim();

        if (title.isEmpty()) {

            statusLabel.setText(
                    "Exam title cannot be empty."
            );

            return;
        }

        if (course == null || course.isEmpty()) {

            statusLabel.setText(
                    "Please select a course."
            );

            return;
        }

        int durationMinutes;

        try {

            durationMinutes =
                    Integer.parseInt(
                            durationField
                                    .getText()
                                    .trim()
                    );

        } catch (NumberFormatException exception) {

            statusLabel.setText(
                    "Duration must be a valid whole number."
            );

            return;
        }

        if (durationMinutes <= 0) {

            statusLabel.setText(
                    "Duration must be greater than zero."
            );

            return;
        }

        if (selectedExamQuestions.isEmpty()) {

            statusLabel.setText(
                    "The exam must contain at least one question."
            );

            return;
        }

        int totalPoints =
                calculateTotalPoints();

        if (totalPoints != 100) {

            statusLabel.setText(
                    "The total exam score must equal exactly 100 points."
            );

            return;
        }

        // Identifier 0 means "new exam"; anything else names the exam this
        // one replaces. The caller routes on it: CREATE_EXAM or UPDATE_EXAM.
        int examId =
                examBeingEdited != null
                        ? examBeingEdited.getExamId()
                        : 0;

        Exam exam =
                new Exam(
                        examId,
                        title,
                        course,
                        instructions,
                        durationMinutes,
                        currentTeacher.getUserId(),
                        new ArrayList<>(
                                selectedExamQuestions
                        )
                );

        saveExamAction.accept(
                exam
        );

        statusLabel.setText(
                examId > 0
                        ? "The update request was sent."
                        : "The exam request was sent."
        );
    }

    /**
     * Checks whether a question is already selected.
     *
     * @param questionId question identifier
     * @return true if the question is already included
     */
    private boolean containsQuestion(
            int questionId
    ) {

        for (ExamQuestion examQuestion
                : selectedExamQuestions) {

            if (examQuestion.getQuestionId()
                    == questionId) {

                return true;
            }
        }

        return false;
    }

    /**
     * Calculates the total selected points.
     *
     * @return total points
     */
    private int calculateTotalPoints() {

        int totalPoints = 0;

        for (ExamQuestion examQuestion
                : selectedExamQuestions) {

            totalPoints +=
                    examQuestion.getPoints();
        }

        return totalPoints;
    }

    /**
     * Updates the total-points label.
     */
    private void updateTotalPointsLabel() {

        int totalPoints =
                calculateTotalPoints();

        totalPointsLabel.setText(
                "Total Points: "
                        + totalPoints
                        + " / 100"
        );
    }

    /**
     * Displays the next page of available questions.
     */
    private void showNextPage() {

        int totalPages =
                calculateTotalPages();

        if (currentPage
                < totalPages - 1) {

            currentPage++;

            displayCurrentQuestionPage();
        }
    }

    /**
     * Displays the previous page of available questions.
     */
    private void showPreviousPage() {

        if (currentPage > 0) {

            currentPage--;

            displayCurrentQuestionPage();
        }
    }

    /**
     * Displays up to six questions on the current page.
     */
    private void displayCurrentQuestionPage() {

        displayedQuestions.clear();

        if (allQuestions.isEmpty()) {

            currentPage = 0;

            updatePaginationControls();

            if (availableQuestionsTable != null) {
                availableQuestionsTable.refresh();
            }

            return;
        }

        int totalPages =
                calculateTotalPages();

        if (currentPage >= totalPages) {
            currentPage = totalPages - 1;
        }

        if (currentPage < 0) {
            currentPage = 0;
        }

        int startIndex =
                currentPage
                        * QUESTIONS_PER_PAGE;

        int endIndex =
                Math.min(
                        startIndex
                                + QUESTIONS_PER_PAGE,
                        allQuestions.size()
                );

        displayedQuestions.addAll(
                allQuestions.subList(
                        startIndex,
                        endIndex
                )
        );

        if (availableQuestionsTable != null) {

            availableQuestionsTable
                    .getSelectionModel()
                    .clearSelection();

            availableQuestionsTable.refresh();
        }

        updatePaginationControls();
    }

    /**
     * Calculates the number of available-question pages.
     *
     * @return total pages
     */
    private int calculateTotalPages() {

        if (allQuestions.isEmpty()) {
            return 0;
        }

        return (int) Math.ceil(
                allQuestions.size()
                        / (double) QUESTIONS_PER_PAGE
        );
    }

    /**
     * Updates the pagination controls.
     */
    private void updatePaginationControls() {

        if (previousButton == null
                || nextButton == null
                || pageLabel == null) {

            return;
        }

        int totalPages =
                calculateTotalPages();

        if (totalPages == 0) {

            pageLabel.setText(
                    "Page 0 of 0"
            );

            previousButton.setDisable(
                    true
            );

            nextButton.setDisable(
                    true
            );

            return;
        }

        pageLabel.setText(
                "Page "
                        + (currentPage + 1)
                        + " of "
                        + totalPages
        );

        previousButton.setDisable(
                currentPage == 0
        );

        nextButton.setDisable(
                currentPage
                        >= totalPages - 1
        );
    }

    /**
     * Populates the course dropdown with the available courses.
     *
     * @param courses list of courses from the server
     */
    public void setCourses(List<Course> courses) {
        if (courseComboBox == null || courses == null) return;
        String current = courseComboBox.getValue();
        courseComboBox.getItems().clear();
        for (Course c : courses) {
            courseComboBox.getItems().add(c.getCourseName());
        }
        if (current != null && courseComboBox.getItems().contains(current)) {
            courseComboBox.setValue(current);
        }
    }

    /**
     * Displays a status message on the screen.
     *
     * @param message message to display
     */
    public void setStatusMessage(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message);
        }
    }

    /**
     * Returns the teacher name displayed on the screen.
     *
     * @return teacher name
     */
    private String getTeacherDisplayName() {

        if (currentTeacher.getFullName() != null
                && !currentTeacher
                .getFullName()
                .trim()
                .isEmpty()) {

            return currentTeacher.getFullName();
        }

        return currentTeacher.getUsername();
    }
}