package com.testify.common;

import org.mindrot.jbcrypt.BCrypt;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UserDAO {

    private Connection connection;

    public UserDAO(Connection connection) {
        this.connection = connection;
    }

    public void createUsersTable() throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS users (
                id INT AUTO_INCREMENT PRIMARY KEY,
                username VARCHAR(50) UNIQUE NOT NULL,
                password_hash VARCHAR(255) NOT NULL,
                role VARCHAR(20) NOT NULL,
                full_name VARCHAR(100) NOT NULL,
                grade_level INT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

                CHECK (role IN ('STUDENT', 'TEACHER', 'PRINCIPAL')),
                CHECK (
                    (role = 'STUDENT' AND grade_level BETWEEN 9 AND 12)
                    OR
                    (role IN ('TEACHER', 'PRINCIPAL') AND grade_level IS NULL)
                )
            )
        """;
        Statement stmt = connection.createStatement();
        stmt.executeUpdate(sql);
        ensureNationalIdColumn();
    }

    /**
     * Adds the {@code national_id} column (nullable, ת"ז entered by students
     * at exam start) plus its unique index to an existing {@code users}
     * table. Idempotent: the column-add swallows MySQL error 1060 (duplicate
     * column) and the index-add swallows error 1061 (duplicate key name), so
     * either half can already exist without the other failing.
     */
    private void ensureNationalIdColumn() {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("ALTER TABLE users ADD COLUMN national_id VARCHAR(9) NULL");
        } catch (SQLException e) {
            if (e.getErrorCode() != 1060) {
                System.err.println("Could not add national_id column to users: " + e.getMessage());
            }
        }
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(
                    "ALTER TABLE users ADD UNIQUE INDEX idx_users_national_id (national_id)");
        } catch (SQLException e) {
            if (e.getErrorCode() != 1061) {
                System.err.println("Could not add national_id unique index to users: " + e.getMessage());
            }
        }
    }

    public void insertUser(User user) throws SQLException {
        String sql = """
            INSERT INTO users
            (username, password_hash, role, full_name, grade_level, national_id)
            VALUES (?, ?, ?, ?, ?, ?)
        """;
        PreparedStatement stmt = connection.prepareStatement(sql);
        stmt.setString(1, user.getUsername());
        stmt.setString(2, BCrypt.hashpw(user.getPassword(), BCrypt.gensalt(12)));
        stmt.setString(3, user.getRole());
        stmt.setString(4, user.getFullName());
        setGradeLevel(stmt, 5, user);
        if (user.getNationalId() != null && !user.getNationalId().isBlank()) {
            stmt.setString(6, user.getNationalId());
        } else {
            stmt.setNull(6, Types.VARCHAR);
        }
        stmt.executeUpdate();
    }

    /**
     * Binds {@code users.grade_level} from the model.
     *
     * The table's CHECK requires a student to have a grade level of 9-12 and
     * a teacher/principal to have none, so this writes the model's value for
     * a student and NULL for anyone else — writing NULL unconditionally, as
     * this used to, made every student insert fail that CHECK.
     */
    private void setGradeLevel(PreparedStatement stmt, int index, User user) throws SQLException {
        if ("STUDENT".equalsIgnoreCase(user.getRole()) && user.getGradeLevel() != null) {
            stmt.setInt(index, user.getGradeLevel());
        } else {
            stmt.setNull(index, Types.INTEGER);
        }
    }

    public void ensureAvatarUrlColumn() {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("ALTER TABLE users ADD COLUMN avatar_url VARCHAR(255) NULL");
        } catch (SQLException e) {
            if (e.getErrorCode() != 1060) {
                System.err.println("Could not add avatar_url column to users: " + e.getMessage());
            }
        }
    }

    public User updateProfile(int userId, String fullName, String avatarUrl) throws SQLException {
        String sql = "UPDATE users SET full_name = ?, avatar_url = ? WHERE id = ?";
        PreparedStatement stmt = connection.prepareStatement(sql);
        stmt.setString(1, fullName);
        if (avatarUrl != null && !avatarUrl.isBlank()) {
            stmt.setString(2, avatarUrl);
        } else {
            stmt.setNull(2, Types.VARCHAR);
        }
        stmt.setInt(3, userId);
        stmt.executeUpdate();
        return getUserById(userId);
    }

    public void deleteUserById(int id) throws SQLException {
        String sql = "DELETE FROM users WHERE id = ?";
        PreparedStatement stmt = connection.prepareStatement(sql);
        stmt.setInt(1, id);
        stmt.executeUpdate();
    }

    public User getUserById(int id) throws SQLException {
        String sql = "SELECT * FROM users WHERE id = ?";
        PreparedStatement stmt = connection.prepareStatement(sql);
        stmt.setInt(1, id);
        ResultSet rs = stmt.executeQuery();

        if (rs.next()) {
            User user = new User();
            user.setUserId(rs.getInt("id"));
            user.setUsername(rs.getString("username"));
            user.setPassword(rs.getString("password_hash"));
            user.setRole(rs.getString("role"));
            user.setFullName(rs.getString("full_name"));
            user.setGradeLevel(rs.getObject("grade_level", Integer.class));
            try { user.setAvatarUrl(rs.getString("avatar_url")); } catch (SQLException ignored) {}
            try { user.setNationalId(rs.getString("national_id")); } catch (SQLException ignored) {}
            return user;
        }
        return null;
    }

    public User authenticateUser(String username, String password) {
        String query = "SELECT * FROM users WHERE username = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String storedHash = rs.getString("password_hash");
                    boolean matches;
                    if (storedHash.startsWith("$2")) {
                        // BCrypt hash — use proper verification
                        matches = BCrypt.checkpw(password, storedHash);
                    } else {
                        // Legacy plaintext stored in dev DB — direct compare
                        matches = storedHash.equals(password);
                    }
                    if (matches) {
                        User user = new User();
                        user.setUserId(rs.getInt("id"));
                        user.setUsername(rs.getString("username"));
                        user.setPassword(storedHash);
                        user.setRole(rs.getString("role"));
                        user.setFullName(rs.getString("full_name"));
                        user.setGradeLevel(rs.getObject("grade_level", Integer.class));
                        try { user.setAvatarUrl(rs.getString("avatar_url")); } catch (SQLException ignored) {}
                        try { user.setNationalId(rs.getString("national_id")); } catch (SQLException ignored) {}
                        return user;
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void updateUser(User user) throws SQLException {
        String sql = """
            UPDATE users
            SET username = ?,
                password_hash = ?,
                role = ?,
                full_name = ?,
                grade_level = ?,
                national_id = ?
            WHERE id = ?
        """;
        PreparedStatement stmt = connection.prepareStatement(sql);
        stmt.setString(1, user.getUsername());
        String rawPw = user.getPassword();
        String hashed = (rawPw != null && rawPw.startsWith("$2"))
                ? rawPw : BCrypt.hashpw(rawPw, BCrypt.gensalt(12));
        stmt.setString(2, hashed);
        stmt.setString(3, user.getRole());
        stmt.setString(4, user.getFullName());
        setGradeLevel(stmt, 5, user);
        if (user.getNationalId() != null && !user.getNationalId().isBlank()) {
            stmt.setString(6, user.getNationalId());
        } else {
            stmt.setNull(6, Types.VARCHAR);
        }
        stmt.setInt(7, user.getUserId());
        stmt.executeUpdate();
    }

    /**
     * Every account, ordered by role then username, for the principal's user
     * management screen.
     *
     * <b>The SELECT list is the point.</b> {@code password_hash} is not read,
     * so it cannot reach the client even by accident — unlike
     * {@link #getUserById(int)}, which loads it because authentication needs
     * it. Nothing on the management screen needs it, so nothing here fetches it.
     *
     * @return every user, staff first, alphabetical within a role
     * @throws SQLException if the query fails
     */
    public List<User> getAllUsers() throws SQLException {
        String sql = """
            SELECT id, username, role, full_name, grade_level, national_id, avatar_url
            FROM users
            ORDER BY FIELD(role, 'PRINCIPAL', 'TEACHER', 'STUDENT'), username
        """;
        List<User> users = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                User user = new User();
                user.setUserId(rs.getInt("id"));
                user.setUsername(rs.getString("username"));
                user.setRole(rs.getString("role"));
                user.setFullName(rs.getString("full_name"));
                user.setGradeLevel(rs.getObject("grade_level", Integer.class));
                user.setNationalId(rs.getString("national_id"));
                user.setAvatarUrl(rs.getString("avatar_url"));
                users.add(user);
            }
        }
        return users;
    }

    /**
     * Inserts and returns the stored row, so the caller can answer with the
     * account as it actually exists — with its generated id — rather than with
     * the request it was handed.
     *
     * @param user the account to create, carrying a RAW password
     * @return the stored account, or null if it could not be read back
     * @throws SQLException if the insert fails, including a duplicate username
     *                      or national ID (MySQL error 1062)
     */
    public User insertUserReturning(User user) throws SQLException {
        insertUser(user);
        try (PreparedStatement stmt =
                     connection.prepareStatement("SELECT id FROM users WHERE username = ?")) {
            stmt.setString(1, user.getUsername());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return getUserById(rs.getInt("id"));
                }
            }
        }
        return null;
    }

    public String checkRole(int id) throws SQLException {
        String sql = "SELECT role FROM users WHERE id = ?";
        PreparedStatement stmt = connection.prepareStatement(sql);
        stmt.setInt(1, id);
        ResultSet rs = stmt.executeQuery();
        if (rs.next()) {
            return rs.getString("role");
        }
        return null;
    }
}
