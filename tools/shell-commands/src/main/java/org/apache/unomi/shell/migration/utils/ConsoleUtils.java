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
package org.apache.unomi.shell.migration.utils;

import org.apache.commons.lang3.StringUtils;
import org.apache.felix.service.command.CommandSession;
import org.jline.reader.LineReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

/**
 * @author dgaillard
 */
public class ConsoleUtils {
    private static final Logger logger = LoggerFactory.getLogger(HttpUtils.class);

    /**
     * This will ask a question to the user and return the default answer if the user does not answer.
     *
     * @param session           CommandSession
     * @param msg               String message to ask
     * @param defaultAnswer     String default answer
     * @return the user answer
     * @throws IOException
     */
    public static String askUserWithDefaultAnswer(CommandSession session, String msg, String defaultAnswer) throws IOException {
        String answer = promptMessageToUser(session, msg);
        if (StringUtils.isBlank(answer)) {
            return defaultAnswer;
        }
        return answer;
    }

    /**
     * This method allow you to ask a question to the user.
     * The answer is controlled before being return so the question will be ask until the user enter one the authorized answer
     *
     * @param session           CommandSession
     * @param msg               String message to ask
     * @param authorizedAnswer  Array of possible answer, all answer must be in lower case
     * @return the user answer
     * @throws IOException
     */
    public static String askUserWithAuthorizedAnswer(CommandSession session, String msg, List<String> authorizedAnswer) throws IOException {
        String answer;
        do {
            answer = promptMessageToUser(session,msg);
        } while (!authorizedAnswer.contains(answer.toLowerCase()));
        return answer;
    }

    /**
     * This method allow you to prompt a message to the user.
     * No control is done on the answer provided by the user.
     *
     * @param session   CommandSession
     * @param msg       String message to prompt
     * @return the user answer
     * @throws IOException
     */
    public static String promptMessageToUser(CommandSession session, String msg) throws IOException {
        LineReader reader = (LineReader) session.get(".jline.reader");
        return reader.readLine(msg, null);
    }

    /**
     * Print a message in the console.
     * @param session
     * @param msg
     */
    public static void printMessage(CommandSession session, String msg) {
        PrintStream writer = session.getConsole();
        writer.println(msg);
    }
}
