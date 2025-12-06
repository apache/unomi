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

/**
 * A comprehensive JUnit test run listener that provides enhanced progress reporting
 * with visual elements, timing information, and motivational quotes during test execution.
 *
 * <p>This listener extends JUnit's {@link RunListener} to provide real-time feedback
 * about test execution progress. It features:</p>
 * <ul>
 *   <li>ASCII art logo display at test suite startup</li>
 *   <li>Real-time progress bar with percentage completion</li>
 *   <li>Colorized output (when ANSI is supported)</li>
 *   <li>Estimated time remaining calculations</li>
 *   <li>Test success/failure counters</li>
 *   <li>Top 10 slowest tests tracking and reporting</li>
 *   <li>Motivational quotes displayed at progress milestones</li>
 *   <li>CSV-formatted performance data output</li>
 * </ul>
 *
 * <p>The listener automatically detects ANSI color support based on the terminal
 * environment and adjusts output accordingly. When ANSI is not supported,
 * plain text output is used instead.</p>
 *
 * <p>Example usage in test configuration:</p>
 * <pre>{@code
 * JUnitCore core = new JUnitCore();
 * ProgressListener listener = new ProgressListener(totalTestCount, completedCounter);
 * core.addListener(listener);
 * core.run(testClasses);
 * }</pre>
 *
 * <p>The listener tracks test execution times and maintains a priority queue
 * of the slowest tests, which is reported at the end of the test run along
 * with CSV-formatted data for further analysis.</p>
 *
 * @author Apache Unomi
 * @since 3.0.0
 * @see org.junit.runner.notification.RunListener
 * @see org.junit.runner.Description
 * @see org.junit.runner.Result
 */
public class ProgressListener extends RunListener {

    /** ANSI escape code to reset text formatting */
    private static final String RESET = "\u001B[0m";
    /** ANSI escape code for green text color */
    private static final String GREEN = "\u001B[32m";
    /** ANSI escape code for yellow text color */
    private static final String YELLOW = "\u001B[33m";
    /** ANSI escape code for red text color */
    private static final String RED = "\u001B[31m";
    /** ANSI escape code for cyan text color */
    private static final String CYAN = "\u001B[36m";
    /** ANSI escape code for blue text color */
    private static final String BLUE = "\u001B[34m";

    /** Array of motivational quotes displayed at progress milestones */
    private static final String[] QUOTES = {
            "Success is not final, failure is not fatal: It is the courage to continue that counts. - Winston Churchill",
            "Believe you can and you're halfway there. - Theodore Roosevelt",
            "Don't watch the clock; do what it does. Keep going. - Sam Levenson",
            "It does not matter how slowly you go as long as you do not stop. - Confucius",
            "Hardships often prepare ordinary people for an extraordinary destiny. - C.S. Lewis"
    };

    /**
     * Inner class representing a test execution time record.
     * Used to track individual test performance for reporting the slowest tests.
     */
    private static class TestTime {
        /** The display name of the test */
        String name;
        /** The execution time in milliseconds */
        long time;

        /**
         * Creates a new test time record.
         *
         * @param name the display name of the test
         * @param time the execution time in milliseconds
         */
        TestTime(String name, long time) {
            this.name = name;
            this.time = time;
        }
    }

    /** Total number of tests to be executed */
    private final int totalTests;
    /** Thread-safe counter for completed tests */
    private final AtomicInteger completedTests;
    /** Thread-safe counter for successful tests */
    private final AtomicInteger successfulTests = new AtomicInteger(0);
    /** Thread-safe counter for failed tests */
    private final AtomicInteger failedTests = new AtomicInteger(0);
    /** Priority queue to track the slowest tests (limited to top 10) */
    private final PriorityQueue<TestTime> slowTests;
    /** Flag indicating whether ANSI color codes are supported in the terminal */
    private final boolean ansiSupported;
    /** Timestamp when the test suite started */
    private long startTime = System.currentTimeMillis();
    /** Timestamp when the current individual test started */
    private long startTestTime = System.currentTimeMillis();

    /**
     * Creates a new ProgressListener instance.
     *
     * @param totalTests the total number of tests that will be executed
     * @param completedTests a thread-safe counter that tracks the number of completed tests
     *                       (this should be shared with the test runner for accurate progress tracking)
     */
    public ProgressListener(int totalTests, AtomicInteger completedTests) {
        this.totalTests = totalTests;
        this.completedTests = completedTests;
        this.slowTests = new PriorityQueue<>((t1, t2) -> Long.compare(t1.time, t2.time));
        this.ansiSupported = isAnsiSupported();
    }

    /**
     * Determines if the current terminal supports ANSI color codes.
     *
     * @return true if ANSI colors are supported, false otherwise
     */
    private boolean isAnsiSupported() {
        String term = System.getenv("TERM");
        return System.console() != null && term != null && term.contains("xterm");
    }

    /**
     * Applies ANSI color codes to text if the terminal supports them.
     *
     * @param text the text to colorize
     * @param color the ANSI color code to apply
     * @return the colorized text if ANSI is supported, otherwise the original text
     */
    private String colorize(String text, String color) {
        if (ansiSupported) {
            return color + text + RESET;
        }
        return text;
    }

    /**
     * Called when the test run starts. Displays an ASCII art logo and welcome message.
     *
     * @param description the description of the test run
     */
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

    /**
     * Called when an individual test starts. Records the start time for timing calculations.
     *
     * @param description the description of the test that started
     */
    @Override
    public void testStarted(Description description) {
        startTestTime = System.currentTimeMillis();
    }

    /**
     * Called when an individual test finishes successfully. Updates counters and displays progress.
     *
     * @param description the description of the test that finished
     */
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

    /**
     * Called when a test fails. Updates failure counters and displays the failure message.
     *
     * @param failure the failure information
     */
    @Override
    public void testFailure(Failure failure) {
        successfulTests.decrementAndGet(); // Remove the previous success count for this test.
        failedTests.incrementAndGet();
        System.out.println(colorize("Test failed: " + failure.getDescription(), RED));
        displayProgress();
    }

    /**
     * Called when the entire test run finishes. Displays final statistics and performance data.
     *
     * @param result the final result of the test run
     */
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
        System.out.printf("Top 10 Slowest Tests:%n");
        // Prepare CSV data
        StringBuilder csvBuilder = new StringBuilder();
        csvBuilder.append("Rank,Test Name,Duration (ms)\n");

        AtomicInteger rank = new AtomicInteger(1);
        slowTests.stream()
                .sorted((t1, t2) -> Long.compare(t2.time, t1.time)) // Sort by descending order
                .limit(10)
                .forEach(test -> csvBuilder.append(String.format("%d,\"%s\",%d%n",
                        rank.getAndIncrement(), escapeCsv(test.name), test.time)));

        // Output CSV
        System.out.println(csvBuilder.toString());
        System.out.println("═══════════════════════════════════════════════════════════");

    }

    /**
     * Escapes special characters for CSV compatibility.
     *
     * @param value the string value to escape
     * @return the escaped string suitable for CSV output
     */
    private String escapeCsv(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * Displays the current progress of the test run including progress bar,
     * percentage completion, estimated time remaining, and success/failure counts.
     * Also displays motivational quotes at progress milestones.
     */
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

    /**
     * Formats a time duration in milliseconds into a human-readable string.
     *
     * @param timeInMillis the time duration in milliseconds
     * @return a formatted time string (e.g., "1h 23m 45s" or "2m 30s")
     */
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

    /**
     * Generates a visual progress bar based on the completion percentage.
     *
     * @param progressPercentage the completion percentage (0.0 to 100.0)
     * @return a string representation of the progress bar with appropriate colors
     */
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
