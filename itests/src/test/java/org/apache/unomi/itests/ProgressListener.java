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

import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class ProgressListener extends RunListener {

    private static final String RESET = "\u001B[0m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String CYAN = "\u001B[36m";
    private static final String BLUE = "\u001B[34m";

    private static final String[] QUOTES = {
            "Success is not final, failure is not fatal: It is the courage to continue that counts. - Winston Churchill",
            "Believe you can and you're halfway there. - Theodore Roosevelt",
            "Don’t watch the clock; do what it does. Keep going. - Sam Levenson",
            "It does not matter how slowly you go as long as you do not stop. - Confucius",
            "Hardships often prepare ordinary people for an extraordinary destiny. - C.S. Lewis"
    };

    private static class TestTime {
        String name;
        long time;

        TestTime(String name, long time) {
            this.name = name;
            this.time = time;
        }
    }

    private final int totalTests;
    private final AtomicInteger completedTests;
    private final AtomicInteger successfulTests = new AtomicInteger(0);
    private final AtomicInteger failedTests = new AtomicInteger(0);
    private final PriorityQueue<TestTime> slowTests;
    private final boolean ansiSupported;
    private long startTime = System.currentTimeMillis();
    private long startTestTime = System.currentTimeMillis();

    public ProgressListener(int totalTests, AtomicInteger completedTests) {
        this.totalTests = totalTests;
        this.completedTests = completedTests;
        this.slowTests = new PriorityQueue<>((t1, t2) -> Long.compare(t1.time, t2.time));
        this.ansiSupported = isAnsiSupported();
    }

    private boolean isAnsiSupported() {
        String term = System.getenv("TERM");
        return System.console() != null && term != null && term.contains("xterm");
    }

    private String colorize(String text, String color) {
        if (ansiSupported) {
            return color + text + RESET;
        }
        return text;
    }

    @Override
    public void testRunStarted(Description description) {
        startTime = System.currentTimeMillis();

        // Provided ASCII Art Logo
        String[] logoLines = {
                "   ____ ___        A P A C H E  .__         ",
                "  |    |   \\____   ____   _____ |__|        ",
                "  |    |   /    \\ /  _ \\ /     \\|  |        ",
                "  |    |  /   |  (  <_> )  Y Y  \\  |        ",
                "  |______/|___|  /\\____/|__|_|  /__|        ",
                "               \\/             \\/            ",
                "                                             ",
                "   I N T E G R A T I O N   T E S T S         "
        };

        // Box dimensions
        int totalWidth = 68;
        String topBorder = "╔" + "═".repeat(totalWidth) + "╗";
        String bottomBorder = "╚" + "═".repeat(totalWidth) + "╝";

        // Print the top border
        System.out.println(colorize(topBorder, CYAN));

        // Center-align each logo line
        for (String line : logoLines) {
            int padding = (totalWidth - line.length()) / 2;
            String paddedLine = " ".repeat(padding) + line + " ".repeat(totalWidth - padding - line.length());
            System.out.println(colorize("║" + paddedLine + "║", CYAN));
        }

        // Print the progress message
        String progressMessage = "Starting test suite with " + totalTests + " tests. Good luck!";
        int progressPadding = (totalWidth - progressMessage.length()) / 2;
        String paddedProgressMessage = " ".repeat(progressPadding) + progressMessage + " ".repeat(totalWidth - progressPadding - progressMessage.length());

        System.out.println(colorize("║" + paddedProgressMessage + "║", CYAN));

        // Print the bottom border
        System.out.println(colorize(bottomBorder, CYAN));
    }

    @Override
    public void testStarted(Description description) {
        startTestTime = System.currentTimeMillis();
    }

    @Override
    public void testFinished(Description description) {
        long testDuration = System.currentTimeMillis() - startTestTime;
        completedTests.incrementAndGet();
        successfulTests.incrementAndGet(); // Default to success unless a failure is recorded separately.
        slowTests.add(new TestTime(description.getDisplayName(), testDuration));
        if (slowTests.size() > 10) {
            // Remove the smallest time, keeping only the top 5 longest
            slowTests.poll();
        }
        displayProgress();
    }

    @Override
    public void testFailure(Failure failure) {
        successfulTests.decrementAndGet(); // Remove the previous success count for this test.
        failedTests.incrementAndGet();
        System.out.println(colorize("Test failed: " + failure.getDescription(), RED));
        displayProgress();
    }

    @Override
    public void testRunFinished(Result result) {
        long elapsedTime = System.currentTimeMillis() - startTime;
        String resultMessage = result.wasSuccessful()
                ? colorize("SUCCESS!", GREEN)
                : colorize("FAILURE", RED);
        System.out.printf("%s═══════════════════════════════════════════════════════════%n" +
                        "Test suite finished in %s%s%s. Result: %s%n" +
                        "Successful: %s%d%s, Failed: %s%d%s%n" +
                        "═══════════════════════════════════════════════════════════%n",
                ansiSupported ? CYAN : "",
                ansiSupported ? YELLOW : "",
                formatTime(elapsedTime),
                ansiSupported ? RESET : "",
                resultMessage,
                ansiSupported ? GREEN : "",
                successfulTests.get(),
                ansiSupported ? RESET : "",
                ansiSupported ? RED : "",
                failedTests.get(),
                ansiSupported ? RESET : "");

        // Display the top 10 slowest tests
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.printf("Top 10 Slowest Tests:%n");
        // Table header
        System.out.printf("%s%-4s %-50s %-10s%s%n",
                ansiSupported ? BLUE : "",
                "Rank", "Test Name", "Duration",
                ansiSupported ? RESET : "");
        System.out.printf("%s%-4s %-50s %-10s%s%n",
                ansiSupported ? BLUE : "",
                "----", "--------------------------------------------------", "----------",
                ansiSupported ? RESET : "");

        // Table rows for the top 10 slowest tests
        AtomicInteger rank = new AtomicInteger(1);
        slowTests.stream()
                .sorted((t1, t2) -> Long.compare(t2.time, t1.time)) // Sort by descending order
                .limit(10)
                .forEach(test -> System.out.printf("%-4d %-50s %-10d ms%n",
                        rank.getAndIncrement(), test.name, test.time));
    }

    private void displayProgress() {
        int completed = completedTests.get();
        long elapsedTime = System.currentTimeMillis() - startTime;

        // Avoid division by very low completed count; use a floor value
        int stableCompleted = Math.max(completed, 1);
        double averageTestTimeMillis = elapsedTime / (double) stableCompleted;

        // Calculate estimated time remaining
        long estimatedRemainingTime = (long) (averageTestTimeMillis * (totalTests - completed));
        String progressBar = generateProgressBar(((double) completed / totalTests) * 100);
        String humanReadableTime = formatTime(estimatedRemainingTime);

        System.out.printf("[%s] %sProgress: %s%.2f%%%s (%d/%d tests). Estimated time remaining: %s%s%s. " +
                        "Successful: %s%d%s, Failed: %s%d%s%n",
                progressBar,
                ansiSupported ? BLUE : "",
                ansiSupported ? GREEN : "",
                ((double) completed / totalTests) * 100,
                ansiSupported ? RESET : "",
                completed,
                totalTests,
                ansiSupported ? YELLOW : "",
                humanReadableTime,
                ansiSupported ? RESET : "",
                ansiSupported ? GREEN : "",
                successfulTests.get(),
                ansiSupported ? RESET : "",
                ansiSupported ? RED : "",
                failedTests.get(),
                ansiSupported ? RESET : "");

        if (completed % Math.max(1, totalTests / 10) == 0 && completed < totalTests) {
            String quote = QUOTES[completed % QUOTES.length];
            System.out.println(colorize("Motivational Quote: " + quote, YELLOW));
        }
    }

    private String formatTime(long timeInMillis) {
        long seconds = timeInMillis / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        seconds = seconds % 60;

        if (hours > 999) {
            // Fallback for extremely large times
            return ">999h";
        }

        StringBuilder timeBuilder = new StringBuilder();
        if (hours > 0) {
            timeBuilder.append(hours).append("h ");
        }
        if (minutes > 0 || hours > 0) { // Show minutes if hours are non-zero
            timeBuilder.append(minutes).append("m ");
        }
        timeBuilder.append(seconds).append("s");

        return timeBuilder.toString().trim(); // Trim any trailing spaces
    }

    private String generateProgressBar(double progressPercentage) {
        int totalBars = 30;
        int completedBars = (int) (progressPercentage / (100.0 / totalBars));
        StringBuilder progressBar = new StringBuilder();
        for (int i = 0; i < completedBars; i++) {
            progressBar.append(ansiSupported ? GREEN + "█" + RESET : "#");
        }
        for (int i = completedBars; i < totalBars; i++) {
            progressBar.append(ansiSupported ? "░" : "-");
        }
        return progressBar.toString();
    }

}
