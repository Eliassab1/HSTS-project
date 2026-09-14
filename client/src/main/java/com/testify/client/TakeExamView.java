package com.testify.client;

import com.testify.common.Exam;
import com.testify.common.ExamQuestion;
import com.testify.common.ExamSubmission;
import com.testify.common.Question;
import com.testify.common.StudentAnswer;
import com.testify.common.User;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Represents the screen used by a student
 * while completing an exam in the HSTS system.
 *
 * The student can:
 * 1. View one question at a time.
 * 2. Select an answer.
 * 3. Save answers during the exam.
 * 4. Move between questions.
 * 5. Submit the exam permanently.
 */
public class TakeExamView extends BorderPane {

    /**
     * Currently logged-in student.
     */
    private final User currentStudent;

    /**
     * Exam currently being completed.
     */
    private final Exam exam;

    /**
     * Action called when the student performs final submission.
     */
    private final Consumer<ExamSubmission> submitAction;

    /**
     * Action called when returning to the Student Dashboard.
     */
    private final Runnable backAction;

    /**
     * Stores the selected answer for every question.
     *
     * Key: question ID
     * Value: A, B, C or D
     */
    private final Map<Integer, String> selectedAnswers;

    /**
     * Index of the question currently displayed.
     */
    private int currentQuestionIndex;

    /**
     * Time when the exam screen was opened.
     */
    private final long examStartTime;

    /**
     * Indicates whether the exam was finally submitted.
     */
    private boolean finalSubmissionCompleted;

    private Label examTitleLabel;
    private Label courseLabel;
    private Label durationLabel;
    private Label timerLabel;

    /** Live countdown for the exam duration; auto-submits when it reaches zero. */
    private Timeline countdownTimeline;
    private int remainingSeconds;
    private Label questionNumberLabel;
    private Label questionPointsLabel;
    private Label questionTextLabel;
    private ImageView questionImageView;
    private Label progressLabel;
    private Label statusLabel;

    private RadioButton answerARadioButton;
    private RadioButton answerBRadioButton;
    private RadioButton answerCRadioButton;
    private RadioButton answerDRadioButton;

    private ToggleGroup answersToggleGroup;

    private Button previousButton;
    private Button nextButton;
    private Button saveAnswersButton;
    private Button finalSubmissionButton;

    /**
     * Creates the exam-taking screen.
     *
     * @param currentStudent logged-in student
     * @param exam exam to complete
     * @param submitAction action used for final exam submission
     * @param backAction action used for returning to the dashboard
     */
    public TakeExamView(
            User currentStudent,
            Exam exam,
            Consumer<ExamSubmission> submitAction,
            Runnable backAction
    ) {

        if (currentStudent == null) {
            throw new IllegalArgumentException(
                    "Current student cannot be null."
            );
        }

        if (exam == null) {
            throw new IllegalArgumentException(
                    "Exam cannot be null."
            );
        }

        if (submitAction == null) {
            throw new IllegalArgumentException(
                    "Submit action cannot be null."
            );
        }

        if (backAction == null) {
            throw new IllegalArgumentException(
                    "Back action cannot be null."
            );
        }

        this.currentStudent = currentStudent;
        this.exam = exam;
        this.submitAction = submitAction;
        this.backAction = backAction;

        this.selectedAnswers = new HashMap<>();
        this.currentQuestionIndex = 0;
        this.examStartTime = System.currentTimeMillis();
        this.finalSubmissionCompleted = false;

        createScreen();
        displayCurrentQuestion();
        startCountdown();
    }

    /**
     * Creates the complete exam screen.
     */
    private void createScreen() {

        setPadding(new Insets(24));

        setTop(createHeader());
        setCenter(createQuestionPanel());
        setBottom(createNavigationPanel());

        BorderPane.setMargin(
                getCenter(),
                new Insets(20, 0, 20, 0)
        );
    }

    /**
     * Creates the top section of the exam screen.
     *
     * @return header
     */
    private HBox createHeader() {

        examTitleLabel =
                new Label(exam.getTitle());

        examTitleLabel.getStyleClass().add(
                "dashboard-title"
        );

        courseLabel =
                new Label(
                        "Course: " + exam.getCourse()
                );

        courseLabel.getStyleClass().add(
                "app-subtitle"
        );

        durationLabel =
                new Label(
                        "Duration: "
                                + exam.getDurationMinutes()
                                + " minutes"
                );

        durationLabel.getStyleClass().add(
                "app-subtitle"
        );

        VBox examInformation =
                new VBox(
                        5,
                        examTitleLabel,
                        courseLabel,
                        durationLabel
                );

        Region spacer =
                new Region();

        HBox.setHgrow(
                spacer,
                Priority.ALWAYS
        );

        // Live countdown clock.
        timerLabel = new Label("--:--");
        timerLabel.setStyle(
                "-fx-font-size: 26px; -fx-font-weight: bold;" +
                        "-fx-text-fill: #5B21B6; -fx-padding: 0 12px 0 0;");

        Button backButton =
                new Button("Back to Dashboard");

        backButton.getStyleClass().add(
                "secondary-button"
        );

        backButton.setOnAction(
                event -> confirmExitExam()
        );

        HBox header =
                new HBox(
                        15,
                        examInformation,
                        spacer,
                        timerLabel,
                        backButton
                );

        header.setAlignment(
                Pos.CENTER_LEFT
        );

        header.getStyleClass().add(
                "navigation-bar"
        );

        return header;
    }

    /**
     * Creates the panel that displays the current question.
     *
     * @return question panel
     */
    private VBox createQuestionPanel() {

        questionNumberLabel =
                new Label();

        questionNumberLabel.getStyleClass().add(
                "page-title"
        );

        questionPointsLabel =
                new Label();

        questionPointsLabel.getStyleClass().add(
                "app-subtitle"
        );

        questionTextLabel =
                new Label();

        questionTextLabel.setWrapText(true);
        questionTextLabel.setMaxWidth(850);

        questionTextLabel.setStyle(
                "-fx-font-size: 20px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-text-fill: #1e293b;" +
                        "-fx-padding: 15px 0;"
        );

        // Optional question image (visual aid). Hidden until a question with an
        // image is shown; it takes no layout space while hidden.
        questionImageView = new ImageView();
        questionImageView.setFitWidth(420);
        questionImageView.setPreserveRatio(true);
        questionImageView.setSmooth(true);
        questionImageView.setVisible(false);
        questionImageView.setManaged(false);

        answersToggleGroup =
                new ToggleGroup();

        answerARadioButton =
                createAnswerRadioButton("A");

        answerBRadioButton =
                createAnswerRadioButton("B");

        answerCRadioButton =
                createAnswerRadioButton("C");

        answerDRadioButton =
                createAnswerRadioButton("D");

        VBox answersBox =
                new VBox(
                        14,
                        answerARadioButton,
                        answerBRadioButton,
                        answerCRadioButton,
                        answerDRadioButton
                );

        answersBox.setPadding(
                new Insets(15)
        );

        progressLabel =
                new Label();

        progressLabel.getStyleClass().add(
                "form-label"
        );

        statusLabel =
                new Label();

        statusLabel.getStyleClass().add(
                "status-label"
        );

        statusLabel.setWrapText(true);

        VBox questionPanel =
                new VBox(
                        14,
                        questionNumberLabel,
                        questionPointsLabel,
                        questionTextLabel,
                        questionImageView,
                        answersBox,
                        progressLabel,
                        statusLabel
                );

        questionPanel.getStyleClass().add(
                "question-panel"
        );

        questionPanel.setAlignment(
                Pos.TOP_LEFT
        );

        questionPanel.setPadding(
                new Insets(30)
        );

        return questionPanel;
    }

    /**
     * Creates the navigation and submission buttons.
     *
     * @return navigation panel
     */
    private HBox createNavigationPanel() {

        previousButton =
                new Button("Previous");

        previousButton.getStyleClass().add(
                "secondary-button"
        );

        previousButton.setOnAction(
                event -> showPreviousQuestion()
        );

        nextButton =
                new Button("Next");

        nextButton.getStyleClass().add(
                "primary-button"
        );

        nextButton.setOnAction(
                event -> showNextQuestion()
        );

        saveAnswersButton =
                new Button("Save Answers");

        saveAnswersButton.getStyleClass().add(
                "primary-button"
        );

        saveAnswersButton.setOnAction(
                event -> saveAnswersManually()
        );

        finalSubmissionButton =
                new Button("Final Submission");

        finalSubmissionButton.getStyleClass().add(
                "success-button"
        );

        finalSubmissionButton.setOnAction(
                event -> confirmFinalSubmission()
        );

        Region spacer =
                new Region();

        HBox.setHgrow(
                spacer,
                Priority.ALWAYS
        );

        HBox navigationPanel =
                new HBox(
                        12,
                        previousButton,
                        nextButton,
                        spacer,
                        saveAnswersButton,
                        finalSubmissionButton
                );

        navigationPanel.setAlignment(
                Pos.CENTER_LEFT
        );

        navigationPanel.getStyleClass().add(
                "navigation-bar"
        );

        return navigationPanel;
    }

    /**
     * Creates one answer radio button.
     *
     * @param answerLetter answer letter
     * @return radio button
     */
    private RadioButton createAnswerRadioButton(
            String answerLetter
    ) {

        RadioButton radioButton =
                new RadioButton();

        radioButton.setUserData(
                answerLetter
        );

        radioButton.setToggleGroup(
                answersToggleGroup
        );

        radioButton.setWrapText(true);
        radioButton.setMaxWidth(850);

        radioButton.setStyle(
                "-fx-font-size: 16px;" +
                        "-fx-padding: 10px;" +
                        "-fx-cursor: hand;"
        );

        return radioButton;
    }

    /**
     * Displays the current exam question.
     */
    private void displayCurrentQuestion() {

        List<ExamQuestion> examQuestions =
                exam.getExamQuestions();

        if (examQuestions == null
                || examQuestions.isEmpty()) {

            questionNumberLabel.setText(
                    "No questions available"
            );

            questionTextLabel.setText(
                    "This exam does not contain questions."
            );

            updateQuestionImage(null);

            previousButton.setDisable(true);
            nextButton.setDisable(true);
            saveAnswersButton.setDisable(true);
            finalSubmissionButton.setDisable(true);

            return;
        }

        if (currentQuestionIndex < 0) {
            currentQuestionIndex = 0;
        }

        if (currentQuestionIndex
                >= examQuestions.size()) {

            currentQuestionIndex =
                    examQuestions.size() - 1;
        }

        ExamQuestion examQuestion =
                examQuestions.get(
                        currentQuestionIndex
                );

        Question question =
                examQuestion.getQuestion();

        if (question == null) {

            questionNumberLabel.setText(
                    "Question unavailable"
            );

            questionTextLabel.setText(
                    "The selected question could not be loaded."
            );

            updateQuestionImage(null);

            return;
        }

        questionNumberLabel.setText(
                "Question "
                        + (currentQuestionIndex + 1)
                        + " of "
                        + examQuestions.size()
        );

        questionPointsLabel.setText(
                "Points: "
                        + examQuestion.getPoints()
        );

        questionTextLabel.setText(
                question.getQuestionText()
        );

        updateQuestionImage(question);

        answerARadioButton.setText(
                "A. " + question.getOptionA()
        );

        answerBRadioButton.setText(
                "B. " + question.getOptionB()
        );

        answerCRadioButton.setText(
                "C. " + question.getOptionC()
        );

        answerDRadioButton.setText(
                "D. " + question.getOptionD()
        );

        restoreSavedAnswer(
                question.getId()
        );

        updateNavigationButtons();
        updateProgressLabel();

        statusLabel.setText("");
    }

    /**
     * Shows the question's visual aid image (if any), else hides the ImageView.
     * The image is resolved through {@link ImageResolver}, so a bare filename
     * stored on the question loads from the project's local {@code images/} folder.
     *
     * @param question current question, or {@code null} to hide the image
     */
    private void updateQuestionImage(Question question) {

        if (questionImageView == null) {
            return;
        }

        String resolved = question == null
                ? null
                : ImageResolver.resolve(question.getVisualAidUrl());

        if (resolved != null) {
            try {
                Image image = new Image(resolved, 420, 0, true, true, false);
                if (!image.isError()) {
                    questionImageView.setImage(image);
                    questionImageView.setVisible(true);
                    questionImageView.setManaged(true);
                    return;
                }
            } catch (Exception ignored) {
                // fall through to hide
            }
        }

        questionImageView.setImage(null);
        questionImageView.setVisible(false);
        questionImageView.setManaged(false);
    }

    /**
     * Saves the answer selected for the current question.
     *
     * @return true if an answer was selected and saved
     */
    private boolean saveCurrentAnswer() {

        List<ExamQuestion> examQuestions =
                exam.getExamQuestions();

        if (examQuestions == null
                || examQuestions.isEmpty()) {

            return false;
        }

        ExamQuestion currentExamQuestion =
                examQuestions.get(
                        currentQuestionIndex
                );

        Question question =
                currentExamQuestion.getQuestion();

        if (question == null) {
            return false;
        }

        if (answersToggleGroup.getSelectedToggle()
                == null) {

            return false;
        }

        String selectedAnswer =
                answersToggleGroup
                        .getSelectedToggle()
                        .getUserData()
                        .toString();

        selectedAnswers.put(
                question.getId(),
                selectedAnswer
        );

        return true;
    }

    /**
     * Saves the current answer when the student presses Save Answers.
     */
    private void saveAnswersManually() {

        if (finalSubmissionCompleted) {

            statusLabel.setText(
                    "The exam has already been submitted."
            );

            return;
        }

        boolean saved =
                saveCurrentAnswer();

        if (!saved) {

            statusLabel.setText(
                    "Please select an answer before saving."
            );

            return;
        }

        updateProgressLabel();

        statusLabel.setText(
                "Answers saved successfully. You may continue the exam."
        );
    }

    /**
     * Restores a previously selected answer.
     *
     * @param questionId question identifier
     */
    private void restoreSavedAnswer(
            int questionId
    ) {

        answersToggleGroup.selectToggle(
                null
        );

        String savedAnswer =
                selectedAnswers.get(
                        questionId
                );

        if (savedAnswer == null) {
            return;
        }

        switch (savedAnswer) {

            case "A" ->
                    answersToggleGroup.selectToggle(
                            answerARadioButton
                    );

            case "B" ->
                    answersToggleGroup.selectToggle(
                            answerBRadioButton
                    );

            case "C" ->
                    answersToggleGroup.selectToggle(
                            answerCRadioButton
                    );

            case "D" ->
                    answersToggleGroup.selectToggle(
                            answerDRadioButton
                    );

            default -> {
            }
        }
    }

    /**
     * Displays the next question.
     */
    private void showNextQuestion() {

        if (finalSubmissionCompleted) {
            return;
        }

        saveCurrentAnswer();

        if (currentQuestionIndex
                < exam.getExamQuestions().size() - 1) {

            currentQuestionIndex++;
            displayCurrentQuestion();
        }
    }

    /**
     * Displays the previous question.
     */
    private void showPreviousQuestion() {

        if (finalSubmissionCompleted) {
            return;
        }

        saveCurrentAnswer();

        if (currentQuestionIndex > 0) {

            currentQuestionIndex--;
            displayCurrentQuestion();
        }
    }

    /**
     * Updates the Previous and Next buttons.
     */
    private void updateNavigationButtons() {

        int questionCount =
                exam.getExamQuestions().size();

        previousButton.setDisable(
                finalSubmissionCompleted
                        || currentQuestionIndex == 0
        );

        nextButton.setDisable(
                finalSubmissionCompleted
                        || currentQuestionIndex
                        >= questionCount - 1
        );
    }

    /**
     * Updates the answered-question progress label.
     */
    private void updateProgressLabel() {

        progressLabel.setText(
                "Saved answers: "
                        + selectedAnswers.size()
                        + " of "
                        + exam.getExamQuestions().size()
        );
    }

    /**
     * Starts the exam countdown clock and schedules automatic submission when
     * the exam duration elapses. No-op if the duration is not positive.
     */
    private void startCountdown() {

        int durationMinutes = exam.getDurationMinutes();
        if (durationMinutes <= 0) {
            if (timerLabel != null) {
                timerLabel.setText("--:--");
            }
            return;
        }

        remainingSeconds = durationMinutes * 60;
        updateTimerLabel();

        countdownTimeline = new Timeline(
                new KeyFrame(Duration.seconds(1), event -> onCountdownTick()));
        countdownTimeline.setCycleCount(Animation.INDEFINITE);
        countdownTimeline.play();
    }

    /**
     * One-second tick: decrements the remaining time, refreshes the display and
     * auto-submits when the clock reaches zero.
     */
    private void onCountdownTick() {

        remainingSeconds--;

        if (remainingSeconds <= 0) {
            remainingSeconds = 0;
            updateTimerLabel();
            stopCountdown();
            handleTimeUp();
            return;
        }

        updateTimerLabel();
    }

    /**
     * Formats the remaining time as MM:SS and turns the clock red in the final
     * minute.
     */
    private void updateTimerLabel() {

        if (timerLabel == null) {
            return;
        }

        int minutes = remainingSeconds / 60;
        int seconds = remainingSeconds % 60;
        timerLabel.setText(String.format("%02d:%02d", minutes, seconds));

        String colour = remainingSeconds <= 60 ? "-hsts-negative" : "#5B21B6";
        timerLabel.setStyle(
                "-fx-font-size: 26px; -fx-font-weight: bold;" +
                        "-fx-text-fill: " + colour + "; -fx-padding: 0 12px 0 0;");
    }

    /**
     * Adds time to the running countdown, in response to the teacher
     * extending the exam (spec 7).
     *
     * Two states must NOT be revived. A student whose clock already hit zero
     * auto-submitted, and one who submitted by hand is equally finished —
     * neither gets more time, because their answers are already scored.
     * {@code countdownTimeline} being null is what "the clock is no longer
     * running" looks like, and it is the same field {@link #stopCountdown()}
     * clears in both cases.
     *
     * Safe to call while the Timeline is running: it only moves the counter
     * the ticks read. Must be called on the JavaFX thread, which it is —
     * {@code ClientApplication.handleServerResponse} wraps everything in
     * {@code Platform.runLater}.
     *
     * @param extraMinutes minutes to add; ignored when not positive
     * @return true if the time was added, false if the exam was already over
     */
    public boolean addMinutes(int extraMinutes) {

        if (extraMinutes <= 0) {
            return false;
        }

        if (finalSubmissionCompleted || countdownTimeline == null) {
            return false;
        }

        remainingSeconds += extraMinutes * 60;
        updateTimerLabel();

        if (statusLabel != null) {
            statusLabel.setText(
                    "Your teacher added " + extraMinutes + " minute"
                            + (extraMinutes == 1 ? "" : "s")
                            + " to this exam. Your remaining time has gone up."
            );
            // A dialog would steal focus in the middle of a timed exam, so the
            // notice is loud in place instead of interrupting.
            statusLabel.setStyle(
                    "-fx-font-size: 14px; -fx-font-weight: bold;"
                            + "-fx-text-fill: -hsts-positive;"
                            + "-fx-background-color: -hsts-positive-soft;"
                            + "-fx-background-radius: 8px; -fx-padding: 8px 12px;"
            );
        }

        return true;
    }

    /** Stops the countdown if it is running. */
    private void stopCountdown() {
        if (countdownTimeline != null) {
            countdownTimeline.stop();
            countdownTimeline = null;
        }
    }

    /**
     * Handles time expiry: saves the currently selected answer and submits the
     * exam automatically (no confirmation dialog).
     */
    private void handleTimeUp() {

        if (finalSubmissionCompleted) {
            return;
        }

        saveCurrentAnswer();
        statusLabel.setText("Time is up. Submitting your exam automatically…");
        performFinalSubmission();
    }

    /**
     * Asks the student to confirm final submission.
     */
    private void confirmFinalSubmission() {

        if (finalSubmissionCompleted) {

            statusLabel.setText(
                    "The exam has already been submitted."
            );

            return;
        }

        saveCurrentAnswer();

        int totalQuestions =
                exam.getExamQuestions().size();

        int answeredQuestions =
                selectedAnswers.size();

        String message;

        if (answeredQuestions < totalQuestions) {

            message =
                    "You saved answers for "
                            + answeredQuestions
                            + " of "
                            + totalQuestions
                            + " questions.\n\n"
                            + "Final submission cannot be cancelled.\n"
                            + "Do you want to submit the exam?";

        } else {

            message =
                    "All answers were saved.\n\n"
                            + "Final submission cannot be cancelled.\n"
                            + "Do you want to submit the exam?";
        }

        Alert confirmationAlert =
                new Alert(
                        Alert.AlertType.CONFIRMATION
                );

        confirmationAlert.setTitle(
                "Final Submission"
        );

        confirmationAlert.setHeaderText(
                "Confirm final exam submission"
        );

        confirmationAlert.setContentText(
                message
        );

        confirmationAlert
                .showAndWait()
                .ifPresent(buttonType -> {

                    if (buttonType == ButtonType.OK) {
                        performFinalSubmission();
                    }
                });
    }

    /**
     * Creates and sends the final exam submission.
     */
    private void performFinalSubmission() {

        stopCountdown();

        List<StudentAnswer> answers =
                new ArrayList<>();

        for (Map.Entry<Integer, String> entry
                : selectedAnswers.entrySet()) {

            answers.add(
                    new StudentAnswer(
                            entry.getKey(),
                            entry.getValue()
                    )
            );
        }

        int usedMinutes =
                calculateUsedMinutes();

        ExamSubmission submission =
                new ExamSubmission(
                        exam.getExamId(),
                        currentStudent.getUserId(),
                        answers,
                        usedMinutes
                );

        statusLabel.setText(
                "Submitting exam, please wait..."
        );

        submitAction.accept(
                submission
        );
    }

    /**
     * Disables all exam controls after final submission.
     */
    private void disableExamControls() {

        previousButton.setDisable(true);
        nextButton.setDisable(true);
        saveAnswersButton.setDisable(true);
        finalSubmissionButton.setDisable(true);

        answerARadioButton.setDisable(true);
        answerBRadioButton.setDisable(true);
        answerCRadioButton.setDisable(true);
        answerDRadioButton.setDisable(true);
    }

    /**
     * Calculates how many minutes the student used.
     *
     * @return used minutes
     */
    private int calculateUsedMinutes() {

        long elapsedMilliseconds =
                System.currentTimeMillis()
                        - examStartTime;

        long elapsedMinutes =
                elapsedMilliseconds / 60_000;

        if (elapsedMinutes <= 0) {
            return 1;
        }

        if (elapsedMinutes
                > exam.getDurationMinutes()) {

            return exam.getDurationMinutes();
        }

        return (int) elapsedMinutes;
    }

    /**
     * Asks for confirmation before leaving the exam.
     */
    private void confirmExitExam() {

        if (finalSubmissionCompleted) {
            stopCountdown();
            backAction.run();
            return;
        }

        Alert confirmationAlert =
                new Alert(
                        Alert.AlertType.CONFIRMATION
                );

        confirmationAlert.setTitle(
                "Exit Exam"
        );

        confirmationAlert.setHeaderText(
                "Leave the current exam?"
        );

        confirmationAlert.setContentText(
                "Answers are stored only while this exam screen is open. "
                        + "Leaving before final submission will cancel the attempt."
        );

        confirmationAlert
                .showAndWait()
                .ifPresent(buttonType -> {

                    if (buttonType == ButtonType.OK) {
                        stopCountdown();
                        backAction.run();
                    }
                });
    }

    /**
     * Locks the exam screen after the server confirms submission.
     *
     * <p>The message is a submission receipt, not a grade: since the
     * grade-approval gate was added the server no longer returns a score at
     * submit time, and the student is told the grade will appear once the
     * teacher has approved it.
     *
     * @param submissionMessage confirmation message received from the server
     */
    public void lockAfterSubmission(String submissionMessage) {
        finalSubmissionCompleted = true;
        stopCountdown();
        disableExamControls();
        statusLabel.setText(submissionMessage);
    }

    /**
     * Displays a message on the exam screen.
     *
     * @param message message to display
     */
    public void setStatusMessage(
            String message
    ) {

        statusLabel.setText(
                message == null
                        ? ""
                        : message
        );
    }
}