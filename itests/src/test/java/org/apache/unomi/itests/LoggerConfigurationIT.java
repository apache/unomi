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
package org.apache.unomi.itests;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Date;

/**
 * @author Jerome Blanchard
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class LoggerConfigurationIT extends BaseIT {

    private final static Logger LOGGER = LoggerFactory.getLogger(LoggerConfigurationIT.class);


    /*
      I did not find a way to add an appender to the pax-logging configuration programmatically to test log format in the test
      and add the correct assertions ; it seems to be a forbidden operation.
      The goal of the test is to check that log injection is no more possible after adding an extension to pax4-logging
      using fragment-bundle (see extensions/log4j-extension).
      For instance, the log are present in the target/exam/{id}/data/log/karaf.log file.
      A check in that file that no line start with PLOP should confirm that the new line character has been escaped correctly
      in exception messages avoiding any log injection.
    @Before
    public void setUp() throws InterruptedException {
        org.apache.log4j.Logger logger = org.apache.log4j.Logger.getRootLogger();
        logger.addAppender(appender)
    }
    */

    @Test
    public void testValueLogInjection() throws IOException {
        testValueLogInjection("test");
        testValueLogInjection("log\r\nPLOP WARN\tinjection");
    }

    @Test
    public void testExceptionLogInjection() throws IOException {
        testExceptionLogInjection("8");
        testExceptionLogInjection("plop\r\n" + "PLOP" + new Date() + " [main] ERROR org.apache.unomi.itests.LoggerConfigurationIT - This line is a fake one to test log injection");
    }

    @Test
    public void testExceptionLogInjectionWithMessage() throws IOException {
        testExceptionLogInjectionWithMessage("8");
        testExceptionLogInjectionWithMessage("plop\r\n" + "PLOP" + new Date() + " [main] ERROR org.apache.unomi.itests.LoggerConfigurationIT - This line is a fake one to test log injection");
    }

    private void testValueLogInjection(String value) {
        LOGGER.warn(value);
    }

    private void testExceptionLogInjection(String value) {
        try {
            Integer.parseInt(value);
        } catch (NumberFormatException e) {
            LOGGER.error("", e);
        }
    }

    private void testExceptionLogInjectionWithMessage(String value) {
        try {
            Integer.parseInt(value);
        } catch (NumberFormatException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

}
