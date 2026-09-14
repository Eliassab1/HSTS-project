package com.testify.demo;

import com.testify.common.BotAsk;
import com.testify.common.BotQuestion;
import com.testify.common.BotSource;
import com.testify.common.CourseBot;
import com.testify.common.Exam;
import com.testify.common.ExamEntryRequest;
import com.testify.common.ExamExtension;
import com.testify.common.ExamQuestion;
import com.testify.common.ExamResult;
import com.testify.common.ExamResultsQuery;
import com.testify.common.ExamStatistics;
import com.testify.common.ExamSubmission;
import com.testify.common.GradeDistributionBucket;
import com.testify.common.GradeOverride;
import com.testify.common.PendingGrade;
import com.testify.common.Question;
import com.testify.common.RequestType;
import com.testify.common.Response;
import com.testify.common.ReviewedQuestion;
import com.testify.common.StudentAnswer;
import com.testify.common.StudentExamResult;
import com.testify.common.SubmissionReview;
import com.testify.common.User;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * One demo participant, in its own JVM.
 *
 * Separate processes are not decoration. Requirement 15 asks for several
 * clients served concurrently and requirement 16 for one session per account;
 * both are claims about independent clients, and a single process holding
 * several sessions would assert them rather than demonstrate them. Because each
 * child takes {@code --host} and {@code --port}, any of them can equally be
 * started by hand on a second laptop, which is the same demonstration over a
 * real network.
 *
 * The protocol with the driver is deliberately dull: one command per line in,
 * exactly one line out, in a fixed shape.
 *
 * <pre>
 *   OK   &lt;action&gt; k=v k=v ...
 *   FAIL &lt;action&gt; &lt;server's own words&gt;
 *   PUSH &lt;action&gt; k=v ...      (a server message we never asked for)
 * </pre>
 *
 * Values never contain spaces, so the driver can parse a line without quoting
 * rules. A refusal is reported as FAIL with the server's message verbatim —
 * several scenes prove a requirement by refusal, and paraphrasing the reason
 * would hide whether it was refused for the right one.
 */
public final class DemoClient {

    private final DemoSession session = new DemoSession(15);

    /** The exam most recently opened through the entry gate, for SUBMIT_EXAM. */
    private Exam openExam;

    /** Cached question bank, for building an exam without re-fetching. */
    private List<Question> questionBank = new ArrayList<>();

    public static void main(String[] args) throws Exception {

        String host = argument(args, "--host", "localhost");
        int port = Integer.parseInt(argument(args, "--port", "5555"));
        String username = argument(args, "--user", null);
        String password = argument(args, "--pass", "password123");

        if (username == null) {
            System.out.println("FAIL STARTUP --user is required");
            System.exit(2);
        }

        new DemoClient().run(host, port, username, password);
    }

    private void run(String host, int port, String username, String password) throws Exception {

        try {
            session.connect(host, port);
        } catch (Exception e) {
            emit("FAIL", "CONNECT", e.getMessage());
            System.exit(2);
        }

        Response login = session.login(username, password);
        if (!login.isSuccess()) {
            // The duplicate-login scene depends on this path: a refused login is
            // a result, not a crash, and the driver reads it as evidence.
            emit("FAIL", RequestType.LOGIN, login.getMessage());
            session.disconnect();
            System.exit(3);
        }

        User user = session.getUser();
        emit("OK", "READY", "userId=" + user.getUserId()
                + " role=" + user.getRole()
                + " name=" + safe(user.getFullName()));

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        String line;
        while ((line = in.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.equals("QUIT")) {
                break;
            }
            try {
                execute(line);
            } catch (Exception e) {
                emit("FAIL", "COMMAND", e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }

        session.disconnect();
    }

    // ── Verb dispatch ─────────────────────────────────────────────────────
    //
    // Only the verbs the scenes actually use. Each one sends a request, waits
    // for its stamped reply, and reports the ids the driver needs to carry into
    // a later scene.

    private void execute(String line) {

        String[] parts = line.split("\\s+", 2);
        String verb = parts[0];
        String rest = parts.length > 1 ? parts[1] : "";

        switch (verb) {

            // ── Local, sends nothing ──────────────────────────────────────
            case "REQUEST_COUNT" ->
                    emit("OK", "REQUEST_COUNT", "sent=" + session.getRequestsSent());

            case "RECONNECT" -> reconnect(rest);

            case "AWAIT_PUSH" -> awaitPush(rest);

            // ── Questions ─────────────────────────────────────────────────
            case "CREATE_QUESTION" -> createQuestion(rest);
            case "UPDATE_QUESTION" -> updateQuestion(rest);
            case "QUESTION_HISTORY" -> questionHistory(rest);
            case "DUPLICATE_QUESTION" -> duplicateQuestion(rest);
            case "DELETE_QUESTION" -> deleteQuestion(rest);
            case "GET_ALL_QUESTIONS" -> allQuestions();

            // ── Exams, teacher side ───────────────────────────────────────
            case "CREATE_EXAM" -> createExam(rest);
            case "UPDATE_EXAM" -> updateExam(rest);
            case "GET_TEACHER_EXAMS" -> teacherExams();
            case "TOGGLE_EXAM" -> toggleExam(rest);
            case "SCHEDULE_EXAM" -> scheduleExam(rest);
            case "EXTEND_EXAM" -> extendExam(rest);
            case "EXAM_RESULTS" -> examResults(rest);

            // ── Exams, principal side ─────────────────────────────────────
            case "GET_PENDING_EXAMS" -> pendingExams();
            case "APPROVE_EXAM" -> approveExam(rest);
            case "REJECT_EXAM" -> rejectExam(rest);

            // ── Exams, student side ───────────────────────────────────────
            case "GET_AVAILABLE_EXAMS" -> availableExams();
            case "START_EXAM" -> startExam(rest);
            case "SUBMIT_EXAM" -> submitExam();
            case "GET_RESULTS" -> studentResults();
            case "GET_REVIEW" -> submissionReview(rest);

            // ── Grading ───────────────────────────────────────────────────
            case "GET_PENDING_GRADES" -> pendingGrades();
            case "APPROVE_GRADE" -> approveGrade(rest);
            case "OVERRIDE_GRADE" -> overrideGrade(rest);

            // ── Principal listings and reports ────────────────────────────
            case "GET_ALL_EXAMS" -> allExams();
            case "GET_ALL_RESULTS" -> allResults();
            case "GET_ALL_USERS" -> allUsers();
            case "CREATE_USER" -> createUser(rest);
            case "GRADE_DISTRIBUTION" -> gradeDistribution(rest);
            case "TEACHER_STATS" -> statistics(RequestType.GET_TEACHER_STATISTICS, rest);
            case "COURSE_STATS" -> statistics(RequestType.GET_COURSE_STATISTICS, rest);
            case "STUDENT_STATS" -> statistics(RequestType.GET_STUDENT_STATISTICS, rest);

            // ── Learning bot ──────────────────────────────────────────────
            case "GET_COURSE_BOT" -> courseBot(rest);
            case "SAVE_BOT_SOURCE" -> saveBotSource(rest);
            case "ASK_BOT" -> askBot(rest);
            case "MY_BOT_HISTORY" -> myBotHistory(rest);
            case "ANON_BOT_HISTORY" -> anonBotHistory(rest);
            case "ENGINE_STATUS" -> engineStatus();

            default -> emit("FAIL", "UNKNOWN_VERB", verb);
        }
    }

    // ── Local verbs ───────────────────────────────────────────────────────

    /**
     * Drops the socket and logs in again.
     *
     * The re-login is the point. A reconnect alone leaves the new handler with
     * no session, so anything authorising from it would be refused for want of
     * a login — which would let the exam-lockout scene pass without the durable
     * check ever being consulted.
     */
    private void reconnect(String credentials) {
        String[] parts = credentials.trim().split("\\s+");
        String username = parts[0];
        String password = parts.length > 1 ? parts[1] : "password123";

        try {
            session.reconnect();

            // The server releases the account from its one-session set in the
            // OLD handler's finally block, and that races with this login: the
            // socket is closed from our side before that thread has run. Retry
            // briefly rather than report a failure that resolves itself in a
            // few milliseconds.
            Response login = null;
            for (int attempt = 0; attempt < 20; attempt++) {
                login = session.login(username, password);
                if (login.isSuccess()) {
                    break;
                }
                if (login.getMessage() == null
                        || !login.getMessage().contains("already logged in")) {
                    break;
                }
                Thread.sleep(150);
            }

            if (login == null || !login.isSuccess()) {
                emit("FAIL", "RECONNECT", login == null ? "no response" : login.getMessage());
                return;
            }
            emit("OK", "RECONNECT", "userId=" + session.getUserId() + " session=new");
        } catch (Exception e) {
            emit("FAIL", "RECONNECT", e.getMessage());
        }
    }

    private void awaitPush(String rest) {
        String[] parts = rest.trim().split("\\s+");
        String action = parts[0];
        int seconds = parts.length > 1 ? Integer.parseInt(parts[1]) : 10;

        Response push = session.awaitPush(action, seconds);
        if (push == null) {
            emit("FAIL", action, "no unsolicited " + action + " arrived within " + seconds + "s");
            return;
        }
        emit("PUSH", action, "data=" + String.valueOf(push.getData())
                + " message=" + safe(push.getMessage()));
    }

    // ── Questions ─────────────────────────────────────────────────────────

    private void createQuestion(String rest) {
        String text = rest.isBlank() ? "Demo question: what is 6 x 7?" : rest;
        Question question = new Question();
        question.setQuestionText(text);
        question.setOptionA("40");
        question.setOptionB("42");
        question.setOptionC("44");
        question.setOptionD("46");
        question.setCorrectAnswer("B");
        question.setCourseId(1);
        question.setTeacherId(session.getUserId());
        question.setDifficultyLevel("EASY");
        question.setTopic("Arithmetic");

        Response response = session.send(RequestType.CREATE_QUESTION,
                () -> session.controller().requestCreateQuestion(question));

        if (!response.isSuccess() || !(response.getData() instanceof Question created)) {
            emit("FAIL", RequestType.CREATE_QUESTION, response.getMessage());
            return;
        }
        emit("OK", RequestType.CREATE_QUESTION, "questionId=" + created.getId());
    }

    private void updateQuestion(String rest) {
        String[] parts = rest.trim().split("\\s+", 2);
        int questionId = Integer.parseInt(parts[0]);
        String newText = parts.length > 1 ? parts[1] : "Demo question, revised: what is 8 x 7?";

        Question question = new Question();
        question.setId(questionId);
        question.setQuestionText(newText);
        question.setOptionA("54");
        question.setOptionB("56");
        question.setOptionC("58");
        question.setOptionD("60");
        question.setCorrectAnswer("B");
        question.setCourseId(1);
        question.setTeacherId(session.getUserId());
        question.setDifficultyLevel("MEDIUM");
        question.setTopic("Arithmetic");

        Response response = session.send(RequestType.UPDATE_QUESTION,
                () -> session.controller().requestQuestionUpdate(question));

        if (!response.isSuccess() || !(response.getData() instanceof Question updated)) {
            emit("FAIL", RequestType.UPDATE_QUESTION, response.getMessage());
            return;
        }
        emit("OK", RequestType.UPDATE_QUESTION, "questionId=" + updated.getId()
                + " text=" + safe(updated.getQuestionText()));
    }

    private void questionHistory(String rest) {
        int questionId = Integer.parseInt(rest.trim());
        Response response = session.send(RequestType.GET_QUESTION_HISTORY,
                () -> session.controller().requestQuestionHistory(questionId));

        if (!response.isSuccess()) {
            emit("FAIL", RequestType.GET_QUESTION_HISTORY, response.getMessage());
            return;
        }
        List<Question> versions = typed(response, Question.class);
        StringBuilder detail = new StringBuilder("versions=" + versions.size());
        if (!versions.isEmpty()) {
            detail.append(" newestArchived=").append(safe(versions.get(0).getQuestionText()));
        }
        emit("OK", RequestType.GET_QUESTION_HISTORY, detail.toString());
    }

    private void duplicateQuestion(String rest) {
        int questionId = Integer.parseInt(rest.trim());
        Response response = session.send(RequestType.DUPLICATE_QUESTION,
                () -> session.controller().requestDuplicateQuestion(questionId));

        if (!response.isSuccess() || !(response.getData() instanceof Question copy)) {
            emit("FAIL", RequestType.DUPLICATE_QUESTION, response.getMessage());
            return;
        }
        emit("OK", RequestType.DUPLICATE_QUESTION,
                "questionId=" + copy.getId() + " sourceId=" + questionId);
    }

    private void deleteQuestion(String rest) {
        int questionId = Integer.parseInt(rest.trim());
        Response response = session.send(RequestType.DELETE_QUESTION,
                () -> session.controller().requestDeleteQuestion(questionId));

        if (!response.isSuccess()) {
            emit("FAIL", RequestType.DELETE_QUESTION, response.getMessage());
            return;
        }
        emit("OK", RequestType.DELETE_QUESTION, "questionId=" + questionId);
    }

    private void allQuestions() {
        Response response = session.send(RequestType.GET_ALL_QUESTIONS,
                () -> session.controller().requestAllQuestions());

        if (!response.isSuccess()) {
            emit("FAIL", RequestType.GET_ALL_QUESTIONS, response.getMessage());
            return;
        }
        questionBank = typed(response, Question.class);
        emit("OK", RequestType.GET_ALL_QUESTIONS, "questions=" + questionBank.size());
    }

    // ── Exams: teacher ────────────────────────────────────────────────────

    /**
     * Builds an exam worth exactly the requested total.
     *
     * The 90-point call is expected to be refused. That refusal comes from
     * ClientController before anything reaches the socket, and the driver
     * labels it as such — the rule is enforced on both sides, and a demo should
     * not let a client-side check take credit for the server's.
     */
    private void createExam(String rest) {
        int totalPoints = rest.isBlank() ? 100 : Integer.parseInt(rest.trim());

        if (questionBank.isEmpty()) {
            emit("FAIL", RequestType.CREATE_EXAM, "call GET_ALL_QUESTIONS first");
            return;
        }

        List<ExamQuestion> chosen = new ArrayList<>();
        int perQuestion = 10;
        int count = totalPoints / perQuestion;
        for (Question question : questionBank) {
            if (question.getCourseId() != 1) {
                continue;
            }
            chosen.add(new ExamQuestion(question, perQuestion));
            if (chosen.size() == count) {
                break;
            }
        }

        Exam exam = new Exam();
        exam.setExamId(0);
        exam.setTitle("Demo Exam (driver)");
        exam.setCourse("Mathematics");
        exam.setInstructions("Answer every question. Built live by the demo driver.");
        exam.setDurationMinutes(30);
        exam.setTeacherId(session.getUserId());
        exam.setExamQuestions(chosen);

        Response response = session.send(RequestType.CREATE_EXAM,
                () -> session.controller().requestCreateExam(exam));

        if (!response.isSuccess() || !(response.getData() instanceof Exam created)) {
            emit("FAIL", RequestType.CREATE_EXAM, response.getMessage());
            return;
        }
        emit("OK", RequestType.CREATE_EXAM, "examId=" + created.getExamId()
                + " questions=" + chosen.size() + " points=" + (chosen.size() * perQuestion));
    }

    private void updateExam(String rest) {
        int examId = Integer.parseInt(rest.trim());

        List<ExamQuestion> chosen = new ArrayList<>();
        for (Question question : questionBank) {
            if (question.getCourseId() != 1) {
                continue;
            }
            chosen.add(new ExamQuestion(question, 10));
            if (chosen.size() == 10) {
                break;
            }
        }

        Exam exam = new Exam();
        exam.setExamId(examId);
        exam.setTitle("Demo Exam (driver, revised)");
        exam.setCourse("Mathematics");
        exam.setInstructions("Revised instructions. Answer every question.");
        exam.setDurationMinutes(35);
        exam.setTeacherId(session.getUserId());
        exam.setExamQuestions(chosen);

        Response response = session.send(RequestType.UPDATE_EXAM,
                () -> session.controller().requestUpdateExam(exam));

        if (!response.isSuccess() || !(response.getData() instanceof Exam updated)) {
            emit("FAIL", RequestType.UPDATE_EXAM, response.getMessage());
            return;
        }
        emit("OK", RequestType.UPDATE_EXAM, "examId=" + updated.getExamId()
                + " approval=" + updated.getApprovalStatus()
                + " duration=" + updated.getDurationMinutes());
    }

    private void teacherExams() {
        Response response = session.send(RequestType.GET_TEACHER_EXAMS,
                () -> session.controller().requestTeacherExams(session.getUserId()));

        if (!response.isSuccess()) {
            emit("FAIL", RequestType.GET_TEACHER_EXAMS, response.getMessage());
            return;
        }
        List<Exam> exams = typed(response, Exam.class);
        StringBuilder detail = new StringBuilder("exams=" + exams.size());

        // The driver's own exam is reported by id so that a run started with
        // --from partway through can pick up where the earlier scenes left off
        // instead of refusing to resume.
        Exam demoExam = null;
        for (Exam exam : exams) {
            if (exam.getTitle() != null && exam.getTitle().startsWith("Demo Exam (driver")) {
                demoExam = exam;
            }
            if (exam.getRejectionReason() != null && !exam.getRejectionReason().isBlank()) {
                detail.append(" rejectedExam=").append(exam.getExamId())
                        .append(" reasonLength=").append(exam.getRejectionReason().length());
            }
        }
        if (demoExam != null) {
            detail.append(" demoExamId=").append(demoExam.getExamId())
                    .append(" demoExamCode=").append(safe(demoExam.getExamCode()))
                    .append(" demoExamApproval=").append(safe(demoExam.getApprovalStatus()))
                    .append(" demoExamActive=").append(demoExam.isActive());
        }
        emit("OK", RequestType.GET_TEACHER_EXAMS, detail.toString());
    }

    private void toggleExam(String rest) {
        int examId = Integer.parseInt(rest.trim());
        Response response = session.send(RequestType.TOGGLE_EXAM_STATUS,
                () -> session.controller().requestToggleExamStatus(examId));

        if (!response.isSuccess() || !(response.getData() instanceof Exam exam)) {
            emit("FAIL", RequestType.TOGGLE_EXAM_STATUS, response.getMessage());
            return;
        }
        emit("OK", RequestType.TOGGLE_EXAM_STATUS,
                "examId=" + exam.getExamId() + " active=" + exam.isActive());
    }

    private void scheduleExam(String rest) {
        String[] parts = rest.trim().split("\\s+");
        int examId = Integer.parseInt(parts[0]);
        String code = parts[1];

        Exam exam = new Exam();
        exam.setExamId(examId);
        exam.setExamCode(code);
        exam.setOpenAt(new java.sql.Timestamp(System.currentTimeMillis() - 60_000L));
        exam.setCloseAt(new java.sql.Timestamp(System.currentTimeMillis() + 7_200_000L));

        Response response = session.send(RequestType.SCHEDULE_EXAM,
                () -> session.controller().requestScheduleExam(exam));

        if (!response.isSuccess() || !(response.getData() instanceof Exam scheduled)) {
            emit("FAIL", RequestType.SCHEDULE_EXAM, response.getMessage());
            return;
        }
        emit("OK", RequestType.SCHEDULE_EXAM,
                "examId=" + scheduled.getExamId() + " code=" + scheduled.getExamCode());
    }

    private void extendExam(String rest) {
        String[] parts = rest.trim().split("\\s+");
        int examId = Integer.parseInt(parts[0]);
        int minutes = Integer.parseInt(parts[1]);

        Response response = session.send(RequestType.EXTEND_EXAM_DURATION,
                () -> session.controller().requestExtendExamDuration(
                        new ExamExtension(examId, minutes)));

        if (!response.isSuccess() || !(response.getData() instanceof Exam exam)) {
            emit("FAIL", RequestType.EXTEND_EXAM_DURATION, response.getMessage());
            return;
        }
        emit("OK", RequestType.EXTEND_EXAM_DURATION,
                "examId=" + exam.getExamId() + " duration=" + exam.getDurationMinutes());
    }

    private void examResults(String rest) {
        String[] parts = rest.trim().split("\\s+");
        int examId = Integer.parseInt(parts[0]);
        Integer gradeLevel = parts.length > 1 && !parts[1].equals("ALL")
                ? Integer.valueOf(parts[1]) : null;

        Response response = session.send(RequestType.GET_EXAM_RESULTS,
                () -> session.controller().requestExamResults(
                        new ExamResultsQuery(examId, gradeLevel)));

        if (!response.isSuccess()) {
            emit("FAIL", RequestType.GET_EXAM_RESULTS, response.getMessage());
            return;
        }
        List<StudentExamResult> results = typed(response, StudentExamResult.class);
        StringBuilder grades = new StringBuilder();
        for (StudentExamResult result : results) {
            if (grades.length() > 0) {
                grades.append(',');
            }
            grades.append(result.getGradeLevel());
        }
        emit("OK", RequestType.GET_EXAM_RESULTS, "rows=" + results.size()
                + " gradeLevels=" + (grades.length() == 0 ? "none" : grades));
    }

    // ── Exams: principal ──────────────────────────────────────────────────

    private void pendingExams() {
        Response response = session.send(RequestType.GET_PENDING_EXAMS,
                () -> session.controller().requestPendingExams());

        if (!response.isSuccess()) {
            emit("FAIL", RequestType.GET_PENDING_EXAMS, response.getMessage());
            return;
        }
        List<Exam> exams = typed(response, Exam.class);
        StringBuilder ids = new StringBuilder();
        for (Exam exam : exams) {
            if (ids.length() > 0) {
                ids.append(',');
            }
            ids.append(exam.getExamId());
        }
        emit("OK", RequestType.GET_PENDING_EXAMS,
                "pending=" + exams.size() + " ids=" + (ids.length() == 0 ? "none" : ids));
    }

    private void approveExam(String rest) {
        int examId = Integer.parseInt(rest.trim());
        Response response = session.send(RequestType.APPROVE_EXAM,
                () -> session.controller().requestApproveExam(examId));

        if (!response.isSuccess() || !(response.getData() instanceof Exam exam)) {
            emit("FAIL", RequestType.APPROVE_EXAM, response.getMessage());
            return;
        }
        emit("OK", RequestType.APPROVE_EXAM,
                "examId=" + exam.getExamId() + " approval=" + exam.getApprovalStatus());
    }

    private void rejectExam(String rest) {
        String[] parts = rest.trim().split("\\s+", 2);
        int examId = Integer.parseInt(parts[0]);
        String reason = parts.length > 1 ? parts[1] : "";

        Exam exam = new Exam();
        exam.setExamId(examId);
        exam.setRejectionReason(reason.equals("BLANK") ? "   " : reason);

        Response response = session.send(RequestType.REJECT_EXAM,
                () -> session.controller().requestRejectExam(exam));

        if (!response.isSuccess() || !(response.getData() instanceof Exam rejected)) {
            emit("FAIL", RequestType.REJECT_EXAM, response.getMessage());
            return;
        }
        emit("OK", RequestType.REJECT_EXAM, "examId=" + rejected.getExamId()
                + " approval=" + rejected.getApprovalStatus());
    }

    // ── Exams: student ────────────────────────────────────────────────────

    private void availableExams() {
        Response response = session.send(RequestType.GET_AVAILABLE_EXAMS,
                () -> session.controller().requestAvailableExams(session.getUserId()));

        if (!response.isSuccess()) {
            emit("FAIL", RequestType.GET_AVAILABLE_EXAMS, response.getMessage());
            return;
        }
        List<Exam> exams = typed(response, Exam.class);
        StringBuilder ids = new StringBuilder();
        for (Exam exam : exams) {
            if (ids.length() > 0) {
                ids.append(',');
            }
            ids.append(exam.getExamId());
        }
        emit("OK", RequestType.GET_AVAILABLE_EXAMS,
                "exams=" + exams.size() + " ids=" + (ids.length() == 0 ? "none" : ids));
    }

    private void startExam(String rest) {
        String[] parts = rest.trim().split("\\s+");
        String code = parts[0];
        String nationalId = parts[1];

        Response response = session.send(RequestType.START_EXAM_BY_CODE,
                () -> session.controller().requestStartExamByCode(
                        new ExamEntryRequest(code, nationalId, session.getUserId())));

        if (!response.isSuccess() || !(response.getData() instanceof Exam exam)) {
            emit("FAIL", RequestType.START_EXAM_BY_CODE, response.getMessage());
            return;
        }
        openExam = exam;
        int questions = exam.getExamQuestions() == null ? 0 : exam.getExamQuestions().size();
        emit("OK", RequestType.START_EXAM_BY_CODE, "examId=" + exam.getExamId()
                + " questions=" + questions + " duration=" + exam.getDurationMinutes());
    }

    /**
     * Answers the open exam and submits.
     *
     * Deliberately answers the first seven questions correctly and the rest
     * wrongly, so the computed score is a specific number the next scene can
     * point at rather than "some value appeared".
     */
    private void submitExam() {
        if (openExam == null) {
            emit("FAIL", RequestType.SUBMIT_EXAM, "no exam open -- run START_EXAM first");
            return;
        }

        List<StudentAnswer> answers = new ArrayList<>();
        List<ExamQuestion> questions = openExam.getExamQuestions();
        for (int i = 0; i < questions.size(); i++) {
            Question question = questions.get(i).getQuestion();
            String correct = question.getCorrectAnswer();
            String chosen = i < 7 ? correct : wrongAnswer(correct);
            answers.add(new StudentAnswer(question.getId(), chosen));
        }

        ExamSubmission submission = new ExamSubmission();
        submission.setExamId(openExam.getExamId());
        submission.setStudentId(session.getUserId());
        submission.setAnswers(answers);
        submission.setUsedMinutes(5);

        Response response = session.send(RequestType.SUBMIT_EXAM,
                () -> session.controller().requestSubmitExam(submission));

        if (!response.isSuccess() || !(response.getData() instanceof ExamResult receipt)) {
            emit("FAIL", RequestType.SUBMIT_EXAM, response.getMessage());
            return;
        }
        emit("OK", RequestType.SUBMIT_EXAM, "examId=" + openExam.getExamId()
                + " answers=" + answers.size()
                + " status=" + receipt.getStatus()
                + " gradeShown=" + receipt.getGrade());
        openExam = null;
    }

    private static String wrongAnswer(String correct) {
        return "A".equalsIgnoreCase(correct) ? "B" : "A";
    }

    private void studentResults() {
        Response response = session.send(RequestType.GET_STUDENT_RESULTS,
                () -> session.controller().requestStudentResults(session.getUserId()));

        if (!response.isSuccess()) {
            emit("FAIL", RequestType.GET_STUDENT_RESULTS, response.getMessage());
            return;
        }
        List<ExamResult> results = typed(response, ExamResult.class);
        StringBuilder examIds = new StringBuilder();
        StringBuilder submissionIds = new StringBuilder();
        for (ExamResult result : results) {
            if (examIds.length() > 0) {
                examIds.append(',');
                submissionIds.append(',');
            }
            examIds.append(result.getExamId());
            submissionIds.append(result.getResultId());
        }
        emit("OK", RequestType.GET_STUDENT_RESULTS, "results=" + results.size()
                + " examIds=" + (examIds.length() == 0 ? "none" : examIds)
                + " submissionIds=" + (submissionIds.length() == 0 ? "none" : submissionIds));
    }

    private void submissionReview(String rest) {
        int submissionId = Integer.parseInt(rest.trim());
        Response response = session.send(RequestType.GET_SUBMISSION_REVIEW,
                () -> session.controller().requestSubmissionReview(submissionId));

        if (!response.isSuccess() || !(response.getData() instanceof SubmissionReview review)) {
            emit("FAIL", RequestType.GET_SUBMISSION_REVIEW, response.getMessage());
            return;
        }

        int withStudentAnswer = 0;
        int withCorrectAnswer = 0;
        for (ReviewedQuestion question : review.getQuestions()) {
            if (question.getStudentAnswer() != null) {
                withStudentAnswer++;
            }
            if (question.getCorrectAnswer() != null) {
                withCorrectAnswer++;
            }
        }
        emit("OK", RequestType.GET_SUBMISSION_REVIEW, "submissionId=" + review.getSubmissionId()
                + " questions=" + review.getQuestions().size()
                + " studentAnswers=" + withStudentAnswer
                + " correctAnswers=" + withCorrectAnswer
                + " score=" + review.getScore());
    }

    // ── Grading ───────────────────────────────────────────────────────────

    private void pendingGrades() {
        Response response = session.send(RequestType.GET_PENDING_GRADES,
                () -> session.controller().requestPendingGrades(session.getUserId()));

        if (!response.isSuccess()) {
            emit("FAIL", RequestType.GET_PENDING_GRADES, response.getMessage());
            return;
        }
        List<PendingGrade> grades = typed(response, PendingGrade.class);
        StringBuilder detail = new StringBuilder("pending=" + grades.size());
        for (PendingGrade grade : grades) {
            detail.append(" submission=").append(grade.getSubmissionId())
                    .append(":exam=").append(grade.getExamId())
                    .append(":student=").append(grade.getStudentId())
                    .append(":score=").append(grade.getScore());
        }
        emit("OK", RequestType.GET_PENDING_GRADES, detail.toString());
    }

    private void approveGrade(String rest) {
        int submissionId = Integer.parseInt(rest.trim());
        Response response = session.send(RequestType.APPROVE_GRADE,
                () -> session.controller().requestApproveGrade(submissionId));

        if (!response.isSuccess() || !(response.getData() instanceof PendingGrade grade)) {
            emit("FAIL", RequestType.APPROVE_GRADE, response.getMessage());
            return;
        }
        emit("OK", RequestType.APPROVE_GRADE, "submissionId=" + grade.getSubmissionId()
                + " status=" + grade.getStatus() + " score=" + grade.getScore());
    }

    private void overrideGrade(String rest) {
        String[] parts = rest.trim().split("\\s+", 3);
        int submissionId = Integer.parseInt(parts[0]);
        double newScore = Double.parseDouble(parts[1]);
        String justification = parts.length > 2 ? parts[2] : "";
        if (justification.equals("BLANK")) {
            justification = "   ";
        }

        GradeOverride override = new GradeOverride(submissionId, newScore, justification);
        Response response = session.send(RequestType.OVERRIDE_GRADE,
                () -> session.controller().requestOverrideGrade(override));

        if (!response.isSuccess() || !(response.getData() instanceof PendingGrade grade)) {
            emit("FAIL", RequestType.OVERRIDE_GRADE, response.getMessage());
            return;
        }
        emit("OK", RequestType.OVERRIDE_GRADE, "submissionId=" + grade.getSubmissionId()
                + " status=" + grade.getStatus() + " score=" + grade.getScore());
    }

    // ── Principal listings and reports ────────────────────────────────────

    private void allExams() {
        Response response = session.send(RequestType.GET_ALL_EXAMS,
                () -> session.controller().requestAllExams());
        countOrFail(response, RequestType.GET_ALL_EXAMS, Exam.class, "exams");
    }

    private void allResults() {
        Response response = session.send(RequestType.GET_ALL_RESULTS,
                () -> session.controller().requestAllResults());
        countOrFail(response, RequestType.GET_ALL_RESULTS, ExamResult.class, "results");
    }

    private void allUsers() {
        Response response = session.send(RequestType.GET_ALL_USERS,
                () -> session.controller().requestAllUsers());
        countOrFail(response, RequestType.GET_ALL_USERS, User.class, "users");
    }

    /** Creates an account. Used to obtain a student enrolled in nothing. */
    private void createUser(String rest) {
        String[] parts = rest.trim().split("\\s+");
        String username = parts[0];
        String role = parts.length > 1 ? parts[1] : "STUDENT";
        String grade = parts.length > 2 ? parts[2] : "9";
        String nationalId = parts.length > 3 ? parts[3] : "";

        User user = new User();
        user.setUsername(username);
        user.setFullName("Demo " + username);
        user.setRole(role);
        user.setPassword("password123");
        if ("STUDENT".equals(role)) {
            user.setGradeLevel(Integer.valueOf(grade));
        }
        if (!nationalId.isBlank()) {
            user.setNationalId(nationalId);
        }

        Response response = session.send(RequestType.CREATE_USER,
                () -> session.controller().requestCreateUser(user));

        if (!response.isSuccess() || !(response.getData() instanceof User created)) {
            emit("FAIL", RequestType.CREATE_USER, response.getMessage());
            return;
        }
        emit("OK", RequestType.CREATE_USER,
                "userId=" + created.getUserId() + " username=" + created.getUsername());
    }

    private void gradeDistribution(String rest) {
        Integer examId = rest.isBlank() || rest.trim().equals("ALL")
                ? null : Integer.valueOf(rest.trim());

        Response response = session.send(RequestType.GET_GRADE_DISTRIBUTION,
                () -> session.controller().requestGradeDistribution(examId));

        if (!response.isSuccess()) {
            emit("FAIL", RequestType.GET_GRADE_DISTRIBUTION, response.getMessage());
            return;
        }
        List<GradeDistributionBucket> buckets = typed(response, GradeDistributionBucket.class);
        int total = 0;
        for (GradeDistributionBucket bucket : buckets) {
            total += bucket.getCount();
        }
        emit("OK", RequestType.GET_GRADE_DISTRIBUTION,
                "bands=" + buckets.size() + " scored=" + total);
    }

    private void statistics(String action, String rest) {
        int id = Integer.parseInt(rest.trim());

        Response response = session.send(action, () -> {
            switch (action) {
                case RequestType.GET_TEACHER_STATISTICS ->
                        session.controller().requestTeacherStatistics(id);
                case RequestType.GET_COURSE_STATISTICS ->
                        session.controller().requestCourseStatistics(id);
                default -> session.controller().requestStudentStatistics(id);
            }
        });

        if (!response.isSuccess()) {
            emit("FAIL", action, response.getMessage());
            return;
        }
        List<ExamStatistics> statistics = typed(response, ExamStatistics.class);
        int withMedian = 0;
        for (ExamStatistics row : statistics) {
            if (row.getMedian() > 0) {
                withMedian++;
            }
        }
        emit("OK", action, "rows=" + statistics.size() + " withMedian=" + withMedian);
    }

    // ── Learning bot ──────────────────────────────────────────────────────

    private void courseBot(String rest) {
        int courseId = Integer.parseInt(rest.trim());
        Response response = session.send(RequestType.GET_COURSE_BOT,
                () -> session.controller().requestCourseBot(courseId));

        if (!response.isSuccess() || !(response.getData() instanceof CourseBot bot)) {
            emit("FAIL", RequestType.GET_COURSE_BOT, response.getMessage());
            return;
        }
        int sources = bot.getSources() == null ? 0 : bot.getSources().size();
        int firstSourceId = sources > 0 ? bot.getSources().get(0).getId() : 0;
        emit("OK", RequestType.GET_COURSE_BOT, "botId=" + bot.getId()
                + " courseId=" + bot.getCourseId()
                + " sources=" + sources
                + " firstSourceId=" + firstSourceId);
    }

    /**
     * Adds or edits a source. {@code sourceId} 0 inserts.
     *
     * An edit sends only the source id and the new content: the owning bot is
     * resolved server-side from the stored row, which is what stops a teacher
     * of another course rewriting this one by sending its id.
     */
    private void saveBotSource(String rest) {
        String[] parts = rest.trim().split("\\s+", 3);
        int sourceId = Integer.parseInt(parts[0]);
        int botId = Integer.parseInt(parts[1]);
        String title = parts.length > 2 ? parts[2] : "Demo source";

        BotSource source = new BotSource();
        source.setId(sourceId);
        source.setBotId(botId);
        source.setTitle(title);
        source.setContent("Pythagoras' theorem states that in a right-angled triangle "
                + "the square of the hypotenuse equals the sum of the squares of the "
                + "other two sides. Written a^2 + b^2 = c^2.");

        Response response = session.send(RequestType.SAVE_BOT_SOURCE,
                () -> session.controller().requestSaveBotSource(source));

        if (!response.isSuccess() || !(response.getData() instanceof BotSource saved)) {
            emit("FAIL", RequestType.SAVE_BOT_SOURCE, response.getMessage());
            return;
        }
        emit("OK", RequestType.SAVE_BOT_SOURCE, "sourceId=" + saved.getId()
                + " botId=" + saved.getBotId()
                + " updatedBy=" + saved.getUpdatedBy());
    }

    private void askBot(String rest) {
        String[] parts = rest.trim().split("\\s+", 2);
        int botId = Integer.parseInt(parts[0]);
        String question = parts.length > 1 ? parts[1] : "What is Pythagoras' theorem?";

        Response response = session.send(RequestType.ASK_BOT,
                () -> session.controller().requestAskBot(new BotAsk(botId, question)));

        if (!response.isSuccess() || !(response.getData() instanceof BotQuestion answered)) {
            emit("FAIL", RequestType.ASK_BOT, response.getMessage());
            return;
        }
        String answer = answered.getAnswerText() == null ? "" : answered.getAnswerText();
        emit("OK", RequestType.ASK_BOT, "botId=" + answered.getBotId()
                + " engine=" + answered.getEngineUsed()
                + " answerChars=" + answer.length());
    }

    private void myBotHistory(String rest) {
        int botId = Integer.parseInt(rest.trim());
        Response response = session.send(RequestType.GET_MY_BOT_HISTORY,
                () -> session.controller().requestMyBotHistory(botId));

        if (!response.isSuccess()) {
            emit("FAIL", RequestType.GET_MY_BOT_HISTORY, response.getMessage());
            return;
        }
        List<BotQuestion> history = typed(response, BotQuestion.class);
        emit("OK", RequestType.GET_MY_BOT_HISTORY, "entries=" + history.size());
    }

    /**
     * The teacher's view of the same conversations.
     *
     * Reports how many rows carry a non-zero student id. The requirement is
     * that the identity never leaves the database, so the number that matters
     * is zero — and it is checked on the wire, not in a UI that could simply be
     * hiding a column it was still sent.
     */
    private void anonBotHistory(String rest) {
        int botId = Integer.parseInt(rest.trim());
        Response response = session.send(RequestType.GET_BOT_HISTORY_ANONYMOUS,
                () -> session.controller().requestAnonymousBotHistory(botId));

        if (!response.isSuccess()) {
            emit("FAIL", RequestType.GET_BOT_HISTORY_ANONYMOUS, response.getMessage());
            return;
        }
        List<BotQuestion> history = typed(response, BotQuestion.class);
        int identified = 0;
        for (BotQuestion entry : history) {
            if (entry.getStudentId() != 0) {
                identified++;
            }
        }
        emit("OK", RequestType.GET_BOT_HISTORY_ANONYMOUS,
                "entries=" + history.size() + " rowsCarryingStudentId=" + identified);
    }

    private void engineStatus() {
        Response response = session.send(RequestType.GET_ENGINE_STATUS,
                () -> session.controller().requestEngineStatus());

        if (!response.isSuccess()
                || !(response.getData() instanceof com.testify.common.EngineStatus status)) {
            emit("FAIL", RequestType.GET_ENGINE_STATUS, response.getMessage());
            return;
        }
        emit("OK", RequestType.GET_ENGINE_STATUS, "engine=" + status.getEngineName()
                + " generation=" + status.isGenerationAvailable()
                + " model=" + safe(status.getModel()));
    }

    // ── Plumbing ──────────────────────────────────────────────────────────

    private <T> void countOrFail(Response response, String action, Class<T> type, String label) {
        if (!response.isSuccess()) {
            emit("FAIL", action, response.getMessage());
            return;
        }
        emit("OK", action, label + "=" + typed(response, type).size());
    }

    private static <T> List<T> typed(Response response, Class<T> type) {
        List<T> values = new ArrayList<>();
        if (response.getData() instanceof List<?> list) {
            for (Object item : list) {
                if (type.isInstance(item)) {
                    values.add(type.cast(item));
                }
            }
        }
        return values;
    }

    /** One line out, flushed, because the driver is blocking on it. */
    private static void emit(String status, String action, String detail) {
        System.out.println(status + " " + action + " " + (detail == null ? "" : detail));
        System.out.flush();
    }

    /** Keeps the line parseable: values never contain spaces. */
    private static String safe(String value) {
        if (value == null) {
            return "none";
        }
        String cleaned = value.trim().replaceAll("\\s+", "_");
        return cleaned.isEmpty() ? "none" : cleaned;
    }

    private static String argument(String[] args, String name, String fallback) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(name)) {
                return args[i + 1];
            }
        }
        return fallback;
    }
}
