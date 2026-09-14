package com.testify.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for the derived values on the result models — the small computations
 * the report screens and the marked-paper view read straight off the object.
 */
class ResultModelsTest {

    private static final double PRECISION = 1e-9;

    private static ReviewedQuestion answered(
            String studentAnswer, String correctAnswer, double points) {

        return new ReviewedQuestion(
                new Question(), studentAnswer, correctAnswer, points,
                correctAnswer.equals(studentAnswer));
    }

    @Nested
    @DisplayName("PassFailStat")
    class PassFail {

        @Test
        @DisplayName("total is passes plus failures")
        void totalIsSum() {
            assertEquals(10, new PassFailStat("Maths", 7, 3).getTotalCount());
        }

        @Test
        @DisplayName("pass rate is a percentage of the total")
        void passRateIsPercentage() {
            assertEquals(70.0,
                    new PassFailStat("Maths", 7, 3).getPassRate(), PRECISION);
        }

        @Test
        @DisplayName("a course nobody has sat reports zero, not a division by zero")
        void noSubmissionsIsZeroNotNaN() {
            PassFailStat stat = new PassFailStat("Untaken", 0, 0);

            assertEquals(0, stat.getTotalCount());
            assertEquals(0.0, stat.getPassRate(), PRECISION);
        }

        @Test
        @DisplayName("all passing is 100 percent and all failing is zero")
        void extremes() {
            assertEquals(100.0,
                    new PassFailStat("Maths", 5, 0).getPassRate(), PRECISION);
            assertEquals(0.0,
                    new PassFailStat("Maths", 0, 5).getPassRate(), PRECISION);
        }

        @Test
        @DisplayName("a repeating rate is not rounded away")
        void repeatingRate() {
            // 1 of 3 is 33.33...; the stat carries full precision and leaves
            // formatting to the screen that displays it.
            assertEquals(100.0 / 3.0,
                    new PassFailStat("Maths", 1, 2).getPassRate(), PRECISION);
        }
    }

    @Nested
    @DisplayName("ReviewedQuestion")
    class Reviewed {

        @Test
        @DisplayName("a correct answer earns the question's full points")
        void correctEarnsFullPoints() {
            assertEquals(10.0, answered("B", "B", 10.0).getPointsEarned(), PRECISION);
        }

        @Test
        @DisplayName("a wrong answer earns nothing")
        void wrongEarnsNothing() {
            assertEquals(0.0, answered("A", "B", 10.0).getPointsEarned(), PRECISION);
        }

        @Test
        @DisplayName("an unanswered question earns nothing and keeps a null answer")
        void unansweredEarnsNothing() {
            // The marked paper distinguishes "wrong" from "left blank", so the
            // null must survive rather than being normalised to an empty string.
            ReviewedQuestion question =
                    new ReviewedQuestion(new Question(), null, "C", 10.0, false);

            assertEquals(0.0, question.getPointsEarned(), PRECISION);
            assertEquals(null, question.getStudentAnswer());
        }

        @Test
        @DisplayName("points earned follow the correct flag, not a re-comparison")
        void pointsFollowTheStoredFlag() {
            // The server decides correctness; the model reports it. If these
            // ever disagreed, the marked paper would contradict the grade.
            ReviewedQuestion marked =
                    new ReviewedQuestion(new Question(), "A", "B", 7.5, true);

            assertEquals(7.5, marked.getPointsEarned(), PRECISION);
        }
    }

    @Nested
    @DisplayName("SubmissionReview")
    class Review {

        @Test
        @DisplayName("counts only the questions marked correct")
        void countsCorrectOnly() {
            SubmissionReview review = new SubmissionReview(
                    1, "Maths Mid-Term", 60.0, 100.0, "GRADED",
                    List.of(
                            answered("A", "A", 20.0),
                            answered("B", "C", 20.0),
                            answered("D", "D", 20.0)));

            assertEquals(2, review.countCorrect());
        }

        @Test
        @DisplayName("a paper with nothing correct counts zero")
        void noneCorrect() {
            SubmissionReview review = new SubmissionReview(
                    2, "Maths Mid-Term", 0.0, 100.0, "GRADED",
                    List.of(answered("A", "B", 50.0), answered("C", "D", 50.0)));

            assertEquals(0, review.countCorrect());
        }

        @Test
        @DisplayName("a review with no questions counts zero")
        void noQuestions() {
            SubmissionReview review = new SubmissionReview(
                    3, "Empty", 0.0, 0.0, "GRADED", List.of());

            assertEquals(0, review.countCorrect());
        }
    }
}
