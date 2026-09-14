package com.testify.demo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * All demo output, and the coverage bookkeeping that falls out of it.
 *
 * Two audiences share one stream. The presenter needs to see the traffic —
 * which child sent what, and what the server said back — while the graders need
 * a single line per scene saying which requirement was just evidenced and
 * whether it held. So each scene prints its traffic indented as it happens and
 * closes with one tagged, dotted-leader verdict line.
 *
 * Nothing here uses ANSI colour: this runs in a Windows console during a live
 * demo, and a terminal that renders escape codes literally would wreck the
 * legibility this class exists to provide.
 */
public final class DemoLog {

    /** Column the dotted leader runs to before the verdict. */
    private static final int LEADER_WIDTH = 78;

    /** Outcome of a scene, recorded per requirement for the closing table. */
    public enum Outcome { OK, FAIL, SKIPPED }

    /** requirement tag -> scenes that touched it, in order. */
    private static final Map<String, Set<Integer>> COVERAGE = new LinkedHashMap<>();

    /** scene number -> its outcome, for the summary counts. */
    private static final Map<Integer, Outcome> OUTCOMES = new LinkedHashMap<>();

    /** Failures worth repeating at the end, so a live failure is not lost in scrollback. */
    private static final List<String> FAILURES = new ArrayList<>();

    private DemoLog() {
    }

    // ── Framing ───────────────────────────────────────────────────────────

    public static void banner(String host, int port, String mode) {
        rule('=');
        System.out.println("  HSTS DEMO DRIVER");
        rule('=');
        System.out.println("  Server        : " + host + ":" + port);
        System.out.println("  Mode          : " + mode);
        System.out.println();
        System.out.println("  BEFORE YOU RUN THIS: re-run hsts_seed.sql.");
        System.out.println("  The driver creates, edits, approves, sits and grades exams, so it");
        System.out.println("  mutates the database. It is only repeatable from a known seed.");
        rule('=');
        System.out.println();
    }

    public static void sceneHeader(int number, String title) {
        System.out.println();
        System.out.println("---- Scene " + number + " " + dashes(66 - String.valueOf(number).length()));
        System.out.println("     " + title);
    }

    // ── Traffic ───────────────────────────────────────────────────────────

    /** A command going out to a child process. */
    public static void sent(String who, String command) {
        System.out.println("     " + pad(who, 10) + " >  " + command);
    }

    /** A line coming back from a child process. */
    public static void received(String who, String line) {
        System.out.println("     " + pad(who, 10) + " <  " + line);
    }

    /** Narration that is neither a request nor a reply. */
    public static void info(String message) {
        System.out.println("     .          .  " + message);
    }

    /**
     * Something true but not provable from where the driver stands.
     *
     * Used where a requirement's evidence lives only in the database and no
     * endpoint exposes it. Saying so is honest; printing OK would not be.
     */
    public static void note(String message) {
        System.out.println("     NOTE       :  " + message);
    }

    // ── Verdicts ──────────────────────────────────────────────────────────

    public static void ok(int scene, String[] requirements, String title, String detail) {
        verdict(scene, requirements, title, Outcome.OK, detail);
    }

    public static void fail(int scene, String[] requirements, String title, String detail) {
        verdict(scene, requirements, title, Outcome.FAIL, detail);
        FAILURES.add("Scene " + scene + " [" + tags(requirements) + "] " + title + " -- " + detail);
    }

    public static void skipped(int scene, String[] requirements, String title, String detail) {
        verdict(scene, requirements, title, Outcome.SKIPPED, detail);
    }

    private static void verdict(int scene, String[] requirements, String title,
                                Outcome outcome, String detail) {

        for (String requirement : requirements) {
            COVERAGE.computeIfAbsent(requirement, key -> new LinkedHashSet<>()).add(scene);
        }
        OUTCOMES.put(scene, outcome);

        String head = "[REQ " + tags(requirements) + "] " + title + " ";
        StringBuilder line = new StringBuilder(head);
        while (line.length() < LEADER_WIDTH) {
            line.append('.');
        }
        line.append(' ').append(pad(outcome.name(), 8));
        if (detail != null && !detail.isBlank()) {
            line.append(" (").append(detail).append(')');
        }
        System.out.println(line);
    }

    // ── Closing summary ───────────────────────────────────────────────────

    /**
     * The coverage table: every requirement 1-21 with the scenes that evidenced
     * it, or the reason it is not demonstrable over the wire.
     *
     * Requirements that are argued from the code rather than shown are named
     * explicitly. Silently omitting them would read as coverage.
     */
    public static void coverageTable(Map<Integer, String> notDemonstrable) {

        System.out.println();
        rule('=');
        System.out.println("  COVERAGE");
        rule('=');

        for (int requirement = 1; requirement <= 21; requirement++) {

            Set<Integer> scenes = new LinkedHashSet<>();
            for (Map.Entry<String, Set<Integer>> entry : COVERAGE.entrySet()) {
                if (topLevel(entry.getKey()) == requirement) {
                    scenes.addAll(entry.getValue());
                }
            }

            String evidence;
            if (!scenes.isEmpty()) {
                evidence = "scenes " + join(scenes);
            } else if (notDemonstrable.containsKey(requirement)) {
                evidence = notDemonstrable.get(requirement);
            } else {
                evidence = "NOT COVERED";
            }

            System.out.println("  REQ " + pad(String.valueOf(requirement), 4) + evidence);
        }

        int ok = count(Outcome.OK);
        int failed = count(Outcome.FAIL);
        int skipped = count(Outcome.SKIPPED);

        rule('-');
        System.out.println("  Scenes: " + OUTCOMES.size()
                + "   OK: " + ok + "   FAILED: " + failed + "   SKIPPED: " + skipped);

        if (!FAILURES.isEmpty()) {
            rule('-');
            System.out.println("  FAILURES");
            for (String failure : FAILURES) {
                System.out.println("    " + failure);
            }
        }
        rule('=');
    }

    private static int count(Outcome outcome) {
        int total = 0;
        for (Outcome value : OUTCOMES.values()) {
            if (value == outcome) {
                total++;
            }
        }
        return total;
    }

    /** "6.1" -> 6, so sub-requirements roll up into the table's rows. */
    private static int topLevel(String requirement) {
        String head = requirement.contains(".")
                ? requirement.substring(0, requirement.indexOf('.'))
                : requirement;
        try {
            return Integer.parseInt(head.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String tags(String[] requirements) {
        return String.join("][", requirements);
    }

    private static String join(Set<Integer> values) {
        StringBuilder text = new StringBuilder();
        for (Integer value : values) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(value);
        }
        return text.toString();
    }

    private static String pad(String value, int width) {
        StringBuilder padded = new StringBuilder(value == null ? "" : value);
        while (padded.length() < width) {
            padded.append(' ');
        }
        return padded.toString();
    }

    private static String dashes(int count) {
        return "-".repeat(Math.max(0, count));
    }

    private static void rule(char character) {
        System.out.println(String.valueOf(character).repeat(84));
    }
}
