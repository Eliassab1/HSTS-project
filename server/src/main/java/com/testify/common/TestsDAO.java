package com.testify.common;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class TestsDAO {

    private Connection connection;

    public TestsDAO() {
        this.connection = DatabaseConnection.getConnection();
    }

    public void createTestsTable() throws SQLException {
        String testsSql = """
        CREATE TABLE IF NOT EXISTS tests (
            id INT AUTO_INCREMENT PRIMARY KEY,
            test_code CHAR(6) NOT NULL UNIQUE,
            teacher_id INT NOT NULL,
            course_id INT NOT NULL,
            duration_minutes INT NOT NULL,
            student_instructions TEXT,
            teacher_notes TEXT,
            is_active BOOLEAN DEFAULT FALSE,
            approval_status VARCHAR(20) DEFAULT 'APPROVED',
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (teacher_id) REFERENCES users(id),
            FOREIGN KEY (course_id) REFERENCES courses(id),
            CHECK (duration_minutes > 0),
            CHECK (approval_status IN ('PENDING','APPROVED','REJECTED'))
        )
    """;
        String statsSql = """
        CREATE TABLE IF NOT EXISTS teacher_statistics (
            teacher_id INT PRIMARY KEY,
            tests_count INT DEFAULT 0,
            questions_count INT DEFAULT 0,
            FOREIGN KEY (teacher_id) REFERENCES users(id)
        )
    """;
        Statement stmt = connection.createStatement();
        stmt.executeUpdate(testsSql);
        stmt.executeUpdate(statsSql);
        ensureApprovalStatusColumn();
        ensureRejectionReasonColumn();
        ensureOpenAtColumn();
        ensureCloseAtColumn();
        ensureExamCodeColumn();
    }

    /**
     * Creates the exam version-history table. Idempotent, so it is safe on
     * every startup.
     *
     * Same reasoning as {@code questions_history}: an edited exam has to
     * leave its previous form in the bank (spec 3.5), but
     * {@code test_questions} and {@code test_submissions} both point at
     * {@code tests.id}, so the live row has to stay put. This captures the
     * pre-edit header only - the question set at that time is not
     * reconstructable from it, which is acceptable because an exam with
     * submissions cannot be edited at all.
     *
     * @throws SQLException if the statement fails
     */
    public void createTestsHistoryTable() throws SQLException {
        String sql = """
        CREATE TABLE IF NOT EXISTS tests_history (
            history_id INT AUTO_INCREMENT PRIMARY KEY,
            test_id INT NOT NULL,
            test_code CHAR(6),
            teacher_id INT,
            course_id INT,
            duration_minutes INT,
            student_instructions TEXT,
            teacher_notes TEXT,
            is_active BOOLEAN,
            approval_status VARCHAR(20),
            archived_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
    """;
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }

    /**
     * Applies an edit to an exam: archives the pre-edit header, rewrites the
     * header, and replaces the question set - all in one transaction on a
     * connection this method owns (spec 3.5).
     *
     * The exam always comes out of this as {@code PENDING} with any earlier
     * rejection reason cleared, so an edit goes back through the principal
     * rather than quietly changing an approved paper. {@code test_code},
     * {@code teacher_id} and {@code is_active} are deliberately left alone:
     * the code identifies the same exam, ownership does not change on an
     * edit, and an exam that is PENDING is already withheld from students by
     * the availability queries regardless of its active flag.
     *
     * @param testId exam being edited
     * @param courseId resolved course
     * @param durationMinutes new duration
     * @param instructions new student instructions
     * @param title new title (stored in teacher_notes)
     * @param questions the new question set, replacing the old one entirely
     * @throws SQLException if any step fails; nothing is applied
     */
    public void updateExamWithHistory(int testId, int courseId, int durationMinutes,
                                      String instructions, String title,
                                      List<ExamQuestion> questions) throws SQLException {

        String archiveSql = """
        INSERT INTO tests_history
        (test_id, test_code, teacher_id, course_id, duration_minutes,
         student_instructions, teacher_notes, is_active, approval_status)
        SELECT id, test_code, teacher_id, course_id, duration_minutes,
               student_instructions, teacher_notes, is_active, approval_status
        FROM tests WHERE id = ?
    """;

        String updateSql = """
        UPDATE tests
        SET course_id = ?, duration_minutes = ?, student_instructions = ?,
            teacher_notes = ?, approval_status = 'PENDING', rejection_reason = NULL
        WHERE id = ?
    """;

        String clearQuestionsSql = "DELETE FROM test_questions WHERE test_id = ?";

        String addQuestionSql = """
        INSERT INTO test_questions (test_id, question_id, points_worth)
        VALUES (?, ?, ?)
    """;

        try (Connection txn = DatabaseConnection.openDedicatedConnection()) {
            txn.setAutoCommit(false);
            try (PreparedStatement archive = txn.prepareStatement(archiveSql);
                 PreparedStatement update = txn.prepareStatement(updateSql);
                 PreparedStatement clear = txn.prepareStatement(clearQuestionsSql);
                 PreparedStatement add = txn.prepareStatement(addQuestionSql)) {

                archive.setInt(1, testId);
                archive.executeUpdate();

                update.setInt(1, courseId);
                update.setInt(2, durationMinutes);
                update.setString(3, instructions);
                update.setString(4, title);
                update.setInt(5, testId);
                update.executeUpdate();

                clear.setInt(1, testId);
                clear.executeUpdate();

                for (ExamQuestion question : questions) {
                    add.setInt(1, testId);
                    add.setInt(2, question.getQuestionId());
                    add.setDouble(3, question.getPoints());
                    add.executeUpdate();
                }

                txn.commit();

            } catch (SQLException failure) {
                txn.rollback();
                throw failure;
            }
        }
    }

    /**
     * Adds the {@code approval_status} column to an existing {@code tests} table
     * that was created before this column was part of the schema. Idempotent:
     * MySQL error 1060 (duplicate column) is swallowed when the column exists.
     */
    public void ensureApprovalStatusColumn() {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(
                    "ALTER TABLE tests ADD COLUMN approval_status VARCHAR(20) DEFAULT 'APPROVED'");
        } catch (SQLException e) {
            if (e.getErrorCode() != 1060) {
                System.err.println("Could not add approval_status column to tests: " + e.getMessage());
            }
        }
    }

    /**
     * Adds the {@code rejection_reason} column (nullable — populated by the
     * principal on {@code REJECT_EXAM}). Idempotent: swallows MySQL error 1060
     * (duplicate column).
     */
    private void ensureRejectionReasonColumn() {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("ALTER TABLE tests ADD COLUMN rejection_reason VARCHAR(500) NULL");
        } catch (SQLException e) {
            if (e.getErrorCode() != 1060) {
                System.err.println("Could not add rejection_reason column to tests: " + e.getMessage());
            }
        }
    }

    /**
     * Adds the {@code open_at} column (nullable — exam scheduling window
     * start). Idempotent: swallows MySQL error 1060 (duplicate column).
     */
    private void ensureOpenAtColumn() {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("ALTER TABLE tests ADD COLUMN open_at DATETIME NULL");
        } catch (SQLException e) {
            if (e.getErrorCode() != 1060) {
                System.err.println("Could not add open_at column to tests: " + e.getMessage());
            }
        }
    }

    /**
     * Adds the {@code close_at} column (nullable — exam scheduling window
     * end). Idempotent: swallows MySQL error 1060 (duplicate column).
     */
    private void ensureCloseAtColumn() {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("ALTER TABLE tests ADD COLUMN close_at DATETIME NULL");
        } catch (SQLException e) {
            if (e.getErrorCode() != 1060) {
                System.err.println("Could not add close_at column to tests: " + e.getMessage());
            }
        }
    }

    /**
     * Adds the {@code exam_code} column (nullable — the 4-digit code students
     * enter to start the exam). Idempotent: swallows MySQL error 1060
     * (duplicate column).
     */
    private void ensureExamCodeColumn() {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("ALTER TABLE tests ADD COLUMN exam_code CHAR(4) NULL");
        } catch (SQLException e) {
            if (e.getErrorCode() != 1060) {
                System.err.println("Could not add exam_code column to tests: " + e.getMessage());
            }
        }
    }

    public void createexam(int test_code, int teacher_id, int duration_minutes, boolean is_active) throws SQLException {
        // Fix: Exact match to your Test constructor
        Test test = new Test(0, String.valueOf(test_code), teacher_id, 0, duration_minutes, "", "", is_active);
        insertTest(test);
    }

    public void insertTest(Test test) throws SQLException {
        String sql = """
        INSERT INTO tests
        (test_code, teacher_id, course_id, duration_minutes, student_instructions, teacher_notes, is_active)
        VALUES (?, ?, ?, ?, ?, ?, ?)
    """;
        PreparedStatement stmt = connection.prepareStatement(sql);
        stmt.setString(1, test.getTestCode());
        stmt.setInt(2, test.getTeacherId());
        stmt.setInt(3, test.getCourseId());
        stmt.setInt(4, test.getDurationMinutes());
        stmt.setString(5, test.getStudentInstructions());
        stmt.setString(6, test.getTeacherNotes());
        stmt.setBoolean(7, test.isActive());
        stmt.executeUpdate();
    }

    public int insertTestGetId(Test test) throws SQLException {
        String sql = """
        INSERT INTO tests
        (test_code, teacher_id, course_id, duration_minutes, student_instructions, teacher_notes, is_active, approval_status)
        VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING')
    """;
        PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
        stmt.setString(1, test.getTestCode());
        stmt.setInt(2, test.getTeacherId());
        stmt.setInt(3, test.getCourseId());
        stmt.setInt(4, test.getDurationMinutes());
        stmt.setString(5, test.getStudentInstructions());
        stmt.setString(6, test.getTeacherNotes());
        stmt.setBoolean(7, test.isActive());
        stmt.executeUpdate();
        ResultSet keys = stmt.getGeneratedKeys();
        if (!keys.next()) throw new SQLException("No generated key returned from test insert.");
        int testId = keys.getInt(1);

        String upsert = """
        INSERT INTO teacher_statistics (teacher_id, tests_count, questions_count)
        VALUES (?, 1, 0)
        ON DUPLICATE KEY UPDATE tests_count = tests_count + 1
    """;
        PreparedStatement us = connection.prepareStatement(upsert);
        us.setInt(1, test.getTeacherId());
        us.executeUpdate();

        return testId;
    }

    public void deleteTestById(int id) throws SQLException {
        String sql = "DELETE FROM tests WHERE id = ?";
        PreparedStatement stmt = connection.prepareStatement(sql);
        stmt.setInt(1, id);
        stmt.executeUpdate();
    }

    public void updateTest(Test test) throws SQLException {
        String sql = """
        UPDATE tests
        SET test_code = ?, teacher_id = ?, course_id = ?, duration_minutes = ?, 
            student_instructions = ?, teacher_notes = ?, is_active = ?
        WHERE id = ?
    """;
        PreparedStatement stmt = connection.prepareStatement(sql);
        stmt.setString(1, test.getTestCode());
        stmt.setInt(2, test.getTeacherId());
        stmt.setInt(3, test.getCourseId());
        stmt.setInt(4, test.getDurationMinutes());
        stmt.setString(5, test.getStudentInstructions());
        stmt.setString(6, test.getTeacherNotes());
        stmt.setBoolean(7, test.isActive());
        stmt.setInt(8, test.getId());
        stmt.executeUpdate();
    }

    public List<Exam> getExamsByTeacherId(int teacherId) throws SQLException {
        String sql = """
        SELECT t.id, COALESCE(t.teacher_notes, '') AS title,
               COALESCE(c.course_name, '') AS course_name,
               COALESCE(t.student_instructions, '') AS instructions,
               t.duration_minutes, t.teacher_id, t.is_active, t.approval_status,
               t.rejection_reason, t.open_at, t.close_at, t.exam_code,
               COUNT(tq.question_id) AS question_count
        FROM tests t
        LEFT JOIN courses c ON c.id = t.course_id
        LEFT JOIN test_questions tq ON tq.test_id = t.id
        WHERE t.teacher_id = ?
        GROUP BY t.id, c.course_name, t.teacher_notes, t.student_instructions,
                 t.duration_minutes, t.teacher_id, t.is_active, t.approval_status,
                 t.rejection_reason, t.open_at, t.close_at, t.exam_code
        ORDER BY t.id DESC
    """;
        PreparedStatement stmt = connection.prepareStatement(sql);
        stmt.setInt(1, teacherId);
        ResultSet rs = stmt.executeQuery();
        List<Exam> exams = new ArrayList<>();
        while (rs.next()) {
            exams.add(mapRowToExamHeader(rs));
        }
        return exams;
    }

    public Exam getExamHeaderById(int examId) throws SQLException {
        String sql = """
        SELECT t.id, COALESCE(t.teacher_notes, '') AS title,
               COALESCE(c.course_name, '') AS course_name,
               COALESCE(t.student_instructions, '') AS instructions,
               t.duration_minutes, t.teacher_id, t.is_active, t.approval_status,
               t.rejection_reason, t.open_at, t.close_at, t.exam_code,
               COUNT(tq.question_id) AS question_count
        FROM tests t
        LEFT JOIN courses c ON c.id = t.course_id
        LEFT JOIN test_questions tq ON tq.test_id = t.id
        WHERE t.id = ?
        GROUP BY t.id, t.approval_status
    """;
        PreparedStatement stmt = connection.prepareStatement(sql);
        stmt.setInt(1, examId);
        ResultSet rs = stmt.executeQuery();
        if (rs.next()) return mapRowToExamHeader(rs);
        return null;
    }

    /**
     * Looks up an exam header by its 4-digit student-facing code, for
     * {@code START_EXAM_BY_CODE}. If more than one exam ever shares a code
     * (codes aren't enforced unique), the most recently created one wins.
     */
    public Exam getExamHeaderByCode(String examCode) throws SQLException {
        String sql = """
        SELECT t.id, COALESCE(t.teacher_notes, '') AS title,
               COALESCE(c.course_name, '') AS course_name,
               COALESCE(t.student_instructions, '') AS instructions,
               t.duration_minutes, t.teacher_id, t.is_active, t.approval_status,
               t.rejection_reason, t.open_at, t.close_at, t.exam_code,
               COUNT(tq.question_id) AS question_count
        FROM tests t
        LEFT JOIN courses c ON c.id = t.course_id
        LEFT JOIN test_questions tq ON tq.test_id = t.id
        WHERE t.exam_code = ?
        GROUP BY t.id, t.approval_status
        ORDER BY t.id DESC
        LIMIT 1
    """;
        PreparedStatement stmt = connection.prepareStatement(sql);
        stmt.setString(1, examCode);
        ResultSet rs = stmt.executeQuery();
        if (rs.next()) return mapRowToExamHeader(rs);
        return null;
    }

    /**
     * Adds time to an exam that is already running (spec 7).
     *
     * The new duration is persisted rather than only pushed to the students
     * sitting the exam right now, so a student who starts the exam after the
     * extension was granted gets the longer duration too. When the exam has a
     * closing time, that moves by the same amount — otherwise the extension
     * would hand out minutes the scheduling window then refuses to honour.
     *
     * @param examId exam to extend
     * @param extraMinutes minutes to add
     * @throws SQLException if the update fails
     */
    public void extendDuration(int examId, int extraMinutes) throws SQLException {
        String sql = """
        UPDATE tests
        SET duration_minutes = duration_minutes + ?,
            close_at = CASE
                           WHEN close_at IS NULL THEN NULL
                           ELSE DATE_ADD(close_at, INTERVAL ? MINUTE)
                       END
        WHERE id = ?
    """;
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, extraMinutes);
            stmt.setInt(2, extraMinutes);
            stmt.setInt(3, examId);
            stmt.executeUpdate();
        }
    }

    public void setExamActive(int examId, boolean active) throws SQLException {
        String sql = "UPDATE tests SET is_active = ? WHERE id = ?";
        PreparedStatement stmt = connection.prepareStatement(sql);
        stmt.setBoolean(1, active);
        stmt.setInt(2, examId);
        stmt.executeUpdate();
    }

    private Exam mapRowToExamHeader(ResultSet rs) throws SQLException {
        Exam exam = new Exam(
            rs.getInt("id"),
            rs.getString("title"),
            rs.getString("course_name"),
            rs.getString("instructions"),
            rs.getInt("duration_minutes"),
            rs.getInt("teacher_id"),
            null
        );
        exam.setActive(rs.getBoolean("is_active"));
        exam.setQuestionCount(rs.getInt("question_count"));
        exam.setApprovalStatus(rs.getString("approval_status"));
        exam.setRejectionReason(rs.getString("rejection_reason"));
        exam.setOpenAt(rs.getTimestamp("open_at"));
        exam.setCloseAt(rs.getTimestamp("close_at"));
        exam.setExamCode(rs.getString("exam_code"));
        return exam;
    }

    /**
     * Every exam in the system, across all teachers — the principal's
     * system-wide exam listing (spec 11).
     *
     * Unlike {@link #getExamsByTeacherId(int)} this also resolves the
     * owning teacher's name, since a listing spanning every teacher is not
     * readable with bare numeric IDs.
     *
     * @return all exams, newest first
     * @throws SQLException if the query fails
     */
    public List<Exam> getAllExams() throws SQLException {
        String sql = """
        SELECT t.id, COALESCE(t.teacher_notes, '') AS title,
               COALESCE(c.course_name, '') AS course_name,
               COALESCE(t.student_instructions, '') AS instructions,
               t.duration_minutes, t.teacher_id, t.is_active, t.approval_status,
               t.rejection_reason, t.open_at, t.close_at, t.exam_code,
               COALESCE(NULLIF(u.full_name, ''), u.username, '') AS teacher_name,
               COUNT(tq.question_id) AS question_count
        FROM tests t
        LEFT JOIN courses c ON c.id = t.course_id
        LEFT JOIN users u ON u.id = t.teacher_id
        LEFT JOIN test_questions tq ON tq.test_id = t.id
        GROUP BY t.id, c.course_name, t.teacher_notes, t.student_instructions,
                 t.duration_minutes, t.teacher_id, t.is_active, t.approval_status,
                 t.rejection_reason, t.open_at, t.close_at, t.exam_code, u.full_name, u.username
        ORDER BY t.id DESC
    """;
        Statement stmt = connection.createStatement();
        ResultSet rs = stmt.executeQuery(sql);
        List<Exam> exams = new ArrayList<>();
        while (rs.next()) {
            Exam exam = mapRowToExamHeader(rs);
            exam.setTeacherName(rs.getString("teacher_name"));
            exams.add(exam);
        }
        return exams;
    }

    public List<Exam> getPendingExams() throws SQLException {
        String sql = """
        SELECT t.id, COALESCE(t.teacher_notes, '') AS title,
               COALESCE(c.course_name, '') AS course_name,
               COALESCE(t.student_instructions, '') AS instructions,
               t.duration_minutes, t.teacher_id, t.is_active,
               t.approval_status,
               t.rejection_reason, t.open_at, t.close_at, t.exam_code,
               COUNT(tq.question_id) AS question_count
        FROM tests t
        LEFT JOIN courses c ON c.id = t.course_id
        LEFT JOIN test_questions tq ON tq.test_id = t.id
        WHERE t.approval_status = 'PENDING'
        GROUP BY t.id, c.course_name, t.teacher_notes, t.student_instructions,
                 t.duration_minutes, t.teacher_id, t.is_active, t.approval_status,
                 t.rejection_reason, t.open_at, t.close_at, t.exam_code
        ORDER BY t.id DESC
    """;
        Statement stmt = connection.createStatement();
        ResultSet rs = stmt.executeQuery(sql);
        List<Exam> exams = new ArrayList<>();
        while (rs.next()) {
            exams.add(mapRowToExamHeader(rs));
        }
        return exams;
    }

    public Exam approveExam(int examId) throws SQLException {
        String sql = "UPDATE tests SET approval_status = 'APPROVED' WHERE id = ?";
        PreparedStatement stmt = connection.prepareStatement(sql);
        stmt.setInt(1, examId);
        stmt.executeUpdate();
        return getExamHeaderById(examId);
    }

    /**
     * Rejects an exam and records why, so the teacher can see the reason
     * (spec 4.2).
     *
     * @param examId identifier of the exam to reject
     * @param reason non-blank reason shown to the teacher
     */
    public Exam rejectExam(int examId, String reason) throws SQLException {
        String sql = "UPDATE tests SET approval_status = 'REJECTED', rejection_reason = ? WHERE id = ?";
        PreparedStatement stmt = connection.prepareStatement(sql);
        stmt.setString(1, reason);
        stmt.setInt(2, examId);
        stmt.executeUpdate();
        return getExamHeaderById(examId);
    }

    /**
     * Sets the scheduling window and 4-digit start code for an exam. Callers
     * must already have verified the exam is APPROVED, the code is exactly 4
     * digits, and closeAt is strictly after openAt — this method just persists.
     */
    public Exam scheduleExam(int examId, Timestamp openAt, Timestamp closeAt, String examCode) throws SQLException {
        String sql = "UPDATE tests SET open_at = ?, close_at = ?, exam_code = ? WHERE id = ?";
        PreparedStatement stmt = connection.prepareStatement(sql);
        stmt.setTimestamp(1, openAt);
        stmt.setTimestamp(2, closeAt);
        stmt.setString(3, examCode);
        stmt.setInt(4, examId);
        stmt.executeUpdate();
        return getExamHeaderById(examId);
    }

    public Test getTestById(int id) throws SQLException {
        String sql = "SELECT * FROM tests WHERE id = ?";
        PreparedStatement stmt = connection.prepareStatement(sql);
        stmt.setInt(1, id);
        ResultSet rs = stmt.executeQuery();

        if(rs.next()) {
            // Fix: Exact match to your Test constructor
            return new Test(
                    rs.getInt("id"),
                    rs.getString("test_code"),
                    rs.getInt("teacher_id"),
                    rs.getInt("course_id"),
                    rs.getInt("duration_minutes"),
                    rs.getString("student_instructions"),
                    rs.getString("teacher_notes"),
                    rs.getBoolean("is_active")
            );
        }
        return null;
    }
}
