# Architecture

Design notes for HSTS. The [README](../README.md) covers what the system does and how to
run it; this document covers how it is put together and why.

---

## Modules

| Module | Contains | Depends on |
|---|---|---|
| `common` | Serializable wire types: `Request`, `Response`, `RequestType`, and every model that crosses the socket | nothing |
| `server` | Accept loop, request routing, DAOs, bot engines, config | `common`, JDBC, BCrypt, Gson |
| `client` | JavaFX screens, the network layer, a scripted protocol demo | `common`, JavaFX |

`common` declares **no dependencies at all**, and that is a constraint rather than an
accident. Everything in it is serialized and sent over a socket, so it must not drag JavaFX
into the server or JDBC into the client. Every class implements `Serializable` with
`serialVersionUID = 1L`; both sides load the same compiled classes, so a field added on one
side cannot silently break deserialization on the other.

---

## The protocol

Plain Java object serialization over a TCP socket on port **5555**.

```
Request  { String action, Object data }
Response { boolean success, String message, Object data, String action }
```

`action` is a constant from `RequestType`. The client sends one; the server echoes it back
on the response.

### Response action stamping

The echoed action is set in exactly one place — immediately before the response is written,
in the handler loop — rather than by each individual request handler. That uniformity is
what makes client dispatch reliable: no handler can forget to stamp its reply.

### Client dispatch

The client switches on the echoed action. An earlier design switched on the *runtime type*
of `response.getData()` and disambiguated collisions by inspecting the window title, which
broke in two predictable ways: an empty list has no element to type-check, and two
different actions can return the same type. Title checks survive only *inside* a handler,
to choose between two legitimate targets for the same action, or to stop a stale response
from overwriting a screen the user has already left.

Every `RequestType` needs a matching case; anything unmatched falls through to a default
that surfaces the server's message.

---

## Server

```
ServerMain
  ├─ verifies database connectivity
  ├─ creates and repairs every table
  └─ TestifyServer(5555).listen()
        └─ accept loop on a background thread
              └─ one handler thread per connected socket
                    ├─ LOGIN / LOGOUT handled inline (they mutate session state)
                    └─ everything else → handleMessageFromClient(request, sessionUserId)
                          └─ switch on action → DAOs → MySQL
```

`TestifyServer` is a plain `ServerSocket` loop rather than a framework server. The client
uses OCSF's `AbstractClient`; the server side of that framework is not used.

### Session identity

Each socket's handler holds the user ID established at login, or `-1` when nobody is logged
in. Handlers that must know who is asking take that value as a parameter and authorize from
it. **An ID inside a request payload is never used for a permission decision** — the client
controls it. A single-argument overload exists for actions that need no session; it passes
`-1`, and anything requiring a session refuses under it.

### One session per account

Logged-in user IDs live in a concurrent set. A second login for an ID already present is
refused. The ID is released on logout *and* in the handler's `finally` block, so a crashed
client does not lock the account out permanently.

---

## The one unsolicited message

Everything is strict request → response, with one deliberate exception: a teacher extending
an exam that is **already running**. Students are mid-exam, and the client does not poll, so
the server has to speak first.

```
teacher's handler thread
      │  EXTEND_EXAM_DURATION
      │  persists the new duration (and moves the close time to match)
      ▼
  pushExamExtension(examId, extraMinutes)
      │  looks up the watcher registry for that exam
      ▼
  for each registered handler → write to ANOTHER thread's socket
      ▼
  student's listening thread → the countdown grows
```

Three properties make this safe, and all three matter:

**The watcher registry** maps exam ID to the set of handlers currently sitting it. A handler
joins when an exam start *succeeds* and leaves on submit, on logout, and in the handler's
`finally` block — the same place the session is released, so a crashed client is dropped
too. Removal empties the set and drops the map entry in one atomic step.

**Socket writes are synchronized**, and both the reply path and the push path go through the
same synchronized method. Two threads writing one `ObjectOutputStream` corrupts it
permanently. That method also resets the stream after every write; without the reset, the
stream's back-reference cache re-sends a handle to a previously written object instead of
its current state.

**A dead socket cannot cost other students their time.** The push catches the I/O failure
per client, drops that one from the registry, and carries on.

The extension is persisted *before* it is pushed, so a student who enters after the grant
gets the longer duration from the entry gate; the push exists only for those already
sitting. One consequence worth knowing: a client silently reconnected after a dropped
socket is no longer in the registry and would miss a later push.

---

## Exam and submission lifecycle

### Approval

```
CREATE_EXAM  →  PENDING  →  principal approves  →  APPROVED  →  schedulable
                         ↘  principal rejects   →  REJECTED + reason shown to the author
```

Editing an approved exam archives the previous header and returns it to `PENDING`. An exam
that has real submissions cannot be edited or deleted at all.

### Entry

The gate validates, in order, each with its own message: the code resolves to an exam; the
exam is approved and active; the clock is inside the scheduling window; the national ID
matches the logged-in account; the student is enrolled in the course.

### Submission rows open at the start

The row is created when the gate is passed, not when the paper is handed in. Three
consequences, all intended:

- the recorded start time is a real start time rather than a copy of the submit time;
- the server can tell who is mid-exam, which is what the bot lockout relies on;
- a submit with no open row is **refused** — the gate is where enrolment, the window and
  the ID are checked, so a client that skipped it cannot record a submission.

Re-entering reuses the existing pending row rather than opening a second one, so a student
who starts, drops out and returns ends with one row. The "has submissions" check that
freezes an exam against editing ignores pending rows, so an abandoned attempt that produced
no answers does not freeze the paper.

### Grading

```
submit → score computed in Java and written to both the final and original score columns,
         status AWAITING_APPROVAL, and the receipt carries NO number
   ↓
teacher's queue
   ├─ approve  → status GRADED, approver recorded
   └─ override → new score + written justification, status GRADED,
                 original computed score left untouched
   ↓
student sees it (their results query filters to GRADED) and can open the marked paper
```

The score is computed in Java rather than SQL to avoid a self-join, and an override
deliberately preserves the computed value so the two are never conflated later.

---

## Data access

### One shared connection, except for transactions

Every DAO is handed the same `Connection`. That rules out running a transaction on it:
disabling auto-commit is connection-wide, so an unrelated thread's statements would join
the transaction and a rollback would discard them. The two operations that must be atomic —
archiving a question version, and rewriting an exam with its question set — therefore open
their own dedicated connection and close it. Anything added later that needs atomicity must
do the same.

This is also why bulk validation happens up front rather than inside the write loop: with no
usable transaction, the only way to avoid a partial write is to check everything before
writing anything.

### Schema created and repaired at startup

Each DAO owns its tables' `CREATE TABLE IF NOT EXISTS`, run in dependency order at boot.
Columns added after a table's original definition are applied as idempotent `ALTER TABLE`
statements that swallow the duplicate-column error, so they are safe to run on every start
and an existing database upgrades itself.

`db/schema.sql` inlines all of that into the base table definitions for a clean rebuild. If
the script and the DAOs ever disagree, **the DAOs are the source of truth.**

Check constraints whose allowed values have changed are dropped and recreated at startup
rather than left as they were, because `CREATE TABLE IF NOT EXISTS` leaves an existing
table's constraints alone — a database created before a new theme or status existed would
otherwise reject it.

---

## The learning bot

```
student question
      │  four checks, in order, all before any network call:
      │  is a student → bot exists and is available → enrolled in the course
      │  → NOT currently in an exam
      ▼
  engine selection
      ├─ API key configured and the call succeeds  → remote answer
      └─ no key, or that call threw                → local retrieval
      ▼
  the exchange is logged with which engine answered it
```

**The engine is chosen per call.** Configuration decides whether the remote engine is tried
at all; failure falls back for that one call only, because a dropped connection is not a
permanent verdict. The engine used is recorded per exchange, so the history is honest, and
the UI labels every answer with its source — a keyword match against the teacher's text is
not a generated answer and must not read like one.

**Grounding is the feature.** The system prompt presents the teacher's sources as the only
permitted material and instructs the model to say plainly when they do not cover the
question. Without that, the sources would be decoration.

**Privacy is a hard rule.** Only the question text and the teacher's material leave the
machine. No student name, username, national ID or user ID is ever placed in a request —
the prompt builder has no access to any of them.

**The local engine cannot generate.** It tokenizes the question, drops stopwords, splits
each source into paragraphs and scores them by inverse-document-frequency-weighted term
overlap, returning the best paragraph above a threshold. Generation asks the server whether
it is available and disables itself with the reason on screen rather than failing on click.

**Generated content is always a draft.** Generated questions and exams come back unsaved
with ID 0. A generated exam goes through the same principal approval as any other; there is
no exception for generated content. Saving one is two steps by necessity — the questions
must be persisted before the exam can reference them by foreign key.

### Course membership

Teacher-side bot actions authorize against a dedicated course-teacher table. The author of
one exam is not the same question as "may this teacher edit this course's material": any
teacher of a course may, not only whoever created a given exam.

### Anonymised history

The teacher's view of student questions does not select the student ID and does not join
the users table, so identity never leaves the database. Fetching the column and hiding it in
the UI would still ship it over the wire and leave it in the client's memory.

---

## Client

`ClientApplication` owns the screens and session state; larger screens live in their own
view classes. The network layer is two classes: a controller that validates input, builds
requests and remembers the connection, and a thin OCSF subclass that owns the socket.

Responses arrive on the socket's listening thread and are handed to the UI through the
JavaFX application thread — every handler is wrapped, so a view updated by an unsolicited
push is as safe as one updated by a reply.

The controller transparently rebuilds a dropped socket, which works because every request
except login and logout is self-contained. The trade-off is documented in the README's
limitations: the rebuilt socket has no session until the user logs in again.

### Theming

Semantic colours are defined once as looked-up colours on the scene root, and every colour
that *carries meaning* — correct/incorrect, active/inactive, approve/reject — refers to
them by name, including the inline styles set from Java. Themes redefine those names.

The colour-blind theme is applied *alongside* the base theme, because it changes only the
meaningful colours rather than the chrome; it swaps the red/green pair for a blue/orange
pair that stays distinguishable under the common forms of colour blindness. A literal
colour written anywhere stops following the theme, which is the thing to watch for when
adding a screen. Colour is never the sole signal in any case: glyphs and words carry the
same information.

---

## Demo driver

`client/src/main/java/com/testify/demo` is a protocol-level driver that walks the system's
requirements over real sockets against a running server. It does not drive the UI.

It spawns **one client per user in its own JVM**, because claims about independent sessions
are demonstrated rather than asserted by a single process holding several. Each child
accepts a host and port, so any of them can be started by hand on a second machine for the
same demonstration over a real network.

The driver makes the controller synchronous, which is only possible because responses carry
the action they answer: anything whose action does not match the pending expectation goes to
a separate queue, so an unsolicited push can never be mistaken for the reply a step is
waiting on. Each scene re-derives its own state, which is what lets a run resume from the
middle instead of only from the beginning.
