package com.testify.demo;

/**
 * One step of the scripted demo: the requirements it evidences, a one-line
 * description for the audience, and the actions that prove it.
 *
 * A scene is data, not control flow. {@link DemoDriver} owns the sequencing,
 * the pacing and the {@code --from} / {@code --only} selection, which is only
 * possible because every scene is addressable by number and independent enough
 * to be skipped.
 */
public final class Scene {

    /** The work of a scene. Allowed to throw — the driver reports and continues. */
    @FunctionalInterface
    public interface Body {
        void run(DemoDriver driver) throws Exception;
    }

    private final int number;
    private final String[] requirements;
    private final String title;
    private final Body body;

    /**
     * @param number     scene number, stable across runs so {@code --from} and
     *                   {@code --only} mean the same thing in rehearsal and on the day
     * @param requirements requirement tags this scene evidences, e.g. {@code "6.1"}
     * @param title      what the audience is told is about to happen
     * @param body       the actions
     */
    public Scene(int number, String[] requirements, String title, Body body) {
        this.number = number;
        this.requirements = requirements;
        this.title = title;
        this.body = body;
    }

    public int getNumber() {
        return number;
    }

    public String[] getRequirements() {
        return requirements;
    }

    public String getTitle() {
        return title;
    }

    public Body getBody() {
        return body;
    }
}
