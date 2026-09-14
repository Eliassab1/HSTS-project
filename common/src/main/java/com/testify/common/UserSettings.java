package com.testify.common;

import java.io.Serializable;

/**
 * Per-user interface preferences (theme + accessibility).
 *
 * Persisted in the {@code user_settings} table and exchanged between the
 * HSTS client and server. Must stay byte-compatible with the other copy of
 * this class ({@code serialVersionUID = 1L}).
 */
public class UserSettings implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Theme identifiers. */
    public static final String THEME_LILAC = "LILAC";
    public static final String THEME_LIGHT = "LIGHT";
    public static final String THEME_DARK  = "DARK";

    /**
     * Colour-blind friendly theme: the same chrome as Lilac, but every colour
     * that <i>carries meaning</i> — correct/wrong, active/inactive, saved/failed
     * — is swapped from the red/green pair to a blue/orange one, which stays
     * distinguishable under deuteranopia and protanopia (red-green colour
     * blindness, by far the most common form).
     */
    public static final String THEME_COLORBLIND = "COLORBLIND";

    /**
     * Every valid theme, in the order the Settings screen offers them.
     *
     * The single whitelist: the server validates against it, the DAO
     * normalises against it, and the {@code user_settings.theme} CHECK
     * constraint is built to match. A theme added here needs a matching
     * {@code .theme-*} block in style.css and a branch in
     * {@code ClientApplication.applyPreferencesToScene}.
     */
    public static final String[] THEMES = {
            THEME_LILAC, THEME_LIGHT, THEME_DARK, THEME_COLORBLIND
    };

    /** Owner of these settings. */
    private int userId;

    /** One of {@link #THEMES}. */
    private String theme;

    /** UI font scale as a percentage (100 = normal, 115 = large, 130 = x-large). */
    private int fontScale;

    /** Whether the high-contrast accessibility mode is on. */
    private boolean highContrast;

    /** Creates default settings (Lilac theme, 100% font, no high contrast). */
    public UserSettings() {
        this.theme = THEME_LILAC;
        this.fontScale = 100;
        this.highContrast = false;
    }

    public UserSettings(int userId, String theme, int fontScale, boolean highContrast) {
        this.userId = userId;
        this.theme = theme != null ? theme : THEME_LILAC;
        this.fontScale = fontScale > 0 ? fontScale : 100;
        this.highContrast = highContrast;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public int getFontScale() {
        return fontScale;
    }

    public void setFontScale(int fontScale) {
        this.fontScale = fontScale;
    }

    public boolean isHighContrast() {
        return highContrast;
    }

    public void setHighContrast(boolean highContrast) {
        this.highContrast = highContrast;
    }

    @Override
    public String toString() {
        return "UserSettings{userId=" + userId + ", theme='" + theme
                + "', fontScale=" + fontScale + ", highContrast=" + highContrast + '}';
    }
}
