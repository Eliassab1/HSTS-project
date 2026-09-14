package com.testify.common;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * The per-course learning bots (spec 13).
 *
 * One bot per course, enforced by the UNIQUE index on {@code course_id}
 * rather than by application code — the bot is a property of the course, and
 * every teacher of that course edits the same one (spec 13.3).
 */
public class CourseBotDAO {

    private final Connection connection;

    public CourseBotDAO() {
        this.connection = DatabaseConnection.getConnection();
    }

    /**
     * Creates the bot table if it is missing. Safe on every startup.
     *
     * @throws SQLException if the statement fails
     */
    public void createCourseBotsTable() throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS course_bots (
                id           INT AUTO_INCREMENT PRIMARY KEY,
                course_id    INT NOT NULL UNIQUE,
                bot_name     VARCHAR(100) NOT NULL,
                is_available BOOLEAN NOT NULL DEFAULT TRUE,
                created_by   INT NULL,
                created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (course_id)  REFERENCES courses(id),
                FOREIGN KEY (created_by) REFERENCES users(id)
            )
        """;
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }

    /**
     * The bot for one course, or null when that course has none yet.
     * Sources are NOT loaded — {@code BotSourceDAO} does that separately, so
     * a listing never drags the whole corpus along.
     *
     * @param courseId course whose bot is wanted
     * @return the bot header, or null
     * @throws SQLException if the query fails
     */
    public CourseBot getBotByCourseId(int courseId) throws SQLException {
        return queryOne(BASE_SELECT + " WHERE cb.course_id = ?", courseId);
    }

    /**
     * The bot with a given ID, or null.
     *
     * @param botId bot identifier
     * @return the bot header, or null
     * @throws SQLException if the query fails
     */
    public CourseBot getBotById(int botId) throws SQLException {
        return queryOne(BASE_SELECT + " WHERE cb.id = ?", botId);
    }

    /**
     * The bots a student may use: available bots for courses that student is
     * enrolled in.
     *
     * The enrolment join is what makes this list a permission boundary rather
     * than a convenience — a bot for a course the student does not take never
     * appears, and ASK_BOT re-checks enrolment anyway.
     *
     * @param studentId the student
     * @return their usable bots, by course name
     * @throws SQLException if the query fails
     */
    public List<CourseBot> getAvailableBotsForStudent(int studentId) throws SQLException {
        String sql = BASE_SELECT + """
             JOIN enrollments e ON e.course_id = cb.course_id
             WHERE e.student_id = ? AND cb.is_available = TRUE
             ORDER BY c.course_name
        """;
        List<CourseBot> bots = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, studentId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    bots.add(mapRow(rs));
                }
            }
        }
        return bots;
    }

    /**
     * Creates the course's bot or updates the existing one's name and
     * availability, and returns the saved state.
     *
     * Keyed on {@code course_id}, not on {@code bot.getId()}: a teacher
     * opening the editor for a course that has no bot yet is saving the
     * first version of that course's bot, and has no ID to send.
     *
     * @param bot bot carrying the course, name and availability
     * @param savingTeacherId teacher performing the save; recorded as
     *                        {@code created_by} on first creation only
     * @return the bot as stored
     * @throws SQLException if the write fails
     */
    public CourseBot saveBot(CourseBot bot, int savingTeacherId) throws SQLException {
        String sql = """
            INSERT INTO course_bots (course_id, bot_name, is_available, created_by)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE bot_name = VALUES(bot_name),
                                    is_available = VALUES(is_available)
        """;
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, bot.getCourseId());
            stmt.setString(2, bot.getBotName());
            stmt.setBoolean(3, bot.isAvailable());
            if (savingTeacherId > 0) {
                stmt.setInt(4, savingTeacherId);
            } else {
                stmt.setNull(4, Types.INTEGER);
            }
            stmt.executeUpdate();
        }
        return getBotByCourseId(bot.getCourseId());
    }

    /**
     * Selects the bot header plus its course name and a source count, so one
     * query answers both the student's picker and the teacher's editor
     * header. The subquery keeps the count correct for a bot with no sources,
     * which a JOIN + GROUP BY would drop or complicate.
     */
    private static final String BASE_SELECT = """
        SELECT cb.id, cb.course_id, cb.bot_name, cb.is_available,
               c.course_name,
               (SELECT COUNT(*) FROM bot_sources bs WHERE bs.bot_id = cb.id) AS source_count
        FROM course_bots cb
        JOIN courses c ON c.id = cb.course_id
    """;

    /** Runs a single-int-parameter BASE_SELECT variant and maps one row. */
    private CourseBot queryOne(String sql, int parameter) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, parameter);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    /** Maps one joined bot row; sources are left empty by design. */
    private CourseBot mapRow(ResultSet rs) throws SQLException {
        CourseBot bot = new CourseBot(
                rs.getInt("id"),
                rs.getInt("course_id"),
                rs.getString("bot_name"),
                rs.getBoolean("is_available"));
        bot.setCourseName(rs.getString("course_name"));
        bot.setSourceCount(rs.getInt("source_count"));
        return bot;
    }
}
