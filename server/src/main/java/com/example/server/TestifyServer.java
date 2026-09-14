package com.example.server;

import com.testify.common.*;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TestifyServer {

    private final int port;
    private volatile boolean running = false;
    private ServerSocket serverSocket;

    /** IDs of users who are currently logged in. Enforces one-session-per-account. */
    private final Set<Integer> loggedInUserIds =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * Clients currently sitting an exam, keyed by exam ID — the address book
     * for the one message the server sends unprompted (spec 7).
     *
     * Every client thread reads and writes this, and the pushing thread walks
     * a set while other threads may be joining or leaving it, so both the map
     * and the sets are concurrent. A handler joins when START_EXAM_BY_CODE
     * succeeds and leaves on submit, on logout, and when its socket closes.
     */
    private final Map<Integer, Set<ClientHandler>> examWatchers =
            new ConcurrentHashMap<>();

    public TestifyServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(port));
        running = true;

        Thread acceptThread = new Thread(() -> {
            while (running) {
                try {
                    Socket client = serverSocket.accept();
                    new Thread(new ClientHandler(client)).start();
                } catch (IOException e) {
                    if (running) e.printStackTrace();
                }
            }
        });

        acceptThread.start();
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {}
    }

    public void listen() throws IOException {
        start();
        System.out.println("TestifyServer listening on port " + port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down TestifyServer...");
            stop();
        }));
        System.out.println("Server is running. Press Ctrl+C to stop.");
    }

    /**
     * Routes a request with no authenticated session attached. Kept so a
     * caller outside {@code ClientHandler} still compiles; any action that
     * needs to know who is asking will refuse.
     *
     * @param request request to route
     * @return the response for that request
     */
    protected Response handleMessageFromClient(Request request) {
        return handleMessageFromClient(request, -1);
    }

    /**
     * Routes a request to its handler.
     *
     * @param request request to route
     * @param sessionUserId user ID logged in on the socket this request
     *                      arrived on, or -1 when no session is active.
     *                      Authorisation is decided from THIS, never from an
     *                      ID inside the payload, which a client controls.
     * @return the response for that request
     */
    protected Response handleMessageFromClient(Request request, int sessionUserId) {
        String action = request.getAction();
        switch (action) {
            case RequestType.GET_ALL_QUESTIONS:  return handleGetAllQuestions();
            case RequestType.CREATE_QUESTION:    return handleCreateQuestion(request.getData());
            case RequestType.DELETE_QUESTION:    return handleDeleteQuestion(request.getData());
            case RequestType.UPDATE_QUESTION:    return handleUpdateQuestion(request.getData());
            case RequestType.GET_QUESTION_HISTORY:return handleGetQuestionHistory(request.getData());
            case RequestType.DUPLICATE_QUESTION: return handleDuplicateQuestion(request.getData());
            case RequestType.CREATE_EXAM:        return handleCreateExam(request.getData());
            case RequestType.UPDATE_EXAM:        return handleUpdateExam(request.getData());
            case RequestType.GET_EXAM_RESULTS:
                return handleGetExamResults(request.getData(), sessionUserId);
            case RequestType.EXTEND_EXAM_DURATION:
                return handleExtendExamDuration(request.getData(), sessionUserId);
            case RequestType.GET_AVAILABLE_EXAMS:return handleGetAvailableExams(request.getData());
            case RequestType.GET_EXAM_BY_ID:     return handleGetExamById(request.getData());
            case RequestType.SUBMIT_EXAM:        return handleSubmitExam(request.getData());
            case RequestType.GET_STUDENT_RESULTS:return handleGetStudentResults(request.getData());
            case RequestType.GET_TEACHER_EXAMS:  return handleGetTeacherExams(request.getData());
            case RequestType.DELETE_EXAM:         return handleDeleteExam(request.getData());
            case RequestType.TOGGLE_EXAM_STATUS:  return handleToggleExamStatus(request.getData());
            case RequestType.GET_PENDING_EXAMS:   return handleGetPendingExams();
            case RequestType.APPROVE_EXAM:        return handleApproveExam(request.getData());
            case RequestType.REJECT_EXAM:         return handleRejectExam(request.getData());
            case RequestType.GET_ALL_COURSES:     return handleGetAllCourses();
            case RequestType.UPDATE_PROFILE:      return handleUpdateProfile(request.getData());
            case RequestType.GET_USER_SETTINGS:   return handleGetUserSettings(request.getData());
            case RequestType.UPDATE_USER_SETTINGS:return handleUpdateUserSettings(request.getData());
            case RequestType.GET_PASS_FAIL_REPORT:        return handleGetPassFailReport();
            case RequestType.GET_SCORE_TREND_REPORT:      return handleGetScoreTrendReport();
            case RequestType.GET_TEACHER_ACTIVITY_REPORT: return handleGetTeacherActivityReport();
            case RequestType.GET_TEACHER_EXAM_PERFORMANCE:return handleGetTeacherExamPerformance(request.getData());
            case RequestType.SCHEDULE_EXAM:       return handleScheduleExam(request.getData());
            case RequestType.START_EXAM_BY_CODE:  return handleStartExamByCode(request.getData());
            case RequestType.GET_ALL_USERS:       return handleGetAllUsers(sessionUserId);
            case RequestType.CREATE_USER:         return handleCreateUser(request.getData(), sessionUserId);
            case RequestType.UPDATE_USER:         return handleUpdateUser(request.getData(), sessionUserId);
            case RequestType.DELETE_USER:         return handleDeleteUser(request.getData(), sessionUserId);
            case RequestType.GET_ALL_EXAMS:       return handleGetAllExams(sessionUserId);
            case RequestType.GET_ALL_RESULTS:     return handleGetAllResults(sessionUserId);
            case RequestType.GET_GRADE_DISTRIBUTION:
                return handleGetGradeDistribution(request.getData(), sessionUserId);
            case RequestType.GET_TEACHER_STATISTICS:
                return handleGetComparisonStatistics(RequestType.GET_TEACHER_STATISTICS, request.getData(), sessionUserId);
            case RequestType.GET_COURSE_STATISTICS:
                return handleGetComparisonStatistics(RequestType.GET_COURSE_STATISTICS, request.getData(), sessionUserId);
            case RequestType.GET_STUDENT_STATISTICS:
                return handleGetComparisonStatistics(RequestType.GET_STUDENT_STATISTICS, request.getData(), sessionUserId);
            case RequestType.GET_PENDING_GRADES:  return handleGetPendingGrades(sessionUserId);
            case RequestType.APPROVE_GRADE:       return handleApproveGrade(request.getData(), sessionUserId);
            case RequestType.OVERRIDE_GRADE:      return handleOverrideGrade(request.getData(), sessionUserId);
            case RequestType.GET_SUBMISSION_REVIEW:
                return handleGetSubmissionReview(request.getData(), sessionUserId);
            case RequestType.GET_MY_COURSES:
                return handleGetMyCourses(sessionUserId);
            case RequestType.GET_STUDENT_BOTS:
                return handleGetStudentBots(sessionUserId);
            case RequestType.ASK_BOT:
                return handleAskBot(request.getData(), sessionUserId);
            case RequestType.GET_MY_BOT_HISTORY:
                return handleGetMyBotHistory(request.getData(), sessionUserId);
            case RequestType.GET_COURSE_BOT:
                return handleGetCourseBot(request.getData(), sessionUserId);
            case RequestType.SAVE_BOT:
                return handleSaveBot(request.getData(), sessionUserId);
            case RequestType.SAVE_BOT_SOURCE:
                return handleSaveBotSource(request.getData(), sessionUserId);
            case RequestType.DELETE_BOT_SOURCE:
                return handleDeleteBotSource(request.getData(), sessionUserId);
            case RequestType.GET_BOT_HISTORY_ANONYMOUS:
                return handleGetBotHistoryAnonymous(request.getData(), sessionUserId);
            case RequestType.GET_ENGINE_STATUS:
                return handleGetEngineStatus(sessionUserId);
            case RequestType.GENERATE_QUESTIONS:
                return handleGenerateQuestions(request.getData(), sessionUserId);
            case RequestType.GENERATE_EXAM:
                return handleGenerateExam(request.getData(), sessionUserId);
            case RequestType.SAVE_GENERATED_QUESTIONS:
                return handleSaveGeneratedQuestions(request.getData(), sessionUserId);
            default:
                return new Response(false, "Unknown action: " + action, null);
        }
    }

    // ── GET_ALL_QUESTIONS ─────────────────────────────────────────────────────
    private Response handleGetAllQuestions() {
        try {
            DatabaseConnection.getInstance();
            QUESTIONSDAO dao = new QUESTIONSDAO();
            List<Question> questions = dao.getAllQuestions();
            return new Response(true, "Questions loaded.", questions);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Could not load questions: " + e.getMessage(), null);
        }
    }

    // ── CREATE_QUESTION ───────────────────────────────────────────────────────
    private Response handleCreateQuestion(Object data) {
        if (!(data instanceof Question)) {
            return new Response(false, "Invalid question data.", null);
        }
        Question question = (Question) data;
        try {
            DatabaseConnection.getInstance();
            QUESTIONSDAO dao = new QUESTIONSDAO();
            sanitize(question);
            int newId = dao.insertQuestion(question);
            Question created = dao.getQuestionById(newId);
            return new Response(true, "Question created.", created);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Could not create question: " + e.getMessage(), null);
        }
    }

    // ── DELETE_QUESTION ───────────────────────────────────────────────────────
    private Response handleDeleteQuestion(Object data) {
        if (!(data instanceof Integer)) {
            return new Response(false, "Invalid question ID.", null);
        }
        int questionId = (Integer) data;
        try {
            DatabaseConnection.getInstance();
            QUESTIONSDAO dao = new QUESTIONSDAO();
            dao.deleteQuestionById(questionId);
            return new Response(true, "Question deleted.", questionId);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Could not delete question: " + e.getMessage(), null);
        }
    }

    // ── UPDATE_QUESTION ───────────────────────────────────────────────────────
    private Response handleUpdateQuestion(Object data) {
        if (!(data instanceof Question)) {
            return new Response(false, "Invalid question data.", null);
        }
        Question question = (Question) data;
        try {
            DatabaseConnection.getInstance();
            QUESTIONSDAO dao = new QUESTIONSDAO();
            sanitize(question);
            dao.updateQuestion(question);
            Question updated = dao.getQuestionById(question.getId());
            return new Response(true,
                    "Question updated. The previous version was kept in the bank.", updated);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Could not update question: " + e.getMessage(), null);
        }
    }

    // ── GET_QUESTION_HISTORY ──────────────────────────────────────────────────
    /**
     * Lists the archived earlier versions of one question (spec 2.2). An
     * unedited question simply has none.
     */
    private Response handleGetQuestionHistory(Object data) {
        if (!(data instanceof Integer)) {
            return new Response(false, "Invalid question ID.", null);
        }
        int questionId = (Integer) data;
        try {
            DatabaseConnection.getInstance();
            QUESTIONSDAO dao = new QUESTIONSDAO();
            List<Question> versions = dao.getQuestionHistory(questionId);
            String message = versions.isEmpty()
                    ? "This question has not been edited yet."
                    : versions.size() + " earlier version(s) of question " + questionId + ".";
            return new Response(true, message, versions);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Could not load question history: " + e.getMessage(), null);
        }
    }

    // ── DUPLICATE_QUESTION ────────────────────────────────────────────────────
    /**
     * Copies a question into a new bank row (spec 2.3).
     *
     * The copy goes through {@code insertQuestion}, which mints a fresh
     * question_code and keeps {@code teacher_statistics.questions_count} in
     * step. Authorship is carried over: a duplicate belongs to whoever wrote
     * the original.
     */
    private Response handleDuplicateQuestion(Object data) {
        if (!(data instanceof Integer)) {
            return new Response(false, "Invalid question ID.", null);
        }
        int questionId = (Integer) data;
        try {
            DatabaseConnection.getInstance();
            QUESTIONSDAO dao = new QUESTIONSDAO();

            Question original = dao.getQuestionById(questionId);
            if (original == null) {
                return new Response(false, "Question " + questionId + " not found.", null);
            }

            Question copy = new Question(
                    0,
                    original.getQuestionText(),
                    original.getOptionA(),
                    original.getOptionB(),
                    original.getOptionC(),
                    original.getOptionD(),
                    original.getCorrectAnswer(),
                    original.getVisualAidUrl(),
                    original.getCourseId());
            copy.setTeacherId(original.getTeacherId());

            int newId = dao.insertQuestion(copy);
            Question created = dao.getQuestionById(newId);
            return new Response(true,
                    "Question " + questionId + " duplicated as question " + newId + ".", created);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Could not duplicate question: " + e.getMessage(), null);
        }
    }

    // ── UPDATE_EXAM ───────────────────────────────────────────────────────────
    /**
     * Saves an edited exam (spec 3.5).
     *
     * Refused outright once anyone has sat the exam — that paper is a
     * historical record, and rewriting its questions would silently change
     * what an existing submission was scored against. Otherwise the pre-edit
     * header is archived and the exam drops back to PENDING, so an edit
     * cannot quietly alter something the principal already approved.
     *
     * The 100-point rule is re-checked here rather than trusted from the
     * builder screen.
     */
    private Response handleUpdateExam(Object data) {
        if (!(data instanceof Exam)) {
            return new Response(false, "Invalid exam data.", null);
        }
        Exam exam = (Exam) data;
        if (exam.getExamId() <= 0) {
            return new Response(false, "Invalid exam ID.", null);
        }
        sanitize(exam);
        try {
            DatabaseConnection.getInstance();

            TestsDAO testsDAO = new TestsDAO();
            Exam current = testsDAO.getExamHeaderById(exam.getExamId());
            if (current == null) {
                return new Response(false, "Exam " + exam.getExamId() + " not found.", null);
            }

            test_submissionsDAO submissionsDAO = new test_submissionsDAO();
            if (submissionsDAO.hasSubmissions(exam.getExamId())) {
                return new Response(false,
                        "Cannot edit exam " + exam.getExamId()
                                + ": students have already submitted it.", null);
            }

            List<ExamQuestion> questions = exam.getExamQuestions();
            if (questions == null || questions.isEmpty()) {
                return new Response(false, "An exam must contain at least one question.", null);
            }

            int totalPoints = 0;
            for (ExamQuestion question : questions) {
                totalPoints += question.getPoints();
            }
            if (totalPoints != 100) {
                return new Response(false,
                        "The exam's questions must total exactly 100 points (currently "
                                + totalPoints + ").", null);
            }

            if (exam.getDurationMinutes() <= 0) {
                return new Response(false, "Duration must be greater than zero.", null);
            }

            CourseDAO courseDAO = new CourseDAO();
            int courseId = courseDAO.getCourseIdByName(exam.getCourse());
            if (courseId == -1) {
                return new Response(false,
                        "Course '" + exam.getCourse() + "' not found in the database.", null);
            }

            testsDAO.updateExamWithHistory(
                    exam.getExamId(),
                    courseId,
                    exam.getDurationMinutes(),
                    exam.getInstructions(),
                    exam.getTitle(),
                    questions);

            Exam updated = testsDAO.getExamHeaderById(exam.getExamId());
            String note = "APPROVED".equalsIgnoreCase(current.getApprovalStatus())
                    ? " It was approved, so it has been sent back to the principal for approval."
                    : " It is awaiting the principal's approval.";

            return new Response(true,
                    "Exam " + exam.getExamId() + " updated." + note, updated);

        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Could not update exam: " + e.getMessage(), null);
        }
    }

    // ── CREATE_EXAM ───────────────────────────────────────────────────────────
    private Response handleCreateExam(Object data) {
        if (!(data instanceof Exam)) {
            return new Response(false, "Invalid exam data.", null);
        }
        Exam exam = (Exam) data;
        sanitize(exam);
        try {
            DatabaseConnection.getInstance();

            CourseDAO courseDAO = new CourseDAO();
            int courseId = courseDAO.getCourseIdByName(exam.getCourse());
            if (courseId == -1) {
                return new Response(false,
                        "Course '" + exam.getCourse() + "' not found in the database.", null);
            }

            // Store title in teacher_notes, instructions in student_instructions
            String testCode = generateTestCode();
            Test test = new Test(0, testCode, exam.getTeacherId(), courseId,
                    exam.getDurationMinutes(), exam.getInstructions(), exam.getTitle(), true);

            TestsDAO testsDAO = new TestsDAO();
            int testId = testsDAO.insertTestGetId(test);

            test_questionsDAO tqDAO = new test_questionsDAO();
            for (ExamQuestion eq : exam.getExamQuestions()) {
                tqDAO.addQuestionToTest(testId, eq.getQuestionId(), eq.getPoints());
            }

            exam.setExamId(testId);
            return new Response(true, "Exam created.", exam);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Could not create exam: " + e.getMessage(), null);
        }
    }


    // ── GET_EXAM_RESULTS ──────────────────────────────────────────────────────
    /**
     * One exam's results across the students who sat it, for the teacher's
     * results screen (spec 10).
     *
     * Authorised from the session, never from the payload: a teacher may only
     * read the results of an exam they own, and the principal — who may see
     * every result anyway (spec 11) — may read any. Sending someone else's
     * exam ID gets a refusal, not their students' scores.
     */
    private Response handleGetExamResults(Object data, int sessionUserId) {

        if (!(data instanceof ExamResultsQuery query)) {
            return new Response(false, "Invalid results query.", null);
        }
        if (query.getExamId() <= 0) {
            return new Response(false, "Invalid exam ID.", null);
        }

        Integer gradeLevel = query.getGradeLevel();
        if (gradeLevel != null && (gradeLevel < 9 || gradeLevel > 12)) {
            return new Response(false, "Grade level must be between 9 and 12.", null);
        }

        try {
            DatabaseConnection.getInstance();

            User requester = loadSessionUser(sessionUserId);
            if (requester == null) {
                return new Response(false, "You must be logged in to view exam results.", null);
            }

            TestsDAO testsDAO = new TestsDAO();
            Exam exam = testsDAO.getExamHeaderById(query.getExamId());
            if (exam == null) {
                return new Response(false, "Exam " + query.getExamId() + " not found.", null);
            }

            boolean isPrincipal = "PRINCIPAL".equalsIgnoreCase(requester.getRole());
            boolean ownsTheExam = "TEACHER".equalsIgnoreCase(requester.getRole())
                    && exam.getTeacherId() == requester.getUserId();

            if (!isPrincipal && !ownsTheExam) {
                return new Response(false,
                        "You can only view the results of your own exams.", null);
            }

            test_submissionsDAO dao = new test_submissionsDAO();
            List<StudentExamResult> results =
                    dao.getResultsForExam(query.getExamId(), gradeLevel);

            String scope = gradeLevel == null ? "all grade levels" : "grade " + gradeLevel;
            return new Response(true,
                    results.size() + " result(s) for " + scope + ".", results);

        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Could not load exam results: " + e.getMessage(), null);
        }
    }


    // ── EXTEND_EXAM_DURATION ──────────────────────────────────────────────────
    /**
     * Adds time to an exam that is already running (spec 7).
     *
     * Persists the new duration first and pushes second, in that order: the
     * push only reaches the students sitting the exam at this instant, while
     * the stored duration is what anyone starting it later will get.
     *
     * Ownership comes from the session, as everywhere else — a teacher can
     * only extend their own exam.
     */
    private Response handleExtendExamDuration(Object data, int sessionUserId) {

        if (!(data instanceof ExamExtension extension)) {
            return new Response(false, "Invalid extension request.", null);
        }
        if (extension.getExamId() <= 0) {
            return new Response(false, "Invalid exam ID.", null);
        }
        if (extension.getExtraMinutes() < 1 || extension.getExtraMinutes() > 120) {
            return new Response(false,
                    "Extra time must be between 1 and 120 minutes.", null);
        }

        try {
            DatabaseConnection.getInstance();

            User requester = loadSessionUser(sessionUserId);
            if (requester == null) {
                return new Response(false, "You must be logged in to extend an exam.", null);
            }

            TestsDAO testsDAO = new TestsDAO();
            Exam exam = testsDAO.getExamHeaderById(extension.getExamId());
            if (exam == null) {
                return new Response(false, "Exam " + extension.getExamId() + " not found.", null);
            }

            boolean ownsTheExam = "TEACHER".equalsIgnoreCase(requester.getRole())
                    && exam.getTeacherId() == requester.getUserId();
            if (!ownsTheExam) {
                return new Response(false, "You can only extend your own exams.", null);
            }

            testsDAO.extendDuration(extension.getExamId(), extension.getExtraMinutes());
            Exam updated = testsDAO.getExamHeaderById(extension.getExamId());

            int notified = pushExamExtension(extension.getExamId(), extension.getExtraMinutes());

            return new Response(true,
                    "Exam extended by " + extension.getExtraMinutes() + " minute(s). "
                            + notified + " student(s) currently sitting it were notified.",
                    updated);

        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Could not extend the exam: " + e.getMessage(), null);
        }
    }

    /**
     * Pushes an extension to every client currently sitting the exam.
     *
     * This is the only place the server writes to a client that did not just
     * ask it something. The response is stamped with
     * {@code EXAM_DURATION_EXTENDED} here rather than by the central stamping
     * in {@code ClientHandler.run()}, which only applies to replies.
     *
     * One dead socket must not cost the other students their extra time, so
     * each write is attempted independently and a failure only drops that
     * client from the registry.
     *
     * @param examId exam that was extended
     * @param extraMinutes minutes added
     * @return how many clients were actually told
     */
    private int pushExamExtension(int examId, int extraMinutes) {

        Set<ClientHandler> watchers = examWatchers.get(examId);
        if (watchers == null || watchers.isEmpty()) {
            return 0;
        }

        Response push = new Response(true,
                "Your teacher added " + extraMinutes + " minute"
                        + (extraMinutes == 1 ? "" : "s") + " to this exam.",
                extraMinutes);
        push.setAction(RequestType.EXAM_DURATION_EXTENDED);

        int delivered = 0;
        for (ClientHandler watcher : watchers) {
            try {
                watcher.sendResponse(push);
                delivered++;
            } catch (IOException e) {
                // That student's socket is gone; drop it and keep going.
                watchers.remove(watcher);
            }
        }
        return delivered;
    }

    // ── GET_AVAILABLE_EXAMS ───────────────────────────────────────────────────
    private Response handleGetAvailableExams(Object data) {
        if (!(data instanceof Integer)) {
            return new Response(false, "Invalid student ID.", null);
        }
        int studentId = (Integer) data;
        try {
            DatabaseConnection.getInstance();
            Connection conn = DatabaseConnection.getConnection();

            EnrollmentDAO enrollmentDAO = new EnrollmentDAO(conn);
            List<Test> tests = enrollmentDAO.getAvailableTestsForStudentId(studentId);

            CourseDAO courseDAO = new CourseDAO();
            List<Exam> exams = new ArrayList<>();
            for (Test t : tests) {
                Course course = courseDAO.getCourseById(t.getCourseId());
                String courseName = course != null ? course.getCourseName() : String.valueOf(t.getCourseId());
                // teacher_notes holds the title, student_instructions holds the instructions
                Exam exam = new Exam(t.getId(), t.getTeacherNotes(), courseName,
                        t.getStudentInstructions(), t.getDurationMinutes(), t.getTeacherId(), null);
                exams.add(exam);
            }
            return new Response(true, "Available exams loaded.", exams);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Could not load available exams: " + e.getMessage(), null);
        }
    }

    // ── GET_EXAM_BY_ID ────────────────────────────────────────────────────────
    private Response handleGetExamById(Object data) {
        if (!(data instanceof Integer)) {
            return new Response(false, "Invalid exam ID.", null);
        }
        int examId = (Integer) data;
        try {
            DatabaseConnection.getInstance();

            TestsDAO testsDAO = new TestsDAO();
            Test test = testsDAO.getTestById(examId);
            if (test == null) {
                return new Response(false, "Exam not found.", null);
            }

            List<ExamQuestion> examQuestions = loadExamQuestions(examId);

            CourseDAO courseDAO = new CourseDAO();
            Course course = courseDAO.getCourseById(test.getCourseId());
            String courseName = course != null ? course.getCourseName() : String.valueOf(test.getCourseId());

            Exam exam = new Exam(test.getId(), test.getTeacherNotes(), courseName,
                    test.getStudentInstructions(), test.getDurationMinutes(),
                    test.getTeacherId(), examQuestions);

            return new Response(true, "Exam loaded.", exam);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Could not load exam: " + e.getMessage(), null);
        }
    }

    /** Loads the full question list (with per-exam points) for an exam. */
    private List<ExamQuestion> loadExamQuestions(int examId) throws SQLException {
        test_questionsDAO tqDAO = new test_questionsDAO();
        List<TestQuestion> testQuestions = tqDAO.getQuestionsOfTest(examId);

        QUESTIONSDAO qDAO = new QUESTIONSDAO();
        List<ExamQuestion> examQuestions = new ArrayList<>();
        for (TestQuestion tq : testQuestions) {
            Question q = qDAO.getQuestionById(tq.getQuestionId());
            if (q != null) {
                examQuestions.add(new ExamQuestion(q, (int) tq.getPointsWorth()));
            }
        }
        return examQuestions;
    }

    // ── SUBMIT_EXAM ───────────────────────────────────────────────────────────
    private Response handleSubmitExam(Object data) {
        if (!(data instanceof ExamSubmission)) {
            return new Response(false, "Invalid exam submission.", null);
        }
        ExamSubmission submission = (ExamSubmission) data;
        try {
            DatabaseConnection.getInstance();
            Connection conn = DatabaseConnection.getConnection();

            test_submissionsDAO tsDAO = new test_submissionsDAO();

            // The row already exists — START_EXAM_BY_CODE opened it. Finalise
            // that one rather than inserting a second, or every sitting would
            // leave an orphaned PENDING row behind that keeps the bot locked
            // out forever. A client that reached here without passing the
            // entry gate has no open row, and is refused rather than quietly
            // granted one: the gate is where enrolment, the exam window and
            // the ת"ז are checked.
            int submissionId = tsDAO.findOpenSubmissionId(
                    submission.getExamId(), submission.getStudentId());
            if (submissionId == -1) {
                return new Response(false,
                        "No exam in progress for this student. Start the exam from the "
                                + "entry gate before submitting.", null);
            }

            student_answersDAO saDAO = new student_answersDAO(conn);
            for (StudentAnswer answer : submission.getAnswers()) {
                String letter = InputValidator.answerLetter(answer.getSelectedAnswer());
                if (letter == null) {
                    continue; // ignore malformed answers
                }
                saDAO.insertAnswer(submissionId, answer.getQuestionId(), letter);
            }

            tsDAO.calculateAndFinalizeScore(submissionId);

            TestSubmission ts = tsDAO.getSubmissionById(submissionId);
            TestsDAO testsDAO = new TestsDAO();
            Test test = testsDAO.getTestById(submission.getExamId());
            String examTitle = (test != null && test.getTeacherNotes() != null)
                    ? test.getTeacherNotes() : "Exam " + submission.getExamId();

            String date = (ts != null && ts.getSubmittedAt() != null)
                    ? ts.getSubmittedAt().toString() : "N/A";

            // The score exists in the database at this point but is NOT sent
            // back: a computed grade is not the student's grade until a
            // teacher approves it. The receipt carries no number — the grade
            // shows up in Student Results once it is released.
            ExamResult result = new ExamResult(submissionId, submission.getExamId(),
                    examTitle, submission.getStudentId(), 0.0, 100.0, date, "AWAITING_APPROVAL");

            return new Response(true,
                    "Exam submitted. Your answers were recorded — your grade will appear "
                            + "once your teacher has approved it.", result);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Could not submit exam: " + e.getMessage(), null);
        }
    }

    // ── GET_STUDENT_RESULTS ───────────────────────────────────────────────────
    private Response handleGetStudentResults(Object data) {
        if (!(data instanceof Integer)) {
            return new Response(false, "Invalid student ID.", null);
        }
        int studentId = (Integer) data;
        try {
            DatabaseConnection.getInstance();
            test_submissionsDAO tsDAO = new test_submissionsDAO();
            List<ExamResult> results = tsDAO.getResultsForStudent(studentId);
            return new Response(true, "Results loaded.", results);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Could not load results: " + e.getMessage(), null);
        }
    }

    // ── GET_TEACHER_EXAMS ─────────────────────────────────────────────────────
    private Response handleGetTeacherExams(Object data) {
        if (!(data instanceof Integer)) {
            return new Response(false, "Invalid teacher ID.", null);
        }
        int teacherId = (Integer) data;
        try {
            DatabaseConnection.getInstance();
            TestsDAO dao = new TestsDAO();
            List<Exam> exams = dao.getExamsByTeacherId(teacherId);
            return new Response(true, "Loaded " + exams.size() + " exam(s).", exams);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Could not load exams: " + e.getMessage(), null);
        }
    }

    // ── DELETE_EXAM ───────────────────────────────────────────────────────────
    private Response handleDeleteExam(Object data) {
        if (!(data instanceof Integer)) {
            return new Response(false, "Invalid exam ID.", null);
        }
        int examId = (Integer) data;
        try {
            DatabaseConnection.getInstance();
            test_submissionsDAO tsDAO = new test_submissionsDAO();
            if (tsDAO.hasSubmissions(examId)) {
                return new Response(false,
                        "Cannot delete exam " + examId + ": it has existing student submissions.", null);
            }
            test_questionsDAO tqDAO = new test_questionsDAO();
            tqDAO.removeAllQuestionsFromTest(examId);
            TestsDAO testDAO = new TestsDAO();
            testDAO.deleteTestById(examId);
            return new Response(true, "Exam " + examId + " deleted.", examId);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Could not delete exam: " + e.getMessage(), null);
        }
    }

    // ── TOGGLE_EXAM_STATUS ────────────────────────────────────────────────────
    private Response handleToggleExamStatus(Object data) {
        if (!(data instanceof Integer)) {
            return new Response(false, "Invalid exam ID.", null);
        }
        int examId = (Integer) data;
        try {
            DatabaseConnection.getInstance();
            TestsDAO testDAO = new TestsDAO();
            Exam current = testDAO.getExamHeaderById(examId);
            if (current == null) {
                return new Response(false, "Exam not found.", null);
            }
            // Only guard the PENDING/REJECTED -> active transition; deactivating
            // is always allowed regardless of approval status.
            boolean willActivate = !current.isActive();
            if (willActivate && !"APPROVED".equals(current.getApprovalStatus())) {
                return new Response(false,
                        "Only an approved exam can be activated.", null);
            }
            test_questionsDAO tqDAO = new test_questionsDAO();
            tqDAO.toggleTestStatus(examId);
            Exam updated = testDAO.getExamHeaderById(examId);
            if (updated == null) {
                return new Response(false, "Exam not found after toggle.", null);
            }
            String state = updated.isActive() ? "activated" : "deactivated";
            return new Response(true, "Exam " + examId + " " + state + ".", updated);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Could not toggle exam status: " + e.getMessage(), null);
        }
    }

    // ── GET_PENDING_EXAMS ─────────────────────────────────────────
    private Response handleGetPendingExams() {
        try {
            DatabaseConnection.getInstance();
            TestsDAO dao = new TestsDAO();
            List<Exam> pending = dao.getPendingExams();
            return new Response(true, "Loaded " + pending.size() + " pending exam(s).", pending);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Could not load pending exams: " + e.getMessage(), null);
        }
    }

    // ── APPROVE_EXAM ──────────────────────────────────────────────
    private Response handleApproveExam(Object data) {
        if (!(data instanceof Integer)) {
            return new Response(false, "Invalid exam ID.", null);
        }
        int examId = (Integer) data;
        try {
            DatabaseConnection.getInstance();
            TestsDAO dao = new TestsDAO();
            Exam updated = dao.approveExam(examId);
            if (updated == null) return new Response(false, "Exam not found.", null);
            return new Response(true, "Exam " + examId + " approved.", updated);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Could not approve exam: " + e.getMessage(), null);
        }
    }

    // ── REJECT_EXAM ───────────────────────────────────────────────
    private Response handleRejectExam(Object data) {
        if (!(data instanceof Exam)) {
            return new Response(false, "Invalid exam data.", null);
        }
        Exam exam = (Exam) data;
        int examId = exam.getExamId();
        if (examId <= 0) {
            return new Response(false, "Invalid exam ID.", null);
        }
        String reason = InputValidator.clean(exam.getRejectionReason(), 500);
        if (reason == null || reason.isBlank()) {
            return new Response(false, "A rejection reason is required.", null);
        }
        try {
            DatabaseConnection.getInstance();
            TestsDAO dao = new TestsDAO();
            Exam updated = dao.rejectExam(examId, reason);
            if (updated == null) return new Response(false, "Exam not found.", null);
            return new Response(true, "Exam " + examId + " rejected.", updated);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Could not reject exam: " + e.getMessage(), null);
        }
    }

    // ── SCHEDULE_EXAM ─────────────────────────────────────────────
    private static final java.util.regex.Pattern EXAM_CODE_PATTERN =
            java.util.regex.Pattern.compile("^[0-9]{4}$");

    private Response handleScheduleExam(Object data) {
        if (!(data instanceof Exam)) {
            return new Response(false, "Invalid exam data.", null);
        }
        Exam exam = (Exam) data;
        int examId = exam.getExamId();
        if (examId <= 0) {
            return new Response(false, "Invalid exam ID.", null);
        }
        Timestamp openAt = exam.getOpenAt();
        Timestamp closeAt = exam.getCloseAt();
        String examCode = exam.getExamCode();

        if (examCode == null || !EXAM_CODE_PATTERN.matcher(examCode).matches()) {
            return new Response(false, "The exam code must be exactly 4 digits.", null);
        }
        if (openAt == null || closeAt == null) {
            return new Response(false, "Both an opening and closing date/time are required.", null);
        }
        if (!closeAt.after(openAt)) {
            return new Response(false, "The closing time must be after the opening time.", null);
        }

        try {
            DatabaseConnection.getInstance();
            TestsDAO dao = new TestsDAO();
            Exam current = dao.getExamHeaderById(examId);
            if (current == null) {
                return new Response(false, "Exam not found.", null);
            }
            if (!"APPROVED".equals(current.getApprovalStatus())) {
                return new Response(false,
                        "Only an approved exam can be scheduled.", null);
            }
            Exam updated = dao.scheduleExam(examId, openAt, closeAt, examCode);
            return new Response(true, "Exam " + examId + " scheduled.", updated);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Could not schedule exam: " + e.getMessage(), null);
        }
    }

    // ── START_EXAM_BY_CODE ────────────────────────────────────────
    private Response handleStartExamByCode(Object data) {
        if (!(data instanceof ExamEntryRequest)) {
            return new Response(false, "Invalid exam entry data.", null);
        }
        ExamEntryRequest entry = (ExamEntryRequest) data;
        String examCode = InputValidator.clean(entry.getExamCode(), 4);
        String nationalId = InputValidator.clean(entry.getNationalId(), 9);
        int studentId = entry.getStudentId();

        if (examCode == null || !EXAM_CODE_PATTERN.matcher(examCode).matches()) {
            return new Response(false, "Enter a valid 4-digit exam code.", null);
        }
        if (nationalId == null || nationalId.isBlank()) {
            return new Response(false, "Enter your national ID.", null);
        }
        if (studentId <= 0) {
            return new Response(false, "Invalid student ID.", null);
        }

        try {
            DatabaseConnection.getInstance();

            TestsDAO testsDAO = new TestsDAO();
            Exam header = testsDAO.getExamHeaderByCode(examCode);
            if (header == null) {
                return new Response(false, "No exam found for that code.", null);
            }

            if (!"APPROVED".equals(header.getApprovalStatus()) || !header.isActive()) {
                return new Response(false, "This exam is not currently available.", null);
            }

            Timestamp now = new Timestamp(System.currentTimeMillis());
            if (header.getOpenAt() != null && now.before(header.getOpenAt())) {
                return new Response(false, "This exam has not opened yet.", null);
            }
            if (header.getCloseAt() != null && now.after(header.getCloseAt())) {
                return new Response(false, "This exam is now closed.", null);
            }

            UserDAO userDAO = new UserDAO(DatabaseConnection.getConnection());
            User student = userDAO.getUserById(studentId);
            if (student == null || !"STUDENT".equalsIgnoreCase(student.getRole())) {
                return new Response(false, "Student account not found.", null);
            }
            if (student.getNationalId() == null || !student.getNationalId().equals(nationalId)) {
                return new Response(false, "The ID you entered does not match our records.", null);
            }

            CourseDAO courseDAO = new CourseDAO();
            int courseId = courseDAO.getCourseIdByName(header.getCourse());
            EnrollmentDAO enrollmentDAO = new EnrollmentDAO(DatabaseConnection.getConnection());
            if (courseId == -1 || !enrollmentDAO.isStudentEnrolled(studentId, courseId)) {
                return new Response(false, "You are not enrolled in this exam's course.", null);
            }

            List<ExamQuestion> examQuestions = loadExamQuestions(header.getExamId());
            header.setExamQuestions(examQuestions);

            // Open the submission row NOW, not at submit time. Two things
            // depend on it: started_at becomes a real start time instead of a
            // duplicate of submitted_at, and the learning bot's exam lockout
            // gains a record that survives a reconnect — ClientController
            // silently rebuilds a dropped socket, and the new ClientHandler
            // starts with no session and no activeExamId, so the in-memory
            // registry alone would forget this student was mid-exam.
            //
            // Deliberately after every admission check: a refused attempt
            // must not leave a row behind that locks the bot out.
            test_submissionsDAO tsDAO = new test_submissionsDAO();
            tsDAO.openSubmission(header.getExamId(), studentId);

            return new Response(true, "Exam loaded.", header);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Could not start exam: " + e.getMessage(), null);
        }
    }

    // ── PRINCIPAL DATA ACCESS (spec 11) ───────────────────────────
    /**
     * Confirms the session belongs to a principal.
     *
     * The system-wide listings and the comparison reports span every
     * teacher and every student, so they are principal-only. As everywhere
     * else, the role comes from the session, not from the payload.
     *
     * @return null when the requester may proceed, otherwise the refusal
     */
    private Response requirePrincipal(int sessionUserId, String what) throws SQLException {
        return requirePrincipalTo(sessionUserId, "view " + what);
    }

    /**
     * The same check phrased around an action rather than a listing, for the
     * handlers that change something ("manage user accounts") instead of
     * reading it ("view all exams").
     *
     * @return null when the requester may proceed, otherwise the refusal
     */
    private Response requirePrincipalTo(int sessionUserId, String action) throws SQLException {
        User requester = loadSessionUser(sessionUserId);
        if (requester == null) {
            return new Response(false, "You must be logged in to " + action + ".", null);
        }
        if (!"PRINCIPAL".equalsIgnoreCase(requester.getRole())) {
            return new Response(false, "Only the principal can " + action + ".", null);
        }
        return null;
    }

    // ══ USER MANAGEMENT (principal) ═══════════════════════════════════════════
    //
    // Four handlers, all principal-only, all authorising from the session and
    // never from an id in the payload. Two rules run through them:
    //
    //   1. A password hash never travels. getAllUsers() does not SELECT it, and
    //      everything answered from getUserById() — which does load it, because
    //      logging in needs it — goes through stripSecret() first.
    //   2. The principal cannot lock themselves out. They may not delete their
    //      own account, nor demote it out of PRINCIPAL. Without that, one
    //      mis-click ends with a system nobody can administer, and there is no
    //      recovery path in the app.

    /** Clears the password field before a user object goes back to a client. */
    private User stripSecret(User user) {
        if (user != null) {
            user.setPassword(null);
        }
        return user;
    }

    // ── GET_ALL_USERS ─────────────────────────────────────────────
    private Response handleGetAllUsers(int sessionUserId) {
        try {
            DatabaseConnection.getInstance();
            Response denial = requirePrincipal(sessionUserId, "user accounts");
            if (denial != null) return denial;

            UserDAO dao = new UserDAO(DatabaseConnection.getConnection());
            List<User> users = dao.getAllUsers();
            return new Response(true, "Loaded " + users.size() + " account(s).", users);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Could not load users: " + e.getMessage(), null);
        }
    }

    /**
     * Validates and normalises an account in place, returning the refusal
     * message for the first thing wrong with it, or null when it is usable.
     *
     * Shared by create and update so the two cannot drift into disagreeing
     * about what a valid account is. The role/grade-level pairing is the part
     * that matters: the users table CHECKs that a student has a grade level of
     * 9-12 and that staff have none, so anything else fails at the database
     * with a message no principal could act on.
     */
    private String validateAccount(User user) {

        user.setUsername(InputValidator.clean(user.getUsername(), 50));
        user.setFullName(InputValidator.clean(user.getFullName(), 100));
        user.setNationalId(InputValidator.clean(user.getNationalId(), 9));
        user.setAvatarUrl(InputValidator.clean(user.getAvatarUrl(), 255));

        if (user.getUsername() == null || user.getUsername().isBlank()) {
            return "A username is required.";
        }
        if (user.getUsername().contains(" ")) {
            return "A username cannot contain spaces.";
        }
        if (user.getFullName() == null || user.getFullName().isBlank()) {
            return "A full name is required.";
        }

        String role = InputValidator.oneOf(user.getRole(), null,
                "STUDENT", "TEACHER", "PRINCIPAL");
        if (role == null) {
            return "Role must be STUDENT, TEACHER or PRINCIPAL.";
        }
        user.setRole(role);

        if ("STUDENT".equals(role)) {
            Integer grade = user.getGradeLevel();
            if (grade == null || grade < 9 || grade > 12) {
                return "A student needs a grade level between 9 and 12.";
            }
        } else {
            // Not an error to send one — staff simply do not have a grade level,
            // and the table's CHECK would reject the row if it were kept.
            user.setGradeLevel(null);
        }

        String nationalId = user.getNationalId();
        if (nationalId != null && !nationalId.isBlank() && !nationalId.matches("[0-9]{9}")) {
            return "A national ID must be exactly 9 digits.";
        }

        return null;
    }

    /**
     * Turns a constraint violation into something the principal can act on.
     * The raw SQLState/message names a column and an index, which is accurate
     * and useless on a form.
     */
    private String describeConstraint(SQLException e, String fallback) {
        if (e.getErrorCode() == 1062) {
            String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            if (message.contains("national_id")) {
                return "That national ID already belongs to another account.";
            }
            return "That username is already taken.";
        }
        if (e.getErrorCode() == 1451 || e.getErrorCode() == 1452) {
            return fallback;
        }
        return fallback + " (" + e.getMessage() + ")";
    }

    // ── CREATE_USER ───────────────────────────────────────────────
    private Response handleCreateUser(Object data, int sessionUserId) {
        if (!(data instanceof User)) {
            return new Response(false, "Invalid user data.", null);
        }
        User user = (User) data;
        try {
            DatabaseConnection.getInstance();
            Response denial = requirePrincipalTo(sessionUserId, "manage user accounts");
            if (denial != null) return denial;

            String problem = validateAccount(user);
            if (problem != null) return new Response(false, problem, null);

            if (user.getPassword() == null || user.getPassword().isBlank()) {
                return new Response(false, "A password is required for a new account.", null);
            }

            UserDAO dao = new UserDAO(DatabaseConnection.getConnection());
            User created = dao.insertUserReturning(user);
            if (created == null) {
                return new Response(false, "The account was not created.", null);
            }
            return new Response(true, "Account created.", stripSecret(created));

        } catch (SQLException e) {
            return new Response(false,
                    describeConstraint(e, "Could not create the account."), null);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Could not create the account: " + e.getMessage(), null);
        }
    }

    // ── UPDATE_USER ───────────────────────────────────────────────
    private Response handleUpdateUser(Object data, int sessionUserId) {
        if (!(data instanceof User)) {
            return new Response(false, "Invalid user data.", null);
        }
        User user = (User) data;
        try {
            DatabaseConnection.getInstance();
            Response denial = requirePrincipalTo(sessionUserId, "manage user accounts");
            if (denial != null) return denial;

            UserDAO dao = new UserDAO(DatabaseConnection.getConnection());
            User existing = dao.getUserById(user.getUserId());
            if (existing == null) {
                return new Response(false, "That account no longer exists.", null);
            }

            String problem = validateAccount(user);
            if (problem != null) return new Response(false, problem, null);

            if (user.getUserId() == sessionUserId
                    && !"PRINCIPAL".equalsIgnoreCase(user.getRole())) {
                return new Response(false,
                        "You cannot change your own role away from principal.", null);
            }

            // A blank password means "leave it alone". Passing the stored hash
            // through is what preserves it: updateUser() re-hashes anything that
            // is not already a BCrypt hash, and keeps it when it is.
            if (user.getPassword() == null || user.getPassword().isBlank()) {
                user.setPassword(existing.getPassword());
            }

            dao.updateUser(user);
            return new Response(true, "Account updated.",
                    stripSecret(dao.getUserById(user.getUserId())));

        } catch (SQLException e) {
            return new Response(false,
                    describeConstraint(e, "Could not update the account."), null);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Could not update the account: " + e.getMessage(), null);
        }
    }

    // ── DELETE_USER ───────────────────────────────────────────────
    private Response handleDeleteUser(Object data, int sessionUserId) {
        if (!(data instanceof Integer)) {
            return new Response(false, "Invalid user ID.", null);
        }
        int userId = (Integer) data;
        try {
            DatabaseConnection.getInstance();
            Response denial = requirePrincipalTo(sessionUserId, "manage user accounts");
            if (denial != null) return denial;

            if (userId == sessionUserId) {
                return new Response(false, "You cannot delete your own account.", null);
            }

            UserDAO dao = new UserDAO(DatabaseConnection.getConnection());
            if (dao.getUserById(userId) == null) {
                return new Response(false, "That account no longer exists.", null);
            }

            dao.deleteUserById(userId);
            return new Response(true, "Account deleted.", userId);

        } catch (SQLException e) {
            // An account that wrote an exam or sat one is referenced by rows the
            // system still needs. Refusing beats cascading a student's results
            // out of existence to tidy up a user list.
            return new Response(false, describeConstraint(e,
                    "This account has exams, submissions or enrolments on record "
                            + "and cannot be deleted."), null);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Could not delete the account: " + e.getMessage(), null);
        }
    }

    // ── GET_ALL_EXAMS ─────────────────────────────────────────────
    private Response handleGetAllExams(int sessionUserId) {
        try {
            DatabaseConnection.getInstance();
            Response denial = requirePrincipal(sessionUserId, "all exams");
            if (denial != null) return denial;

            TestsDAO dao = new TestsDAO();
            List<Exam> exams = dao.getAllExams();
            return new Response(true, "Loaded " + exams.size() + " exam(s).", exams);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Could not load all exams: " + e.getMessage(), null);
        }
    }

    // ── GET_ALL_RESULTS ───────────────────────────────────────────
    private Response handleGetAllResults(int sessionUserId) {
        try {
            DatabaseConnection.getInstance();
            Response denial = requirePrincipal(sessionUserId, "all results");
            if (denial != null) return denial;

            test_submissionsDAO dao = new test_submissionsDAO();
            List<ExamResult> results = dao.getAllResults();
            return new Response(true, "Loaded " + results.size() + " result(s).", results);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Could not load all results: " + e.getMessage(), null);
        }
    }

    // ── GET_GRADE_DISTRIBUTION ────────────────────────────────────
    /**
     * Grade distribution in 10-point bands. A null payload means "across
     * every graded submission"; an Integer restricts it to one exam.
     */
    private Response handleGetGradeDistribution(Object data, int sessionUserId) {
        Integer examId;
        if (data == null) {
            examId = null;
        } else if (data instanceof Integer id) {
            examId = id > 0 ? id : null;
        } else {
            return new Response(false, "Invalid exam ID.", null);
        }
        try {
            DatabaseConnection.getInstance();
            Response denial = requirePrincipal(sessionUserId, "reports");
            if (denial != null) return denial;

            ReportsDAO dao = new ReportsDAO();
            List<GradeDistributionBucket> buckets = dao.getGradeDistribution(examId);
            return new Response(true, "Grade distribution loaded.", buckets);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Could not load grade distribution: " + e.getMessage(), null);
        }
    }

    // ── GET_TEACHER/COURSE/STUDENT_STATISTICS ─────────────────────
    /**
     * The three comparison reports (spec 12). They differ only in what the
     * ID means, so one handler serves all three and the action picks the
     * DAO method.
     */
    private Response handleGetComparisonStatistics(String action, Object data, int sessionUserId) {
        if (!(data instanceof Integer)) {
            return new Response(false, "Invalid identifier for this report.", null);
        }
        int id = (Integer) data;
        if (id <= 0) {
            return new Response(false, "Invalid identifier for this report.", null);
        }
        try {
            DatabaseConnection.getInstance();
            Response denial = requirePrincipal(sessionUserId, "reports");
            if (denial != null) return denial;

            ReportsDAO dao = new ReportsDAO();
            List<ExamStatistics> stats = switch (action) {
                case RequestType.GET_TEACHER_STATISTICS -> dao.getStatisticsByTeacher(id);
                case RequestType.GET_COURSE_STATISTICS  -> dao.getStatisticsByCourse(id);
                default                                 -> dao.getStatisticsByStudent(id);
            };
            return new Response(true, "Loaded statistics for " + stats.size() + " exam(s).", stats);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Could not load statistics: " + e.getMessage(), null);
        }
    }

    // ── GET_PENDING_GRADES ────────────────────────────────────────
    /**
     * Lists the submissions awaiting the requesting teacher's approval.
     *
     * The teacher is taken from the socket's session, not from the request
     * payload, so a client cannot ask for another teacher's queue by
     * sending a different ID.
     */
    private Response handleGetPendingGrades(int sessionUserId) {
        try {
            DatabaseConnection.getInstance();
            User requester = loadSessionUser(sessionUserId);
            if (requester == null) {
                return new Response(false, "You must be logged in to view pending grades.", null);
            }
            if (!"TEACHER".equalsIgnoreCase(requester.getRole())) {
                return new Response(false, "Only a teacher can approve grades.", null);
            }
            test_submissionsDAO dao = new test_submissionsDAO();
            List<PendingGrade> pending = dao.getPendingGradesForTeacher(sessionUserId);
            return new Response(true, "Loaded " + pending.size() + " grade(s) awaiting approval.", pending);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Could not load pending grades: " + e.getMessage(), null);
        }
    }

    // ── APPROVE_GRADE ─────────────────────────────────────────────
    /**
     * Releases a computer-calculated grade to the student unchanged.
     */
    private Response handleApproveGrade(Object data, int sessionUserId) {
        if (!(data instanceof Integer)) {
            return new Response(false, "Invalid submission ID.", null);
        }
        int submissionId = (Integer) data;
        if (submissionId <= 0) {
            return new Response(false, "Invalid submission ID.", null);
        }
        try {
            DatabaseConnection.getInstance();
            test_submissionsDAO dao = new test_submissionsDAO();

            Response denial = checkGradingRights(dao, submissionId, sessionUserId);
            if (denial != null) return denial;

            if (!dao.approveGrade(submissionId, sessionUserId)) {
                return new Response(false,
                        "This grade is no longer awaiting approval.", null);
            }
            PendingGrade updated = dao.getPendingGradeById(submissionId);
            return new Response(true, "Grade approved and released to the student.", updated);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Could not approve grade: " + e.getMessage(), null);
        }
    }

    // ── OVERRIDE_GRADE ────────────────────────────────────────────
    /**
     * Replaces a computer-calculated grade with the teacher's own, keeping
     * the computed score and the stated justification on record.
     */
    private Response handleOverrideGrade(Object data, int sessionUserId) {
        if (!(data instanceof GradeOverride)) {
            return new Response(false, "Invalid grade override data.", null);
        }
        GradeOverride override = (GradeOverride) data;
        int submissionId = override.getSubmissionId();
        if (submissionId <= 0) {
            return new Response(false, "Invalid submission ID.", null);
        }
        double newScore = override.getNewScore();
        if (Double.isNaN(newScore) || newScore < 0.0 || newScore > 100.0) {
            return new Response(false, "The new grade must be between 0 and 100.", null);
        }
        String reason = InputValidator.clean(override.getJustification(), 500);
        if (reason == null || reason.isBlank()) {
            return new Response(false, "A justification is required to change a grade.", null);
        }
        try {
            DatabaseConnection.getInstance();
            test_submissionsDAO dao = new test_submissionsDAO();

            Response denial = checkGradingRights(dao, submissionId, sessionUserId);
            if (denial != null) return denial;

            if (!dao.overrideGrade(submissionId, newScore, reason, sessionUserId)) {
                return new Response(false,
                        "This grade is no longer awaiting approval.", null);
            }
            PendingGrade updated = dao.getPendingGradeById(submissionId);
            return new Response(true,
                    "Grade changed to " + String.format("%.1f", newScore)
                            + " and released to the student.", updated);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Could not change grade: " + e.getMessage(), null);
        }
    }

    /**
     * Confirms the session user is the teacher who owns the exam behind a
     * submission, and may therefore approve or change its grade.
     *
     * @return null when the requester may proceed, otherwise the refusal to
     *         send back
     */
    private Response checkGradingRights(test_submissionsDAO dao, int submissionId, int sessionUserId)
            throws SQLException {

        User requester = loadSessionUser(sessionUserId);
        if (requester == null) {
            return new Response(false, "You must be logged in to approve grades.", null);
        }
        if (!"TEACHER".equalsIgnoreCase(requester.getRole())) {
            return new Response(false, "Only a teacher can approve grades.", null);
        }
        int ownerId = dao.getOwningTeacherId(submissionId);
        if (ownerId == -1) {
            return new Response(false, "Submission not found.", null);
        }
        if (ownerId != sessionUserId) {
            return new Response(false, "You can only grade submissions for your own exams.", null);
        }
        return null;
    }

    // ── GET_SUBMISSION_REVIEW ─────────────────────────────────────
    /**
     * Builds the checked exam form for one of the requesting student's own
     * submissions.
     *
     * Two rules make this safe to hand a student: the submission must belong
     * to the session's own user (so one student cannot read another's paper),
     * and it must already be GRADED (so the correct answers of an exam still
     * awaiting approval — and possibly still being sat by others — are not
     * handed out early).
     */
    private Response handleGetSubmissionReview(Object data, int sessionUserId) {
        if (!(data instanceof Integer)) {
            return new Response(false, "Invalid submission ID.", null);
        }
        int submissionId = (Integer) data;
        if (submissionId <= 0) {
            return new Response(false, "Invalid submission ID.", null);
        }
        try {
            DatabaseConnection.getInstance();

            User requester = loadSessionUser(sessionUserId);
            if (requester == null) {
                return new Response(false, "You must be logged in to review an exam.", null);
            }

            test_submissionsDAO tsDAO = new test_submissionsDAO();
            TestSubmission submission = tsDAO.getSubmissionById(submissionId);
            if (submission == null || submission.getStudentId() != sessionUserId) {
                // Deliberately the same message either way — a student must
                // not be able to probe which submission IDs exist.
                return new Response(false, "That exam form is not available.", null);
            }
            if (!"GRADED".equalsIgnoreCase(submission.getStatus())) {
                return new Response(false,
                        "This exam has not been approved by your teacher yet.", null);
            }

            TestsDAO testsDAO = new TestsDAO();
            Test test = testsDAO.getTestById(submission.getTestId());
            String examTitle = (test != null && test.getTeacherNotes() != null)
                    ? test.getTeacherNotes() : "Exam " + submission.getTestId();

            // Answers keyed by question so the exam's own question order drives
            // the review; an unanswered question simply has no entry.
            student_answersDAO saDAO = new student_answersDAO(DatabaseConnection.getConnection());
            Map<Integer, String> givenAnswers = new HashMap<>();
            for (StudentAnswer answer : saDAO.getAnswersForSubmission(submissionId)) {
                givenAnswers.put(answer.getQuestionId(), answer.getSelectedAnswer());
            }

            List<ReviewedQuestion> reviewed = new ArrayList<>();
            for (ExamQuestion eq : loadExamQuestions(submission.getTestId())) {
                Question q = eq.getQuestion();
                if (q == null) continue;
                String given = givenAnswers.get(q.getId());
                String correct = q.getCorrectAnswer();
                boolean isCorrect = given != null && given.equalsIgnoreCase(correct);
                reviewed.add(new ReviewedQuestion(q, given, correct, eq.getPoints(), isCorrect));
            }

            double score = submission.getFinalScore() != null ? submission.getFinalScore() : 0.0;
            SubmissionReview review = new SubmissionReview(
                    submissionId, examTitle, score, 100.0, submission.getStatus(), reviewed);

            return new Response(true, "Checked exam form loaded.", review);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Could not load the checked exam: " + e.getMessage(), null);
        }
    }

    /**
     * Loads the user behind a socket's session.
     *
     * @param sessionUserId session user ID, or -1 when nobody is logged in
     * @return the user, or null when there is no active session
     */
    private User loadSessionUser(int sessionUserId) throws SQLException {
        if (sessionUserId <= 0) return null;
        UserDAO userDAO = new UserDAO(DatabaseConnection.getConnection());
        return userDAO.getUserById(sessionUserId);
    }

    // ── GET_ALL_COURSES ───────────────────────────────────────────
    private Response handleGetAllCourses() {
        try {
            DatabaseConnection.getInstance();
            CourseDAO dao = new CourseDAO();
            List<Course> courses = dao.getAllCourses();
            return new Response(true, "Courses loaded.", courses);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Could not load courses: " + e.getMessage(), null);
        }
    }

    // ── GET_PASS_FAIL_REPORT ──────────────────────────────────────
    private Response handleGetPassFailReport() {
        try {
            DatabaseConnection.getInstance();
            ReportsDAO dao = new ReportsDAO();
            List<PassFailStat> stats = dao.getPassFailByCourse();
            return new Response(true, "Pass/fail report loaded.", stats);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Could not load pass/fail report: " + e.getMessage(), null);
        }
    }

    // ── GET_SCORE_TREND_REPORT ────────────────────────────────────
    private Response handleGetScoreTrendReport() {
        try {
            DatabaseConnection.getInstance();
            ReportsDAO dao = new ReportsDAO();
            List<ScoreTrendPoint> points = dao.getScoreTrendByDate();
            return new Response(true, "Score trend report loaded.", points);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Could not load score trend report: " + e.getMessage(), null);
        }
    }

    // ── GET_TEACHER_ACTIVITY_REPORT ───────────────────────────────
    private Response handleGetTeacherActivityReport() {
        try {
            DatabaseConnection.getInstance();
            ReportsDAO dao = new ReportsDAO();
            List<TeacherActivityStat> stats = dao.getTeacherActivity();
            return new Response(true, "Teacher activity report loaded.", stats);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Could not load teacher activity report: " + e.getMessage(), null);
        }
    }

    // ── GET_TEACHER_EXAM_PERFORMANCE ──────────────────────────────
    private Response handleGetTeacherExamPerformance(Object data) {
        if (!(data instanceof Integer)) {
            return new Response(false, "Invalid teacher ID.", null);
        }
        int teacherId = (Integer) data;
        try {
            DatabaseConnection.getInstance();
            ReportsDAO dao = new ReportsDAO();
            List<TeacherExamPerformance> points = dao.getExamPerformanceByTeacher(teacherId);
            return new Response(true, "Teacher exam performance loaded.", points);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Could not load teacher exam performance: " + e.getMessage(), null);
        }
    }

    // ── UPDATE_PROFILE ────────────────────────────────────────────────────────
    private Response handleUpdateProfile(Object data) {
        if (!(data instanceof User)) {
            return new Response(false, "Invalid profile data.", null);
        }
        User u = (User) data;
        u.setFullName(InputValidator.clean(u.getFullName(), 100));
        u.setAvatarUrl(InputValidator.clean(u.getAvatarUrl(), 255));
        try {
            DatabaseConnection.getInstance();
            UserDAO dao = new UserDAO(DatabaseConnection.getConnection());
            User updated = dao.updateProfile(u.getUserId(), u.getFullName(), u.getAvatarUrl());
            if (updated == null) return new Response(false, "User not found.", null);
            return new Response(true, "Profile updated.", updated);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Could not update profile: " + e.getMessage(), null);
        }
    }

    // ── GET_USER_SETTINGS ─────────────────────────────────────────────────────
    private Response handleGetUserSettings(Object data) {
        if (!(data instanceof Integer)) {
            return new Response(false, "Invalid user ID.", null);
        }
        int userId = (Integer) data;
        try {
            DatabaseConnection.getInstance();
            UserSettingsDAO dao = new UserSettingsDAO();
            UserSettings settings = dao.getSettings(userId);
            return new Response(true, "Settings loaded.", settings);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Could not load settings: " + e.getMessage(), null);
        }
    }

    // ── UPDATE_USER_SETTINGS ──────────────────────────────────────────────────
    private Response handleUpdateUserSettings(Object data) {
        if (!(data instanceof UserSettings)) {
            return new Response(false, "Invalid settings data.", null);
        }
        UserSettings settings = (UserSettings) data;
        settings.setTheme(InputValidator.oneOf(settings.getTheme(),
                UserSettings.THEME_LILAC, UserSettings.THEMES));
        try {
            DatabaseConnection.getInstance();
            UserSettingsDAO dao = new UserSettingsDAO();
            UserSettings saved = dao.upsertSettings(settings);
            return new Response(true, "Settings saved.", saved);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Could not save settings: " + e.getMessage(), null);
        }
    }

    // ══ LEARNING BOT (spec 13 + 14) ═══════════════════════════════════════════
    //
    // Every handler below authorises from sessionUserId and never from an ID
    // in the payload. They all fail closed when sessionUserId is -1.
    //
    // KNOWN LIMITATION, deliberately not fixed here: sessionUserId is -1
    // after ClientController.verifyConnection() transparently rebuilds a
    // dropped socket, because it does not re-send LOGIN. Every check below
    // therefore refuses after a silent reconnect until the user logs in
    // again. That is the safe direction to fail, but it is a real rough edge
    // and it is separate work from this feature.

    /**
     * Confirms the session belongs to a student, and returns them.
     *
     * @return the student, or null when the caller is not one
     */
    private User loadSessionStudent(int sessionUserId) throws SQLException {
        User requester = loadSessionUser(sessionUserId);
        if (requester == null || !"STUDENT".equalsIgnoreCase(requester.getRole())) {
            return null;
        }
        return requester;
    }

    /**
     * Confirms the session is a teacher attached to a course.
     *
     * This is spec 13.3: the bot belongs to the course, so <i>any</i> teacher
     * of that course may edit it — not only whoever created it. Membership
     * comes from {@code course_teachers}, which is why that table had to
     * exist at all.
     *
     * @return null when the requester may proceed, otherwise the refusal
     */
    private Response requireTeacherOfCourse(int sessionUserId, int courseId, String what)
            throws SQLException {
        User requester = loadSessionUser(sessionUserId);
        if (requester == null) {
            return new Response(false, "You must be logged in to " + what + ".", null);
        }
        if (!"TEACHER".equalsIgnoreCase(requester.getRole())) {
            return new Response(false, "Only a teacher can " + what + ".", null);
        }
        CourseTeacherDAO ctDAO = new CourseTeacherDAO();
        if (!ctDAO.isTeacherOfCourse(sessionUserId, courseId)) {
            return new Response(false,
                    "You are not a teacher of this course, so you cannot " + what + ".", null);
        }
        return null;
    }

    /**
     * Whether a student is sitting an exam right now — the bot's lockout
     * (spec 14).
     *
     * Two independent checks, because neither is sufficient alone:
     * <ul>
     *   <li>the in-memory {@code examWatchers} registry is instant and needs
     *       no query, but it lives on a socket and a reconnect loses it;</li>
     *   <li>the open PENDING submission row survives a reconnect, a server
     *       restart and a client that has been modified to lie about what
     *       screen it is on.</li>
     * </ul>
     *
     * <b>Fails closed.</b> If the check itself throws, this returns true:
     * refusing the bot to a student who is not in an exam is an
     * inconvenience, while allowing it to one who is defeats the requirement.
     *
     * @param studentId student to check
     * @return true when they appear to be mid-exam, or when the check failed
     */
    private boolean isStudentInExam(int studentId) {
        if (studentId <= 0) {
            return true;
        }
        try {
            for (Set<ClientHandler> watchers : examWatchers.values()) {
                for (ClientHandler handler : watchers) {
                    if (handler.sessionUserId == studentId) {
                        return true;
                    }
                }
            }
            DatabaseConnection.getInstance();
            return new test_submissionsDAO().hasOpenSubmission(studentId);
        } catch (Exception e) {
            System.err.println("Exam-lockout check failed, refusing the bot: " + e.getMessage());
            return true;
        }
    }

    // ── GET_MY_COURSES ────────────────────────────────────────────────────
    /**
     * The courses the requesting teacher is attached to.
     *
     * Answered from the session, so it cannot be pointed at another teacher.
     * The bot editor and the generation screens populate their course pickers
     * from this rather than from GET_ALL_COURSES: offering a course the
     * server will refuse is a worse experience than not offering it.
     */
    private Response handleGetMyCourses(int sessionUserId) {
        try {
            DatabaseConnection.getInstance();
            User requester = loadSessionUser(sessionUserId);
            if (requester == null) {
                return new Response(false, "You must be logged in to view your courses.", null);
            }
            if (!"TEACHER".equalsIgnoreCase(requester.getRole())) {
                return new Response(false, "Only a teacher has assigned courses.", null);
            }
            List<Course> courses = new CourseTeacherDAO().getCoursesForTeacher(sessionUserId);
            return new Response(true, "Loaded " + courses.size() + " course(s).", courses);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Could not load your courses: " + e.getMessage(), null);
        }
    }

    // ── GET_STUDENT_BOTS ──────────────────────────────────────────────────
    private Response handleGetStudentBots(int sessionUserId) {
        try {
            DatabaseConnection.getInstance();
            User student = loadSessionStudent(sessionUserId);
            if (student == null) {
                return new Response(false,
                        "You must be logged in as a student to use the learning bots.", null);
            }
            // The student ID comes from the session, so the payload's copy of
            // it cannot be used to read another student's bot list.
            List<CourseBot> bots =
                    new CourseBotDAO().getAvailableBotsForStudent(sessionUserId);
            return new Response(true, "Loaded " + bots.size() + " bot(s).", bots);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Could not load your learning bots: " + e.getMessage(), null);
        }
    }

    // ── ASK_BOT ───────────────────────────────────────────────────────────
    /**
     * Answers one question, after four checks in a fixed order (spec 14).
     *
     * Each failure has its own message so a student can tell "the bot is
     * switched off" from "you are not in this course" from "finish your exam
     * first". None of the checks may be skipped, and all of them run BEFORE
     * any engine call — a refused request must never reach Anthropic.
     */
    private Response handleAskBot(Object data, int sessionUserId) {
        if (!(data instanceof BotAsk)) {
            return new Response(false, "Invalid bot question.", null);
        }
        BotAsk ask = (BotAsk) data;
        String question = InputValidator.clean(ask.getQuestionText(), 1000);
        if (question == null || question.isBlank()) {
            return new Response(false, "Type a question first.", null);
        }

        try {
            DatabaseConnection.getInstance();

            // 1. the caller is a student
            User student = loadSessionStudent(sessionUserId);
            if (student == null) {
                return new Response(false,
                        "You must be logged in as a student to ask a learning bot.", null);
            }

            // 2. the bot exists and is switched on
            CourseBotDAO botDAO = new CourseBotDAO();
            CourseBot bot = botDAO.getBotById(ask.getBotId());
            if (bot == null) {
                return new Response(false, "That learning bot does not exist.", null);
            }
            if (!bot.isAvailable()) {
                return new Response(false,
                        "This course's learning bot is currently unavailable.", null);
            }

            // 3. the student takes that course
            EnrollmentDAO enrollmentDAO = new EnrollmentDAO(DatabaseConnection.getConnection());
            if (!enrollmentDAO.isStudentEnrolled(sessionUserId, bot.getCourseId())) {
                return new Response(false,
                        "You are not enrolled in this bot's course.", null);
            }

            // 4. they are not sitting an exam
            if (isStudentInExam(sessionUserId)) {
                return new Response(false,
                        "The learning bot is not available while you are taking an exam.", null);
            }

            // Only the question and the teacher's material go to the engine.
            // No name, username, national ID or user ID is ever sent.
            List<BotSource> sources = new BotSourceDAO().getSourcesForBot(bot.getId());
            BotEngineFactory.Outcome outcome = BotEngineFactory.answer(question, sources);

            BotQuestion exchange = new BotQuestion();
            exchange.setBotId(bot.getId());
            exchange.setStudentId(sessionUserId);
            exchange.setQuestionText(question);
            exchange.setAnswerText(outcome.getAnswer());
            exchange.setEngineUsed(outcome.getEngineUsed());

            BotQuestion logged = new BotQuestionDAO().logQuestion(exchange);
            return new Response(true, "Answered.", logged);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "The learning bot could not answer: " + e.getMessage(), null);
        }
    }

    // ── GET_MY_BOT_HISTORY ────────────────────────────────────────────────
    private Response handleGetMyBotHistory(Object data, int sessionUserId) {
        if (!(data instanceof Integer)) {
            return new Response(false, "Invalid bot ID.", null);
        }
        int botId = (Integer) data;
        try {
            DatabaseConnection.getInstance();
            User student = loadSessionStudent(sessionUserId);
            if (student == null) {
                return new Response(false,
                        "You must be logged in as a student to view your bot history.", null);
            }
            // Keyed on the session's own ID, so "my history" cannot be
            // pointed at somebody else's.
            List<BotQuestion> history =
                    new BotQuestionDAO().getHistoryForStudent(sessionUserId, botId);
            return new Response(true, "Loaded " + history.size() + " question(s).", history);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Could not load your bot history: " + e.getMessage(), null);
        }
    }

    // ── GET_COURSE_BOT ────────────────────────────────────────────────────
    /**
     * The full bot record for a course, sources included.
     *
     * Teacher-only, and only for a teacher of that course: the sources are
     * the material answers are drawn from, and handing them to a student
     * would hand them the answer key to their own bot.
     */
    private Response handleGetCourseBot(Object data, int sessionUserId) {
        if (!(data instanceof Integer)) {
            return new Response(false, "Invalid course ID.", null);
        }
        int courseId = (Integer) data;
        try {
            DatabaseConnection.getInstance();
            Response denial = requireTeacherOfCourse(sessionUserId, courseId,
                    "edit this course's learning bot");
            if (denial != null) return denial;

            CourseBotDAO botDAO = new CourseBotDAO();
            CourseBot bot = botDAO.getBotByCourseId(courseId);
            if (bot == null) {
                // No bot yet is not an error — it is the empty editor. An
                // unsaved shell is returned so the screen has something to
                // bind to, and SAVE_BOT creates the row on first save.
                bot = new CourseBot(0, courseId, "", false);
                CourseDAO courseDAO = new CourseDAO();
                Course course = courseDAO.getCourseById(courseId);
                bot.setCourseName(course != null ? course.getCourseName() : "");
                return new Response(true, "No bot has been created for this course yet.", bot);
            }
            bot.setSources(new BotSourceDAO().getSourcesForBot(bot.getId()));
            return new Response(true, "Bot loaded.", bot);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Could not load the course bot: " + e.getMessage(), null);
        }
    }

    // ── SAVE_BOT ──────────────────────────────────────────────────────────
    private Response handleSaveBot(Object data, int sessionUserId) {
        if (!(data instanceof CourseBot)) {
            return new Response(false, "Invalid bot data.", null);
        }
        CourseBot bot = (CourseBot) data;
        bot.setBotName(InputValidator.clean(bot.getBotName(), 100));
        if (bot.getBotName() == null || bot.getBotName().isBlank()) {
            return new Response(false, "Give the bot a name.", null);
        }
        try {
            DatabaseConnection.getInstance();
            Response denial = requireTeacherOfCourse(sessionUserId, bot.getCourseId(),
                    "edit this course's learning bot");
            if (denial != null) return denial;

            CourseBot saved = new CourseBotDAO().saveBot(bot, sessionUserId);
            if (saved == null) {
                return new Response(false, "The bot could not be saved.", null);
            }
            saved.setSources(new BotSourceDAO().getSourcesForBot(saved.getId()));
            return new Response(true, "Bot saved.", saved);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Could not save the bot: " + e.getMessage(), null);
        }
    }

    // ── SAVE_BOT_SOURCE ───────────────────────────────────────────────────
    private Response handleSaveBotSource(Object data, int sessionUserId) {
        if (!(data instanceof BotSource)) {
            return new Response(false, "Invalid source data.", null);
        }
        BotSource source = (BotSource) data;
        source.setTitle(InputValidator.clean(source.getTitle(), 200));
        source.setContent(InputValidator.clean(source.getContent(), 60000));
        if (source.getTitle() == null || source.getTitle().isBlank()) {
            return new Response(false, "Give the source a title.", null);
        }
        if (source.getContent() == null || source.getContent().isBlank()) {
            return new Response(false, "A source needs some content.", null);
        }
        try {
            DatabaseConnection.getInstance();
            CourseBotDAO botDAO = new CourseBotDAO();

            // An edit identifies its bot through the stored source, not
            // through the payload: otherwise a teacher of course A could
            // rewrite a source belonging to course B by sending its ID with
            // their own botId attached.
            int botId = source.getBotId();
            if (source.getId() > 0) {
                BotSource existing = new BotSourceDAO().getSourceById(source.getId());
                if (existing == null) {
                    return new Response(false, "That source no longer exists.", null);
                }
                botId = existing.getBotId();
                source.setBotId(botId);
            }

            CourseBot bot = botDAO.getBotById(botId);
            if (bot == null) {
                return new Response(false,
                        "Create the bot before adding material to it.", null);
            }
            Response denial = requireTeacherOfCourse(sessionUserId, bot.getCourseId(),
                    "edit this course's learning bot");
            if (denial != null) return denial;

            BotSource saved = new BotSourceDAO().saveSource(source, sessionUserId);
            return new Response(true, "Source saved.", saved);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Could not save the source: " + e.getMessage(), null);
        }
    }

    // ── DELETE_BOT_SOURCE ─────────────────────────────────────────────────
    private Response handleDeleteBotSource(Object data, int sessionUserId) {
        if (!(data instanceof Integer)) {
            return new Response(false, "Invalid source ID.", null);
        }
        int sourceId = (Integer) data;
        try {
            DatabaseConnection.getInstance();
            BotSourceDAO sourceDAO = new BotSourceDAO();
            BotSource existing = sourceDAO.getSourceById(sourceId);
            if (existing == null) {
                return new Response(false, "That source no longer exists.", null);
            }
            CourseBot bot = new CourseBotDAO().getBotById(existing.getBotId());
            if (bot == null) {
                return new Response(false, "That source's bot no longer exists.", null);
            }
            Response denial = requireTeacherOfCourse(sessionUserId, bot.getCourseId(),
                    "edit this course's learning bot");
            if (denial != null) return denial;

            if (!sourceDAO.deleteSource(sourceId)) {
                return new Response(false, "The source could not be deleted.", null);
            }
            return new Response(true, "Source deleted.", sourceId);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Could not delete the source: " + e.getMessage(), null);
        }
    }

    // ── GET_BOT_HISTORY_ANONYMOUS ─────────────────────────────────────────
    /**
     * The teacher's view of what students have been asking (spec 14.3).
     *
     * The anonymisation is done by the DAO's SELECT list, not here and not in
     * the UI — see {@code BotQuestionDAO.getAnonymousHistoryForBot}.
     */
    private Response handleGetBotHistoryAnonymous(Object data, int sessionUserId) {
        if (!(data instanceof Integer)) {
            return new Response(false, "Invalid bot ID.", null);
        }
        int botId = (Integer) data;
        try {
            DatabaseConnection.getInstance();
            CourseBot bot = new CourseBotDAO().getBotById(botId);
            if (bot == null) {
                return new Response(false, "That learning bot does not exist.", null);
            }
            Response denial = requireTeacherOfCourse(sessionUserId, bot.getCourseId(),
                    "view this course's bot history");
            if (denial != null) return denial;

            List<BotQuestion> history = new BotQuestionDAO().getAnonymousHistoryForBot(botId);
            return new Response(true, "Loaded " + history.size() + " question(s).", history);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Could not load the bot history: " + e.getMessage(), null);
        }
    }

    // ── GET_ENGINE_STATUS ─────────────────────────────────────────────────
    /**
     * Reports what the engine can do, so the client can render degraded mode
     * correctly instead of discovering it on a failed click.
     *
     * Carries no secret — whether a key is configured, never its value.
     */
    private Response handleGetEngineStatus(int sessionUserId) {
        try {
            DatabaseConnection.getInstance();
            if (loadSessionUser(sessionUserId) == null) {
                return new Response(false, "You must be logged in.", null);
            }
            EngineStatus status = new EngineStatus(
                    BotEngineFactory.isGenerationAvailable(),
                    BotEngineFactory.getEngine().name(),
                    BotEngineFactory.getConfiguredModel());
            return new Response(true, status.describe(), status);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Could not read the engine status: " + e.getMessage(), null);
        }
    }

    // ── GENERATE_QUESTIONS ────────────────────────────────────────────────
    /**
     * Drafts questions for a teacher to review. Nothing is saved here —
     * SAVE_GENERATED_QUESTIONS is a separate action a human triggers.
     */
    private Response handleGenerateQuestions(Object data, int sessionUserId) {
        if (!(data instanceof GenerationRequest)) {
            return new Response(false, "Invalid generation request.", null);
        }
        GenerationRequest req = (GenerationRequest) data;
        Response invalid = validateGenerationRequest(req);
        if (invalid != null) return invalid;

        try {
            DatabaseConnection.getInstance();
            Response denial = requireTeacherOfCourse(sessionUserId, req.getCourseId(),
                    "generate questions for this course");
            if (denial != null) return denial;

            List<Question> drafts = generateDrafts(req, sessionUserId);
            return new Response(true,
                    "Generated " + drafts.size() + " draft question(s). "
                            + "Review them before saving.", drafts);
        } catch (UnsupportedOperationException e) {
            return new Response(false, e.getMessage(), null);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Could not generate questions: " + e.getMessage(), null);
        }
    }

    // ── GENERATE_EXAM ─────────────────────────────────────────────────────
    /**
     * Drafts a whole paper for a teacher to review.
     *
     * The exam comes back with {@code examId == 0} and is not persisted: the
     * teacher accepts it into the existing BuildExamView and saves through
     * the normal authoring path, so a generated exam goes through exactly the
     * same validation and the same principal approval as a hand-written one.
     *
     * The points are distributed here rather than left to the model, because
     * the 100-point rule is the client's and the server's, not Claude's.
     */
    private Response handleGenerateExam(Object data, int sessionUserId) {
        if (!(data instanceof GenerationRequest)) {
            return new Response(false, "Invalid generation request.", null);
        }
        GenerationRequest req = (GenerationRequest) data;
        Response invalid = validateGenerationRequest(req);
        if (invalid != null) return invalid;
        if (req.getDurationMinutes() <= 0) {
            return new Response(false, "Set an exam duration greater than zero.", null);
        }

        try {
            DatabaseConnection.getInstance();
            Response denial = requireTeacherOfCourse(sessionUserId, req.getCourseId(),
                    "generate exams for this course");
            if (denial != null) return denial;

            List<Question> drafts = generateDrafts(req, sessionUserId);

            CourseDAO courseDAO = new CourseDAO();
            Course course = courseDAO.getCourseById(req.getCourseId());

            Exam draft = new Exam();
            draft.setExamId(0);                      // unsaved — this is a draft
            draft.setTitle(req.getTopic() + " exam");
            draft.setCourse(course != null ? course.getCourseName() : "");
            draft.setInstructions("Answer all questions. "
                    + req.getDurationMinutes() + " minutes allowed.");
            draft.setDurationMinutes(req.getDurationMinutes());
            draft.setTeacherId(sessionUserId);
            draft.setApprovalStatus("PENDING");
            draft.setExamQuestions(distributePoints(drafts));
            draft.setQuestionCount(drafts.size());

            return new Response(true,
                    "Generated a draft exam with " + drafts.size() + " question(s), "
                            + "worth 100 points in total. Review it before saving.", draft);
        } catch (UnsupportedOperationException e) {
            return new Response(false, e.getMessage(), null);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Could not generate the exam: " + e.getMessage(), null);
        }
    }

    // ── SAVE_GENERATED_QUESTIONS ──────────────────────────────────────────
    /**
     * Persists the drafts a teacher kept, possibly after editing them.
     *
     * Every incoming question goes through the same {@code sanitize(Question)}
     * as a hand-written one. Generated text that has been round-tripped
     * through a client is untrusted input twice over — once because a model
     * wrote it, and once because a client sent it.
     */
    private Response handleSaveGeneratedQuestions(Object data, int sessionUserId) {
        if (!(data instanceof List)) {
            return new Response(false, "Invalid question list.", null);
        }
        List<?> incoming = (List<?>) data;
        if (incoming.isEmpty()) {
            return new Response(false, "No questions were selected to save.", null);
        }

        try {
            DatabaseConnection.getInstance();
            QUESTIONSDAO dao = new QUESTIONSDAO();
            CourseTeacherDAO ctDAO = new CourseTeacherDAO();

            User requester = loadSessionUser(sessionUserId);
            if (requester == null) {
                return new Response(false, "You must be logged in to save questions.", null);
            }
            if (!"TEACHER".equalsIgnoreCase(requester.getRole())) {
                return new Response(false, "Only a teacher can save questions.", null);
            }

            // TWO PASSES, and the split matters. Validating and inserting in
            // one loop means a bad question at position N leaves 0..N-1
            // already committed while the refusal says "Nothing was saved" —
            // a message that is simply false, and the teacher then has no
            // idea what is in their bank. Everything is checked first;
            // nothing is written until all of it passes.
            //
            // A transaction would be the other way to get this, but every DAO
            // shares one process-wide Connection, so setAutoCommit(false)
            // here would sweep other client threads' statements into this
            // transaction (see DatabaseConnection.openDedicatedConnection).
            // Validating up front avoids needing one: past this point the
            // only remaining failure is the database itself going away.
            List<Question> toSave = new ArrayList<>();
            for (Object element : incoming) {
                if (!(element instanceof Question)) {
                    return new Response(false, "Invalid question in the list. Nothing was saved.",
                            null);
                }
                Question q = (Question) element;

                // Course ownership is re-checked per question: the list is
                // client-supplied, so a single request could otherwise mix in
                // a question aimed at a course this teacher does not teach.
                if (!ctDAO.isTeacherOfCourse(sessionUserId, q.getCourseId())) {
                    return new Response(false,
                            "You are not a teacher of the course one of these questions "
                                    + "belongs to. Nothing was saved.", null);
                }

                sanitize(q);
                q.setDifficultyLevel(InputValidator.oneOf(q.getDifficultyLevel(), null,
                        GenerationRequest.DIFFICULTY_EASY,
                        GenerationRequest.DIFFICULTY_MEDIUM,
                        GenerationRequest.DIFFICULTY_HARD));
                q.setTopic(InputValidator.clean(q.getTopic(), 100));
                q.setTeacherId(sessionUserId);

                if (q.getQuestionText() == null || q.getQuestionText().isBlank()) {
                    return new Response(false,
                            "One of these questions has no text. Nothing was saved.", null);
                }
                toSave.add(q);
            }

            List<Question> saved = new ArrayList<>();
            for (Question q : toSave) {
                int id = dao.insertQuestion(q);
                Question stored = dao.getQuestionById(id);
                if (stored != null) saved.add(stored);
            }
            return new Response(true, "Saved " + saved.size() + " question(s).", saved);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Could not save the questions: " + e.getMessage(), null);
        }
    }

    /** Shared validation for both generation actions. */
    private Response validateGenerationRequest(GenerationRequest req) {
        String topic = InputValidator.clean(req.getTopic(), 100);
        if (topic == null || topic.isBlank()) {
            return new Response(false, "Enter a topic to generate from.", null);
        }
        req.setTopic(topic);
        if (req.getCount() < 1 || req.getCount() > GenerationRequest.MAX_COUNT) {
            return new Response(false,
                    "Ask for between 1 and " + GenerationRequest.MAX_COUNT + " questions.", null);
        }
        req.setDifficulty(InputValidator.oneOf(req.getDifficulty(),
                GenerationRequest.DIFFICULTY_MEDIUM,
                GenerationRequest.DIFFICULTY_EASY,
                GenerationRequest.DIFFICULTY_MEDIUM,
                GenerationRequest.DIFFICULTY_HARD));
        return null;
    }

    /**
     * Runs the engine and sanitises what comes back, so a draft is already
     * clean by the time it reaches the teacher's screen.
     */
    private List<Question> generateDrafts(GenerationRequest req, int teacherId) throws Exception {
        List<BotSource> sources = new ArrayList<>();
        CourseBot bot = new CourseBotDAO().getBotByCourseId(req.getCourseId());
        if (bot != null) {
            sources = new BotSourceDAO().getSourcesForBot(bot.getId());
        }

        List<Question> drafts = BotEngineFactory.generate(
                req.getTopic(), req.getDifficulty(), req.getCount(),
                req.getCourseId(), sources);

        for (Question q : drafts) {
            sanitize(q);
            q.setTopic(InputValidator.clean(q.getTopic(), 100));
            q.setTeacherId(teacherId);
            q.setId(0);   // a draft has no identity until a teacher saves it
        }
        return drafts;
    }

    /**
     * Spreads exactly 100 points across the drafted questions.
     *
     * Integer division leaves a remainder that has to go somewhere, so the
     * first {@code 100 % n} questions carry one extra point — three questions
     * become 34/33/33, not 33/33/33 with four points quietly lost. The exam
     * builder validates the total again, and would refuse anything else.
     */
    private List<ExamQuestion> distributePoints(List<Question> questions) {
        List<ExamQuestion> examQuestions = new ArrayList<>();
        int n = questions.size();
        if (n == 0) return examQuestions;
        int base = 100 / n;
        int remainder = 100 % n;
        for (int i = 0; i < n; i++) {
            int points = base + (i < remainder ? 1 : 0);
            examQuestions.add(new ExamQuestion(questions.get(i), points));
        }
        return examQuestions;
    }

    // ── INPUT HARDENING (defense-in-depth; DAOs already parameterize) ──────────
    /** Cleans and length-caps the free-text fields of an incoming question. */
    private void sanitize(Question q) {
        if (q == null) return;
        q.setQuestionText(InputValidator.clean(q.getQuestionText(), 1000));
        q.setOptionA(InputValidator.clean(q.getOptionA(), 255));
        q.setOptionB(InputValidator.clean(q.getOptionB(), 255));
        q.setOptionC(InputValidator.clean(q.getOptionC(), 255));
        q.setOptionD(InputValidator.clean(q.getOptionD(), 255));
        q.setCorrectAnswer(InputValidator.oneOf(q.getCorrectAnswer(), "A", "A", "B", "C", "D"));
        q.setVisualAidUrl(InputValidator.clean(q.getVisualAidUrl(), 255));
    }

    /** Cleans and length-caps the free-text fields of an incoming exam. */
    private void sanitize(Exam e) {
        if (e == null) return;
        e.setTitle(InputValidator.clean(e.getTitle(), 255));
        e.setInstructions(InputValidator.clean(e.getInstructions(), 1000));
        e.setCourse(InputValidator.clean(e.getCourse(), 100));
    }

    // ── UTILITIES ─────────────────────────────────────────────────────────────
    private String generateTestCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder code = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 6; i++) {
            code.append(chars.charAt(random.nextInt(chars.length())));
        }
        return code.toString();
    }

    // ── CLIENT HANDLER ────────────────────────────────────────────────────────
    private class ClientHandler implements Runnable {
        private final Socket socket;
        /** ID of the user logged in on this socket; -1 when no session is active. */
        private int sessionUserId = -1;

        /**
         * Exam this socket is currently sitting, or -1. Mirrors this handler's
         * membership of {@link #examWatchers} so leaving is a lookup, not a scan.
         */
        private int activeExamId = -1;

        /**
         * The socket's output stream, held as a field rather than a local of
         * {@code run()} so the push path can write to it from another thread.
         * Only ever written through {@link #sendResponse(Response)}.
         */
        private ObjectOutputStream out;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
                 ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

                this.out = output;

                Object obj;
                while ((obj = in.readObject()) != null) {
                    if (!(obj instanceof Request)) {
                        sendResponse(new Response(false, "Unsupported message type.", null));
                        continue;
                    }
                    Request req = (Request) obj;
                    Response resp;
                    if (RequestType.LOGIN.equals(req.getAction())) {
                        resp = doLogin(req.getData());
                    } else if (RequestType.LOGOUT.equals(req.getAction())) {
                        resp = doLogout();
                    } else {
                        // The session's own user ID travels with the request so
                        // authorisation never has to trust an ID in the payload.
                        resp = handleMessageFromClient(req, sessionUserId);
                    }
                    // Stamp the originating action so the client can route this
                    // response without inferring intent from the payload type
                    // or the current screen. Central on purpose: no individual
                    // handler has to remember to set it. (A pushed response is
                    // stamped by the pusher instead — it answers no request.)
                    if (resp != null) {
                        resp.setAction(req.getAction());
                    }
                    // Exam-registry bookkeeping is socket-scoped, like the
                    // logged-in set: this handler owns the socket the push has
                    // to reach, so it is the thing that gets registered.
                    trackExamMembership(req, resp);
                    sendResponse(resp);
                }

            } catch (IOException | ClassNotFoundException e) {
                // client disconnected — normal
            } finally {
                // Release the session slot whether the client logged out cleanly or crashed.
                if (sessionUserId != -1) {
                    loggedInUserIds.remove(sessionUserId);
                }
                // ...and stop pushing to a socket that is going away.
                leaveExam();
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        /**
         * Writes one response to this client.
         *
         * Synchronized because two threads can reach the same stream: the
         * socket's own request loop, and a teacher's thread pushing an
         * extension. An ObjectOutputStream written by two threads at once
         * corrupts the stream for good.
         *
         * The {@code reset()} matters as much as the lock: without it the
         * stream's back-reference cache would re-send an earlier object's
         * handle instead of the object's current state.
         *
         * @param response response to write; null is ignored
         * @throws IOException if the socket is gone
         */
        synchronized void sendResponse(Response response) throws IOException {
            if (out == null || response == null) {
                return;
            }
            out.writeObject(response);
            out.reset();
        }

        /**
         * Keeps this handler's exam-registry membership in step with what the
         * client just did: entering an exam joins, submitting or logging out
         * leaves. Only successful responses count — a refused entry attempt
         * must not subscribe anyone.
         *
         * @param req the request just handled
         * @param resp the response about to be sent
         */
        private void trackExamMembership(Request req, Response resp) {

            if (resp == null || !resp.isSuccess()) {
                return;
            }

            String action = req.getAction();

            if (RequestType.START_EXAM_BY_CODE.equals(action)
                    && resp.getData() instanceof Exam exam) {
                joinExam(exam.getExamId());
            } else if (RequestType.SUBMIT_EXAM.equals(action)
                    || RequestType.LOGOUT.equals(action)) {
                leaveExam();
            }
        }

        /**
         * Subscribes this handler to an exam's push list.
         *
         * @param examId exam being entered
         */
        private void joinExam(int examId) {
            // Never be in two exams at once: entering one leaves the last.
            leaveExam();
            activeExamId = examId;
            examWatchers
                    .computeIfAbsent(examId,
                            id -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
                    .add(this);
        }

        /**
         * Unsubscribes this handler. Safe to call when it is not subscribed.
         *
         * The removal goes through {@code computeIfPresent} so that emptying
         * the set and dropping the map entry happen as one step — otherwise a
         * handler joining the same exam at that moment could be discarded
         * along with the empty set.
         */
        private void leaveExam() {
            if (activeExamId == -1) {
                return;
            }
            examWatchers.computeIfPresent(activeExamId, (id, watchers) -> {
                watchers.remove(this);
                return watchers.isEmpty() ? null : watchers;
            });
            activeExamId = -1;
        }

        private Response doLogin(Object data) {
            if (!(data instanceof User)) {
                return new Response(false, "Invalid login data.", null);
            }
            User loginUser = (User) data;
            try {
                DatabaseConnection.getInstance();
                UserDAO dao = new UserDAO(DatabaseConnection.getConnection());
                String username = InputValidator.clean(loginUser.getUsername(), 50);
                User authenticated = dao.authenticateUser(username, loginUser.getPassword());
                if (authenticated == null) {
                    return new Response(false, "Invalid credentials.", null);
                }
                // add() returns false when the ID is already present → already logged in
                if (!loggedInUserIds.add(authenticated.getUserId())) {
                    return new Response(false,
                            "This account is already logged in on another device.", null);
                }
                sessionUserId = authenticated.getUserId();
                return new Response(true, "Login successful.", authenticated);
            } catch (Exception e) {
                e.printStackTrace();
                return new Response(false, "Server error during login: " + e.getMessage(), null);
            }
        }

        private Response doLogout() {
            if (sessionUserId != -1) {
                loggedInUserIds.remove(sessionUserId);
                sessionUserId = -1;
            }
            return new Response(true, "Logged out successfully.", null);
        }
    }
}