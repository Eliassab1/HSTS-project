package com.testify.common;

import java.io.Serializable;

/**
 * Payload for {@code ASK_BOT}: which bot, and what the student typed.
 *
 * There is deliberately no student ID here. The asker is taken from the
 * socket's session, the same rule the grading and principal actions follow —
 * an ID in the payload is client-controlled, and here it would decide both
 * whose enrolment is checked and whose exam lockout applies.
 */
public class BotAsk implements Serializable {

    private static final long serialVersionUID = 1L;

    private int botId;
    private String questionText;

    public BotAsk() {
    }

    public BotAsk(int botId, String questionText) {
        this.botId = botId;
        this.questionText = questionText;
    }

    public int getBotId() {
        return botId;
    }

    public void setBotId(int botId) {
        this.botId = botId;
    }

    public String getQuestionText() {
        return questionText;
    }

    public void setQuestionText(String questionText) {
        this.questionText = questionText;
    }
}
