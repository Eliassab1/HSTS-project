package com.testify.client;

import com.testify.client.control.ClientController;
import com.testify.common.BotAsk;
import com.testify.common.BotQuestion;
import com.testify.common.BotSource;
import com.testify.common.Course;
import com.testify.common.CourseBot;
import com.testify.common.EngineStatus;
import com.testify.common.Exam;
import com.testify.common.ExamEntryRequest;
import com.testify.common.ExamExtension;
import com.testify.common.ExamQuestion;
import com.testify.common.ExamResult;
import com.testify.common.ExamResultsQuery;
import com.testify.common.ExamStatistics;
import com.testify.common.ExamSubmission;
import com.testify.common.GenerationRequest;
import com.testify.common.GradeDistributionBucket;
import com.testify.common.GradeOverride;
import com.testify.common.PassFailStat;
import com.testify.common.PendingGrade;
import com.testify.common.Question;
import com.testify.common.RequestType;
import com.testify.common.Response;
import com.testify.common.ScoreTrendPoint;
import com.testify.common.StudentExamResult;
import com.testify.common.SubmissionReview;
import com.testify.common.TeacherActivityStat;
import com.testify.common.TeacherExamPerformance;
import com.testify.common.User;
import com.testify.common.UserSettings;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.Scene;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Main JavaFX application of the HSTS client.
 *
 * The application supports:
 * 1. Login.
 * 2. Teacher Dashboard.
 * 3. Question Bank.
 * 4. Build Exam.
 * 5. Student Dashboard.
 * 6. Available Exams.
 * 7. Taking an Exam.
 * 8. Student Results.
 *
 * The Question Bank displays up to six questions per page.
 */
public class ClientApplication extends Application {

    /** Port used when nothing overrides it. */
    private static final int FALLBACK_PORT = 5555;

    /**
     * Server host the login screen is pre-filled with.
     *
     * <p>Resolved once, at class load: the system property {@code hsts.host}
     * first, then the environment variable {@code HSTS_HOST}, then loopback.
     * The field itself stays editable — this only decides its starting value,
     * so a room full of clients can be pointed at the server with
     * {@code -Dhsts.host=192.168.1.20} rather than relying on every laptop
     * typing the address correctly.
     */
    private static final String DEFAULT_HOST = resolveSetting(
            "hsts.host", "HSTS_HOST", "localhost");

    /**
     * Server port the login screen is pre-filled with, resolved from
     * {@code hsts.port} / {@code HSTS_PORT}; see {@link #DEFAULT_HOST}.
     */
    private static final int DEFAULT_PORT = resolvePort();

    /** The exam-results grade filter's "no filter" entry. */
    private static final String ALL_GRADE_LEVELS = "All grade levels";

    private static final int QUESTIONS_PER_PAGE = 6;

    private Stage primaryStage;
    private ClientController clientController;
    private User currentUser;

    private TextField hostField;
    private TextField portField;
    private TextField usernameField;
    private PasswordField passwordField;
    private Label loginStatusLabel;
    private Button loginButton;

    private final ObservableList<Question> allQuestions =
            FXCollections.observableArrayList();

    private final ObservableList<Question> displayedQuestions =
            FXCollections.observableArrayList();

    private final ObservableList<Exam> availableExams =
            FXCollections.observableArrayList();

    private final ObservableList<ExamResult> studentResults =
            FXCollections.observableArrayList();

    private TableView<Question> questionTable;

    private int currentPage;

    private Button previousPageButton;
    private Button nextPageButton;
    private Label pageLabel;

    private TextField courseField;
    private TextArea questionTextArea;
    private TextField answerAField;
    private TextField answerBField;
    private TextField answerCField;
    private TextField answerDField;
    private ComboBox<String> correctAnswerComboBox;

    private Label questionStatusLabel;

    // Question image upload (.png copied into the local images/ folder). Holds the
    // filename stored in the question's visualAidUrl; the label shows it in the form.
    private String questionImageFileName;
    private Label questionImageLabel;

    private StudentDashboardView studentDashboardView;
    private StudentResultsView studentResultsView;
    private TakeExamView takeExamView;
    private BuildExamView buildExamView;
    private ExamBankView examBankView;
    private ApproveExamView approveExamView;
    private GradeApprovalView gradeApprovalView;
    private BotChatView botChatView;
    private BotEditorView botEditorView;
    private GenerateContentView generateContentView;

    // A generated exam waiting for its questions to be persisted.
    //
    // A draft exam's questions carry id 0 because they have never been saved,
    // and CREATE_EXAM writes test_questions rows keyed on question_id -- so
    // handing the draft straight to the builder would fail on a foreign key
    // the moment the teacher saved. Accepting a draft therefore saves its
    // questions FIRST (through the same SAVE_GENERATED_QUESTIONS every
    // reviewed draft uses), and this field holds the exam until the real IDs
    // come back. Non-null means "the next SAVE_GENERATED_QUESTIONS response
    // belongs to the exam flow, not the question-review flow".
    private Exam pendingGeneratedExam;

    // Learning bot (spec 13 + 14).
    //
    // examInProgress is a COURTESY flag only: it greys out the bot entry
    // point on the student dashboard while an exam is open. The server
    // refuses ASK_BOT mid-exam on its own evidence (an open submission row
    // plus its in-memory registry), and a client that lied about this would
    // gain nothing.
    private boolean examInProgress;

    // The teacher's own courses, from GET_MY_COURSES rather than
    // GET_ALL_COURSES: offering a course the server will refuse is worse
    // than not offering it.
    private final ObservableList<Course> myCourses =
            FXCollections.observableArrayList();

    private Label profileStatusLabel;

    // User interface preferences (theme + accessibility), loaded after login.
    private UserSettings currentSettings;
    private VBox settingsDetailArea;
    private Label settingsStatusLabel;

    // Exam-list screens (Active Exams / Schedule) — shared list container + status
    // label, populated on GET_AVAILABLE_EXAMS response.
    private VBox examListBox;
    private Label examListStatusLabel;

    // Exam entry gate (4-digit code + national ID) — status label for rejects
    private Label examGateStatusLabel;

    // Teacher exam-results screen (spec 10): the exam being examined, its
    // rows, and the pieces that redraw whenever the grade-level filter moves.
    private Exam examResultsExam;

    private final ObservableList<StudentExamResult> examResults =
            FXCollections.observableArrayList();

    private Label examResultsStatusLabel;
    private Label examResultsSummaryLabel;
    private VBox examResultsHistogramContainer;
    private ComboBox<String> examResultsGradeFilter;

    // Set while a GET_EXAM_BY_ID is in flight on behalf of the exam editor.
    // The same request also backs the (legacy) "open an exam to sit it" path,
    // so the response needs to know which one asked for it.
    private boolean examEditRequested;

    // Dashboard stat card value labels — updated on server response
    private Label teacherQCountLabel;
    private Label teacherExamCountLabel;
    private Label teacherActiveCountLabel;
    private Label adminPendingLabel;
    private Label adminQCountLabel;

    // Teacher dashboard performance chart card — populated once
    // GET_TEACHER_EXAM_PERFORMANCE responds
    private VBox teacherChartContainer;

    // Teacher Reports screen — stat cards, chart cards, the per-exam table
    // and its status line, populated once GET_TEACHER_EXAMS /
    // GET_TEACHER_EXAM_PERFORMANCE / GET_ALL_QUESTIONS respond.
    private Label teacherReportsStatusLabel;
    private Label teacherReportsExamCountLabel;
    private Label teacherReportsActiveLabel;
    private Label teacherReportsPendingLabel;
    private Label teacherReportsQuestionLabel;
    private Label teacherReportsAverageLabel;
    private VBox teacherReportsAverageChart;
    private VBox teacherReportsVolumeChart;
    private TableView<Exam> teacherReportsTable;

    private final ObservableList<Exam> teacherReportsExams =
            FXCollections.observableArrayList();

    // Per-exam performance keyed by exam TITLE: the rows the server sends
    // carry no exam id, so that is the only handle they give us. Rebuilt
    // whenever GET_TEACHER_EXAM_PERFORMANCE responds.
    private final Map<String, TeacherExamPerformance> teacherPerformanceByExam =
            new LinkedHashMap<>();

    // Admin Reports screen — chart card containers and status label,
    // populated once the corresponding server response arrives
    private Label reportsStatusLabel;
    private VBox passFailChartContainer;
    private VBox scoreTrendChartContainer;
    private VBox teacherActivityChartContainer;

    // Principal system-wide listings (spec 11): every exam and every approved
    // result. Cached rather than screen-local because the Reports selectors are
    // built from the same two responses — a teacher list only exists inside the
    // exams, and a student list only inside the results.
    private final ObservableList<Exam> allExams =
            FXCollections.observableArrayList();

    private final ObservableList<ExamResult> allResults =
            FXCollections.observableArrayList();

    private final ObservableList<Course> allCourses =
            FXCollections.observableArrayList();

    private Label allExamsStatusLabel;
    private Label allResultsStatusLabel;

    // Principal's Users screen (spec: account administration). The list is
    // cached here rather than inside the screen so a CREATE/UPDATE/DELETE
    // response can amend it without a full reload.
    private final ObservableList<User> allUsers =
            FXCollections.observableArrayList();

    private Label usersStatusLabel;
    private TableView<User> usersTable;

    // Admin Reports screen — grade distribution + exam comparison (spec 12).
    // The comparison card drives all three GET_*_STATISTICS actions from one
    // pair of selectors: what to compare by, then which teacher/course/student.
    private VBox distributionChartContainer;
    private VBox comparisonChartContainer;
    private ComboBox<ReportOption> distributionExamCombo;
    private ComboBox<String> comparisonTypeCombo;
    private ComboBox<ReportOption> comparisonSubjectCombo;

    /**
     * Reads a startup setting from the system property first, then the
     * environment variable, then the supplied fallback.
     *
     * <p>The property wins so one client can be redirected on the command line
     * without disturbing a machine-wide variable.
     *
     * @param propertyName system property to consult, e.g. {@code hsts.host}
     * @param variableName environment variable to consult, e.g. {@code HSTS_HOST}
     * @param fallback     value used when neither is set, or both are blank
     * @return the resolved, trimmed value; never blank
     */
    private static String resolveSetting(String propertyName,
                                         String variableName,
                                         String fallback) {

        String value = System.getProperty(propertyName);

        if (value == null || value.trim().isEmpty()) {
            value = System.getenv(variableName);
        }

        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }

        return value.trim();
    }

    /**
     * Resolves the pre-filled port the same way as the host.
     *
     * <p>A value that is not a number, or not a usable port, falls back to
     * {@link #FALLBACK_PORT} instead of failing at class load — a typo in a
     * launch script must not stop the client from starting, since the field is
     * editable anyway.
     *
     * @return the resolved port
     */
    private static int resolvePort() {

        String value = resolveSetting(
                "hsts.port", "HSTS_PORT", String.valueOf(FALLBACK_PORT));

        try {
            int parsed = Integer.parseInt(value);

            if (parsed > 0 && parsed <= 65535) {
                return parsed;
            }

        } catch (NumberFormatException ignored) {
            // A bad override is not worth a crash — fall through.
        }

        return FALLBACK_PORT;
    }

    /**
     * Starts the JavaFX application.
     *
     * @param primaryStage main application window
     */
    @Override
    public void start(Stage primaryStage) {

        this.primaryStage = primaryStage;
        this.clientController = new ClientController();
        this.currentPage = 0;

        primaryStage.setMinWidth(600);
        primaryStage.setMinHeight(500);

        primaryStage.setOnCloseRequest(event -> {

            if (currentUser != null) {
                clientController.requestLogout(currentUser);
            } else {
                clientController.disconnect();
            }
        });

        showLoginScreen();
        primaryStage.show();
    }

    // ── Standard window geometry ──────────────────────────────────────────
    //
    // Every post-login screen is built at the SAME size, so moving around a
    // dashboard never resizes or re-centres the window. Replacing a Scene on a
    // showing Stage makes the Stage adopt the new Scene's dimensions, so the
    // per-screen sizes that used to be hardcoded here made the window jump on
    // every navigation.
    //
    // One size per role, each the largest that role's own screens need: the
    // teacher's exam builder (details + browse + selected, side by side) is the
    // widest screen in the app, so the teacher window is sized for it and every
    // other teacher screen simply has room to spare. Shrinking them all to the
    // dashboard's old 1100 would have cramped the builder instead.

    private static final double TEACHER_WINDOW_WIDTH    = 1600;
    private static final double TEACHER_WINDOW_HEIGHT   =  820;

    // Reports is the principal's widest screen and sets this number: two 460px
    // chart canvases side by side, plus a 940px one below them, none of which
    // can shrink, plus the 185px sidebar and the padding around it all.
    private static final double PRINCIPAL_WINDOW_WIDTH  = 1350;
    private static final double PRINCIPAL_WINDOW_HEIGHT =  820;

    private static final double STUDENT_WINDOW_WIDTH    = 1200;
    private static final double STUDENT_WINDOW_HEIGHT   =  760;

    /** The login card is a small centred panel and predates any role. */
    private static final double LOGIN_WINDOW_WIDTH  = 920;
    private static final double LOGIN_WINDOW_HEIGHT = 680;

    /**
     * Width of every post-login window, for the logged-in user's role.
     *
     * Capped to the primary screen's visual bounds so a small display gets a
     * window that still fits on it rather than one running off the edge.
     */
    private double windowWidth() {

        String role = currentUser == null ? null : currentUser.getRole();

        double preferred =
                "TEACHER".equalsIgnoreCase(role)   ? TEACHER_WINDOW_WIDTH
              : "PRINCIPAL".equalsIgnoreCase(role) ? PRINCIPAL_WINDOW_WIDTH
              :                                      STUDENT_WINDOW_WIDTH;

        return Math.min(preferred, Screen.getPrimary().getVisualBounds().getWidth() * 0.96);
    }

    /**
     * Height of every post-login window, for the logged-in user's role.
     */
    private double windowHeight() {

        String role = currentUser == null ? null : currentUser.getRole();

        double preferred =
                "TEACHER".equalsIgnoreCase(role)   ? TEACHER_WINDOW_HEIGHT
              : "PRINCIPAL".equalsIgnoreCase(role) ? PRINCIPAL_WINDOW_HEIGHT
              :                                      STUDENT_WINDOW_HEIGHT;

        return Math.min(preferred, Screen.getPrimary().getVisualBounds().getHeight() * 0.96);
    }

    /**
     * Pins the window to the current role's standard size, then centres it.
     * Called by every post-login screen in place of a bare centreOnScreen().
     *
     * Sizing the Scene is only a request. Setting a Scene on a Stage that is
     * already showing makes the Stage adopt the scene's size <i>unless the new
     * content cannot fit inside it</i> — in which case JavaFX grows the window
     * out to the content's minimum instead. Content minimums differ from screen
     * to screen, which is why the principal's window still changed size between
     * the dashboard and Reports after the scenes themselves were made uniform.
     * A Canvas is the usual culprit: it is not resizable, so a 940px chart is a
     * 940px hard floor no matter what the Scene was constructed with.
     *
     * Setting the size on the Stage after the swap makes the window size
     * authoritative rather than a suggestion. That puts the burden on each
     * role's standard width to clear its own widest screen — pinning a window
     * narrower than its content clips the content instead of resizing it.
     *
     * A maximized or full-screen window is left alone: the user asked for that,
     * and it stays put across a scene swap by itself.
     */
    private void applyStandardWindowSize() {

        if (primaryStage.isMaximized() || primaryStage.isFullScreen()) {
            return;
        }

        primaryStage.setWidth(windowWidth());
        primaryStage.setHeight(windowHeight());
        primaryStage.centerOnScreen();
    }

    /**
     * Displays the HSTS login screen.
     */
    private void showLoginScreen() {

        // ── Logo inside purple card ───────────────────────
        Label hexLabel = new Label("⬢ HSTS");
        hexLabel.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: white;");

        Label cardSubtitle = new Label("High School Test System");
        cardSubtitle.setStyle("-fx-font-size: 13px; -fx-text-fill: rgba(255,255,255,0.7);");

        Label welcomeHead = new Label("Welcome back!");
        welcomeHead.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: white;");

        // Server fields (compact, inside card)
        hostField = new TextField(DEFAULT_HOST);
        hostField.setPromptText("Server host");
        hostField.setPrefWidth(300);

        portField = new TextField(String.valueOf(DEFAULT_PORT));
        portField.setPromptText("Port");
        portField.setPrefWidth(300);

        HBox serverRow = new HBox(8, hostField, portField);
        serverRow.setAlignment(Pos.CENTER_LEFT);
        hostField.setPrefWidth(200);
        portField.setPrefWidth(90);

        usernameField = new TextField();
        usernameField.setPromptText("Username");
        usernameField.setPrefWidth(300);

        passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        passwordField.setPrefWidth(300);

        loginButton = new Button("Login as Student");
        loginButton.setStyle(
                "-fx-background-color: #7C3AED; -fx-text-fill: white;" +
                "-fx-font-size: 14px; -fx-font-weight: bold;" +
                "-fx-background-radius: 12px; -fx-padding: 12px 22px;" +
                "-fx-cursor: hand; -fx-border-width: 0;");
        loginButton.setPrefWidth(300);
        loginButton.setDefaultButton(true);
        loginButton.setOnAction(e -> performLogin());

        loginStatusLabel = new Label();
        loginStatusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #F9A8D4;");
        loginStatusLabel.setWrapText(true);
        loginStatusLabel.setMaxWidth(300);

        Label serverHint = new Label("Server");
        serverHint.getStyleClass().add("form-label");

        VBox loginCard = new VBox(12,
                hexLabel, cardSubtitle,
                new Region() {{ setMinHeight(6); }},
                welcomeHead,
                new Region() {{ setMinHeight(4); }},
                serverHint, serverRow,
                usernameField, passwordField,
                loginButton, loginStatusLabel);
        loginCard.getStyleClass().add("login-card");
        loginCard.setAlignment(Pos.CENTER_LEFT);
        loginCard.setPrefWidth(360);
        loginCard.setMaxWidth(360);

        // ── Right column: quick-login buttons ────────────
        Button studentBtn = new Button("Student Login\ngo to Student Portal");
        studentBtn.setStyle(
                "-fx-background-color: #86EFAC; -fx-text-fill: #14532D;" +
                "-fx-font-size: 14px; -fx-font-weight: bold;" +
                "-fx-background-radius: 12px; -fx-padding: 14px 22px;" +
                "-fx-cursor: hand; -fx-border-width: 0; -fx-pref-width: 240px;" +
                "-fx-text-alignment: center;");
        studentBtn.setWrapText(true);
        studentBtn.setOnAction(e -> {});

        Button teacherBtn = new Button("Teacher Login\ngo to Teacher Portal");
        teacherBtn.setStyle(
                "-fx-background-color: #7DD3FC; -fx-text-fill: #0C4A6E;" +
                "-fx-font-size: 14px; -fx-font-weight: bold;" +
                "-fx-background-radius: 12px; -fx-padding: 14px 22px;" +
                "-fx-cursor: hand; -fx-border-width: 0; -fx-pref-width: 240px;" +
                "-fx-text-alignment: center;");
        teacherBtn.setWrapText(true);
        teacherBtn.setOnAction(e -> {});

        Button adminBtn = new Button("Admin Login\ngo to Admin Portal");
        adminBtn.setStyle(
                "-fx-background-color: #FDB28A; -fx-text-fill: #7C2D12;" +
                "-fx-font-size: 14px; -fx-font-weight: bold;" +
                "-fx-background-radius: 12px; -fx-padding: 14px 22px;" +
                "-fx-cursor: hand; -fx-border-width: 0; -fx-pref-width: 240px;" +
                "-fx-text-alignment: center;");
        adminBtn.setWrapText(true);
        adminBtn.setOnAction(e -> {});

        Button forgotBtn = new Button("Forgot Password\nRecovery on this page");
        forgotBtn.setStyle(
                "-fx-background-color: #F9A8D4; -fx-text-fill: #831843;" +
                "-fx-font-size: 14px; -fx-font-weight: bold;" +
                "-fx-background-radius: 12px; -fx-padding: 14px 22px;" +
                "-fx-cursor: hand; -fx-border-width: 0; -fx-pref-width: 240px;" +
                "-fx-text-alignment: center;");
        forgotBtn.setWrapText(true);
        forgotBtn.setOnAction(e -> loginStatusLabel.setText("Password recovery: contact your administrator."));

        Label lilacRules = new Label("Lilac theme rules");
        lilacRules.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: -hsts-strong;");

        Label rulesText = new Label(
                "Main color: lilac / purple. Important navigation buttons are colored.\n" +
                "Settings pages available for account, profile, theme, privacy, language and AI assistant.");
        rulesText.setWrapText(true);
        rulesText.setStyle("-fx-font-size: 12px; -fx-text-fill: -hsts-muted;");
        rulesText.setMaxWidth(260);

        VBox rulesCard = new VBox(8, lilacRules, rulesText);
        rulesCard.setStyle("-fx-background-color: white; -fx-background-radius: 14px; -fx-padding: 18px;" +
                "-fx-effect: dropshadow(gaussian, rgba(88,28,135,0.10), 12, 0.07, 0, 3);");
        rulesCard.setMaxWidth(280);

        VBox rightCol = new VBox(16, studentBtn, teacherBtn, adminBtn, forgotBtn, rulesCard);
        rightCol.setAlignment(Pos.TOP_LEFT);

        HBox mainRow = new HBox(40, loginCard, rightCol);
        mainRow.setAlignment(Pos.CENTER);

        StackPane root = new StackPane(mainRow);
        root.getStyleClass().add("login-background");
        root.setPadding(new Insets(50));

        Scene scene = new Scene(root, LOGIN_WINDOW_WIDTH, LOGIN_WINDOW_HEIGHT);
        applyStyleSheet(scene);
        primaryStage.setScene(scene);
        primaryStage.setTitle("HSTS - Login");

        // Set explicitly rather than left to the Scene: the stage carries a
        // pinned size from whatever screen the user logged out of, and an
        // explicitly sized window does not shrink back on its own.
        primaryStage.setWidth(LOGIN_WINDOW_WIDTH);
        primaryStage.setHeight(LOGIN_WINDOW_HEIGHT);
        primaryStage.centerOnScreen();
    }

    /** Fills in credentials and submits login. */
    private void quickLogin(String username, String password) {
        usernameField.setText(username);
        passwordField.setText(password);
        performLogin();
    }

    /**
     * Validates login details and sends a login request.
     */
    private void performLogin() {

        String host =
                hostField.getText().trim();

        String portText =
                portField.getText().trim();

        String username =
                usernameField.getText().trim();

        String password =
                passwordField.getText();

        if (host.isEmpty()) {
            loginStatusLabel.setText(
                    "Please enter the server host."
            );
            return;
        }

        if (portText.isEmpty()) {
            loginStatusLabel.setText(
                    "Please enter the server port."
            );
            return;
        }

        if (username.isEmpty()) {
            loginStatusLabel.setText(
                    "Please enter your username."
            );
            return;
        }

        if (password.isEmpty()) {
            loginStatusLabel.setText(
                    "Please enter your password."
            );
            return;
        }

        int port;

        try {
            port = Integer.parseInt(portText);

        } catch (NumberFormatException exception) {

            loginStatusLabel.setText(
                    "The port must contain numbers only."
            );

            return;
        }

        try {

            if (!clientController.isConnected()) {

                clientController.connect(
                        host,
                        port,
                        this::handleServerResponse
                );
            }

            clientController.requestLogin(
                    username,
                    password
            );

            loginButton.setDisable(true);

            loginStatusLabel.setText(
                    "Connecting to the HSTS server..."
            );

        } catch (IllegalArgumentException exception) {

            loginButton.setDisable(false);

            loginStatusLabel.setText(
                    exception.getMessage()
            );

        } catch (IOException exception) {

            loginButton.setDisable(false);

            loginStatusLabel.setText(
                    "Connection failed: "
                            + exception.getMessage()
            );
        }
    }

    /**
     * Handles every response received from the server.
     *
     * <p>Routing is driven by {@link Response#getAction()} — the action the
     * server echoes back from the originating request. Screen checks appear
     * only <em>inside</em> a case, where a single action can legitimately
     * land on more than one screen (GET_TEACHER_EXAMS, for instance, feeds
     * both the Teacher Dashboard stat cards and the Exam Bank table). The
     * action answers "what is this?"; the screen answers "where does it go?".
     *
     * @param response server response
     */
    private void handleServerResponse(
            Response response
    ) {

        Platform.runLater(() -> {

            if (response == null) {
                showCurrentStatus("An empty response was received.");
                return;
            }

            String action  = response.getAction();
            String message = response.getMessage();

            if (!response.isSuccess()) {

                if (loginButton != null) {
                    loginButton.setDisable(false);
                }

                handleFailedResponse(action, message);
                return;
            }

            Object data = response.getData();

            if (action == null) {
                showCurrentStatus(message);
                return;
            }

            switch (action) {

                case RequestType.LOGIN -> {
                    if (data instanceof User user) {
                        handleSuccessfulLogin(user);
                    }
                }

                case RequestType.UPDATE_PROFILE -> {
                    if (data instanceof User user) {
                        handleProfileUpdated(user, message);
                    }
                }

                case RequestType.GET_USER_SETTINGS,
                     RequestType.UPDATE_USER_SETTINGS -> {
                    if (data instanceof UserSettings settings) {
                        handleSettingsLoaded(settings, message);
                    }
                }

                case RequestType.GET_ALL_QUESTIONS ->
                        handleQuestionList(asList(data), message);

                case RequestType.CREATE_QUESTION,
                     RequestType.UPDATE_QUESTION,
                     RequestType.DUPLICATE_QUESTION -> {
                    // A duplicate arrives as a question with an id the bank has
                    // not seen, which the same handler appends rather than
                    // replaces.
                    if (data instanceof Question question) {
                        handleUpdatedQuestion(question, message);
                    }
                }

                case RequestType.GET_QUESTION_HISTORY ->
                        showQuestionHistoryDialog(asList(data), message);

                case RequestType.GET_EXAM_RESULTS ->
                        handleExamResultsList(asList(data), message);

                case RequestType.EXTEND_EXAM_DURATION -> {
                    if (data instanceof Exam exam && examBankView != null) {
                        examBankView.updateExam(exam);
                        examBankView.setStatusMessage(message);
                    } else {
                        showCurrentStatus(message);
                    }
                }

                case RequestType.EXAM_DURATION_EXTENDED -> {
                    // The one response nobody asked for: the server pushes it
                    // to whoever is sitting the exam a teacher just extended.
                    // TakeExamView decides whether it still applies — a
                    // student who already submitted does not get revived.
                    if (data instanceof Integer extraMinutes && takeExamView != null) {
                        takeExamView.addMinutes(extraMinutes);
                    }
                }

                case RequestType.DELETE_QUESTION -> {
                    if (data instanceof Integer questionId) {
                        handleDeletedQuestion(questionId, message);
                    }
                }

                case RequestType.DELETE_EXAM -> {
                    if (data instanceof Integer examId) {
                        handleDeletedExam(examId, message);
                    }
                }

                case RequestType.CREATE_EXAM -> {
                    if (buildExamView != null
                            && primaryStage.getTitle().contains("Build Exam")) {
                        buildExamView.setStatusMessage(message);
                    } else {
                        showCurrentStatus(message);
                    }
                }

                case RequestType.GET_EXAM_BY_ID -> {
                    if (data instanceof Exam exam) {
                        if (examEditRequested) {
                            // The teacher asked to edit this exam, so the full
                            // exam opens in the builder rather than being sat.
                            examEditRequested = false;
                            showBuildExamScreen(exam);
                        } else if (!primaryStage.getTitle().contains("Take Exam")) {
                            showTakeExamScreen(exam);
                        }
                    }
                }

                case RequestType.UPDATE_EXAM -> {
                    if (buildExamView != null
                            && primaryStage.getTitle().contains("Build Exam")) {
                        buildExamView.setStatusMessage(message);
                    } else {
                        showCurrentStatus(message);
                    }
                }

                case RequestType.TOGGLE_EXAM_STATUS -> {
                    if (data instanceof Exam exam && examBankView != null) {
                        examBankView.updateExam(exam);
                        examBankView.setStatusMessage(message);
                    } else {
                        showCurrentStatus(message);
                    }
                }

                case RequestType.APPROVE_EXAM,
                     RequestType.REJECT_EXAM -> {
                    if (data instanceof Exam exam && approveExamView != null) {
                        approveExamView.removeExam(exam.getExamId());
                        approveExamView.setStatusMessage(message);
                    } else {
                        showCurrentStatus(message);
                    }
                }

                case RequestType.SCHEDULE_EXAM -> {
                    if (data instanceof Exam exam && examBankView != null) {
                        examBankView.updateExam(exam);
                        examBankView.setStatusMessage(message);
                    } else {
                        showCurrentStatus(message);
                    }
                }

                case RequestType.START_EXAM_BY_CODE -> {
                    // The gate only opens once the server has verified the code,
                    // the ID, the window and enrolment.
                    if (data instanceof Exam exam
                            && !primaryStage.getTitle().contains("Take Exam")) {
                        showTakeExamScreen(exam);
                    }
                }

                case RequestType.GET_AVAILABLE_EXAMS ->
                        handleAvailableExamList(asList(data), message);

                case RequestType.GET_TEACHER_EXAMS ->
                        handleTeacherExamList(asList(data), message);

                case RequestType.GET_PENDING_EXAMS ->
                        handlePendingExamList(asList(data), message);

                case RequestType.SUBMIT_EXAM -> {
                    if (data instanceof ExamResult result) {
                        handleSubmittedExam(result, message);
                    }
                }

                case RequestType.GET_STUDENT_RESULTS ->
                        handleResultList(asList(data), message);

                case RequestType.GET_PENDING_GRADES ->
                        handlePendingGradeList(asList(data), message);

                case RequestType.APPROVE_GRADE,
                     RequestType.OVERRIDE_GRADE -> {
                    if (data instanceof PendingGrade grade) {
                        handleGradeReleased(grade, message);
                    } else {
                        showCurrentStatus(message);
                    }
                }

                case RequestType.GET_SUBMISSION_REVIEW -> {
                    if (data instanceof SubmissionReview review) {
                        showSubmissionReviewScreen(review);
                    }
                }

                case RequestType.GET_ALL_COURSES ->
                        handleCourseList(asList(data));

                case RequestType.GET_PASS_FAIL_REPORT ->
                        handlePassFailReport(asList(data));

                case RequestType.GET_SCORE_TREND_REPORT ->
                        handleScoreTrendReport(asList(data));

                case RequestType.GET_TEACHER_ACTIVITY_REPORT ->
                        handleTeacherActivityReport(asList(data));

                case RequestType.GET_TEACHER_EXAM_PERFORMANCE ->
                        handleTeacherExamPerformance(asList(data));

                case RequestType.GET_ALL_USERS ->
                        handleUserList(asList(data), message);

                case RequestType.CREATE_USER,
                     RequestType.UPDATE_USER -> {
                    if (data instanceof User user) {
                        handleSavedUser(user, message);
                    }
                }

                case RequestType.DELETE_USER -> {
                    if (data instanceof Integer userId) {
                        handleDeletedUser(userId, message);
                    }
                }

                case RequestType.GET_ALL_EXAMS ->
                        handleAllExamList(asList(data), message);

                case RequestType.GET_ALL_RESULTS ->
                        handleAllResultList(asList(data), message);

                case RequestType.GET_GRADE_DISTRIBUTION ->
                        handleGradeDistribution(asList(data));

                case RequestType.GET_TEACHER_STATISTICS,
                     RequestType.GET_COURSE_STATISTICS,
                     RequestType.GET_STUDENT_STATISTICS ->
                        handleComparisonStatistics(asList(data), message);

                case RequestType.GET_MY_COURSES -> {
                    myCourses.setAll(asCourseList(asList(data)));
                    if (isShowing("Generate Content") && generateContentView != null) {
                        generateContentView.setCourses(myCourses);
                    } else if (botEditorView != null) {
                        botEditorView.setCourses(myCourses);
                    }
                }

                case RequestType.GET_ENGINE_STATUS -> {
                    if (data instanceof EngineStatus status && generateContentView != null) {
                        generateContentView.setEngineStatus(status);
                    } else {
                        showCurrentStatus(message);
                    }
                }

                case RequestType.GENERATE_QUESTIONS -> {
                    if (generateContentView != null) {
                        generateContentView.setDrafts(asQuestionList(asList(data)));
                        generateContentView.setStatusMessage(message);
                    } else {
                        showCurrentStatus(message);
                    }
                }

                case RequestType.GENERATE_EXAM -> {
                    if (data instanceof Exam draft && generateContentView != null) {
                        generateContentView.setDraftExam(draft);
                        generateContentView.setStatusMessage(message);
                    } else {
                        showCurrentStatus(message);
                    }
                }

                case RequestType.SAVE_GENERATED_QUESTIONS ->
                        handleGeneratedQuestionsSaved(asQuestionList(asList(data)), message);

                case RequestType.GET_STUDENT_BOTS -> {
                    if (botChatView != null) {
                        botChatView.setBots(asBotList(asList(data)));
                    }
                }

                case RequestType.ASK_BOT -> {
                    if (data instanceof BotQuestion exchange && botChatView != null) {
                        botChatView.addExchange(exchange);
                    } else {
                        showCurrentStatus(message);
                    }
                }

                case RequestType.GET_MY_BOT_HISTORY -> {
                    if (botChatView != null) {
                        botChatView.setHistory(asBotQuestionList(asList(data)));
                    }
                }

                case RequestType.GET_COURSE_BOT -> {
                    if (data instanceof CourseBot bot && botEditorView != null) {
                        botEditorView.setBot(bot);
                        botEditorView.setStatusMessage(message);
                    } else {
                        showCurrentStatus(message);
                    }
                }

                case RequestType.SAVE_BOT -> {
                    if (data instanceof CourseBot bot && botEditorView != null) {
                        botEditorView.setBot(bot);
                        botEditorView.setStatusMessage(message);
                    } else {
                        showCurrentStatus(message);
                    }
                }

                case RequestType.SAVE_BOT_SOURCE -> {
                    if (data instanceof BotSource source && botEditorView != null) {
                        botEditorView.setSourceSaved(source);
                    } else {
                        showCurrentStatus(message);
                    }
                }

                case RequestType.DELETE_BOT_SOURCE -> {
                    if (data instanceof Integer sourceId && botEditorView != null) {
                        botEditorView.removeSource(sourceId);
                    } else {
                        showCurrentStatus(message);
                    }
                }

                case RequestType.GET_BOT_HISTORY_ANONYMOUS -> {
                    if (botEditorView != null) {
                        botEditorView.setHistory(asBotQuestionList(asList(data)));
                    }
                }

                case RequestType.LOGOUT -> {
                    // The client tears its own session down; nothing to render.
                }

                default -> showCurrentStatus(message);
            }
        });
    }

    /**
     * Narrows a response payload to a list, yielding an empty list when the
     * server sent something else or nothing at all.
     *
     * <p>This is what lets an empty result be routed correctly: previously the
     * client identified a list by type-checking its first element, so a list
     * with no elements was untypeable and had to be guessed at from the
     * window title and the message text.
     *
     * @param data raw response payload
     * @return the payload as a list, never null
     */
    private List<?> asList(Object data) {
        return data instanceof List<?> list ? list : List.of();
    }

    /**
     * Reports a failed response on whichever surface makes sense for the
     * action that failed, so an error lands next to the control that
     * triggered it rather than always in the generic status label.
     *
     * @param action action that failed, may be null
     * @param message error message from the server
     */
    private void handleFailedResponse(
            String action,
            String message
    ) {

        if (action == null) {
            showCurrentStatus(message);
            return;
        }

        switch (action) {

            case RequestType.CREATE_EXAM -> {
                if (buildExamView != null
                        && primaryStage.getTitle().contains("Build Exam")) {
                    buildExamView.setStatusMessage(message);
                } else {
                    showCurrentStatus(message);
                }
            }

            case RequestType.APPROVE_EXAM,
                 RequestType.REJECT_EXAM,
                 RequestType.GET_PENDING_EXAMS -> {
                if (approveExamView != null) {
                    approveExamView.setStatusMessage(message);
                } else {
                    showCurrentStatus(message);
                }
            }

            case RequestType.DELETE_EXAM,
                 RequestType.TOGGLE_EXAM_STATUS,
                 RequestType.SCHEDULE_EXAM,
                 RequestType.EXTEND_EXAM_DURATION,
                 RequestType.GET_TEACHER_EXAMS -> {
                // The exam list backs the Reports screen too, and examBankView
                // stays non-null after the bank is left, so the screen on show
                // decides -- not whichever view was built last.
                if (examBankView != null
                        && !primaryStage.getTitle().contains("My Reports")) {
                    examBankView.setStatusMessage(message);
                } else {
                    showCurrentStatus(message);
                }
            }

            case RequestType.GET_ALL_USERS,
                 RequestType.CREATE_USER,
                 RequestType.UPDATE_USER,
                 RequestType.DELETE_USER -> {
                // Every refusal here is actionable -- a taken username, an
                // account still referenced by exam rows -- so it belongs on the
                // screen that asked, not in a generic status line.
                if (usersStatusLabel != null) {
                    usersStatusLabel.setText(message);
                } else {
                    showCurrentStatus(message);
                }
            }

            case RequestType.START_EXAM_BY_CODE -> {
                // Every gate rejection (bad code, wrong ID, closed window, not
                // enrolled) surfaces on the gate itself.
                if (examGateStatusLabel != null) {
                    examGateStatusLabel.setText(message);
                } else {
                    showCurrentStatus(message);
                }
            }

            case RequestType.SUBMIT_EXAM -> {
                if (takeExamView != null) {
                    takeExamView.setStatusMessage(message);
                } else {
                    showCurrentStatus(message);
                }
            }

            case RequestType.UPDATE_EXAM -> {
                // A refused edit (submissions exist, points do not total 100)
                // belongs on the editor the teacher is looking at.
                if (buildExamView != null) {
                    buildExamView.setStatusMessage(message);
                } else {
                    showCurrentStatus(message);
                }
            }

            case RequestType.GET_EXAM_BY_ID -> {
                // Clear the flag either way, so a failed edit fetch cannot leave
                // the editor armed for some later unrelated response.
                boolean wasOpeningEditor = examEditRequested;
                examEditRequested = false;
                if (wasOpeningEditor && examBankView != null) {
                    examBankView.setStatusMessage(message);
                } else {
                    showCurrentStatus(message);
                }
            }

            case RequestType.DUPLICATE_QUESTION,
                 RequestType.GET_QUESTION_HISTORY -> {
                if (questionStatusLabel != null) {
                    questionStatusLabel.setText(message);
                } else {
                    showCurrentStatus(message);
                }
            }

            case RequestType.GET_EXAM_RESULTS -> {
                // A refusal here is the ownership check talking; it belongs on
                // the results screen the teacher is looking at.
                if (examResultsStatusLabel != null && isShowing("Exam Results")) {
                    examResultsStatusLabel.setText(message);
                } else if (examBankView != null) {
                    examBankView.setStatusMessage(message);
                } else {
                    showCurrentStatus(message);
                }
            }

            case RequestType.GET_PENDING_GRADES,
                 RequestType.APPROVE_GRADE,
                 RequestType.OVERRIDE_GRADE -> {
                if (gradeApprovalView != null) {
                    gradeApprovalView.setStatusMessage(message);
                } else {
                    showCurrentStatus(message);
                }
            }

            case RequestType.GET_SUBMISSION_REVIEW -> {
                // A refused review (not yours, or not approved yet) belongs
                // next to the results table the student clicked from.
                if (studentResultsView != null) {
                    studentResultsView.setStatusMessage(message);
                } else {
                    showCurrentStatus(message);
                }
            }

            case RequestType.GET_PASS_FAIL_REPORT,
                 RequestType.GET_SCORE_TREND_REPORT,
                 RequestType.GET_TEACHER_ACTIVITY_REPORT,
                 RequestType.GET_GRADE_DISTRIBUTION,
                 RequestType.GET_TEACHER_STATISTICS,
                 RequestType.GET_COURSE_STATISTICS,
                 RequestType.GET_STUDENT_STATISTICS -> {
                if (reportsStatusLabel != null) {
                    reportsStatusLabel.setText(message);
                } else {
                    showCurrentStatus(message);
                }
            }

            case RequestType.GET_ALL_EXAMS -> {
                // Fired from two places: the listing screen, and the Reports
                // selectors. Report it wherever the principal is standing.
                if (isShowing("All Exams") && allExamsStatusLabel != null) {
                    allExamsStatusLabel.setText(message);
                } else if (reportsStatusLabel != null) {
                    reportsStatusLabel.setText(message);
                } else {
                    showCurrentStatus(message);
                }
            }

            case RequestType.GET_ALL_RESULTS -> {
                if (isShowing("All Results") && allResultsStatusLabel != null) {
                    allResultsStatusLabel.setText(message);
                } else if (reportsStatusLabel != null) {
                    reportsStatusLabel.setText(message);
                } else {
                    showCurrentStatus(message);
                }
            }

            case RequestType.ASK_BOT -> {
                // A refusal carries the reason the student needs -- not
                // enrolled, bot switched off, or mid-exam. Show the server's
                // own wording, and re-enable the input.
                if (botChatView != null) {
                    botChatView.failPending(message);
                } else {
                    showCurrentStatus(message);
                }
            }

            case RequestType.GET_STUDENT_BOTS,
                 RequestType.GET_MY_BOT_HISTORY -> {
                if (botChatView != null) {
                    botChatView.setStatusMessage(message);
                } else {
                    showCurrentStatus(message);
                }
            }

            case RequestType.GET_ENGINE_STATUS,
                 RequestType.GENERATE_QUESTIONS,
                 RequestType.GENERATE_EXAM,
                 RequestType.SAVE_GENERATED_QUESTIONS -> {
                // A refusal must not leave the exam flow half-armed, or the
                // next unrelated save would be mistaken for the exam's.
                pendingGeneratedExam = null;
                if (generateContentView != null) {
                    generateContentView.generationFailed(message);
                } else {
                    showCurrentStatus(message);
                }
            }

            case RequestType.GET_MY_COURSES,
                 RequestType.GET_COURSE_BOT,
                 RequestType.SAVE_BOT,
                 RequestType.SAVE_BOT_SOURCE,
                 RequestType.DELETE_BOT_SOURCE,
                 RequestType.GET_BOT_HISTORY_ANONYMOUS -> {
                if (botEditorView != null) {
                    botEditorView.setStatusMessage(message);
                } else {
                    showCurrentStatus(message);
                }
            }

            default -> showCurrentStatus(message);
        }
    }

    /**
     * Narrows a raw response list to the Question rows it contains.
     *
     * @param dataList raw response list
     * @return questions found in the list, never null
     */
    private List<Question> asQuestionList(List<?> dataList) {
        List<Question> questions = new ArrayList<>();
        for (Object element : dataList) {
            if (element instanceof Question question) questions.add(question);
        }
        return questions;
    }

    /**
     * Narrows a raw response list to the CourseBot rows it contains.
     *
     * @param dataList raw response list
     * @return bots found in the list, never null
     */
    private List<CourseBot> asBotList(List<?> dataList) {
        List<CourseBot> bots = new ArrayList<>();
        for (Object element : dataList) {
            if (element instanceof CourseBot bot) bots.add(bot);
        }
        return bots;
    }

    /**
     * Narrows a raw response list to the BotQuestion rows it contains.
     *
     * @param dataList raw response list
     * @return exchanges found in the list, never null
     */
    private List<BotQuestion> asBotQuestionList(List<?> dataList) {
        List<BotQuestion> exchanges = new ArrayList<>();
        for (Object element : dataList) {
            if (element instanceof BotQuestion exchange) exchanges.add(exchange);
        }
        return exchanges;
    }

    /**
     * Narrows a raw response list to the Course rows it contains.
     *
     * @param dataList raw response list
     * @return courses found in the list, never null
     */
    private List<Course> asCourseList(List<?> dataList) {
        List<Course> courses = new ArrayList<>();
        for (Object element : dataList) {
            if (element instanceof Course course) courses.add(course);
        }
        return courses;
    }

    /**
     * Narrows a raw response list to the Exam rows it contains.
     *
     * @param dataList raw response list
     * @return exams found in the list, never null
     */
    private List<Exam> toExams(
            List<?> dataList
    ) {

        List<Exam> exams = new ArrayList<>();

        for (Object item : dataList) {
            if (item instanceof Exam exam) {
                exams.add(exam);
            }
        }

        return exams;
    }

    /**
     * Handles the exams a student is eligible to take (GET_AVAILABLE_EXAMS).
     *
     * @param dataList returned exams
     * @param message server message
     */
    private void handleAvailableExamList(
            List<?> dataList,
            String message
    ) {

        availableExams.setAll(toExams(dataList));

        String currentTitle = primaryStage.getTitle();

        if (currentTitle.contains("Schedule")
                || currentTitle.contains("Active Exams")) {

            populateExamList(availableExams);
            return;
        }

        if (studentDashboardView != null) {

            studentDashboardView.setAvailableExams(
                    availableExams
            );

            studentDashboardView.setStatusMessage(
                    message
            );
        }
    }

    /**
     * Handles the teacher's own exams (GET_TEACHER_EXAMS). The same action
     * serves the Exam Bank table, the Reports screen's table and cards, and
     * the Teacher Dashboard stat cards, so the active screen decides which
     * one is refreshed.
     *
     * @param dataList returned exams
     * @param message server message
     */
    private void handleTeacherExamList(
            List<?> dataList,
            String message
    ) {

        List<Exam> exams = toExams(dataList);

        if (primaryStage.getTitle().contains("My Reports")) {

            teacherReportsExams.setAll(exams);

            long active = exams.stream().filter(Exam::isActive).count();
            long pending = exams.stream()
                    .filter(e -> "PENDING".equalsIgnoreCase(e.getApprovalStatus()))
                    .count();

            teacherReportsExamCountLabel.setText(String.valueOf(exams.size()));
            teacherReportsActiveLabel.setText(String.valueOf(active));
            teacherReportsPendingLabel.setText(String.valueOf(pending));
            teacherReportsStatusLabel.setText(message);

            // The Submissions / Average cells read from a map the performance
            // response fills, which may already have arrived.
            teacherReportsTable.refresh();
            return;
        }

        if (examBankView != null
                && primaryStage.getTitle().contains("Exam Bank")) {

            examBankView.setExams(exams);

            examBankView.setStatusMessage(
                    message + "  Total: " + exams.size()
            );

            return;
        }

        if (teacherExamCountLabel != null) {
            teacherExamCountLabel.setText(String.valueOf(exams.size()));
        }

        if (teacherActiveCountLabel != null) {

            long active = exams.stream().filter(Exam::isActive).count();
            teacherActiveCountLabel.setText(String.valueOf(active));
        }
    }

    /**
     * Handles exams awaiting approval (GET_PENDING_EXAMS). Serves both the
     * Approve Exams table and the Admin Dashboard stat card.
     *
     * @param dataList returned exams
     * @param message server message
     */
    private void handlePendingExamList(
            List<?> dataList,
            String message
    ) {

        List<Exam> exams = toExams(dataList);

        if (approveExamView != null
                && primaryStage.getTitle().contains("Approve Exams")) {

            approveExamView.setPendingExams(exams);
            approveExamView.setStatusMessage(message);
            return;
        }

        if (adminPendingLabel != null) {
            adminPendingLabel.setText(String.valueOf(exams.size()));
        }
    }

    /**
     * Handles the receipt returned after a student submits an exam.
     *
     * <p>Since the grade-approval gate was added, this receipt carries no
     * usable grade — it is stamped AWAITING_APPROVAL and the score is not
     * sent. Such a receipt is deliberately NOT added to the results list: it
     * would show as a 0 next to real grades. The result appears in Student
     * Results only once the teacher approves it and GET_STUDENT_RESULTS
     * (which returns GRADED rows only) picks it up.
     *
     * @param result submission receipt
     * @param message server message
     */
    private void handleSubmittedExam(
            ExamResult result,
            String message
    ) {

        if (!"AWAITING_APPROVAL".equalsIgnoreCase(result.getStatus())) {

            studentResults.add(result);

            if (studentResultsView != null) {
                studentResultsView.setResults(studentResults);
            }
        }

        // The sitting is over, so the bot entry point comes back. The
        // server's own lockout lifts at the same moment for the same reason:
        // the submission row has left PENDING.
        examInProgress = false;

        if (takeExamView != null
                && primaryStage.getTitle().contains("Take Exam")) {

            takeExamView.lockAfterSubmission(message);

        } else {
            showCurrentStatus(message);
        }
    }

    /**
     * Handles a successful login.
     *
     * @param user logged-in user
     */
    private void handleSuccessfulLogin(
            User user
    ) {

        currentUser = user;

        if (currentUser.getRole() == null) {

            loginButton.setDisable(false);

            loginStatusLabel.setText(
                    "The server returned a user without a role."
            );

            return;
        }

        // Load this user's saved theme / accessibility preferences. The response
        // arrives asynchronously and is re-applied to whatever scene is showing.
        loadUserSettings();

        if ("TEACHER".equalsIgnoreCase(
                currentUser.getRole()
        )) {

            showTeacherDashboard();
            return;
        }

        if ("STUDENT".equalsIgnoreCase(
                currentUser.getRole()
        )) {

            showStudentDashboard();
            return;
        }

        if ("PRINCIPAL".equalsIgnoreCase(
                currentUser.getRole()
        )) {

            showPrincipalDashboard();
            return;
        }

        loginButton.setDisable(false);

        loginStatusLabel.setText(
                "Unsupported user role: "
                        + currentUser.getRole()
        );
    }

    private void handleProfileUpdated(User updated, String message) {
        currentUser.setFullName(updated.getFullName());
        currentUser.setAvatarUrl(updated.getAvatarUrl());
        if (profileStatusLabel != null) {
            profileStatusLabel.setText(message);
        }
    }

    /**
     * Displays the teacher dashboard.
     */
    private void showTeacherDashboard() {

        // ── Sidebar ───────────────────────────────────────
        VBox sidebar = buildRoleSidebar("TEACHER", "Dashboard");

        // ── Top bar ───────────────────────────────────────
        TextField searchBox = new TextField();
        searchBox.setPromptText("Search anything...");
        searchBox.getStyleClass().add("search-field");
        HBox topBar = new HBox(searchBox);
        topBar.getStyleClass().add("top-bar");
        topBar.setAlignment(Pos.CENTER_LEFT);

        // ── Welcome ───────────────────────────────────────
        Label welcomeLabel = new Label("Welcome back, " + getCurrentUserDisplayName() + " 👋");
        welcomeLabel.getStyleClass().add("welcome-label");

        // ── Stat cards ────────────────────────────────────
        teacherQCountLabel    = new Label("—");
        teacherExamCountLabel = new Label("—");
        teacherActiveCountLabel = new Label("—");
        HBox statsRow = new HBox(14,
                buildStatCard("Questions", teacherQCountLabel,     "in bank"),
                buildStatCard("Exam Bank", teacherExamCountLabel,  "exams"),
                buildStatCard("Active",    teacherActiveCountLabel, "running"),
                buildStatCard("Reports",   "View",                  "analytics"));
        statsRow.setAlignment(Pos.CENTER_LEFT);

        // ── Quick-action buttons ──────────────────────────
        Button qbBtn  = buildQuickBtn("Question Bank",  "go to Question Bank",   "quick-btn-pink",   this::showQuestionBankScreen);
        Button beBtn  = buildQuickBtn("Build Exam",     "create a new exam",     "quick-btn-peach",  this::showBuildExamScreen);
        Button ebBtn  = buildQuickBtn("Exam Bank",      "go to Exam Bank",       "quick-btn-sky",    this::showExamBankScreen);
        Button rpBtn  = buildQuickBtn("Reports",        "open statistics",       "quick-btn-purple", this::showTeacherReportsScreen);
        HBox quickRow = new HBox(14, qbBtn, beBtn, ebBtn, rpBtn);
        quickRow.setAlignment(Pos.CENTER_LEFT);

        // ── Bottom: chart + AI insights ───────────────────
        teacherChartContainer = new VBox(8);
        teacherChartContainer.getStyleClass().add("section-card");
        Label chartTitle = new Label("Performance / analytics chart");
        chartTitle.getStyleClass().add("section-title");
        Label chartPlaceholder = new Label("Loading performance data...");
        chartPlaceholder.setStyle("-fx-font-size: 12px; -fx-text-fill: -hsts-muted;");
        teacherChartContainer.getChildren().addAll(chartTitle, chartPlaceholder);
        HBox.setHgrow(teacherChartContainer, Priority.ALWAYS);

        VBox aiCard = new VBox(8);
        aiCard.getStyleClass().add("section-card");
        aiCard.setPrefWidth(230);
        Label aiTitle = new Label("AI Insights");
        aiTitle.getStyleClass().add("section-title");
        Label aiText = new Label("Students struggled with recent questions. Average decreased by 6%. Suggested review topic: Data Structures.");
        aiText.setWrapText(true);
        aiText.setStyle("-fx-font-size: 13px; -fx-text-fill: -hsts-muted;");
        aiCard.getChildren().addAll(aiTitle, aiText);

        HBox bottomRow = new HBox(14, teacherChartContainer, aiCard);
        bottomRow.setAlignment(Pos.TOP_LEFT);

        // ── Main content ──────────────────────────────────
        VBox mainContent = new VBox(16, topBar, welcomeLabel, statsRow, quickRow, bottomRow);
        mainContent.setPadding(new Insets(0, 0, 0, 20));
        VBox.setVgrow(bottomRow, Priority.ALWAYS);
        HBox.setHgrow(mainContent, Priority.ALWAYS);

        HBox root = new HBox(16, sidebar, mainContent);
        root.getStyleClass().add("login-background");
        root.setPadding(new Insets(22));

        Scene scene = new Scene(root, windowWidth(), windowHeight());
        applyStyleSheet(scene);
        primaryStage.setScene(scene);
        primaryStage.setTitle("HSTS - Teacher Dashboard");
        applyStandardWindowSize();

        try {
            clientController.requestAllQuestions();
            clientController.requestTeacherExams(currentUser.getUserId());
            clientController.requestTeacherExamPerformance(currentUser.getUserId());
        } catch (IOException e) {
            System.err.println("Teacher Dashboard stat load failed: " + e.getMessage());
        }
    }

    /**
     * Displays the teacher's own Reports screen: how the exams they wrote are
     * actually performing.
     *
     * Built only from data a teacher is entitled to. The principal's Reports
     * screen is system-wide — pass/fail across every course, every teacher's
     * activity, the grade distribution, the comparison selectors — and the
     * server refuses most of that to a teacher anyway (requirePrincipal). What
     * is left is this teacher's own material: their exams, the average and the
     * volume of submissions on each, and a way into the per-exam results table
     * they already own.
     */
    private void showTeacherReportsScreen() {

        // ── Sidebar ─────────────────────────────────────────────
        VBox sidebar = buildRoleSidebar("TEACHER", "Reports");

        // ── Top bar ────────────────────────────────────────────
        TextField searchBox = new TextField();
        searchBox.setPromptText("Search anything...");
        searchBox.getStyleClass().add("search-field");
        HBox topBar = new HBox(searchBox);
        topBar.getStyleClass().add("top-bar");
        topBar.setAlignment(Pos.CENTER_LEFT);

        Label pageTitle = new Label("My Reports");
        pageTitle.getStyleClass().add("welcome-label");

        teacherReportsStatusLabel = new Label("Loading reports...");
        teacherReportsStatusLabel.setStyle(
                "-fx-font-size: 12px; -fx-text-fill: -hsts-muted;");

        // ── Stat cards ────────────────────────────────────────
        teacherReportsExamCountLabel = new Label("—");
        teacherReportsActiveLabel    = new Label("—");
        teacherReportsPendingLabel   = new Label("—");
        teacherReportsQuestionLabel  = new Label("—");
        teacherReportsAverageLabel   = new Label("—");

        HBox statsRow = new HBox(14,
                buildStatCard("Exams",     teacherReportsExamCountLabel, "written by you"),
                buildStatCard("Active",    teacherReportsActiveLabel,    "running now"),
                buildStatCard("Pending",   teacherReportsPendingLabel,   "awaiting approval"),
                buildStatCard("Questions", teacherReportsQuestionLabel,  "authored by you"),
                buildStatCard("Average",   teacherReportsAverageLabel,   "graded exams"));
        statsRow.setAlignment(Pos.CENTER_LEFT);

        // ── Chart cards ──────────────────────────────────────
        teacherReportsAverageChart = new VBox(8);
        teacherReportsAverageChart.getStyleClass().add("section-card");
        Label averageTitle = new Label("Average Score per Exam");
        averageTitle.getStyleClass().add("section-title");
        teacherReportsAverageChart.getChildren().add(averageTitle);
        HBox.setHgrow(teacherReportsAverageChart, Priority.ALWAYS);

        teacherReportsVolumeChart = new VBox(8);
        teacherReportsVolumeChart.getStyleClass().add("section-card");
        Label volumeTitle = new Label("Submissions per Exam");
        volumeTitle.getStyleClass().add("section-title");
        teacherReportsVolumeChart.getChildren().add(volumeTitle);
        HBox.setHgrow(teacherReportsVolumeChart, Priority.ALWAYS);

        HBox chartRow = new HBox(14, teacherReportsAverageChart, teacherReportsVolumeChart);
        chartRow.setAlignment(Pos.TOP_LEFT);

        // ── Per-exam table ───────────────────────────────────
        teacherReportsTable = new TableView<>();
        teacherReportsTable.setItems(teacherReportsExams);
        teacherReportsTable.setPlaceholder(
                new Label("You have not written any exams yet."));
        teacherReportsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        teacherReportsTable.getColumns().add(buildTextColumn("Exam", 240,
                row -> displayName(row.getTitle(), "Exam", row.getExamId())));
        teacherReportsTable.getColumns().add(buildTextColumn("Course", 150,
                row -> blankToPlaceholder(row.getCourse(), "—")));
        teacherReportsTable.getColumns().add(buildTextColumn("Questions", 90,
                row -> String.valueOf(row.getQuestionCount())));
        teacherReportsTable.getColumns().add(buildTextColumn("Approval", 110,
                row -> blankToPlaceholder(row.getApprovalStatus(), "—")));
        teacherReportsTable.getColumns().add(buildTextColumn("Active", 80,
                row -> row.isActive() ? "Yes" : "No"));
        teacherReportsTable.getColumns().add(buildTextColumn("Submissions", 110,
                row -> examPerformanceText(row, false)));
        teacherReportsTable.getColumns().add(buildTextColumn("Average", 100,
                row -> examPerformanceText(row, true)));

        teacherReportsTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                openExamResultsFromReports(
                        teacherReportsTable.getSelectionModel().getSelectedItem());
            }
        });

        VBox.setVgrow(teacherReportsTable, Priority.ALWAYS);

        Button viewResultsButton = new Button("View Results");
        viewResultsButton.getStyleClass().add("primary-button");
        viewResultsButton.setOnAction(event ->
                openExamResultsFromReports(
                        teacherReportsTable.getSelectionModel().getSelectedItem()));

        Button refreshButton = new Button("Refresh");
        refreshButton.getStyleClass().add("secondary-button");
        refreshButton.setOnAction(event -> loadTeacherReports());

        Button backButton = new Button("Back to Dashboard");
        backButton.getStyleClass().add("secondary-button");
        backButton.setOnAction(event -> showTeacherDashboard());

        Region actionSpacer = new Region();
        HBox.setHgrow(actionSpacer, Priority.ALWAYS);

        HBox actionRow = new HBox(12, viewResultsButton, refreshButton,
                actionSpacer, backButton);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        VBox tableCard = new VBox(10, actionRow, teacherReportsTable);
        tableCard.getStyleClass().add("section-card");
        VBox.setVgrow(tableCard, Priority.ALWAYS);

        // ── Main content ─────────────────────────────────────
        VBox mainContent = new VBox(14, topBar, pageTitle, teacherReportsStatusLabel,
                statsRow, chartRow, tableCard);
        mainContent.setPadding(new Insets(0, 0, 0, 20));
        HBox.setHgrow(mainContent, Priority.ALWAYS);

        HBox root = new HBox(16, sidebar, mainContent);
        root.getStyleClass().add("login-background");
        root.setPadding(new Insets(22));

        Scene scene = new Scene(root, windowWidth(), windowHeight());
        applyStyleSheet(scene);
        primaryStage.setScene(scene);
        primaryStage.setTitle("HSTS - My Reports");
        applyStandardWindowSize();

        loadTeacherReports();
    }

    /**
     * Requests everything the Reports screen draws. Also the Refresh button.
     */
    private void loadTeacherReports() {

        if (currentUser == null) {
            return;
        }

        try {
            clientController.requestTeacherExams(currentUser.getUserId());
            clientController.requestTeacherExamPerformance(currentUser.getUserId());
            clientController.requestAllQuestions();
        } catch (IOException e) {
            teacherReportsStatusLabel.setText(
                    "Could not load reports: " + e.getMessage());
        }
    }

    /**
     * Opens the per-exam results screen for the row selected on Reports, with
     * its Back button pointing here rather than at the Exam Bank.
     *
     * @param exam the selected exam, or null when nothing is selected
     */
    private void openExamResultsFromReports(Exam exam) {

        if (exam == null) {
            teacherReportsStatusLabel.setText("Select an exam first.");
            return;
        }

        showExamResultsScreen(exam, "Reports", "Back to Reports",
                this::showTeacherReportsScreen);
    }

    /**
     * One cell of the Reports table's Submissions / Average columns.
     *
     * Matched by exam title, because the performance rows carry no exam id, and
     * only across the ten most recent graded exams the server returns. Anything
     * older, or with no graded submission yet, reads as a dash rather than a
     * misleading zero.
     *
     * @param exam the row being rendered
     * @param average true for the average score, false for the submission count
     * @return the cell text
     */
    private String examPerformanceText(Exam exam, boolean average) {

        TeacherExamPerformance point =
                exam == null || exam.getTitle() == null
                        ? null
                        : teacherPerformanceByExam.get(exam.getTitle());

        if (point == null) {
            return "—";
        }

        return average
                ? String.format("%.1f", point.getAverageScore())
                : String.valueOf(point.getSubmissionCount());
    }

    /**
     * The principal's Users screen: every account, and add / edit / delete.
     *
     * The server is the authority on all of it — this screen sends what the
     * principal typed and reports what comes back. Three refusals it cannot
     * pre-empt and does not try to: a duplicate username, an account that is
     * referenced by exams or submissions and so cannot be deleted, and the
     * principal editing themselves out of their own role.
     */
    private void showUserManagementScreen() {

        // ── Sidebar ─────────────────────────────────────────────
        VBox sidebar = buildRoleSidebar("PRINCIPAL", "Users");

        // ── Top bar ────────────────────────────────────────────
        TextField searchBox = new TextField();
        searchBox.setPromptText("Search anything...");
        searchBox.getStyleClass().add("search-field");
        HBox topBar = new HBox(searchBox);
        topBar.getStyleClass().add("top-bar");
        topBar.setAlignment(Pos.CENTER_LEFT);

        Label pageTitle = new Label("Users");
        pageTitle.getStyleClass().add("welcome-label");

        Label subtitle = new Label(
                "Teachers, students and principals. A password is only ever set here, never shown.");
        subtitle.setStyle("-fx-font-size: 13px; -fx-text-fill: -hsts-muted;");

        usersStatusLabel = new Label("Loading accounts...");
        usersStatusLabel.getStyleClass().add("status-label");

        // ── Table ──────────────────────────────────────────────
        usersTable = new TableView<>();
        usersTable.setItems(allUsers);
        usersTable.setPlaceholder(new Label("No accounts to show."));
        usersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        usersTable.getColumns().add(buildTextColumn("ID", 60,
                row -> String.valueOf(row.getUserId())));
        usersTable.getColumns().add(buildTextColumn("Username", 150,
                row -> blankToPlaceholder(row.getUsername(), "—")));
        usersTable.getColumns().add(buildTextColumn("Full name", 220,
                row -> blankToPlaceholder(row.getFullName(), "—")));
        usersTable.getColumns().add(buildTextColumn("Role", 120,
                row -> blankToPlaceholder(row.getRole(), "—")));
        usersTable.getColumns().add(buildTextColumn("Grade", 80,
                row -> row.getGradeLevel() == null ? "—" : String.valueOf(row.getGradeLevel())));
        usersTable.getColumns().add(buildTextColumn("National ID", 130,
                row -> blankToPlaceholder(row.getNationalId(), "—")));

        usersTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                openUserDialog(usersTable.getSelectionModel().getSelectedItem());
            }
        });

        VBox.setVgrow(usersTable, Priority.ALWAYS);

        // ── Actions ────────────────────────────────────────────
        Button addButton = new Button("Add User");
        addButton.getStyleClass().add("success-button");
        addButton.setOnAction(event -> openUserDialog(null));

        Button editButton = new Button("Edit");
        editButton.getStyleClass().add("primary-button");
        editButton.setOnAction(event ->
                openUserDialog(usersTable.getSelectionModel().getSelectedItem()));

        Button deleteButton = new Button("Delete");
        deleteButton.getStyleClass().add("danger-button");
        deleteButton.setOnAction(event ->
                confirmDeleteUser(usersTable.getSelectionModel().getSelectedItem()));

        Button refreshButton = new Button("Refresh");
        refreshButton.getStyleClass().add("secondary-button");
        refreshButton.setOnAction(event -> loadAllUsers());

        Button backButton = new Button("Back to Dashboard");
        backButton.getStyleClass().add("secondary-button");
        backButton.setOnAction(event -> showPrincipalDashboard());

        Region actionSpacer = new Region();
        HBox.setHgrow(actionSpacer, Priority.ALWAYS);

        HBox actionRow = new HBox(12, addButton, editButton, deleteButton,
                refreshButton, actionSpacer, backButton);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        VBox tableCard = new VBox(10, actionRow, usersTable);
        tableCard.getStyleClass().add("section-card");
        VBox.setVgrow(tableCard, Priority.ALWAYS);

        VBox mainContent = new VBox(14, topBar, pageTitle, subtitle,
                usersStatusLabel, tableCard);
        mainContent.setPadding(new Insets(0, 0, 0, 20));
        HBox.setHgrow(mainContent, Priority.ALWAYS);

        HBox root = new HBox(16, sidebar, mainContent);
        root.getStyleClass().add("login-background");
        root.setPadding(new Insets(22));

        Scene scene = new Scene(root, windowWidth(), windowHeight());
        applyStyleSheet(scene);
        primaryStage.setScene(scene);
        primaryStage.setTitle("HSTS - Users");
        applyStandardWindowSize();

        loadAllUsers();
    }

    /** Fetches the account list. Also the Refresh button. */
    private void loadAllUsers() {
        try {
            clientController.requestAllUsers();
        } catch (IllegalArgumentException | IOException e) {
            usersStatusLabel.setText("Could not load users: " + e.getMessage());
        }
    }

    /**
     * The add / edit form. One dialog for both, because the fields are the
     * same and the only difference is what the password box means.
     *
     * @param existing the account being edited, or null to create a new one
     */
    private void openUserDialog(User existing) {

        boolean editing = existing != null;

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(editing ? "Edit User" : "Add User");
        dialog.setHeaderText(editing
                ? "Editing " + displayName(existing.getFullName(), "User", existing.getUserId())
                : "Create a new account");

        ButtonType saveType = new ButtonType(
                editing ? "Save Changes" : "Create Account",
                javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        TextField usernameField = new TextField(editing ? existing.getUsername() : "");
        usernameField.setPromptText("no spaces");

        TextField fullNameField = new TextField(editing ? existing.getFullName() : "");

        ComboBox<String> roleCombo = new ComboBox<>();
        roleCombo.getItems().addAll("STUDENT", "TEACHER", "PRINCIPAL");
        roleCombo.setValue(editing && existing.getRole() != null
                ? existing.getRole().toUpperCase() : "STUDENT");
        roleCombo.setMaxWidth(Double.MAX_VALUE);

        ComboBox<String> gradeCombo = new ComboBox<>();
        gradeCombo.getItems().addAll("9", "10", "11", "12");
        if (editing && existing.getGradeLevel() != null) {
            gradeCombo.setValue(String.valueOf(existing.getGradeLevel()));
        }
        gradeCombo.setMaxWidth(Double.MAX_VALUE);

        // Only a student has a grade level -- the users table CHECKs exactly
        // that, so the form should not let the principal build a row the
        // database will refuse.
        Label gradeLabel = new Label("Grade level:");
        Runnable syncGrade = () -> {
            boolean student = "STUDENT".equals(roleCombo.getValue());
            gradeCombo.setDisable(!student);
            gradeLabel.setDisable(!student);
            if (!student) {
                gradeCombo.setValue(null);
            } else if (gradeCombo.getValue() == null) {
                gradeCombo.setValue("9");
            }
        };
        roleCombo.setOnAction(event -> syncGrade.run());
        syncGrade.run();

        TextField nationalIdField = new TextField(editing ? existing.getNationalId() : "");
        nationalIdField.setPromptText("9 digits, optional");

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText(editing ? "leave blank to keep current" : "required");

        Label dialogError = new Label();
        dialogError.setWrapText(true);
        dialogError.setStyle("-fx-text-fill: -hsts-negative; -fx-font-size: 12px;");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(14));
        grid.add(new Label("Username:"), 0, 0);      grid.add(usernameField, 1, 0);
        grid.add(new Label("Full name:"), 0, 1);     grid.add(fullNameField, 1, 1);
        grid.add(new Label("Role:"), 0, 2);          grid.add(roleCombo, 1, 2);
        grid.add(gradeLabel, 0, 3);                  grid.add(gradeCombo, 1, 3);
        grid.add(new Label("National ID:"), 0, 4);   grid.add(nationalIdField, 1, 4);
        grid.add(new Label("Password:"), 0, 5);      grid.add(passwordField, 1, 5);
        grid.add(dialogError, 0, 6, 2, 1);
        dialog.getDialogPane().setContent(grid);
        applyStyleSheet(dialog.getDialogPane().getScene());

        // Catch the obvious mistakes here so the dialog stays open with the
        // typing intact. Everything else is the server's call.
        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveType);
        saveButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
            String fullName = fullNameField.getText() == null ? "" : fullNameField.getText().trim();
            String nationalId = nationalIdField.getText() == null ? "" : nationalIdField.getText().trim();

            if (username.isEmpty() || fullName.isEmpty()) {
                dialogError.setText("Username and full name are required.");
                event.consume();
            } else if (username.contains(" ")) {
                dialogError.setText("A username cannot contain spaces.");
                event.consume();
            } else if (!editing && passwordField.getText().isEmpty()) {
                dialogError.setText("A new account needs a password.");
                event.consume();
            } else if (!nationalId.isEmpty() && !nationalId.matches("[0-9]{9}")) {
                dialogError.setText("A national ID must be exactly 9 digits.");
                event.consume();
            } else if ("STUDENT".equals(roleCombo.getValue()) && gradeCombo.getValue() == null) {
                dialogError.setText("A student needs a grade level.");
                event.consume();
            }
        });

        dialog.showAndWait().ifPresent(result -> {
            if (result != saveType) {
                return;
            }

            User user = new User();
            user.setUserId(editing ? existing.getUserId() : 0);
            user.setUsername(usernameField.getText().trim());
            user.setFullName(fullNameField.getText().trim());
            user.setRole(roleCombo.getValue());
            user.setGradeLevel("STUDENT".equals(roleCombo.getValue()) && gradeCombo.getValue() != null
                    ? Integer.valueOf(gradeCombo.getValue())
                    : null);
            user.setNationalId(nationalIdField.getText().trim());
            user.setPassword(passwordField.getText());
            if (editing) {
                user.setAvatarUrl(existing.getAvatarUrl());
            }

            try {
                if (editing) {
                    clientController.requestUpdateUser(user);
                } else {
                    clientController.requestCreateUser(user);
                }
                usersStatusLabel.setText(editing ? "Saving changes..." : "Creating account...");
            } catch (IllegalArgumentException | IOException e) {
                usersStatusLabel.setText("Could not save: " + e.getMessage());
            }
        });
    }

    /**
     * Confirms, then asks the server to delete. The server refuses an account
     * that other rows still reference, and refuses the principal's own.
     *
     * @param user the selected account, or null when nothing is selected
     */
    private void confirmDeleteUser(User user) {

        if (user == null) {
            usersStatusLabel.setText("Select an account first.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete User");
        confirm.setHeaderText("Delete " + displayName(user.getFullName(), "User", user.getUserId()) + "?");
        confirm.setContentText("This cannot be undone.");
        applyStyleSheet(confirm.getDialogPane().getScene());

        confirm.showAndWait().ifPresent(choice -> {
            if (choice != ButtonType.OK) {
                return;
            }
            try {
                clientController.requestDeleteUser(user.getUserId());
                usersStatusLabel.setText("Deleting...");
            } catch (IllegalArgumentException | IOException e) {
                usersStatusLabel.setText("Could not delete: " + e.getMessage());
            }
        });
    }

    /**
     * Handles the account list (GET_ALL_USERS).
     *
     * @param dataList returned users
     * @param message server message
     */
    private void handleUserList(List<?> dataList, String message) {

        List<User> users = new ArrayList<>();
        for (Object item : dataList) {
            if (item instanceof User user) users.add(user);
        }

        allUsers.setAll(users);

        if (usersStatusLabel != null) {
            usersStatusLabel.setText(message);
        }
    }

    /**
     * Handles one account coming back from a create or an update. The list is
     * amended in place rather than re-fetched: the server returns the stored
     * row, so the table can show exactly what was saved without a round trip.
     *
     * @param user the stored account
     * @param message server message
     */
    private void handleSavedUser(User user, String message) {

        boolean replaced = false;
        for (int i = 0; i < allUsers.size(); i++) {
            if (allUsers.get(i).getUserId() == user.getUserId()) {
                allUsers.set(i, user);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            allUsers.add(user);
        }

        if (usersTable != null) {
            usersTable.getSelectionModel().select(user);
        }
        if (usersStatusLabel != null) {
            usersStatusLabel.setText(message);
        }
    }

    /**
     * Handles a deleted account (DELETE_USER), whose payload is the id.
     *
     * @param userId the deleted account
     * @param message server message
     */
    private void handleDeletedUser(int userId, String message) {

        allUsers.removeIf(user -> user.getUserId() == userId);

        if (usersStatusLabel != null) {
            usersStatusLabel.setText(message);
        }
    }

    /**
     * Displays the principal dashboard.
     */
    private void showPrincipalDashboard() {

        // ── Sidebar ───────────────────────────────────────
        VBox sidebar = buildRoleSidebar("PRINCIPAL", "Dashboard");

        // ── Top bar ───────────────────────────────────────
        TextField searchBox = new TextField();
        searchBox.setPromptText("Search anything...");
        searchBox.getStyleClass().add("search-field");
        HBox topBar = new HBox(searchBox);
        topBar.getStyleClass().add("top-bar");
        topBar.setAlignment(Pos.CENTER_LEFT);

        // ── Title + stat cards ────────────────────────────
        Label dashTitle = new Label("Admin Dashboard");
        dashTitle.getStyleClass().add("welcome-label");

        adminPendingLabel = new Label("—");
        adminQCountLabel  = new Label("—");
        HBox statsRow = new HBox(14,
                buildStatCard("Approve",   adminPendingLabel, "pending"),
                buildStatCard("Questions", adminQCountLabel,  "in bank"),
                buildStatCard("Teachers",  "—",               "users"),
                buildStatCard("Students",  "—",               "users"));
        statsRow.setAlignment(Pos.CENTER_LEFT);

        // ── Quick-action buttons ──────────────────────────
        Button approveBtn = buildQuickBtn("Approve Exam",    "approve or reject",     "quick-btn-peach",  this::showApproveExamScreen);
        Button qbBtn      = buildQuickBtn("Question Bank",   "manage all questions",  "quick-btn-pink",   this::showReadOnlyQuestionBankScreen);
        Button reportsBtn = buildQuickBtn("Reports",         "open system reports",   "quick-btn-purple", this::showReportsDashboard);
        Button examsBtn   = buildQuickBtn("All Exams",       "every exam in system",  "quick-btn-green",  this::showAllExamsScreen);
        Button resultsBtn = buildQuickBtn("All Results",     "every graded result",   "quick-btn-sky",    this::showAllResultsScreen);
        HBox quickRow = new HBox(14, approveBtn, qbBtn, reportsBtn, examsBtn, resultsBtn);
        quickRow.setAlignment(Pos.CENTER_LEFT);

        // ── Bottom: recent activity + chart ───────────────
        VBox activityCard = new VBox(8);
        activityCard.getStyleClass().add("section-card");
        activityCard.setPrefWidth(350);
        Label actTitle = new Label("Recent Activity");
        actTitle.getStyleClass().add("section-title");
        Label actText = new Label("New exam added.  Teacher registered.  Student registered.  Exam approved.");
        actText.setWrapText(true);
        actText.setStyle("-fx-font-size: 13px; -fx-text-fill: -hsts-muted;");
        activityCard.getChildren().addAll(actTitle, actText);

        VBox chartCard = new VBox(8);
        chartCard.getStyleClass().add("section-card");
        Label chartTitle = new Label("Performance / analytics chart");
        chartTitle.getStyleClass().add("section-title");
        Canvas chartCanvas = buildLineChart(new double[]{20, 40, 55, 45, 70, 65, 88}, "#F9A8D4", 500, 150);
        chartCard.getChildren().addAll(chartTitle, chartCanvas);
        HBox.setHgrow(chartCard, Priority.ALWAYS);

        HBox bottomRow = new HBox(14, activityCard, chartCard);
        bottomRow.setAlignment(Pos.TOP_LEFT);

        // ── Main content ──────────────────────────────────
        VBox mainContent = new VBox(16, topBar, dashTitle, statsRow, quickRow, bottomRow);
        mainContent.setPadding(new Insets(0, 0, 0, 20));
        VBox.setVgrow(bottomRow, Priority.ALWAYS);
        HBox.setHgrow(mainContent, Priority.ALWAYS);

        HBox root = new HBox(16, sidebar, mainContent);
        root.getStyleClass().add("login-background");
        root.setPadding(new Insets(22));

        Scene scene = new Scene(root, windowWidth(), windowHeight());
        applyStyleSheet(scene);
        primaryStage.setScene(scene);
        primaryStage.setTitle("HSTS - Admin Dashboard");
        applyStandardWindowSize();

        try {
            clientController.requestPendingExams();
            clientController.requestAllQuestions();
        } catch (IOException e) {
            System.err.println("Admin Dashboard stat load failed: " + e.getMessage());
        }
    }

    /**
     * Displays the administrator's Reports screen: exam pass rate by
     * course, average score over time, and teacher activity — each card
     * is populated once its server response arrives.
     */
    private void showReportsDashboard() {

        // ── Sidebar ───────────────────────────────────────
        VBox sidebar = buildRoleSidebar("PRINCIPAL", "Reports");

        // ── Top bar ───────────────────────────────────────
        TextField searchBox = new TextField();
        searchBox.setPromptText("Search anything...");
        searchBox.getStyleClass().add("search-field");
        HBox topBar = new HBox(searchBox);
        topBar.getStyleClass().add("top-bar");
        topBar.setAlignment(Pos.CENTER_LEFT);

        Label dashTitle = new Label("Admin Reports");
        dashTitle.getStyleClass().add("welcome-label");

        reportsStatusLabel = new Label("Loading reports…");
        reportsStatusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: -hsts-muted;");

        // ── Report cards (populated once server data arrives) ─────
        passFailChartContainer = new VBox(8);
        passFailChartContainer.getStyleClass().add("section-card");
        Label pfTitle = new Label("Exam Pass Rate by Course");
        pfTitle.getStyleClass().add("section-title");
        passFailChartContainer.getChildren().add(pfTitle);
        HBox.setHgrow(passFailChartContainer, Priority.ALWAYS);

        scoreTrendChartContainer = new VBox(8);
        scoreTrendChartContainer.getStyleClass().add("section-card");
        Label stTitle = new Label("Average Score Over Time");
        stTitle.getStyleClass().add("section-title");
        scoreTrendChartContainer.getChildren().add(stTitle);
        HBox.setHgrow(scoreTrendChartContainer, Priority.ALWAYS);

        HBox topRow = new HBox(14, passFailChartContainer, scoreTrendChartContainer);
        topRow.setAlignment(Pos.TOP_LEFT);

        teacherActivityChartContainer = new VBox(8);
        teacherActivityChartContainer.getStyleClass().add("section-card");
        Label taTitle = new Label("Teacher Activity");
        taTitle.getStyleClass().add("section-title");
        teacherActivityChartContainer.getChildren().add(taTitle);

        // ── Distribution + comparison cards (spec 12) ─────
        // Each keeps its header row as child 0 so clearReportContainer()
        // redraws the chart without taking the selectors with it.
        distributionChartContainer = new VBox(8);
        distributionChartContainer.getStyleClass().add("section-card");
        distributionChartContainer.getChildren().add(buildDistributionHeader());
        HBox.setHgrow(distributionChartContainer, Priority.ALWAYS);

        comparisonChartContainer = new VBox(8);
        comparisonChartContainer.getStyleClass().add("section-card");
        comparisonChartContainer.getChildren().add(buildComparisonHeader());
        HBox.setHgrow(comparisonChartContainer, Priority.ALWAYS);

        HBox bottomRow = new HBox(14, distributionChartContainer, comparisonChartContainer);
        bottomRow.setAlignment(Pos.TOP_LEFT);

        // ── Main content ──────────────────────────────────
        // Five cards no longer fit on one screen, so the stack scrolls.
        VBox cardStack = new VBox(16, topRow, teacherActivityChartContainer, bottomRow);

        javafx.scene.control.ScrollPane cardScroll = new javafx.scene.control.ScrollPane(cardStack);
        cardScroll.setFitToWidth(true);
        cardScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        VBox.setVgrow(cardScroll, Priority.ALWAYS);

        VBox mainContent = new VBox(16, topBar, dashTitle, reportsStatusLabel, cardScroll);
        mainContent.setPadding(new Insets(0, 0, 0, 20));
        HBox.setHgrow(mainContent, Priority.ALWAYS);

        HBox root = new HBox(16, sidebar, mainContent);
        root.getStyleClass().add("login-background");
        root.setPadding(new Insets(22));

        Scene scene = new Scene(root, windowWidth(), windowHeight());
        applyStyleSheet(scene);
        primaryStage.setScene(scene);
        primaryStage.setTitle("HSTS - Reports");
        applyStandardWindowSize();

        try {
            clientController.requestPassFailReport();
            clientController.requestScoreTrendReport();
            clientController.requestTeacherActivityReport();
            clientController.requestGradeDistribution(null);

            // The comparison selectors have no listing endpoints of their own:
            // the teachers are distilled from the system-wide exam list and the
            // students from the system-wide results.
            clientController.requestAllExams();
            clientController.requestAllResults();
            clientController.requestAllCourses();
        } catch (IOException e) {
            reportsStatusLabel.setText("Could not load reports: " + e.getMessage());
        }
    }

    /**
     * Displays a read-only view of the question bank for principals.
     */
    private void showReadOnlyQuestionBankScreen() {

        // ── Sidebar ───────────────────────────────────────
        VBox sidebar = buildRoleSidebar("PRINCIPAL", "Question Bank");

        // ── Table + controls ──────────────────────────────
        questionTable = createQuestionTable();
        questionTable.setItems(displayedQuestions);

        questionStatusLabel = new Label();
        questionStatusLabel.getStyleClass().add("status-label");
        questionStatusLabel.setWrapText(true);

        Button loadButton = new Button("Load Questions");
        loadButton.getStyleClass().add("primary-button");
        loadButton.setOnAction(event -> requestAllQuestions());

        Button backButton = new Button("Back to Dashboard");
        backButton.getStyleClass().add("secondary-button");
        backButton.setOnAction(event -> showPrincipalDashboard());

        HBox paginationBar = createPaginationBar();

        HBox actionButtons = new HBox(12, loadButton, backButton);
        actionButtons.setAlignment(Pos.CENTER_LEFT);

        // ── Search + title ────────────────────────────────
        TextField searchBox = new TextField();
        searchBox.setPromptText("Search anything...");
        searchBox.getStyleClass().add("search-field");
        HBox topBar = new HBox(searchBox);
        topBar.getStyleClass().add("top-bar");
        topBar.setAlignment(Pos.CENTER_LEFT);

        Label pageTitle = new Label("Question Bank (Read-only)");
        pageTitle.getStyleClass().add("welcome-label");

        VBox centerArea = new VBox(12,
                topBar, pageTitle, questionTable,
                paginationBar, actionButtons, questionStatusLabel);
        VBox.setVgrow(questionTable, Priority.ALWAYS);
        HBox.setHgrow(centerArea, Priority.ALWAYS);

        HBox root = new HBox(16, sidebar, centerArea);
        root.getStyleClass().add("login-background");
        root.setPadding(new Insets(22));

        displayCurrentPage();

        Scene scene = new Scene(root, windowWidth(), windowHeight());
        applyStyleSheet(scene);
        primaryStage.setScene(scene);
        primaryStage.setTitle("HSTS - Question Bank");
        applyStandardWindowSize();
    }

    /**
     * Displays the Build Exam screen.
     */
    private void showBuildExamScreen() {
        showBuildExamScreen(null);
    }

    /**
     * Opens the exam builder, empty or loaded with an exam to edit.
     *
     * The window title keeps saying "Build Exam" in both modes on purpose:
     * several response handlers use it to tell that the builder is the screen
     * on show. The header inside the screen is what names the mode.
     *
     * @param examToEdit exam to load, or null to start a new one
     */
    private void showBuildExamScreen(Exam examToEdit) {

        buildExamView = new BuildExamView(
                currentUser,
                buildRoleSidebar("TEACHER", "Build Exam"),
                allQuestions,
                this::saveExam,
                this::showTeacherDashboard,
                examToEdit
        );

        Scene scene = new Scene(buildExamView, windowWidth(), windowHeight());

        applyStyleSheet(scene);

        primaryStage.setScene(scene);

        primaryStage.setTitle(
                examToEdit == null
                        ? "HSTS - Build Exam"
                        : "HSTS - Build Exam (Editing)"
        );

        applyStandardWindowSize();

        if (allQuestions.isEmpty()) {

            try {

                clientController.requestAllQuestions();

            } catch (IOException exception) {

                System.err.println(
                        "Could not load questions: "
                                + exception.getMessage()
                );
            }
        }

        try {
            clientController.requestAllCourses();
        } catch (IOException exception) {
            System.err.println("Could not load courses: " + exception.getMessage());
        }
    }

    /**
     * Displays the Exam Bank screen for the current teacher.
     */
    private void showExamBankScreen() {

        examBankView = new ExamBankView(
                currentUser,
                buildRoleSidebar("TEACHER", "Exam Bank"),
                this::loadTeacherExams,
                this::requestToggleExamStatus,
                this::requestDeleteExam,
                this::requestScheduleExam,
                this::beginExamEdit,
                this::showExamResultsScreen,
                this::extendExamDuration,
                this::showTeacherDashboard,
                this::performLogout
        );

        Scene scene = new Scene(examBankView, windowWidth(), windowHeight());
        applyStyleSheet(scene);

        primaryStage.setScene(scene);
        primaryStage.setTitle("HSTS - Exam Bank");
        applyStandardWindowSize();

        loadTeacherExams();
    }

    /**
     * Displays the teacher's Grade Approval screen — the gate every
     * computer-calculated grade passes through before a student sees it.
     */
    private void showGradeApprovalScreen() {

        gradeApprovalView = new GradeApprovalView(
                currentUser,
                buildRoleSidebar("TEACHER", "Grade Approval"),
                this::approveSelectedGrade,
                this::overrideSelectedGrade,
                this::loadPendingGrades,
                this::showTeacherDashboard
        );

        Scene scene = new Scene(gradeApprovalView, windowWidth(), windowHeight());
        applyStyleSheet(scene);

        primaryStage.setScene(scene);
        primaryStage.setTitle("HSTS - Grade Approval");
        applyStandardWindowSize();

        loadPendingGrades();
    }

    // ==========================================================
    //  CONTENT GENERATION  (teacher; Claude-backed drafting)
    // ==========================================================

    /**
     * Opens the teacher's Generate Content screen.
     *
     * It asks the server what the engine can do before anything else, so
     * degraded mode is rendered on arrival rather than discovered on a click.
     */
    private void showGenerateContentScreen() {

        generateContentView = new GenerateContentView(
                currentUser,
                buildRoleSidebar("TEACHER", "Generate"),
                this::generateQuestions,
                this::generateExam,
                this::saveGeneratedQuestions,
                this::acceptGeneratedExam,
                this::showTeacherDashboard
        );

        Scene scene = new Scene(generateContentView, windowWidth(), windowHeight());
        applyStyleSheet(scene);

        primaryStage.setScene(scene);
        primaryStage.setTitle("HSTS - Generate Content");
        applyStandardWindowSize();

        try {
            clientController.requestEngineStatus();
        } catch (IOException exception) {
            generateContentView.setEngineStatus(null);
        }
        loadMyCourses();
    }

    /**
     * Asks the server for draft questions.
     *
     * @param request course, topic, difficulty and count
     */
    private void generateQuestions(GenerationRequest request) {

        try {
            clientController.requestGenerateQuestions(request);
        } catch (IllegalArgumentException exception) {
            if (generateContentView != null) {
                generateContentView.generationFailed(exception.getMessage());
            }
        } catch (IOException exception) {
            if (generateContentView != null) {
                generateContentView.generationFailed(
                        "Could not reach the server: " + exception.getMessage());
            }
        }
    }

    /**
     * Asks the server for a draft exam.
     *
     * @param request course, topic, question count and duration
     */
    private void generateExam(GenerationRequest request) {

        try {
            clientController.requestGenerateExam(request);
        } catch (IllegalArgumentException exception) {
            if (generateContentView != null) {
                generateContentView.generationFailed(exception.getMessage());
            }
        } catch (IOException exception) {
            if (generateContentView != null) {
                generateContentView.generationFailed(
                        "Could not reach the server: " + exception.getMessage());
            }
        }
    }

    /**
     * Saves the drafts a teacher kept from the question-review list.
     *
     * @param questions the kept, possibly-edited drafts
     */
    private void saveGeneratedQuestions(List<Question> questions) {

        pendingGeneratedExam = null;   // this save belongs to the review list
        try {
            clientController.requestSaveGeneratedQuestions(questions);
        } catch (IllegalArgumentException exception) {
            if (generateContentView != null) {
                generateContentView.generationFailed(exception.getMessage());
            }
        } catch (IOException exception) {
            if (generateContentView != null) {
                generateContentView.generationFailed(
                        "Could not save the questions: " + exception.getMessage());
            }
        }
    }

    /**
     * Accepts a generated exam: persists its questions, then opens the normal
     * exam builder with them.
     *
     * The two steps are not an accident of implementation. A draft's questions
     * do not exist in the question bank, and the exam builder's save path
     * (CREATE_EXAM) links an exam to questions by ID. Saving them first is
     * what lets a generated exam go through the ordinary authoring path
     * instead of needing a second, parallel save path of its own.
     *
     * @param draft the generated exam
     */
    private void acceptGeneratedExam(Exam draft) {

        if (draft == null || draft.getExamQuestions() == null
                || draft.getExamQuestions().isEmpty()) {
            if (generateContentView != null) {
                generateContentView.generationFailed("There is no draft exam to accept.");
            }
            return;
        }

        List<Question> questions = new ArrayList<>();
        for (ExamQuestion eq : draft.getExamQuestions()) {
            if (eq.getQuestion() != null) questions.add(eq.getQuestion());
        }

        pendingGeneratedExam = draft;
        try {
            clientController.requestSaveGeneratedQuestions(questions);
        } catch (IllegalArgumentException exception) {
            pendingGeneratedExam = null;
            if (generateContentView != null) {
                generateContentView.generationFailed(exception.getMessage());
            }
        } catch (IOException exception) {
            pendingGeneratedExam = null;
            if (generateContentView != null) {
                generateContentView.generationFailed(
                        "Could not save the generated questions: " + exception.getMessage());
            }
        }
    }

    /**
     * Handles persisted questions coming back from SAVE_GENERATED_QUESTIONS.
     *
     * Two callers land here, told apart by {@link #pendingGeneratedExam}:
     * the question-review list (which just reports how many were stored) and
     * the accepted exam draft (which now has real IDs to build with).
     *
     * @param saved the persisted questions, in the order they were sent
     * @param message the server's message
     */
    private void handleGeneratedQuestionsSaved(List<Question> saved, String message) {

        // The bank the exam builder browses is cached client-side, so newly
        // saved questions have to join it or they would be invisible there.
        allQuestions.addAll(saved);

        Exam draft = pendingGeneratedExam;
        pendingGeneratedExam = null;

        if (draft == null) {
            if (generateContentView != null) {
                generateContentView.draftsSaved(saved.size());
            } else {
                showCurrentStatus(message);
            }
            return;
        }

        // Re-pair each persisted question with the points the server assigned
        // to it. Both lists are in generation order -- the server appends as
        // it inserts -- so index alignment is the pairing.
        List<ExamQuestion> rebuilt = new ArrayList<>();
        List<ExamQuestion> original = draft.getExamQuestions();
        for (int i = 0; i < saved.size() && i < original.size(); i++) {
            rebuilt.add(new ExamQuestion(saved.get(i), original.get(i).getPoints()));
        }

        if (rebuilt.size() != original.size()) {
            if (generateContentView != null) {
                generateContentView.generationFailed(
                        "Only " + rebuilt.size() + " of " + original.size()
                                + " generated questions were saved, so the exam was not opened. "
                                + "The saved questions are in your question bank.");
            }
            return;
        }

        draft.setExamQuestions(rebuilt);
        draft.setQuestionCount(rebuilt.size());
        showBuildExamScreen(draft);

        if (buildExamView != null) {
            buildExamView.setStatusMessage(
                    "Generated exam loaded, and its " + rebuilt.size()
                            + " question(s) were added to your question bank. Review it, then "
                            + "save -- it goes to the principal for approval like any other exam.");
        }
    }

    // ==========================================================
    //  LEARNING BOT  (spec 13 + 14)
    // ==========================================================

    /**
     * Opens the student's Learning Bot chat.
     *
     * The exam check here mirrors the greyed-out dashboard button: it stops a
     * student who backed out of an exam from wandering in through the
     * sidebar. It is not the enforcement -- ASK_BOT is refused server-side
     * from the open submission row, which survives a reconnect and a lying
     * client, and neither of those is true of this flag.
     */
    private void showBotChatScreen() {

        if (examInProgress) {
            showCurrentStatus("The learning bot is not available while you are taking an exam.");
            return;
        }

        botChatView = new BotChatView(
                currentUser,
                buildRoleSidebar("STUDENT", "AI Assistant"),
                this::askBot,
                bot -> loadMyBotHistory(bot.getId()),
                this::loadStudentBots,
                this::showStudentDashboard
        );

        Scene scene = new Scene(botChatView, windowWidth(), windowHeight());
        applyStyleSheet(scene);

        primaryStage.setScene(scene);
        primaryStage.setTitle("HSTS - Learning Bot");
        applyStandardWindowSize();

        loadStudentBots();
    }

    /** Requests the bots this student may use. */
    private void loadStudentBots() {

        try {
            clientController.requestStudentBots(currentUser.getUserId());
        } catch (IllegalArgumentException exception) {
            if (botChatView != null) botChatView.setStatusMessage(exception.getMessage());
        } catch (IOException exception) {
            if (botChatView != null) {
                botChatView.setStatusMessage(
                        "Could not load your learning bots: " + exception.getMessage());
            }
        }
    }

    /**
     * Sends one question to a course bot.
     *
     * @param ask the bot and the question the student typed
     */
    private void askBot(BotAsk ask) {

        try {
            clientController.requestAskBot(ask);
        } catch (IllegalArgumentException exception) {
            if (botChatView != null) botChatView.failPending(exception.getMessage());
        } catch (IOException exception) {
            if (botChatView != null) {
                botChatView.failPending("Could not reach the server: " + exception.getMessage());
            }
        }
    }

    /**
     * Requests the student's own history with one bot.
     *
     * @param botId identifier of the bot
     */
    private void loadMyBotHistory(int botId) {

        try {
            clientController.requestMyBotHistory(botId);
        } catch (IllegalArgumentException exception) {
            if (botChatView != null) botChatView.setStatusMessage(exception.getMessage());
        } catch (IOException exception) {
            if (botChatView != null) {
                botChatView.setStatusMessage(
                        "Could not load your history: " + exception.getMessage());
            }
        }
    }

    /** Opens the teacher's Learning Bot editor. */
    private void showBotEditorScreen() {

        botEditorView = new BotEditorView(
                currentUser,
                buildRoleSidebar("TEACHER", "Learning Bot"),
                course -> loadCourseBot(course.getId()),
                this::saveBot,
                this::saveBotSource,
                this::deleteBotSource,
                bot -> loadAnonymousBotHistory(bot.getId()),
                this::showTeacherDashboard
        );

        Scene scene = new Scene(botEditorView, windowWidth(), windowHeight());
        applyStyleSheet(scene);

        primaryStage.setScene(scene);
        primaryStage.setTitle("HSTS - Learning Bot Editor");
        applyStandardWindowSize();

        loadMyCourses();
    }

    /**
     * Requests the courses this teacher is attached to.
     *
     * Shared by the bot editor and the generation screen, so a failure is
     * reported on whichever one asked for it.
     */
    private void loadMyCourses() {

        try {
            clientController.requestMyCourses();
        } catch (IOException exception) {
            String problem = "Could not load your courses: " + exception.getMessage();
            if (isShowing("Generate Content") && generateContentView != null) {
                generateContentView.setStatusMessage(problem);
            } else if (botEditorView != null) {
                botEditorView.setStatusMessage(problem);
            } else {
                showCurrentStatus(problem);
            }
        }
    }

    /**
     * Requests one course's bot, sources included.
     *
     * @param courseId identifier of the course
     */
    private void loadCourseBot(int courseId) {

        try {
            clientController.requestCourseBot(courseId);
        } catch (IllegalArgumentException exception) {
            if (botEditorView != null) botEditorView.setStatusMessage(exception.getMessage());
        } catch (IOException exception) {
            if (botEditorView != null) {
                botEditorView.setStatusMessage(
                        "Could not load the bot: " + exception.getMessage());
            }
        }
    }

    /**
     * Saves a bot's name and availability.
     *
     * @param bot the bot to save
     */
    private void saveBot(CourseBot bot) {

        try {
            clientController.requestSaveBot(bot);
        } catch (IllegalArgumentException exception) {
            if (botEditorView != null) botEditorView.setStatusMessage(exception.getMessage());
        } catch (IOException exception) {
            if (botEditorView != null) {
                botEditorView.setStatusMessage(
                        "Could not save the bot: " + exception.getMessage());
            }
        }
    }

    /**
     * Saves one information source.
     *
     * @param source the source to save
     */
    private void saveBotSource(BotSource source) {

        try {
            clientController.requestSaveBotSource(source);
        } catch (IllegalArgumentException exception) {
            if (botEditorView != null) botEditorView.setStatusMessage(exception.getMessage());
        } catch (IOException exception) {
            if (botEditorView != null) {
                botEditorView.setStatusMessage(
                        "Could not save the source: " + exception.getMessage());
            }
        }
    }

    /**
     * Deletes one information source.
     *
     * @param sourceId identifier of the source
     */
    private void deleteBotSource(int sourceId) {

        try {
            clientController.requestDeleteBotSource(sourceId);
        } catch (IllegalArgumentException exception) {
            if (botEditorView != null) botEditorView.setStatusMessage(exception.getMessage());
        } catch (IOException exception) {
            if (botEditorView != null) {
                botEditorView.setStatusMessage(
                        "Could not delete the source: " + exception.getMessage());
            }
        }
    }

    /**
     * Requests one bot's anonymised question history (spec 14.3).
     *
     * @param botId identifier of the bot
     */
    private void loadAnonymousBotHistory(int botId) {

        try {
            clientController.requestAnonymousBotHistory(botId);
        } catch (IllegalArgumentException exception) {
            if (botEditorView != null) botEditorView.setStatusMessage(exception.getMessage());
        } catch (IOException exception) {
            if (botEditorView != null) {
                botEditorView.setStatusMessage(
                        "Could not load the history: " + exception.getMessage());
            }
        }
    }

    /**
     * Requests the submissions awaiting the current teacher's approval.
     */
    private void loadPendingGrades() {

        try {
            clientController.requestPendingGrades(currentUser.getUserId());
        } catch (IllegalArgumentException exception) {
            if (gradeApprovalView != null) {
                gradeApprovalView.setStatusMessage(exception.getMessage());
            }
        } catch (IOException exception) {
            if (gradeApprovalView != null) {
                gradeApprovalView.setStatusMessage(
                        "Could not load pending grades: " + exception.getMessage());
            }
        }
    }

    /**
     * Releases a computed grade to the student unchanged.
     *
     * @param grade row selected in the Grade Approval table
     */
    private void approveSelectedGrade(PendingGrade grade) {

        try {
            clientController.requestApproveGrade(grade.getSubmissionId());
            gradeApprovalView.setStatusMessage(
                    "Approving grade for submission " + grade.getSubmissionId() + "...");
        } catch (IllegalArgumentException exception) {
            gradeApprovalView.setStatusMessage(exception.getMessage());
        } catch (IOException exception) {
            gradeApprovalView.setStatusMessage(
                    "Could not approve grade: " + exception.getMessage());
        }
    }

    /**
     * Replaces a computed grade with the teacher's own, plus a justification.
     *
     * @param override submission ID, new score and justification
     */
    private void overrideSelectedGrade(GradeOverride override) {

        try {
            clientController.requestOverrideGrade(override);
            gradeApprovalView.setStatusMessage(
                    "Changing grade for submission " + override.getSubmissionId() + "...");
        } catch (IllegalArgumentException exception) {
            gradeApprovalView.setStatusMessage(exception.getMessage());
        } catch (IOException exception) {
            gradeApprovalView.setStatusMessage(
                    "Could not change grade: " + exception.getMessage());
        }
    }

    /**
     * Handles a grade released to the student (APPROVE_GRADE / OVERRIDE_GRADE).
     * The row leaves the queue as soon as it is no longer awaiting approval.
     *
     * @param grade updated row
     * @param message server message
     */
    private void handleGradeReleased(
            PendingGrade grade,
            String message
    ) {

        if (gradeApprovalView == null) {
            showCurrentStatus(message);
            return;
        }

        gradeApprovalView.removeGrade(grade.getSubmissionId());
        gradeApprovalView.setStatusMessage(message);
    }

    /**
     * Handles the teacher's queue of grades awaiting approval
     * (GET_PENDING_GRADES).
     *
     * @param dataList returned rows
     * @param message server message
     */
    private void handlePendingGradeList(
            List<?> dataList,
            String message
    ) {

        if (gradeApprovalView == null) {
            return;
        }

        List<PendingGrade> grades = new ArrayList<>();

        for (Object item : dataList) {
            if (item instanceof PendingGrade grade) {
                grades.add(grade);
            }
        }

        gradeApprovalView.setPendingGrades(grades);
        gradeApprovalView.setStatusMessage(message);
    }

    /**
     * Requests the checked exam form for one of the student's own results.
     *
     * @param result result row the student selected
     */
    private void openSubmissionReview(ExamResult result) {

        try {
            clientController.requestSubmissionReview(result.getResultId());
        } catch (IllegalArgumentException exception) {
            if (studentResultsView != null) {
                studentResultsView.setStatusMessage(exception.getMessage());
            }
        } catch (IOException exception) {
            if (studentResultsView != null) {
                studentResultsView.setStatusMessage(
                        "Could not open the checked exam: " + exception.getMessage());
            }
        }
    }

    /**
     * Displays a checked exam form returned by GET_SUBMISSION_REVIEW.
     *
     * @param review checked form built by the server
     */
    private void showSubmissionReviewScreen(SubmissionReview review) {

        SubmissionReviewView reviewView = new SubmissionReviewView(
                review,
                buildRoleSidebar("STUDENT", "Review Exams"),
                this::showStudentResultsScreen
        );

        Scene scene = new Scene(reviewView, windowWidth(), windowHeight());
        applyStyleSheet(scene);

        primaryStage.setScene(scene);
        primaryStage.setTitle("HSTS - Checked Exam");
        applyStandardWindowSize();
    }

    /**
     * Displays the Approve Exams screen for the principal.
     */
    private void showApproveExamScreen() {

        approveExamView = new ApproveExamView(
                currentUser,
                buildRoleSidebar("PRINCIPAL", "Approve Exams"),
                this::approveSelectedExam,
                this::rejectSelectedExam,
                this::showPrincipalDashboard
        );

        Scene scene = new Scene(approveExamView, windowWidth(), windowHeight());
        applyStyleSheet(scene);
        primaryStage.setScene(scene);
        primaryStage.setTitle("HSTS - Approve Exams");
        applyStandardWindowSize();

        try {
            clientController.requestPendingExams();
        } catch (IOException e) {
            approveExamView.setStatusMessage("Could not load pending exams: " + e.getMessage());
        }
    }

    private void approveSelectedExam(Exam exam) {
        try {
            clientController.requestApproveExam(exam.getExamId());
            approveExamView.setStatusMessage("Approving exam " + exam.getExamId() + "...");
        } catch (IOException e) {
            approveExamView.setStatusMessage("Could not approve exam: " + e.getMessage());
        }
    }

    private void rejectSelectedExam(Exam exam) {
        try {
            clientController.requestRejectExam(exam);
            approveExamView.setStatusMessage("Rejecting exam " + exam.getExamId() + "...");
        } catch (IllegalArgumentException e) {
            approveExamView.setStatusMessage(e.getMessage());
        } catch (IOException e) {
            approveExamView.setStatusMessage("Could not reject exam: " + e.getMessage());
        }
    }

    /**
     * Requests all exams created by the current teacher.
     */
    private void loadTeacherExams() {

        try {

            clientController.requestTeacherExams(
                    currentUser.getUserId()
            );

        } catch (IllegalArgumentException exception) {

            if (examBankView != null) {
                examBankView.setStatusMessage(
                        exception.getMessage()
                );
            }

        } catch (IOException exception) {

            if (examBankView != null) {
                examBankView.setStatusMessage(
                        "Could not load exams: "
                                + exception.getMessage()
                );
            }
        }
    }

    /**
     * Sends a toggle-active request for the given exam.
     *
     * @param exam exam to toggle
     */
    private void requestToggleExamStatus(
            Exam exam
    ) {

        try {

            clientController.requestToggleExamStatus(
                    exam.getExamId()
            );

            if (examBankView != null) {
                examBankView.setStatusMessage(
                        "Toggling status of exam "
                                + exam.getExamId()
                                + "..."
                );
            }

        } catch (IOException exception) {

            if (examBankView != null) {
                examBankView.setStatusMessage(
                        "Could not toggle status: "
                                + exception.getMessage()
                );
            }
        }
    }

    /**
     * Sends a delete request for the given exam.
     *
     * @param exam exam to delete
     */
    private void requestDeleteExam(
            Exam exam
    ) {

        try {

            clientController.requestDeleteExam(
                    exam.getExamId()
            );

            if (examBankView != null) {
                examBankView.setStatusMessage(
                        "Deleting exam "
                                + exam.getExamId()
                                + "..."
                );
            }

        } catch (IOException exception) {

            if (examBankView != null) {
                examBankView.setStatusMessage(
                        "Could not delete exam: "
                                + exception.getMessage()
                );
            }
        }
    }

    /**
     * Sends a scheduling request for the given exam. The exam already carries
     * the window and code chosen in the Exam Bank's Schedule dialog.
     *
     * @param exam exam with openAt, closeAt and examCode populated
     */
    private void requestScheduleExam(
            Exam exam
    ) {

        try {

            clientController.requestScheduleExam(exam);

            if (examBankView != null) {
                examBankView.setStatusMessage(
                        "Scheduling exam "
                                + exam.getExamId()
                                + "..."
                );
            }

        } catch (IllegalArgumentException exception) {

            if (examBankView != null) {
                examBankView.setStatusMessage(exception.getMessage());
            }

        } catch (IOException exception) {

            if (examBankView != null) {
                examBankView.setStatusMessage(
                        "Could not schedule exam: "
                                + exception.getMessage()
                );
            }
        }
    }

    /**
     * Removes a deleted exam from the Exam Bank view.
     *
     * @param examId identifier of the deleted exam
     * @param message server message
     */
    private void handleDeletedExam(
            int examId,
            String message
    ) {

        if (examBankView != null) {
            examBankView.removeExam(examId);
            examBankView.setStatusMessage(message);
        }
    }

    /**
     * Sends a new exam to the server.
     *
     * @param exam exam created by the teacher
     */
    private void saveExam(
            Exam exam
    ) {

        try {

            // Identifier 0 means the builder was in create mode; anything else
            // names the exam this one replaces.
            if (exam.getExamId() > 0) {

                clientController.requestUpdateExam(
                        exam
                );

            } else {

                clientController.requestCreateExam(
                        exam
                );
            }

        } catch (IllegalArgumentException exception) {

            reportOnBuilder(
                    exception.getMessage()
            );

        } catch (IOException exception) {

            reportOnBuilder(
                    "Could not save the exam: "
                            + exception.getMessage()
            );
        }
    }

    /**
     * Reports a message on the exam builder when it is the screen showing,
     * and on the generic status label otherwise.
     *
     * @param message message to show
     */
    private void reportOnBuilder(
            String message
    ) {

        if (buildExamView != null) {
            buildExamView.setStatusMessage(message);
        } else {
            showCurrentStatus(message);
        }
    }

    /**
     * Asks the server to add time to an exam that is already running
     * (spec 7).
     *
     * Nothing is refreshed here on the strength of having asked: the students
     * sitting the exam are told by a server push, and this teacher's own row
     * updates when the reply lands.
     *
     * @param extension the exam and the minutes to add
     */
    private void extendExamDuration(
            ExamExtension extension
    ) {

        if (extension == null) {
            return;
        }

        try {

            clientController.requestExtendExamDuration(
                    extension
            );

            if (examBankView != null) {

                examBankView.setStatusMessage(
                        "Adding "
                                + extension.getExtraMinutes()
                                + " minute(s) to exam "
                                + extension.getExamId()
                                + "..."
                );
            }

        } catch (IllegalArgumentException | IOException exception) {

            if (examBankView != null) {

                examBankView.setStatusMessage(
                        "Could not extend the exam: "
                                + exception.getMessage()
                );
            }
        }
    }

    /**
     * Starts editing an exam from the Exam Bank (spec 3.5).
     *
     * The bank holds headers only, so the full exam — its questions and their
     * point values — is fetched first; the GET_EXAM_BY_ID response opens the
     * builder. The server independently refuses the eventual save if the exam
     * has submissions, so this does not try to guess that here.
     *
     * @param exam the bank row the teacher selected
     */
    private void beginExamEdit(
            Exam exam
    ) {

        if (exam == null) {
            return;
        }

        examEditRequested = true;

        try {

            clientController.requestExamById(
                    exam.getExamId()
            );

            if (examBankView != null) {

                examBankView.setStatusMessage(
                        "Opening exam "
                                + exam.getExamId()
                                + " for editing..."
                );
            }

        } catch (IllegalArgumentException | IOException exception) {

            examEditRequested = false;

            if (examBankView != null) {

                examBankView.setStatusMessage(
                        "Could not open the exam: "
                                + exception.getMessage()
                );
            }
        }
    }

    /**
     * Displays the Student Dashboard.
     */
    private void showStudentDashboard() {

        studentDashboardView =
                new StudentDashboardView(
                        currentUser,
                        buildRoleSidebar("STUDENT", "Dashboard"),
                        availableExams,
                        this::showStudentActiveExamsScreen,
                        this::startSelectedExam,
                        this::showStudentResultsScreen,
                        () -> showSettingsScreen("STUDENT"),
                        this::showBotChatScreen,
                        examInProgress ? "unavailable during an exam" : null
                );

        Scene scene =
                new Scene(
                        studentDashboardView,
                        windowWidth(),
                        windowHeight()
                );

        applyStyleSheet(scene);

        primaryStage.setScene(scene);

        primaryStage.setTitle(
                "HSTS - Student Dashboard"
        );

        applyStandardWindowSize();

        // Populate stat cards + performance chart from cached results (refreshed below)
        if (!studentResults.isEmpty()) {
            studentDashboardView.setStudentStats(studentResults);
            studentDashboardView.setPerformanceChart(studentResults);
        }

        loadAvailableExams();
        loadStudentResults();
    }

    /**
     * Requests the student's available exams.
     */
    private void loadAvailableExams() {

        try {

            clientController.requestAvailableExams(
                    currentUser.getUserId()
            );

        } catch (IllegalArgumentException exception) {

            if (studentDashboardView != null) {

                studentDashboardView.setStatusMessage(
                        exception.getMessage()
                );
            }

        } catch (IOException exception) {

            if (studentDashboardView != null) {

                studentDashboardView.setStatusMessage(
                        "Could not load exams: "
                                + exception.getMessage()
                );
            }
        }
    }

    /**
     * Starts an exam the student picked from a listing. The listing is
     * informational only — the student still has to pass the entry gate
     * (4-digit code + national ID), so this just opens the gate rather than
     * fetching the exam directly (spec 6.1, 6.2).
     *
     * @param selectedExam exam the student clicked, used only to pre-fill context
     */
    private void startSelectedExam(
            Exam selectedExam
    ) {

        showExamEntryGate(selectedExam);
    }

    /**
     * Displays the exam entry gate: the student types the 4-digit code the
     * teacher issued plus their national ID (ת"ז). The server re-checks both
     * along with the exam's window and the student's enrolment before it
     * returns any exam content.
     *
     * @param context exam the student came from, or null when entered directly
     */
    private void showExamEntryGate(Exam context) {

        VBox sidebar = buildRoleSidebar("STUDENT", "Active Exams");

        Label pageTitle = new Label("Start an Exam");
        pageTitle.getStyleClass().add("welcome-label");

        Label subtitle = new Label(
                "Enter the 4-digit code your teacher gave you, and your ID number.");
        subtitle.setStyle("-fx-font-size: 13px; -fx-text-fill: -hsts-muted;");
        subtitle.setWrapText(true);

        if (context != null && context.getTitle() != null) {
            Label which = new Label("Selected: " + context.getTitle());
            which.setStyle("-fx-font-size: 13px; -fx-text-fill: -hsts-muted;");
            subtitle = new Label(subtitle.getText() + "\n" + which.getText());
            subtitle.setWrapText(true);
            subtitle.setStyle("-fx-font-size: 13px; -fx-text-fill: -hsts-muted;");
        }

        TextField codeField = new TextField();
        codeField.setPromptText("4-digit exam code");
        codeField.setMaxWidth(260);

        TextField nationalIdField = new TextField();
        nationalIdField.setPromptText("ID number (ת\"ז)");
        nationalIdField.setMaxWidth(260);

        examGateStatusLabel = new Label();
        examGateStatusLabel.setWrapText(true);
        examGateStatusLabel.getStyleClass().add("status-label");

        Button startBtn = new Button("Start Exam");
        startBtn.getStyleClass().add("primary-button");
        startBtn.setDefaultButton(true);
        startBtn.setOnAction(e -> submitExamEntry(
                codeField.getText(), nationalIdField.getText()));

        Button backBtn = new Button("Back");
        backBtn.getStyleClass().add("secondary-button");
        backBtn.setOnAction(e -> showStudentDashboard());

        HBox buttonRow = new HBox(12, startBtn, backBtn);
        buttonRow.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(12,
                new Label("Exam code") {{ getStyleClass().add("form-label"); }},
                codeField,
                new Label("ID number") {{ getStyleClass().add("form-label"); }},
                nationalIdField,
                buttonRow,
                examGateStatusLabel);
        card.getStyleClass().add("section-card");
        card.setMaxWidth(420);

        VBox mainContent = new VBox(16, pageTitle, subtitle, card);
        mainContent.setPadding(new Insets(0, 0, 0, 20));
        HBox.setHgrow(mainContent, Priority.ALWAYS);

        HBox root = new HBox(16, sidebar, mainContent);
        root.getStyleClass().add("login-background");
        root.setPadding(new Insets(22));

        Scene scene = new Scene(root, windowWidth(), windowHeight());
        applyStyleSheet(scene);
        primaryStage.setScene(scene);
        primaryStage.setTitle("HSTS - Start Exam");
        applyStandardWindowSize();
    }

    /**
     * Sends the entry-gate credentials to the server.
     *
     * @param code 4-digit exam code the student typed
     * @param nationalId national ID the student typed
     */
    private void submitExamEntry(String code, String nationalId) {

        ExamEntryRequest entry = new ExamEntryRequest(
                code == null ? null : code.trim(),
                nationalId == null ? null : nationalId.trim(),
                currentUser.getUserId()
        );

        try {

            clientController.requestStartExamByCode(entry);

            if (examGateStatusLabel != null) {
                examGateStatusLabel.setText("Checking your details...");
            }

        } catch (IllegalArgumentException exception) {

            if (examGateStatusLabel != null) {
                examGateStatusLabel.setText(exception.getMessage());
            }

        } catch (IOException exception) {

            if (examGateStatusLabel != null) {
                examGateStatusLabel.setText(
                        "Could not start the exam: " + exception.getMessage());
            }
        }
    }

    /**
     * Displays the exam-taking screen.
     *
     * @param exam exam to complete
     */
    private void showTakeExamScreen(
            Exam exam
    ) {

        takeExamView =
                new TakeExamView(
                        currentUser,
                        exam,
                        this::submitExam,
                        this::showStudentDashboard
                );

        Scene scene =
                new Scene(
                        takeExamView,
                        windowWidth(),
                        windowHeight()
                );

        applyStyleSheet(scene);

        primaryStage.setScene(scene);

        primaryStage.setTitle(
                "HSTS - Take Exam"
        );

        applyStandardWindowSize();

        // Grey out the bot entry point for the rest of this sitting. Courtesy
        // only -- see the field comment on examInProgress.
        examInProgress = true;
    }

    /**
     * Sends the student's completed exam.
     *
     * @param submission completed exam submission
     */
    private void submitExam(
            ExamSubmission submission
    ) {

        try {

            clientController.requestSubmitExam(
                    submission
            );

        } catch (IllegalArgumentException exception) {

            if (takeExamView != null) {

                takeExamView.setStatusMessage(
                        exception.getMessage()
                );
            }

        } catch (IOException exception) {

            if (takeExamView != null) {

                takeExamView.setStatusMessage(
                        "Could not submit the exam: "
                                + exception.getMessage()
                );
            }
        }
    }

    /**
     * Displays the student's results screen.
     */
    private void showStudentResultsScreen() {

        studentResultsView =
                new StudentResultsView(
                        currentUser,
                        buildRoleSidebar("STUDENT", "Grades"),
                        studentResults,
                        this::loadStudentResults,
                        this::openSubmissionReview,
                        this::showStudentDashboard
                );

        Scene scene =
                new Scene(
                        studentResultsView,
                        windowWidth(),
                        windowHeight()
                );

        applyStyleSheet(scene);

        primaryStage.setScene(scene);

        primaryStage.setTitle(
                "HSTS - Student Results"
        );

        applyStandardWindowSize();

        loadStudentResults();
    }

    /**
     * Requests all results of the current student.
     */
    private void loadStudentResults() {

        try {

            clientController.requestStudentResults(
                    currentUser.getUserId()
            );

        } catch (IllegalArgumentException exception) {

            if (studentResultsView != null) {

                studentResultsView.setStatusMessage(
                        exception.getMessage()
                );
            }

        } catch (IOException exception) {

            if (studentResultsView != null) {

                studentResultsView.setStatusMessage(
                        "Could not load results: "
                                + exception.getMessage()
                );
            }
        }
    }

    /**
     * Handles a list of results.
     *
     * @param dataList returned data
     * @param message server message
     */
    private void handleResultList(
            List<?> dataList,
            String message
    ) {

        List<ExamResult> receivedResults =
                new ArrayList<>();

        for (Object item : dataList) {

            if (item instanceof ExamResult result) {
                receivedResults.add(result);
            }
        }

        studentResults.setAll(
                receivedResults
        );

        if (studentResultsView != null) {

            studentResultsView.setResults(
                    studentResults
            );

            studentResultsView.setStatusMessage(
                    message
            );
        }

        if (studentDashboardView != null) {
            studentDashboardView.setStudentStats(studentResults);
            studentDashboardView.setPerformanceChart(studentResults);
        }
    }

    private void handleCourseList(List<?> dataList) {

        List<Course> courses = new ArrayList<>();
        for (Object item : dataList) {
            if (item instanceof Course c) courses.add(c);
        }

        allCourses.setAll(courses);

        if (buildExamView != null) {
            buildExamView.setCourses(courses);
        }

        refreshComparisonSubjects();
    }

    /**
     * Renders the administrator's exam pass-rate-by-course chart.
     *
     * @param dataList list of PassFailStat rows returned by the server
     */
    private void handlePassFailReport(List<?> dataList) {

        if (passFailChartContainer == null) {
            return;
        }

        List<PassFailStat> stats = new ArrayList<>();
        for (Object item : dataList) {
            if (item instanceof PassFailStat s) stats.add(s);
        }

        String[] labels = new String[stats.size()];
        double[] rates  = new double[stats.size()];
        for (int i = 0; i < stats.size(); i++) {
            PassFailStat s = stats.get(i);
            labels[i] = s.getLabel() + " (" + s.getTotalCount() + ")";
            rates[i]  = s.getPassRate();
        }

        clearReportContainer(passFailChartContainer);
        passFailChartContainer.getChildren().add(
                buildBarChart(labels, rates, "#34D399", 460, 150, true)
        );

        if (reportsStatusLabel != null) {
            reportsStatusLabel.setText("Reports loaded.");
        }
    }

    /**
     * Renders the administrator's average-score-over-time chart.
     *
     * @param dataList list of ScoreTrendPoint rows returned by the server
     */
    private void handleScoreTrendReport(List<?> dataList) {

        if (scoreTrendChartContainer == null) {
            return;
        }

        List<ScoreTrendPoint> points = new ArrayList<>();
        for (Object item : dataList) {
            if (item instanceof ScoreTrendPoint p) points.add(p);
        }

        clearReportContainer(scoreTrendChartContainer);

        if (points.size() < 2) {

            // A single point (or none) can't be drawn as a connected line.
            VBox single = new VBox(4);
            for (ScoreTrendPoint p : points) {
                Label l = new Label(p.getPeriodLabel() + ":  avg " + String.format("%.1f", p.getAverageScore())
                        + "  (" + p.getSubmissionCount() + " submission" + (p.getSubmissionCount() == 1 ? "" : "s") + ")");
                l.setStyle("-fx-font-size: 12px; -fx-text-fill: -hsts-muted;");
                single.getChildren().add(l);
            }
            if (points.isEmpty()) {
                Label empty = new Label("Not enough graded exams yet to show a trend.");
                empty.setStyle("-fx-font-size: 12px; -fx-text-fill: #9CA3AF;");
                single.getChildren().add(empty);
            }
            scoreTrendChartContainer.getChildren().add(single);

        } else {

            double[] values = new double[points.size()];
            for (int i = 0; i < points.size(); i++) {
                values[i] = points.get(i).getAverageScore();
            }
            scoreTrendChartContainer.getChildren().add(
                    buildLineChart(values, "#93C5FD", 460, 150)
            );

            VBox legend = new VBox(2);
            for (ScoreTrendPoint p : points) {
                Label l = new Label(p.getPeriodLabel() + ":  avg " + String.format("%.1f", p.getAverageScore())
                        + "  (" + p.getSubmissionCount() + " submission" + (p.getSubmissionCount() == 1 ? "" : "s") + ")");
                l.setStyle("-fx-font-size: 11px; -fx-text-fill: -hsts-muted;");
                legend.getChildren().add(l);
            }
            scoreTrendChartContainer.getChildren().add(legend);
        }

        if (reportsStatusLabel != null) {
            reportsStatusLabel.setText("Reports loaded.");
        }
    }

    /**
     * Renders the administrator's teacher-activity chart.
     *
     * @param dataList list of TeacherActivityStat rows returned by the server
     */
    private void handleTeacherActivityReport(List<?> dataList) {

        if (teacherActivityChartContainer == null) {
            return;
        }

        List<TeacherActivityStat> stats = new ArrayList<>();
        for (Object item : dataList) {
            if (item instanceof TeacherActivityStat s) stats.add(s);
        }

        String[] labels = new String[stats.size()];
        double[] testCounts = new double[stats.size()];
        for (int i = 0; i < stats.size(); i++) {
            TeacherActivityStat s = stats.get(i);
            labels[i] = s.getTeacherName();
            testCounts[i] = s.getTestsCount();
        }

        clearReportContainer(teacherActivityChartContainer);
        teacherActivityChartContainer.getChildren().add(
                buildBarChart(labels, testCounts, "#F9A8D4", 940, 150, false)
        );

        VBox legend = new VBox(2);
        for (TeacherActivityStat s : stats) {
            Label l = new Label(s.getTeacherName() + ":  " + s.getTestsCount() + " test"
                    + (s.getTestsCount() == 1 ? "" : "s") + ",  " + s.getQuestionsCount()
                    + " question" + (s.getQuestionsCount() == 1 ? "" : "s"));
            l.setStyle("-fx-font-size: 11px; -fx-text-fill: -hsts-muted;");
            legend.getChildren().add(l);
        }
        teacherActivityChartContainer.getChildren().add(legend);

        if (reportsStatusLabel != null) {
            reportsStatusLabel.setText("Reports loaded.");
        }
    }

    /**
     * Renders the teacher dashboard's performance chart: the average score
     * students achieved on each of the teacher's 10 most recently created
     * (and graded) exams.
     *
     * @param dataList list of TeacherExamPerformance rows returned by the server
     */
    private void handleTeacherExamPerformance(List<?> dataList) {

        List<TeacherExamPerformance> points = new ArrayList<>();
        for (Object item : dataList) {
            if (item instanceof TeacherExamPerformance p) points.add(p);
        }

        // Keyed by title because the rows carry no exam id. Two exams sharing a
        // title collapse onto the newer one's figures; the Reports drill-down
        // navigates by id and is unaffected.
        teacherPerformanceByExam.clear();
        for (TeacherExamPerformance point : points) {
            if (point.getExamLabel() != null) {
                teacherPerformanceByExam.put(point.getExamLabel(), point);
            }
        }

        String title = primaryStage.getTitle();

        if (teacherChartContainer != null && title.contains("Teacher Dashboard")) {
            renderTeacherPerformanceChart(points);
        }

        if (title.contains("My Reports")) {
            renderTeacherReportCharts(points);
            teacherReportsTable.refresh();
        }
    }

    /**
     * Draws the Reports screen's two charts — average score and submission
     * volume, one bar per exam — and the overall average stat card.
     *
     * The average card is weighted by submission count rather than a mean of
     * the per-exam means: an exam sat by thirty students should not carry the
     * same weight as one sat by two.
     *
     * @param points per-exam performance rows, oldest exam first
     */
    private void renderTeacherReportCharts(List<TeacherExamPerformance> points) {

        if (teacherReportsAverageChart == null || teacherReportsVolumeChart == null) {
            return;
        }

        clearReportContainer(teacherReportsAverageChart);
        clearReportContainer(teacherReportsVolumeChart);

        if (points.isEmpty()) {
            showEmptyReportState(teacherReportsAverageChart,
                    "No graded submissions yet.");
            showEmptyReportState(teacherReportsVolumeChart,
                    "No graded submissions yet.");
            teacherReportsAverageLabel.setText("\u2014");
            return;
        }

        String[] labels = new String[points.size()];
        double[] averages = new double[points.size()];
        double[] volumes = new double[points.size()];

        double weightedTotal = 0;
        int submissionTotal = 0;

        for (int i = 0; i < points.size(); i++) {

            TeacherExamPerformance point = points.get(i);

            labels[i] = point.getExamLabel();
            averages[i] = point.getAverageScore();
            volumes[i] = point.getSubmissionCount();

            weightedTotal += point.getAverageScore() * point.getSubmissionCount();
            submissionTotal += point.getSubmissionCount();
        }

        teacherReportsAverageChart.getChildren().add(
                buildBarChart(labels, averages, "#A78BFA", 520, 170, true));

        teacherReportsVolumeChart.getChildren().add(
                buildBarChart(labels, volumes, "#93C5FD", 520, 170, false));

        teacherReportsAverageLabel.setText(
                submissionTotal == 0
                        ? "\u2014"
                        : String.format("%.1f", weightedTotal / submissionTotal));
    }

    /**
     * Draws (or redraws) the teacher dashboard performance chart from a
     * list of per-exam average scores, replacing whatever the chart card
     * currently shows.
     *
     * @param points per-exam average scores, oldest exam first
     */
    private void renderTeacherPerformanceChart(List<TeacherExamPerformance> points) {

        clearReportContainer(teacherChartContainer);

        if (points.isEmpty()) {
            Label empty = new Label("No graded exams yet.");
            empty.setStyle("-fx-font-size: 12px; -fx-text-fill: -hsts-muted;");
            teacherChartContainer.getChildren().add(empty);
            return;
        }

        double[] values = new double[points.size()];
        String[] labels = new String[points.size()];
        for (int i = 0; i < points.size(); i++) {
            values[i] = points.get(i).getAverageScore();
            labels[i] = formatShortDate(points.get(i).getExamDate());
        }

        teacherChartContainer.getChildren().add(
                buildLineChart(values, labels, "#93C5FD", 580, 160)
        );
    }

    /**
     * Shows a small "no data" placeholder inside a report card.
     *
     * @param container report card content box
     * @param message placeholder text
     */
    private void showEmptyReportState(VBox container, String message) {

        if (container == null) {
            return;
        }

        clearReportContainer(container);
        Label empty = new Label(message);
        empty.setId("empty-state");
        empty.setStyle("-fx-font-size: 13px; -fx-text-fill: #9CA3AF;");
        container.getChildren().add(empty);
    }

    /**
     * Removes any previously rendered chart/placeholder/legend from a
     * report card, keeping only its title label.
     *
     * @param container report card content box
     */
    private void clearReportContainer(VBox container) {

        if (container.getChildren().size() > 1) {
            container.getChildren().remove(1, container.getChildren().size());
        }
    }

    // ==========================================================
    //  PRINCIPAL REPORTS - distribution + comparison (spec 12)
    // ==========================================================

    /**
     * One entry in a Reports selector: the label the principal reads, plus
     * the identifier the server needs. {@code id} 0 is reserved by the
     * distribution selector for its "All exams" entry.
     */
    private static final class ReportOption {

        private final int id;
        private final String label;

        private ReportOption(int id, String label) {
            this.id = id;
            this.label = label;
        }

        private int getId() {
            return id;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * Builds the grade distribution card's header: title plus the exam
     * selector. Kept as child 0 of the card so redrawing the histogram
     * leaves the selector alone.
     *
     * @return the header row
     */
    private HBox buildDistributionHeader() {

        Label title = new Label("Grade Distribution");
        title.getStyleClass().add("section-title");

        distributionExamCombo = new ComboBox<>();
        distributionExamCombo.setPrefWidth(210);
        distributionExamCombo.getItems().add(new ReportOption(0, "All exams"));
        distributionExamCombo.getSelectionModel().selectFirst();
        distributionExamCombo.setOnAction(event -> loadGradeDistribution());

        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);

        HBox header = new HBox(10, title, grow, distributionExamCombo);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    /**
     * Builds the comparison card's header: title, what to compare by, and
     * which teacher/course/student to compare. One pair of selectors drives
     * all three GET_*_STATISTICS actions.
     *
     * @return the header row
     */
    private HBox buildComparisonHeader() {

        Label title = new Label("Exam Comparison");
        title.getStyleClass().add("section-title");

        comparisonTypeCombo = new ComboBox<>();
        comparisonTypeCombo.getItems().addAll("Teacher", "Course", "Student");
        comparisonTypeCombo.getSelectionModel().selectFirst();
        comparisonTypeCombo.setPrefWidth(105);
        comparisonTypeCombo.setOnAction(event -> {
            // The IDs mean something different per type, so a previous
            // choice must not carry over to the new list.
            comparisonSubjectCombo.getSelectionModel().clearSelection();
            refreshComparisonSubjects();
        });

        comparisonSubjectCombo = new ComboBox<>();
        comparisonSubjectCombo.setPrefWidth(190);
        comparisonSubjectCombo.setPromptText("Choose a teacher");
        comparisonSubjectCombo.setOnAction(event -> runComparisonReport());

        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);

        HBox header = new HBox(10, title, grow, comparisonTypeCombo, comparisonSubjectCombo);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    /**
     * Asks the server for the histogram of whichever exam is selected.
     * The "All exams" entry is sent as a null payload, which the server
     * reads as "every graded submission".
     */
    private void loadGradeDistribution() {

        if (distributionExamCombo == null) {
            return;
        }

        ReportOption selected = distributionExamCombo.getSelectionModel().getSelectedItem();
        Integer examId = (selected == null || selected.getId() <= 0) ? null : selected.getId();

        try {
            clientController.requestGradeDistribution(examId);
        } catch (IOException | IllegalArgumentException exception) {
            if (reportsStatusLabel != null) {
                reportsStatusLabel.setText("Could not load distribution: " + exception.getMessage());
            }
        }
    }

    /**
     * Rebuilds the distribution selector from the cached system-wide exam
     * list, keeping whatever the principal had chosen if it is still there.
     */
    private void refreshDistributionExams() {

        if (distributionExamCombo == null) {
            return;
        }

        ReportOption previous = distributionExamCombo.getSelectionModel().getSelectedItem();
        int previousId = previous != null ? previous.getId() : 0;

        List<ReportOption> options = new ArrayList<>();
        options.add(new ReportOption(0, "All exams"));
        for (Exam exam : allExams) {
            options.add(new ReportOption(
                    exam.getExamId(),
                    displayName(exam.getTitle(), "Exam", exam.getExamId())));
        }

        // Detached while the items are swapped: repopulating changes the
        // selection, and the handler would fire a request per change.
        distributionExamCombo.setOnAction(null);
        distributionExamCombo.getItems().setAll(options);
        selectOptionById(distributionExamCombo, previousId);
        distributionExamCombo.setOnAction(event -> loadGradeDistribution());
    }

    /**
     * Rebuilds the second comparison selector for the chosen comparison
     * type. Neither teachers nor students have a listing endpoint of their
     * own, so they are distilled from the system-wide exams and results;
     * courses come from GET_ALL_COURSES.
     */
    private void refreshComparisonSubjects() {

        if (comparisonSubjectCombo == null || comparisonTypeCombo == null) {
            return;
        }

        String type = comparisonTypeCombo.getSelectionModel().getSelectedItem();
        Map<Integer, String> subjects = new LinkedHashMap<>();

        if ("Course".equals(type)) {
            for (Course course : allCourses) {
                subjects.putIfAbsent(course.getId(),
                        displayName(course.getCourseName(), "Course", course.getId()));
            }
        } else if ("Student".equals(type)) {
            for (ExamResult result : allResults) {
                subjects.putIfAbsent(result.getStudentId(),
                        displayName(result.getStudentName(), "Student", result.getStudentId()));
            }
        } else {
            for (Exam exam : allExams) {
                if (exam.getTeacherId() > 0) {
                    subjects.putIfAbsent(exam.getTeacherId(),
                            displayName(exam.getTeacherName(), "Teacher", exam.getTeacherId()));
                }
            }
        }

        List<ReportOption> options = new ArrayList<>();
        for (Map.Entry<Integer, String> entry : subjects.entrySet()) {
            options.add(new ReportOption(entry.getKey(), entry.getValue()));
        }

        ReportOption previous = comparisonSubjectCombo.getSelectionModel().getSelectedItem();

        comparisonSubjectCombo.setOnAction(null);
        comparisonSubjectCombo.getItems().setAll(options);
        if (previous != null) {
            selectOptionById(comparisonSubjectCombo, previous.getId());
        }
        comparisonSubjectCombo.setOnAction(event -> runComparisonReport());

        comparisonSubjectCombo.setPromptText(options.isEmpty()
                ? "Nothing graded yet"
                : "Choose a " + (type == null ? "teacher" : type.toLowerCase()));
    }

    /**
     * Selects the option carrying the given ID, if the selector still has
     * one. Anything else is left unselected rather than guessed at.
     *
     * @param combo selector to move
     * @param id identifier to look for
     */
    private void selectOptionById(ComboBox<ReportOption> combo, int id) {

        for (ReportOption option : combo.getItems()) {
            if (option.getId() == id) {
                combo.getSelectionModel().select(option);
                return;
            }
        }
    }

    /**
     * Asks the server for the comparison the two selectors describe. Does
     * nothing while no subject is chosen — the selector is momentarily
     * empty each time its list is rebuilt.
     */
    private void runComparisonReport() {

        if (comparisonTypeCombo == null || comparisonSubjectCombo == null) {
            return;
        }

        ReportOption subject = comparisonSubjectCombo.getSelectionModel().getSelectedItem();
        if (subject == null) {
            return;
        }

        String type = comparisonTypeCombo.getSelectionModel().getSelectedItem();

        try {
            if ("Course".equals(type)) {
                clientController.requestCourseStatistics(subject.getId());
            } else if ("Student".equals(type)) {
                clientController.requestStudentStatistics(subject.getId());
            } else {
                clientController.requestTeacherStatistics(subject.getId());
            }
        } catch (IOException | IllegalArgumentException exception) {
            if (reportsStatusLabel != null) {
                reportsStatusLabel.setText("Could not load comparison: " + exception.getMessage());
            }
        }
    }

    /**
     * Renders the grade distribution histogram: one bar per 10-point band.
     *
     * @param dataList list of GradeDistributionBucket rows returned by the server
     */
    private void handleGradeDistribution(List<?> dataList) {

        if (distributionChartContainer == null) {
            return;
        }

        List<GradeDistributionBucket> buckets = new ArrayList<>();
        for (Object item : dataList) {
            if (item instanceof GradeDistributionBucket bucket) buckets.add(bucket);
        }

        int total = 0;
        for (GradeDistributionBucket bucket : buckets) {
            total += bucket.getCount();
        }

        clearReportContainer(distributionChartContainer);

        if (total == 0) {

            // Every band is present but empty when nothing has been graded;
            // a chart of ten zero-height bars says less than a sentence.
            Label empty = new Label("No graded submissions to chart yet.");
            empty.setStyle("-fx-font-size: 12px; -fx-text-fill: #9CA3AF;");
            distributionChartContainer.getChildren().add(empty);

        } else {

            String[] labels = new String[buckets.size()];
            double[] counts = new double[buckets.size()];
            for (int i = 0; i < buckets.size(); i++) {
                labels[i] = buckets.get(i).getLabel();
                counts[i] = buckets.get(i).getCount();
            }

            distributionChartContainer.getChildren().add(
                    buildBarChart(labels, counts, "#C4B5FD", 460, 150, false)
            );

            Label caption = new Label(total + " graded submission"
                    + (total == 1 ? "" : "s") + " in this selection.");
            caption.setStyle("-fx-font-size: 11px; -fx-text-fill: -hsts-muted;");
            distributionChartContainer.getChildren().add(caption);
        }

        if (reportsStatusLabel != null) {
            reportsStatusLabel.setText("Reports loaded.");
        }
    }

    /**
     * Renders one of the three comparison reports. All three return the
     * same row shape, so they share this renderer; the bars carry each
     * exam's average and the line under them carries the figures a bar
     * cannot show.
     *
     * @param dataList list of ExamStatistics rows returned by the server
     * @param message server message
     */
    private void handleComparisonStatistics(List<?> dataList, String message) {

        if (comparisonChartContainer == null) {
            showCurrentStatus(message);
            return;
        }

        List<ExamStatistics> stats = new ArrayList<>();
        for (Object item : dataList) {
            if (item instanceof ExamStatistics stat) stats.add(stat);
        }

        clearReportContainer(comparisonChartContainer);

        if (stats.isEmpty()) {

            Label empty = new Label("No graded exams for this selection yet.");
            empty.setStyle("-fx-font-size: 12px; -fx-text-fill: #9CA3AF;");
            comparisonChartContainer.getChildren().add(empty);

        } else {

            String[] labels = new String[stats.size()];
            double[] averages = new double[stats.size()];
            for (int i = 0; i < stats.size(); i++) {
                labels[i] = blankToPlaceholder(stats.get(i).getLabel(), "Untitled");
                averages[i] = stats.get(i).getAverage();
            }

            comparisonChartContainer.getChildren().add(
                    buildBarChart(labels, averages, "#FCA5A5", 460, 150, false)
            );

            VBox legend = new VBox(2);
            for (ExamStatistics stat : stats) {
                Label line = new Label(String.format(
                        "%s:  avg %.1f  |  median %.1f  |  %.0f-%.0f  |  %d submission%s",
                        blankToPlaceholder(stat.getLabel(), "Untitled"),
                        stat.getAverage(),
                        stat.getMedian(),
                        stat.getMinScore(),
                        stat.getMaxScore(),
                        stat.getSubmissionCount(),
                        stat.getSubmissionCount() == 1 ? "" : "s"));
                line.setStyle("-fx-font-size: 11px; -fx-text-fill: -hsts-muted;");
                legend.getChildren().add(line);
            }
            comparisonChartContainer.getChildren().add(legend);
        }

        if (reportsStatusLabel != null) {
            reportsStatusLabel.setText(message);
        }
    }

    // ==========================================================
    //  PRINCIPAL LISTINGS - all exams / all results (spec 11)
    // ==========================================================

    /**
     * Caches the system-wide exam list and refreshes anything built from
     * it: the listing table (bound to the same observable list), and the
     * Reports selectors for exams and teachers.
     *
     * @param dataList list of Exam headers returned by the server
     * @param message server message
     */
    private void handleAllExamList(List<?> dataList, String message) {

        List<Exam> exams = new ArrayList<>();
        for (Object item : dataList) {
            if (item instanceof Exam exam) exams.add(exam);
        }

        allExams.setAll(exams);

        if (allExamsStatusLabel != null && isShowing("All Exams")) {
            allExamsStatusLabel.setText(message);
        }

        refreshDistributionExams();
        refreshComparisonSubjects();
    }

    /**
     * Caches the system-wide result list and refreshes anything built from
     * it: the listing table and the Reports student selector.
     *
     * @param dataList list of ExamResult rows returned by the server
     * @param message server message
     */
    private void handleAllResultList(List<?> dataList, String message) {

        List<ExamResult> results = new ArrayList<>();
        for (Object item : dataList) {
            if (item instanceof ExamResult result) results.add(result);
        }

        allResults.setAll(results);

        if (allResultsStatusLabel != null && isShowing("All Results")) {
            allResultsStatusLabel.setText(message);
        }

        refreshComparisonSubjects();
    }

    /**
     * Displays every exam in the system for the principal: one row per
     * exam across all teachers, with its approval state, activation,
     * entry code and scheduling window.
     */
    private void showAllExamsScreen() {

        TableView<Exam> table = new TableView<>();
        table.setItems(allExams);
        table.setPlaceholder(new Label("No exams have been created yet."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        table.getColumns().add(buildTextColumn("ID", 50,
                exam -> String.valueOf(exam.getExamId())));
        table.getColumns().add(buildTextColumn("Title", 200,
                exam -> displayName(exam.getTitle(), "Exam", exam.getExamId())));
        table.getColumns().add(buildTextColumn("Course", 130,
                exam -> blankToPlaceholder(exam.getCourse(), "-")));
        table.getColumns().add(buildTextColumn("Teacher", 140,
                exam -> displayName(exam.getTeacherName(), "Teacher", exam.getTeacherId())));
        table.getColumns().add(buildTextColumn("Questions", 75,
                exam -> String.valueOf(exam.getQuestionCount())));
        table.getColumns().add(buildTextColumn("Duration", 75,
                exam -> exam.getDurationMinutes() + " min"));
        table.getColumns().add(buildTextColumn("Approval", 95,
                exam -> blankToPlaceholder(exam.getApprovalStatus(), "-")));
        table.getColumns().add(buildTextColumn("Active", 65,
                exam -> exam.isActive() ? "Yes" : "No"));
        table.getColumns().add(buildTextColumn("Code", 60,
                exam -> blankToPlaceholder(exam.getExamCode(), "-")));
        table.getColumns().add(buildTextColumn("Window", 200, this::formatExamWindow));

        allExamsStatusLabel = new Label("Loading exams...");
        allExamsStatusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: -hsts-muted;");

        showPrincipalListingScreen(
                "All Exams",
                "Every exam in the system, across all teachers.",
                "HSTS - All Exams",
                table,
                allExamsStatusLabel);

        try {
            clientController.requestAllExams();
        } catch (IOException exception) {
            allExamsStatusLabel.setText("Could not load exams: " + exception.getMessage());
        }
    }

    /**
     * Displays every approved result in the system for the principal.
     * Grades a teacher has not released yet are absent here too - the
     * principal sees exactly what the students see.
     */
    private void showAllResultsScreen() {

        TableView<ExamResult> table = new TableView<>();
        table.setItems(allResults);
        table.setPlaceholder(new Label("No exam has been graded and approved yet."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        table.getColumns().add(buildTextColumn("ID", 60,
                result -> String.valueOf(result.getResultId())));
        table.getColumns().add(buildTextColumn("Exam", 260,
                result -> displayName(result.getExamTitle(), "Exam", result.getExamId())));
        table.getColumns().add(buildTextColumn("Student", 180,
                result -> displayName(result.getStudentName(), "Student", result.getStudentId())));
        table.getColumns().add(buildTextColumn("Grade", 110,
                result -> String.format("%.1f / %.0f", result.getGrade(), result.getMaximumGrade())));
        table.getColumns().add(buildTextColumn("Outcome", 100,
                result -> blankToPlaceholder(result.getStatus(), "-")));
        table.getColumns().add(buildTextColumn("Submitted", 190,
                result -> blankToPlaceholder(result.getSubmissionDate(), "-")));

        allResultsStatusLabel = new Label("Loading results...");
        allResultsStatusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: -hsts-muted;");

        showPrincipalListingScreen(
                "All Results",
                "Every approved exam result, across all students.",
                "HSTS - All Results",
                table,
                allResultsStatusLabel);

        try {
            clientController.requestAllResults();
        } catch (IOException exception) {
            allResultsStatusLabel.setText("Could not load results: " + exception.getMessage());
        }
    }

    /**
     * Renders the shared chrome of the two principal listings - sidebar,
     * top bar, title card - around whichever table it is given.
     *
     * @param activeLabel sidebar item to highlight, also the page title
     * @param subtitleText line under the page title
     * @param sceneTitle window title
     * @param table the listing itself
     * @param statusLabel label the listing reports into
     */
    private void showPrincipalListingScreen(String activeLabel, String subtitleText,
                                            String sceneTitle, TableView<?> table,
                                            Label statusLabel) {

        VBox sidebar = buildRoleSidebar("PRINCIPAL", activeLabel);

        TextField searchBox = new TextField();
        searchBox.setPromptText("Search anything...");
        searchBox.getStyleClass().add("search-field");
        HBox topBar = new HBox(searchBox);
        topBar.getStyleClass().add("top-bar");
        topBar.setAlignment(Pos.CENTER_LEFT);

        Label pageTitle = new Label(activeLabel);
        pageTitle.getStyleClass().add("welcome-label");

        Label subtitle = new Label(subtitleText);
        subtitle.setStyle("-fx-font-size: 13px; -fx-text-fill: -hsts-muted;");

        VBox.setVgrow(table, Priority.ALWAYS);

        VBox card = new VBox(12, pageTitle, subtitle, statusLabel, table);
        card.getStyleClass().add("section-card");
        VBox.setVgrow(card, Priority.ALWAYS);

        VBox mainContent = new VBox(16, topBar, card);
        mainContent.setPadding(new Insets(0, 0, 0, 20));
        HBox.setHgrow(mainContent, Priority.ALWAYS);

        HBox root = new HBox(16, sidebar, mainContent);
        root.getStyleClass().add("login-background");
        root.setPadding(new Insets(22));

        Scene scene = new Scene(root, windowWidth(), windowHeight());
        applyStyleSheet(scene);
        primaryStage.setScene(scene);
        primaryStage.setTitle(sceneTitle);
        applyStandardWindowSize();
    }

    /**
     * Builds one read-only text column, formatting each row itself rather
     * than naming a bean property - most of these columns are derived
     * ("12 min", "Yes"/"No", a scheduling window) rather than a plain field.
     *
     * @param heading column heading
     * @param width preferred width
     * @param valueOf renders one row to its cell text
     * @param <T> row type
     * @return the column
     */
    private <T> TableColumn<T, String> buildTextColumn(String heading, double width,
                                                       Function<T, String> valueOf) {

        TableColumn<T, String> column = new TableColumn<>(heading);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell ->
                new SimpleStringProperty(valueOf.apply(cell.getValue())));
        return column;
    }

    /**
     * Renders an exam's scheduling window for the listing.
     *
     * @param exam exam to describe
     * @return the window, or a note that the exam has none yet
     */
    private String formatExamWindow(Exam exam) {

        if (exam.getOpenAt() == null && exam.getCloseAt() == null) {
            return "Not scheduled";
        }
        return formatWindowBound(exam.getOpenAt()) + " to " + formatWindowBound(exam.getCloseAt());
    }

    /**
     * Trims a timestamp to minute precision for display.
     *
     * @param value timestamp, may be null
     * @return "yyyy-MM-dd HH:mm", or a dash
     */
    private String formatWindowBound(Timestamp value) {

        if (value == null) {
            return "-";
        }
        String text = value.toString();
        return text.length() >= 16 ? text.substring(0, 16) : text;
    }

    /**
     * Falls back to a name built from the identifier when a row carries no
     * readable name - unattributed questions and pre-BCrypt seed rows both
     * produce these.
     *
     * @param name name from the server, may be null or blank
     * @param kind what the identifier counts ("Teacher", "Exam", ...)
     * @param id identifier to fall back to
     * @return a label that always says something
     */
    private String displayName(String name, String kind, int id) {

        return (name == null || name.isBlank()) ? kind + " #" + id : name;
    }

    /**
     * Substitutes a placeholder for a missing value.
     *
     * @param value value to show
     * @param placeholder what to show instead when it is blank
     * @return one of the two
     */
    private String blankToPlaceholder(String value, String placeholder) {

        return (value == null || value.isBlank()) ? placeholder : value;
    }

    /**
     * Whether the window currently showing is the named screen. Used to
     * keep a response from writing into a status label belonging to a
     * screen the user has already left.
     *
     * @param titleFragment fragment of the window title to look for
     * @return true when that screen is on show
     */
    private boolean isShowing(String titleFragment) {

        return primaryStage != null
                && primaryStage.getTitle() != null
                && primaryStage.getTitle().contains(titleFragment);
    }

    // ══════════════════════════════════════════════════════════
    //  TEACHER EXAM RESULTS  (spec 10)
    // ══════════════════════════════════════════════════════════

    /**
     * Shows one exam's results: the students who sat it as a table, and the
     * same scores as a histogram, either across every שכבה or narrowed to one.
     *
     * Reached from the Exam Bank. The server decides whether the logged-in
     * teacher may see this exam at all, so nothing is filtered here for
     * safety — a refusal simply lands on the status label.
     *
     * @param exam the exam the teacher selected in the bank
     */
    private void showExamResultsScreen(
            Exam exam
    ) {

        showExamResultsScreen(exam, "Exam Bank", "Back to Exam Bank",
                this::showExamBankScreen);
    }

    /**
     * The same screen, told where it was opened from, so its Back button and
     * the highlighted sidebar entry match the way in — the Exam Bank's View
     * Results button, or the Reports table's.
     *
     * @param exam the exam whose results to show
     * @param sidebarActive sidebar entry to highlight
     * @param backLabel text for the Back button
     * @param backAction screen the Back button returns to
     */
    private void showExamResultsScreen(
            Exam exam,
            String sidebarActive,
            String backLabel,
            Runnable backAction
    ) {

        if (exam == null) {
            return;
        }

        examResultsExam = exam;
        examResults.clear();

        VBox sidebar = buildRoleSidebar("TEACHER", sidebarActive);

        TextField searchBox = new TextField();
        searchBox.setPromptText("Search anything...");
        searchBox.getStyleClass().add("search-field");
        HBox topBar = new HBox(searchBox);
        topBar.getStyleClass().add("top-bar");
        topBar.setAlignment(Pos.CENTER_LEFT);

        Label pageTitle = new Label("Exam Results");
        pageTitle.getStyleClass().add("welcome-label");

        Label subtitle = new Label(
                displayName(exam.getTitle(), "Exam", exam.getExamId())
                        + "  •  " + blankToPlaceholder(exam.getCourse(), "no course")
        );
        subtitle.setStyle("-fx-font-size: 13px; -fx-text-fill: -hsts-muted;");

        // ── Grade-level filter ────────────────────────────
        Label filterLabel = new Label("Grade level:");
        filterLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: -hsts-strong;");

        examResultsGradeFilter = new ComboBox<>();
        examResultsGradeFilter.getItems().addAll(
                ALL_GRADE_LEVELS, "9", "10", "11", "12");
        examResultsGradeFilter.getSelectionModel().selectFirst();
        examResultsGradeFilter.setPrefWidth(150);
        examResultsGradeFilter.setOnAction(event -> loadExamResults());

        Button refreshButton = new Button("Refresh");
        refreshButton.getStyleClass().add("primary-button");
        refreshButton.setOnAction(event -> loadExamResults());

        Button backButton = new Button(backLabel);
        backButton.getStyleClass().add("secondary-button");
        backButton.setOnAction(event -> backAction.run());

        Region filterSpacer = new Region();
        HBox.setHgrow(filterSpacer, Priority.ALWAYS);

        HBox filterRow = new HBox(12, filterLabel, examResultsGradeFilter, refreshButton,
                filterSpacer, backButton);
        filterRow.setAlignment(Pos.CENTER_LEFT);

        // ── Summary + status ──────────────────────────────
        examResultsSummaryLabel = new Label("—");
        examResultsSummaryLabel.setWrapText(true);
        examResultsSummaryLabel.setStyle(
                "-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: -hsts-strong;");

        examResultsStatusLabel = new Label("Loading results...");
        examResultsStatusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: -hsts-muted;");

        // ── Histogram ─────────────────────────────────────
        examResultsHistogramContainer = new VBox(8);
        examResultsHistogramContainer.getStyleClass().add("section-card");
        Label histogramTitle = new Label("Score Distribution");
        histogramTitle.getStyleClass().add("section-title");
        examResultsHistogramContainer.getChildren().add(histogramTitle);

        // ── Table ─────────────────────────────────────────
        TableView<StudentExamResult> table = new TableView<>();
        table.setItems(examResults);
        table.setPlaceholder(new Label("No graded results for this selection yet."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        table.getColumns().add(buildTextColumn("Student", 220,
                row -> displayName(row.getStudentName(), "Student", row.getStudentId())));
        table.getColumns().add(buildTextColumn("Grade level", 100,
                row -> row.getGradeLevel() == null ? "—" : String.valueOf(row.getGradeLevel())));
        table.getColumns().add(buildTextColumn("Score", 90,
                row -> String.format("%.1f", row.getScore())));
        table.getColumns().add(buildTextColumn("Outcome", 100,
                row -> blankToPlaceholder(row.getStatus(), "—")));
        table.getColumns().add(buildTextColumn("Submitted", 190,
                row -> blankToPlaceholder(row.getSubmittedAt(), "—")));

        VBox.setVgrow(table, Priority.ALWAYS);

        VBox tableCard = new VBox(10, examResultsSummaryLabel, table);
        tableCard.getStyleClass().add("section-card");
        VBox.setVgrow(tableCard, Priority.ALWAYS);

        VBox mainContent = new VBox(14, topBar, pageTitle, subtitle, filterRow,
                examResultsStatusLabel, examResultsHistogramContainer, tableCard);
        mainContent.setPadding(new Insets(0, 0, 0, 20));
        HBox.setHgrow(mainContent, Priority.ALWAYS);

        HBox root = new HBox(16, sidebar, mainContent);
        root.getStyleClass().add("login-background");
        root.setPadding(new Insets(22));

        Scene scene = new Scene(root, windowWidth(), windowHeight());
        applyStyleSheet(scene);
        primaryStage.setScene(scene);
        primaryStage.setTitle("HSTS - Exam Results");
        applyStandardWindowSize();

        loadExamResults();
    }

    /**
     * Fetches the results for the exam on show, at the grade level selected.
     */
    private void loadExamResults() {

        if (examResultsExam == null) {
            return;
        }

        try {

            clientController.requestExamResults(
                    new ExamResultsQuery(
                            examResultsExam.getExamId(),
                            selectedGradeLevelFilter()
                    )
            );

            if (examResultsStatusLabel != null) {
                examResultsStatusLabel.setText("Loading results...");
            }

        } catch (IllegalArgumentException | IOException exception) {

            if (examResultsStatusLabel != null) {
                examResultsStatusLabel.setText(
                        "Could not load results: " + exception.getMessage());
            }
        }
    }

    /**
     * Reads the grade-level filter.
     *
     * @return the selected grade level, or null for every grade level
     */
    private Integer selectedGradeLevelFilter() {

        if (examResultsGradeFilter == null) {
            return null;
        }

        String selected = examResultsGradeFilter.getValue();
        if (selected == null || ALL_GRADE_LEVELS.equals(selected)) {
            return null;
        }

        try {
            return Integer.valueOf(selected.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * Renders a set of exam results: the table rows, the summary line and the
     * histogram all come from this one response, so the grade-level filter
     * moves them together.
     *
     * @param dataList list of StudentExamResult rows returned by the server
     * @param message server message
     */
    private void handleExamResultsList(
            List<?> dataList,
            String message
    ) {

        List<StudentExamResult> rows = new ArrayList<>();
        for (Object item : dataList) {
            if (item instanceof StudentExamResult row) rows.add(row);
        }

        examResults.setAll(rows);

        List<Double> scores = new ArrayList<>();
        int passed = 0;
        for (StudentExamResult row : rows) {
            scores.add(row.getScore());
            if (row.isPassed()) {
                passed++;
            }
        }

        if (examResultsSummaryLabel != null) {

            if (rows.isEmpty()) {

                examResultsSummaryLabel.setText(
                        "No graded results for this selection yet.");

            } else {

                // The same reduction the principal's comparison reports use,
                // so an average or median never disagrees between screens.
                ExamStatistics stats = ExamStatistics.summarise("", scores);

                examResultsSummaryLabel.setText(String.format(
                        "%d student%s   |   average %.1f   |   median %.1f   |   "
                                + "lowest %.0f, highest %.0f   |   %d passed (%.0f%%)",
                        rows.size(),
                        rows.size() == 1 ? "" : "s",
                        stats.getAverage(),
                        stats.getMedian(),
                        stats.getMinScore(),
                        stats.getMaxScore(),
                        passed,
                        (passed * 100.0) / rows.size()));
            }
        }

        renderExamResultsHistogram(scores);

        if (examResultsStatusLabel != null) {
            examResultsStatusLabel.setText(message);
        }
    }

    /**
     * Draws the score histogram for the filtered set, in the same 10-point
     * bands the principal's distribution report uses — the banding comes from
     * {@code GradeDistributionBucket.bucketize} rather than being repeated
     * here.
     *
     * @param scores the filtered scores
     */
    private void renderExamResultsHistogram(
            List<Double> scores
    ) {

        if (examResultsHistogramContainer == null) {
            return;
        }

        clearReportContainer(examResultsHistogramContainer);

        if (scores.isEmpty()) {

            Label empty = new Label("Nothing to chart for this selection yet.");
            empty.setStyle("-fx-font-size: 12px; -fx-text-fill: #9CA3AF;");
            examResultsHistogramContainer.getChildren().add(empty);
            return;
        }

        List<GradeDistributionBucket> buckets =
                GradeDistributionBucket.bucketize(scores);

        String[] labels = new String[buckets.size()];
        double[] counts = new double[buckets.size()];
        for (int i = 0; i < buckets.size(); i++) {
            labels[i] = buckets.get(i).getLabel();
            counts[i] = buckets.get(i).getCount();
        }

        examResultsHistogramContainer.getChildren().add(
                buildBarChart(labels, counts, "#93C5FD", 900, 170, false)
        );
    }

    /**
     * Displays the Question Bank.
     */
    private void showQuestionBankScreen() {

        questionTable =
                createQuestionTable();

        questionTable.setItems(
                displayedQuestions
        );

        VBox editForm =
                createEditForm();

        Button loadButton =
                new Button("Load Questions");

        loadButton.getStyleClass().add(
                "primary-button"
        );

        loadButton.setOnAction(
                event -> requestAllQuestions()
        );

        Button updateButton =
                new Button("Update Question");

        updateButton.getStyleClass().add(
                "success-button"
        );

        updateButton.setOnAction(
                event -> updateSelectedQuestion()
        );

        Button clearButton =
                new Button("Clear Fields");

        clearButton.getStyleClass().add(
                "secondary-button"
        );

        clearButton.setOnAction(
                event -> clearQuestionFields()
        );

        Button saveNewButton =
                new Button("Save New Question");

        saveNewButton.getStyleClass().add(
                "primary-button"
        );

        saveNewButton.setOnAction(
                event -> createNewQuestion()
        );

        Button deleteButton =
                new Button("Delete Question");

        deleteButton.getStyleClass().add(
                "danger-button"
        );

        deleteButton.setOnAction(
                event -> deleteSelectedQuestion()
        );

        Button duplicateButton =
                new Button("Duplicate Question");

        duplicateButton.getStyleClass().add(
                "primary-button"
        );

        duplicateButton.setOnAction(
                event -> duplicateSelectedQuestion()
        );

        Button historyButton =
                new Button("View History");

        historyButton.getStyleClass().add(
                "secondary-button"
        );

        historyButton.setOnAction(
                event -> showHistoryOfSelectedQuestion()
        );

        // Two even columns, every button stretched to its cell, so the
        // labels' differing lengths cannot make the grid ragged.
        GridPane actionGrid = new GridPane();

        actionGrid.setHgap(10);
        actionGrid.setVgap(8);

        ColumnConstraints actionCol1 = new ColumnConstraints();
        ColumnConstraints actionCol2 = new ColumnConstraints();

        actionCol1.setPercentWidth(50);
        actionCol2.setPercentWidth(50);
        actionCol1.setHgrow(Priority.ALWAYS);
        actionCol2.setHgrow(Priority.ALWAYS);
        actionCol1.setFillWidth(true);
        actionCol2.setFillWidth(true);

        actionGrid.getColumnConstraints().addAll(
                actionCol1,
                actionCol2
        );

        // create / edit, then the non-destructive variants, then reset /
        // delete last so the dangerous one is furthest from the first click.
        actionGrid.add(saveNewButton,   0, 0);
        actionGrid.add(updateButton,    1, 0);
        actionGrid.add(duplicateButton, 0, 1);
        actionGrid.add(historyButton,   1, 1);
        actionGrid.add(clearButton,     0, 2);
        actionGrid.add(deleteButton,    1, 2);

        actionGrid.add(loadButton, 0, 3, 2, 1);

        for (Button actionButton : new Button[] {
                saveNewButton,
                updateButton,
                duplicateButton,
                historyButton,
                clearButton,
                deleteButton,
                loadButton
        }) {

            actionButton.setMaxWidth(Double.MAX_VALUE);
            actionButton.getStyleClass().add("qb-action-button");
            GridPane.setFillWidth(actionButton, true);
            GridPane.setHgrow(actionButton, Priority.ALWAYS);
        }

        VBox actionButtons = new VBox(
                8,
                actionGrid
        );

        questionStatusLabel = new Label();

        questionStatusLabel.getStyleClass().add(
                "status-label"
        );

        questionStatusLabel.setWrapText(true);

        Label editTitle =
                new Label("Edit / New Question");

        editTitle.getStyleClass().add(
                "page-title"
        );

        VBox rightPanel = new VBox(
                16,
                editTitle,
                editForm,
                actionButtons,
                questionStatusLabel
        );

        rightPanel.getStyleClass().add(
                "question-panel"
        );

        rightPanel.setPrefWidth(460);

        questionTable
                .getSelectionModel()
                .selectedItemProperty()
                .addListener(
                        (
                                observable,
                                oldQuestion,
                                selectedQuestion
                        ) -> {

                            if (selectedQuestion != null) {

                                displayQuestionDetails(
                                        selectedQuestion
                                );
                            }
                        }
                );

        // ── Sidebar ───────────────────────────────────────
        VBox sidebarQB = buildRoleSidebar("TEACHER", "Question Bank");

        // ── Search bar + title ────────────────────────────
        TextField qbSearchBox = new TextField();
        qbSearchBox.setPromptText("Search anything...");
        qbSearchBox.getStyleClass().add("search-field");
        HBox qbTopBar = new HBox(qbSearchBox);
        qbTopBar.getStyleClass().add("top-bar");
        qbTopBar.setAlignment(Pos.CENTER_LEFT);

        Label qbPageTitle = new Label("Question Bank");
        qbPageTitle.getStyleClass().add("welcome-label");

        HBox paginationBar = createPaginationBar();

        VBox centerArea = new VBox(12, qbTopBar, qbPageTitle, questionTable, paginationBar);
        VBox.setVgrow(questionTable, Priority.ALWAYS);
        HBox.setHgrow(centerArea, Priority.ALWAYS);

        HBox contentArea = new HBox(18, centerArea, rightPanel);
        HBox.setHgrow(contentArea, Priority.ALWAYS);
        VBox.setVgrow(contentArea, Priority.ALWAYS);

        VBox mainContent = new VBox(0, contentArea);
        mainContent.setPadding(new Insets(0, 0, 0, 0));
        VBox.setVgrow(contentArea, Priority.ALWAYS);
        HBox.setHgrow(mainContent, Priority.ALWAYS);

        HBox root = new HBox(16, sidebarQB, mainContent);
        root.getStyleClass().add("login-background");
        root.setPadding(new Insets(22));

        displayCurrentPage();

        Scene scene = new Scene(root, windowWidth(), windowHeight());
        applyStyleSheet(scene);
        primaryStage.setScene(scene);
        primaryStage.setTitle("HSTS - Question Bank");
        applyStandardWindowSize();
    }

    /**
     * Creates the pagination controls.
     *
     * @return pagination bar
     */
    private HBox createPaginationBar() {

        previousPageButton =
                new Button("Previous");

        previousPageButton.getStyleClass().add(
                "secondary-button"
        );

        previousPageButton.setOnAction(
                event -> showPreviousPage()
        );

        nextPageButton =
                new Button("Next");

        nextPageButton.getStyleClass().add(
                "primary-button"
        );

        nextPageButton.setOnAction(
                event -> showNextPage()
        );

        pageLabel =
                new Label("Page 0 of 0");

        pageLabel.getStyleClass().add(
                "form-label"
        );

        HBox paginationBar = new HBox(
                15,
                previousPageButton,
                pageLabel,
                nextPageButton
        );

        paginationBar.setAlignment(
                Pos.CENTER
        );

        return paginationBar;
    }

    /**
     * Requests all questions from the server.
     */
    private void requestAllQuestions() {

        try {

            clientController.requestAllQuestions();

            if (questionStatusLabel != null) {

                questionStatusLabel.setText(
                        "Loading questions..."
                );
            }

        } catch (IOException exception) {

            if (questionStatusLabel != null) {

                questionStatusLabel.setText(
                        "Could not load questions: "
                                + exception.getMessage()
                );
            }
        }
    }

    /**
     * Handles a returned question list.
     *
     * @param dataList returned data
     * @param message server message
     */
    private void handleQuestionList(
            List<?> dataList,
            String message
    ) {

        List<Question> receivedQuestions =
                new ArrayList<>();

        for (Object item : dataList) {

            if (item instanceof Question question) {
                receivedQuestions.add(question);
            }
        }

        allQuestions.setAll(
                receivedQuestions
        );

        currentPage = 0;
        displayCurrentPage();

        if (questionStatusLabel != null) {

            questionStatusLabel.setText(
                    message
                            + " Total questions: "
                            + allQuestions.size()
            );
        }

        String dashTitle = primaryStage.getTitle();
        if (dashTitle.contains("Teacher Dashboard") && teacherQCountLabel != null) {
            teacherQCountLabel.setText(String.valueOf(receivedQuestions.size()));
        }
        if (dashTitle.contains("Admin Dashboard") && adminQCountLabel != null) {
            adminQCountLabel.setText(String.valueOf(receivedQuestions.size()));
        }
        if (dashTitle.contains("My Reports") && currentUser != null) {
            // The bank is shared, so the Reports card counts only the rows this
            // teacher wrote. teacherId 0 means unattributed (pre-dates the column).
            long mine = receivedQuestions.stream()
                    .filter(q -> q.getTeacherId() == currentUser.getUserId())
                    .count();
            teacherReportsQuestionLabel.setText(String.valueOf(mine));
        }
    }

    /**
     * Displays up to six questions on the current page.
     */
    private void displayCurrentPage() {

        displayedQuestions.clear();

        int totalQuestions =
                allQuestions.size();

        if (totalQuestions == 0) {

            currentPage = 0;
            updatePaginationControls();

            if (questionTable != null) {
                questionTable.refresh();
            }

            return;
        }

        int totalPages =
                calculateTotalPages();

        if (currentPage >= totalPages) {
            currentPage = totalPages - 1;
        }

        if (currentPage < 0) {
            currentPage = 0;
        }

        int startIndex =
                currentPage * QUESTIONS_PER_PAGE;

        int endIndex =
                Math.min(
                        startIndex + QUESTIONS_PER_PAGE,
                        totalQuestions
                );

        displayedQuestions.addAll(
                allQuestions.subList(
                        startIndex,
                        endIndex
                )
        );

        if (questionTable != null) {

            questionTable
                    .getSelectionModel()
                    .clearSelection();

            questionTable.refresh();
        }

        clearQuestionFieldsSafely();
        updatePaginationControls();
    }

    /**
     * Moves to the next page.
     */
    private void showNextPage() {

        int totalPages =
                calculateTotalPages();

        if (currentPage < totalPages - 1) {

            currentPage++;
            displayCurrentPage();
        }
    }

    /**
     * Moves to the previous page.
     */
    private void showPreviousPage() {

        if (currentPage > 0) {

            currentPage--;
            displayCurrentPage();
        }
    }

    /**
     * Calculates the number of question pages.
     *
     * @return page count
     */
    private int calculateTotalPages() {

        if (allQuestions.isEmpty()) {
            return 0;
        }

        return (int) Math.ceil(
                allQuestions.size()
                        / (double) QUESTIONS_PER_PAGE
        );
    }

    /**
     * Updates the pagination controls.
     */
    private void updatePaginationControls() {

        if (previousPageButton == null
                || nextPageButton == null
                || pageLabel == null) {
            return;
        }

        int totalPages =
                calculateTotalPages();

        if (totalPages == 0) {

            pageLabel.setText("Page 0 of 0");
            previousPageButton.setDisable(true);
            nextPageButton.setDisable(true);
            return;
        }

        pageLabel.setText(
                "Page "
                        + (currentPage + 1)
                        + " of "
                        + totalPages
        );

        previousPageButton.setDisable(
                currentPage == 0
        );

        nextPageButton.setDisable(
                currentPage >= totalPages - 1
        );
    }

     /**
      * Handles an updated question.
      *
      * @param updatedQuestion updated question
      * @param message server message
      */
     private void handleUpdatedQuestion(
             Question updatedQuestion,
             String message
     ) {

         replaceQuestionInAllQuestions(
                 updatedQuestion
         );

         displayCurrentPage();

         selectQuestionOnCurrentPage(
                 updatedQuestion.getId()
         );

         if (questionStatusLabel != null) {

             questionStatusLabel.setText(
                     message
             );
         }
     }

     /**
      * Replaces a question in the complete question list.
      *
      * @param updatedQuestion updated question
      */
     private void replaceQuestionInAllQuestions(
             Question updatedQuestion
     ) {

         for (int index = 0;
              index < allQuestions.size();
              index++) {

             Question currentQuestion =
                     allQuestions.get(index);

             if (currentQuestion.getId()
                     == updatedQuestion.getId()) {

                 allQuestions.set(
                         index,
                         updatedQuestion
                 );

                 return;
             }
         }

         allQuestions.add(updatedQuestion);
     }

     /**
      * Selects a question on the current page.
      *
      * @param questionId question ID
      */
     private void selectQuestionOnCurrentPage(
             int questionId
     ) {

         if (questionTable == null) {
             return;
         }

         for (Question question
                 : displayedQuestions) {

             if (question.getId()
                     == questionId) {

                 questionTable
                         .getSelectionModel()
                         .select(question);

                 displayQuestionDetails(question);
                 return;
             }
         }
     }

     /**
      * Creates the Question Bank table.
      *
      * @return question table
      */
     private TableView<Question> createQuestionTable() {

         TableView<Question> table =
                 new TableView<>();

         TableColumn<Question, Integer> idColumn =
                 new TableColumn<>("ID");

         idColumn.setCellValueFactory(
                 new PropertyValueFactory<>(
                         "id"
                 )
         );

         TableColumn<Question, Integer> courseColumn =
                 new TableColumn<>("Course");

         courseColumn.setCellValueFactory(
                 new PropertyValueFactory<>(
                         "courseId"
                 )
         );

         TableColumn<Question, String> questionColumn =
                 new TableColumn<>("Question");

         questionColumn.setCellValueFactory(
                 new PropertyValueFactory<>(
                         "questionText"
                 )
         );

         TableColumn<Question, String> correctColumn =
                 new TableColumn<>("Correct");

         correctColumn.setCellValueFactory(
                 new PropertyValueFactory<>(
                         "correctAnswer"
                 )
         );

        idColumn.setPrefWidth(60);
        courseColumn.setPrefWidth(150);
        questionColumn.setPrefWidth(420);
        correctColumn.setPrefWidth(100);

        table.getColumns().addAll(
                idColumn,
                courseColumn,
                questionColumn,
                correctColumn
        );

        table.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY
        );

        table.setPlaceholder(
                new Label(
                        "Click Load Questions to display the question bank."
                )
        );

        return table;
    }

    /**
     * Creates the question-editing form.
     *
     * @return editing form
     */
    private VBox createEditForm() {

        courseField = new TextField();
        courseField.setPromptText("Course ID (numeric)");

        questionTextArea = new TextArea();
        questionTextArea.setPromptText(
                "Enter the question text"
        );

        questionTextArea.setPrefRowCount(3);
        questionTextArea.setWrapText(true);

        answerAField = new TextField();
        answerAField.setPromptText("Answer A");

        answerBField = new TextField();
        answerBField.setPromptText("Answer B");

        answerCField = new TextField();
        answerCField.setPromptText("Answer C");

        answerDField = new TextField();
        answerDField.setPromptText("Answer D");

        correctAnswerComboBox =
                new ComboBox<>();

        correctAnswerComboBox
                .getItems()
                .addAll("A", "B", "C", "D");

        correctAnswerComboBox.setPromptText(
                "Select correct answer"
        );

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(12);

        addFormRow(form, 0, "Course ID", courseField);
        addFormRow(form, 1, "Question", questionTextArea);
        addFormRow(form, 2, "Answer A", answerAField);
        addFormRow(form, 3, "Answer B", answerBField);
        addFormRow(form, 4, "Answer C", answerCField);
        addFormRow(form, 5, "Answer D", answerDField);

        addFormRow(
                form,
                6,
                "Correct answer",
                correctAnswerComboBox
        );

        Button uploadImageButton = new Button("Upload .png");
        uploadImageButton.getStyleClass().add("secondary-button");
        uploadImageButton.setOnAction(e -> chooseAndUploadQuestionImage());

        questionImageLabel = new Label("No image");
        questionImageLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: -hsts-muted;");

        HBox imageRow = new HBox(10, uploadImageButton, questionImageLabel);
        imageRow.setAlignment(Pos.CENTER_LEFT);
        addFormRow(form, 7, "Image (.png)", imageRow);

        return new VBox(form);
    }

    /**
     * Opens a file chooser restricted to PNG files, copies the chosen image into
     * the project's local {@code images/} folder, and records its filename to be
     * stored as the question's visual aid.
     */
    private void chooseAndUploadQuestionImage() {

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a PNG image");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PNG images", "*.png"));

        java.io.File selected = chooser.showOpenDialog(primaryStage);
        if (selected == null) {
            return;
        }

        // Defensive: enforce .png even though the filter should already restrict it.
        if (!selected.getName().toLowerCase().endsWith(".png")) {
            if (questionStatusLabel != null) {
                questionStatusLabel.setText("Only .png images are allowed.");
            }
            return;
        }

        try {
            java.io.File target = new java.io.File(ImageResolver.imagesDir(), selected.getName());
            java.nio.file.Files.copy(
                    selected.toPath(),
                    target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            questionImageFileName = selected.getName();
            if (questionImageLabel != null) {
                questionImageLabel.setText(questionImageFileName);
            }
            if (questionStatusLabel != null) {
                questionStatusLabel.setText("Image uploaded: " + questionImageFileName);
            }
        } catch (Exception ex) {
            if (questionStatusLabel != null) {
                questionStatusLabel.setText("Could not upload image: " + ex.getMessage());
            }
        }
    }

    /**
     * Adds a row to a form.
     *
     * @param form form grid
     * @param row row number
     * @param text label text
     * @param control form control
     */
    private void addFormRow(
            GridPane form,
            int row,
            String text,
            javafx.scene.Node control
    ) {

        Label label = new Label(text);
        label.getStyleClass().add("form-label");

        form.add(label, 0, row);
        form.add(control, 1, row);
    }

     /**
      * Displays selected question details.
      *
      * @param question selected question
      */
     private void displayQuestionDetails(
             Question question
     ) {

         if (question == null
                 || courseField == null) {
             return;
         }

         courseField.setText(String.valueOf(question.getCourseId()));

         questionTextArea.setText(
                 question.getQuestionText()
         );

         answerAField.setText(question.getOptionA());
         answerBField.setText(question.getOptionB());
         answerCField.setText(question.getOptionC());
         answerDField.setText(question.getOptionD());

         correctAnswerComboBox.setValue(
                 question.getCorrectAnswer()
         );

         questionImageFileName = question.getVisualAidUrl();
         if (questionImageLabel != null) {
             questionImageLabel.setText(
                     (questionImageFileName == null || questionImageFileName.isBlank())
                             ? "No image" : questionImageFileName);
         }
     }

     /**
      * Sends an updated question.
      */
     /**
      * Sends a new question to the server for creation.
      */
     /**
      * Sends a delete request for the selected question.
      */
     private void deleteSelectedQuestion() {

         Question selectedQuestion =
                 questionTable
                         .getSelectionModel()
                         .getSelectedItem();

         if (selectedQuestion == null) {

             questionStatusLabel.setText(
                     "Please select a question to delete."
             );

             return;
         }

         try {

             clientController.requestDeleteQuestion(
                     selectedQuestion.getId()
             );

             questionStatusLabel.setText(
                     "Deleting question "
                             + selectedQuestion.getId()
                             + "..."
             );

         } catch (IOException exception) {

             questionStatusLabel.setText(
                     "Could not delete question: "
                             + exception.getMessage()
             );
         }
     }

     /**
      * Removes a deleted question from the local list and refreshes the view.
      *
      * @param questionId identifier of the deleted question
      * @param message server message
      */
     private void handleDeletedQuestion(
             int questionId,
             String message
     ) {

         allQuestions.removeIf(
                 q -> q.getId() == questionId
         );

         if (currentPage >= calculateTotalPages()
                 && currentPage > 0) {

             currentPage--;
         }

         displayCurrentPage();
         clearQuestionFieldsSafely();

         if (questionStatusLabel != null) {
             questionStatusLabel.setText(message);
         }
     }

     private void createNewQuestion() {

         if (!validateQuestionFields()) {

             questionStatusLabel.setText(
                     "Please complete all question fields."
             );

             return;
         }

         int courseId;

         try {
             courseId = Integer.parseInt(
                     courseField.getText().trim()
             );
         } catch (NumberFormatException e) {

             questionStatusLabel.setText(
                     "Course ID must be a valid number."
             );

             return;
         }

         Question newQuestion = new Question(
                 0,
                 questionTextArea.getText().trim(),
                 answerAField.getText().trim(),
                 answerBField.getText().trim(),
                 answerCField.getText().trim(),
                 answerDField.getText().trim(),
                 correctAnswerComboBox.getValue(),
                 (questionImageFileName != null && !questionImageFileName.isBlank())
                         ? questionImageFileName : null,
                 courseId
         );

         if (currentUser != null) {
             newQuestion.setTeacherId(currentUser.getUserId());
         }

         try {

             clientController.requestCreateQuestion(
                     newQuestion
             );

             questionStatusLabel.setText(
                     "Sending new question..."
             );

         } catch (IOException exception) {

             questionStatusLabel.setText(
                     "Could not create question: "
                             + exception.getMessage()
             );
         }
     }

     private void updateSelectedQuestion() {

         Question selectedQuestion =
                 questionTable
                         .getSelectionModel()
                         .getSelectedItem();

         if (selectedQuestion == null) {

             questionStatusLabel.setText(
                     "Please select a question first."
             );

             return;
         }

         if (!validateQuestionFields()) {

             questionStatusLabel.setText(
                     "Please complete all question fields."
             );

             return;
         }

         int courseId;
         try {
             courseId = Integer.parseInt(courseField.getText().trim());
         } catch (NumberFormatException e) {
             questionStatusLabel.setText(
                     "Course ID must be a valid number."
             );
             return;
         }

          Question updatedQuestion =
                  new Question(
                          selectedQuestion.getId(),
                          questionTextArea.getText().trim(),
                          answerAField.getText().trim(),
                          answerBField.getText().trim(),
                          answerCField.getText().trim(),
                          answerDField.getText().trim(),
                          correctAnswerComboBox.getValue(),
                          (questionImageFileName != null && !questionImageFileName.isBlank())
                                  ? questionImageFileName : selectedQuestion.getVisualAidUrl(),
                          courseId
                  );

         try {

             clientController.requestQuestionUpdate(
                     updatedQuestion
             );

             questionStatusLabel.setText(
                     "Sending the updated question..."
             );

         } catch (IOException exception) {

             questionStatusLabel.setText(
                     "Could not update the question: "
                             + exception.getMessage()
             );
         }
     }

    /**
     * Validates the question fields.
     *
     * @return true when valid
     */
    private boolean validateQuestionFields() {

        return !courseField.getText().trim().isEmpty()
                && !questionTextArea.getText().trim().isEmpty()
                && !answerAField.getText().trim().isEmpty()
                && !answerBField.getText().trim().isEmpty()
                && !answerCField.getText().trim().isEmpty()
                && !answerDField.getText().trim().isEmpty()
                && correctAnswerComboBox.getValue() != null;
    }

    // ══════════════════════════════════════════════════════════
    //  QUESTION DUPLICATION + VERSION HISTORY  (spec 2.2 / 2.3)
    // ══════════════════════════════════════════════════════════

    /**
     * Asks the server to copy the selected question into a new bank row.
     * The copy comes back as a normal question and joins the bank list.
     */
    private void duplicateSelectedQuestion() {

        Question selectedQuestion =
                questionTable
                        .getSelectionModel()
                        .getSelectedItem();

        if (selectedQuestion == null) {

            questionStatusLabel.setText(
                    "Please select a question to duplicate."
            );

            return;
        }

        try {

            clientController.requestDuplicateQuestion(
                    selectedQuestion.getId()
            );

            questionStatusLabel.setText(
                    "Duplicating question "
                            + selectedQuestion.getId()
                            + "..."
            );

        } catch (IOException exception) {

            questionStatusLabel.setText(
                    "Could not duplicate question: "
                            + exception.getMessage()
            );
        }
    }

    /**
     * Asks the server for the archived earlier versions of the selected
     * question. The response opens the history dialog.
     */
    private void showHistoryOfSelectedQuestion() {

        Question selectedQuestion =
                questionTable
                        .getSelectionModel()
                        .getSelectedItem();

        if (selectedQuestion == null) {

            questionStatusLabel.setText(
                    "Please select a question to see its history."
            );

            return;
        }

        try {

            clientController.requestQuestionHistory(
                    selectedQuestion.getId()
            );

            questionStatusLabel.setText(
                    "Loading the history of question "
                            + selectedQuestion.getId()
                            + "..."
            );

        } catch (IOException exception) {

            questionStatusLabel.setText(
                    "Could not load question history: "
                            + exception.getMessage()
            );
        }
    }

    /**
     * Shows the earlier versions of a question, newest first.
     *
     * Editing a question keeps its previous text in the bank rather than
     * overwriting it (spec 2.2); this is where a teacher reads that trail.
     * It is deliberately read-only — restoring an old version would be an
     * edit of its own, and would go through the normal update path.
     *
     * @param dataList list of Question rows returned by the server
     * @param message server message, used as the dialog header
     */
    private void showQuestionHistoryDialog(
            List<?> dataList,
            String message
    ) {

        List<Question> versions = new ArrayList<>();
        for (Object item : dataList) {
            if (item instanceof Question version) versions.add(version);
        }

        if (questionStatusLabel != null) {
            questionStatusLabel.setText(message);
        }

        VBox content = new VBox(12);

        if (versions.isEmpty()) {

            Label empty = new Label(
                    "No earlier versions — this question has not been edited yet."
            );
            empty.setWrapText(true);
            empty.setStyle("-fx-font-size: 13px; -fx-text-fill: -hsts-muted;");
            content.getChildren().add(empty);

        } else {

            // Newest first, so the highest version number comes first.
            int versionNumber = versions.size();
            for (Question version : versions) {
                content.getChildren().add(
                        buildQuestionVersionCard(version, versionNumber)
                );
                versionNumber--;
            }
        }

        javafx.scene.control.ScrollPane scroll =
                new javafx.scene.control.ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportWidth(560);
        scroll.setPrefViewportHeight(420);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(primaryStage);
        dialog.setTitle("Question History");
        dialog.setHeaderText(message);
        dialog.getDialogPane().setContent(scroll);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        // The dialog builds its own scene, so it needs the stylesheet and the
        // user's theme applied to it separately from the main window.
        if (dialog.getDialogPane().getScene() != null) {
            applyStyleSheet(dialog.getDialogPane().getScene());
        }

        dialog.showAndWait();
    }

    /**
     * Builds one card of the question history dialog.
     *
     * @param version the archived version
     * @param versionNumber its position in the trail, 1 being the oldest
     * @return the card
     */
    private VBox buildQuestionVersionCard(
            Question version,
            int versionNumber
    ) {

        Label heading = new Label("Version " + versionNumber);
        heading.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: -hsts-strong;");

        Label text = new Label(
                blankToPlaceholder(version.getQuestionText(), "(no question text)")
        );
        text.setWrapText(true);
        text.setStyle("-fx-font-size: 13px; -fx-text-fill: -hsts-strong;");

        VBox options = new VBox(
                3,
                buildVersionOptionLabel("A", version.getOptionA(), version.getCorrectAnswer()),
                buildVersionOptionLabel("B", version.getOptionB(), version.getCorrectAnswer()),
                buildVersionOptionLabel("C", version.getOptionC(), version.getCorrectAnswer()),
                buildVersionOptionLabel("D", version.getOptionD(), version.getCorrectAnswer())
        );

        VBox card = new VBox(8, heading, text, options);
        card.setStyle("-fx-background-color: -hsts-row; -fx-background-radius: 10px; -fx-padding: 12px 14px;");
        return card;
    }

    /**
     * Builds one option line of a history card, marking the answer that was
     * correct in that version.
     *
     * @param letter option letter
     * @param optionText option text as it read then
     * @param correctAnswer the version's correct option
     * @return the line
     */
    private Label buildVersionOptionLabel(
            String letter,
            String optionText,
            String correctAnswer
    ) {

        boolean isCorrect = letter.equalsIgnoreCase(correctAnswer);

        Label label = new Label(
                letter + ")  "
                        + blankToPlaceholder(optionText, "—")
                        + (isCorrect ? "     ✓ correct" : "")
        );
        label.setWrapText(true);
        label.setStyle("-fx-font-size: 12px; -fx-text-fill: "
                + (isCorrect ? "-hsts-accent" : "-hsts-muted") + ";");
        return label;
    }

    /**
     * Clears the question form.
     */
    private void clearQuestionFields() {

        if (questionTable != null) {

            questionTable
                    .getSelectionModel()
                    .clearSelection();
        }

        clearQuestionFieldsSafely();

        if (questionStatusLabel != null) {
            questionStatusLabel.setText("");
        }
    }

    /**
     * Safely clears all question fields.
     */
    private void clearQuestionFieldsSafely() {

        if (courseField != null) {
            courseField.clear();
        }

        if (questionTextArea != null) {
            questionTextArea.clear();
        }

        if (answerAField != null) {
            answerAField.clear();
        }

        if (answerBField != null) {
            answerBField.clear();
        }

        if (answerCField != null) {
            answerCField.clear();
        }

        if (answerDField != null) {
            answerDField.clear();
        }

        if (correctAnswerComboBox != null) {
            correctAnswerComboBox.setValue(null);
        }

        questionImageFileName = null;
        if (questionImageLabel != null) {
            questionImageLabel.setText("No image");
        }
    }

    /**
     * Logs out the current user.
     */
    private void performLogout() {

        if (currentUser != null) {

            clientController.requestLogout(
                    currentUser
            );

        } else {

            clientController.disconnect();
        }

        currentUser = null;

        allQuestions.clear();
        displayedQuestions.clear();
        availableExams.clear();
        studentResults.clear();

        currentPage = 0;

        myCourses.clear();
        examInProgress = false;

        studentDashboardView = null;
        studentResultsView = null;
        botChatView = null;
        botEditorView = null;
        generateContentView = null;
        pendingGeneratedExam = null;
        takeExamView = null;
        buildExamView = null;
        examBankView = null;
        approveExamView = null;

        showLoginScreen();
    }

    /**
     * Displays a status message on the current screen.
     *
     * @param message message to display
     */
    private void showCurrentStatus(
            String message
    ) {

        String title =
                primaryStage.getTitle();

        if (title.contains("Approve Exams")
                && approveExamView != null) {

            approveExamView.setStatusMessage(message);
            return;
        }

        if (title.contains("Exam Bank")
                && examBankView != null) {

            examBankView.setStatusMessage(message);
            return;
        }

        if (title.contains("Users")
                && usersStatusLabel != null) {

            usersStatusLabel.setText(message);
            return;
        }

        if (title.contains("My Reports")
                && teacherReportsStatusLabel != null) {

            teacherReportsStatusLabel.setText(message);
            return;
        }

        if (title.contains("Question Bank")
                && questionStatusLabel != null) {

            questionStatusLabel.setText(message);
            return;
        }

        if (title.contains("Build Exam")
                && buildExamView != null) {

            buildExamView.setStatusMessage(message);
            return;
        }

        if (title.contains("Generate Content")
                && generateContentView != null) {

            generateContentView.setStatusMessage(message);
            return;
        }

        if (title.contains("Learning Bot Editor")
                && botEditorView != null) {

            botEditorView.setStatusMessage(message);
            return;
        }

        if (title.contains("Learning Bot")
                && botChatView != null) {

            botChatView.setStatusMessage(message);
            return;
        }

        if (title.contains("Student Dashboard")
                && studentDashboardView != null) {

            studentDashboardView.setStatusMessage(
                    message
            );

            return;
        }

        if (title.contains("Student Results")
                && studentResultsView != null) {

            studentResultsView.setStatusMessage(
                    message
            );

            return;
        }

        if (title.contains("Take Exam")
                && takeExamView != null) {

            takeExamView.setStatusMessage(
                    message
            );

            return;
        }

        if (loginStatusLabel != null) {
            loginStatusLabel.setText(message);
        }
    }

    /**
     * Returns the current user's display name.
     *
     * @return display name
     */
    private String getCurrentUserDisplayName() {

        if (currentUser.getFullName() != null
                && !currentUser
                .getFullName()
                .trim()
                .isEmpty()) {

            return currentUser.getFullName();
        }

        return currentUser.getUsername();
    }

    /**
     * Applies style.css to a scene.
     *
     * @param scene scene to style
     */
    private void applyStyleSheet(
            Scene scene
    ) {

        URL styleUrl =
                getClass().getResource("/style.css");

        if (styleUrl != null) {

            scene.getStylesheets().add(
                    styleUrl.toExternalForm()
            );

        } else {

            System.err.println(
                    "Could not find style.css"
            );
        }

        // Apply the current user's theme + accessibility preferences so every
        // screen looks consistent as soon as it is shown.
        applyPreferencesToScene(scene);
    }

    /**
     * Applies the current {@link #currentSettings} (theme, font scale, high
     * contrast) to a scene's root node. Safe to call with no settings loaded —
     * it simply falls back to the default Lilac theme.
     */
    private void applyPreferencesToScene(Scene scene) {
        if (scene == null || scene.getRoot() == null) {
            return;
        }
        javafx.scene.Parent root = scene.getRoot();

        // Theme: remove any previous theme class, add the current one.
        root.getStyleClass().removeAll("theme-lilac", "theme-light", "theme-dark",
                "theme-colorblind", "high-contrast");

        String theme = currentSettings != null ? currentSettings.getTheme() : UserSettings.THEME_LILAC;
        if (UserSettings.THEME_LIGHT.equals(theme)) {
            root.getStyleClass().add("theme-light");
        } else if (UserSettings.THEME_DARK.equals(theme)) {
            root.getStyleClass().add("theme-dark");
        } else if (UserSettings.THEME_COLORBLIND.equals(theme)) {
            // Keeps the lilac chrome and swaps only the colours that mean
            // something, so both classes go on.
            root.getStyleClass().addAll("theme-lilac", "theme-colorblind");
        } else {
            root.getStyleClass().add("theme-lilac");
        }

        // Accessibility: high contrast + font scale (root font size cascades to
        // any text that does not hard-code its own size).
        int scale = currentSettings != null ? currentSettings.getFontScale() : 100;
        boolean highContrast = currentSettings != null && currentSettings.isHighContrast();
        if (highContrast) {
            root.getStyleClass().add("high-contrast");
        }
        double basePx = 13.0 * (scale / 100.0);
        root.setStyle("-fx-font-size: " + String.format(java.util.Locale.US, "%.1f", basePx) + "px;");
    }

    /**
     * Requests the current user's saved settings from the server.
     */
    private void loadUserSettings() {
        try {
            clientController.requestUserSettings(currentUser.getUserId());
        } catch (IllegalArgumentException | IOException e) {
            System.err.println("Could not load user settings: " + e.getMessage());
        }
    }

    /**
     * Handles a settings object returned by the server (from either a load or a
     * save). Stores it and re-applies it to the scene currently on screen.
     */
    private void handleSettingsLoaded(UserSettings settings, String message) {
        currentSettings = settings;
        applyPreferencesToScene(primaryStage.getScene());
        if (settingsStatusLabel != null) {
            settingsStatusLabel.setText(message);
        }
    }

    // ══════════════════════════════════════════════════════════
    //  SHARED UI HELPERS
    // ══════════════════════════════════════════════════════════

    /**
     * Builds the sidebar VBox for any role.
     * activeIndex = index of the item that should appear highlighted.
     */
    private VBox buildSidebar(String[] labels, Runnable[] actions, int activeIndex) {

        Label logoLabel = new Label("⬢ HSTS");
        logoLabel.getStyleClass().add("sidebar-logo");

        Label logoSub = new Label("High School Test System");
        logoSub.getStyleClass().add("sidebar-subtitle");

        VBox sidebar = new VBox(4);
        sidebar.getStyleClass().add("sidebar");
        sidebar.getChildren().addAll(logoLabel, logoSub, new Region() {{ setMinHeight(12); }});

        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            Button btn = new Button(labels[i]);
            btn.getStyleClass().add(i == activeIndex ? "sidebar-item-active" : "sidebar-item");
            btn.setOnAction(e -> actions[idx].run());
            sidebar.getChildren().add(btn);
        }

        return sidebar;
    }

    /**
     * Single source of truth for the navigation sidebar of each role.
     * Every scene must obtain its sidebar from here so the navigation bar is
     * IDENTICAL (same items, same order, same targets) across all scenes of a
     * role. {@code activeLabel} is the item highlighted on the current scene.
     */
    private VBox buildRoleSidebar(String role, String activeLabel) {

        String[] labels;
        Runnable[] actions;

        if ("TEACHER".equalsIgnoreCase(role)) {
            labels  = new String[]{"Dashboard", "Question Bank", "Build Exam", "Exam Bank", "Grade Approval", "Learning Bot", "Generate", "Reports", "Settings", "Logout"};
            actions = new Runnable[]{
                    this::showTeacherDashboard,
                    this::showQuestionBankScreen,
                    this::showBuildExamScreen,
                    this::showExamBankScreen,
                    this::showGradeApprovalScreen,
                    this::showBotEditorScreen,
                    this::showGenerateContentScreen,
                    this::showTeacherReportsScreen,      // Reports
                    () -> showSettingsScreen("TEACHER"),
                    this::performLogout
            };
        } else if ("STUDENT".equalsIgnoreCase(role)) {
            labels  = new String[]{"Dashboard", "Grades", "Active Exams", "Review Exams", "Schedule", "AI Assistant", "Settings", "Logout"};
            actions = new Runnable[]{
                    this::showStudentDashboard,
                    this::showStudentResultsScreen,
                    this::showStudentActiveExamsScreen,  // Active Exams → full list of open exams
                    this::showStudentResultsScreen,      // Review Exams → results
                    this::showStudentScheduleScreen,
                    this::showBotChatScreen,             // AI Assistant → Learning Bot
                    () -> showSettingsScreen("STUDENT"),
                    this::performLogout
            };
        } else { // PRINCIPAL
            labels  = new String[]{"Dashboard", "Approve Exams", "Question Bank", "All Exams", "All Results", "Reports", "Users", "Settings", "Logout"};
            actions = new Runnable[]{
                    this::showPrincipalDashboard,
                    this::showApproveExamScreen,
                    this::showReadOnlyQuestionBankScreen,
                    this::showAllExamsScreen,
                    this::showAllResultsScreen,
                    this::showReportsDashboard,
                    this::showUserManagementScreen,
                    () -> showSettingsScreen("PRINCIPAL"),
                    this::performLogout
            };
        }

        int activeIndex = 0;
        for (int i = 0; i < labels.length; i++) {
            if (labels[i].equals(activeLabel)) {
                activeIndex = i;
                break;
            }
        }
        return buildSidebar(labels, actions, activeIndex);
    }

    /** Builds one stat card for dashboards. */
    private VBox buildStatCard(String label, String value, String sub) {
        return buildStatCard(label, new Label(value), sub);
    }

    /** Builds a stat card whose value label is stored externally for live updates. */
    private VBox buildStatCard(String label, Label valueLabel, String sub) {
        valueLabel.getStyleClass().add("stat-value");
        Label lbl  = new Label(label); lbl.getStyleClass().add("stat-label");
        Label subL = new Label(sub);   subL.getStyleClass().add("stat-sub");

        Label dot = new Label("●");
        dot.setStyle("-fx-font-size: 28px; -fx-text-fill: #DDD6FE;");

        HBox top = new HBox(10, dot, new VBox(2, lbl, valueLabel, subL));
        top.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(top);
        card.getStyleClass().add("stat-card");
        return card;
    }

    /**
     * Builds a coloured quick-action button with subtitle text.
     * styleClass must be one of: quick-btn-pink / sky / green / peach / purple
     */
    private Button buildQuickBtn(String title, String subtitle, String styleClass, Runnable action) {
        Button btn = new Button(title + "\n" + subtitle);
        btn.getStyleClass().add(styleClass);
        btn.setWrapText(true);
        btn.setPrefWidth(175);
        btn.setOnAction(e -> action.run());
        return btn;
    }

    /**
     * Draws a simple line-chart on a Canvas.
     * dataPoints should be values 0-100.
     */
    private Canvas buildLineChart(double[] dataPoints, String hexColor, double w, double h) {
        return buildLineChart(dataPoints, null, hexColor, w, h);
    }

    /**
     * Draws a simple line-chart on a Canvas, optionally with one axis label
     * printed under each data point. Handles any point count &gt;= 1 — a
     * single point is drawn as a lone dot (no line) instead of being dropped.
     * dataPoints should be values 0-100.
     *
     * @param dataPoints values to plot, 0-100
     * @param labels per-point x-axis labels (same length as dataPoints), or null for none
     */
    private Canvas buildLineChart(double[] dataPoints, String[] labels, String hexColor, double w, double h) {
        double labelAreaHeight = labels != null ? 16 : 0;
        Canvas canvas = new Canvas(w, h + labelAreaHeight);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        gc.setStroke(Color.web("#E9D5FF"));
        gc.setLineWidth(1);
        for (int i = 1; i <= 4; i++) {
            double y = h * i / 5.0;
            gc.strokeLine(0, y, w, y);
        }

        int n = dataPoints.length;
        double stepX = n > 1 ? w / (n - 1.0) : 0;

        if (n > 1) {
            gc.setStroke(Color.web(hexColor));
            gc.setLineWidth(2.5);
            for (int i = 0; i < n - 1; i++) {
                double x1 = i * stepX;
                double y1 = h - (dataPoints[i] / 100.0 * (h - 10)) - 5;
                double x2 = (i + 1) * stepX;
                double y2 = h - (dataPoints[i + 1] / 100.0 * (h - 10)) - 5;
                gc.strokeLine(x1, y1, x2, y2);
            }
        }

        gc.setFill(Color.web(hexColor));
        for (int i = 0; i < n; i++) {
            double x = n > 1 ? i * stepX : w / 2.0;
            double y = h - (dataPoints[i] / 100.0 * (h - 10)) - 5;
            gc.fillOval(x - 4, y - 4, 8, 8);
        }

        if (labels != null) {
            gc.setFill(Color.web("#6B7280"));
            gc.setFont(Font.font(10));
            gc.setTextAlign(TextAlignment.CENTER);
            for (int i = 0; i < n && i < labels.length; i++) {
                double x = n > 1 ? i * stepX : w / 2.0;
                gc.fillText(labels[i], x, h + 13);
            }
        }

        return canvas;
    }

    /**
     * Shortens a date string (e.g. "2026-08-06" or a full timestamp like
     * "2026-08-06 10:15:30.0") to a compact "MM-dd" label for chart axes.
     */
    private String formatShortDate(String rawDate) {
        if (rawDate == null || rawDate.length() < 10) {
            return rawDate == null ? "" : rawDate;
        }
        return rawDate.substring(5, 10);
    }

    /**
     * Draws a simple vertical bar chart on a Canvas: one bar per value,
     * auto-scaled to the largest value, with the value printed above each
     * bar and its category label printed below.
     *
     * @param labels category label per bar
     * @param values bar height per category (any positive scale)
     * @param hexColor bar fill color
     * @param w chart width
     * @param h chart height (excluding label margins)
     * @param asPercentage whether to format values as whole percentages
     */
    private Canvas buildBarChart(String[] labels, double[] values, String hexColor, double w, double h, boolean asPercentage) {
        double topMargin = 18;
        double bottomMargin = 34;
        Canvas canvas = new Canvas(w, h + topMargin + bottomMargin);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        int n = values.length;
        if (n == 0) {
            return canvas;
        }

        double max = 0;
        for (double v : values) {
            max = Math.max(max, v);
        }
        if (max <= 0) {
            max = 1;
        }

        double slot = w / n;
        double barWidth = Math.min(slot * 0.55, 60);

        gc.setFont(Font.font(10));
        gc.setTextAlign(TextAlignment.CENTER);

        for (int i = 0; i < n; i++) {
            double barHeight = (values[i] / max) * (h - 4);
            double x = i * slot + (slot - barWidth) / 2.0;
            double y = topMargin + h - barHeight;

            gc.setFill(Color.web(hexColor));
            gc.fillRoundRect(x, y, barWidth, barHeight, 4, 4);

            gc.setFill(Color.web("#374151"));
            String valueText = asPercentage
                    ? String.format("%.0f%%", values[i])
                    : String.valueOf(Math.round(values[i]));
            gc.fillText(valueText, x + barWidth / 2.0, y - 4);

            String label = labels[i] != null ? labels[i] : "";
            if (label.length() > 12) {
                label = label.substring(0, 11) + "…";
            }
            gc.fillText(label, x + barWidth / 2.0, topMargin + h + 14);
        }

        return canvas;
    }

    // ══════════════════════════════════════════════════════════
    //  SETTINGS SCREEN  (Page 11)
    // ══════════════════════════════════════════════════════════

    /** Shows the Settings Center for any role. */
    private void showSettingsScreen(String role) {

        Runnable backAction = "TEACHER".equals(role)   ? this::showTeacherDashboard
                            : "STUDENT".equals(role)   ? this::showStudentDashboard
                            :                            this::showPrincipalDashboard;

        VBox sidebar = buildRoleSidebar(role, "Settings");

        // Top bar
        TextField searchBox = new TextField();
        searchBox.setPromptText("Search anything...");
        searchBox.getStyleClass().add("search-field");
        HBox topBar = new HBox(searchBox);
        topBar.getStyleClass().add("top-bar");
        topBar.setAlignment(Pos.CENTER_LEFT);

        Label pageTitle = new Label("Settings Center");
        pageTitle.getStyleClass().add("welcome-label");

        // Dynamic detail area: clicking Theme / Accessibility swaps in an inline
        // editor panel here. Created before the buttons so their handlers can use it.
        settingsDetailArea = new VBox(14);
        settingsDetailArea.setAlignment(Pos.TOP_LEFT);

        // Settings buttons. Profile + Theme + Accessibility are functional.
        // Language and AI Assistant remain stubs (to be built later).
        Button profileBtn       = buildQuickBtn("Profile",       "view your account info",           "quick-btn-purple", () -> showProfileScreen(role));
        Button themeBtn         = buildQuickBtn("Theme",         "lilac, light or dark",             "quick-btn-pink",   () -> showThemePanel());
        Button languageBtn      = buildQuickBtn("Language",      "English / Hebrew / Arabic",         "quick-btn-sky",    () -> {});
        Button aiBtn            = buildQuickBtn("AI Assistant",  "study recommendations, insights",   "quick-btn-pink",   () -> {});
        Button accessibilityBtn = buildQuickBtn("Accessibility", "font size, contrast",               "quick-btn-sky",    () -> showAccessibilityPanel());
        Button backBtn          = buildQuickBtn("Back",          "return to dashboard",               "quick-btn-purple", backAction);

        HBox row1 = new HBox(14, profileBtn, themeBtn, languageBtn);
        HBox row2 = new HBox(14, aiBtn, accessibilityBtn, backBtn);
        row1.setAlignment(Pos.CENTER_LEFT);
        row2.setAlignment(Pos.CENTER_LEFT);

        // Default detail content: the Theme editor.
        showThemePanel();

        VBox mainContent = new VBox(16, topBar, pageTitle, row1, row2, settingsDetailArea);
        mainContent.setPadding(new Insets(0, 0, 0, 20));
        HBox.setHgrow(mainContent, Priority.ALWAYS);

        HBox root = new HBox(16, sidebar, mainContent);
        root.getStyleClass().add("login-background");
        root.setPadding(new Insets(22));

        Scene scene = new Scene(root, windowWidth(), windowHeight());
        applyStyleSheet(scene);
        primaryStage.setScene(scene);
        primaryStage.setTitle("HSTS - Settings");
        applyStandardWindowSize();
    }

    /** Returns the current settings, creating defaults if none are loaded yet. */
    private UserSettings ensureSettings() {
        if (currentSettings == null) {
            currentSettings = new UserSettings(
                    currentUser != null ? currentUser.getUserId() : 0,
                    UserSettings.THEME_LILAC, 100, false);
        }
        return currentSettings;
    }

    /** Persists settings: applies them immediately, then saves on the server. */
    private void saveSettings(UserSettings updated) {
        updated.setUserId(currentUser != null ? currentUser.getUserId() : 0);
        currentSettings = updated;
        applyPreferencesToScene(primaryStage.getScene());   // optimistic, instant feedback
        if (settingsStatusLabel != null) settingsStatusLabel.setText("Saving…");
        try {
            clientController.requestUpdateUserSettings(updated);
        } catch (IllegalArgumentException | IOException e) {
            if (settingsStatusLabel != null) settingsStatusLabel.setText("Could not save: " + e.getMessage());
        }
    }

    /** Inline Theme editor shown inside the Settings detail area. */
    private void showThemePanel() {
        if (settingsDetailArea == null) return;
        UserSettings s = ensureSettings();

        Label title = new Label("Theme");
        title.getStyleClass().add("section-title");
        Label hint = new Label("Choose the colour theme for the whole app.");
        hint.setStyle("-fx-font-size: 13px; -fx-text-fill: -hsts-muted;");

        ToggleGroup group = new ToggleGroup();
        RadioButton lilac = themeRadio("Lilac (default)", UserSettings.THEME_LILAC, group, s.getTheme());
        RadioButton light = themeRadio("Light",           UserSettings.THEME_LIGHT, group, s.getTheme());
        RadioButton dark  = themeRadio("Dark",            UserSettings.THEME_DARK,  group, s.getTheme());
        RadioButton blind = themeRadio("Colour-blind friendly",
                UserSettings.THEME_COLORBLIND, group, s.getTheme());
        if (group.getSelectedToggle() == null) lilac.setSelected(true);

        // Says what the option actually does. "Colour-blind friendly" on its own
        // suggests the whole app is recoloured; only the colours that mean
        // something are.
        Label blindHint = new Label(
                "Marks correct/wrong, active/inactive and approve/reject in blue and "
                        + "orange instead of green and red.");
        blindHint.setWrapText(true);
        blindHint.setStyle("-fx-font-size: 12px; -fx-text-fill: -hsts-muted;"
                + " -fx-padding: 0 0 0 24;");

        VBox options = new VBox(10, lilac, light, dark, blind, blindHint);

        settingsStatusLabel = new Label();
        settingsStatusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: -hsts-positive;");

        Button save = new Button("Save Theme");
        save.getStyleClass().add("primary-button");
        save.setOnAction(e -> {
            String chosen = (String) group.getSelectedToggle().getUserData();
            UserSettings updated = new UserSettings(s.getUserId(), chosen, s.getFontScale(), s.isHighContrast());
            saveSettings(updated);
        });

        VBox card = new VBox(14, title, hint, options, save, settingsStatusLabel);
        card.getStyleClass().add("section-card");
        card.setPrefWidth(420);

        settingsDetailArea.getChildren().setAll(card);
    }

    private RadioButton themeRadio(String label, String value, ToggleGroup group, String current) {
        RadioButton rb = new RadioButton(label);
        rb.setToggleGroup(group);
        rb.setUserData(value);
        if (value.equals(current)) rb.setSelected(true);
        return rb;
    }

    /** Inline Accessibility editor shown inside the Settings detail area. */
    private void showAccessibilityPanel() {
        if (settingsDetailArea == null) return;
        UserSettings s = ensureSettings();

        Label title = new Label("Accessibility");
        title.getStyleClass().add("section-title");

        Label fontLabel = new Label("Text size");
        fontLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: -hsts-accent;");
        ToggleGroup fontGroup = new ToggleGroup();
        RadioButton normal = fontRadio("Normal",      100, fontGroup, s.getFontScale());
        RadioButton large  = fontRadio("Large",       115, fontGroup, s.getFontScale());
        RadioButton xlarge = fontRadio("Extra large", 130, fontGroup, s.getFontScale());
        if (fontGroup.getSelectedToggle() == null) normal.setSelected(true);
        VBox fontOptions = new VBox(8, normal, large, xlarge);

        CheckBox contrast = new CheckBox("High-contrast mode");
        contrast.setSelected(s.isHighContrast());

        settingsStatusLabel = new Label();
        settingsStatusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: -hsts-positive;");

        Button save = new Button("Save Accessibility");
        save.getStyleClass().add("primary-button");
        save.setOnAction(e -> {
            int scale = (Integer) fontGroup.getSelectedToggle().getUserData();
            UserSettings updated = new UserSettings(s.getUserId(), s.getTheme(), scale, contrast.isSelected());
            saveSettings(updated);
        });

        VBox card = new VBox(14, title, fontLabel, fontOptions, contrast, save, settingsStatusLabel);
        card.getStyleClass().add("section-card");
        card.setPrefWidth(420);

        settingsDetailArea.getChildren().setAll(card);
    }

    private RadioButton fontRadio(String label, int value, ToggleGroup group, int current) {
        RadioButton rb = new RadioButton(label);
        rb.setToggleGroup(group);
        rb.setUserData(value);
        if (value == current) rb.setSelected(true);
        return rb;
    }

    // ══════════════════════════════════════════════════════════
    //  PROFILE SCREEN
    // ══════════════════════════════════════════════════════════

    private void showProfileScreen(String role) {

        // Sidebar — identical to every other scene of this role (Settings highlighted)
        VBox sidebar = buildRoleSidebar(role, "Settings");

        // Top bar
        TextField searchBox = new TextField();
        searchBox.setPromptText("Search anything...");
        searchBox.getStyleClass().add("search-field");
        HBox topBar = new HBox(searchBox);
        topBar.getStyleClass().add("top-bar");
        topBar.setAlignment(Pos.CENTER_LEFT);

        Label pageTitle = new Label("My Profile");
        pageTitle.getStyleClass().add("welcome-label");

        // Avatar — ImageView if avatarUrl resolves to an image, else initials circle
        javafx.scene.Node avatar;
        String avatarUrl = currentUser.getAvatarUrl();
        String resolvedAvatar = ImageResolver.resolve(avatarUrl);
        if (resolvedAvatar != null) {
            try {
                javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView(
                        new javafx.scene.image.Image(resolvedAvatar, 80, 80, true, true, true));
                iv.setFitWidth(80);
                iv.setFitHeight(80);
                iv.setStyle("-fx-background-radius: 40px;");
                avatar = iv;
            } catch (Exception ex) {
                avatar = buildInitialsAvatar();
            }
        } else {
            avatar = buildInitialsAvatar();
        }

        // Role display name
        String roleName;
        if ("TEACHER".equals(currentUser.getRole()))         roleName = "Teacher";
        else if ("PRINCIPAL".equals(currentUser.getRole()))  roleName = "Principal";
        else                                                  roleName = "Student";

        String displayName = (currentUser.getFullName() != null && !currentUser.getFullName().isBlank())
                ? currentUser.getFullName() : currentUser.getUsername();

        Label nameLabel = new Label(displayName);
        nameLabel.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: -hsts-strong;");

        Label usernameLabel = new Label("@" + currentUser.getUsername());
        usernameLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: -hsts-muted;");

        Label roleBadge = new Label(roleName);
        roleBadge.setStyle(
                "-fx-background-color: #EDE9FE; -fx-text-fill: #5B21B6; -fx-font-weight: bold;" +
                "-fx-font-size: 12px; -fx-background-radius: 12px; -fx-padding: 4px 14px;");

        VBox nameBox = new VBox(4, nameLabel, usernameLabel, roleBadge);
        nameBox.setAlignment(Pos.CENTER_LEFT);

        HBox avatarRow = new HBox(20, avatar, nameBox);
        avatarRow.setAlignment(Pos.CENTER_LEFT);

        // Editable fields
        Label nameFieldLabel = new Label("Full Name");
        nameFieldLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: -hsts-muted; -fx-min-width: 110px;");
        TextField nameField = new TextField(currentUser.getFullName() != null ? currentUser.getFullName() : "");
        nameField.setPromptText("Your full name");
        nameField.setPrefWidth(280);

        Label avatarFieldLabel = new Label("Avatar image");
        avatarFieldLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: -hsts-muted; -fx-min-width: 110px;");
        TextField avatarField = new TextField(avatarUrl != null ? avatarUrl : "");
        avatarField.setPromptText("file in images/ (e.g. me.png) or https://…");
        avatarField.setPrefWidth(280);

        HBox nameRow   = new HBox(12, nameFieldLabel,   nameField);
        HBox avatarRow2 = new HBox(12, avatarFieldLabel, avatarField);
        nameRow.setAlignment(Pos.CENTER_LEFT);
        avatarRow2.setAlignment(Pos.CENTER_LEFT);

        profileStatusLabel = new Label();
        profileStatusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: -hsts-positive;");

        Button saveBtn = new Button("Save Changes");
        saveBtn.getStyleClass().add("primary-button");
        saveBtn.setOnAction(e -> {
            String newName = nameField.getText().trim();
            if (newName.isEmpty()) {
                profileStatusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: -hsts-negative;");
                profileStatusLabel.setText("Full name cannot be empty.");
                return;
            }
            profileStatusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: -hsts-muted;");
            profileStatusLabel.setText("Saving…");
            User updated = new User(currentUser.getUserId(), currentUser.getUsername(),
                    currentUser.getPassword(), newName, currentUser.getRole());
            updated.setAvatarUrl(avatarField.getText().trim());
            try {
                clientController.requestUpdateProfile(updated);
            } catch (java.io.IOException ex) {
                profileStatusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: -hsts-negative;");
                profileStatusLabel.setText("Could not save: " + ex.getMessage());
            }
        });

        Button backBtn = new Button("Back to Settings");
        backBtn.getStyleClass().add("secondary-button");
        backBtn.setOnAction(e -> showSettingsScreen(role));

        HBox buttonRow = new HBox(12, saveBtn, backBtn);
        buttonRow.setAlignment(Pos.CENTER_LEFT);

        Region divider = buildProfileDivider();

        VBox profileCard = new VBox(16);
        profileCard.getChildren().addAll(
                avatarRow,
                divider,
                buildProfileInfoRow("Username", currentUser.getUsername()),
                buildProfileInfoRow("Role",     roleName),
                buildProfileInfoRow("User ID",  "#" + currentUser.getUserId()));

        // The ID number is what a student types at the exam entry gate beside
        // the 4-digit code, and it is issued out of band -- so without this row
        // there is nowhere in the app to look it up, and the gate refuses with
        // "The ID you entered does not match our records" either way. Staff
        // never pass the gate, so the row would only be noise on their profile.
        if ("STUDENT".equals(currentUser.getRole())) {
            profileCard.getChildren().add(buildProfileInfoRow(
                    "ID number (ת\"ז)",
                    blankToPlaceholder(currentUser.getNationalId(),
                            "Not set - ask your principal to add one")));
        }

        profileCard.getChildren().addAll(
                buildProfileDivider(),
                nameRow,
                avatarRow2,
                buttonRow,
                profileStatusLabel);
        profileCard.getStyleClass().add("section-card");
        profileCard.setMaxWidth(600);

        VBox mainContent = new VBox(16, topBar, pageTitle, profileCard);
        mainContent.setPadding(new Insets(0, 0, 0, 20));
        HBox.setHgrow(mainContent, Priority.ALWAYS);

        HBox root = new HBox(16, sidebar, mainContent);
        root.getStyleClass().add("login-background");
        root.setPadding(new Insets(22));

        Scene scene = new Scene(root, windowWidth(), windowHeight());
        applyStyleSheet(scene);
        primaryStage.setScene(scene);
        primaryStage.setTitle("HSTS - Profile");
        applyStandardWindowSize();
    }

    // ══════════════════════════════════════════════════════════
    //  EXAM-LIST SCREENS  (student — Active Exams / Schedule)
    // ══════════════════════════════════════════════════════════

    /** Active Exams: the full list of exams the student can take right now. */
    private void showStudentActiveExamsScreen() {
        showExamListScreen("Active Exams", "Active Exams",
                "Exams that are open for you to take right now.", "HSTS - Active Exams");
    }

    /** Schedule: same list, framed as the student's upcoming exams. */
    private void showStudentScheduleScreen() {
        showExamListScreen("Schedule", "Exam Schedule",
                "Your upcoming exams — start any exam that is currently open.", "HSTS - Schedule");
    }

    /**
     * Shared builder for the student exam-list screens. Both Active Exams and
     * Schedule show the same available-exams data with a Start button per exam.
     */
    private void showExamListScreen(String activeLabel, String pageTitleText,
                                    String subtitleText, String sceneTitle) {

        VBox sidebar = buildRoleSidebar("STUDENT", activeLabel);

        // Top bar
        TextField searchBox = new TextField();
        searchBox.setPromptText("Search anything...");
        searchBox.getStyleClass().add("search-field");
        HBox topBar = new HBox(searchBox);
        topBar.getStyleClass().add("top-bar");
        topBar.setAlignment(Pos.CENTER_LEFT);

        Label pageTitle = new Label(pageTitleText);
        pageTitle.getStyleClass().add("welcome-label");

        Label subtitle = new Label(subtitleText);
        subtitle.setStyle("-fx-font-size: 13px; -fx-text-fill: -hsts-muted;");

        examListStatusLabel = new Label("Loading exams…");
        examListStatusLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: -hsts-muted;");

        examListBox = new VBox(12);
        examListBox.getChildren().add(examListStatusLabel);

        javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(examListBox);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox card = new VBox(14, pageTitle, subtitle, scroll);
        card.getStyleClass().add("section-card");
        VBox.setVgrow(card, Priority.ALWAYS);

        VBox mainContent = new VBox(16, topBar, card);
        mainContent.setPadding(new Insets(0, 0, 0, 20));
        HBox.setHgrow(mainContent, Priority.ALWAYS);

        HBox root = new HBox(16, sidebar, mainContent);
        root.getStyleClass().add("login-background");
        root.setPadding(new Insets(22));

        Scene scene = new Scene(root, windowWidth(), windowHeight());
        applyStyleSheet(scene);
        primaryStage.setScene(scene);
        primaryStage.setTitle(sceneTitle);
        applyStandardWindowSize();

        // If exams were already cached, show them immediately; always refresh from server.
        if (!availableExams.isEmpty()) {
            populateExamList(availableExams);
        }
        loadAvailableExams();
    }

    /** Rebuilds the exam list (Active Exams / Schedule) from the given exams. */
    private void populateExamList(List<Exam> exams) {

        if (examListBox == null) {
            return;
        }

        examListBox.getChildren().clear();

        if (exams == null || exams.isEmpty()) {
            Label empty = new Label("No exams are open for you right now. You're all caught up! 🎉");
            empty.setStyle("-fx-font-size: 14px; -fx-text-fill: -hsts-muted;");
            examListBox.getChildren().add(empty);
            return;
        }

        for (Exam exam : exams) {
            examListBox.getChildren().add(buildExamRow(exam));
        }
    }

    /** Builds a single exam row for the exam-list screens. */
    private HBox buildExamRow(Exam exam) {

        Label icon = new Label("📝");
        icon.setStyle("-fx-font-size: 24px;");

        String courseName = exam.getCourse() != null ? exam.getCourse() : "—";
        int questions = exam.getQuestionCount() > 0 ? exam.getQuestionCount() : exam.getExamQuestions().size();

        Label title = new Label(exam.getTitle() != null ? exam.getTitle() : "Untitled Exam");
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: -hsts-strong;");

        Label details = new Label(courseName + "  •  " + exam.getDurationMinutes()
                + " min  •  " + questions + " question" + (questions == 1 ? "" : "s"));
        details.setStyle("-fx-font-size: 12px; -fx-text-fill: -hsts-muted;");

        VBox info = new VBox(3, title, details);
        info.setAlignment(Pos.CENTER_LEFT);

        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);

        Button startBtn = new Button("Start Exam");
        startBtn.getStyleClass().add("primary-button");
        startBtn.setOnAction(e -> startSelectedExam(exam));

        HBox row = new HBox(14, icon, info, grow, startBtn);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-background-color: -hsts-row; -fx-background-radius: 12px; -fx-padding: 14px 18px;");
        return row;
    }

    private javafx.scene.Node buildInitialsAvatar() {
        String initials = getProfileInitials(currentUser);
        Label lbl = new Label(initials);
        lbl.setStyle(
                "-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: white;" +
                "-fx-background-color: #7C3AED; -fx-background-radius: 40px;" +
                "-fx-min-width: 80px; -fx-min-height: 80px;" +
                "-fx-max-width: 80px; -fx-max-height: 80px;" +
                "-fx-alignment: center;");
        return lbl;
    }

    private String getProfileInitials(User user) {
        String name = user.getFullName();
        if (name != null && !name.isBlank()) {
            String[] parts = name.trim().split("\\s+");
            if (parts.length >= 2) {
                return ("" + parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
            }
            return String.valueOf(parts[0].charAt(0)).toUpperCase();
        }
        return String.valueOf(user.getUsername().charAt(0)).toUpperCase();
    }

    private Region buildProfileDivider() {
        Region div = new Region();
        div.setPrefHeight(1);
        div.setStyle("-fx-background-color: #E9D5FF;");
        return div;
    }

    private HBox buildProfileInfoRow(String label, String value) {
        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-size: 13px; -fx-text-fill: -hsts-muted; -fx-min-width: 110px;");
        Label val = new Label(value);
        val.setStyle("-fx-font-size: 13px; -fx-text-fill: -hsts-strong; -fx-font-weight: bold;");
        HBox row = new HBox(12, lbl, val);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /**
     * Main entry point.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
}
