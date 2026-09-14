package com.testify.client;

import com.testify.common.Question;
import com.testify.common.ReviewedQuestion;
import com.testify.common.SubmissionReview;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Student – checked exam form (spec 9.2).
 *
 * Shows the approved grade and then every question of the exam with the four
 * options, which one the student chose, which one was right, what the
 * question was worth and whether the points were awarded.
 *
 * The server only builds a {@link SubmissionReview} for the student's own
 * already-approved submission, so nothing shown here can belong to another
 * student or to an exam still awaiting approval.
 */
public class SubmissionReviewView extends HBox {

    private final SubmissionReview review;
    private final Runnable backAction;

    public SubmissionReviewView(
            SubmissionReview review,
            VBox sidebar,
            Runnable backAction
    ) {
        if (review == null)     throw new IllegalArgumentException("Review cannot be null.");
        if (backAction == null) throw new IllegalArgumentException("Back action cannot be null.");

        this.review     = review;
        this.backAction = backAction;

        buildUI(sidebar);
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

    private VBox buildMain() {

        Label title = new Label("Checked Exam — " + safe(review.getExamTitle()));
        title.getStyleClass().add("welcome-label");

        // Summary cards: the grade, how many questions were right, and the
        // count of questions on the paper.
        Label gradeValue = new Label(String.format("%.1f / %.0f",
                review.getScore(), review.getMaxScore()));
        Label correctValue = new Label(review.countCorrect()
                + " / " + review.getQuestions().size());
        Label statusValue = new Label(review.getScore() >= 60.0 ? "Passed" : "Failed");

        HBox summary = new HBox(14,
                statCard("Final Grade", gradeValue, "approved by your teacher"),
                statCard("Correct Answers", correctValue, "of all questions"),
                statCard("Result", statusValue, "pass mark is 60"));
        summary.setAlignment(Pos.CENTER_LEFT);

        VBox questionList = new VBox(14);
        questionList.setPadding(new Insets(4, 4, 4, 0));

        int number = 1;
        for (ReviewedQuestion rq : review.getQuestions()) {
            questionList.getChildren().add(buildQuestionCard(number++, rq));
        }
        if (review.getQuestions().isEmpty()) {
            Label empty = new Label("This submission has no recorded questions.");
            empty.getStyleClass().add("status-label");
            questionList.getChildren().add(empty);
        }

        ScrollPane scroll = new ScrollPane(questionList);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Button backBtn = new Button("Back to My Grades");
        backBtn.getStyleClass().add("secondary-button");
        backBtn.setOnAction(e -> backAction.run());

        HBox actions = new HBox(12, backBtn);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox main = new VBox(16, title, summary, scroll, actions);
        main.setPadding(new Insets(0));
        return main;
    }

    // ── One checked question ──────────────────────────────────────────────────

    private VBox buildQuestionCard(int number, ReviewedQuestion rq) {

        Question q = rq.getQuestion();

        Label heading = new Label("Question " + number);
        heading.getStyleClass().add("section-title");

        Label verdict = new Label(rq.isCorrect()
                ? String.format("✓  +%.1f points", rq.getPointsWorth())
                : String.format("✗  0 of %.1f points", rq.getPointsWorth()));
        verdict.setStyle("-fx-font-weight: bold; -fx-text-fill: "
                + (rq.isCorrect() ? "-hsts-positive" : "-hsts-negative") + ";");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox headingRow = new HBox(10, heading, spacer, verdict);
        headingRow.setAlignment(Pos.CENTER_LEFT);

        Label text = new Label(q != null ? safe(q.getQuestionText()) : "(question unavailable)");
        text.setWrapText(true);
        text.setStyle("-fx-text-fill: -hsts-strong; -fx-font-size: 14px;");

        VBox options = new VBox(6);
        if (q != null) {
            options.getChildren().addAll(
                    buildOptionRow("A", q.getOptionA(), rq),
                    buildOptionRow("B", q.getOptionB(), rq),
                    buildOptionRow("C", q.getOptionC(), rq),
                    buildOptionRow("D", q.getOptionD(), rq));
        }

        Label answered = new Label(rq.getStudentAnswer() == null || rq.getStudentAnswer().isBlank()
                ? "You did not answer this question.  Correct answer: " + safe(rq.getCorrectAnswer())
                : "Your answer: " + rq.getStudentAnswer()
                        + "     Correct answer: " + safe(rq.getCorrectAnswer()));
        answered.setStyle("-fx-text-fill: -hsts-muted;");

        VBox card = new VBox(10, headingRow, text, options, answered);
        card.getStyleClass().add("section-card");
        card.setPadding(new Insets(16));
        return card;
    }

    /**
     * Renders one option, marked up so the right answer and the student's own
     * choice are both visible at a glance.
     */
    private Label buildOptionRow(String letter, String optionText, ReviewedQuestion rq) {

        boolean isCorrectOption = letter.equalsIgnoreCase(rq.getCorrectAnswer());
        boolean isChosenOption  = letter.equalsIgnoreCase(rq.getStudentAnswer());

        StringBuilder line = new StringBuilder();
        line.append(letter).append(".  ").append(safe(optionText));
        if (isCorrectOption) line.append("     ← correct answer");
        if (isChosenOption && !isCorrectOption) line.append("     ← your answer");
        if (isChosenOption && isCorrectOption) line.append(" (your answer)");

        Label label = new Label(line.toString());
        label.setWrapText(true);

        String style;
        if (isCorrectOption) {
            style = "-fx-text-fill: -hsts-positive; -fx-font-weight: bold;";
        } else if (isChosenOption) {
            style = "-fx-text-fill: -hsts-negative; -fx-font-weight: bold;";
        } else {
            style = "-fx-text-fill: -hsts-muted;";
        }
        label.setStyle(style + " -fx-padding: 2 0 2 8;");
        return label;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private VBox statCard(String label, Label valueLabel, String sub) {
        valueLabel.getStyleClass().add("stat-value");
        Label lbl  = new Label(label); lbl.getStyleClass().add("stat-label");
        Label subL = new Label(sub);   subL.getStyleClass().add("stat-sub");

        Label dot = new Label("●");
        dot.setStyle("-fx-font-size: 26px; -fx-text-fill: #DDD6FE;");

        HBox top = new HBox(10, dot, new VBox(2, lbl, valueLabel, subL));
        top.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(top);
        card.getStyleClass().add("stat-card");
        return card;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
