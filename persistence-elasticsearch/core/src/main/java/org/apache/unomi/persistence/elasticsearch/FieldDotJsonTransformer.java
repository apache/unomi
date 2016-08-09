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
package org.apache.unomi.persistence.elasticsearch;

import org.apache.commons.lang3.StringUtils;

import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Set;

/**
 * A custom JSON transformer that can replace dot characters in field names with a marker. This is useful for tools like
 * ElasticSearch 2.x that doesn't allow dot characters in field names since version 2.x
 */
public class FieldDotJsonTransformer {

    public static final char BEGIN_ARRAY_CHAR = '[';
    public static final char END_ARRAY_CHAR = ']';
    public static final char BEGIN_OBJECT_CHAR = '{';
    public static final char END_OBJECT_CHAR = '}';
    public static final char NAME_SEPARATOR_CHAR = ':';
    public static final char VALUE_SEPARATOR_CHAR = ',';

    public static final char STRING_BEGIN_OR_END_CHAR = '"';
    public static final char STRING_ESCAPE_CHAR = '\\';
    public static final char STRING_UNICODE_CHAR = 'u';
    public static final char STRING_DOT_CHAR = '.';

    public static final String WHITESPACE_CHARS = " \t\n\r";
    public static final String NUMBER_CHARS = "+-0123456789eE.";

    String jsonInput;
    int pos = -1;
    StringBuffer output;
    String dotReplacement = null;
    Set<String> modifiedNames = new LinkedHashSet<>();
    Deque<String> currentPath = new LinkedList<>();

    public FieldDotJsonTransformer(String jsonInput, StringBuffer output, String dotReplacement) {
        this.jsonInput = jsonInput;
        this.output = output;
        this.dotReplacement = dotReplacement;
    }

    public Set<String> transform() {
        parseValue();
        return modifiedNames;
    }

    protected Character getNextChar() {
        pos++;
        char ch = jsonInput.charAt(pos);
        if (pos >= jsonInput.length()) {
            return null;
        }
        return ch;
    }

    protected Character peekNextToken() {
        if (pos + 1 >= jsonInput.length()) {
            return null;
        }
        int i = 1;
        Character ch = jsonInput.charAt(pos + i);
        while (WHITESPACE_CHARS.indexOf(ch) > -1 && (pos + i < jsonInput.length())) {
            i++;
            ch = jsonInput.charAt(pos + i);
        }
        return ch;
    }

    protected Character getNextToken() {
        Character ch = getNextChar();
        while ((ch != null) && (WHITESPACE_CHARS.indexOf(ch) > -1)) {
            output.append(ch);
            ch = getNextChar();
        }
        return ch;
    }

    protected void parseBooleanValue(boolean expectedValue) {
        if (expectedValue) {
            StringBuilder sb = new StringBuilder();
            sb.append(getNextToken());
            sb.append(getNextChar());
            sb.append(getNextChar());
            sb.append(getNextChar());
            if ("true".equals(sb.toString())) {
                // everything matches
            }
            output.append(sb.toString());
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append(getNextToken());
            sb.append(getNextChar());
            sb.append(getNextChar());
            sb.append(getNextChar());
            sb.append(getNextChar());
            if ("false".equals(sb.toString())) {
                // everything matches
            }
            output.append(sb.toString());
        }
    }

    protected void parseNullValue() {
        StringBuilder sb = new StringBuilder();
        sb.append(getNextToken());
        sb.append(getNextChar());
        sb.append(getNextChar());
        sb.append(getNextChar());
        if ("null".equals(sb.toString())) {
            // everything matches
        }
        output.append(sb.toString());
    }

    protected String parseString(boolean escapeDots) {
        Character ch = getNextToken();
        if (ch != STRING_BEGIN_OR_END_CHAR) {
            return null;
        }
        output.append(ch);
        boolean modified = false;
        StringBuilder stringContent = new StringBuilder();
        while ((ch = getNextChar()) != null) {
            switch (ch) {
                case STRING_ESCAPE_CHAR:
                    stringContent.append(ch);
                    output.append(ch);
                    ch = getNextChar();
                    if (ch == STRING_UNICODE_CHAR) {
                        // case of Unicode escape sequence
                    }
                    output.append(ch);
                    stringContent.append(ch);
                    break;
                case STRING_DOT_CHAR:
                    if (escapeDots && dotReplacement != null) {
                        output.append(dotReplacement);
                        modified = true;
                    } else {
                        output.append(ch);
                    }
                    stringContent.append(ch);
                    break;
                case STRING_BEGIN_OR_END_CHAR:
                    output.append(ch);
                    if (modified) {
                        modifiedNames.add(StringUtils.join(currentPath, "/") + "/" + stringContent.toString());
                    }
                    return stringContent.toString();
                default:
                    output.append(ch);
                    stringContent.append(ch);
            }
        }
        return null;
    }

    protected void parseNumber() {
        StringBuilder sb = new StringBuilder();
        Character ch = peekNextToken();
        while ((ch != null) && (NUMBER_CHARS.indexOf(ch) > -1)) {
            ch = getNextChar();
            sb.append(ch);
            ch = peekNextToken();
        }
        output.append(sb.toString());
    }

    protected void parseValue() {
        char ch = peekNextToken();
        // we've got to identify the type or value first
        switch (ch) {
            case 't': // true
                parseBooleanValue(true);
                break;
            case 'f': // false
                parseBooleanValue(false);
                break;
            case 'n': // null
                parseNullValue();
                break;
            case BEGIN_OBJECT_CHAR:
                parseObject();
                break;
            case BEGIN_ARRAY_CHAR:
                parseArray();
                break;
            case STRING_BEGIN_OR_END_CHAR:
                parseString(false);
                break;
            default:
                parseNumber();
        }
    }

    protected void parseObject() {
        Character ch = getNextToken();
        if (ch != BEGIN_OBJECT_CHAR) {
            return;
        }
        output.append(ch);
        // now let's check the case of an empty object
        ch = peekNextToken();
        if (ch == END_OBJECT_CHAR) {
            ch = getNextToken();
            output.append(ch);
            return;
        }
        if (parseNameValuePair()) {
            return;
        }
        while ((ch = getNextToken()) != null) {
            output.append(ch);
            switch (ch) {
                case VALUE_SEPARATOR_CHAR:
                    parseNameValuePair();
                    break;
                case END_OBJECT_CHAR:
                    return;
                default:
                    return;
            }
        }
    }

    protected void parseArray() {
        Character ch = getNextToken();
        if (ch != BEGIN_ARRAY_CHAR) {
            return;
        }
        output.append(ch);
        // now let's check the case of an empty array
        ch = peekNextToken();
        if (ch == END_ARRAY_CHAR) {
            ch = getNextToken();
            output.append(ch);
            return;
        }
        parseValue();
        while ((ch = getNextToken()) != null) {
            output.append(ch);
            switch (ch) {
                case VALUE_SEPARATOR_CHAR:
                    parseValue();
                    break;
                case END_ARRAY_CHAR:
                    return;
                default:
                    return;
            }
        }
    }

    protected boolean parseNameValuePair() {
        Character ch;
        String name = parseString(true);
        if (name != null) {
            currentPath.addLast(name);
        }
        ch = getNextToken();
        if (ch != NAME_SEPARATOR_CHAR) {
            return true;
        }
        output.append(ch);
        parseValue();
        if (name != null) {
            currentPath.removeLast();
        }
        return false;
    }

}
