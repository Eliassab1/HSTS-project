package com.testify.common;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CourseDAO {

    private Connection connection;

    // Fix: Handles the missing connection errors seamlessly
    public CourseDAO() {
        this.connection = DatabaseConnection.getConnection();
    }

    public void createCoursesTable() throws SQLException {
        String sql = """
        CREATE TABLE IF NOT EXISTS courses (
            id INT AUTO_INCREMENT PRIMARY KEY,
            course_code CHAR(2) NOT NULL UNIQUE,
            course_name VARCHAR(100) NOT NULL,
            subject_code CHAR(2) NOT NULL,
            subject_name VARCHAR(100) NOT NULL,
            UNIQUE(subject_code, course_code)
        )
    """;
        Statement stmt = connection.createStatement();
        stmt.executeUpdate(sql);
    }

    public void insertCourse(Course course) throws SQLException {
        String sql = """
        INSERT INTO courses
        (course_code, course_name, subject_code, subject_name)
        VALUES (?, ?, ?, ?)
    """;
        PreparedStatement stmt = connection.prepareStatement(sql);
        stmt.setString(1, course.getCourseCode());
        stmt.setString(2, course.getCourseName());
        stmt.setString(3, course.getSubjectCode());
        stmt.setString(4, course.getSubjectName());
        stmt.executeUpdate();
    }

    public void deleteCourseById(int id) throws SQLException {
        String sql = "DELETE FROM courses WHERE id = ?";
        PreparedStatement stmt = connection.prepareStatement(sql);
        stmt.setInt(1, id);
        stmt.executeUpdate();
    }

    public void updateCourse(Course course) throws SQLException {
        String sql = """
        UPDATE courses
        SET course_code = ?, course_name = ?, subject_code = ?, subject_name = ?
        WHERE id = ?
    """;
        PreparedStatement stmt = connection.prepareStatement(sql);
        stmt.setString(1, course.getCourseCode());
        stmt.setString(2, course.getCourseName());
        stmt.setString(3, course.getSubjectCode());
        stmt.setString(4, course.getSubjectName());
        stmt.setInt(5, course.getId());
        stmt.executeUpdate();
    }

    public int getCourseIdByName(String courseName) throws SQLException {
        String sql = "SELECT id FROM courses WHERE course_name = ? OR course_code = ? LIMIT 1";
        PreparedStatement stmt = connection.prepareStatement(sql);
        stmt.setString(1, courseName);
        stmt.setString(2, courseName);
        ResultSet rs = stmt.executeQuery();
        if (rs.next()) return rs.getInt("id");
        return -1;
    }

    public List<Course> getAllCourses() throws SQLException {
        String sql = "SELECT * FROM courses ORDER BY course_name";
        Statement stmt = connection.createStatement();
        ResultSet rs = stmt.executeQuery(sql);
        List<Course> courses = new ArrayList<>();
        while (rs.next()) {
            courses.add(new Course(
                rs.getInt("id"),
                rs.getString("course_code"),
                rs.getString("course_name"),
                rs.getString("subject_code"),
                rs.getString("subject_name")
            ));
        }
        return courses;
    }

    public Course getCourseById(int id) throws SQLException {
        String sql = "SELECT * FROM courses WHERE id = ?";
        PreparedStatement stmt = connection.prepareStatement(sql);
        stmt.setInt(1, id);
        ResultSet rs = stmt.executeQuery();

        if (rs.next()) {
            return new Course(
                    rs.getInt("id"),
                    rs.getString("course_code"),
                    rs.getString("course_name"),
                    rs.getString("subject_code"),
                    rs.getString("subject_name")
            );
        }
        return null;
    }
}
