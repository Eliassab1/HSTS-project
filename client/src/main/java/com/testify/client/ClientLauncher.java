package com.testify.client;

import javafx.application.Application;

/**
 * Entry point for the runnable fat jar.
 *
 * <p>This class exists only so the jar's {@code Main-Class} is NOT a subclass
 * of {@link Application}. When the JVM launcher is handed an
 * {@code Application} subclass and the JavaFX classes come from the classpath
 * rather than the module path — which is exactly how a shaded fat jar loads
 * them — it aborts with "JavaFX runtime components are missing, and are
 * required to run this application" before any of our code runs.
 *
 * <p>Launching indirectly from a plain class sidesteps that check, so the fat
 * jar runs with a bare {@code java -jar} on a machine that has no separate
 * JavaFX SDK installed.
 */
public final class ClientLauncher {

    private ClientLauncher() {
    }

    /**
     * Starts the HSTS client.
     *
     * @param args passed through to the JavaFX application
     */
    public static void main(String[] args) {
        Application.launch(ClientApplication.class, args);
    }
}
