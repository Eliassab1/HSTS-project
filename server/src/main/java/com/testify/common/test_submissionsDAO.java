package com.testify.common;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class test_submissionsDAO {

    private Connection connection;

    // Fix: Match constructor name to class name and add DatabaseConnection
    public test_submissionsDAO() {
        this.connection = DatabaseConnection.getConnection();
    }

    public void createTestSubmissionsTable() throws SQLException {
        String sql = """
        CREATE TABLE IF NOT EXISTS test_submissions (
            id INT AUTO_INCREMENT PRIMARY KEY,
            test_id INT NOT NULL,
            student_id INT NOT NULL,
            status VARCHAR(20) DEFAULT 'PENDING',
            final_score DECIMAL(5,2) NULL,
            started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            submitted_at TIMESTAMP NULL,
            FOREIGN KEY (test_id) REFERENCES tests(id),
            FOREIGN KEY (student_id) REFERENCES users(id),
            CHECK (status IN ('PENDING', 'PROCESSING', 'AWAITING_APPROVAL', 'GRADED')),
            CHECK (final_score IS NULL OR final_score BETWEEN 0.00 AND 100.00)
        )
    """;
        Statement stmt = connection.createStatement();
        stmt.executeUpdate(sql);
        ensureOriginalScoreColumn();
        ensureGradeOverrideReasonColumn();
        ensureApprovedByColumn();
    }

    /**
     * Adds the {@code original_score} column (nullable — the computer-graded
     * score, preserved even after a teacher override). Idempotent: swallows
     * MySQL error 1060 (duplicate column).
     */
    private void ensureOriginalScoreColumn() {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(
                    "ALTER TABLE test_submissions ADD COLUMN original_score DECIMAL(5,2) NULL");
        } catch (SQLException e) {
            if (e.getErrorCode() != 1060) {
                System.err.println("Could not add original_score column to test_submissions: " + e.getMessage());
            }
        }
    }

    /**
     * Adds the {@code grade_override_reason} column (nullable — justification
     * a teacher supplies when overriding a grade). Idempotent: swallows MySQL
     * error 1060 (duplicate column).
     */
    private void ensureGradeOverrideReasonColumn() {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(
                    "ALTER TABLE test_submissions ADD COLUMN grade_override_reason VARCHAR(500) NULL");
        } catch (SQLException e) {
            if (e.getErrorCode() != 1060) {
                System.err.println("Could not add grade_override_reason column to test_submissions: " + e.getMessage());
            }
        }
    }

    /**
     * Adds the {@code approved_by} column (nullable — the teacher user ID
     * that approved or overrode this grade). Idempotent: swallows MySQL error
     * 1060 (duplicate column).
     */
    private void ensureApprovedByColumn() {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(
                    "ALTER TABLE test_submissions ADD COLUMN approved_by INT NULL");
        } catch (SQLException e) {
            if (e.getErrorCode() != 1060) {
                System.err.println("Could not add approved_by column to test_submissions: " + e.getMessage());
            }
        }
    }

    public void insertSubmission(int test_id, int student_id) throws SQLException {
        String status = "PENDING";
        String sql = """
        INSERT INTO test_submissions (test_id, student_id, status) VALUES (?, ?, ?)
    """;
        PreparedStatement stmt = connection.prepareStatement(sql);
        stmt.setInt(1, test_id);
        stmt.setInt(2, student_id);
        stmt.setString(3, status);
        stmt.executeUpdate();
    }

    public void deleteSubmissionById(int id) throws SQLException {
        String sql = "DELETE FROM test_submissions WHERE id = ?";
        PreparedStatement stmt = connection.prepareStatement(sql);
        stmt.setInt(1, id);
        stmt.executeUpdate();
    }

    public void submitSubmission(int submissionId, Timestamp submittedAt) throws SQLException {
        String sql = """
        UPDATE test_submissions SET submitted_at = ?, status = 'PROCESSING' WHERE id = ?
    """;
        PreparedStatement stmt = connection.prepareStatement(sql);
        stmt.setTimestamp(1, submittedAt);
        stmt.setInt(2, submissionId);
        stmt.executeUpdate();
    }

    public void updateSubmission(TestSubmission submission) throws SQLException {
        String sql = """
        UPDATE test_submissions
        SET test_id = ?, student_id = ?, status = ?, final_score = ?, submitted_at = ?
        WHERE id = ?
    """;
        PreparedStatement stmt = connection.prepareStatement(sql);
        stmt.setInt(1, submission.getTestId());
        stmt.setInt(2, submission.getStudentId());
        stmt.setString(3, submission.getStatus());

        // Fix: getScore() matches the model, mapped to final_score in DB
        if (submission.getFinalScore() == null) {
            stmt.setNull(4, Types.DECIMAL);
        } else {
            stmt.setDouble(4, submission.getFinalScore());
        }

        stmt.setTimestamp(5, submission.getSubmittedAt());
        stmt.setInt(6, submission.getId());
        stmt.executeUpdate();
    }

    public TestSubmission getSubmissionById(int id) throws SQLException {
        String sql = "SELECT * FROM test_submissions WHERE id = ?";
        PreparedStatement stmt = connection.prepareStatement(sql);
        stmt.setInt(1, id);
        ResultSet rs = stmt.executeQuery();

        if (rs.next()) {
            return new TestSubmission(
                    rs.getInt("id"),
                    rs.getInt("test_id"),
                    rs.getInt("student_id"),
                    rs.getString("status"),
                    rs.getObject("final_score", Double.class),
                    rs.getTimestamp("started_at"),  // <-- Fixed back to Timestamp!
                    rs.getTimestamp("submitted_at")// <-- Fixed back to Timestamp!
            );
        }
        return null;
    }

    public void calculateAndFinalizeScore(int submissionId) throws SQLException {
        // Step 1: look up which test this submission belongs to
        int testId;
        try (PreparedStatement s = connection.prepareStatement(
                "SELECT test_id FROM test_submissions WHERE id = ?")) {
            s.setInt(1, submissionId);
            ResultSet rs = s.executeQuery();
            if (!rs.next()) throw new SQLException("Submission " + submissionId + " not found.");
            testId = rs.getInt("test_id");
        }

        // Step 2: compute score in Java (avoids self-join / SQL-mode issues)
        String answerSql = """
            SELECT sa.student_answer, q.correct_option, tq.points_worth
            FROM student_answers sa
            JOIN questions q ON q.id = sa.question_id
            JOIN test_questions tq ON tq.question_id = sa.question_id AND tq.test_id = ?
            WHERE sa.submission_id = ?
        """;
        double earned = 0.0;
        try (PreparedStatement s = connection.prepareStatement(answerSql)) {
            s.setInt(1, testId);
            s.setInt(2, submissionId);
            ResultSet rs = s.executeQuery();
            while (rs.next()) {
                String given   = rs.getString("student_answer");
                String correct = rs.getString("correct_option");
                if (given != null && given.equalsIgnoreCase(correct)) {
                    earned += rs.getDouble("points_worth");
                }
            }
        }

        // Clamp to the allowed DB range [0, 100]
        double finalScore = Math.min(Math.max(earned, 0.0), 100.0);

        // Step 3: single, plain UPDATE — no joins, no self-references.
        // The status stops at AWAITING_APPROVAL, not GRADED: the computed
        // score is not released to the student until a teacher approves it
        // (getResultsForStudent only returns GRADED rows). original_score
        // records what the computer calculated, so a later teacher override
        // never destroys it.
        try (PreparedStatement s = connection.prepareStatement(
                "UPDATE test_submissions SET final_score = ?, original_score = ?, " +
                "submitted_at = NOW(), status = 'AWAITING_APPROVAL' WHERE id = ?")) {
            s.setDouble(1, finalScore);
            s.setDouble(2, finalScore);
            s.setInt(3, submissionId);
            s.executeUpdate();
        }
    }

    /**
     * Loads every submission awaiting the given teacher's grade approval —
     * the rows behind the teacher's Grade Approval screen.
     *
     * @param teacherId identifier of the teacher who owns the exams
     * @return the pending rows, newest submission first
     * @throws SQLException if the query fails
     */
    public List<PendingGrade> getPendingGradesForTeacher(int teacherId) throws SQLException {
        String sql = """
            SELECT ts.id, ts.test_id, t.teacher_notes AS exam_title,
                   ts.student_id, u.full_name, u.username, ts.final_score, ts.status
            FROM test_submissions ts
            JOIN tests t ON t.id = ts.test_id
            JOIN users u ON u.id = ts.student_id
            WHERE t.teacher_id = ? AND ts.status = 'AWAITING_APPROVAL'
            ORDER BY ts.submitted_at DESC
        """;
        List<PendingGrade> pending = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, teacherId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                pending.add(mapPendingGrade(rs));
            }
        }
        return pending;
    }

    /**
     * Loads one grade-approval row by submission ID, regardless of status —
     * used to echo the updated row back after an approval or an override.
     *
     * @param submissionId identifier of the submission
     * @return the row, or null when the submission does not exist
     * @throws SQLException if the query fails
     */
    public PendingGrade getPendingGradeById(int submissionId) throws SQLException {
        String sql = """
            SELECT ts.id, ts.test_id, t.teacher_notes AS exam_title,
                   ts.student_id, u.full_name, u.username, ts.final_score, ts.status
            FROM test_submissions ts
            JOIN tests t ON t.id = ts.test_id
            JOIN users u ON u.id = ts.student_id
            WHERE ts.id = ?
        """;
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, submissionId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return mapPendingGrade(rs);
            }
        }
        return null;
    }

    /** Maps one joined submission row onto a {@link PendingGrade}. */
    private PendingGrade mapPendingGrade(ResultSet rs) throws SQLException {
        String name = rs.getString("full_name");
        if (name == null || name.isBlank()) {
            name = rs.getString("username");
        }
        Double score = rs.getObject("final_score", Double.class);
        return new PendingGrade(
                rs.getInt("id"),
                rs.getInt("test_id"),
                rs.getString("exam_title"),
                rs.getInt("student_id"),
                name,
                score != null ? score : 0.0,
                rs.getString("status")
        );
    }

    /**
     * Returns the ID of the teacher who owns the exam a submission belongs
     * to, so the server can refuse an approval from anyone else.
     *
     * @param submissionId identifier of the submission
     * @return the owning teacher's user ID, or -1 when the submission does not exist
     * @throws SQLException if the query fails
     */
    public int getOwningTeacherId(int submissionId) throws SQLException {
        String sql = """
            SELECT t.teacher_id
            FROM test_submissions ts
            JOIN tests t ON t.id = ts.test_id
            WHERE ts.id = ?
        """;
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, submissionId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1);
        }
        return -1;
    }

    /**
     * Approves a computer-calculated grade as it stands: the status moves to
     * GRADED (which is what makes it visible to the student) and the
     * approving teacher is recorded.
     *
     * @param submissionId identifier of the submission
     * @param approverId user ID of the approving teacher
     * @return true if a row was updated
     * @throws SQLException if the update fails
     */
    public boolean approveGrade(int submissionId, int approverId) throws SQLException {
        String sql = """
            UPDATE test_submissions
            SET status = 'GRADED', approved_by = ?
            WHERE id = ? AND status = 'AWAITING_APPROVAL'
        """;
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, approverId);
            stmt.setInt(2, submissionId);
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Replaces a computer-calculated grade with one the teacher sets.
     * {@code original_score} is deliberately left untouched so the computed
     * grade remains on record next to the override and its justification.
     *
     * @param submissionId identifier of the submission
     * @param newScore grade the teacher is assigning, 0-100
     * @param reason justification for the change
     * @param approverId user ID of the overriding teacher
     * @return true if a row was updated
     * @throws SQLException if the update fails
     */
    public boolean overrideGrade(int submissionId, double newScore, String reason, int approverId)
            throws SQLException {
        String sql = """
            UPDATE test_submissions
            SET final_score = ?, grade_override_reason = ?, approved_by = ?, status = 'GRADED'
            WHERE id = ? AND status = 'AWAITING_APPROVAL'
        """;
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setDouble(1, newScore);
            stmt.setString(2, reason);
            stmt.setInt(3, approverId);
            stmt.setInt(4, submissionId);
            return stmt.executeUpdate() > 0;
        }
    }

    public List<Double> getStudentGrades(int studentId) throws SQLException {
        String sql = "SELECT final_score FROM test_submissions WHERE student_id = ? AND status = 'GRADED'";
        PreparedStatement stmt = connection.prepareStatement(sql);
        stmt.setInt(1, studentId);
        ResultSet rs = stmt.executeQuery();
        List<Double> grades = new ArrayList<>();

        while (rs.next()) {
            grades.add(rs.getDouble("final_score"));
        }
        return grades;
    }

    /**
     * Unconditionally inserts a new PENDING submission row.
     *
     * <b>No longer the submit path.</b> A submission row is now opened when
     * the student starts the exam — see {@link #openSubmission(int, int)} —
     * and SUBMIT_EXAM finalises that row rather than creating a second one.
     * Kept because it is the unconditional insert {@code openSubmission}
     * would otherwise duplicate, and because a caller that genuinely wants a
     * fresh row should not have to go through the reuse lookup.
     */
    public int insertSubmissionGetId(int testId, int studentId) throws SQLException {
        String sql = "INSERT INTO test_submissions (test_id, student_id, status) VALUES (?, ?, 'PENDING')";
        PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
        stmt.setInt(1, testId);
        stmt.setInt(2, studentId);
        stmt.executeUpdate();
        ResultSet keys = stmt.getGeneratedKeys();
        if (keys.next()) return keys.getInt(1);
        throw new SQLException("No generated key returned from submission insert.");
    }

    public void ensureCorrectConstraints() {
        try {
            List<String> checkNames = new ArrayList<>();
            String findSql = "SELECT CONSTRAINT_NAME FROM information_schema.TABLE_CONSTRAINTS " +
                             "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'test_submissions' " +
                             "AND CONSTRAINT_TYPE = 'CHECK'";
            try (Statement s = connection.createStatement(); ResultSet rs = s.executeQuery(findSql)) {
                while (rs.next()) {
                    checkNames.add(rs.getString(1));
                }
            }
            for (String name : checkNames) {
                // The name comes from information_schema (not client input), but
                // it is embedded as a raw identifier here — validate it anyway.
                if (!InputValidator.isSafeIdentifier(name)) {
                    System.err.println("Skipping constraint with unsafe name: " + name);
                    continue;
                }
                try (Statement s = connection.createStatement()) {
                    s.executeUpdate("ALTER TABLE test_submissions DROP CHECK `" + name + "`");
                } catch (SQLException ignored) {}
            }
            try (Statement s = connection.createStatement()) {
                s.executeUpdate("ALTER TABLE test_submissions ADD CONSTRAINT CHECK (status IN ('PENDING', 'PROCESSING', 'AWAITING_APPROVAL', 'GRADED'))");
            } catch (SQLException e) {
                System.err.println("Could not add status constraint: " + e.getMessage());
            }
            try (Statement s = connection.createStatement()) {
                s.executeUpdate("ALTER TABLE test_submissions ADD CONSTRAINT CHECK (final_score IS NULL OR final_score BETWEEN 0.00 AND 100.00)");
            } catch (SQLException e) {
                System.err.println("Could not add score constraint: " + e.getMessage());
            }
            System.out.println("test_submissions constraints verified.");
        } catch (SQLException e) {
            System.err.println("Schema migration warning (non-fatal): " + e.getMessage());
        }
    }

    /**
     * Whether an exam has any <i>genuinely submitted</i> attempt — the rule
     * behind "an exam with submissions can no longer be edited or deleted".
     *
     * The {@code status <> 'PENDING'} filter is load-bearing. Since a row is
     * now opened when a student <i>starts</i> an exam rather than when they
     * submit it, a student who opens the paper and walks away leaves a
     * PENDING row behind. Counting that row would freeze the exam against
     * editing on the strength of an attempt that produced no answers and no
     * score, so only rows that have moved past PENDING count here.
     *
     * @param testId exam to check
     * @return true when at least one non-PENDING submission exists
     * @throws SQLException if the query fails
     */
    public boolean hasSubmissions(int testId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM test_submissions " +
                     "WHERE test_id = ? AND status <> 'PENDING'";
        PreparedStatement stmt = connection.prepareStatement(sql);
        stmt.setInt(1, testId);
        ResultSet rs = stmt.executeQuery();
        if (rs.next()) return rs.getInt(1) > 0;
        return false;
    }

    /**
     * The ID of this student's open (PENDING) attempt at an exam, if any.
     *
     * @param testId exam being sat
     * @param studentId student sitting it
     * @return the open submission's ID, or -1 when there is none
     * @throws SQLException if the query fails
     */
    public int findOpenSubmissionId(int testId, int studentId) throws SQLException {
        String sql = """
            SELECT id FROM test_submissions
            WHERE test_id = ? AND student_id = ? AND status = 'PENDING'
            ORDER BY id DESC
            LIMIT 1
        """;
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, testId);
            stmt.setInt(2, studentId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return -1;
    }

    /**
     * Opens the submission row for an exam a student is starting, or hands
     * back the one already open.
     *
     * Called from START_EXAM_BY_CODE once every admission check has passed,
     * which is what gives the system a durable record that someone is
     * mid-exam. The in-memory {@code examWatchers} registry knows the same
     * thing but does not survive a reconnect, and the bot's exam lockout
     * (spec 14) has to hold across one.
     *
     * <b>Reusing an open row is the point of the lookup.</b> A student who
     * starts, drops out and re-enters would otherwise accumulate one row per
     * entry, and every one of them would keep {@code hasOpenSubmission} true
     * forever after they finally submitted through just one of them.
     *
     * @param testId exam being started
     * @param studentId student starting it
     * @return the open submission's ID, existing or freshly inserted
     * @throws SQLException if the lookup or the insert fails
     */
    public int openSubmission(int testId, int studentId) throws SQLException {
        int existing = findOpenSubmissionId(testId, studentId);
        if (existing != -1) {
            return existing;
        }
        String sql = """
            INSERT INTO test_submissions (test_id, student_id, status, started_at)
            VALUES (?, ?, 'PENDING', NOW())
        """;
        try (PreparedStatement stmt =
                     connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, testId);
            stmt.setInt(2, studentId);
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        throw new SQLException("No generated key returned when opening a submission.");
    }

    /**
     * Whether this student has an exam open anywhere — the durable half of
     * the learning bot's exam lockout (spec 14).
     *
     * Unlike the {@code examWatchers} registry this survives a reconnect, a
     * server restart and a modified client, because it is a fact in the
     * database rather than a property of a socket.
     *
     * @param studentId student to check
     * @return true when that student has any PENDING submission
     * @throws SQLException if the query fails
     */
    public boolean hasOpenSubmission(int studentId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM test_submissions " +
                     "WHERE student_id = ? AND status = 'PENDING'";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, studentId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1) > 0;
            }
        }
        return false;
    }

    /**
     * Every approved result in the system, across all students — the
     * principal's system-wide results listing (spec 11).
     *
     * Same {@code status = 'GRADED'} filter as
     * {@link #getResultsForStudent(int)}: a grade a teacher has not yet
     * approved is not shown to anyone, the principal included.
     *
     * @return all graded results, newest submission first
     * @throws SQLException if the query fails
     */
    public List<ExamResult> getAllResults() throws SQLException {
        String sql = """
        SELECT ts.id, ts.test_id, t.teacher_notes AS exam_title,
               ts.student_id, ts.final_score, ts.submitted_at,
               COALESCE(NULLIF(u.full_name, ''), u.username, '') AS student_name
        FROM test_submissions ts
        JOIN tests t ON ts.test_id = t.id
        LEFT JOIN users u ON u.id = ts.student_id
        WHERE ts.status = 'GRADED'
        ORDER BY ts.submitted_at DESC
    """;
        List<ExamResult> results = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                double score = rs.getDouble("final_score");
                String date = rs.getTimestamp("submitted_at") != null
                        ? rs.getTimestamp("submitted_at").toString() : "N/A";
                ExamResult result = new ExamResult(
                        rs.getInt("id"),
                        rs.getInt("test_id"),
                        rs.getString("exam_title"),
                        rs.getInt("student_id"),
                        score,
                        100.0,
                        date,
                        score >= 60.0 ? "PASSED" : "FAILED"
                );
                result.setStudentName(rs.getString("student_name"));
                results.add(result);
            }
        }
        return results;
    }


    /**
     * One exam's results across the students who sat it, optionally narrowed
     * to a single שכבה (spec 10).
     *
     * The mirror image of {@link #getResultsForStudent(int)}: same
     * {@code status = 'GRADED'} filter — a grade the teacher has not released
     * is not part of the class picture either — but keyed on the exam and
     * carrying the student rather than the other way round.
     *
     * @param examId exam whose results are wanted
     * @param gradeLevelFilter grade level to restrict to, or null for all
     * @return one row per graded submission, by grade level then name
     * @throws SQLException if the query fails
     */
    public List<StudentExamResult> getResultsForExam(int examId, Integer gradeLevelFilter)
            throws SQLException {

        String sql = """
        SELECT ts.id, ts.student_id,
               COALESCE(NULLIF(u.full_name, ''), u.username, '') AS student_name,
               u.grade_level, ts.final_score, ts.submitted_at
        FROM test_submissions ts
        JOIN users u ON u.id = ts.student_id
        WHERE ts.test_id = ? AND ts.status = 'GRADED' AND ts.final_score IS NOT NULL
    """ + (gradeLevelFilter != null ? " AND u.grade_level = ?" : "")
                + " ORDER BY u.grade_level, student_name";

        List<StudentExamResult> results = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, examId);
            if (gradeLevelFilter != null) {
                stmt.setInt(2, gradeLevelFilter);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    double score = rs.getDouble("final_score");
                    String submittedAt = rs.getTimestamp("submitted_at") != null
                            ? rs.getTimestamp("submitted_at").toString()
                            : "N/A";
                    results.add(new StudentExamResult(
                            rs.getInt("id"),
                            rs.getInt("student_id"),
                            rs.getString("student_name"),
                            rs.getObject("grade_level", Integer.class),
                            score,
                            score >= 60.0 ? "PASSED" : "FAILED",
                            submittedAt
                    ));
                }
            }
        }
        return results;
    }

    public List<ExamResult> getResultsForStudent(int studentId) throws SQLException {
        String sql = """
        SELECT ts.id, ts.test_id, t.teacher_notes AS exam_title,
               ts.student_id, ts.final_score, ts.submitted_at
        FROM test_submissions ts
        JOIN tests t ON ts.test_id = t.id
        WHERE ts.student_id = ? AND ts.status = 'GRADED'
        ORDER BY ts.submitted_at DESC
    """;
        PreparedStatement stmt = connection.prepareStatement(sql);
        stmt.setInt(1, studentId);
        ResultSet rs = stmt.executeQuery();
        List<ExamResult> results = new ArrayList<>();
        while (rs.next()) {
            double score = rs.getDouble("final_score");
            String status = score >= 60.0 ? "PASSED" : "FAILED";
            String date = rs.getTimestamp("submitted_at") != null
                    ? rs.getTimestamp("submitted_at").toString() : "N/A";
            results.add(new ExamResult(
                    rs.getInt("id"),
                    rs.getInt("test_id"),
                    rs.getString("exam_title"),
                    rs.getInt("student_id"),
                    score,
                    100.0,
                    date,
                    status
            ));
        }
        return results;
    }
}
