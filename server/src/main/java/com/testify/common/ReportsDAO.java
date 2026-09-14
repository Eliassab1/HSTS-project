package com.testify.common;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO backing the administrator's (principal's) reports/graphs screen.
 * Every method here returns wire-ready, graph-friendly rows — no raw
 * ResultSets or entity objects — so the client can plot them directly.
 */
public class ReportsDAO {

    private Connection connection;

    public ReportsDAO() {
        this.connection = DatabaseConnection.getConnection();
    }

    /**
     * Pass/fail counts per course, based on graded submissions
     * (final_score &gt;= 60 counts as a pass, matching the cutoff used
     * elsewhere in the app).
     */
    public List<PassFailStat> getPassFailByCourse() throws SQLException {
        String sql = """
        SELECT c.course_name AS label,
               SUM(CASE WHEN ts.final_score >= 60 THEN 1 ELSE 0 END) AS pass_count,
               SUM(CASE WHEN ts.final_score < 60  THEN 1 ELSE 0 END) AS fail_count
        FROM test_submissions ts
        JOIN tests t   ON t.id = ts.test_id
        JOIN courses c ON c.id = t.course_id
        WHERE ts.status = 'GRADED'
        GROUP BY c.course_name
        ORDER BY c.course_name
    """;
        Statement stmt = connection.createStatement();
        ResultSet rs = stmt.executeQuery(sql);
        List<PassFailStat> stats = new ArrayList<>();
        while (rs.next()) {
            stats.add(new PassFailStat(
                    rs.getString("label"),
                    rs.getInt("pass_count"),
                    rs.getInt("fail_count")
            ));
        }
        return stats;
    }

    /**
     * Average final score per submission date, across all graded
     * submissions, ordered chronologically.
     */
    public List<ScoreTrendPoint> getScoreTrendByDate() throws SQLException {
        String sql = """
        SELECT DATE(ts.submitted_at) AS period_label,
               AVG(ts.final_score)   AS average_score,
               COUNT(*)              AS submission_count
        FROM test_submissions ts
        WHERE ts.status = 'GRADED' AND ts.submitted_at IS NOT NULL
        GROUP BY DATE(ts.submitted_at)
        ORDER BY DATE(ts.submitted_at)
    """;
        Statement stmt = connection.createStatement();
        ResultSet rs = stmt.executeQuery(sql);
        List<ScoreTrendPoint> points = new ArrayList<>();
        while (rs.next()) {
            Date date = rs.getDate("period_label");
            points.add(new ScoreTrendPoint(
                    date != null ? date.toString() : "N/A",
                    rs.getDouble("average_score"),
                    rs.getInt("submission_count")
            ));
        }
        return points;
    }

    /**
     * Average final score per exam for a teacher's 10 most recently
     * created exams, ordered chronologically. Only exams with at least
     * one graded submission are included.
     */
    public List<TeacherExamPerformance> getExamPerformanceByTeacher(int teacherId) throws SQLException {
        String sql = """
        SELECT label, exam_date, avg_score, submission_count
        FROM (
            SELECT t.teacher_notes      AS label,
                   t.created_at         AS created_at,
                   DATE(t.created_at)   AS exam_date,
                   AVG(ts.final_score)  AS avg_score,
                   COUNT(*)             AS submission_count
            FROM tests t
            JOIN test_submissions ts ON ts.test_id = t.id AND ts.status = 'GRADED'
            WHERE t.teacher_id = ?
            GROUP BY t.id, t.teacher_notes, t.created_at
            ORDER BY t.created_at DESC
            LIMIT 10
        ) recent_exams
        ORDER BY created_at ASC
    """;
        List<TeacherExamPerformance> points = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, teacherId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Date examDate = rs.getDate("exam_date");
                    points.add(new TeacherExamPerformance(
                            rs.getString("label"),
                            examDate != null ? examDate.toString() : "N/A",
                            rs.getDouble("avg_score"),
                            rs.getInt("submission_count")
                    ));
                }
            }
        }
        return points;
    }

    /**
     * Grade distribution in 10-point bands (spec 12).
     *
     * Bands are 0-9, 10-19, … 80-89, 90-100 — the top band is eleven points
     * wide so a perfect 100 lands somewhere. Every band is returned even
     * when empty, so the histogram keeps a stable shape.
     *
     * @param examId one exam to restrict to, or null for every graded
     *               submission in the system
     * @return ten bands, lowest first
     * @throws SQLException if the query fails
     */
    public List<GradeDistributionBucket> getGradeDistribution(Integer examId) throws SQLException {

        String sql = "SELECT final_score FROM test_submissions "
                + "WHERE status = 'GRADED' AND final_score IS NOT NULL"
                + (examId != null ? " AND test_id = ?" : "");

        List<Double> scores = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            if (examId != null) {
                stmt.setInt(1, examId);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    scores.add(rs.getDouble("final_score"));
                }
            }
        }

        // The banding itself lives on the model, so the teacher's per-exam
        // histogram on the client bands scores exactly the same way.
        return GradeDistributionBucket.bucketize(scores);
    }

    /**
     * Per-exam statistics for one teacher — the "compare the exams of the
     * same teacher" report (spec 12).
     *
     * @param teacherId identifier of the teacher
     * @return one row per exam of that teacher which has graded submissions
     * @throws SQLException if the query fails
     */
    public List<ExamStatistics> getStatisticsByTeacher(int teacherId) throws SQLException {
        return collectStatistics("t.teacher_id = ?", teacherId);
    }

    /**
     * Per-exam statistics for one course — the "compare the exams of the
     * same course" report (spec 12).
     *
     * @param courseId identifier of the course
     * @return one row per exam of that course which has graded submissions
     * @throws SQLException if the query fails
     */
    public List<ExamStatistics> getStatisticsByCourse(int courseId) throws SQLException {
        return collectStatistics("t.course_id = ?", courseId);
    }

    /**
     * Per-exam statistics for one student — the "compare the exams of the
     * same student" report (spec 12). A student normally sits an exam once,
     * so these rows are typically a single score each.
     *
     * @param studentId identifier of the student
     * @return one row per exam that student has sat and had graded
     * @throws SQLException if the query fails
     */
    public List<ExamStatistics> getStatisticsByStudent(int studentId) throws SQLException {
        return collectStatistics("ts.student_id = ?", studentId);
    }

    /**
     * Shared body of the three comparison reports: pulls every graded score
     * matching a filter, grouped by exam, then reduces each exam's scores to
     * one {@link ExamStatistics} row.
     *
     * <p><b>Median is computed in Java, not SQL.</b> MySQL has no MEDIAN
     * aggregate, and the usual window-function workaround is easy to get
     * subtly wrong for an even number of rows. Ordering the scores here and
     * reducing them in {@link #summarise} keeps the even case explicit: the
     * mean of the two middle values.
     *
     * @param whereClause filter on the joined tests/test_submissions rows
     * @param filterValue value bound into that filter
     */
    private List<ExamStatistics> collectStatistics(String whereClause, int filterValue)
            throws SQLException {

        // whereClause is never client-supplied — the three callers above pass
        // fixed literals, and the value itself is bound as a parameter.
        String sql = """
        SELECT t.id AS exam_id, COALESCE(t.teacher_notes, '') AS label, ts.final_score
        FROM tests t
        JOIN test_submissions ts ON ts.test_id = t.id
        WHERE ts.status = 'GRADED' AND ts.final_score IS NOT NULL AND
        """ + whereClause + " ORDER BY t.id, ts.final_score";

        List<ExamStatistics> stats = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, filterValue);
            try (ResultSet rs = stmt.executeQuery()) {

                int currentExamId = -1;
                String currentLabel = null;
                List<Double> scores = new ArrayList<>();

                while (rs.next()) {
                    int examId = rs.getInt("exam_id");
                    if (examId != currentExamId) {
                        if (currentExamId != -1) {
                            stats.add(summarise(currentLabel, scores));
                        }
                        currentExamId = examId;
                        currentLabel = rs.getString("label");
                        scores = new ArrayList<>();
                    }
                    scores.add(rs.getDouble("final_score"));
                }

                if (currentExamId != -1) {
                    stats.add(summarise(currentLabel, scores));
                }
            }
        }
        return stats;
    }

    /**
     * Reduces one exam's scores to a statistics row.
     *
     * @param label exam title
     * @param sorted that exam's scores, already in ascending order (the
     *               query's ORDER BY guarantees this)
     */
    private ExamStatistics summarise(String label, List<Double> sorted) {
        // The reduction lives on the model, so the client's per-exam average
        // and median agree with the reports' — including the even-count case.
        // ExamStatistics.summarise sorts its own copy, which makes the
        // query's ORDER BY a convenience here rather than a precondition.
        return ExamStatistics.summarise(label, sorted);
    }

    /**
     * Tests and questions authored per teacher, from teacher_statistics.
     * Teachers with no activity yet are included with zero counts.
     */
    public List<TeacherActivityStat> getTeacherActivity() throws SQLException {
        String sql = """
        SELECT u.full_name AS teacher_name,
               COALESCE(st.tests_count, 0)     AS tests_count,
               COALESCE(st.questions_count, 0) AS questions_count
        FROM users u
        LEFT JOIN teacher_statistics st ON st.teacher_id = u.id
        WHERE u.role = 'TEACHER'
        ORDER BY tests_count DESC, u.full_name
    """;
        Statement stmt = connection.createStatement();
        ResultSet rs = stmt.executeQuery(sql);
        List<TeacherActivityStat> stats = new ArrayList<>();
        while (rs.next()) {
            stats.add(new TeacherActivityStat(
                    rs.getString("teacher_name"),
                    rs.getInt("tests_count"),
                    rs.getInt("questions_count")
            ));
        }
        return stats;
    }
}
