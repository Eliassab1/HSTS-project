package com.testify.common;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads configuration from the real environment first, and from a {@code .env}
 * file second.
 *
 * <b>Server-side only</b>, like the engines — it is not mirrored into the
 * client tree and is excluded from the common-tree parity check. Only the
 * server holds credentials.
 *
 * Why this exists: the workspace already had a {@code .env} carrying
 * {@code HSTS_DB_URL} / {@code HSTS_DB_USER} / {@code HSTS_DB_PASS}, but
 * nothing read it — {@link DatabaseConnection} called
 * {@code System.getenv} directly, so the file sat there looking effective and
 * doing nothing. Adding {@code ANTHROPIC_API_KEY} to it would have been the
 * same trap a second time, and a key that silently fails to load is
 * indistinguishable from a key that is wrong.
 *
 * <b>The real environment always wins.</b> A variable exported in the shell or
 * set in an IntelliJ run configuration overrides the file, so the file is a
 * convenience for local development and never a way to accidentally override
 * a deliberately configured deployment.
 *
 * The file is looked for in the working directory and then upwards, because
 * the server is launched from the workspace root, from {@code server/}, or
 * from an IDE with either as its working directory.
 */
public final class EnvConfig {

    /** How far up from the working directory to look for a .env file. */
    private static final int MAX_PARENT_LEVELS = 3;

    /** Parsed file contents; empty when there is no .env. Never mutated after load. */
    private static final Map<String, String> FILE_VALUES = load();

    private EnvConfig() {
    }

    /**
     * The value of a configuration variable.
     *
     * @param name variable name
     * @return the real environment's value, else the .env file's, else null
     */
    public static String get(String name) {
        String fromEnvironment = System.getenv(name);
        if (fromEnvironment != null && !fromEnvironment.isBlank()) {
            return fromEnvironment;
        }
        return FILE_VALUES.get(name);
    }

    /**
     * The value of a configuration variable, or a fallback.
     *
     * @param name variable name
     * @param defaultValue value to use when the variable is unset or blank
     * @return the resolved value
     */
    public static String get(String name, String defaultValue) {
        String value = get(name);
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    /**
     * Where the .env file was found, for a startup diagnostic.
     *
     * @return the path as a string, or null when no file was loaded
     */
    public static String getLoadedFrom() {
        return loadedFrom;
    }

    private static String loadedFrom;

    /**
     * Finds and parses the nearest .env file.
     *
     * Failure is deliberately silent: a missing .env is the normal case for
     * anyone using real environment variables, and an unreadable one must not
     * stop the server from starting when the environment already has
     * everything it needs.
     */
    private static Map<String, String> load() {
        Path directory = Paths.get("").toAbsolutePath();
        for (int level = 0; level <= MAX_PARENT_LEVELS && directory != null; level++) {
            Path candidate = directory.resolve(".env");
            if (Files.isRegularFile(candidate)) {
                try {
                    Map<String, String> parsed =
                            parse(Files.readAllLines(candidate, StandardCharsets.UTF_8));
                    loadedFrom = candidate.toString();
                    return Collections.unmodifiableMap(parsed);
                } catch (IOException e) {
                    System.err.println("Could not read " + candidate + ": " + e.getMessage());
                    return Collections.emptyMap();
                }
            }
            directory = directory.getParent();
        }
        return Collections.emptyMap();
    }

    /**
     * Parses {@code NAME=value} lines.
     *
     * Blank lines and {@code #} comments are skipped. A value may be wrapped
     * in matching single or double quotes, which are stripped — an API key is
     * often pasted with them. Values are NOT unescaped or interpolated: a
     * secret containing a backslash or a {@code $} must survive verbatim.
     */
    private static Map<String, String> parse(List<String> lines) {
        Map<String, String> values = new HashMap<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            // "export FOO=bar" is a common shape in a hand-written .env.
            if (trimmed.startsWith("export ")) {
                trimmed = trimmed.substring("export ".length()).trim();
            }
            int separator = trimmed.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String name = trimmed.substring(0, separator).trim();
            String value = trimmed.substring(separator + 1).trim();
            if (value.length() >= 2
                    && ((value.startsWith("\"") && value.endsWith("\""))
                     || (value.startsWith("'") && value.endsWith("'")))) {
                value = value.substring(1, value.length() - 1);
            }
            if (!name.isEmpty()) {
                values.put(name, value);
            }
        }
        return values;
    }
}
