package com.testify.demo;

import com.testify.common.RequestType;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The scripted demo: sequences the scenes, drives the child processes, and
 * narrates the whole thing in one requirement-tagged stream.
 *
 * <pre>
 *   mvn -pl common,client -am compile
 *   java -cp client/target/classes:common/target/classes com.testify.demo.DemoDriver --step
 * </pre>
 *
 * Flags:
 * <ul>
 *   <li>{@code --step} — wait for Enter between scenes (the presenter sets the tempo)</li>
 *   <li>{@code --auto [ms]} — fixed delay between scenes, for recording</li>
 *   <li>{@code --from n} — start at scene n. If a scene fails live, continue from
 *       the next one instead of restarting the run</li>
 *   <li>{@code --only n[,n...]} — just those scenes, for rehearsing one moment</li>
 *   <li>{@code --idle n} — seconds the no-polling scene waits (default 60)</li>
 * </ul>
 *
 * A failed scene prints FAIL with the server's own words and the run continues.
 * A live demo that aborts on one bad step is worse than one that shows a red
 * line and carries on.
 */
public final class DemoDriver {

    private String host = "localhost";
    private int port = 5555;
    private boolean stepMode;
    private long autoDelayMillis = 1500;
    private int fromScene = 1;
    private List<Integer> onlyScenes = new ArrayList<>();
    private int idleSeconds = 60;

    private final Map<String, Child> children = new LinkedHashMap<>();

    // ── State carried between scenes ──────────────────────────────────────
    private int questionId;
    private int duplicateQuestionId;
    private int demoExamId;
    private String demoExamCode = "4821";
    private int submissionId;
    private int botId = 1;
    private int botSourceId;
    private String unenrolledUsername;

    /** Whether student1 currently has the demo exam open on this driver's watch. */
    private boolean studentSitting;

    /** Seeded exam that stays PENDING, so "activate an unapproved exam" has a target. */
    private static final int SEEDED_PENDING_EXAM = 4;

    /** Course 1 (Mathematics): teacher1 and teacher2 teach it, teacher3 does not. */
    private static final int COURSE_MATHS = 1;

    private static final String STUDENT1_NATIONAL_ID = "200000001";

    public static void main(String[] args) {
        DemoDriver driver = new DemoDriver();
        driver.parseArguments(args);
        driver.run();
    }

    // ── Argument parsing ──────────────────────────────────────────────────

    private void parseArguments(String[] args) {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--host" -> host = args[++i];
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--step" -> stepMode = true;
                case "--auto" -> {
                    stepMode = false;
                    if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                        autoDelayMillis = Long.parseLong(args[++i]);
                    }
                }
                case "--from" -> fromScene = Integer.parseInt(args[++i]);
                case "--only" -> {
                    for (String value : args[++i].split(",")) {
                        onlyScenes.add(Integer.parseInt(value.trim()));
                    }
                }
                case "--idle" -> idleSeconds = Integer.parseInt(args[++i]);
                default -> { /* ignore unknown flags rather than abort a live demo */ }
            }
        }
    }

    // ── The run ───────────────────────────────────────────────────────────

    private void run() {

        DemoLog.banner(host, port, stepMode ? "--step (Enter advances)"
                : "--auto (" + autoDelayMillis + "ms between scenes)");

        List<Scene> scenes = buildScenes();

        try {
            for (Scene scene : scenes) {

                if (!selected(scene.getNumber())) {
                    continue;
                }

                DemoLog.sceneHeader(scene.getNumber(), scene.getTitle());
                try {
                    scene.getBody().run(this);
                } catch (Exception e) {
                    DemoLog.fail(scene.getNumber(), scene.getRequirements(), scene.getTitle(),
                            e.getClass().getSimpleName() + ": " + e.getMessage());
                }
                pause();
            }
        } finally {
            shutdownChildren();
            DemoLog.coverageTable(notDemonstrable());
        }
    }

    private boolean selected(int number) {
        if (!onlyScenes.isEmpty()) {
            return onlyScenes.contains(number);
        }
        return number >= fromScene;
    }

    private void pause() {
        try {
            if (stepMode) {
                System.out.print("     [Enter to continue] ");
                System.out.flush();
                new BufferedReader(new InputStreamReader(System.in)).readLine();
            } else {
                Thread.sleep(autoDelayMillis);
            }
        } catch (Exception ignored) {
            Thread.currentThread().interrupt();
        }
    }

    /** Requirements that are argued from the code, not shown over a socket. */
    private Map<Integer, String> notDemonstrable() {
        Map<Integer, String> map = new LinkedHashMap<>();
        map.put(19, "architectural - flexible design, see documentation");
        map.put(20, "architectural - design patterns, see documentation");
        map.put(21, "demonstrated separately in the GUI walkthrough");
        return map;
    }

    // ── Child processes ───────────────────────────────────────────────────

    /**
     * One child JVM, spoken to over its stdin/stdout.
     *
     * stderr is inherited rather than merged: merging it would interleave stack
     * traces into the line protocol the driver parses, and a demo that
     * misreads a crash as a reply is worse than one that shows the crash.
     */
    private static final class Child {
        private final String name;
        private final Process process;
        private final BufferedReader out;
        private final BufferedWriter in;
        private String userId = "?";
        private String role = "?";

        private Child(String name, Process process) {
            this.name = name;
            this.process = process;
            this.out = new BufferedReader(new InputStreamReader(process.getInputStream()));
            this.in = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
        }
    }

    /** A parsed line from a child. */
    static final class Reply {
        private final String status;
        private final String action;
        private final String detail;

        private Reply(String status, String action, String detail) {
            this.status = status;
            this.action = action;
            this.detail = detail;
        }

        boolean isOk() {
            return "OK".equals(status);
        }

        boolean isFail() {
            return "FAIL".equals(status);
        }

        boolean isPush() {
            return "PUSH".equals(status);
        }

        String action() {
            return action;
        }

        String detail() {
            return detail == null ? "" : detail;
        }

        /** Reads a {@code key=value} token, or "" when absent. */
        String field(String key) {
            for (String token : detail().split("\\s+")) {
                if (token.startsWith(key + "=")) {
                    return token.substring(key.length() + 1);
                }
            }
            return "";
        }

        int intField(String key) {
            String value = field(key);
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        /** True when the refusal message contains this text — the RIGHT reason. */
        boolean refusedBecause(String fragment) {
            return isFail() && detail().toLowerCase().contains(fragment.toLowerCase());
        }
    }

    private Child child(String username) throws IOException {
        Child existing = children.get(username);
        if (existing != null) {
            return existing;
        }
        return spawn(username);
    }

    private Child spawn(String username) throws IOException {

        String java = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("java.class.path");

        ProcessBuilder builder = new ProcessBuilder(java, "-cp", classpath,
                "com.testify.demo.DemoClient",
                "--host", host, "--port", String.valueOf(port),
                "--user", username, "--pass", "password123");
        builder.redirectError(ProcessBuilder.Redirect.INHERIT);

        Child spawned = new Child(username, builder.start());
        Reply reply = readProtocolLine(spawned);
        if (reply != null && reply.isOk()) {
            spawned.userId = reply.field("userId");
            spawned.role = reply.field("role");
            children.put(username, spawned);
            return spawned;
        }

        // A child that could not log in is still useful: the duplicate-session
        // scene depends on exactly this outcome. Do not cache it.
        spawned.process.destroy();
        throw new IOException(reply == null ? "child produced no output" : reply.detail());
    }

    /**
     * Spawns a child that is EXPECTED to be refused, and returns the refusal.
     *
     * @return the child's failure line, or null if it unexpectedly logged in
     */
    private Reply spawnExpectingRefusal(String username) throws IOException {
        try {
            Child unexpected = spawn(username);
            children.remove(username);
            send(unexpected, "QUIT");
            return null;
        } catch (IOException refused) {
            DemoLog.received(username + "#2", "FAIL LOGIN " + refused.getMessage());
            return new Reply("FAIL", RequestType.LOGIN, refused.getMessage());
        }
    }

    /** Sends a command and returns the child's single-line answer. */
    private Reply send(Child child, String command) throws IOException {
        DemoLog.sent(child.name, command);
        child.in.write(command);
        child.in.newLine();
        child.in.flush();

        if (command.equals("QUIT")) {
            return null;
        }

        return readProtocolLine(child);
    }

    private Reply cmd(String username, String command) throws IOException {
        return send(child(username), command);
    }

    /**
     * Reads the child's next PROTOCOL line, stepping over anything else.
     *
     * A child shares its stdout with production code: ClientController narrates
     * "Connected successfully to the HSTS server." and its counterpart on
     * disconnect. Those lines are useful to see and fatal to parse — the first
     * one was being read as the reply to startup and taken as a failed login.
     * So anything that is not OK/FAIL/PUSH is shown as narration and skipped.
     *
     * @return the parsed line, or null if the child produced nothing more
     */
    private Reply readProtocolLine(Child child) throws IOException {
        while (true) {
            String line = child.out.readLine();
            if (line == null) {
                DemoLog.received(child.name, "(no output -- child died)");
                return null;
            }
            String trimmed = line.trim();
            if (trimmed.startsWith("OK ") || trimmed.startsWith("FAIL ")
                    || trimmed.startsWith("PUSH ")) {
                DemoLog.received(child.name, trimmed);
                return parse(trimmed);
            }
            if (!trimmed.isEmpty()) {
                DemoLog.info(child.name + " | " + trimmed);
            }
        }
    }

    private static Reply parse(String line) {
        if (line == null) {
            return null;
        }
        String[] parts = line.trim().split("\\s+", 3);
        if (parts.length < 2) {
            return new Reply(parts.length > 0 ? parts[0] : "?", "?", "");
        }
        return new Reply(parts[0], parts[1], parts.length > 2 ? parts[2] : "");
    }

    private void shutdownChildren() {
        for (Child child : children.values()) {
            try {
                send(child, "QUIT");
                child.process.waitFor();
            } catch (Exception ignored) {
                child.process.destroy();
            }
        }
        children.clear();
    }

    // ── Assertion helpers ─────────────────────────────────────────────────

    private void ok(Scene scene, String detail) {
        DemoLog.ok(scene.getNumber(), scene.getRequirements(), scene.getTitle(), detail);
    }

    private void fail(Scene scene, String detail) {
        DemoLog.fail(scene.getNumber(), scene.getRequirements(), scene.getTitle(), detail);
    }

    private void skip(Scene scene, String detail) {
        DemoLog.skipped(scene.getNumber(), scene.getRequirements(), scene.getTitle(), detail);
    }

    /** Verdict from a boolean, with the same message either way. */
    private void assertThat(Scene scene, boolean condition, String detail) {
        if (condition) {
            ok(scene, detail);
        } else {
            fail(scene, detail);
        }
    }

    // ── The scenes ────────────────────────────────────────────────────────

    private List<Scene> buildScenes() {

        List<Scene> scenes = new ArrayList<>();

        scenes.add(new Scene(1, new String[]{"15", "16"},
                "Three clients connect and log in concurrently, each in its own JVM",
                driver -> {
                    Scene scene = scenes.get(0);
                    Child teacher = child("teacher1");
                    Child student = child("student1");
                    Child admin = child("admin1");
                    DemoLog.info("Three separate JVMs, three sockets, one server. "
                            + "Any of these can be started on another laptop with --host.");
                    assertThat(scene, teacher != null && student != null && admin != null,
                            "live sessions=" + children.size()
                                    + " roles=" + teacher.role + "," + student.role + "," + admin.role);
                }));

        scenes.add(new Scene(2, new String[]{"16"},
                "A fourth client tries to log in as student1 -- one session per account",
                driver -> {
                    Scene scene = scenes.get(1);

                    // Rehearsing this scene on its own must still mean something:
                    // without a first session already holding the account, the
                    // "second" login is simply the first and is rightly accepted.
                    child("student1");

                    Reply refusal = spawnExpectingRefusal("student1");
                    if (refusal == null) {
                        fail(scene, "the second student1 login was ACCEPTED");
                        return;
                    }
                    assertThat(scene, refusal.refusedBecause("already logged in"),
                            "refused: " + refusal.detail());
                }));

        scenes.add(new Scene(3, new String[]{"1"},
                "Each session reports its role, which decides the menu the GUI shows",
                driver -> {
                    Scene scene = scenes.get(2);
                    StringBuilder detail = new StringBuilder();
                    for (Child child : children.values()) {
                        DemoLog.info(child.name + " -> role " + child.role
                                + " -> GUI would show the " + dashboardFor(child.role));
                        if (detail.length() > 0) {
                            detail.append(", ");
                        }
                        detail.append(child.name).append('=').append(child.role);
                    }
                    DemoLog.note("The menu itself is a UI concern, shown in the GUI walkthrough.");
                    ok(scene, detail.toString());
                }));

        scenes.add(new Scene(4, new String[]{"2.1"},
                "teacher1 creates a question",
                driver -> {
                    Scene scene = scenes.get(3);
                    Reply reply = cmd("teacher1", "CREATE_QUESTION Demo question: what is 6 x 7?");
                    questionId = reply.intField("questionId");
                    assertThat(scene, reply.isOk() && questionId > 0,
                            reply.isOk() ? "questionId=" + questionId : reply.detail());
                }));

        scenes.add(new Scene(5, new String[]{"2.2"},
                "teacher1 edits it, and the PREVIOUS version is still retrievable",
                driver -> {
                    Scene scene = scenes.get(4);
                    if (questionId == 0) {
                        skip(scene, "no question from scene 4 -- run from scene 4");
                        return;
                    }
                    Reply edit = cmd("teacher1",
                            "UPDATE_QUESTION " + questionId + " Demo question, revised: what is 8 x 7?");
                    Reply history = cmd("teacher1", "QUESTION_HISTORY " + questionId);
                    int versions = history.intField("versions");
                    DemoLog.info("Editing archives the pre-edit row before overwriting it. "
                            + "The archived copy is the requirement's real test.");
                    assertThat(scene, edit.isOk() && history.isOk() && versions >= 1,
                            "archived versions=" + versions
                                    + " newest=" + history.field("newestArchived"));
                }));

        scenes.add(new Scene(6, new String[]{"2.3"},
                "teacher1 duplicates a question -- new id, fresh question code",
                driver -> {
                    Scene scene = scenes.get(5);
                    if (questionId == 0) {
                        skip(scene, "no question from scene 4");
                        return;
                    }
                    Reply reply = cmd("teacher1", "DUPLICATE_QUESTION " + questionId);
                    duplicateQuestionId = reply.intField("questionId");
                    assertThat(scene, reply.isOk() && duplicateQuestionId != questionId
                                    && duplicateQuestionId > 0,
                            "original=" + questionId + " copy=" + duplicateQuestionId);
                }));

        scenes.add(new Scene(7, new String[]{"2.4"},
                "teacher1 deletes the duplicate",
                driver -> {
                    Scene scene = scenes.get(6);
                    if (duplicateQuestionId == 0) {
                        skip(scene, "no duplicate from scene 6");
                        return;
                    }
                    Reply reply = cmd("teacher1", "DELETE_QUESTION " + duplicateQuestionId);
                    assertThat(scene, reply.isOk(),
                            reply.isOk() ? "deleted questionId=" + duplicateQuestionId
                                    : reply.detail());
                }));

        scenes.add(new Scene(8, new String[]{"3"},
                "teacher1 builds an exam -- 90 points is REFUSED, 100 points is accepted",
                driver -> {
                    Scene scene = scenes.get(7);
                    cmd("teacher1", "GET_ALL_QUESTIONS");

                    Reply short90 = cmd("teacher1", "CREATE_EXAM 90");
                    Reply valid = cmd("teacher1", "CREATE_EXAM 100");
                    demoExamId = valid.intField("examId");

                    DemoLog.note("The 90-point refusal came from ClientController before "
                            + "anything reached the socket. The server enforces the same rule "
                            + "on UPDATE_EXAM, which scene 9 exercises.");
                    assertThat(scene, short90.isFail() && valid.isOk() && demoExamId > 0,
                            "90pt refused: " + short90.detail()
                                    + " | 100pt accepted: examId=" + demoExamId);
                }));

        scenes.add(new Scene(9, new String[]{"3.5"},
                "teacher1 edits the exam -- approval falls back to PENDING",
                driver -> {
                    Scene scene = scenes.get(8);
                    if (!ensureDemoExam()) {
                        skip(scene, "no demo exam found -- run from scene 8");
                        return;
                    }
                    Reply reply = cmd("teacher1", "UPDATE_EXAM " + demoExamId);
                    DemoLog.note("The superseded header is archived to tests_history in the "
                            + "same transaction. No endpoint exposes that table, so it is "
                            + "verified in the database, not here.");
                    assertThat(scene, reply.isOk() && "PENDING".equals(reply.field("approval")),
                            "approval=" + reply.field("approval")
                                    + " duration=" + reply.field("duration"));
                }));

        scenes.add(new Scene(10, new String[]{"4.1"},
                "student1 lists available exams -- the unapproved one is NOT among them",
                driver -> {
                    Scene scene = scenes.get(9);
                    Reply reply = cmd("student1", "GET_AVAILABLE_EXAMS");
                    boolean absent = !containsId(reply.field("ids"), demoExamId);
                    assertThat(scene, reply.isOk() && absent,
                            "visible=" + reply.field("ids") + " demoExam=" + demoExamId
                                    + " hidden=" + absent);
                }));

        scenes.add(new Scene(11, new String[]{"4.2"},
                "admin1 rejects with a reason -- a blank reason is refused, teacher1 sees the reason",
                driver -> {
                    Scene scene = scenes.get(10);
                    if (!ensureDemoExam()) {
                        skip(scene, "no demo exam found");
                        return;
                    }
                    Reply blank = cmd("admin1", "REJECT_EXAM " + demoExamId + " BLANK");
                    Reply rejected = cmd("admin1", "REJECT_EXAM " + demoExamId
                            + " Question_3_duplicates_the_mid-term._Please_revise.");
                    Reply teacherView = cmd("teacher1", "GET_TEACHER_EXAMS");

                    boolean reasonVisible = teacherView.detail().contains("rejectedExam=" + demoExamId);
                    assertThat(scene, blank.isFail() && rejected.isOk() && reasonVisible,
                            "blank refused: " + blank.detail()
                                    + " | reason reached the teacher: " + reasonVisible);
                }));

        scenes.add(new Scene(12, new String[]{"4.1"},
                "admin1 approves the exam teacher1 built",
                driver -> {
                    Scene scene = scenes.get(11);
                    if (!ensureDemoExam()) {
                        skip(scene, "no demo exam found");
                        return;
                    }
                    Reply reply = cmd("admin1", "APPROVE_EXAM " + demoExamId);
                    assertThat(scene, reply.isOk() && "APPROVED".equals(reply.field("approval")),
                            "examId=" + demoExamId + " approval=" + reply.field("approval"));
                }));

        scenes.add(new Scene(13, new String[]{"5.1"},
                "Activating an unapproved exam is REFUSED; the approved one activates",
                driver -> {
                    Scene scene = scenes.get(12);
                    Reply refused = cmd("teacher1", "TOGGLE_EXAM " + SEEDED_PENDING_EXAM);
                    boolean rightReason = refused.refusedBecause("approved");

                    // handleCreateExam inserts a new exam with is_active = true
                    // even while it is PENDING (students still cannot see it --
                    // the availability query demands APPROVED too). Toggling
                    // blindly would therefore switch this exam OFF, so read the
                    // flag and only toggle when it is genuinely inactive.
                    boolean active = demoExamIsActive();
                    if (!active) {
                        Reply toggled = cmd("teacher1", "TOGGLE_EXAM " + demoExamId);
                        active = toggled.isOk() && "true".equals(toggled.field("active"));
                    } else {
                        DemoLog.info("The approved exam is already active -- a new exam is "
                                + "created active, and only approval was holding it back.");
                    }

                    assertThat(scene, rightReason && active,
                            "pending exam " + SEEDED_PENDING_EXAM + " refused: " + refused.detail()
                                    + " | approved exam active=" + active);
                }));

        scenes.add(new Scene(14, new String[]{"5.2", "5.3"},
                "teacher1 schedules a window and a 4-digit code -- 3 digits is refused",
                driver -> {
                    Scene scene = scenes.get(13);
                    if (!ensureDemoExam()) {
                        skip(scene, "no demo exam found");
                        return;
                    }
                    Reply threeDigits = cmd("teacher1", "SCHEDULE_EXAM " + demoExamId + " 482");
                    Reply scheduled = cmd("teacher1",
                            "SCHEDULE_EXAM " + demoExamId + " " + demoExamCode);
                    assertThat(scene, threeDigits.isFail() && scheduled.isOk(),
                            "3-digit refused: " + threeDigits.detail()
                                    + " | scheduled code=" + scheduled.field("code"));
                }));

        scenes.add(new Scene(15, new String[]{"6.1"},
                "student1 enters the WRONG exam code -- refused",
                driver -> {
                    Scene scene = scenes.get(14);
                    Reply reply = cmd("student1", "START_EXAM 9999 " + STUDENT1_NATIONAL_ID);
                    assertThat(scene, reply.refusedBecause("No exam found"),
                            "refused: " + reply.detail());
                }));

        scenes.add(new Scene(16, new String[]{"6.2"},
                "Right code, WRONG national ID -- refused",
                driver -> {
                    Scene scene = scenes.get(15);
                    Reply reply = cmd("student1", "START_EXAM " + demoExamCode + " 123456789");
                    // The REASON matters. Every gate check refuses, so a scene that
                    // only asked "was it refused" would pass on a closed window or
                    // an inactive exam and prove nothing about the ID at all.
                    assertThat(scene, reply.refusedBecause("does not match"),
                            "refused: " + reply.detail());
                }));

        scenes.add(new Scene(17, new String[]{"6.1", "6.2"},
                "Correct code AND national ID -- the exam opens, carrying its questions",
                driver -> {
                    Scene scene = scenes.get(16);
                    Reply reply = cmd("student1",
                            "START_EXAM " + demoExamCode + " " + STUDENT1_NATIONAL_ID);
                    int questions = reply.intField("questions");
                    studentSitting = reply.isOk();
                    assertThat(scene, reply.isOk() && questions > 0,
                            reply.isOk() ? "examId=" + reply.field("examId")
                                    + " questions=" + questions
                                    + " duration=" + reply.field("duration")
                                    : reply.detail());
                }));

        scenes.add(new Scene(18, new String[]{"14"},
                "The learning bot is locked out mid-exam -- and STILL locked after a reconnect",
                driver -> {
                    Scene scene = scenes.get(17);
                    ensureStudentSitting();
                    Reply inExam = cmd("student1", "ASK_BOT " + botId + " What is Pythagoras?");

                    DemoLog.info("Now dropping student1's socket and logging in again: "
                            + "the in-memory registry is gone, so only the durable check remains.");
                    Reply reconnected = cmd("student1", "RECONNECT student1 password123");
                    if (!reconnected.isOk()) {
                        // Without a live session the next refusal would be "you are
                        // not logged in", which says nothing about the exam lockout.
                        fail(scene, "reconnect did not re-establish a session: "
                                + reconnected.detail());
                        return;
                    }
                    Reply afterReconnect = cmd("student1",
                            "ASK_BOT " + botId + " What is Pythagoras?");

                    boolean first = inExam.refusedBecause("while you are taking an exam");
                    boolean second = afterReconnect.refusedBecause("while you are taking an exam");

                    // The durable check is now proven. But the reconnect also cost
                    // student1 its place in the server's push registry, which is
                    // socket-scoped -- so re-enter the exam with the code, which
                    // re-registers the new socket and reuses the same PENDING
                    // submission row rather than opening a second one. Without
                    // this the extension scene could not reach this student, for
                    // reasons that have nothing to do with requirement 7.
                    cmd("student1", "START_EXAM " + demoExamCode + " " + STUDENT1_NATIONAL_ID);
                    DemoLog.info("Re-entered the exam: the push registry is socket-scoped, "
                            + "so the new socket has to announce itself again.");

                    assertThat(scene, first && second,
                            "in exam: " + inExam.detail()
                                    + " | after reconnect: " + afterReconnect.detail());
                }));

        scenes.add(new Scene(19, new String[]{"7"},
                "teacher1 adds 5 minutes -- student1 receives a message it never asked for",
                driver -> {
                    Scene scene = scenes.get(18);
                    if (!ensureDemoExam()) {
                        skip(scene, "no demo exam found");
                        return;
                    }
                    // The push only reaches a client the server has on its watch
                    // list for this exam, and that list is keyed on the socket
                    // that entered. A run resumed at this scene has no such
                    // socket yet.
                    ensureStudentSitting();
                    Reply extended = cmd("teacher1", "EXTEND_EXAM " + demoExamId + " 5");
                    Reply push = cmd("student1", "AWAIT_PUSH "
                            + RequestType.EXAM_DURATION_EXTENDED + " 15");

                    DemoLog.note("This is the ONLY server-initiated message in the system. "
                            + "Everything else is strict request-response.");
                    assertThat(scene, extended.isOk() && push != null && push.isPush(),
                            "teacher side: duration=" + extended.field("duration")
                                    + " | student side: unsolicited "
                                    + (push == null ? "none" : push.action()));
                }));

        scenes.add(new Scene(20, new String[]{"6.3"},
                "student1 submits answers",
                driver -> {
                    Scene scene = scenes.get(19);
                    ensureStudentSitting();
                    Reply reply = cmd("student1", "SUBMIT_EXAM");
                    studentSitting = studentSitting && !reply.isOk();
                    assertThat(scene, reply.isOk(),
                            reply.isOk() ? "answers=" + reply.field("answers")
                                    + " status=" + reply.field("status") : reply.detail());
                }));

        scenes.add(new Scene(21, new String[]{"8.1"},
                "A score was computed automatically, and waits in the teacher's queue",
                driver -> {
                    Scene scene = scenes.get(20);
                    Reply reply = cmd("teacher1", "GET_PENDING_GRADES");
                    submissionId = firstSubmissionId(reply.detail());
                    boolean scored = reply.detail().contains(":score=")
                            && !reply.detail().contains(":score=0.0 ");
                    assertThat(scene, reply.isOk() && submissionId > 0,
                            "pending=" + reply.field("pending")
                                    + " submissionId=" + submissionId
                                    + " computedScore=" + scored);
                }));

        scenes.add(new Scene(22, new String[]{"8.2"},
                "The grade is invisible to the student until the teacher approves it",
                driver -> {
                    Scene scene = scenes.get(21);
                    if (submissionId == 0) {
                        skip(scene, "no pending submission from scene 21");
                        return;
                    }
                    Reply before = cmd("student1", "GET_RESULTS");
                    boolean hiddenBefore = !containsId(before.field("submissionIds"), submissionId);

                    Reply approved = cmd("teacher1", "APPROVE_GRADE " + submissionId);

                    Reply after = cmd("student1", "GET_RESULTS");
                    boolean visibleAfter = containsId(after.field("submissionIds"), submissionId);

                    assertThat(scene, hiddenBefore && approved.isOk() && visibleAfter,
                            "before approval hidden=" + hiddenBefore
                                    + " | after approval visible=" + visibleAfter);
                }));

        scenes.add(new Scene(23, new String[]{"8.3"},
                "A teacher changes a computed grade -- a blank justification is refused",
                driver -> {
                    Scene scene = scenes.get(22);

                    // Not teacher1's submission from scene 21: approving it in
                    // scene 22 moved it to GRADED, and an override is only
                    // offered on a grade still awaiting approval. The seed keeps
                    // one pending grade on teacher3's Physics exam for exactly
                    // this, so the override runs against a real queued grade
                    // rather than one manufactured for the demo.
                    Reply queue = cmd("teacher3", "GET_PENDING_GRADES");
                    int pendingId = firstSubmissionId(queue.detail());
                    if (pendingId == 0) {
                        skip(scene, "no grade awaiting approval -- re-run hsts_seed.sql");
                        return;
                    }
                    DemoLog.info("Overriding submission " + pendingId
                            + ", still awaiting approval on teacher3's exam.");

                    Reply blank = cmd("teacher3", "OVERRIDE_GRADE " + pendingId + " 88 BLANK");
                    Reply changed = cmd("teacher3", "OVERRIDE_GRADE " + pendingId
                            + " 88 Question_4_was_ambiguous_so_the_mark_is_raised.");

                    DemoLog.note("original_score keeps the computed grade alongside the new one. "
                            + "No endpoint returns that column, so it is verified in the "
                            + "database rather than claimed here.");
                    assertThat(scene, blank.isFail() && changed.isOk(),
                            "blank refused: " + blank.detail()
                                    + " | change: " + (changed.isOk()
                                    ? "new score=" + changed.field("score")
                                    + " status=" + changed.field("status")
                                    : changed.detail()));
                }));

        scenes.add(new Scene(24, new String[]{"9.1"},
                "student1 lists her own grades",
                driver -> {
                    Scene scene = scenes.get(23);
                    Reply reply = cmd("student1", "GET_RESULTS");
                    assertThat(scene, reply.isOk() && reply.intField("results") > 0,
                            "results=" + reply.field("results"));
                }));

        scenes.add(new Scene(25, new String[]{"9.2"},
                "student1 opens the marked script -- her answers AND the correct ones",
                driver -> {
                    Scene scene = scenes.get(24);
                    if (!ensureSubmissionId()) {
                        skip(scene, "student1 has no approved submission to open");
                        return;
                    }
                    Reply reply = cmd("student1", "GET_REVIEW " + submissionId);
                    assertThat(scene, reply.isOk()
                                    && reply.intField("studentAnswers") > 0
                                    && reply.intField("correctAnswers") > 0,
                            reply.isOk() ? "questions=" + reply.field("questions")
                                    + " herAnswers=" + reply.field("studentAnswers")
                                    + " correctAnswers=" + reply.field("correctAnswers")
                                    + " score=" + reply.field("score") : reply.detail());
                }));

        scenes.add(new Scene(26, new String[]{"9"},
                "student2 asks for student1's marked script -- refused",
                driver -> {
                    Scene scene = scenes.get(25);
                    if (!ensureSubmissionId()) {
                        skip(scene, "no submission of student1's to ask for");
                        return;
                    }
                    Reply reply = cmd("student2", "GET_REVIEW " + submissionId);
                    assertThat(scene, reply.isFail(),
                            "refused: " + reply.detail());
                }));

        scenes.add(new Scene(27, new String[]{"10"},
                "teacher1 sees the class results, then narrows them to one grade level",
                driver -> {
                    Scene scene = scenes.get(26);
                    if (!ensureDemoExam()) {
                        skip(scene, "no demo exam found");
                        return;
                    }
                    Reply all = cmd("teacher1", "EXAM_RESULTS " + demoExamId + " ALL");
                    Reply filtered = cmd("teacher1", "EXAM_RESULTS " + demoExamId + " 9");
                    boolean subset = filtered.intField("rows") <= all.intField("rows");
                    assertThat(scene, all.isOk() && filtered.isOk() && subset,
                            "all=" + all.field("rows") + " grade9=" + filtered.field("rows")
                                    + " subset=" + subset);
                }));

        scenes.add(new Scene(28, new String[]{"11"},
                "admin1 sees every exam, every approved result and the whole question bank",
                driver -> {
                    Scene scene = scenes.get(27);
                    Reply exams = cmd("admin1", "GET_ALL_EXAMS");
                    Reply results = cmd("admin1", "GET_ALL_RESULTS");
                    Reply questions = cmd("admin1", "GET_ALL_QUESTIONS");
                    assertThat(scene, exams.isOk() && results.isOk() && questions.isOk(),
                            "exams=" + exams.field("exams")
                                    + " results=" + results.field("results")
                                    + " questions=" + questions.field("questions"));
                }));

        scenes.add(new Scene(29, new String[]{"12"},
                "admin1 pulls the distribution and the three comparisons",
                driver -> {
                    Scene scene = scenes.get(28);
                    Reply distribution = cmd("admin1", "GRADE_DISTRIBUTION ALL");
                    Reply byTeacher = cmd("admin1", "TEACHER_STATS 1");
                    Reply byCourse = cmd("admin1", "COURSE_STATS " + COURSE_MATHS);
                    Reply byStudent = cmd("admin1", "STUDENT_STATS 7");

                    boolean medians = byTeacher.intField("withMedian") > 0
                            && byCourse.intField("withMedian") > 0
                            && byStudent.intField("withMedian") > 0;
                    assertThat(scene, distribution.isOk() && byTeacher.isOk()
                                    && byCourse.isOk() && byStudent.isOk() && medians,
                            "bands=" + distribution.field("bands")
                                    + " scored=" + distribution.field("scored")
                                    + " teacherRows=" + byTeacher.field("rows")
                                    + " courseRows=" + byCourse.field("rows")
                                    + " studentRows=" + byStudent.field("rows")
                                    + " mediansPopulated=" + medians);
                }));

        scenes.add(new Scene(30, new String[]{"13.1"},
                "teacher1 opens the course bot and adds a source",
                driver -> {
                    Scene scene = scenes.get(29);
                    Reply bot = cmd("teacher1", "GET_COURSE_BOT " + COURSE_MATHS);
                    botId = bot.intField("botId") > 0 ? bot.intField("botId") : botId;
                    Reply saved = cmd("teacher1",
                            "SAVE_BOT_SOURCE 0 " + botId + " Pythagoras_(added_live)");
                    botSourceId = saved.intField("sourceId");
                    assertThat(scene, bot.isOk() && saved.isOk() && botSourceId > 0,
                            "botId=" + botId + " newSourceId=" + botSourceId
                                    + " sourcesBefore=" + bot.field("sources"));
                }));

        scenes.add(new Scene(31, new String[]{"13.3"},
                "The OTHER teacher of the course may edit that source -- a third teacher may not",
                driver -> {
                    Scene scene = scenes.get(30);
                    if (botSourceId == 0) {
                        skip(scene, "no source from scene 30");
                        return;
                    }
                    Reply colleague = cmd("teacher2",
                            "SAVE_BOT_SOURCE " + botSourceId + " " + botId + " Edited_by_teacher2");
                    Reply outsider = cmd("teacher3",
                            "SAVE_BOT_SOURCE " + botSourceId + " " + botId + " Edited_by_teacher3");

                    DemoLog.info("Both sent the same source id. The owning bot is resolved from "
                            + "the STORED row, not from the payload, so teacher3 cannot reach "
                            + "another course's material by quoting its id.");
                    assertThat(scene, colleague.isOk() && outsider.isFail(),
                            "teacher2 (same course) updatedBy=" + colleague.field("updatedBy")
                                    + " | teacher3 (other course) refused: " + outsider.detail());
                }));

        scenes.add(new Scene(32, new String[]{"14.1"},
                "An enrolled student gets an answer; a student not on the course does not",
                driver -> {
                    Scene scene = scenes.get(31);
                    Reply enrolled = cmd("student1",
                            "ASK_BOT " + botId + " What is Pythagoras' theorem?");

                    unenrolledUsername = "demo_nobody" + (System.currentTimeMillis() % 10000);
                    Reply created = cmd("admin1", "CREATE_USER " + unenrolledUsername
                            + " STUDENT 9 " + nationalIdFor(unenrolledUsername));
                    DemoLog.note("Every seeded student takes all three courses, so the demo "
                            + "creates an account enrolled in nothing to have an outsider at all.");

                    Reply outsider = created.isOk()
                            ? cmd(unenrolledUsername, "ASK_BOT " + botId + " What is Pythagoras?")
                            : null;

                    boolean refusedForEnrolment = outsider != null
                            && outsider.refusedBecause("not enrolled");
                    assertThat(scene, enrolled.isOk() && refusedForEnrolment,
                            "enrolled: engine=" + enrolled.field("engine")
                                    + " answerChars=" + enrolled.field("answerChars")
                                    + " | outsider refused: "
                                    + (outsider == null ? "account not created" : outsider.detail()));
                }));

        scenes.add(new Scene(33, new String[]{"14.2"},
                "student1 reviews her own bot history",
                driver -> {
                    Scene scene = scenes.get(32);
                    Reply reply = cmd("student1", "MY_BOT_HISTORY " + botId);
                    assertThat(scene, reply.isOk() && reply.intField("entries") > 0,
                            "entries=" + reply.field("entries"));
                }));

        scenes.add(new Scene(34, new String[]{"14.3"},
                "teacher1's view of the same questions carries NO student id",
                driver -> {
                    Scene scene = scenes.get(33);
                    Reply reply = cmd("teacher1", "ANON_BOT_HISTORY " + botId);
                    int identified = reply.intField("rowsCarryingStudentId");
                    DemoLog.info("Checked on the wire, not in the UI: the query never selects "
                            + "student_id, so the identity does not leave the database.");
                    assertThat(scene, reply.isOk() && reply.intField("entries") > 0
                                    && identified == 0,
                            "entries=" + reply.field("entries")
                                    + " rows carrying a student id=" + identified);
                }));

        scenes.add(new Scene(35, new String[]{"18"},
                "All clients sit idle -- and send nothing at all",
                driver -> {
                    Scene scene = scenes.get(34);
                    Map<String, Integer> before = new LinkedHashMap<>();
                    for (String name : new ArrayList<>(children.keySet())) {
                        before.put(name, cmd(name, "REQUEST_COUNT").intField("sent"));
                    }

                    DemoLog.info("Idling " + idleSeconds + "s. No screen refreshes itself, "
                            + "so the only way to show this is that nothing happens.");
                    Thread.sleep(idleSeconds * 1000L);

                    StringBuilder detail = new StringBuilder();
                    boolean quiet = true;
                    for (String name : before.keySet()) {
                        int after = cmd(name, "REQUEST_COUNT").intField("sent");
                        int sent = after - before.get(name);
                        quiet &= sent == 0;
                        if (detail.length() > 0) {
                            detail.append(' ');
                        }
                        detail.append(name).append('=').append(sent);
                    }
                    assertThat(scene, quiet,
                            "requests sent while idle: " + detail);
                }));

        scenes.add(new Scene(36, new String[]{"17"},
                "The prepared test data behind everything you have just seen",
                driver -> {
                    Scene scene = scenes.get(35);
                    Reply users = cmd("admin1", "GET_ALL_USERS");
                    Reply exams = cmd("admin1", "GET_ALL_EXAMS");
                    Reply results = cmd("admin1", "GET_ALL_RESULTS");
                    Reply questions = cmd("admin1", "GET_ALL_QUESTIONS");
                    assertThat(scene, users.isOk() && exams.isOk(),
                            "users=" + users.field("users")
                                    + " exams=" + exams.field("exams")
                                    + " questions=" + questions.field("questions")
                                    + " gradedResults=" + results.field("results"));
                }));

        return scenes;
    }

    // ── Scene helpers ─────────────────────────────────────────────────────

    /**
     * Finds the demo exam when the run started partway through.
     *
     * {@code --from 19} has to work in front of an audience, and every scene
     * after 8 needs the exam id that scene 8 produced. Rather than refuse to
     * resume, ask the teacher for their exams and recognise the one the driver
     * builds by its title.
     */
    private boolean ensureDemoExam() throws IOException {
        if (demoExamId > 0) {
            return true;
        }
        Reply reply = cmd("teacher1", "GET_TEACHER_EXAMS");
        demoExamId = reply.intField("demoExamId");
        if (demoExamId > 0) {
            String code = reply.field("demoExamCode");
            if (!code.isBlank() && !code.equals("null")) {
                demoExamCode = code;
            }
            DemoLog.info("Resuming: found the driver's exam from an earlier scene, id "
                    + demoExamId + ".");
        }
        return demoExamId > 0;
    }

    /**
     * Makes sure student1 actually has the demo exam open.
     *
     * Scenes 18-20 all rest on it, and a run started with {@code --from} joins
     * after the entry gate scene. Re-entering with the code is safe: the server
     * reuses the existing PENDING attempt rather than opening a second one, so
     * this restores the sitting without inventing a new one.
     */
    private void ensureStudentSitting() throws IOException {
        if (studentSitting || !ensureDemoExam()) {
            return;
        }
        Reply entered = cmd("student1",
                "START_EXAM " + demoExamCode + " " + STUDENT1_NATIONAL_ID);
        studentSitting = entered.isOk();
        if (studentSitting) {
            DemoLog.info("Resuming: student1 re-entered exam " + demoExamId
                    + " -- the same attempt, not a second one.");
        }
    }

    /**
     * Finds a submission of student1's to open or to be refused, when the run
     * did not include the scene that produced one.
     */
    private boolean ensureSubmissionId() throws IOException {
        if (submissionId > 0) {
            return true;
        }
        Reply results = cmd("student1", "GET_RESULTS");
        String ids = results.field("submissionIds");
        if (ids.isBlank() || ids.equals("none")) {
            return false;
        }
        submissionId = Integer.parseInt(ids.split(",")[0].trim());
        DemoLog.info("Resuming: using student1's submission " + submissionId + ".");
        return true;
    }

    /** Reads the demo exam's current active flag from the teacher's own listing. */
    private boolean demoExamIsActive() throws IOException {
        Reply reply = cmd("teacher1", "GET_TEACHER_EXAMS");
        if (demoExamId == 0) {
            demoExamId = reply.intField("demoExamId");
        }
        return "true".equals(reply.field("demoExamActive"));
    }

    private static String dashboardFor(String role) {
        return switch (role == null ? "" : role.toUpperCase()) {
            case "TEACHER" -> "Teacher Dashboard";
            case "STUDENT" -> "Student Dashboard";
            case "PRINCIPAL" -> "Admin Dashboard";
            default -> "login screen";
        };
    }

    private static boolean containsId(String csv, int id) {
        if (csv == null || csv.isBlank() || csv.equals("none")) {
            return false;
        }
        for (String value : csv.split(",")) {
            if (value.trim().equals(String.valueOf(id))) {
                return true;
            }
        }
        return false;
    }

    /** Pulls the first {@code submission=<id>} out of a pending-grades line. */
    private static int firstSubmissionId(String detail) {
        for (String token : detail.split("\\s+")) {
            if (token.startsWith("submission=")) {
                String[] fields = token.split(":");
                try {
                    return Integer.parseInt(fields[0].substring("submission=".length()));
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        }
        return 0;
    }

    /** A unique 9-digit national ID, so re-runs do not collide on the index. */
    private static String nationalIdFor(String username) {
        long digits = Math.abs((long) username.hashCode()) % 900_000_000L + 100_000_000L;
        return String.valueOf(digits);
    }
}
