package com.testify.common;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

    public class EnrollmentDAO {

        private Connection connection;

        public EnrollmentDAO(Connection connection) {
            this.connection = connection;
        }

        public void createEnrollmentsTable()
                throws SQLException {

            String sql = """
            CREATE TABLE IF NOT EXISTS enrollments (

                id INT AUTO_INCREMENT PRIMARY KEY,

                student_id INT NOT NULL,

                course_id INT NOT NULL,

                enrolled_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

                FOREIGN KEY (student_id)
                REFERENCES users(id),

                FOREIGN KEY (course_id)
                REFERENCES courses(id),

                UNIQUE(student_id, course_id)
            )
        """;

            Statement stmt = connection.createStatement();
            stmt.executeUpdate(sql);
        }

        public void insertEnrollment(int studentId,
                                     int courseId)
                throws SQLException {

            String sql = """
            INSERT INTO enrollments
            (
                student_id,
                course_id
            )
            VALUES (?, ?)
        """;

            PreparedStatement stmt =
                    connection.prepareStatement(sql);

            stmt.setInt(1, studentId);
            stmt.setInt(2, courseId);

            stmt.executeUpdate();
        }

        public void deleteEnrollmentById(int id)
                throws SQLException {

            String sql =
                    "DELETE FROM enrollments WHERE id = ?";

            PreparedStatement stmt =
                    connection.prepareStatement(sql);

            stmt.setInt(1, id);

            stmt.executeUpdate();
        }

        public void deleteEnrollment(int studentId,
                                     int courseId)
                throws SQLException {

            String sql = """
            DELETE FROM enrollments
            WHERE student_id = ?
              AND course_id = ?
        """;

            PreparedStatement stmt =
                    connection.prepareStatement(sql);

            stmt.setInt(1, studentId);
            stmt.setInt(2, courseId);

            stmt.executeUpdate();
        }

//        public void updateEnrollment(Enrollment enrollment)
//                throws SQLException {
//
//            String sql = """
//            UPDATE enrollments
//            SET student_id = ?,
//                course_id = ?
//            WHERE id = ?
//        """;
//
//            PreparedStatement stmt =
//                    connection.prepareStatement(sql);
//
//            stmt.setInt(1, enrollment.getStudentId());
//            stmt.setInt(2, enrollment.getCourseId());
//            stmt.setInt(3, enrollment.getId());
//
//            stmt.executeUpdate();
//        }
public List<Course> getCoursesByStudent(int studentId)
        throws SQLException {

    String sql = """
        SELECT c.*
        FROM enrollments e
        JOIN courses c
            ON e.course_id = c.id
        WHERE e.student_id = ?
    """;

    PreparedStatement stmt =
            connection.prepareStatement(sql);

    stmt.setInt(1, studentId);

    ResultSet rs =
            stmt.executeQuery();

    List<Course> courses =
            new ArrayList<>();

    while (rs.next()) {

        courses.add(
                new Course(
                        rs.getInt("id"),
                        rs.getString("course_code"),
                        rs.getString("course_name"),
                        rs.getString("subject_code"),
                        rs.getString("subject_name")
                )
        );
    }

    return courses;
}
        public List<Test> getAvailableTestsForStudentId(int studentId)
                throws SQLException {

            String sql = """
        SELECT t.*
        FROM enrollments e
        JOIN tests t
            ON e.course_id = t.course_id
        WHERE e.student_id = ?
          AND t.is_active = TRUE
          AND t.approval_status = 'APPROVED'
          AND (t.open_at IS NULL OR NOW() >= t.open_at)
          AND (t.close_at IS NULL OR NOW() <= t.close_at)
    """;

            PreparedStatement stmt =
                    connection.prepareStatement(sql);

            stmt.setInt(1, studentId);

            ResultSet rs =
                    stmt.executeQuery();

            List<Test> tests =
                    new ArrayList<>();

            while (rs.next()) {

                tests.add(
                        new Test(
                                rs.getInt("id"),
                                rs.getString("test_code"),
                                rs.getInt("teacher_id"),
                                rs.getInt("course_id"),
                                rs.getInt("duration_minutes"),
                                rs.getString("student_instructions"),
                                rs.getString("teacher_notes"),
                                rs.getBoolean("is_active")
                        )
                );
            }

            return tests;
        }

        /**
         * Checks whether a student is enrolled in a course, used by
         * START_EXAM_BY_CODE to verify course membership before letting a
         * student into an exam found by code.
         */
        public boolean isStudentEnrolled(int studentId, int courseId) throws SQLException {

            String sql = """
        SELECT 1 FROM enrollments WHERE student_id = ? AND course_id = ? LIMIT 1
    """;

            PreparedStatement stmt =
                    connection.prepareStatement(sql);

            stmt.setInt(1, studentId);
            stmt.setInt(2, courseId);

            ResultSet rs = stmt.executeQuery();
            return rs.next();
        }
}

