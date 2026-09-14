package com.testify.common;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Data access for the {@code user_settings} table — per-user theme and
 * accessibility preferences. One row per user; a default row is created on
 * demand the first time a user's settings are requested.
 */
public class UserSettingsDAO {

    private final Connection connection;

    public UserSettingsDAO() {
        this.connection = DatabaseConnection.getConnection();
    }

    public void createUserSettingsTable() throws SQLException {
        String sql = """
        CREATE TABLE IF NOT EXISTS user_settings (
            user_id INT PRIMARY KEY,
            theme VARCHAR(20) NOT NULL DEFAULT 'LILAC',
            font_scale INT NOT NULL DEFAULT 100,
            high_contrast BOOLEAN NOT NULL DEFAULT FALSE,
            FOREIGN KEY (user_id) REFERENCES users(id),
            CHECK (theme IN ('LILAC','LIGHT','DARK','COLORBLIND')),
            CHECK (font_scale BETWEEN 50 AND 200)
        )
    """;
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }

    /**
     * Brings an existing {@code user_settings} table up to the current theme
     * whitelist, the way {@code test_submissionsDAO.ensureCorrectConstraints()}
     * does for submissions.
     *
     * CREATE TABLE IF NOT EXISTS does nothing to a table that already exists,
     * so a database created before COLORBLIND still carries the three-value
     * CHECK and a VARCHAR(10) column — saving the new theme would be rejected
     * by the constraint rather than by anything in the application. Both the
     * widening and the constraint rewrite are idempotent, so this runs on
     * every startup.
     *
     * Non-fatal throughout: an older MySQL that ignores CHECK constraints
     * simply leaves the server validation as the only gate, which is where the
     * real enforcement lives anyway.
     */
    public void ensureThemeConstraint() {
        try {
            // 'COLORBLIND' is exactly 10 characters, so VARCHAR(10) would hold
            // it with nothing to spare. Widen before touching the constraint.
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate(
                        "ALTER TABLE user_settings MODIFY theme VARCHAR(20) NOT NULL DEFAULT 'LILAC'");
            } catch (SQLException ignored) {
                // Already widened, or the server does not need it.
            }

            List<String> checkNames = new ArrayList<>();
            String findSql = "SELECT CONSTRAINT_NAME FROM information_schema.TABLE_CONSTRAINTS " +
                             "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_settings' " +
                             "AND CONSTRAINT_TYPE = 'CHECK'";
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(findSql)) {
                while (rs.next()) {
                    checkNames.add(rs.getString(1));
                }
            }

            for (String name : checkNames) {
                // The name comes from information_schema, not from client input,
                // but it is embedded as a raw identifier here — validate anyway.
                if (!InputValidator.isSafeIdentifier(name)) {
                    System.err.println("Skipping constraint with unsafe name: " + name);
                    continue;
                }
                try (Statement stmt = connection.createStatement()) {
                    stmt.executeUpdate("ALTER TABLE user_settings DROP CHECK `" + name + "`");
                } catch (SQLException ignored) {}
            }

            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate("ALTER TABLE user_settings ADD CONSTRAINT CHECK "
                        + "(theme IN ('LILAC','LIGHT','DARK','COLORBLIND'))");
            } catch (SQLException e) {
                System.err.println("Could not add theme constraint: " + e.getMessage());
            }

            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate("ALTER TABLE user_settings ADD CONSTRAINT CHECK "
                        + "(font_scale BETWEEN 50 AND 200)");
            } catch (SQLException e) {
                System.err.println("Could not add font scale constraint: " + e.getMessage());
            }

            System.out.println("user_settings constraints verified.");
        } catch (SQLException e) {
            System.err.println("Schema migration warning (non-fatal): " + e.getMessage());
        }
    }

    /**
     * Returns the settings for a user, creating and returning a default row
     * the first time (so the caller always gets a usable object).
     */
    public UserSettings getSettings(int userId) throws SQLException {
        String sql = "SELECT user_id, theme, font_scale, high_contrast FROM user_settings WHERE user_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new UserSettings(
                            rs.getInt("user_id"),
                            rs.getString("theme"),
                            rs.getInt("font_scale"),
                            rs.getBoolean("high_contrast"));
                }
            }
        }
        // No row yet — create a default one and return it.
        UserSettings defaults = new UserSettings(userId,
                UserSettings.THEME_LILAC, 100, false);
        upsertSettings(defaults);
        return defaults;
    }

    /**
     * Inserts or updates the settings row for a user.
     *
     * @return the persisted settings (echoed back)
     */
    public UserSettings upsertSettings(UserSettings settings) throws SQLException {
        String sql = """
        INSERT INTO user_settings (user_id, theme, font_scale, high_contrast)
        VALUES (?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE
            theme = VALUES(theme),
            font_scale = VALUES(font_scale),
            high_contrast = VALUES(high_contrast)
    """;
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, settings.getUserId());
            stmt.setString(2, normaliseTheme(settings.getTheme()));
            stmt.setInt(3, clampFontScale(settings.getFontScale()));
            stmt.setBoolean(4, settings.isHighContrast());
            stmt.executeUpdate();
        }
        return getSettings(settings.getUserId());
    }

    /**
     * Coerces anything unrecognised to Lilac, against the model's whitelist so
     * a theme added there is stored here without a second edit.
     */
    private String normaliseTheme(String theme) {
        for (String known : UserSettings.THEMES) {
            if (known.equalsIgnoreCase(theme)) {
                return known;
            }
        }
        return UserSettings.THEME_LILAC;
    }

    private int clampFontScale(int scale) {
        if (scale < 50) return 50;
        if (scale > 200) return 200;
        return scale;
    }
}
