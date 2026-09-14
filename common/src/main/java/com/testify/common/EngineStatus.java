package com.testify.common;

import java.io.Serializable;

/**
 * What the server's learning-bot engine can currently do.
 *
 * The teacher's generation screen has to know this <i>before</i> the teacher
 * clicks anything. {@link LocalRetrievalEngine} cannot generate at all, so
 * with no API key configured the generate buttons must arrive already
 * disabled and already explained — offering a button that fails on click, or
 * one that silently produces nothing, is the failure this class exists to
 * prevent.
 *
 * It deliberately carries no secret: whether a key is set, not what it is.
 */
public class EngineStatus implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Whether question and exam generation can run. False whenever the
     * offline fallback is the active engine.
     */
    private boolean generationAvailable;

    /** {@code "CLAUDE"} or {@code "LOCAL"} — which engine answers bot questions. */
    private String engineName;

    /**
     * The model the Claude engine is configured to call. Reported even when
     * generation is unavailable, so a misconfigured model ID is visible
     * rather than mysterious.
     */
    private String model;

    public EngineStatus() {
    }

    public EngineStatus(boolean generationAvailable, String engineName, String model) {
        this.generationAvailable = generationAvailable;
        this.engineName = engineName;
        this.model = model;
    }

    public boolean isGenerationAvailable() {
        return generationAvailable;
    }

    public void setGenerationAvailable(boolean generationAvailable) {
        this.generationAvailable = generationAvailable;
    }

    public String getEngineName() {
        return engineName;
    }

    public void setEngineName(String engineName) {
        this.engineName = engineName;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    /** True when the offline retrieval fallback is the active engine. */
    public boolean isOffline() {
        return "LOCAL".equalsIgnoreCase(engineName);
    }

    /** A sentence a teacher can act on, for the generation screen's banner. */
    public String describe() {
        if (generationAvailable) {
            return "Generation is available (Claude, model " + model + ").";
        }
        return "Generation is unavailable: the server has no ANTHROPIC_API_KEY set, so it is "
                + "running on offline retrieval. Learning bots still answer from course "
                + "material; only generation is affected.";
    }
}
