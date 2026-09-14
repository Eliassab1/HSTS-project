package com.testify.common;

import java.io.Serializable;

/**
 * One piece of teacher-supplied material a course bot is allowed to answer
 * from (spec 13.2 — "define and edit the bot's information sources").
 *
 * The sources are not decoration: {@code BotEngine.answerQuestion} passes
 * them to the engine as the ONLY permitted material, so a course with no
 * sources has a bot that can only say it does not know.
 */
public class BotSource implements Serializable {

    private static final long serialVersionUID = 1L;

    private int id;
    private int botId;
    private String title;
    private String content;

    /** User ID of the teacher who last saved this source; 0 when unknown. */
    private int updatedBy;

    /**
     * Display name behind {@link #updatedBy}, filled by the DAO's join so the
     * editor screen can show "last edited by …" without a second round trip.
     * Multi-teacher editing (spec 13.3) is invisible without it.
     */
    private String updatedByName;

    /** Timestamp as a display string; not a Date, to keep the wire simple. */
    private String updatedAt;

    public BotSource() {
    }

    public BotSource(int id, int botId, String title, String content) {
        this.id = id;
        this.botId = botId;
        this.title = title;
        this.content = content;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getBotId() {
        return botId;
    }

    public void setBotId(int botId) {
        this.botId = botId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(int updatedBy) {
        this.updatedBy = updatedBy;
    }

    public String getUpdatedByName() {
        return updatedByName;
    }

    public void setUpdatedByName(String updatedByName) {
        this.updatedByName = updatedByName;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return title == null ? "(untitled source)" : title;
    }
}
