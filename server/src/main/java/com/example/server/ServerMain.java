package com.example.server;

import com.testify.common.BotEngineFactory;
import com.testify.common.BotQuestionDAO;
import com.testify.common.BotSourceDAO;
import com.testify.common.CourseBotDAO;
import com.testify.common.CourseDAO;
import com.testify.common.CourseTeacherDAO;
import com.testify.common.DatabaseConnection;
import com.testify.common.EnrollmentDAO;
import com.testify.common.EnvConfig;
import com.testify.common.ImagesDAO;
import com.testify.common.QUESTIONSDAO;
import com.testify.common.TestsDAO;
import com.testify.common.UserDAO;
import com.testify.common.UserSettingsDAO;
import com.testify.common.student_answersDAO;
import com.testify.common.test_questionsDAO;
import com.testify.common.test_submissionsDAO;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

public class ServerMain {
    public static void main(String[] args) {
        // Verify DB connectivity before accepting client connections.
        // This surfaces a bad password or missing schema immediately at startup
        // instead of crashing silently on the first client request.
        // Before anything reads configuration, say whether a .env was found.
        // A key that fails to load looks exactly like a key that is wrong, so
        // this line is the difference between a two-minute fix and an hour.
        if (EnvConfig.getLoadedFrom() != null) {
            System.out.println("Loaded configuration from " + EnvConfig.getLoadedFrom()
                    + " (real environment variables take precedence).");
        }

        try {
            DatabaseConnection.getInstance();
            System.out.println("Database connection established.");

            // Creates any table that's missing in MySQL Workbench (each DAO's
            // CREATE TABLE IF NOT EXISTS is a no-op for tables that already exist).
            initializeDatabaseTables();

            // Ensure test_submissions has the correct CHECK constraints.
            // The table may have been created before 'GRADED' was a valid status;
            // this drops stale constraints and recreates them on every startup.
            test_submissionsDAO tsDAO = new test_submissionsDAO();
            tsDAO.ensureCorrectConstraints();

            // Say out loud which learning-bot engine this run will use. The
            // offline fallback is easy to end up on by accident (an unset
            // key in the launch configuration), and silently degrading is
            // exactly the failure mode that gets discovered during a demo.
            if (BotEngineFactory.isGenerationAvailable()) {
                System.out.println("Learning bot: Claude engine ready (model "
                        + BotEngineFactory.getConfiguredModel() + ").");
            } else {
                System.out.println("Learning bot: ANTHROPIC_API_KEY is not set - "
                        + "running on local retrieval. Bots answer from course material; "
                        + "question generation is unavailable.");
            }
        } catch (Exception e) {
            System.err.println("ERROR: Could not initialize the database.");
            System.err.println("Check that MySQL is running and hsts_db exists.");
            System.err.println("Detail: " + e.getMessage());
            System.exit(1);
        }

        TestifyServer server = new TestifyServer(5555);
        try {
            server.listen();
        } catch (IOException e) {
            System.err.println("ERROR: Could not start server on port 5555.");
            System.err.println("Detail: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Creates every table the app needs, via each DAO's own
     * "CREATE TABLE IF NOT EXISTS" statement. Safe to run on every startup:
     * tables that already exist in the MySQL Workbench schema are left alone,
     * and only genuinely missing tables get created.
     */
    private static void initializeDatabaseTables() throws Exception {
        Connection connection = DatabaseConnection.getConnection();
        if (connection == null || connection.isClosed()) {
            throw new SQLException("Database connection is not available.");
        }
        /*
         1
         */
        UserDAO userDAO = new UserDAO(connection);
        userDAO.createUsersTable();
        userDAO.ensureAvatarUrlColumn();
        CourseDAO courseDAO = new CourseDAO();
        courseDAO.createCoursesTable();
        ImagesDAO imagesDAO = new ImagesDAO();
        imagesDAO.createTableIfNotExists();
        /*
         * 2
         */
        EnrollmentDAO enrollmentDAO = new EnrollmentDAO(connection);
        enrollmentDAO.createEnrollmentsTable();
        QUESTIONSDAO questionsDAO = new QUESTIONSDAO();
        questionsDAO.createQuestionsTable();
        questionsDAO.createQuestionsHistoryTable();
        TestsDAO testsDAO = new TestsDAO();
        testsDAO.createTestsTable();
        testsDAO.createTestsHistoryTable();
        /*
         3
         */
        test_questionsDAO testQuestionsDAO = new test_questionsDAO();
        testQuestionsDAO.createTestQuestionsTable();
        test_submissionsDAO testSubmissionsDAO =
                new test_submissionsDAO();
        testSubmissionsDAO.createTestSubmissionsTable();
        /*
         4
         */
        student_answersDAO studentAnswersDAO =
                new student_answersDAO(connection);
        studentAnswersDAO.createStudentAnswersTable();
        UserSettingsDAO userSettingsDAO = new UserSettingsDAO();
        userSettingsDAO.createUserSettingsTable();
        // CREATE TABLE IF NOT EXISTS leaves an existing table's CHECK alone, so
        // a database made before the colour-blind theme would refuse to store it.
        userSettingsDAO.ensureThemeConstraint();
        /*
         * 5 — learning bot (spec 13/14). course_teachers and course_bots both
         * reference courses and users, so they come after those; bot_sources
         * and bot_questions reference course_bots, so they come after it.
         */
        CourseTeacherDAO courseTeacherDAO = new CourseTeacherDAO();
        courseTeacherDAO.createCourseTeachersTable();
        CourseBotDAO courseBotDAO = new CourseBotDAO();
        courseBotDAO.createCourseBotsTable();
        BotSourceDAO botSourceDAO = new BotSourceDAO();
        botSourceDAO.createBotSourcesTable();
        BotQuestionDAO botQuestionDAO = new BotQuestionDAO();
        botQuestionDAO.createBotQuestionsTable();
        System.out.println("All database tables are ready.");
    }
}
