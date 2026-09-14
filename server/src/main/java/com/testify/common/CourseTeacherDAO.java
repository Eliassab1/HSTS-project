package com.testify.common;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Which teachers are attached to which courses.
 *
 * The schema had no teacher-to-course link at all before this table:
 * {@code tests.teacher_id} records who authored one exam, which is not the
 * same thing and cannot answer "may this teacher edit this course's bot".
 * Spec 13.3 requires exactly that — <i>other</i> teachers of the course can
 * edit the bot's sources, not only whoever created it — so the relationship
 * had to become a first-class row.
 */
public class CourseTeacherDAO {

    private final Connection connection;

    public CourseTeacherDAO() {
        this.connection = DatabaseConnection.getConnection();
    }

    /**
     * Creates the join table if it is missing. Safe on every startup.
     *
     * @throws SQLException if the statement fails
     */
    public void createCourseTeachersTable() throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS course_teachers (
                course_id  INT NOT NULL,
                teacher_id INT NOT NULL,
                PRIMARY KEY (course_id, teacher_id),
                FOREIGN KEY (course_id)  REFERENCES courses(id),
                FOREIGN KEY (teacher_id) REFERENCES users(id)
            )
        """;
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }

    /**
     * The authorisation check behind every teacher-side bot action.
     *
     * @param teacherId user ID from the session, never from a payload
     * @param courseId course whose bot is being read or written
     * @return true when that teacher is attached to that course
     * @throws SQLException if the query fails
     */
    public boolean isTeacherOfCourse(int teacherId, int courseId) throws SQLException {
        String sql = "SELECT 1 FROM course_teachers WHERE teacher_id = ? AND course_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, teacherId);
            stmt.setInt(2, courseId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Every course a teacher is attached to — the course picker on the bot
     * editor and the generation screen.
     *
     * @param teacherId the teacher
     * @return their courses, by name
     * @throws SQLException if the query fails
     */
    public List<Course> getCoursesForTeacher(int teacherId) throws SQLException {
        String sql = """
            SELECT c.id, c.course_code, c.course_name, c.subject_code, c.subject_name
            FROM course_teachers ct
            JOIN courses c ON c.id = ct.course_id
            WHERE ct.teacher_id = ?
            ORDER BY c.course_name
        """;
        List<Course> courses = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, teacherId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    courses.add(new Course(
                            rs.getInt("id"),
                            rs.getString("course_code"),
                            rs.getString("course_name"),
                            rs.getString("subject_code"),
                            rs.getString("subject_name")
                    ));
                }
            }
        }
        return courses;
    }

    /**
     * Attaches a teacher to a course. Idempotent — re-adding an existing pair
     * is a no-op rather than a primary-key violation.
     *
     * @param teacherId the teacher
     * @param courseId the course
     * @throws SQLException if the insert fails for any other reason
     */
    public void addTeacherToCourse(int teacherId, int courseId) throws SQLException {
        String sql = """
            INSERT INTO course_teachers (course_id, teacher_id)
            VALUES (?, ?)
            ON DUPLICATE KEY UPDATE course_id = course_id
        """;
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, courseId);
            stmt.setInt(2, teacherId);
            stmt.executeUpdate();
        }
    }

    /**
     * Detaches a teacher from a course.
     *
     * @param teacherId the teacher
     * @param courseId the course
     * @throws SQLException if the delete fails
     */
    public void removeTeacherFromCourse(int teacherId, int courseId) throws SQLException {
        String sql = "DELETE FROM course_teachers WHERE teacher_id = ? AND course_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, teacherId);
            stmt.setInt(2, courseId);
            stmt.executeUpdate();
        }
    }
}
