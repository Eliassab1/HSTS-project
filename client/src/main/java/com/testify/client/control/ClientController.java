package com.testify.client.control;

import com.testify.common.Exam;
import com.testify.common.ExamExtension;
import com.testify.common.ExamResultsQuery;
import com.testify.common.ExamEntryRequest;
import com.testify.common.ExamSubmission;
import com.testify.common.GradeOverride;
import com.testify.common.Question;
import com.testify.common.BotAsk;
import com.testify.common.GenerationRequest;
import com.testify.common.BotSource;
import com.testify.common.CourseBot;
import com.testify.common.Request;
import com.testify.common.RequestType;
import com.testify.common.Response;
import com.testify.common.User;
import com.testify.common.UserSettings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Controls the communication operations of the HSTS client.
 *
 * This class connects the graphical interface to the OCSF
 * communication class and creates the requests sent to the server.
 */
public class ClientController {

    /**
     * OCSF client communication object.
     */
    private TestifyClient client;

    // Remembered connection parameters, used to transparently reconnect if the
    // socket drops mid-session. Every request other than LOGIN/LOGOUT is
    // self-contained (it carries its own IDs), so a silent reconnect restores
    // functionality without re-authenticating.
    private String lastHost;
    private int lastPort;
    private Consumer<Response> lastResponseHandler;

    /**
     * Connects the client to the HSTS server.
     *
     * @param host server address
     * @param port server port
     * @param responseHandler function that handles server responses
     * @throws IOException if the connection cannot be opened
     */
    public void connect(
            String host,
            int port,
            Consumer<Response> responseHandler
    ) throws IOException {

        // Remember the parameters so verifyConnection() can silently reconnect
        // if the socket is dropped later in the session.
        this.lastHost = host;
        this.lastPort = port;
        this.lastResponseHandler = responseHandler;

        if (client != null && client.isConnected()) {
            return;
        }

        client = new TestifyClient(
                host,
                port
        );

        client.setResponseHandler(
                responseHandler
        );

        client.connectToServer();
    }

    /**
     * Sends a login request to the server.
     *
     * @param username username entered by the user
     * @param password password entered by the user
     * @throws IOException if the request cannot be sent
     */
    public void requestLogin(
            String username,
            String password
    ) throws IOException {

        if (username == null
                || username.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Username cannot be empty."
            );
        }

        if (password == null
                || password.isEmpty()) {

            throw new IllegalArgumentException(
                    "Password cannot be empty."
            );
        }

        verifyConnection();

        User loginUser = new User(
                0,
                username.trim(),
                password,
                null,
                null
        );

        Request request = new Request(
                RequestType.LOGIN,
                loginUser
        );

        client.sendMessageToServer(
                request
        );
    }

    /**
     * Requests all questions from the server.
     *
     * @throws IOException if the request cannot be sent
     */
    public void requestAllQuestions()
            throws IOException {

        verifyConnection();

        Request request = new Request(
                RequestType.GET_ALL_QUESTIONS,
                null
        );

        client.sendMessageToServer(
                request
        );
    }

    /**
     * Sends a new question to the server for creation.
     *
     * @param question new question to create
     * @throws IOException if the request cannot be sent
     */
    public void requestCreateQuestion(
            Question question
    ) throws IOException {

        if (question == null) {

            throw new IllegalArgumentException(
                    "Question cannot be null."
            );
        }

        verifyConnection();

        Request request = new Request(
                RequestType.CREATE_QUESTION,
                question
        );

        client.sendMessageToServer(
                request
        );
    }

    /**
     * Sends a request to delete a question by its identifier.
     *
     * @param questionId identifier of the question to delete
     * @throws IOException if the request cannot be sent
     */
    public void requestDeleteQuestion(
            int questionId
    ) throws IOException {

        if (questionId <= 0) {

            throw new IllegalArgumentException(
                    "Question ID must be valid."
            );
        }

        verifyConnection();

        Request request = new Request(
                RequestType.DELETE_QUESTION,
                questionId
        );

        client.sendMessageToServer(
                request
        );
    }

    /**
     * Sends an updated question to the server.
     *
     * @param question updated question
     * @throws IOException if the request cannot be sent
     */
    public void requestQuestionUpdate(
            Question question
    ) throws IOException {

        if (question == null) {

            throw new IllegalArgumentException(
                    "Question cannot be null."
            );
        }

        verifyConnection();

        Request request = new Request(
                RequestType.UPDATE_QUESTION,
                question
        );

        client.sendMessageToServer(
                request
        );
    }

    /**
     * Sends a request for creating a new exam.
     *
     * @param exam exam created by the teacher
     * @throws IOException if the request cannot be sent
     */
    public void requestCreateExam(
            Exam exam
    ) throws IOException {

        if (exam == null) {

            throw new IllegalArgumentException(
                    "Exam cannot be null."
            );
        }

        if (exam.getTitle() == null
                || exam.getTitle().trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Exam title cannot be empty."
            );
        }

        if (exam.getCourse() == null
                || exam.getCourse().trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Exam course cannot be empty."
            );
        }

        if (exam.getDurationMinutes() <= 0) {

            throw new IllegalArgumentException(
                    "Exam duration must be greater than zero."
            );
        }

        if (exam.getExamQuestions() == null
                || exam.getExamQuestions().isEmpty()) {

            throw new IllegalArgumentException(
                    "The exam must contain at least one question."
            );
        }

        if (exam.calculateTotalPoints() != 100) {

            throw new IllegalArgumentException(
                    "The total exam points must equal 100."
            );
        }

        verifyConnection();

        Request request = new Request(
                RequestType.CREATE_EXAM,
                exam
        );

        client.sendMessageToServer(
                request
        );
    }

    /**
     * Sends an edited exam back to the server (spec 3.5).
     *
     * Validated exactly like {@link #requestCreateExam(Exam)}, plus the exam
     * identifier, since an update names the row it replaces. The server
     * re-checks all of it and additionally refuses the edit if any student
     * has already sat the exam.
     *
     * @param exam edited exam, carrying the identifier of the exam it replaces
     * @throws IOException if the request cannot be sent
     */
    public void requestUpdateExam(
            Exam exam
    ) throws IOException {

        if (exam == null) {

            throw new IllegalArgumentException(
                    "Exam cannot be null."
            );
        }

        if (exam.getExamId() <= 0) {

            throw new IllegalArgumentException(
                    "Exam ID must be valid."
            );
        }

        if (exam.getTitle() == null
                || exam.getTitle().trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Exam title cannot be empty."
            );
        }

        if (exam.getCourse() == null
                || exam.getCourse().trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Exam course cannot be empty."
            );
        }

        if (exam.getDurationMinutes() <= 0) {

            throw new IllegalArgumentException(
                    "Exam duration must be greater than zero."
            );
        }

        if (exam.getExamQuestions() == null
                || exam.getExamQuestions().isEmpty()) {

            throw new IllegalArgumentException(
                    "The exam must contain at least one question."
            );
        }

        if (exam.calculateTotalPoints() != 100) {

            throw new IllegalArgumentException(
                    "The total exam points must equal 100."
            );
        }

        verifyConnection();

        client.sendMessageToServer(
                new Request(RequestType.UPDATE_EXAM, exam)
        );
    }

    /**
     * Asks the server to add time to an exam that is already running
     * (spec 7).
     *
     * <p>The server replies to this teacher and, separately, pushes an
     * unsolicited {@code EXAM_DURATION_EXTENDED} response to every client
     * currently sitting the exam. Ownership is checked server-side.
     *
     * @param extension the exam and the minutes to add (1-120)
     * @throws IOException if the request cannot be sent
     */
    public void requestExtendExamDuration(
            ExamExtension extension
    ) throws IOException {

        if (extension == null) {

            throw new IllegalArgumentException(
                    "Extension cannot be null."
            );
        }

        if (extension.getExamId() <= 0) {

            throw new IllegalArgumentException(
                    "Exam ID must be valid."
            );
        }

        if (extension.getExtraMinutes() < 1
                || extension.getExtraMinutes() > 120) {

            throw new IllegalArgumentException(
                    "Extra time must be between 1 and 120 minutes."
            );
        }

        verifyConnection();

        client.sendMessageToServer(
                new Request(RequestType.EXTEND_EXAM_DURATION, extension)
        );
    }

    /**
     * Requests one exam's results across the students who sat it, optionally
     * narrowed to a single grade level (spec 10).
     * The server responds with a {@code List<StudentExamResult>}.
     *
     * <p>Ownership is decided server-side from the session: asking for an
     * exam the logged-in teacher does not own is refused, not answered.
     *
     * @param query the exam, and the grade level to filter to (null = all)
     * @throws IOException if the request cannot be sent
     */
    public void requestExamResults(
            ExamResultsQuery query
    ) throws IOException {

        if (query == null) {

            throw new IllegalArgumentException(
                    "Results query cannot be null."
            );
        }

        if (query.getExamId() <= 0) {

            throw new IllegalArgumentException(
                    "Exam ID must be valid."
            );
        }

        if (query.getGradeLevel() != null
                && (query.getGradeLevel() < 9
                || query.getGradeLevel() > 12)) {

            throw new IllegalArgumentException(
                    "Grade level must be between 9 and 12."
            );
        }

        verifyConnection();

        client.sendMessageToServer(
                new Request(RequestType.GET_EXAM_RESULTS, query)
        );
    }

    /**
     * Requests the archived earlier versions of a question (spec 2.2).
     * The server responds with a {@code List<Question>}, newest first.
     *
     * @param questionId identifier of the question
     * @throws IOException if the request cannot be sent
     */
    public void requestQuestionHistory(
            int questionId
    ) throws IOException {

        if (questionId <= 0) {

            throw new IllegalArgumentException(
                    "Question ID must be valid."
            );
        }

        verifyConnection();

        client.sendMessageToServer(
                new Request(RequestType.GET_QUESTION_HISTORY, questionId)
        );
    }

    /**
     * Asks the server to copy a question into a new bank row (spec 2.3).
     * The server responds with the new {@code Question}.
     *
     * @param questionId identifier of the question to copy
     * @throws IOException if the request cannot be sent
     */
    public void requestDuplicateQuestion(
            int questionId
    ) throws IOException {

        if (questionId <= 0) {

            throw new IllegalArgumentException(
                    "Question ID must be valid."
            );
        }

        verifyConnection();

        client.sendMessageToServer(
                new Request(RequestType.DUPLICATE_QUESTION, questionId)
        );
    }

    /**
     * Requests the exams available for a student.
     *
     * @param studentId identifier of the student
     * @throws IOException if the request cannot be sent
     */
    public void requestAvailableExams(
            int studentId
    ) throws IOException {

        if (studentId <= 0) {

            throw new IllegalArgumentException(
                    "Student ID must be valid."
            );
        }

        verifyConnection();

        Request request = new Request(
                RequestType.GET_AVAILABLE_EXAMS,
                studentId
        );

        client.sendMessageToServer(
                request
        );
    }

    /**
     * Requests a specific exam from the server.
     *
     * @param examId identifier of the exam
     * @throws IOException if the request cannot be sent
     */
    public void requestExamById(
            int examId
    ) throws IOException {

        if (examId <= 0) {

            throw new IllegalArgumentException(
                    "Exam ID must be valid."
            );
        }

        verifyConnection();

        Request request = new Request(
                RequestType.GET_EXAM_BY_ID,
                examId
        );

        client.sendMessageToServer(
                request
        );
    }

    /**
     * Sends a completed exam submission to the server.
     *
     * @param submission completed student exam
     * @throws IOException if the request cannot be sent
     */
    public void requestSubmitExam(
            ExamSubmission submission
    ) throws IOException {

        if (submission == null) {

            throw new IllegalArgumentException(
                    "Exam submission cannot be null."
            );
        }

        if (submission.getExamId() <= 0) {

            throw new IllegalArgumentException(
                    "Exam ID must be valid."
            );
        }

        if (submission.getStudentId() <= 0) {

            throw new IllegalArgumentException(
                    "Student ID must be valid."
            );
        }

        if (submission.getAnswers() == null
                || submission.getAnswers().isEmpty()) {

            throw new IllegalArgumentException(
                    "The exam submission must contain answers."
            );
        }

        verifyConnection();

        Request request = new Request(
                RequestType.SUBMIT_EXAM,
                submission
        );

        client.sendMessageToServer(
                request
        );
    }

    /**
     * Requests all exams created by a teacher.
     *
     * @param teacherId identifier of the teacher
     * @throws IOException if the request cannot be sent
     */
    public void requestTeacherExams(
            int teacherId
    ) throws IOException {

        if (teacherId <= 0) {
            throw new IllegalArgumentException("Teacher ID must be valid.");
        }

        verifyConnection();

        Request request = new Request(
                RequestType.GET_TEACHER_EXAMS,
                teacherId
        );

        client.sendMessageToServer(request);
    }

    /**
     * Requests deletion of an exam.
     *
     * @param examId identifier of the exam to delete
     * @throws IOException if the request cannot be sent
     */
    public void requestDeleteExam(
            int examId
    ) throws IOException {

        if (examId <= 0) {
            throw new IllegalArgumentException("Exam ID must be valid.");
        }

        verifyConnection();

        Request request = new Request(
                RequestType.DELETE_EXAM,
                examId
        );

        client.sendMessageToServer(request);
    }

    /**
     * Requests toggling the active status of an exam.
     *
     * @param examId identifier of the exam
     * @throws IOException if the request cannot be sent
     */
    public void requestToggleExamStatus(
            int examId
    ) throws IOException {

        if (examId <= 0) {
            throw new IllegalArgumentException("Exam ID must be valid.");
        }

        verifyConnection();

        Request request = new Request(
                RequestType.TOGGLE_EXAM_STATUS,
                examId
        );

        client.sendMessageToServer(request);
    }

    /**
     * Requests all results of a student.
     *
     * @param studentId identifier of the student
     * @throws IOException if the request cannot be sent
     */
    public void requestStudentResults(
            int studentId
    ) throws IOException {

        if (studentId <= 0) {

            throw new IllegalArgumentException(
                    "Student ID must be valid."
            );
        }

        verifyConnection();

        Request request = new Request(
                RequestType.GET_STUDENT_RESULTS,
                studentId
        );

        client.sendMessageToServer(
                request
        );
    }

    /**
     * Requests all exams pending principal approval.
     *
     * @throws IOException if the request cannot be sent
     */
    public void requestPendingExams() throws IOException {
        verifyConnection();
        client.sendMessageToServer(new Request(RequestType.GET_PENDING_EXAMS, null));
    }

    /**
     * Requests approval of an exam.
     *
     * @param examId identifier of the exam to approve
     * @throws IOException if the request cannot be sent
     */
    public void requestApproveExam(int examId) throws IOException {
        if (examId <= 0) throw new IllegalArgumentException("Exam ID must be valid.");
        verifyConnection();
        client.sendMessageToServer(new Request(RequestType.APPROVE_EXAM, examId));
    }

    /**
     * Requests rejection of an exam, with a reason the teacher will see.
     *
     * @param exam exam carrying examId and rejectionReason
     * @throws IOException if the request cannot be sent
     */
    public void requestRejectExam(Exam exam) throws IOException {
        if (exam == null || exam.getExamId() <= 0) {
            throw new IllegalArgumentException("Exam ID must be valid.");
        }
        if (exam.getRejectionReason() == null || exam.getRejectionReason().isBlank()) {
            throw new IllegalArgumentException("A rejection reason is required.");
        }
        verifyConnection();
        client.sendMessageToServer(new Request(RequestType.REJECT_EXAM, exam));
    }

    /**
     * Requests scheduling of an approved exam: an opening/closing window and
     * the 4-digit code students will enter to start it.
     *
     * @param exam exam carrying examId, openAt, closeAt and examCode
     * @throws IOException if the request cannot be sent
     */
    public void requestScheduleExam(Exam exam) throws IOException {
        if (exam == null || exam.getExamId() <= 0) {
            throw new IllegalArgumentException("Exam ID must be valid.");
        }
        if (exam.getExamCode() == null || !exam.getExamCode().matches("^[0-9]{4}$")) {
            throw new IllegalArgumentException("The exam code must be exactly 4 digits.");
        }
        if (exam.getOpenAt() == null || exam.getCloseAt() == null) {
            throw new IllegalArgumentException("Both an opening and closing date/time are required.");
        }
        if (!exam.getCloseAt().after(exam.getOpenAt())) {
            throw new IllegalArgumentException("The closing time must be after the opening time.");
        }
        verifyConnection();
        client.sendMessageToServer(new Request(RequestType.SCHEDULE_EXAM, exam));
    }

    /**
     * Requests entry into an exam using its 4-digit code and the student's
     * national ID (ת"ז).
     *
     * @param entry code + national ID + the logged-in student's ID
     * @throws IOException if the request cannot be sent
     */
    public void requestStartExamByCode(ExamEntryRequest entry) throws IOException {
        if (entry == null) {
            throw new IllegalArgumentException("Exam entry details cannot be null.");
        }
        if (entry.getExamCode() == null || !entry.getExamCode().matches("^[0-9]{4}$")) {
            throw new IllegalArgumentException("Enter a valid 4-digit exam code.");
        }
        if (entry.getNationalId() == null || entry.getNationalId().isBlank()) {
            throw new IllegalArgumentException("Enter your national ID.");
        }
        if (entry.getStudentId() <= 0) {
            throw new IllegalArgumentException("Student ID must be valid.");
        }
        verifyConnection();
        client.sendMessageToServer(new Request(RequestType.START_EXAM_BY_CODE, entry));
    }

    /**
     * Requests all courses from the server.
     *
     * @throws IOException if the request cannot be sent
     */
    public void requestAllCourses() throws IOException {
        verifyConnection();
        client.sendMessageToServer(new Request(RequestType.GET_ALL_COURSES, null));
    }

    /**
     * Requests the administrator's exam pass/fail report, grouped by course.
     * The server responds with a {@code List<PassFailStat>}.
     *
     * @throws IOException if the request cannot be sent
     */
    public void requestPassFailReport() throws IOException {
        verifyConnection();
        client.sendMessageToServer(new Request(RequestType.GET_PASS_FAIL_REPORT, null));
    }

    /**
     * Requests the administrator's average-score-over-time report.
     * The server responds with a {@code List<ScoreTrendPoint>}.
     *
     * @throws IOException if the request cannot be sent
     */
    public void requestScoreTrendReport() throws IOException {
        verifyConnection();
        client.sendMessageToServer(new Request(RequestType.GET_SCORE_TREND_REPORT, null));
    }

    /**
     * Requests the administrator's teacher activity report.
     * The server responds with a {@code List<TeacherActivityStat>}.
     *
     * @throws IOException if the request cannot be sent
     */
    public void requestTeacherActivityReport() throws IOException {
        verifyConnection();
        client.sendMessageToServer(new Request(RequestType.GET_TEACHER_ACTIVITY_REPORT, null));
    }

    /**
     * Requests the teacher's average-score-per-exam report for their
     * 10 most recently created exams.
     * The server responds with a {@code List<TeacherExamPerformance>}.
     *
     * @param teacherId identifier of the teacher
     * @throws IOException if the request cannot be sent
     */
    public void requestTeacherExamPerformance(int teacherId) throws IOException {
        if (teacherId <= 0) {
            throw new IllegalArgumentException("Teacher ID must be valid.");
        }
        verifyConnection();
        client.sendMessageToServer(new Request(RequestType.GET_TEACHER_EXAM_PERFORMANCE, teacherId));
    }

    /**
     * Requests every exam in the system, across all teachers.
     * The server responds with a {@code List<Exam>} of headers.
     *
     * <p>Principal-only: the server refuses this for any other role.
     *
     * @throws IOException if the request cannot be sent
     */
    public void requestAllExams() throws IOException {
        verifyConnection();
        client.sendMessageToServer(new Request(RequestType.GET_ALL_EXAMS, null));
    }

    /**
     * Requests every approved result in the system, across all students.
     * The server responds with a {@code List<ExamResult>}.
     *
     * <p>Principal-only. Grades still awaiting a teacher's approval are not
     * included — the principal sees what the students see.
     *
     * @throws IOException if the request cannot be sent
     */
    public void requestAllResults() throws IOException {
        verifyConnection();
        client.sendMessageToServer(new Request(RequestType.GET_ALL_RESULTS, null));
    }

    /**
     * Requests every user account. The server responds with a
     * {@code List<User>} and refuses anyone who is not the principal.
     *
     * @throws IOException if the request cannot be sent
     */
    public void requestAllUsers() throws IOException {
        verifyConnection();
        client.sendMessageToServer(new Request(RequestType.GET_ALL_USERS, null));
    }

    /**
     * Creates a user account. The password travels raw and is hashed by the
     * server before it is stored; it is never sent back.
     *
     * @param user the account to create
     * @throws IOException if the request cannot be sent
     */
    public void requestCreateUser(User user) throws IOException {
        if (user == null) {
            throw new IllegalArgumentException("User must not be null.");
        }
        verifyConnection();
        client.sendMessageToServer(new Request(RequestType.CREATE_USER, user));
    }

    /**
     * Updates a user account. A blank password means "keep the existing one" —
     * the principal is not asked to retype a password they do not know.
     *
     * @param user the account to update, carrying its own id
     * @throws IOException if the request cannot be sent
     */
    public void requestUpdateUser(User user) throws IOException {
        if (user == null || user.getUserId() <= 0) {
            throw new IllegalArgumentException("A valid user is required.");
        }
        verifyConnection();
        client.sendMessageToServer(new Request(RequestType.UPDATE_USER, user));
    }

    /**
     * Deletes a user account.
     *
     * @param userId the account to delete
     * @throws IOException if the request cannot be sent
     */
    public void requestDeleteUser(int userId) throws IOException {
        if (userId <= 0) {
            throw new IllegalArgumentException("User ID must be valid.");
        }
        verifyConnection();
        client.sendMessageToServer(new Request(RequestType.DELETE_USER, userId));
    }

    /**
     * Requests the grade distribution histogram in 10-point bands.
     * The server responds with a {@code List<GradeDistributionBucket>}.
     *
     * @param examId exam to restrict the histogram to, or {@code null} for
     *               every graded submission in the system
     * @throws IOException if the request cannot be sent
     */
    public void requestGradeDistribution(Integer examId) throws IOException {
        if (examId != null && examId <= 0) {
            throw new IllegalArgumentException("Exam ID must be valid.");
        }
        verifyConnection();
        client.sendMessageToServer(new Request(RequestType.GET_GRADE_DISTRIBUTION, examId));
    }

    /**
     * Requests per-exam statistics for one teacher — the "compare the exams
     * of the same teacher" report.
     * The server responds with a {@code List<ExamStatistics>}.
     *
     * @param teacherId identifier of the teacher
     * @throws IOException if the request cannot be sent
     */
    public void requestTeacherStatistics(int teacherId) throws IOException {
        if (teacherId <= 0) {
            throw new IllegalArgumentException("Teacher ID must be valid.");
        }
        verifyConnection();
        client.sendMessageToServer(new Request(RequestType.GET_TEACHER_STATISTICS, teacherId));
    }

    /**
     * Requests per-exam statistics for one course — the "compare the exams
     * of the same course" report.
     * The server responds with a {@code List<ExamStatistics>}.
     *
     * @param courseId identifier of the course
     * @throws IOException if the request cannot be sent
     */
    public void requestCourseStatistics(int courseId) throws IOException {
        if (courseId <= 0) {
            throw new IllegalArgumentException("Course ID must be valid.");
        }
        verifyConnection();
        client.sendMessageToServer(new Request(RequestType.GET_COURSE_STATISTICS, courseId));
    }

    /**
     * Requests per-exam statistics for one student — the "compare the exams
     * of the same student" report.
     * The server responds with a {@code List<ExamStatistics>}.
     *
     * @param studentId identifier of the student
     * @throws IOException if the request cannot be sent
     */
    public void requestStudentStatistics(int studentId) throws IOException {
        if (studentId <= 0) {
            throw new IllegalArgumentException("Student ID must be valid.");
        }
        verifyConnection();
        client.sendMessageToServer(new Request(RequestType.GET_STUDENT_STATISTICS, studentId));
    }

    /**
     * Requests the submissions awaiting the teacher's grade approval.
     * The server responds with a {@code List<PendingGrade>}.
     *
     * <p>The teacher ID is sent for completeness, but the server decides
     * whose queue to return from the logged-in session — a client cannot
     * ask for another teacher's pending grades.
     *
     * @param teacherId identifier of the teacher
     * @throws IOException if the request cannot be sent
     */
    public void requestPendingGrades(int teacherId) throws IOException {
        if (teacherId <= 0) {
            throw new IllegalArgumentException("Teacher ID must be valid.");
        }
        verifyConnection();
        client.sendMessageToServer(new Request(RequestType.GET_PENDING_GRADES, teacherId));
    }

    /**
     * Approves a computer-calculated grade as it stands, releasing it to
     * the student.
     *
     * @param submissionId identifier of the submission
     * @throws IOException if the request cannot be sent
     */
    public void requestApproveGrade(int submissionId) throws IOException {
        if (submissionId <= 0) {
            throw new IllegalArgumentException("Submission ID must be valid.");
        }
        verifyConnection();
        client.sendMessageToServer(new Request(RequestType.APPROVE_GRADE, submissionId));
    }

    /**
     * Replaces a computer-calculated grade with one the teacher sets.
     *
     * @param override submission ID, new score and justification
     * @throws IOException if the request cannot be sent
     */
    public void requestOverrideGrade(GradeOverride override) throws IOException {
        if (override == null || override.getSubmissionId() <= 0) {
            throw new IllegalArgumentException("Submission ID must be valid.");
        }
        if (override.getNewScore() < 0 || override.getNewScore() > 100) {
            throw new IllegalArgumentException("The new grade must be between 0 and 100.");
        }
        if (override.getJustification() == null || override.getJustification().isBlank()) {
            throw new IllegalArgumentException("A justification is required to change a grade.");
        }
        verifyConnection();
        client.sendMessageToServer(new Request(RequestType.OVERRIDE_GRADE, override));
    }

    /**
     * Requests the checked exam form for one of the student's own approved
     * submissions. The server responds with a {@code SubmissionReview}.
     *
     * @param submissionId identifier of the submission
     * @throws IOException if the request cannot be sent
     */
    public void requestSubmissionReview(int submissionId) throws IOException {
        if (submissionId <= 0) {
            throw new IllegalArgumentException("Submission ID must be valid.");
        }
        verifyConnection();
        client.sendMessageToServer(new Request(RequestType.GET_SUBMISSION_REVIEW, submissionId));
    }

    // -- LEARNING BOT (spec 13 + 14) ---------------------------------------
    //
    // Client-side validation here is a courtesy that saves a round trip and
    // gives a faster message; it is never the enforcement. Every one of these
    // is re-validated and re-authorised server-side from the socket's session,
    // because a client is not a trustworthy source of who is asking.

    /**
     * Requests the courses this teacher is attached to. The server answers for
     * the session's own user and responds with a {@code List<Course>}.
     *
     * @throws IOException if the request cannot be sent
     */
    public void requestMyCourses() throws IOException {
        verifyConnection();
        client.sendMessageToServer(new Request(RequestType.GET_MY_COURSES, null));
    }

    /**
     * Requests the learning bots this student may use.
     * The server responds with a {@code List<CourseBot>}.
     *
     * @param studentId identifier of the student
     * @throws IOException if the request cannot be sent
     */
    public void requestStudentBots(int studentId) throws IOException {
        if (studentId <= 0) {
            throw new IllegalArgumentException("Student ID must be valid.");
        }
        verifyConnection();
        client.sendMessageToServer(new Request(RequestType.GET_STUDENT_BOTS, studentId));
    }

    /**
     * Asks a course bot one question. The server responds with the logged
     * {@code BotQuestion}, or refuses with its own reason.
     *
     * @param ask the bot and the question
     * @throws IOException if the request cannot be sent
     */
    public void requestAskBot(BotAsk ask) throws IOException {
        if (ask == null || ask.getBotId() <= 0) {
            throw new IllegalArgumentException("Choose a course first.");
        }
        if (ask.getQuestionText() == null || ask.getQuestionText().isBlank()) {
            throw new IllegalArgumentException("Type a question first.");
        }
        verifyConnection();
        client.sendMessageToServer(new Request(RequestType.ASK_BOT, ask));
    }

    /**
     * Requests the student's own history with one bot.
     * The server responds with a {@code List<BotQuestion>}.
     *
     * @param botId identifier of the bot
     * @throws IOException if the request cannot be sent
     */
    public void requestMyBotHistory(int botId) throws IOException {
        if (botId <= 0) {
            throw new IllegalArgumentException("Bot ID must be valid.");
        }
        verifyConnection();
        client.sendMessageToServer(new Request(RequestType.GET_MY_BOT_HISTORY, botId));
    }

    /**
     * Requests one course's bot with its sources, for a teacher of that
     * course. The server responds with a {@code CourseBot}.
     *
     * @param courseId identifier of the course
     * @throws IOException if the request cannot be sent
     */
    public void requestCourseBot(int courseId) throws IOException {
        if (courseId <= 0) {
            throw new IllegalArgumentException("Course ID must be valid.");
        }
        verifyConnection();
        client.sendMessageToServer(new Request(RequestType.GET_COURSE_BOT, courseId));
    }

    /**
     * Creates or updates a course bot's name and availability.
     *
     * @param bot the bot to save
     * @throws IOException if the request cannot be sent
     */
    public void requestSaveBot(CourseBot bot) throws IOException {
        if (bot == null || bot.getCourseId() <= 0) {
            throw new IllegalArgumentException("Choose a course first.");
        }
        if (bot.getBotName() == null || bot.getBotName().isBlank()) {
            throw new IllegalArgumentException("Give the bot a name.");
        }
        verifyConnection();
        client.sendMessageToServer(new Request(RequestType.SAVE_BOT, bot));
    }

    /**
     * Creates or updates one of a bot's information sources.
     *
     * @param source the source to save
     * @throws IOException if the request cannot be sent
     */
    public void requestSaveBotSource(BotSource source) throws IOException {
        if (source == null || source.getBotId() <= 0) {
            throw new IllegalArgumentException("Save the bot before adding material to it.");
        }
        if (source.getTitle() == null || source.getTitle().isBlank()) {
            throw new IllegalArgumentException("A source needs a title.");
        }
        if (source.getContent() == null || source.getContent().isBlank()) {
            throw new IllegalArgumentException("A source needs some content.");
        }
        verifyConnection();
        client.sendMessageToServer(new Request(RequestType.SAVE_BOT_SOURCE, source));
    }

    /**
     * Deletes one of a bot's information sources.
     *
     * @param sourceId identifier of the source
     * @throws IOException if the request cannot be sent
     */
    public void requestDeleteBotSource(int sourceId) throws IOException {
        if (sourceId <= 0) {
            throw new IllegalArgumentException("Source ID must be valid.");
        }
        verifyConnection();
        client.sendMessageToServer(new Request(RequestType.DELETE_BOT_SOURCE, sourceId));
    }

    /**
     * Requests one bot's question history without user identification
     * (spec 14.3). The server responds with a {@code List<BotQuestion>} whose
     * student IDs were never selected in the first place.
     *
     * @param botId identifier of the bot
     * @throws IOException if the request cannot be sent
     */
    public void requestAnonymousBotHistory(int botId) throws IOException {
        if (botId <= 0) {
            throw new IllegalArgumentException("Save the bot before viewing its history.");
        }
        verifyConnection();
        client.sendMessageToServer(new Request(RequestType.GET_BOT_HISTORY_ANONYMOUS, botId));
    }

    /**
     * Asks what the server's engine can currently do. The server responds
     * with an {@code EngineStatus}.
     *
     * The generation screen sends this on open so it can disable itself with
     * an explanation when the offline fallback is active, instead of letting
     * a teacher fill in a form that cannot work.
     *
     * @throws IOException if the request cannot be sent
     */
    public void requestEngineStatus() throws IOException {
        verifyConnection();
        client.sendMessageToServer(new Request(RequestType.GET_ENGINE_STATUS, null));
    }

    /**
     * Asks for draft questions. The server responds with a
     * {@code List<Question>} that is NOT saved -- the teacher reviews them
     * first.
     *
     * @param request course, topic, difficulty and how many
     * @throws IOException if the request cannot be sent
     */
    public void requestGenerateQuestions(GenerationRequest request) throws IOException {
        validateGeneration(request);
        verifyConnection();
        client.sendMessageToServer(new Request(RequestType.GENERATE_QUESTIONS, request));
    }

    /**
     * Asks for a draft exam. The server responds with an unsaved {@code Exam}
     * whose points already total 100.
     *
     * @param request course, topic, question count and duration
     * @throws IOException if the request cannot be sent
     */
    public void requestGenerateExam(GenerationRequest request) throws IOException {
        validateGeneration(request);
        if (request.getDurationMinutes() <= 0) {
            throw new IllegalArgumentException("The duration must be greater than zero.");
        }
        verifyConnection();
        client.sendMessageToServer(new Request(RequestType.GENERATE_EXAM, request));
    }

    /**
     * Saves the drafts a teacher kept, after any edits. The server sanitises
     * every one of them exactly like a hand-written question before storing
     * it, and responds with the persisted rows.
     *
     * @param questions the kept, possibly-edited drafts
     * @throws IOException if the request cannot be sent
     */
    public void requestSaveGeneratedQuestions(List<Question> questions) throws IOException {
        if (questions == null || questions.isEmpty()) {
            throw new IllegalArgumentException("There are no questions to save.");
        }
        verifyConnection();
        client.sendMessageToServer(
                new Request(RequestType.SAVE_GENERATED_QUESTIONS, new ArrayList<>(questions)));
    }

    /** Validation shared by both generation requests. */
    private void validateGeneration(GenerationRequest request) {
        if (request == null || request.getCourseId() <= 0) {
            throw new IllegalArgumentException("Choose one of your courses first.");
        }
        if (request.getTopic() == null || request.getTopic().isBlank()) {
            throw new IllegalArgumentException("Enter a topic to generate from.");
        }
        if (request.getCount() < 1 || request.getCount() > GenerationRequest.MAX_COUNT) {
            throw new IllegalArgumentException(
                    "Ask for between 1 and " + GenerationRequest.MAX_COUNT + " questions.");
        }
    }

    /**
     * Sends a profile update request to the server.
     *
     * @param user user with updated fullName and avatarUrl
     * @throws IOException if the request cannot be sent
     */
    public void requestUpdateProfile(User user) throws IOException {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null.");
        }
        verifyConnection();
        client.sendMessageToServer(new Request(RequestType.UPDATE_PROFILE, user));
    }

    /**
     * Requests the interface settings (theme + accessibility) for a user.
     *
     * @param userId identifier of the user
     * @throws IOException if the request cannot be sent
     */
    public void requestUserSettings(int userId) throws IOException {
        if (userId <= 0) {
            throw new IllegalArgumentException("User ID must be valid.");
        }
        verifyConnection();
        client.sendMessageToServer(new Request(RequestType.GET_USER_SETTINGS, userId));
    }

    /**
     * Saves the interface settings (theme + accessibility) for a user.
     *
     * @param settings settings to persist
     * @throws IOException if the request cannot be sent
     */
    public void requestUpdateUserSettings(UserSettings settings) throws IOException {
        if (settings == null) {
            throw new IllegalArgumentException("Settings cannot be null.");
        }
        verifyConnection();
        client.sendMessageToServer(new Request(RequestType.UPDATE_USER_SETTINGS, settings));
    }

    /**
     * Sends a logout request and closes the connection.
     *
     * @param user currently connected user
     */
    public void requestLogout(
            User user
    ) {

        if (client == null) {
            return;
        }

        if (client.isConnected()
                && user != null) {

            try {

                Request request = new Request(
                        RequestType.LOGOUT,
                        user
                );

                client.sendMessageToServer(
                        request
                );

            } catch (IOException exception) {

                System.err.println(
                        "Could not send logout request: "
                                + exception.getMessage()
                );
            }
        }

        disconnect();
    }

    /**
     * Disconnects the client from the server.
     */
    public void disconnect() {

        if (client != null) {
            client.disconnectFromServer();
        }
    }

    /**
     * Checks whether the client is connected.
     *
     * @return true if the client is connected
     */
    public boolean isConnected() {

        return client != null
                && client.isConnected();
    }

    /**
     * Verifies that an active server connection exists.
     *
     * @throws IOException if the client is not connected
     */
    private void verifyConnection()
            throws IOException {

        if (isConnected()) {
            return;
        }

        // The socket dropped mid-session. Try to transparently re-open it using
        // the last-known parameters so the pending request can still go through.
        if (lastHost != null && lastResponseHandler != null) {

            try {

                client = new TestifyClient(lastHost, lastPort);
                client.setResponseHandler(lastResponseHandler);
                client.connectToServer();

            } catch (IOException reconnectFailure) {

                throw new IOException(
                        "Lost connection to the HSTS server and could not reconnect: "
                                + reconnectFailure.getMessage()
                );
            }
        }

        if (!isConnected()) {

            throw new IOException(
                    "The client is not connected to the HSTS server."
            );
        }
    }
}
