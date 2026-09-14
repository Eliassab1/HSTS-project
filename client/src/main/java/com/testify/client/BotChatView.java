package com.testify.client;

import com.testify.common.BotAsk;
import com.testify.common.BotQuestion;
import com.testify.common.CourseBot;
import com.testify.common.User;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Consumer;

/**
 * Student - Learning Bot chat (spec 14).
 *
 * Like every other view here it talks to nobody: it takes its actions as
 * callbacks and receives results through its {@code setXxx} methods.
 *
 * Two things this screen is careful about:
 * <ul>
 *   <li><b>It says which engine answered.</b> A reply from the offline
 *       retrieval fallback is a keyword match against the teacher's material,
 *       not a generated answer, and presenting the two identically would be
 *       misleading in exactly the situation where the student can least
 *       afford it.</li>
 *   <li><b>It never claims to enforce the exam lockout.</b> The entry point
 *       greying out on the dashboard is a courtesy; the server refuses
 *       ASK_BOT mid-exam and remains the only thing actually enforcing it.
 *       When a refusal arrives, the server's own wording is shown rather than
 *       a generic error, because the server distinguishes four different
 *       reasons and the student needs to know which one applies.</li>
 * </ul>
 */
public class BotChatView extends HBox {

    private final Consumer<BotAsk> askAction;
    private final Consumer<CourseBot> historyAction;
    private final Runnable refreshAction;
    private final Runnable backAction;

    private ComboBox<CourseBot> botPicker;
    private VBox conversation;
    private ScrollPane conversationScroll;
    private TextArea questionInput;
    private Button sendButton;
    private ProgressIndicator waitingSpinner;
    private Label statusLabel;
    private VBox historyList;
    private Label historyStatus;

    /** True between sending a question and the reply (or refusal) arriving. */
    private boolean awaitingAnswer;

    public BotChatView(
            User student,
            VBox sidebar,
            Consumer<BotAsk> askAction,
            Consumer<CourseBot> historyAction,
            Runnable refreshAction,
            Runnable backAction
    ) {
        if (askAction == null)     throw new IllegalArgumentException("Ask action cannot be null.");
        if (historyAction == null) throw new IllegalArgumentException("History action cannot be null.");
        if (refreshAction == null) throw new IllegalArgumentException("Refresh action cannot be null.");
        if (backAction == null)    throw new IllegalArgumentException("Back action cannot be null.");

        this.askAction     = askAction;
        this.historyAction = historyAction;
        this.refreshAction = refreshAction;
        this.backAction    = backAction;

        buildUI(student, sidebar);
    }

    // -- Build ----------------------------------------------------------------

    private void buildUI(User student, VBox sidebar) {
        getStyleClass().add("login-background");
        setPadding(new Insets(22));
        setSpacing(16);

        VBox main = buildMain(student);
        HBox.setHgrow(main, Priority.ALWAYS);
        getChildren().addAll(sidebar, main);
    }

    private VBox buildMain(User student) {

        String name = student != null && student.getFullName() != null
                && !student.getFullName().isBlank()
                ? student.getFullName()
                : (student != null ? student.getUsername() : "Student");

        Label title = new Label("Learning Bot - " + name);
        title.getStyleClass().add("page-title");

        Label hint = new Label(
                "Ask about your course material. The bot answers from what your teacher "
                        + "has provided, and will say so when a topic is not covered.");
        hint.setWrapText(true);
        hint.setStyle("-fx-text-fill: -hsts-muted;");

        botPicker = new ComboBox<>();
        botPicker.setPrefWidth(320);
        botPicker.setPromptText("Choose a course");
        botPicker.setOnAction(e -> {
            CourseBot selected = botPicker.getSelectionModel().getSelectedItem();
            if (selected != null) {
                clearConversation();
                setStatusMessage("Ask " + selected.getBotName() + " a question.");
                historyAction.accept(selected);
            }
        });

        Button refreshBtn = new Button("Refresh");
        refreshBtn.getStyleClass().add("secondary-button");
        refreshBtn.setOnAction(e -> {
            setStatusMessage("Loading your learning bots...");
            refreshAction.run();
        });

        Button backBtn = new Button("Back to Dashboard");
        backBtn.getStyleClass().add("secondary-button");
        backBtn.setOnAction(e -> backAction.run());

        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);

        HBox pickerRow = new HBox(12, new Label("Course:"), botPicker,
                refreshBtn, topSpacer, backBtn);
        pickerRow.setAlignment(Pos.CENTER_LEFT);

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(
                new Tab("Chat", buildChatPane()),
                new Tab("My questions", buildHistoryPane()));
        VBox.setVgrow(tabs, Priority.ALWAYS);

        statusLabel = new Label();
        statusLabel.getStyleClass().add("status-label");
        statusLabel.setWrapText(true);

        return new VBox(14, title, hint, pickerRow, tabs, statusLabel);
    }

    private VBox buildChatPane() {

        conversation = new VBox(10);
        conversation.setPadding(new Insets(14));

        conversationScroll = new ScrollPane(conversation);
        conversationScroll.setFitToWidth(true);
        conversationScroll.getStyleClass().add("section-card");
        VBox.setVgrow(conversationScroll, Priority.ALWAYS);

        questionInput = new TextArea();
        questionInput.setPromptText("Type your question...");
        questionInput.setPrefRowCount(3);
        questionInput.setWrapText(true);
        HBox.setHgrow(questionInput, Priority.ALWAYS);

        sendButton = new Button("Send");
        sendButton.getStyleClass().add("primary-button");
        sendButton.setPrefWidth(120);
        sendButton.setOnAction(e -> send());

        waitingSpinner = new ProgressIndicator();
        waitingSpinner.setPrefSize(26, 26);
        waitingSpinner.setVisible(false);
        waitingSpinner.setManaged(false);

        VBox sendColumn = new VBox(8, sendButton, waitingSpinner);
        sendColumn.setAlignment(Pos.CENTER);

        HBox inputRow = new HBox(12, questionInput, sendColumn);
        inputRow.setAlignment(Pos.CENTER_LEFT);

        VBox pane = new VBox(12, conversationScroll, inputRow);
        pane.setPadding(new Insets(14));
        clearConversation();
        return pane;
    }

    private VBox buildHistoryPane() {

        historyStatus = new Label("Choose a course to see the questions you have asked.");
        historyStatus.setWrapText(true);
        historyStatus.setStyle("-fx-text-fill: -hsts-muted;");

        historyList = new VBox(10);
        historyList.setPadding(new Insets(14));

        ScrollPane scroll = new ScrollPane(historyList);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("section-card");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox pane = new VBox(12, historyStatus, scroll);
        pane.setPadding(new Insets(14));
        return pane;
    }

    // -- Sending --------------------------------------------------------------

    private void send() {

        if (awaitingAnswer) {
            return;
        }

        CourseBot bot = botPicker.getSelectionModel().getSelectedItem();
        if (bot == null) {
            setStatusMessage("Choose a course first.");
            return;
        }

        String question = questionInput.getText() == null ? "" : questionInput.getText().trim();
        if (question.isBlank()) {
            setStatusMessage("Type a question first.");
            return;
        }

        addBubble("You", question, null, true);
        questionInput.clear();
        setWaiting(true);
        setStatusMessage("Asking " + bot.getBotName() + "...");

        askAction.accept(new BotAsk(bot.getId(), question));
    }

    /**
     * Disables Send and shows a spinner while a question is in flight.
     *
     * A Claude call takes a few seconds. Without this the window looks frozen,
     * and an impatient second click would ask the same question twice.
     */
    private void setWaiting(boolean waiting) {
        awaitingAnswer = waiting;
        sendButton.setDisable(waiting);
        questionInput.setDisable(waiting);
        waitingSpinner.setVisible(waiting);
        waitingSpinner.setManaged(waiting);
    }

    // -- Bubbles --------------------------------------------------------------

    /**
     * Appends one message to the conversation.
     *
     * @param who speaker label
     * @param text the message
     * @param engineNote engine attribution, or null for the student's own turn
     * @param fromStudent true to align the bubble right
     */
    private void addBubble(String who, String text, String engineNote, boolean fromStudent) {

        if (emptyPlaceholderShowing) {
            conversation.getChildren().clear();
            emptyPlaceholderShowing = false;
        }

        Label speaker = new Label(who);
        speaker.setStyle("-fx-font-weight: bold; -fx-text-fill: -hsts-accent;");

        Label body = new Label(text);
        body.setWrapText(true);
        body.setStyle("-fx-text-fill: -hsts-strong;");

        VBox bubble = new VBox(4, speaker, body);
        bubble.getStyleClass().add("section-card");
        bubble.setMaxWidth(640);

        if (engineNote != null) {
            Label note = new Label(engineNote);
            note.setWrapText(true);
            note.setStyle("-fx-font-size: 11px; -fx-text-fill: -hsts-muted;");
            bubble.getChildren().add(note);
        }

        HBox row = new HBox(bubble);
        row.setAlignment(fromStudent ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        conversation.getChildren().add(row);

        // Show the newest message rather than leaving the reader at the top.
        conversationScroll.setVvalue(1.0);
    }

    /** True while the conversation shows only its "no messages yet" placeholder. */
    private boolean emptyPlaceholderShowing;

    private void clearConversation() {
        if (conversation == null) return;
        conversation.getChildren().clear();
        Label empty = new Label("No messages yet.");
        empty.setStyle("-fx-text-fill: -hsts-muted;");
        conversation.getChildren().add(empty);
        emptyPlaceholderShowing = true;
    }

    /**
     * How an answer is attributed on screen.
     *
     * The offline wording is deliberately plain. "Answered from course
     * material (offline mode)" tells the student they are reading their
     * teacher's own text, which is true, useful, and not the same claim as
     * "the AI answered this".
     */
    private String describeEngine(BotQuestion exchange) {
        if (exchange == null) return null;
        return exchange.isFromLocalEngine()
                ? "answered from course material (offline mode)"
                : "answered by Claude";
    }

    // -- Public API -----------------------------------------------------------

    /**
     * Fills the course picker.
     *
     * @param bots available bots for courses the student is enrolled in
     */
    public void setBots(List<CourseBot> bots) {

        CourseBot previous = botPicker.getSelectionModel().getSelectedItem();
        botPicker.getItems().setAll(bots == null ? List.<CourseBot>of() : bots);

        if (botPicker.getItems().isEmpty()) {
            setStatusMessage("No learning bot is available for your courses yet - "
                    + "your teachers create them.");
            return;
        }

        // Keep the student on the course they were reading, if it survived.
        int keep = 0;
        if (previous != null) {
            for (int i = 0; i < botPicker.getItems().size(); i++) {
                if (botPicker.getItems().get(i).getId() == previous.getId()) {
                    keep = i;
                    break;
                }
            }
        }
        botPicker.getSelectionModel().select(keep);
        setStatusMessage("Showing " + botPicker.getItems().size() + " bot(s) you can use.");
    }

    /**
     * Appends an answer that has come back from the server.
     *
     * @param exchange the logged question and answer
     */
    public void addExchange(BotQuestion exchange) {
        setWaiting(false);
        if (exchange == null) {
            setStatusMessage("The bot returned nothing.");
            return;
        }
        addBubble("Bot", exchange.getAnswerText(), describeEngine(exchange), false);
        setStatusMessage("Answered.");
    }

    /**
     * Re-enables the input after a refusal, showing the server's own reason.
     *
     * The server distinguishes "not enrolled", "bot unavailable" and "you are
     * in an exam"; flattening those into one generic error would leave the
     * student guessing which applies.
     *
     * @param message the server's message
     */
    public void failPending(String message) {
        setWaiting(false);
        addBubble("Bot", message == null ? "The request was refused." : message, null, false);
        setStatusMessage(message == null ? "" : message);
    }

    /**
     * Fills the "My questions" tab.
     *
     * @param history this student's own exchanges with the selected bot
     */
    public void setHistory(List<BotQuestion> history) {

        historyList.getChildren().clear();

        if (history == null || history.isEmpty()) {
            historyStatus.setText("You have not asked this bot anything yet.");
            return;
        }

        historyStatus.setText("Showing your " + history.size() + " most recent question(s).");
        for (BotQuestion exchange : history) {
            Label when = new Label(exchange.getAskedAt());
            when.setStyle("-fx-font-size: 11px; -fx-text-fill: -hsts-muted;");

            Label question = new Label("Q: " + exchange.getQuestionText());
            question.setWrapText(true);
            question.setStyle("-fx-font-weight: bold; -fx-text-fill: -hsts-strong;");

            Label answer = new Label("A: " + exchange.getAnswerText());
            answer.setWrapText(true);
            answer.setStyle("-fx-text-fill: -hsts-strong;");

            Label engine = new Label(describeEngine(exchange));
            engine.setStyle("-fx-font-size: 11px; -fx-text-fill: -hsts-muted;");

            VBox card = new VBox(4, when, question, answer, engine);
            card.getStyleClass().add("section-card");
            historyList.getChildren().add(card);
        }
    }

    /** The bot the student is currently talking to, or null. */
    public CourseBot getSelectedBot() {
        return botPicker == null ? null : botPicker.getSelectionModel().getSelectedItem();
    }

    public void setStatusMessage(String message) {
        if (statusLabel != null) statusLabel.setText(message == null ? "" : message);
    }
}
