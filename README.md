# HSTS — High School Test System

A client/server exam management system for high schools. Teachers author questions and build exams, a principal approves and schedules them, students sit them under a timed entry gate, and results flow back through a teacher approval step before anyone sees a grade.

Java 17 · JavaFX 21 · MySQL 8 · Maven multi-module · plain TCP sockets with Java object serialization.

---

## Contents

- [What it does](#what-it-does)
- [Features by role](#features-by-role)
- [How it behaves](#how-it-behaves)
- [Repository layout](#repository-layout)
- [Getting started](#getting-started)
- [Configuration — the .env file](#configuration--the-env-file)
- [Demo accounts](#demo-accounts)
- [Security notes](#security-notes)
- [Known limitations](#known-limitations)
- [About this repository](#about-this-repository)
- [Third-party code](#third-party-code)

---

## What it does

Three roles share one desktop client, each seeing a different application:

| Role | What they do |
|---|---|
| **Teacher** | Write and version questions, build exams that must total exactly 100 points, schedule and extend them, approve or override computed grades, curate a course learning bot |
| **Student** | Enter an exam with a 4-digit code and national ID, answer under a live countdown, review the marked paper afterwards, ask the course bot questions |
| **Principal** | Approve or reject submitted exams with a reason, manage user accounts, and read system-wide reports |

The server is the authority for every rule. The client renders and validates for convenience; nothing it sends is trusted.

---

## Features by role

### Teacher

- **Question bank** with pagination, search, per-question difficulty and topic, and an optional image attached to a question as a visual aid.
- **Question versioning** — editing a question archives the previous version first, and any prior version can be read back from the UI.
- **Duplicate question** — copies an existing question into a new one with a fresh code, so a variant does not have to be retyped.
- **Exam builder and editor** — browse the bank, add questions, assign points. An exam is refused unless its points total exactly 100 and its duration is positive. Editing an exam archives the old header and returns the exam to `PENDING` for re-approval.
- **An exam with submissions cannot be edited or deleted.** Abandoned attempts that never produced answers do not count, so one student walking away does not freeze the paper.
- **Scheduling** — an approved exam gets an open/close window and a 4-digit entry code.
- **Extend a running exam** — grants 1–120 extra minutes, which reaches students who are *already sitting it* (see [How it behaves](#how-it-behaves)).
- **Grade approval queue** — computer-marked scores are held until the teacher releases them, either approving the computed score unchanged or overriding it with a written justification. The original computed score is kept on record either way.
- **Per-exam results** — a table and a histogram filtered by grade level, with count, average, median, range and pass rate.
- **Learning bot editor** — name the course bot, toggle its availability, and maintain the source material it is allowed to answer from. Sources are shared between all teachers of a course and each shows who last edited it.
- **AI-assisted drafting** — generate draft questions or a whole draft exam by topic, difficulty and count. Every draft is editable and unsaved until explicitly kept.

### Student

- **Exam entry gate** — a 4-digit code plus the student's own national ID. The server re-checks that the exam exists, is approved and active, that the clock is inside the scheduling window, that the ID matches the logged-in account, and that the student is enrolled in the course. Each failure has its own message.
- **Timed exam** — one question at a time, answers saved as you navigate, a live countdown, and an automatic submit at zero.
- **Results** — only grades a teacher has released are visible.
- **Marked paper review** — every question with the four options, the correct answer, the student's own choice, and whether the points were awarded.
- **Learning bot** — a per-course chat grounded in the teacher's uploaded material, with a personal question history. Every answer is labelled with the engine that produced it.
- **The bot is locked during an exam**, enforced server-side.

### Principal

- **Exam approval queue** — approve, or reject with a reason that reaches the author.
- **User management** — create, edit and delete accounts across all three roles.
- **System-wide listings** — every exam across all teachers, every released result across all students.
- **Reports dashboard** — pass/fail by course, score trend over time, teacher activity, grade distribution histogram, and comparison statistics by teacher, course or student.

### Everyone

- **Themes and accessibility** — Lilac, Light, Dark and a colour-blind-safe theme, plus a font scale and a high-contrast switch, stored per user. The colour-blind theme swaps only the colours that *carry meaning*, and colour is never the sole signal: correct/incorrect and active/inactive are always also words or glyphs.
- **Profile** — display name and avatar.

---

## How it behaves

### Request/response over sockets

```
JavaFX UI  ->  ClientController  ->  TestifyClient (TCP :5555)
                                          |  Java object serialization
                                          v
                              TestifyServer accept loop
                                          |  one handler thread per socket
                                          v
                              handleMessageFromClient(Request, sessionUserId)
                                          |
                                          v
                                  DAOs  ->  MySQL (JDBC)
```

Every exchange is strict request → response: a handler writes only as a direct reply to a request it just read. Responses echo the action they answer, and the client dispatches on that echoed action rather than guessing from the payload's type — two different actions can return the same type, and an empty list has no type to inspect at all.

### The one message the server sends unprompted

A teacher extending a running exam is the single exception, and it has to be: students are already mid-exam, and the client does not poll.

```
teacher extends  ->  duration persisted FIRST  ->  push to watchers of that exam
                                                       |
                                                       v
                                     every student currently sitting it
                                                       |
                                                       v
                                        the live countdown grows
```

Three things make that safe. Writes to a socket are synchronized, because two threads writing one `ObjectOutputStream` corrupts it permanently. The stream is reset after every write, or its back-reference cache re-sends stale state instead of current values. And a dead socket is dropped individually, so one crashed client cannot cost the rest of the class their extra minutes.

The extension is persisted *before* it is pushed, so a student who starts late gets the longer duration from the entry gate rather than from the push.

### Authorization comes from the session, never the payload

Handlers that must know *who is asking* read the user ID established at login for that socket. An ID inside a request is data a client controls, so it is never used to decide permission. Anything requiring a session refuses when there isn't one.

### Grading lifecycle

```
student submits
    -> score computed and stored, status AWAITING_APPROVAL, student sees no number
    -> teacher approves unchanged, or overrides with a justification
    -> status GRADED, and only now is it visible to the student
```

An override records the new score and the reason while leaving the computed score on record, so the two are never confused afterwards.

### Submissions open at the start, not the end

The submission row is created when a student passes the entry gate. That makes the recorded start time real, lets the server know who is mid-exam, and means a submit with no open row is refused rather than silently granted one — the entry gate is where enrolment, the window and the ID are checked, so it cannot be skipped.

### The learning bot degrades instead of failing

With an API key configured the bot answers through the Anthropic API, grounded strictly in the teacher's uploaded sources and instructed to say plainly when they don't cover the question. With no key — or if that one call fails — it falls back to local keyword retrieval over the same sources: no network, no dependency.

The fallback is per call, since a dropped connection is not a permanent verdict, and each exchange records which engine answered it so the history stays honest. Only the question text and the teacher's material ever leave the machine; no student name, ID or username is ever included in a request.

---

## Repository layout

```
HSTS-project/
├── pom.xml            aggregator; builds all three modules
├── common/            wire types shared by both sides — no dependencies at all
├── server/            TCP server, request routing, DAOs, bot engines
├── client/            JavaFX application
├── db/
│   ├── schema.sql     full DDL for every table
│   └── seed.sql       demo data: users, courses, questions, exams, results
└── docs/
    └── ARCHITECTURE.md   deeper design notes
```

`common/` exists so the serialized model classes have exactly one definition. Both sides must agree byte-for-byte on these classes or deserialization fails at runtime, which is exactly the drift a shared module prevents. Nothing in `common/` may depend on JavaFX or JDBC — it is wire types only, and the module declares no dependencies at all to keep it that way.

---

## Getting started

### Prerequisites

- JDK 17 or newer
- Maven 3.8+
- MySQL 8 with a reachable account

### 1. Create the database

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS hsts_db;"
mysql -u root -p hsts_db < db/schema.sql
mysql -u root -p hsts_db < db/seed.sql
```

The server also creates and repairs every table itself at startup, so `schema.sql` is mainly for a clean rebuild. `seed.sql` is what gives you something to log into.

### 2. Configure

```bash
cp .env.example .env
```

Then edit `.env` — see the next section.

### 3. Build

```bash
mvn clean package
```

Produces `server/target/hsts-server.jar` and `client/target/hsts-client.jar`, both self-contained.

To run the test suite on its own:

```bash
mvn test
```

58 tests covering the grade banding, summary statistics, result models and the
request-boundary input validation. They need no database and no display.

### 4. Run

```bash
java -jar server/target/hsts-server.jar     # listens on port 5555
java -jar client/target/hsts-client.jar     # start one per user
```

The server prints the configuration file it loaded and which bot engine it started on.

Run as many clients as you like, on one machine or across a LAN. The login screen has host and port fields; on another machine, point them at the server's IP. Those fields are pre-filled from `-Dhsts.host=` / `-Dhsts.port=`, then `HSTS_HOST` / `HSTS_PORT`, then `localhost:5555`:

```bash
java -Dhsts.host=192.168.1.20 -jar client/target/hsts-client.jar
```

> Leaving the default `localhost` on a second machine means "connect to myself", which shows up as a connection refused before a packet ever leaves the client.

---

## Configuration — the .env file

Create `.env` in the repository root by copying `.env.example`. It is **gitignored and must stay that way** — it holds a database password and an API key.

Only the **server** reads it. It is resolved by `EnvConfig`, which looks in the working directory and up to three parent directories, so running the server from the repo root or from `server/` both work. **A real environment variable always beats a value in the file**, which is how you override one setting for one run without editing anything.

The client never reads it and never needs a key.

| Variable | Required | Default | Purpose |
|---|---|---|---|
| `HSTS_DB_URL` | no | `jdbc:mysql://localhost:3306/hsts_db` | JDBC URL of the database |
| `HSTS_DB_USER` | no | `root` | Database user |
| `HSTS_DB_PASS` | no | `123` | Database password |
| `ANTHROPIC_API_KEY` | no | *(unset)* | Enables AI answers and question generation |
| `ANTHROPIC_MODEL` | no | `claude-haiku-4-5` | Model override |

A complete example:

```dotenv
# -- Database ------------------------------------------------------------
HSTS_DB_URL=jdbc:mysql://localhost:3306/hsts_db
HSTS_DB_USER=root
HSTS_DB_PASS=your_mysql_password

# -- Learning bot (optional) ---------------------------------------------
ANTHROPIC_API_KEY=sk-ant-...
# ANTHROPIC_MODEL=claude-haiku-4-5
```

**The database values have working defaults**, so if your MySQL really is `root` with password `123` on localhost, the application runs with no `.env` at all. Set them the moment that isn't true.

**The API key is genuinely optional.** Leave it empty and the system runs in offline mode: the learning bot still answers students from the teacher's uploaded course material using local retrieval, and the question-generation screen disables itself with the reason shown on screen rather than failing when clicked. Everything else — exams, grading, reports — is unaffected. Running at least once without a key is a good way to see that path.

---

## Demo accounts

All seeded accounts use the password `password123`.

| Username | Role |
|---|---|
| `student1` … `student6` | Student |
| `teacher1` … `teacher6` | Teacher |
| `admin1`, `admin2` | Principal |

### Sitting a seeded exam

The entry gate needs a scheduled exam's 4-digit code **and the student's own national ID**. Neither is shown anywhere in the student UI by design — the code comes from the teacher, the ID is issued out of band.

| Exam | Code |
|---|---|
| Mathematics Mid-Term | `1101` |
| Physics Mid-Term | `2201` |
| Computer Science Mid-Term | `3301` |

National IDs are `200000001` … `200000006` for `student1` … `student6` respectively.

The three exams above are seeded to open and close **on the day you run the seed**, so they are enterable immediately. Re-run `seed.sql` to move them to another day.

> An account created by hand through the principal's user screen has no national ID unless one is typed in, and can never pass the gate until it does.

---

## Security notes

- **Passwords are BCrypt-hashed.** A legacy plaintext comparison remains for rows seeded before hashing was introduced; see [Known limitations](#known-limitations).
- **Password hashes are never selected into a listing.** The user-management query omits the column entirely rather than fetching it and hiding it in the UI.
- **All SQL goes through prepared statements**, with input sanitization — control-character stripping, length caps, enum whitelisting — as a second layer, not the primary defense.
- **Authorization is derived from the socket's authenticated session**, never from an ID in a request payload.
- **One session per account.** A second login for a user already online is refused, and the hold is released when the socket closes, so a crash cannot lock an account out permanently.
- **Teacher-side bot actions verify course membership**, and an edit resolves the owning course from the stored record rather than from the request, so one teacher cannot reach another course's material by sending its ID.
- **The teacher's view of bot history is anonymised in the query itself** — the student ID is never selected and the users table is never joined, so identity never reaches the client to be hidden there.
- **The bot's exam lockout fails closed.** It is checked two independent ways, one of which survives a reconnect and a server restart, and if the check itself errors the bot is refused.

---

## Known limitations

Stated plainly rather than left to be discovered:

- **A transparent reconnect loses the session.** If the socket drops mid-session the client rebuilds it without re-authenticating, so actions that authorize from the session refuse until the user logs in again. It fails in the safe direction, but it is a rough edge.
- **An abandoned exam attempt keeps the bot locked.** The open submission row is only cleared by submitting, so a student who opens an exam and never finishes stays locked out until they re-enter and submit it. There is no timeout.
- **Anonymised bot history is not anonymous against a small cohort.** No identifier is sent, but timestamps remain, and in a class of six that can still narrow a question down to a person. That is inherent to the cohort size, not fixable in the query.
- **Legacy plaintext passwords are still accepted** when a stored value is not a BCrypt hash. Seeded rows should be re-saved through the user screen to pick up hashing.
- **The `images` table is created but unused.** Image references currently resolve directly from the question and user rows.
- **Test coverage is limited to the pure logic.** The grading, banding, statistics and input-validation helpers are covered; the DAOs, request routing and UI are not, since they need a live database or a display.
- **One shared JDBC connection** is handed to every DAO, so the two operations that must be atomic open their own dedicated connection instead. Anything added later that needs a transaction must do the same.

---

## About this repository

The original team repository is private, and its history contains configuration and credentials that were never intended for publication. Rather than rewrite that history, this repository publishes the finished, sanitized source — which is why it carries no incremental commit history.

### My role

I was the main developer, architect and tester on this project:

- **Architecture** — the client-server split, the request/response protocol, and the real-time push channel layered on top of it.
- **Server** — the asynchronous accept loop, per-connection `ClientHandler` threads, the `ConcurrentHashMap` watcher registry, synchronized socket writes, and per-client failure isolation.
- **Data layer** — the relational schema, DDL and seed scripts, and the JDBC DAO layer, including version history for exams and questions so edits archive rather than overwrite.
- **Learning Bot** — Claude API integration and the local offline fallback engine that keeps the feature usable when the API is unavailable.
- **Access control and validation** — server-side role enforcement for the three user types, re-validation of exam access codes and enrollment windows on every request, input sanitization, and jBCrypt password hashing.
- **Testing** — the JUnit suite.

The JavaFX scene layouts were designed and built by a teammate.

---

## Third-party code

`client/src/main/java/ocsf/client/AbstractClient.java` is from the **OCSF (Object Client-Server Framework)** by Dr. Robert Laganière, Dr. Timothy C. Lethbridge, François Bélanger and Paul Holden, supporting section 3.7 of *Object Oriented Software Engineering*, under the open-source license at [lloseng.com](https://www.lloseng.com). It is vendored as source and otherwise unmodified.

Dependencies: MySQL Connector/J, jBCrypt, Gson, OpenJFX.

---

## License

[MIT](LICENSE).
