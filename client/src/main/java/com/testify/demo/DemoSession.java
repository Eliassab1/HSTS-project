package com.testify.demo;

import com.testify.client.control.ClientController;
import com.testify.common.RequestType;
import com.testify.common.Response;
import com.testify.common.User;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * One user's session, made synchronous.
 *
 * {@link ClientController} is asynchronous by design: you send, and a
 * {@code Consumer<Response>} fires later on the OCSF reader thread. A scripted
 * demo needs the opposite shape — send, wait, assert — so this wraps it.
 *
 * <b>This is only possible because {@code Response} carries the originating
 * action.</b> The server stamps every reply with the action it answers, so a
 * reply can be matched to the request that caused it. Without that field the
 * correlation would be guesswork, and the whole driver would rest on assuming
 * the next message to arrive is the one you asked for.
 *
 * <b>Unsolicited pushes are the reason that assumption would be wrong.</b>
 * {@code EXAM_DURATION_EXTENDED} arrives with no request behind it, at a moment
 * the server chooses. If it were handed to whatever call is currently waiting,
 * that call would return someone else's message and the requirement-7 scene
 * would pass for entirely the wrong reason. So any response whose action does
 * not match the pending expectation goes to a separate queue that scenes drain
 * deliberately via {@link #awaitPush(String, int)}.
 */
public final class DemoSession {

    /** How long a single request may take before the step is reported failed. */
    private final int timeoutSeconds;

    private final ClientController controller = new ClientController();

    /** Responses that answered nothing this session asked for. */
    private final LinkedBlockingQueue<Response> pushes = new LinkedBlockingQueue<>();

    /** The request currently in flight, or null. */
    private final AtomicReference<Pending> pending = new AtomicReference<>();

    /**
     * Requests actually put on the wire by this session.
     *
     * Requirement 18 (no proactive refreshing) can only be shown by absence, so
     * something has to be counting.
     */
    private final AtomicInteger requestsSent = new AtomicInteger();

    private String host;
    private int port;
    private User user;

    public DemoSession(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    /** A call to a controller method that may refuse before anything is sent. */
    @FunctionalInterface
    public interface Call {
        void run() throws IOException;
    }

    private static final class Pending {
        private final String expectedAction;
        private final CountDownLatch latch = new CountDownLatch(1);
        private final AtomicReference<Response> result = new AtomicReference<>();

        private Pending(String expectedAction) {
            this.expectedAction = expectedAction;
        }
    }

    // ── Connection ────────────────────────────────────────────────────────

    public void connect(String host, int port) throws IOException {
        this.host = host;
        this.port = port;
        controller.connect(host, port, this::onResponse);
    }

    /**
     * Drops the socket and opens a new one.
     *
     * Used by the exam-lockout scene. A reconnect deliberately does NOT restore
     * the server-side session — the new socket's handler starts with no logged
     * in user — so the caller must log in again before asserting anything that
     * authorises from the session.
     */
    public void reconnect() throws IOException {
        controller.disconnect();

        // disconnect() returns before OCSF's reader thread has finished tearing
        // the socket down, and connect() short-circuits when it still looks
        // connected -- leaving the caller holding the half-closed client, whose
        // next send fails with "socket does not exist". So wait for it to be
        // genuinely down before opening the replacement.
        for (int attempt = 0; attempt < 50 && controller.isConnected(); attempt++) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        pushes.clear();
        pending.set(null);
        controller.connect(host, port, this::onResponse);
    }

    public void disconnect() {
        controller.disconnect();
    }

    // ── The correlation ───────────────────────────────────────────────────

    private void onResponse(Response response) {

        Pending waiting = pending.get();
        String action = response.getAction();

        if (waiting != null && action != null && action.equals(waiting.expectedAction)) {
            waiting.result.set(response);
            waiting.latch.countDown();
            return;
        }

        // Either an unsolicited push, or a late reply to a call that already
        // timed out. Both belong in the queue, and neither may be mistaken for
        // the answer to whatever is waiting now.
        pushes.offer(response);
    }

    /**
     * Sends a request and blocks until its reply arrives.
     *
     * @param expectedAction the {@link RequestType} the reply will be stamped with
     * @param call           the controller method to invoke
     * @return the reply, or a synthesised failure on timeout or client-side refusal
     */
    public Response send(String expectedAction, Call call) {

        Pending waiting = new Pending(expectedAction);
        pending.set(waiting);

        try {
            call.run();
            requestsSent.incrementAndGet();
        } catch (IllegalArgumentException refusal) {
            // The client validated it and never sent it. That is still a
            // refusal and still evidence -- but it is the CLIENT's refusal, and
            // the log says so rather than implying the server was consulted.
            pending.set(null);
            return synthesise(expectedAction, "refused by client-side validation: "
                    + refusal.getMessage());
        } catch (IOException failure) {
            pending.set(null);
            return synthesise(expectedAction, "could not send: " + failure.getMessage());
        }

        try {
            if (!waiting.latch.await(timeoutSeconds, TimeUnit.SECONDS)) {
                pending.set(null);
                return synthesise(expectedAction, "TIMEOUT after " + timeoutSeconds + "s");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            pending.set(null);
            return synthesise(expectedAction, "interrupted");
        }

        pending.set(null);
        return waiting.result.get();
    }

    /**
     * Waits for a server message that answers no request of ours.
     *
     * Drains anything already queued first: the push may well have arrived
     * before the scene got round to asking for it, and a demo that only looked
     * forward in time would miss it and report a failure that never happened.
     *
     * @param action  the action to wait for
     * @param seconds how long to wait
     * @return the push, or null if none arrived in time
     */
    public Response awaitPush(String action, int seconds) {

        long deadline = System.currentTimeMillis() + (seconds * 1000L);
        List<Response> unmatched = new ArrayList<>();
        Response found = null;

        try {
            while (System.currentTimeMillis() < deadline) {
                long remaining = deadline - System.currentTimeMillis();
                Response response = pushes.poll(remaining, TimeUnit.MILLISECONDS);
                if (response == null) {
                    break;
                }
                if (action.equals(response.getAction())) {
                    found = response;
                    break;
                }
                unmatched.add(response);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }

        // Put back anything we looked at and did not want, so a later scene
        // waiting on a different action can still find it.
        for (Response response : unmatched) {
            pushes.offer(response);
        }
        return found;
    }

    // ── Session identity ──────────────────────────────────────────────────

    /**
     * Logs in and remembers the user, so later requests can quote their id.
     *
     * @return the reply, successful or not — a refused login is itself evidence
     *         (requirement 16) and must not be thrown away
     */
    public Response login(String username, String password) {
        Response response = send(RequestType.LOGIN,
                () -> controller.requestLogin(username, password));
        if (response.isSuccess() && response.getData() instanceof User loggedIn) {
            this.user = loggedIn;
        }
        return response;
    }

    public User getUser() {
        return user;
    }

    public int getUserId() {
        return user == null ? -1 : user.getUserId();
    }

    public ClientController controller() {
        return controller;
    }

    public int getRequestsSent() {
        return requestsSent.get();
    }

    private static Response synthesise(String action, String message) {
        Response response = new Response(false, message, null);
        response.setAction(action);
        return response;
    }
}
