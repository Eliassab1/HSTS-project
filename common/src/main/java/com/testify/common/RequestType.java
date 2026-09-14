package com.testify.common;

/**
 * Contains all request action names used in the communication
 * between the HSTS client and server.
 */
public final class RequestType {

    /**
     * Prevents the creation of RequestType objects.
     */
    private RequestType() {
    }

    /**
     * Logs a user into the system.
     */
    public static final String LOGIN =
            "LOGIN";

    /**
     * Logs the current user out of the system.
     */
    public static final String LOGOUT =
            "LOGOUT";

    /**
     * Requests all questions from the server.
     */
    public static final String GET_ALL_QUESTIONS =
            "GET_ALL_QUESTIONS";

    /**
     * Creates a new question in the question bank.
     */
    public static final String CREATE_QUESTION =
            "CREATE_QUESTION";

    /**
     * Deletes a question from the question bank.
     */
    public static final String DELETE_QUESTION =
            "DELETE_QUESTION";

    /**
     * Updates an existing question.
     */
    public static final String UPDATE_QUESTION =
            "UPDATE_QUESTION";

    /**
     * Creates and saves a new exam.
     */
    public static final String CREATE_EXAM =
            "CREATE_EXAM";

    /**
     * Requests all exams available for a student.
     */
    public static final String GET_AVAILABLE_EXAMS =
            "GET_AVAILABLE_EXAMS";

    /**
     * Requests one exam according to its identifier.
     */
    public static final String GET_EXAM_BY_ID =
            "GET_EXAM_BY_ID";

    /**
     * Submits a completed student exam.
     */
    public static final String SUBMIT_EXAM =
            "SUBMIT_EXAM";

    /**
     * Requests the exam results of the logged-in student.
     */
    public static final String GET_STUDENT_RESULTS =
            "GET_STUDENT_RESULTS";

    /**
     * Requests all exams created by a teacher.
     */
    public static final String GET_TEACHER_EXAMS =
            "GET_TEACHER_EXAMS";

    /**
     * Deletes an exam (only if it has no submissions).
     */
    public static final String DELETE_EXAM =
            "DELETE_EXAM";

    /**
     * Toggles the active/inactive status of an exam.
     */
    public static final String TOGGLE_EXAM_STATUS =
            "TOGGLE_EXAM_STATUS";

    /**
     * Requests all exams pending principal approval.
     */
    public static final String GET_PENDING_EXAMS =
            "GET_PENDING_EXAMS";

    /**
     * Approves a pending exam.
     */
    public static final String APPROVE_EXAM =
            "APPROVE_EXAM";

    /**
     * Rejects a pending exam.
     */
    public static final String REJECT_EXAM =
            "REJECT_EXAM";

    /**
     * Requests all courses from the server.
     */
    public static final String GET_ALL_COURSES =
            "GET_ALL_COURSES";

    /**
     * Requests the administrator's exam pass/fail report, grouped by course.
     */
    public static final String GET_PASS_FAIL_REPORT =
            "GET_PASS_FAIL_REPORT";

    /**
     * Requests the administrator's average-score-over-time report.
     */
    public static final String GET_SCORE_TREND_REPORT =
            "GET_SCORE_TREND_REPORT";

    /**
     * Requests the administrator's teacher activity report
     * (tests/questions authored per teacher).
     */
    public static final String GET_TEACHER_ACTIVITY_REPORT =
            "GET_TEACHER_ACTIVITY_REPORT";

    /**
     * Requests the teacher's average-score-per-exam report for their
     * last 10 created exams (teacher dashboard performance chart).
     */
    public static final String GET_TEACHER_EXAM_PERFORMANCE =
            "GET_TEACHER_EXAM_PERFORMANCE";

    /**
     * Sets an approved exam's opening/closing window and 4-digit start code.
     */
    public static final String SCHEDULE_EXAM =
            "SCHEDULE_EXAM";

    /**
     * Starts an exam via its 4-digit code and the student's national ID,
     * after verifying the exam is open and the student may take it.
     */
    public static final String START_EXAM_BY_CODE =
            "START_EXAM_BY_CODE";

    /**
     * Requests every exam in the system, across all teachers (principal).
     */
    public static final String GET_ALL_EXAMS =
            "GET_ALL_EXAMS";

    /**
     * Requests every graded exam result in the system, across all students
     * (principal).
     */
    public static final String GET_ALL_RESULTS =
            "GET_ALL_RESULTS";

    /**
     * Requests the grade distribution in 10-point bands, for one exam or
     * across every graded submission.
     */
    public static final String GET_GRADE_DISTRIBUTION =
            "GET_GRADE_DISTRIBUTION";

    /**
     * Requests per-exam statistics for one teacher, for comparing that
     * teacher's exams against each other.
     */
    public static final String GET_TEACHER_STATISTICS =
            "GET_TEACHER_STATISTICS";

    /**
     * Requests per-exam statistics for one course, for comparing the exams
     * of that course against each other.
     */
    public static final String GET_COURSE_STATISTICS =
            "GET_COURSE_STATISTICS";

    /**
     * Requests per-exam statistics for one student, for comparing the exams
     * that student has sat.
     */
    public static final String GET_STUDENT_STATISTICS =
            "GET_STUDENT_STATISTICS";

    /**
     * Requests the submissions awaiting this teacher's grade approval.
     */
    public static final String GET_PENDING_GRADES =
            "GET_PENDING_GRADES";

    /**
     * Approves a computer-calculated grade, releasing it to the student.
     */
    public static final String APPROVE_GRADE =
            "APPROVE_GRADE";

    /**
     * Replaces a computer-calculated grade with one the teacher sets,
     * together with a justification.
     */
    public static final String OVERRIDE_GRADE =
            "OVERRIDE_GRADE";

    /**
     * Requests the checked exam form of one of the student's own
     * already-approved submissions.
     */
    public static final String GET_SUBMISSION_REVIEW =
            "GET_SUBMISSION_REVIEW";

    /**
     * Updates the current user's profile (display name and avatar URL).
     */
    public static final String UPDATE_PROFILE =
            "UPDATE_PROFILE";

    /**
     * Requests the current user's interface settings (theme + accessibility).
     */
    public static final String GET_USER_SETTINGS =
            "GET_USER_SETTINGS";

    /**
     * Saves the current user's interface settings (theme + accessibility).
     */
    public static final String UPDATE_USER_SETTINGS =
            "UPDATE_USER_SETTINGS";

    /**
     * Requests the archived earlier versions of one question, newest first.
     * Editing a question keeps its previous version in the bank rather than
     * overwriting it (spec 2.2).
     */
    public static final String GET_QUESTION_HISTORY =
            "GET_QUESTION_HISTORY";

    /**
     * Copies an existing question into a new question-bank row with a fresh
     * question code (spec 2.3).
     */
    public static final String DUPLICATE_QUESTION =
            "DUPLICATE_QUESTION";

    /**
     * Saves an edited exam. Refused once the exam has submissions, and it
     * sends an already-approved exam back through approval (spec 3.5).
     */
    public static final String UPDATE_EXAM =
            "UPDATE_EXAM";

    /**
     * Requests one exam's results across the students who sat it, optionally
     * narrowed to a single שכבה (spec 10). Answered only for the teacher who
     * owns the exam, or the principal.
     */
    public static final String GET_EXAM_RESULTS =
            "GET_EXAM_RESULTS";

    /**
     * Adds time to an exam that is already running (spec 7). Payload is an
     * {@link ExamExtension}; accepted only from the teacher who owns the exam.
     */
    public static final String EXTEND_EXAM_DURATION =
            "EXTEND_EXAM_DURATION";

    /**
     * <b>Not a request — the one action the SERVER initiates.</b>
     *
     * Pushed unsolicited to every client currently sitting the extended exam,
     * carrying the extra minutes as an {@code Integer}. It travels as a
     * {@link Response} like everything else, so the client routes it through
     * the same action switch; only its direction is unusual. Spec 18 forbids
     * proactive screen refreshing, so a live extension cannot be discovered by
     * polling — it has to be pushed.
     */
    public static final String EXAM_DURATION_EXTENDED =
            "EXAM_DURATION_EXTENDED";

    // ── LEARNING BOT (spec 13 + 14) ───────────────────────────────────────
    // Every one of these is authorised from the socket's session, never from
    // an ID in the payload, and refused outright when no session is attached.

    /**
     * The courses the requesting TEACHER is attached to, via
     * {@code course_teachers}. Distinct from {@link #GET_ALL_COURSES}, which
     * lists every course in the school: the bot editor and the generation
     * screens must offer only courses the teacher may actually act on, and
     * the server refuses anything else anyway.
     */
    public static final String GET_MY_COURSES =
            "GET_MY_COURSES";

    /**
     * The bots a student may actually use: available bots for the courses
     * that student is enrolled in. Payload is the student ID, but the server
     * answers for the session's own user.
     */
    public static final String GET_STUDENT_BOTS =
            "GET_STUDENT_BOTS";

    /**
     * Asks a course bot one question ({@link BotAsk}). Refused while the
     * student is sitting an exam (spec 14) — the check is made before any
     * engine call, and fails closed.
     */
    public static final String ASK_BOT =
            "ASK_BOT";

    /**
     * The asking student's own history with one bot (spec 14.2).
     */
    public static final String GET_MY_BOT_HISTORY =
            "GET_MY_BOT_HISTORY";

    /**
     * The full bot record for one course, sources included — answered only
     * for a teacher of that course.
     */
    public static final String GET_COURSE_BOT =
            "GET_COURSE_BOT";

    /**
     * Creates or updates a course bot's name and availability (spec 13.1).
     */
    public static final String SAVE_BOT =
            "SAVE_BOT";

    /**
     * Creates or updates one of a bot's information sources (spec 13.2).
     */
    public static final String SAVE_BOT_SOURCE =
            "SAVE_BOT_SOURCE";

    /**
     * Removes one of a bot's information sources.
     */
    public static final String DELETE_BOT_SOURCE =
            "DELETE_BOT_SOURCE";

    /**
     * The general question history for one bot, <b>without user
     * identification</b> (spec 14.3). The student ID is not selected at all
     * server-side, so it never reaches the client to be hidden.
     */
    public static final String GET_BOT_HISTORY_ANONYMOUS =
            "GET_BOT_HISTORY_ANONYMOUS";

    /**
     * Asks what the server's engine can currently do, as an
     * {@link EngineStatus}. The generation screen requests this on open so it
     * can disable itself with an explanation when the offline fallback is
     * active, rather than offering a button that cannot work.
     */
    public static final String GET_ENGINE_STATUS =
            "GET_ENGINE_STATUS";

    /**
     * Generates draft questions from a {@link GenerationRequest}. The result
     * is UNSAVED — the teacher reviews and edits before anything is stored.
     */
    public static final String GENERATE_QUESTIONS =
            "GENERATE_QUESTIONS";

    /**
     * Generates a draft exam from a {@link GenerationRequest}. Also UNSAVED;
     * the teacher finishes it through the normal exam-authoring path.
     */
    public static final String GENERATE_EXAM =
            "GENERATE_EXAM";

    /**
     * Persists the questions a teacher kept from a generated draft. Every
     * incoming question is sanitised exactly like a hand-written one —
     * generated content is untrusted input.
     */
    public static final String SAVE_GENERATED_QUESTIONS =
            "SAVE_GENERATED_QUESTIONS";

    /**
     * Every user account, as a {@code List<User>}. Principal-only, and the
     * password hash is never included — the client has no use for it and it
     * has no business on the wire.
     */
    public static final String GET_ALL_USERS =
            "GET_ALL_USERS";

    /**
     * Creates an account from a {@link User} (with the raw password in
     * {@code password}, hashed by the DAO on the way in). Principal-only.
     */
    public static final String CREATE_USER =
            "CREATE_USER";

    /**
     * Updates an account from a {@link User}. A blank password means "keep the
     * existing one" — the server re-reads the stored hash rather than making
     * the principal retype a password they do not know. Principal-only.
     */
    public static final String UPDATE_USER =
            "UPDATE_USER";

    /**
     * Deletes the account with the given {@code int} id. Refused for the
     * principal's own account, and for any user whose work is referenced
     * elsewhere. Principal-only.
     */
    public static final String DELETE_USER =
            "DELETE_USER";
}
