package com.testify.common;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Talks to the Anthropic Messages API over plain HTTP.
 *
 * <b>Only the server ever constructs this.</b> The key lives in the server's
 * environment, the server is the only machine on the LAN that needs internet
 * access, and every client reaches Claude only by asking the server to.
 *
 * The transport is {@link java.net.http.HttpClient}, built into Java 17 — no
 * HTTP dependency is added. Gson is the one new library, and it is used for
 * both directions: hand-built JSON would break the first time a teacher put a
 * quotation mark in a source.
 */
public class ClaudeBotEngine implements BotEngine {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";

    /** Pinned API version, as the Messages API requires on every request. */
    private static final String API_VERSION = "2023-06-01";

    /**
     * Small, fast and cheap — the right tier for short grounded answers and
     * short JSON drafts. Override with {@code ANTHROPIC_MODEL} to use a more
     * capable model. Note the ID carries no date suffix.
     */
    private static final String DEFAULT_MODEL = "claude-haiku-4-5";

    private static final int ANSWER_MAX_TOKENS = 1024;
    private static final int GENERATION_MAX_TOKENS = 4096;

    /**
     * Both timeouts are set deliberately. A connect timeout alone still lets
     * a server that accepts the socket and then stalls hold a
     * {@code ClientHandler} thread open indefinitely, and that thread is one
     * student's whole session.
     */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    /** Cap on how much source material is sent, in characters. */
    private static final int MAX_SOURCE_CHARS = 60_000;

    private final String apiKey;
    private final String model;
    private final HttpClient http;

    public ClaudeBotEngine() {
        // EnvConfig so a key in the workspace's .env works, which is what
        // the setup instructions tell you to do. A real environment variable
        // still wins over the file.
        String key = EnvConfig.get("ANTHROPIC_API_KEY");
        this.apiKey = key == null ? null : key.trim();
        String configured = EnvConfig.get("ANTHROPIC_MODEL");
        this.model = (configured == null || configured.isBlank())
                ? DEFAULT_MODEL
                : configured.trim();
        this.http = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    @Override
    public String name() {
        return ENGINE_CLAUDE;
    }

    /** No key, no engine — this is what makes the fallback take over. */
    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** The model this engine will call, for the server's status reporting. */
    public String getModel() {
        return model;
    }

    // ── ANSWERING ─────────────────────────────────────────────────────────

    /**
     * Answers a student's question from the teacher's material alone.
     *
     * The grounding lives in the system prompt: the sources are presented as
     * the only permitted material, and the model is told to say plainly when
     * they do not cover the question rather than fill the gap from general
     * knowledge. Without that instruction the bot would happily answer
     * anything, and the teacher's sources would be decoration.
     *
     * <b>Privacy:</b> the question text and the teacher's material are the
     * only things that leave this machine. No student name, username,
     * national ID or user ID is ever put in the request — see
     * {@link #buildAnswerSystemPrompt}, which has no access to any of them.
     */
    @Override
    public String answerQuestion(String question, List<BotSource> sources) throws Exception {
        String system = buildAnswerSystemPrompt(sources);
        String text = callClaude(system, question, ANSWER_MAX_TOKENS);
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("Claude returned an empty answer.");
        }
        return text.trim();
    }

    /** The grounding instruction plus the teacher's material, and nothing else. */
    private String buildAnswerSystemPrompt(List<BotSource> sources) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a study assistant for one high-school course. ")
              .append("A student is asking you a question about the course.\n\n")
              .append("Answer ONLY from the course material given below. It is the only ")
              .append("material you are permitted to use.\n")
              .append("- If the material does not cover the question, say so plainly and ")
              .append("suggest the student ask their teacher. Do not answer from general ")
              .append("knowledge and do not guess.\n")
              .append("- Never invent facts, figures, definitions or citations.\n")
              .append("- Explain in a way a high-school student can follow, and keep it short.\n")
              .append("- Help the student understand the topic. Do not do their homework or ")
              .append("exam for them.\n\n")
              .append("=== COURSE MATERIAL ===\n");

        if (sources == null || sources.isEmpty()) {
            prompt.append("(The teacher has not added any material yet. ")
                  .append("Tell the student you have nothing to answer from.)\n");
            return prompt.toString();
        }

        int budget = MAX_SOURCE_CHARS;
        for (BotSource source : sources) {
            String content = source.getContent() == null ? "" : source.getContent();
            if (content.length() > budget) {
                content = content.substring(0, Math.max(0, budget));
            }
            budget -= content.length();
            prompt.append("\n--- ").append(source.getTitle()).append(" ---\n")
                  .append(content).append('\n');
            if (budget <= 0) break;
        }
        return prompt.toString();
    }

    // ── GENERATING ────────────────────────────────────────────────────────

    /**
     * Drafts multiple-choice questions as JSON.
     *
     * The model is asked for a bare JSON array. When the reply will not parse
     * it is retried ONCE with a stricter instruction, and then the failure is
     * reported to the teacher in words they can act on. A partially parsed
     * batch is never returned: half a draft set silently missing its last two
     * questions is worse than a clear failure.
     */
    @Override
    public List<Question> generateQuestions(String topic, String difficulty, int count,
                                            int courseId, List<BotSource> sources)
            throws Exception {

        String system = buildGenerationSystemPrompt(sources);
        String user = buildGenerationUserPrompt(topic, difficulty, count);

        String raw = callClaude(system, user, GENERATION_MAX_TOKENS);
        List<Question> drafts = tryParse(raw, courseId, topic, difficulty);

        if (drafts == null) {
            String stricter = user + "\n\nYour previous reply could not be parsed. "
                    + "Reply with the JSON array ONLY: no explanation, no preamble, "
                    + "no markdown code fence. The first character must be '[' and the "
                    + "last must be ']'.";
            raw = callClaude(system, stricter, GENERATION_MAX_TOKENS);
            drafts = tryParse(raw, courseId, topic, difficulty);
        }

        if (drafts == null || drafts.isEmpty()) {
            throw new IllegalStateException(
                    "Claude did not return usable questions. Try a narrower topic, "
                            + "or ask for fewer questions.");
        }
        return drafts;
    }

    /** Generation grounding: use the material when there is any, stay on the course. */
    private String buildGenerationSystemPrompt(List<BotSource> sources) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You write multiple-choice exam questions for a high-school course.\n")
              .append("Rules:\n")
              .append("- Exactly four options, A to D. Exactly one is correct.\n")
              .append("- The wrong options must be plausible, not obviously silly.\n")
              .append("- No trick questions, no ambiguity, no 'all of the above'.\n")
              .append("- Each question must stand on its own without referring to the others.\n");

        if (sources != null && !sources.isEmpty()) {
            prompt.append("\nBase the questions on this course material where it is relevant:\n")
                  .append("=== COURSE MATERIAL ===\n");
            int budget = MAX_SOURCE_CHARS;
            for (BotSource source : sources) {
                String content = source.getContent() == null ? "" : source.getContent();
                if (content.length() > budget) {
                    content = content.substring(0, Math.max(0, budget));
                }
                budget -= content.length();
                prompt.append("\n--- ").append(source.getTitle()).append(" ---\n")
                      .append(content).append('\n');
                if (budget <= 0) break;
            }
        }
        return prompt.toString();
    }

    /** The shape contract. Kept in the user turn so the retry can tighten it. */
    private String buildGenerationUserPrompt(String topic, String difficulty, int count) {
        return "Write " + count + " multiple-choice question(s) at " + difficulty
                + " difficulty on the topic: " + topic + ".\n\n"
                + "Reply with ONLY a JSON array. No prose, no markdown code fence. "
                + "Each element must have exactly these keys:\n"
                + "{\"questionText\": \"...\", \"optionA\": \"...\", \"optionB\": \"...\", "
                + "\"optionC\": \"...\", \"optionD\": \"...\", "
                + "\"correctAnswer\": \"A\"|\"B\"|\"C\"|\"D\", "
                + "\"topic\": \"...\", \"difficulty\": \"EASY\"|\"MEDIUM\"|\"HARD\"}";
    }

    /**
     * Parses a reply into drafts, or returns null when it cannot.
     *
     * Returning null rather than throwing is what lets the caller retry once
     * before giving up. Anything that parses but is not a usable question —
     * a missing option, a correct answer outside A-D — fails the whole batch
     * rather than being quietly dropped.
     *
     * @return the drafts, or null when the reply was not usable JSON
     */
    private List<Question> tryParse(String raw, int courseId,
                                    String requestedTopic, String requestedDifficulty) {
        if (raw == null || raw.isBlank()) return null;

        // Models sometimes wrap JSON in a fence despite being asked not to;
        // trimming to the outermost brackets is cheaper than a failed retry.
        String json = raw.trim();
        int start = json.indexOf('[');
        int end = json.lastIndexOf(']');
        if (start == -1 || end == -1 || end <= start) return null;
        json = json.substring(start, end + 1);

        JsonArray array;
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonArray()) return null;
            array = parsed.getAsJsonArray();
        } catch (JsonSyntaxException e) {
            return null;
        }

        List<Question> drafts = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) return null;
            JsonObject o = element.getAsJsonObject();

            String text = optString(o, "questionText");
            String a = optString(o, "optionA");
            String b = optString(o, "optionB");
            String c = optString(o, "optionC");
            String d = optString(o, "optionD");
            String correct = InputValidator.answerLetter(optString(o, "correctAnswer"));

            if (text == null || a == null || b == null || c == null || d == null
                    || correct == null) {
                return null;
            }

            Question q = new Question();
            q.setId(0);                 // a draft has no identity until it is saved
            q.setCourseId(courseId);
            q.setQuestionText(text);
            q.setOptionA(a);
            q.setOptionB(b);
            q.setOptionC(c);
            q.setOptionD(d);
            q.setCorrectAnswer(correct);

            String topic = optString(o, "topic");
            q.setTopic(topic == null ? requestedTopic : topic);
            q.setDifficultyLevel(InputValidator.oneOf(
                    optString(o, "difficulty"),
                    requestedDifficulty,
                    GenerationRequest.DIFFICULTY_EASY,
                    GenerationRequest.DIFFICULTY_MEDIUM,
                    GenerationRequest.DIFFICULTY_HARD));

            drafts.add(q);
        }
        return drafts;
    }

    /** A non-blank string member, or null. */
    private String optString(JsonObject o, String key) {
        if (!o.has(key) || o.get(key).isJsonNull()) return null;
        try {
            String value = o.get(key).getAsString().trim();
            return value.isEmpty() ? null : value;
        } catch (UnsupportedOperationException | IllegalStateException e) {
            return null;
        }
    }

    // ── TRANSPORT ─────────────────────────────────────────────────────────

    /**
     * One request/response round trip against the Messages API.
     *
     * Wire shape, for anyone maintaining this without the docs open:
     * <pre>
     *   POST https://api.anthropic.com/v1/messages
     *   x-api-key: &lt;key&gt;
     *   anthropic-version: 2023-06-01
     *   content-type: application/json
     *
     *   {"model":…, "max_tokens":…, "system":…,
     *    "messages":[{"role":"user","content":…}]}
     *
     *   → {"content":[{"type":"text","text":"…"}], "stop_reason":"end_turn", …}
     * </pre>
     *
     * @return the first text block's text
     * @throws Exception on a non-200 status, a malformed body, or a timeout
     */
    private String callClaude(String system, String userMessage, int maxTokens)
            throws Exception {

        if (!isAvailable()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY is not set on the server.");
        }

        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", userMessage);

        JsonArray messages = new JsonArray();
        messages.add(message);

        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("max_tokens", maxTokens);
        body.addProperty("system", system);
        body.add("messages", messages);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .timeout(REQUEST_TIMEOUT)
                .header("x-api-key", apiKey)
                .header("anthropic-version", API_VERSION)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(),
                        StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response =
                http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                    "Anthropic API returned HTTP " + response.statusCode() + ": "
                            + describeError(response.body()));
        }

        JsonObject parsed = JsonParser.parseString(response.body()).getAsJsonObject();

        // A safety refusal is an HTTP 200 with stop_reason "refusal", not an
        // error status — checking the status alone would read it as success
        // and then find no text to return.
        if (parsed.has("stop_reason") && !parsed.get("stop_reason").isJsonNull()
                && "refusal".equals(parsed.get("stop_reason").getAsString())) {
            throw new IllegalStateException("Claude declined to answer that request.");
        }

        if (!parsed.has("content") || !parsed.get("content").isJsonArray()) {
            throw new IllegalStateException("Unexpected response shape from the Anthropic API.");
        }
        JsonArray content = parsed.getAsJsonArray("content");
        for (JsonElement block : content) {
            if (!block.isJsonObject()) continue;
            JsonObject o = block.getAsJsonObject();
            if (o.has("type") && "text".equals(o.get("type").getAsString()) && o.has("text")) {
                return o.get("text").getAsString();
            }
        }
        throw new IllegalStateException("The Anthropic API returned no text content.");
    }

    /** Pulls the API's own error message out of a failure body, if it has one. */
    private String describeError(String body) {
        if (body == null || body.isBlank()) return "(no body)";
        try {
            JsonObject parsed = JsonParser.parseString(body).getAsJsonObject();
            if (parsed.has("error") && parsed.get("error").isJsonObject()) {
                JsonObject error = parsed.getAsJsonObject("error");
                if (error.has("message")) {
                    return error.get("message").getAsString();
                }
            }
        } catch (RuntimeException ignored) {
            // fall through to the raw body
        }
        return body.length() > 300 ? body.substring(0, 300) + "…" : body;
    }
}
