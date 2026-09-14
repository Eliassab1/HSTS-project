-- =============================================================
--  HSTS  –  Drop & Recreate All Tables
--  Run against: hsts_db
-- =============================================================

USE hsts_db;

-- ─────────────────────────────────────────────────────────────
--  DROP (children first to respect FK constraints)
-- ─────────────────────────────────────────────────────────────
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS bot_questions;
DROP TABLE IF EXISTS bot_sources;
DROP TABLE IF EXISTS course_bots;
DROP TABLE IF EXISTS course_teachers;
DROP TABLE IF EXISTS student_answers;
DROP TABLE IF EXISTS test_submissions;
DROP TABLE IF EXISTS test_questions;
DROP TABLE IF EXISTS tests_history;
DROP TABLE IF EXISTS questions_history;
DROP TABLE IF EXISTS enrollments;
DROP TABLE IF EXISTS user_settings;
DROP TABLE IF EXISTS images;
DROP TABLE IF EXISTS tests;
DROP TABLE IF EXISTS teacher_statistics;
DROP TABLE IF EXISTS questions;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS courses;

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================
--  CREATE (parents first)
-- =============================================================

CREATE TABLE courses (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    course_code  CHAR(2)      NOT NULL UNIQUE,
    course_name  VARCHAR(100) NOT NULL,
    subject_code CHAR(2)      NOT NULL,
    subject_name VARCHAR(100) NOT NULL,
    UNIQUE (subject_code, course_code)
);

CREATE TABLE users (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    full_name     VARCHAR(100) NOT NULL,
    grade_level   INT NULL,
    avatar_url    VARCHAR(255) NULL,   -- added at runtime by UserDAO.ensureAvatarUrlColumn(); inlined here for a clean recreate
    national_id   VARCHAR(9) NULL,     -- added at runtime by UserDAO.ensureNationalIdColumn(); ת"ז, unique when set
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CHECK (role IN ('STUDENT', 'TEACHER', 'PRINCIPAL')),
    CHECK (
        (role = 'STUDENT'                    AND grade_level BETWEEN 9 AND 12)
        OR
        (role IN ('TEACHER', 'PRINCIPAL')    AND grade_level IS NULL)
    ),
    UNIQUE (national_id)
);

CREATE TABLE questions (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    question_code VARCHAR(5)   NOT NULL UNIQUE,
    course_id     INT          NOT NULL,
    question_text TEXT         NOT NULL,
    option_a      TEXT         NOT NULL,
    option_b      TEXT         NOT NULL,
    option_c      TEXT         NOT NULL,
    option_d      TEXT         NOT NULL,
    correct_option CHAR(1)     NOT NULL,
    image_url     VARCHAR(255),
    teacher_id    INT NULL,   -- added at runtime by QUESTIONSDAO.ensureTeacherIdColumn(); NULL/0 = unattributed. No FK in the live schema (matches the ALTER TABLE that adds it), so it's left unconstrained here too.
    difficulty_level VARCHAR(10) NULL,  -- added at runtime by QUESTIONSDAO.ensureDifficultyLevelColumn(); 'EASY' | 'MEDIUM' | 'HARD'
    topic         VARCHAR(100) NULL,    -- added at runtime by QUESTIONSDAO.ensureTopicColumn(); subject area within the course
    FOREIGN KEY (course_id) REFERENCES courses(id),
    CHECK (correct_option IN ('A','B','C','D'))
);

CREATE TABLE tests (
    id                   INT AUTO_INCREMENT PRIMARY KEY,
    test_code            CHAR(6)  NOT NULL UNIQUE,
    teacher_id           INT      NOT NULL,
    course_id            INT      NOT NULL,
    duration_minutes     INT      NOT NULL,
    student_instructions TEXT,
    teacher_notes        TEXT,
    is_active            BOOLEAN  DEFAULT FALSE,
    approval_status      VARCHAR(20) DEFAULT 'APPROVED',
    rejection_reason     VARCHAR(500) NULL,   -- added at runtime by TestsDAO.ensureRejectionReasonColumn()
    open_at              DATETIME NULL,       -- added at runtime by TestsDAO.ensureOpenAtColumn()
    close_at             DATETIME NULL,       -- added at runtime by TestsDAO.ensureCloseAtColumn()
    exam_code            CHAR(4)  NULL,       -- added at runtime by TestsDAO.ensureExamCodeColumn()
    created_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (teacher_id) REFERENCES users(id),
    FOREIGN KEY (course_id)  REFERENCES courses(id),
    CHECK (duration_minutes > 0),
    CHECK (approval_status IN ('PENDING','APPROVED','REJECTED'))
);

CREATE TABLE teacher_statistics (
    teacher_id      INT PRIMARY KEY,
    tests_count     INT DEFAULT 0,
    questions_count INT DEFAULT 0,
    FOREIGN KEY (teacher_id) REFERENCES users(id)
);

CREATE TABLE enrollments (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    student_id  INT NOT NULL,
    course_id   INT NOT NULL,
    enrolled_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (student_id) REFERENCES users(id),
    FOREIGN KEY (course_id)  REFERENCES courses(id),
    UNIQUE (student_id, course_id)
);

CREATE TABLE test_questions (
    test_id      INT            NOT NULL,
    question_id  INT            NOT NULL,
    points_worth DECIMAL(5,2)   DEFAULT 1.00,
    PRIMARY KEY (test_id, question_id),
    FOREIGN KEY (test_id)     REFERENCES tests(id),
    FOREIGN KEY (question_id) REFERENCES questions(id)
);

CREATE TABLE test_submissions (
    id                     INT AUTO_INCREMENT PRIMARY KEY,
    test_id                INT            NOT NULL,
    student_id             INT            NOT NULL,
    status                 VARCHAR(20)    DEFAULT 'PENDING',
    final_score            DECIMAL(5,2)   NULL,
    original_score         DECIMAL(5,2)   NULL,   -- added at runtime by test_submissionsDAO.ensureOriginalScoreColumn(); computer-graded score, kept even after an override
    grade_override_reason  VARCHAR(500)   NULL,   -- added at runtime by test_submissionsDAO.ensureGradeOverrideReasonColumn()
    approved_by            INT            NULL,   -- added at runtime by test_submissionsDAO.ensureApprovedByColumn(); teacher user ID who approved/overrode the grade
    started_at             TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    submitted_at           TIMESTAMP      NULL,
    FOREIGN KEY (test_id)    REFERENCES tests(id),
    FOREIGN KEY (student_id) REFERENCES users(id),
    CHECK (status IN ('PENDING', 'PROCESSING', 'AWAITING_APPROVAL', 'GRADED')),
    CHECK (final_score IS NULL OR final_score BETWEEN 0.00 AND 100.00)
);

CREATE TABLE student_answers (
    id             INT AUTO_INCREMENT PRIMARY KEY,
    submission_id  INT         NOT NULL,
    question_id    INT         NOT NULL,
    student_answer VARCHAR(1)  NOT NULL,
    FOREIGN KEY (submission_id) REFERENCES test_submissions(id),
    FOREIGN KEY (question_id)   REFERENCES questions(id)
);

-- Owned by ImagesDAO.createTableIfNotExists(). Not wired into the rest of the
-- app yet (questions/avatars still resolve through image_url/avatar_url
-- directly). No FK to questions(id) — the live table is created before
-- questions during server startup init, and MySQL rejects a FK to a table
-- that doesn't exist yet, so this stays unconstrained here for parity.
CREATE TABLE images (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    image_url   VARCHAR(255) NOT NULL,
    question_id INT NULL,
    uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Owned by QUESTIONSDAO.createQuestionsHistoryTable(). One row per archived
-- version of a question, written by updateQuestion() before it overwrites the
-- live row (spec 2.2). Deliberately has NO foreign key to questions(id):
-- test_questions and student_answers already point at the live row, and an
-- archived version has to outlive a deleted question rather than cascade away
-- with it.
CREATE TABLE questions_history (
    history_id     INT AUTO_INCREMENT PRIMARY KEY,
    question_id    INT NOT NULL,
    question_code  VARCHAR(5),
    course_id      INT,
    question_text  TEXT,
    option_a       TEXT,
    option_b       TEXT,
    option_c       TEXT,
    option_d       TEXT,
    correct_option CHAR(1),
    image_url      VARCHAR(255),
    teacher_id     INT NULL,
    difficulty_level VARCHAR(10) NULL,
    topic          VARCHAR(100) NULL,
    archived_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Owned by TestsDAO.createTestsHistoryTable(). One row per archived version of
-- an exam header, written by updateExamWithHistory() before it rewrites the
-- live row (spec 3.5). No foreign key to tests(id), for the same reason
-- questions_history has none. The question set at the time is not captured:
-- an exam with submissions cannot be edited at all, so there is no scored
-- paper whose question set would need reconstructing.
CREATE TABLE tests_history (
    history_id           INT AUTO_INCREMENT PRIMARY KEY,
    test_id              INT NOT NULL,
    test_code            CHAR(6),
    teacher_id           INT,
    course_id            INT,
    duration_minutes     INT,
    student_instructions TEXT,
    teacher_notes        TEXT,
    is_active            BOOLEAN,
    approval_status      VARCHAR(20),
    archived_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Owned by UserSettingsDAO.createUserSettingsTable(). One row per user;
-- created on demand with defaults the first time a user's settings are
-- requested (see UserSettingsDAO.getSettings()).
CREATE TABLE user_settings (
    user_id       INT PRIMARY KEY,
    theme         VARCHAR(20) NOT NULL DEFAULT 'LILAC',
    font_scale    INT NOT NULL DEFAULT 100,
    high_contrast BOOLEAN NOT NULL DEFAULT FALSE,
    FOREIGN KEY (user_id) REFERENCES users(id),
    CHECK (theme IN ('LILAC','LIGHT','DARK','COLORBLIND')),
    CHECK (font_scale BETWEEN 50 AND 200)
);


-- ─────────────────────────────────────────────────────────────
--  LEARNING BOT (spec 13 + 14)
-- ─────────────────────────────────────────────────────────────

-- Owned by CourseTeacherDAO.createCourseTeachersTable(). Which teachers are
-- attached to which courses. The schema had no teacher-to-course link before
-- this: tests.teacher_id says who authored one exam, which cannot answer "may
-- this teacher edit this course's bot". Spec 13.3 needs exactly that, because
-- ANY teacher of the course may edit the bot's sources, not only its creator.
CREATE TABLE course_teachers (
    course_id  INT NOT NULL,
    teacher_id INT NOT NULL,
    PRIMARY KEY (course_id, teacher_id),
    FOREIGN KEY (course_id)  REFERENCES courses(id),
    FOREIGN KEY (teacher_id) REFERENCES users(id)
);

-- Owned by CourseBotDAO.createCourseBotsTable(). One bot per course — the
-- UNIQUE on course_id is what enforces that, rather than application code.
CREATE TABLE course_bots (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    course_id    INT NOT NULL UNIQUE,
    bot_name     VARCHAR(100) NOT NULL,
    is_available BOOLEAN NOT NULL DEFAULT TRUE,
    created_by   INT NULL,
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (course_id)  REFERENCES courses(id),
    FOREIGN KEY (created_by) REFERENCES users(id)
);

-- Owned by BotSourceDAO.createBotSourcesTable(). The teacher-supplied material
-- a bot answers from (spec 13.2) — passed to the engine as its ONLY permitted
-- corpus, so a bot with no rows here can only say it does not know.
-- updated_by is stamped on every write because spec 13.3 makes these shared
-- documents, and a shared document that does not say who last edited it is a
-- document nobody trusts.
CREATE TABLE bot_sources (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    bot_id     INT NOT NULL,
    title      VARCHAR(200) NOT NULL,
    content    TEXT NOT NULL,
    updated_by INT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (bot_id)     REFERENCES course_bots(id),
    FOREIGN KEY (updated_by) REFERENCES users(id)
);

-- Owned by BotQuestionDAO.createBotQuestionsTable(). Every exchange with a
-- bot. student_id IS stored — a student can review their own history (spec
-- 14.2) — but the teacher's history query never SELECTs it (spec 14.3), so
-- the identity stays in this table rather than travelling to a client to be
-- hidden there. engine_used records per exchange whether Claude or the local
-- fallback answered, since one failed call falls back for that call alone.
CREATE TABLE bot_questions (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    bot_id        INT NOT NULL,
    student_id    INT NOT NULL,
    question_text TEXT NOT NULL,
    answer_text   TEXT NOT NULL,
    engine_used   VARCHAR(20) NOT NULL,
    asked_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (bot_id)     REFERENCES course_bots(id),
    FOREIGN KEY (student_id) REFERENCES users(id)
);

-- ─────────────────────────────────────────────────────────────
--  VERIFY
-- ─────────────────────────────────────────────────────────────
SELECT table_name, table_rows
FROM information_schema.tables
WHERE table_schema = 'hsts_db'
ORDER BY table_name;
