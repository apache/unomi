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
 * <p>Schemes other than {@code file} carry no local path and are left to the scheme allow-list.
 */
public final class EndpointValidator {

    public static final String FILE_SCHEME = "file";

    /**
     * The Camel file endpoint options whose value is, or contains, a path. Compared in lower case.
     */
    private static final Set<String> PATH_BEARING_OPTIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "filename", "tempfilename", "move", "movefailed", "premove", "donefilename",
            "include", "antinclude", "antfilter")));

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

        return validateContainment(endpointUri, permittedBaseDirs);
    }

    private static String validateContainment(String endpointUri, String permittedBaseDirs) {
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
            String optionName = decode(parameter[0]);
            if (!PATH_BEARING_OPTIONS.contains(optionName.toLowerCase(Locale.ROOT))) {
                continue;
            }
            String value = stripRaw(decode(parameter[1]));
            if (value.isEmpty()) {
                continue;
            }
            if (!isContained(directory.resolve(value), baseDirs)) {
                return "option '" + optionName + "' points outside the permitted directories";
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

    private static boolean isContained(Path path, List<Path> baseDirs) {
        Path candidate = canonicalize(path);
        for (Path baseDir : baseDirs) {
            if (candidate.startsWith(baseDir)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves a path to the one the file system would actually use: made absolute, stripped of its
     * parent segments, and with the symbolic links of its existing part followed. A path that does not
     * exist yet is canonicalized through its deepest existing ancestor — an export destination is
     * created on first write, and must be decided on before it exists.
     */
    private static Path canonicalize(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        Path existing = normalized;
        while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
            existing = existing.getParent();
        }
        if (existing == null) {
            return normalized;
        }
        try {
            return existing.toRealPath().resolve(existing.relativize(normalized));
        } catch (IOException e) {
            return normalized;
        }
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

    private static List<String[]> parseQuery(String query) {
        List<String[]> parameters = new ArrayList<>();
        for (String parameter : query.split("&")) {
            if (parameter.isEmpty()) {
                continue;
            }
            int separator = parameter.indexOf('=');
            if (separator > 0) {
                parameters.add(new String[]{parameter.substring(0, separator), parameter.substring(separator + 1)});
            }
        }
        return parameters;
    }

    /**
     * Decodes the percent-encoding of a URI, so that containment is decided on the path the file system
     * will see. Unlike form decoding, {@code +} is left alone: it is a valid character in a file name.
     */
    private static String decode(String value) {
        if (value.indexOf('%') < 0) {
            return value;
        }
        ByteArrayOutputStream decoded = new ByteArrayOutputStream(value.length());
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == '%' && i + 2 < value.length()) {
                int high = Character.digit(value.charAt(i + 1), 16);
                int low = Character.digit(value.charAt(i + 2), 16);
                if (high >= 0 && low >= 0) {
                    decoded.write((high << 4) + low);
                    i += 2;
                    continue;
                }
            }
            decoded.write(character);
        }
        return new String(decoded.toByteArray(), StandardCharsets.UTF_8);
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
}
