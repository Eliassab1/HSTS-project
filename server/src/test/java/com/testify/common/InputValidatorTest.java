package com.testify.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the request-boundary input hardening.
 *
 * Prepared statements are the primary defence against injection; this class is
 * the second layer. {@code isSafeIdentifier} is the exception — it guards the
 * one place a value is used as a raw SQL identifier, where there is no
 * parameter placeholder to hide behind, so its rejections are load-bearing.
 */
class InputValidatorTest {

    @Nested
    @DisplayName("clean")
    class Clean {

        @Test
        @DisplayName("null in, null out")
        void nullPassesThrough() {
            // An optional field that was never filled in must stay absent
            // rather than becoming an empty string in the database.
            assertNull(InputValidator.clean(null, 100));
        }

        @Test
        @DisplayName("surrounding whitespace is trimmed")
        void trimsWhitespace() {
            assertEquals("Algebra", InputValidator.clean("  Algebra  ", 100));
        }

        /** Wraps the given code point between A and B, such as NUL or DEL. */
        private static String around(int codePoint) {
            return "A" + (char) codePoint + "B";
        }

        @Test
        @DisplayName("NUL and control characters are stripped")
        void stripsControlCharacters() {
            // A NUL is the classic truncation trick against C-backed drivers;
            // the rest are simply not valid in any field the UI offers.
            assertEquals("AB", InputValidator.clean(around(0x00), 100), "NUL");
            assertEquals("AB", InputValidator.clean(around(0x07), 100), "BEL");
            assertEquals("AB", InputValidator.clean(around(0x1B), 100), "ESC");
            assertEquals("AB", InputValidator.clean(around(0x1F), 100), "unit separator");
            assertEquals("AB", InputValidator.clean(around(0x7F), 100), "DEL");
        }

        @Test
        @DisplayName("a control character is stripped before the value is trimmed")
        void stripsThenTrims() {
            String padded = (char) 0x00 + "  AB  " + (char) 0x00;

            assertEquals("AB", InputValidator.clean(padded, 100));
        }

        @Test
        @DisplayName("tab, newline and carriage return survive")
        void keepsOrdinaryWhitespace() {
            // Question text and instructions are multi-line by nature; stripping
            // newlines here would quietly reflow every teacher's formatting.
            assertEquals("line one\nline two",
                    InputValidator.clean("line one\nline two", 100));
            assertEquals("a\tb", InputValidator.clean("a\tb", 100));
            assertEquals("a\r\nb", InputValidator.clean("a\r\nb", 100));
        }

        @Test
        @DisplayName("values longer than the cap are truncated")
        void capsLength() {
            assertEquals("abcde", InputValidator.clean("abcdefghij", 5));
        }

        @Test
        @DisplayName("a value at exactly the cap is untouched")
        void exactlyAtCap() {
            assertEquals("abcde", InputValidator.clean("abcde", 5));
        }

        @Test
        @DisplayName("a cap of zero or less means no cap")
        void noCapWhenNonPositive() {
            String long_ = "x".repeat(500);

            assertEquals(long_, InputValidator.clean(long_, 0));
            assertEquals(long_, InputValidator.clean(long_, -1));
        }

        @Test
        @DisplayName("trimming happens before the length cap")
        void trimsBeforeCapping() {
            // Otherwise padding a value with spaces would eat into its budget
            // and truncate real characters off the end.
            assertEquals("abcde", InputValidator.clean("   abcde   ", 5));
        }

        @Test
        @DisplayName("a value that is only whitespace becomes empty")
        void whitespaceOnly() {
            assertEquals("", InputValidator.clean("   ", 100));
        }
    }

    @Nested
    @DisplayName("isSafeIdentifier")
    class SafeIdentifier {

        @Test
        @DisplayName("accepts letters, digits and underscores")
        void acceptsPlainIdentifiers() {
            assertTrue(InputValidator.isSafeIdentifier("test_submissions"));
            assertTrue(InputValidator.isSafeIdentifier("chk_status_1"));
            assertTrue(InputValidator.isSafeIdentifier("A"));
        }

        @Test
        @DisplayName("rejects null and empty")
        void rejectsNullAndEmpty() {
            assertFalse(InputValidator.isSafeIdentifier(null));
            assertFalse(InputValidator.isSafeIdentifier(""));
        }

        @Test
        @DisplayName("rejects anything that could break out of an identifier")
        void rejectsInjectionCharacters() {
            // These are the ones that matter: this value is concatenated into
            // DDL, so a backtick, quote or semicolon getting through would be
            // an injection rather than merely bad data.
            assertFalse(InputValidator.isSafeIdentifier("drop table"));
            assertFalse(InputValidator.isSafeIdentifier("a;DROP TABLE users"));
            assertFalse(InputValidator.isSafeIdentifier("a`b"));
            assertFalse(InputValidator.isSafeIdentifier("a'b"));
            assertFalse(InputValidator.isSafeIdentifier("a\"b"));
            assertFalse(InputValidator.isSafeIdentifier("a-b"));
            assertFalse(InputValidator.isSafeIdentifier("a.b"));
            assertFalse(InputValidator.isSafeIdentifier("a b"));
        }

        @Test
        @DisplayName("accepts 64 characters and rejects 65")
        void enforcesLengthBoundary() {
            assertTrue(InputValidator.isSafeIdentifier("a".repeat(64)));
            assertFalse(InputValidator.isSafeIdentifier("a".repeat(65)));
        }
    }

    @Nested
    @DisplayName("oneOf")
    class OneOf {

        @Test
        @DisplayName("matches case-insensitively")
        void matchesIgnoringCase() {
            assertEquals("DARK",
                    InputValidator.oneOf("dark", "LILAC", "LILAC", "LIGHT", "DARK"));
        }

        @Test
        @DisplayName("returns the whitelisted spelling, not the caller's")
        void returnsCanonicalSpelling() {
            // The database CHECK constraint is on the canonical value, so
            // echoing the caller's casing back would fail the write.
            assertEquals("LIGHT",
                    InputValidator.oneOf("LiGhT", "LILAC", "LILAC", "LIGHT"));
        }

        @Test
        @DisplayName("falls back to the default when nothing matches")
        void unknownFallsBackToDefault() {
            assertEquals("LILAC",
                    InputValidator.oneOf("NEON", "LILAC", "LILAC", "LIGHT", "DARK"));
        }

        @Test
        @DisplayName("null falls back to the default")
        void nullFallsBackToDefault() {
            assertEquals("LILAC",
                    InputValidator.oneOf(null, "LILAC", "LILAC", "LIGHT"));
        }

        @Test
        @DisplayName("an empty whitelist always yields the default")
        void emptyWhitelist() {
            assertEquals("LILAC", InputValidator.oneOf("DARK", "LILAC"));
        }
    }

    @Nested
    @DisplayName("answerLetter")
    class AnswerLetter {

        @Test
        @DisplayName("accepts A through D in any case, with padding")
        void normalisesValidLetters() {
            assertEquals("A", InputValidator.answerLetter("A"));
            assertEquals("B", InputValidator.answerLetter("b"));
            assertEquals("C", InputValidator.answerLetter(" c "));
            assertEquals("D", InputValidator.answerLetter("d "));
        }

        @Test
        @DisplayName("rejects a letter outside A-D")
        void rejectsOutOfRangeLetter() {
            // Every question has exactly four options, so an E is either a bug
            // or a hand-crafted request; either way it must not be stored.
            assertNull(InputValidator.answerLetter("E"));
            assertNull(InputValidator.answerLetter("Z"));
        }

        @Test
        @DisplayName("rejects null, empty and multi-character values")
        void rejectsMalformed() {
            assertNull(InputValidator.answerLetter(null));
            assertNull(InputValidator.answerLetter(""));
            assertNull(InputValidator.answerLetter("   "));
            assertNull(InputValidator.answerLetter("AB"));
            assertNull(InputValidator.answerLetter("1"));
        }
    }
}
