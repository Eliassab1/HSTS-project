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
 * The information sources a course bot answers from (spec 13.2).
 *
 * Every write stamps {@code updated_by}, and every read joins it back to a
 * name: spec 13.3 lets any teacher of the course edit these, and a shared
 * document that does not say who last touched it is a document nobody trusts.
 */
public class BotSourceDAO {

    private final Connection connection;

    public BotSourceDAO() {
        this.connection = DatabaseConnection.getConnection();
    }

    /**
     * Creates the sources table if it is missing. Safe on every startup.
     *
     * @throws SQLException if the statement fails
     */
    public void createBotSourcesTable() throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS bot_sources (
                id         INT AUTO_INCREMENT PRIMARY KEY,
                bot_id     INT NOT NULL,
                title      VARCHAR(200) NOT NULL,
                content    TEXT NOT NULL,
                updated_by INT NULL,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                           ON UPDATE CURRENT_TIMESTAMP,
                FOREIGN KEY (bot_id)     REFERENCES course_bots(id),
                FOREIGN KEY (updated_by) REFERENCES users(id)
            )
        """;
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }

    /**
     * Every source belonging to one bot, oldest first so the teacher's list
     * keeps a stable order as sources are edited.
     *
     * This is also what the engine is handed as its entire permitted corpus.
     *
     * @param botId bot whose material is wanted
     * @return its sources, possibly empty
     * @throws SQLException if the query fails
     */
    public List<BotSource> getSourcesForBot(int botId) throws SQLException {
        String sql = """
            SELECT bs.id, bs.bot_id, bs.title, bs.content, bs.updated_by, bs.updated_at,
                   COALESCE(NULLIF(u.full_name, ''), u.username, '') AS updated_by_name
            FROM bot_sources bs
            LEFT JOIN users u ON u.id = bs.updated_by
            WHERE bs.bot_id = ?
            ORDER BY bs.id
        """;
        List<BotSource> sources = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, botId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    sources.add(mapRow(rs));
                }
            }
        }
        return sources;
    }

    /**
     * One source by ID, or null — used to find which bot a source belongs to
     * before authorising an edit or a delete.
     *
     * @param sourceId source identifier
     * @return the source, or null
     * @throws SQLException if the query fails
     */
    public BotSource getSourceById(int sourceId) throws SQLException {
        String sql = """
            SELECT bs.id, bs.bot_id, bs.title, bs.content, bs.updated_by, bs.updated_at,
                   COALESCE(NULLIF(u.full_name, ''), u.username, '') AS updated_by_name
            FROM bot_sources bs
            LEFT JOIN users u ON u.id = bs.updated_by
            WHERE bs.id = ?
        """;
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, sourceId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    /**
     * Inserts a new source or updates an existing one, always stamping the
     * editing teacher, and returns the saved row.
     *
     * @param source the source; {@code id <= 0} means "insert"
     * @param editingTeacherId teacher performing the edit, from the session
     * @return the source as stored, with its name and timestamp filled in
     * @throws SQLException if the write fails
     */
    public BotSource saveSource(BotSource source, int editingTeacherId) throws SQLException {
        if (source.getId() > 0) {
            String sql = """
                UPDATE bot_sources
                SET title = ?, content = ?, updated_by = ?
                WHERE id = ?
            """;
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, source.getTitle());
                stmt.setString(2, source.getContent());
                setEditor(stmt, 3, editingTeacherId);
                stmt.setInt(4, source.getId());
                stmt.executeUpdate();
            }
            return getSourceById(source.getId());
        }

        String sql = """
            INSERT INTO bot_sources (bot_id, title, content, updated_by)
            VALUES (?, ?, ?, ?)
        """;
        try (PreparedStatement stmt =
                     connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, source.getBotId());
            stmt.setString(2, source.getTitle());
            stmt.setString(3, source.getContent());
            setEditor(stmt, 4, editingTeacherId);
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) return getSourceById(keys.getInt(1));
            }
        }
        throw new SQLException("No generated key returned when inserting a bot source.");
    }

    /**
     * Removes one source.
     *
     * @param sourceId source to delete
     * @return true when a row was removed
     * @throws SQLException if the delete fails
     */
    public boolean deleteSource(int sourceId) throws SQLException {
        try (PreparedStatement stmt =
                     connection.prepareStatement("DELETE FROM bot_sources WHERE id = ?")) {
            stmt.setInt(1, sourceId);
            return stmt.executeUpdate() > 0;
        }
    }

    /** Binds the editing teacher, or SQL NULL when there is no valid session. */
    private void setEditor(PreparedStatement stmt, int index, int teacherId) throws SQLException {
        if (teacherId > 0) {
            stmt.setInt(index, teacherId);
        } else {
            stmt.setNull(index, Types.INTEGER);
        }
    }

    /** Maps one joined source row. */
    private BotSource mapRow(ResultSet rs) throws SQLException {
        BotSource source = new BotSource(
                rs.getInt("id"),
                rs.getInt("bot_id"),
                rs.getString("title"),
                rs.getString("content"));
        source.setUpdatedBy(rs.getInt("updated_by"));
        source.setUpdatedByName(rs.getString("updated_by_name"));
        source.setUpdatedAt(rs.getTimestamp("updated_at") != null
                ? rs.getTimestamp("updated_at").toString()
                : "");
        return source;
    }
}
