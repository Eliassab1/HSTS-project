package com.testify.common;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * One 10-point band of a grade distribution histogram (spec 12).
 *
 * The bands are 0-9, 10-19, … 80-89, 90-100 — the top band is deliberately
 * eleven points wide so a perfect 100 has somewhere to land.
 */
public class GradeDistributionBucket implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Human-readable band, e.g. "70-79".
     */
    private String label;

    /**
     * Lowest score in the band, inclusive.
     */
    private int lowerBound;

    /**
     * Highest score in the band, inclusive.
     */
    private int upperBound;

    /**
     * Number of graded submissions that fell in this band.
     */
    private int count;

    /**
     * Empty constructor.
     */
    public GradeDistributionBucket() {
    }

    /**
     * Creates one histogram band.
     *
     * @param label human-readable band
     * @param lowerBound lowest score, inclusive
     * @param upperBound highest score, inclusive
     * @param count submissions in the band
     */
    public GradeDistributionBucket(
            String label,
            int lowerBound,
            int upperBound,
            int count
    ) {
        this.label = label;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.count = count;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(
            String label
    ) {
        this.label = label;
    }

    public int getLowerBound() {
        return lowerBound;
    }

    public void setLowerBound(
            int lowerBound
    ) {
        this.lowerBound = lowerBound;
    }

    public int getUpperBound() {
        return upperBound;
    }

    public void setUpperBound(
            int upperBound
    ) {
        this.upperBound = upperBound;
    }

    public int getCount() {
        return count;
    }

    public void setCount(
            int count
    ) {
        this.count = count;
    }

    /**
     * Sorts a set of scores into the ten standard bands.
     *
     * This lives on the model rather than in a DAO because both sides of the
     * wire need it: the server buckets whole-system distributions in
     * {@code ReportsDAO.getGradeDistribution}, and the client buckets the
     * filtered rows of one exam for the teacher's results histogram. Two
     * implementations would be two chances to disagree about where a 90
     * belongs.
     *
     * Every band is returned even when empty, so a histogram drawn from this
     * keeps a stable shape as the data changes.
     *
     * @param scores scores to count, in any order; null entries are skipped
     * @return ten bands, lowest first
     */
    public static List<GradeDistributionBucket> bucketize(
            List<Double> scores
    ) {
        int[] counts = new int[10];

        if (scores != null) {
            for (Double score : scores) {
                if (score == null) {
                    continue;
                }
                // Clamp into 0..9: a 100 would otherwise index band 10.
                int band = (int) Math.floor(score / 10.0);
                if (band < 0) band = 0;
                if (band > 9) band = 9;
                counts[band]++;
            }
        }

        List<GradeDistributionBucket> buckets = new ArrayList<>();
        for (int i = 0; i < counts.length; i++) {
            int lower = i * 10;
            int upper = (i == 9) ? 100 : lower + 9;
            buckets.add(new GradeDistributionBucket(
                    lower + "-" + upper, lower, upper, counts[i]));
        }
        return buckets;
    }

    @Override
    public String toString() {
        return "GradeDistributionBucket{" +
                "label='" + label + '\'' +
                ", count=" + count +
                '}';
    }
}
