package com.testify.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for the summary statistics shared by the principal's comparison
 * reports and the teacher's per-exam results screen.
 *
 * The median is the interesting part: MySQL has no MEDIAN aggregate, so it is
 * computed here in Java, and both screens depend on this one implementation
 * agreeing with itself.
 */
class ExamStatisticsTest {

    private static final double PRECISION = 1e-9;

    @Nested
    @DisplayName("median")
    class Median {

        @Test
        @DisplayName("an odd count takes the middle value")
        void oddCountTakesMiddle() {
            ExamStatistics stats =
                    ExamStatistics.summarise("Maths", List.of(50.0, 70.0, 90.0));

            assertEquals(70.0, stats.getMedian(), PRECISION);
        }

        @Test
        @DisplayName("an even count averages the two middle values")
        void evenCountAveragesMiddlePair() {
            ExamStatistics stats =
                    ExamStatistics.summarise("Maths", List.of(50.0, 60.0, 80.0, 90.0));

            assertEquals(70.0, stats.getMedian(), PRECISION);
        }

        @Test
        @DisplayName("unsorted input still yields the correct median")
        void unsortedInputIsSortedFirst() {
            // The guarantee that matters: callers hand over scores in whatever
            // order the database returned them. Taking the middle element of an
            // unsorted list would silently return an arbitrary score.
            ExamStatistics stats =
                    ExamStatistics.summarise("Maths", List.of(90.0, 50.0, 70.0));

            assertEquals(70.0, stats.getMedian(), PRECISION);
        }

        @Test
        @DisplayName("a two-score median is their mean")
        void twoScores() {
            ExamStatistics stats =
                    ExamStatistics.summarise("Maths", List.of(40.0, 100.0));

            assertEquals(70.0, stats.getMedian(), PRECISION);
        }

        @Test
        @DisplayName("median and average differ on a skewed spread")
        void medianIsNotTheAverage() {
            // One very low score drags the mean but not the median; a test where
            // they coincide would pass even if the median were computed as a mean.
            ExamStatistics stats =
                    ExamStatistics.summarise("Maths", List.of(0.0, 90.0, 95.0, 100.0));

            assertEquals(92.5, stats.getMedian(), PRECISION);
            assertEquals(71.25, stats.getAverage(), PRECISION);
        }
    }

    @Nested
    @DisplayName("the caller's list")
    class CallerList {

        @Test
        @DisplayName("is not reordered by summarising it")
        void doesNotMutateCallersList() {
            // summarise sorts a copy. Sorting the caller's list in place would
            // silently reorder whatever the DAO is still holding.
            List<Double> scores = new ArrayList<>(List.of(90.0, 50.0, 70.0));
            List<Double> before = new ArrayList<>(scores);

            ExamStatistics.summarise("Maths", scores);

            assertEquals(before, scores);
        }
    }

    @Nested
    @DisplayName("aggregates")
    class Aggregates {

        @Test
        @DisplayName("average, min, max and count come from the whole set")
        void basicAggregates() {
            ExamStatistics stats = ExamStatistics.summarise(
                    "Physics", List.of(60.0, 70.0, 80.0, 90.0));

            assertEquals("Physics", stats.getLabel());
            assertEquals(75.0, stats.getAverage(), PRECISION);
            assertEquals(60.0, stats.getMinScore(), PRECISION);
            assertEquals(90.0, stats.getMaxScore(), PRECISION);
            assertEquals(4, stats.getSubmissionCount());
        }

        @Test
        @DisplayName("range is max minus min")
        void rangeIsDerived() {
            ExamStatistics stats =
                    ExamStatistics.summarise("Physics", List.of(42.0, 88.0));

            assertEquals(46.0, stats.getRange(), PRECISION);
        }

        @Test
        @DisplayName("a single score is its own average, median, min and max")
        void singleScore() {
            ExamStatistics stats =
                    ExamStatistics.summarise("Physics", List.of(64.0));

            assertEquals(64.0, stats.getAverage(), PRECISION);
            assertEquals(64.0, stats.getMedian(), PRECISION);
            assertEquals(64.0, stats.getMinScore(), PRECISION);
            assertEquals(64.0, stats.getMaxScore(), PRECISION);
            assertEquals(0.0, stats.getRange(), PRECISION);
            assertEquals(1, stats.getSubmissionCount());
        }

        @Test
        @DisplayName("identical scores give a zero range")
        void identicalScores() {
            ExamStatistics stats =
                    ExamStatistics.summarise("Physics", List.of(75.0, 75.0, 75.0));

            assertEquals(75.0, stats.getAverage(), PRECISION);
            assertEquals(75.0, stats.getMedian(), PRECISION);
            assertEquals(0.0, stats.getRange(), PRECISION);
        }
    }

    @Nested
    @DisplayName("no submissions")
    class Empty {

        @Test
        @DisplayName("an empty list gives a zeroed row that keeps its label")
        void emptyListIsZeroed() {
            // An exam nobody has sat still needs a row in a comparison report,
            // so this returns zeros rather than null or an exception.
            ExamStatistics stats = ExamStatistics.summarise("Untaken", List.of());

            assertEquals("Untaken", stats.getLabel());
            assertEquals(0, stats.getSubmissionCount());
            assertEquals(0.0, stats.getAverage(), PRECISION);
            assertEquals(0.0, stats.getMedian(), PRECISION);
            assertEquals(0.0, stats.getRange(), PRECISION);
        }

        @Test
        @DisplayName("a null list behaves like an empty one")
        void nullListIsZeroed() {
            ExamStatistics stats = ExamStatistics.summarise("Untaken", null);

            assertEquals(0, stats.getSubmissionCount());
            assertEquals(0.0, stats.getAverage(), PRECISION);
        }
    }
}
