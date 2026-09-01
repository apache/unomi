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
package org.apache.unomi.router.api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Decides whether the endpoint URI carried by an import or export configuration may be used.
 *
 * <p>Two rules apply. The scheme must belong to the configured allow-list. And a {@code file}
 * endpoint must resolve inside one of the base directories the deployment permits — the directory
 * the URI names, and every path-bearing option it carries, since validating only the directory would
 * leave {@code file:///permitted/?fileName=../../elsewhere} open.
 *
 * <p>Containment is recursive: any depth under a permitted base directory is accepted, whether or
 * not the directory exists yet. It is decided on canonical paths — percent-encoding decoded, parent
 * segments resolved, symbolic links followed — and compared component by component, so a sibling
 * that merely shares a textual prefix with a permitted directory is not mistaken for one of its
 * children.
 *
 * <p>What is validated is what Camel will use, so the URI is read the way Camel reads it: option
 * names are percent-decoded before they are matched, a {@code RAW()} value keeps its ampersands
 * instead of being cut in two, and the File Language expressions a path-bearing option may carry are
 * accounted for rather than taken literally.
 *
 * <p>A path the file system cannot make sense of, and a path whose existing part cannot be resolved,
 * are both refusals: nothing is thrown out of this class, because one malformed endpoint must not
 * cost a deployment the routes of every other configuration.
 *
 * <p>Schemes other than {@code file} carry no local path and are left to the scheme allow-list.
 */
public final class EndpointValidator {

    public static final String FILE_SCHEME = "file";

    /**
     * The Camel file endpoint options whose value is, or contains, a path. Compared in lower case.
     *
     * <p>Selection options ({@code include}, {@code exclude}, {@code antInclude}, {@code antExclude})
     * are deliberately absent: they are patterns matched against the files the endpoint directory
     * already offers, not paths Camel resolves, so they cannot direct a read or a write anywhere else.
     * Holding them to containment would only refuse legitimate patterns — a regular expression is not
     * a valid path on every file system.
     */
    private static final Set<String> PATH_BEARING_OPTIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "filename", "tempfilename", "tempprefix", "move", "movefailed", "moveexisting", "premove",
            "donefilename")));

    /**
     * Camel evaluates a path-bearing option as a File Language expression, so the value that reaches
     * the file system is not the one configured. These tokens expand to a name, or to a name and the
     * subdirectories the file already sits in: whatever they hold, they cannot hold a parent segment,
     * so containment can be decided with them replaced by a placeholder.
     */
    private static final Set<String> NAME_TOKENS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "file:name", "file:name.noext", "file:name.ext", "file:onlyname", "file:onlyname.noext",
            "file:ext", "file:size", "file:modified", "exchangeid")));

    /** Expands to a date, which cannot hold a parent segment either. A prefix, not a whole token. */
    private static final String DATE_TOKEN_PREFIX = "date:";

    /** Expands to the directory the file sits in, which is the endpoint directory or one below it. */
    private static final String PARENT_TOKEN = "file:parent";

    /** Stands in for a token that expands to a name: one path component, never a parent segment. */
    private static final String NAME_PLACEHOLDER = "_";

    /** The path segments the walk interprets rather than resolves against the file system. */
    private static final String CURRENT_DIRECTORY = ".";
    private static final String PARENT_DIRECTORY = "..";

    private EndpointValidator() {
    }

    /**
     * Validates the endpoint URI of an import or export configuration.
     *
     * @param endpointUri       the endpoint URI, as configured
     * @param allowedSchemes    the comma-separated list of allowed schemes
     * @param permittedBaseDirs the comma-separated list of base directories a {@code file} endpoint may
     *                          resolve into
     * @return {@code null} when the endpoint may be used, otherwise the reason it is refused
     */
    public static String validate(String endpointUri, String allowedSchemes, String permittedBaseDirs) {
        if (isBlank(endpointUri)) {
            return "no endpoint is configured";
        }

        int schemeSeparator = endpointUri.indexOf(':');
        if (schemeSeparator <= 0) {
            return "endpoint '" + endpointUri + "' has no scheme";
        }

        String scheme = endpointUri.substring(0, schemeSeparator);
        if (!containsIgnoreCase(split(allowedSchemes), scheme)) {
            return "endpoint scheme '" + scheme + "' is not allowed";
        }

        if (!FILE_SCHEME.equalsIgnoreCase(scheme)) {
            return null;
        }

        try {
            return validateContainment(endpointUri, permittedBaseDirs);
        } catch (Refusal refusal) {
            return refusal.getMessage();
        } catch (InvalidPathException e) {
            return "endpoint '" + endpointUri + "' does not denote a path this file system can use: "
                    + e.getMessage();
        }
    }

    private static String validateContainment(String endpointUri, String permittedBaseDirs) throws Refusal {
        List<Path> baseDirs = new ArrayList<>();
        for (String baseDir : split(permittedBaseDirs)) {
            baseDirs.add(canonicalize(Paths.get(baseDir)));
        }
        if (baseDirs.isEmpty()) {
            return "no permitted base directory is configured for file endpoints";
        }

        int querySeparator = endpointUri.indexOf('?');
        String head = querySeparator < 0 ? endpointUri : endpointUri.substring(0, querySeparator);
        String query = querySeparator < 0 ? "" : endpointUri.substring(querySeparator + 1);

        Path directory = Paths.get(decode(stripScheme(head)));
        if (!isContained(directory, baseDirs)) {
            return "directory '" + directory + "' is outside the permitted directories";
        }

        for (String[] parameter : parseQuery(query)) {
            String option = parameter[0];
            if (!PATH_BEARING_OPTIONS.contains(decode(option).toLowerCase(Locale.ROOT))) {
                continue;
            }
            String value = stripRaw(decode(parameter[1]));
            if (value.isEmpty()) {
                continue;
            }
            if (!isContained(directory.resolve(withoutExpressions(option, value)), baseDirs)) {
                return "option '" + option + "' points outside the permitted directories";
            }
        }

        return null;
    }

    /**
     * Removes the scheme, and the authority separator Camel tolerates in any of its forms
     * ({@code file:dir}, {@code file://dir}, {@code file:///dir}).
     */
    private static String stripScheme(String head) {
        String path = head.substring(head.indexOf(':') + 1);
        return path.startsWith("//") ? path.substring(2) : path;
    }

    private static boolean isContained(Path path, List<Path> baseDirs) throws Refusal {
        Path candidate = canonicalize(path);
        for (Path baseDir : baseDirs) {
            if (candidate.startsWith(baseDir)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves a path to the one the file system would actually use, walking it one component at a
     * time from the root the way the file system does: a symbolic link is expanded where it stands,
     * and a parent segment is applied to what the walk has resolved so far.
     *
     * <p>The order is what makes this correct. Collapsing parent segments first — {@code normalize()}
     * on the whole path — erases the component they cancel, symbolic link included, so
     * {@code /base/link/..} reads as {@code /base} while the file system resolves it to the parent of
     * the link's target. Only a walk sees the link before the segment that cancels it.
     *
     * <p>A path that does not exist yet is resolved as far as it exists and kept as it stands from
     * there — an export destination is created on first write, and must be decided on before it
     * exists.
     *
     * <p>A path whose existing part cannot be resolved is refused rather than accepted as it stands: a
     * dangling symbolic link inside a permitted directory would otherwise be taken for a child of it,
     * and would leave it as soon as its target is created.
     */
    private static Path canonicalize(Path path) throws Refusal {
        Path absolute = path.toAbsolutePath();
        Path resolved = absolute.getRoot();
        if (resolved == null) {
            // an absolute path always has a root; without one there is nothing to decide on
            throw new Refusal("path '" + path + "' cannot be made absolute");
        }
        // once a component is missing, nothing below it can exist: the rest is kept as written
        boolean belowWhatExists = false;
        for (Path component : absolute) {
            String name = component.toString();
            if (CURRENT_DIRECTORY.equals(name)) {
                continue;
            }
            if (PARENT_DIRECTORY.equals(name)) {
                Path parent = resolved.getParent();
                if (parent != null) {
                    resolved = parent;
                }
                continue;
            }
            Path candidate = resolved.resolve(component);
            if (belowWhatExists || !Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
                belowWhatExists = true;
                resolved = candidate;
            } else if (Files.isSymbolicLink(candidate)) {
                try {
                    resolved = candidate.toRealPath();
                } catch (IOException e) {
                    throw new Refusal("path '" + candidate + "' cannot be resolved on the file system: " + e);
                }
            } else {
                resolved = candidate;
            }
        }
        return resolved;
    }

    /**
     * Replaces the File Language expressions of a path-bearing option by what they can contribute to a
     * path, so that containment is decided on the value Camel will resolve instead of on the
     * placeholder as it is written: {@code move=${file:parent}/../elsewhere} normalizes to a child of
     * the endpoint directory while Camel sends it to a sibling.
     *
     * <p>{@code ${file:parent}} becomes the endpoint directory itself, which is the shallowest
     * directory it can expand to, so parent segments are measured against the worst case. A token that
     * expands to a name becomes a placeholder. Every other expression is refused: a header, a property
     * or the body can hold any path at all, and there is nothing left to validate.
     */
    private static String withoutExpressions(String option, String value) throws Refusal {
        if (value.indexOf('$') < 0) {
            return value;
        }
        StringBuilder substituted = new StringBuilder(value.length());
        int position = 0;
        while (position < value.length()) {
            int start = expressionStart(value, position);
            if (start < 0) {
                substituted.append(value, position, value.length());
                break;
            }
            substituted.append(value, position, start);
            int open = value.indexOf('{', start);
            int close = value.indexOf('}', open);
            if (close < 0) {
                throw new Refusal("option '" + option + "' carries an expression that is never closed");
            }
            substituted.append(contributionOf(option, value.substring(open + 1, close)));
            position = close + 1;
        }
        return substituted.toString();
    }

    /**
     * The start of the next expression, in either of the forms Camel accepts ({@code ${...}} and
     * {@code $simple{...}}), or {@code -1}. A dollar sign that starts neither is a plain character.
     */
    private static int expressionStart(String value, int from) {
        for (int i = value.indexOf('$', from); i >= 0; i = value.indexOf('$', i + 1)) {
            if (value.startsWith("${", i) || value.startsWith("$simple{", i)) {
                return i;
            }
        }
        return -1;
    }

    private static String contributionOf(String option, String expression) throws Refusal {
        String token = expression.trim().toLowerCase(Locale.ROOT);
        if (PARENT_TOKEN.equals(token)) {
            return ".";
        }
        if (NAME_TOKENS.contains(token) || token.startsWith(DATE_TOKEN_PREFIX)) {
            return NAME_PLACEHOLDER;
        }
        throw new Refusal("option '" + option + "' uses the expression '${" + expression
                + "}', whose value cannot be held inside the permitted directories");
    }

    /**
     * {@code RAW(...)} and {@code RAW{...}} tell Camel not to decode a value; the path it wraps is used
     * as it stands.
     */
    private static String stripRaw(String value) {
        if (value.startsWith("RAW(") && value.endsWith(")")) {
            return value.substring(4, value.length() - 1);
        }
        if (value.startsWith("RAW{") && value.endsWith("}")) {
            return value.substring(4, value.length() - 1);
        }
        return value;
    }

    /**
     * Splits the query into its parameters the way Camel does. A {@code RAW()} value ends at the marker
     * that closes it, not at the first ampersand, so an ampersand it contains does not start a new
     * parameter: splitting on every ampersand would leave the rest of that value unvalidated, which is
     * enough to carry {@code fileName=RAW(profiles.csv&../../elsewhere)} through.
     */
    private static List<String[]> parseQuery(String query) {
        List<String[]> parameters = new ArrayList<>();
        int position = 0;
        while (position < query.length()) {
            int end = endOfParameter(query, position);
            String parameter = query.substring(position, end);
            int separator = parameter.indexOf('=');
            if (separator > 0) {
                parameters.add(new String[]{parameter.substring(0, separator), parameter.substring(separator + 1)});
            }
            position = end + 1;
        }
        return parameters;
    }

    /** Where the parameter that starts at {@code from} ends: exclusive, on its closing ampersand. */
    private static int endOfParameter(String query, int from) {
        int ampersand = query.indexOf('&', from);
        int valueStart = query.indexOf('=', from);
        char closing = 0;
        if (valueStart >= 0 && (ampersand < 0 || valueStart < ampersand)) {
            closing = rawClosingMarker(query.substring(valueStart + 1));
        }
        if (closing == 0) {
            return ampersand < 0 ? query.length() : ampersand;
        }
        for (int i = valueStart + 1; i < query.length(); i++) {
            if (query.charAt(i) == closing && (i + 1 == query.length() || query.charAt(i + 1) == '&')) {
                return i + 1;
            }
        }
        return query.length();
    }

    private static char rawClosingMarker(String value) {
        if (value.startsWith("RAW(")) {
            return ')';
        }
        if (value.startsWith("RAW{")) {
            return '}';
        }
        return 0;
    }

    /**
     * Decodes the percent-encoding of a URI, so that containment is decided on the path the file system
     * will see. Unlike form decoding, {@code +} is left alone: it is a valid character in a file name.
     * Characters that are not escaped keep their own encoding, so a path that mixes an escape with a
     * non-ASCII name is not corrupted into a different path.
     */
    private static String decode(String value) {
        if (value.indexOf('%') < 0) {
            return value;
        }
        ByteArrayOutputStream decoded = new ByteArrayOutputStream(value.length());
        StringBuilder verbatim = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == '%' && i + 2 < value.length()) {
                int high = Character.digit(value.charAt(i + 1), 16);
                int low = Character.digit(value.charAt(i + 2), 16);
                if (high >= 0 && low >= 0) {
                    writeUtf8(verbatim, decoded);
                    decoded.write((high << 4) + low);
                    i += 2;
                    continue;
                }
            }
            verbatim.append(character);
        }
        writeUtf8(verbatim, decoded);
        return new String(decoded.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void writeUtf8(StringBuilder verbatim, ByteArrayOutputStream decoded) {
        if (verbatim.length() == 0) {
            return;
        }
        byte[] bytes = verbatim.toString().getBytes(StandardCharsets.UTF_8);
        decoded.write(bytes, 0, bytes.length);
        verbatim.setLength(0);
    }

    private static List<String> split(String commaSeparated) {
        List<String> values = new ArrayList<>();
        if (commaSeparated == null) {
            return values;
        }
        for (String value : commaSeparated.split(",")) {
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }
        return values;
    }

    private static boolean containsIgnoreCase(List<String> values, String searched) {
        for (String value : values) {
            if (value.equalsIgnoreCase(searched)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * A reason an endpoint cannot be validated, carried back to {@link #validate} to be answered as a
     * refusal. Nothing is thrown out of this class.
     */
    private static final class Refusal extends Exception {

        private static final long serialVersionUID = 1L;

        Refusal(String reason) {
            super(reason);
        }
    }
}
