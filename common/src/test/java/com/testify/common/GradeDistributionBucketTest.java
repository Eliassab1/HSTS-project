package com.testify.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests for the histogram banding.
 *
 * This is shared banding: the server buckets whole-system distributions and
 * the client buckets one exam's filtered rows. Both call this method, so the
 * boundaries it picks are the contract between the two histograms.
 */
class GradeDistributionBucketTest {

    /** Convenience: the count in band {@code index} for the given scores. */
    private static int countIn(int index, Double... scores) {
        return GradeDistributionBucket.bucketize(Arrays.asList(scores))
                .get(index).getCount();
    }

    @Nested
    @DisplayName("shape of the returned bands")
    class Shape {

        @Test
        @DisplayName("always returns exactly ten bands, even with no data")
        void alwaysTenBands() {
            // A histogram whose shape changed with the data would jump around
            // between refreshes, so empty bands are returned rather than omitted.
            assertEquals(10, GradeDistributionBucket.bucketize(List.of()).size());
            assertEquals(10, GradeDistributionBucket.bucketize(null).size());
            assertEquals(10, GradeDistributionBucket.bucketize(List.of(55.0)).size());
        }

        @Test
        @DisplayName("bands are labelled 0-9 through 90-100")
        void bandLabels() {
            List<GradeDistributionBucket> bands =
                    GradeDistributionBucket.bucketize(List.of());

            assertEquals("0-9", bands.get(0).getLabel());
            assertEquals("10-19", bands.get(1).getLabel());
            assertEquals("80-89", bands.get(8).getLabel());
            assertEquals("90-100", bands.get(9).getLabel());
        }

        @Test
        @DisplayName("the top band is eleven points wide so 100 has somewhere to land")
        void topBandIsWider() {
            List<GradeDistributionBucket> bands =
                    GradeDistributionBucket.bucketize(List.of());

            assertEquals(90, bands.get(9).getLowerBound());
            assertEquals(100, bands.get(9).getUpperBound());

            // every other band is exactly ten wide
            for (int i = 0; i < 9; i++) {
                assertEquals(i * 10, bands.get(i).getLowerBound());
                assertEquals(i * 10 + 9, bands.get(i).getUpperBound());
            }
        }

        @Test
        @DisplayName("an empty input leaves every band at zero")
        void emptyInputCountsZero() {
            for (GradeDistributionBucket band
                    : GradeDistributionBucket.bucketize(List.of())) {
                assertEquals(0, band.getCount(), band.getLabel());
            }
        }
    }

    @Nested
    @DisplayName("placing a score in a band")
    class Placement {

        @Test
        @DisplayName("a score lands in the band its tens digit names")
        void scoresLandInTheirBand() {
            assertEquals(1, countIn(0, 0.0));
            assertEquals(1, countIn(1, 10.0));
            assertEquals(1, countIn(5, 55.5));
            assertEquals(1, countIn(8, 89.99));
        }

        @Test
        @DisplayName("100 lands in the top band rather than overflowing")
        void perfectScoreLandsInTopBand() {
            // 100 / 10 == 10, which would index an eleventh band that does not
            // exist. This is the case the clamp exists for.
            assertEquals(1, countIn(9, 100.0));
        }

        @Test
        @DisplayName("90 and 100 share the top band")
        void ninetyAndHundredShareTopBand() {
            assertEquals(2, countIn(9, 90.0, 100.0));
        }

        @Test
        @DisplayName("the 89/90 boundary splits into adjacent bands")
        void boundaryBetweenLastTwoBands() {
            List<GradeDistributionBucket> bands =
                    GradeDistributionBucket.bucketize(List.of(89.0, 90.0));

            assertEquals(1, bands.get(8).getCount());
            assertEquals(1, bands.get(9).getCount());
        }

        @Test
        @DisplayName("out-of-range scores are clamped into the end bands")
        void outOfRangeIsClamped() {
            // Not reachable through the UI, but the method is public and a
            // stored score could be anything; it must not throw.
            assertEquals(1, countIn(0, -5.0));
            assertEquals(1, countIn(9, 140.0));
        }
    }

    @Nested
    @DisplayName("input handling")
    class Input {

        @Test
        @DisplayName("null entries are skipped, not counted")
        void nullEntriesSkipped() {
            // An ungraded submission arrives as a null score.
            List<Double> scores = new ArrayList<>();
            scores.add(75.0);
            scores.add(null);
            scores.add(78.0);

            List<GradeDistributionBucket> bands =
                    GradeDistributionBucket.bucketize(scores);

            assertEquals(2, bands.get(7).getCount());
            assertEquals(2, totalCount(bands), "the null must not be counted anywhere");
        }

        @Test
        @DisplayName("a null list is treated as no data")
        void nullListIsEmpty() {
            List<GradeDistributionBucket> bands =
                    GradeDistributionBucket.bucketize(null);

            assertNotNull(bands);
            assertEquals(0, totalCount(bands));
        }

        @Test
        @DisplayName("every score is counted exactly once")
        void everyScoreCountedOnce() {
            List<Double> scores = List.of(0.0, 9.9, 10.0, 50.0, 89.0, 90.0, 100.0);

            assertEquals(scores.size(),
                    totalCount(GradeDistributionBucket.bucketize(scores)));
        }

        private int totalCount(List<GradeDistributionBucket> bands) {
            int total = 0;
            for (GradeDistributionBucket band : bands) {
                total += band.getCount();
            }
            return total;
        }
    }
}
