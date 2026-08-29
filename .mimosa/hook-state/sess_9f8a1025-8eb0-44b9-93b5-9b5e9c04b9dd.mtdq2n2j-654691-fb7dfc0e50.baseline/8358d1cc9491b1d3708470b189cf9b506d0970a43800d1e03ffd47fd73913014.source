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
package org.apache.unomi.rest.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Ensures the distribution cannot start with a known or blank admin/health password.
 * <p>
 * The launcher guards are <em>executed</em> here rather than grepped: a check whose text is present
 * but whose condition never matches would otherwise pass silently, which is exactly how the Windows
 * {@code KARAF_SCRIPT} quoting bug survived review.
 */
class ShippedAdminPasswordConfigTest {

    private static final String ROOT_PASSWORD_PROPERTY = "org.apache.unomi.security.root.password";
    private static final String HEALTHCHECK_PASSWORD_PROPERTY = "org.apache.unomi.healthcheck.password";

    /**
     * The two files this test executes. They double as the fingerprint of the repository root (see
     * {@link #locateRepoRoot()}): a directory containing both is the Unomi checkout, not some nested
     * copy or a same-named directory further up the filesystem.
     */
    private static final String SETENV_PATH = "package/src/main/resources/bin/setenv";
    private static final String ENTRYPOINT_PATH = "docker/src/main/docker/entrypoint.sh";

    /** First line of the guard to copy out of entrypoint.sh. */
    private static final String ENTRYPOINT_GUARD_START = "check_required_password()";
    /** Last line of the guard; the slice is meaningless unless this is actually reached. */
    private static final String ENTRYPOINT_GUARD_END = "UNOMI_SKIP_HEALTHCHECK_PASSWORD_CHECK || exit 1";

    /** Default stub launcher name, mirroring {@code bin/karaf}. */
    private static final String DEFAULT_KARAF_SCRIPT = "karaf";

    private static final Path REPO_ROOT = locateRepoRoot();

    // ---------------------------------------------------------------- shipped configuration

    @Test
    void usersProperties_hasNoKnownDefaultPasswordFallback() throws Exception {
        String content = Files.readString(repoFile("package/src/main/resources/etc/users.properties"));

        assertFalse(content.contains(":-karaf"), "users.properties must not default the karaf password to 'karaf'");
        assertFalse(content.contains(":-health"), "users.properties must not default the health password to 'health'");
        assertTrue(content.contains("${" + ROOT_PASSWORD_PROPERTY + "}"));
        assertTrue(content.contains("${" + HEALTHCHECK_PASSWORD_PROPERTY + "}"));
    }

    @Test
    void customSystemProperties_requiresRootPasswordEnvWithoutKnownDefault() throws Exception {
        List<String> securityLines = Files.readAllLines(repoFile("package/src/main/resources/etc/custom.system.properties"))
                .stream()
                .filter(line -> line.contains(ROOT_PASSWORD_PROPERTY) || line.contains(HEALTHCHECK_PASSWORD_PROPERTY))
                .collect(Collectors.toList());

        assertFalse(securityLines.isEmpty());
        for (String line : securityLines) {
            assertFalse(line.contains(":-karaf"), "root password must not fall back to karaf: " + line);
            assertFalse(line.contains(":-health"), "health password must not fall back to health: " + line);
        }
    }


    // ---------------------------------------------------------------- bin/setenv, executed

    @Test
    void setenv_refusesToStartWhenPasswordsMissing(@TempDir Path karafHome) throws Exception {
        assumeTrue(hasPosixShell(), "requires a POSIX shell");
        installFakeKarafHome(karafHome, DEFAULT_KARAF_SCRIPT, "${env:UNOMI_ROOT_PASSWORD}", "${env:UNOMI_HEALTHCHECK_PASSWORD}");

        Result missing = runLauncher(karafHome, DEFAULT_KARAF_SCRIPT, new HashMap<>());
        assertEquals(1, missing.exitCode, "setenv must refuse to start without passwords:\n" + missing.output);
        assertFalse(missing.output.contains("LAUNCHED"), "the launcher must not be reached");
        assertTrue(missing.output.contains("UNOMI_ROOT_PASSWORD is not set"), missing.output);
    }

    /**
     * The outer {@code case "${KARAF_SCRIPT}"} in bin/setenv lists every shipped script that boots a
     * server: {@code bin/karaf}, {@code bin/start} and {@code bin/karaf server} all reach
     * {@code org.apache.karaf.main.Main}. Only {@code karaf} used to be exercised here, so narrowing
     * that list to a single entry would have gone unnoticed - now each entry has its own test case.
     */
    @ParameterizedTest(name = "KARAF_SCRIPT={0}")
    @ValueSource(strings = {"karaf", "start", "server"})
    void setenv_refusesToStartForEveryServerStartingScript(String karafScript, @TempDir Path tempDir) throws Exception {
        assumeTrue(hasPosixShell(), "requires a POSIX shell");
        Path karafHome = Files.createDirectories(tempDir.resolve(karafScript));
        installFakeKarafHome(karafHome, karafScript, "${env:UNOMI_ROOT_PASSWORD}", "${env:UNOMI_HEALTHCHECK_PASSWORD}");

        Result missing = runLauncher(karafHome, karafScript, new HashMap<>());
        assertEquals(1, missing.exitCode,
                "bin/" + karafScript + " starts a server, so it must refuse to run without passwords:\n" + missing.output);
        assertFalse(missing.output.contains("LAUNCHED"), "the launcher must not be reached for KARAF_SCRIPT=" + karafScript);
        assertTrue(missing.output.contains("UNOMI_ROOT_PASSWORD is not set"), missing.output);
    }

    /**
     * The dedicated {@code bin/stop}, {@code bin/status}, {@code bin/client} and {@code bin/shell}
     * scripts set {@code KARAF_SCRIPT} to their own name. None of them starts a server - they talk to
     * an already running one - so the outer case must keep excluding them, otherwise stopping an
     * instance would require the passwords used to start it.
     */
    @ParameterizedTest(name = "KARAF_SCRIPT={0}")
    @ValueSource(strings = {"stop", "status", "client", "shell"})
    void setenv_doesNotGuardScriptsThatOnlyTalkToARunningServer(String karafScript, @TempDir Path tempDir) throws Exception {
        assumeTrue(hasPosixShell(), "requires a POSIX shell");
        Path karafHome = Files.createDirectories(tempDir.resolve(karafScript));
        installFakeKarafHome(karafHome, karafScript, "${env:UNOMI_ROOT_PASSWORD}", "${env:UNOMI_HEALTHCHECK_PASSWORD}");

        Result result = runLauncher(karafHome, karafScript, new HashMap<>());
        assertEquals(0, result.exitCode, "bin/" + karafScript + " must not require the passwords:\n" + result.output);
        assertTrue(result.output.contains("LAUNCHED"), result.output);
    }

    @Test
    void setenv_startsWhenPasswordsProvided(@TempDir Path karafHome) throws Exception {
        assumeTrue(hasPosixShell(), "requires a POSIX shell");
        installFakeKarafHome(karafHome, DEFAULT_KARAF_SCRIPT, "${env:UNOMI_ROOT_PASSWORD}", "${env:UNOMI_HEALTHCHECK_PASSWORD}");

        Map<String, String> env = new HashMap<>();
        env.put("UNOMI_ROOT_PASSWORD", "a-strong-password");
        env.put("UNOMI_HEALTHCHECK_PASSWORD", "a-strong-health-password");

        Result provided = runLauncher(karafHome, DEFAULT_KARAF_SCRIPT, env);
        assertEquals(0, provided.exitCode, provided.output);
        assertTrue(provided.output.contains("LAUNCHED"), provided.output);
    }

    /**
     * The escape hatch only suppresses the environment-variable check. Configuring the property
     * directly is a supported way to start, and must not be reported as an error.
     */
    @Test
    void setenv_acceptsPasswordsConfiguredInPropertiesFile(@TempDir Path karafHome) throws Exception {
        assumeTrue(hasPosixShell(), "requires a POSIX shell");
        installFakeKarafHome(karafHome, DEFAULT_KARAF_SCRIPT, "configured-root", "configured-health");

        Result configured = runLauncher(karafHome, DEFAULT_KARAF_SCRIPT, new HashMap<>());
        assertEquals(0, configured.exitCode, configured.output);
        assertTrue(configured.output.contains("LAUNCHED"), configured.output);
    }

    /**
     * Claiming the password is set elsewhere, while leaving it blank everywhere, is the dangerous
     * case: it must warn rather than pass silently.
     */
    @Test
    void setenv_warnsWhenSkipFlagHidesABlankPassword(@TempDir Path karafHome) throws Exception {
        assumeTrue(hasPosixShell(), "requires a POSIX shell");
        installFakeKarafHome(karafHome, DEFAULT_KARAF_SCRIPT, "${env:UNOMI_ROOT_PASSWORD}", "${env:UNOMI_HEALTHCHECK_PASSWORD}");

        Map<String, String> env = new HashMap<>();
        env.put("UNOMI_SKIP_ROOT_PASSWORD_CHECK", "true");
        env.put("UNOMI_SKIP_HEALTHCHECK_PASSWORD_CHECK", "true");

        Result skipped = runLauncher(karafHome, DEFAULT_KARAF_SCRIPT, env);
        assertEquals(0, skipped.exitCode, skipped.output);
        assertTrue(skipped.output.contains("WARNING"), "a bypassed check must still warn:\n" + skipped.output);
    }

    /**
     * {@code bin/karaf stop} and {@code bin/karaf status} keep {@code KARAF_SCRIPT=karaf} but replace
     * the main class with {@code Main.Stop} / {@code Main.Status}: no server is started, so no
     * password is needed. Requiring one would make an instance impossible to shut down cleanly from a
     * shell that no longer has the startup environment.
     */
    @ParameterizedTest(name = "bin/karaf {0}")
    @ValueSource(strings = {"stop", "status"})
    void setenv_doesNotBlockSubcommandsThatDoNotStartAServer(String subcommand, @TempDir Path tempDir) throws Exception {
        assumeTrue(hasPosixShell(), "requires a POSIX shell");
        Path karafHome = Files.createDirectories(tempDir.resolve(subcommand));
        installFakeKarafHome(karafHome, DEFAULT_KARAF_SCRIPT, "${env:UNOMI_ROOT_PASSWORD}", "${env:UNOMI_HEALTHCHECK_PASSWORD}");

        Result result = runLauncher(karafHome, DEFAULT_KARAF_SCRIPT, new HashMap<>(), subcommand);
        assertEquals(0, result.exitCode, "'karaf " + subcommand + "' must not require the passwords:\n" + result.output);
        assertTrue(result.output.contains("LAUNCHED"), result.output);
    }

    /**
     * Regression guard for a real bug: the inner skip list once read {@code stop|status|client|shell},
     * which silently disabled the password check for {@code bin/karaf client} and
     * {@code bin/karaf shell}. Those subcommands are <em>not</em> the {@code bin/client} /
     * {@code bin/shell} remote consoles - the karaf script does not special-case them, so they fall
     * through to {@code org.apache.karaf.main.Main} and boot a complete server, admin account
     * included. The same is true of any argument the script does not recognise. Only {@code stop} and
     * {@code status} may skip the check; everything else here must still be refused.
     */
    @ParameterizedTest(name = "bin/karaf {0}")
    @ValueSource(strings = {"client", "shell", "console", "--an-argument-karaf-does-not-know"})
    void setenv_stillGuardsSubcommandsThatBootAFullServer(String subcommand, @TempDir Path tempDir) throws Exception {
        assumeTrue(hasPosixShell(), "requires a POSIX shell");
        Path karafHome = Files.createDirectories(tempDir.resolve(subcommand.replace("-", "_")));
        installFakeKarafHome(karafHome, DEFAULT_KARAF_SCRIPT, "${env:UNOMI_ROOT_PASSWORD}", "${env:UNOMI_HEALTHCHECK_PASSWORD}");

        Result result = runLauncher(karafHome, DEFAULT_KARAF_SCRIPT, new HashMap<>(), subcommand);
        assertEquals(1, result.exitCode,
                "'karaf " + subcommand + "' starts a full server, so it must refuse to run without passwords:\n" + result.output);
        assertFalse(result.output.contains("LAUNCHED"), "the launcher must not be reached for 'karaf " + subcommand + "'");
        assertTrue(result.output.contains("UNOMI_ROOT_PASSWORD is not set"), result.output);
    }

    // ---------------------------------------------------------------- docker entrypoint, executed

    @Test
    void entrypoint_refusesToStartWhenPasswordsMissing(@TempDir Path workDir) throws Exception {
        assumeTrue(hasPosixShell(), "requires a POSIX shell");
        Path guard = extractEntrypointPasswordGuard(workDir);

        assertEquals(1, runShell(guard, workDir, new HashMap<>()).exitCode,
                "the Docker entrypoint must exit non-zero without passwords");

        Map<String, String> env = new HashMap<>();
        env.put("UNOMI_ROOT_PASSWORD", "a-strong-password");
        env.put("UNOMI_HEALTHCHECK_PASSWORD", "a-strong-health-password");
        Result provided = runShell(guard, workDir, env);
        assertEquals(0, provided.exitCode, provided.output);
        assertTrue(provided.output.contains("GUARD-PASSED"), provided.output);
    }

    // ---------------------------------------------------------------- setenv.bat regression guard

    /**
     * {@code karaf.bat} sets {@code KARAF_SCRIPT} with the quotes included in the value
     * ({@code SET KARAF_SCRIPT="karaf.bat"}), so comparing {@code "%KARAF_SCRIPT%"} against
     * {@code "karaf.bat"} never matches and the whole check is skipped. The quotes must be stripped
     * before comparing. This cannot be executed on a POSIX CI machine, so assert the shape instead.
     */
    @Test
    void setenvBat_stripsQuotesBeforeComparingKarafScript() throws Exception {
        String content = Files.readString(repoFile("package/src/main/resources/bin/setenv.bat"));

        assertTrue(content.contains("%KARAF_SCRIPT:\"=%"),
                "setenv.bat must strip the quotes karaf.bat embeds in KARAF_SCRIPT");
        assertFalse(content.contains("\"%KARAF_SCRIPT%\"==\"karaf.bat\""),
                "comparing the raw KARAF_SCRIPT against karaf.bat never matches");
        assertTrue(content.contains("UNOMI_ROOT_PASSWORD") && content.contains("UNOMI_HEALTHCHECK_PASSWORD"),
                "setenv.bat must check both passwords");
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Finds the repository root by walking up from the working directory until an ancestor holds
     * <em>both</em> shipped launcher scripts this test executes. Matching on a single relative path
     * would let a nested checkout (or any unrelated directory that happens to contain a
     * {@code package/} tree) win, and the test would then silently assert against the wrong sources.
     */
    private static Path locateRepoRoot() {
        Path start = Paths.get("").toAbsolutePath().normalize();
        for (Path candidate = start; candidate != null; candidate = candidate.getParent()) {
            if (Files.isRegularFile(candidate.resolve(SETENV_PATH))
                    && Files.isRegularFile(candidate.resolve(ENTRYPOINT_PATH))) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not locate the Unomi repository root from " + start
                + " (looked for an ancestor containing both " + SETENV_PATH + " and " + ENTRYPOINT_PATH + ")");
    }

    /**
     * Resolves a repository-relative path against the detected repository root, so the test works
     * regardless of which module directory the build runs it from.
     */
    private static Path repoFile(String relativePath) {
        Path resolved = REPO_ROOT.resolve(relativePath);
        if (!Files.exists(resolved)) {
            throw new IllegalStateException("Could not locate " + relativePath + " under repository root " + REPO_ROOT);
        }
        return resolved;
    }

    private static boolean hasPosixShell() {
        return !System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /**
     * Builds a throwaway Karaf layout containing the real {@code bin/setenv} plus a stub launcher
     * that mimics how the shipped scripts source it. {@code karafScript} names the stub and the value
     * it exports as {@code KARAF_SCRIPT}, so tests can reproduce {@code bin/karaf}, {@code bin/start},
     * {@code bin/stop}, ... rather than only ever exercising {@code karaf}.
     */
    private static void installFakeKarafHome(Path karafHome, String karafScript, String rootPassword, String healthPassword)
            throws IOException {
        Path bin = Files.createDirectories(karafHome.resolve("bin"));
        Path etc = Files.createDirectories(karafHome.resolve("etc"));

        Files.copy(repoFile(SETENV_PATH), bin.resolve("setenv"));
        Files.write(etc.resolve("custom.system.properties"),
                (ROOT_PASSWORD_PROPERTY + "=" + rootPassword + "\n"
                        + HEALTHCHECK_PASSWORD_PROPERTY + "=" + healthPassword + "\n").getBytes(StandardCharsets.UTF_8));

        // Mirrors apache-karaf/bin/<script>: export KARAF_SCRIPT, then source setenv at top level.
        Path launcher = bin.resolve(karafScript);
        Files.write(launcher, ("#!/bin/sh\n"
                + "KARAF_SCRIPT=\"" + karafScript + "\"\n"
                + "export KARAF_SCRIPT\n"
                + ". \"$(dirname \"$0\")/setenv\"\n"
                + "echo LAUNCHED\n").getBytes(StandardCharsets.UTF_8));
        launcher.toFile().setExecutable(true);
    }

    /**
     * Copies the entrypoint's password guard into a standalone script, so the test does not have to
     * run the rest of the container bootstrap (which needs a search engine).
     */
    private static Path extractEntrypointPasswordGuard(Path workDir) throws IOException {
        List<String> lines = Files.readAllLines(repoFile(ENTRYPOINT_PATH));
        StringBuilder guard = new StringBuilder("#!/bin/sh\n");
        boolean capturing = false;
        boolean terminated = false;
        for (String line : lines) {
            if (line.startsWith(ENTRYPOINT_GUARD_START)) {
                capturing = true;
            }
            if (capturing) {
                guard.append(line).append('\n');
            }
            if (capturing && line.contains(ENTRYPOINT_GUARD_END)) {
                terminated = true;
                break;
            }
        }
        assertTrue(capturing, "entrypoint.sh must define the guard; start marker '" + ENTRYPOINT_GUARD_START
                + "' was not found in " + ENTRYPOINT_PATH);
        // Without this, a reformatted entrypoint.sh would make the slice run to end-of-file: the test
        // would still "pass" while executing something entirely different from the guard.
        assertTrue(terminated, "the guard slice never reached its terminating line '" + ENTRYPOINT_GUARD_END
                + "' in " + ENTRYPOINT_PATH + " - update the marker, this test is no longer testing the guard");
        guard.append("echo GUARD-PASSED\n");

        Path script = workDir.resolve("entrypoint-guard.sh");
        Files.write(script, guard.toString().getBytes(StandardCharsets.UTF_8));
        return script;
    }

    private static Result runLauncher(Path karafHome, String karafScript, Map<String, String> env, String... args) throws Exception {
        String[] command = new String[args.length + 2];
        command[0] = "/bin/sh";
        command[1] = karafHome.resolve("bin").resolve(karafScript).toString();
        System.arraycopy(args, 0, command, 2, args.length);
        return run(new ProcessBuilder(command).directory(karafHome.toFile()), env);
    }

    private static Result runShell(Path script, Path workDir, Map<String, String> env) throws Exception {
        return run(new ProcessBuilder("/bin/sh", script.toString()).directory(workDir.toFile()), env);
    }

    private static Result run(ProcessBuilder builder, Map<String, String> env) throws Exception {
        Map<String, String> environment = builder.environment();

        // Inherit the JVM's environment - bin/setenv shells out to dirname, sed, grep and tail, which
        // do not live under /usr/bin:/bin on every platform (NixOS, minimal images), and a hardcoded
        // PATH turns that into a confusing failure instead of a working test.
        //
        // But strip every UNOMI_* variable first: an ambient UNOMI_ROOT_PASSWORD on a developer's
        // machine would otherwise satisfy the "missing password" cases and make them pass by accident,
        // which is the one property these tests cannot afford to lose. KARAF_* goes too, because
        // KARAF_ETC would redirect the script at the developer's own etc/ directory.
        List<String> inherited = new ArrayList<>(environment.keySet());
        for (String name : inherited) {
            if (name.startsWith("UNOMI_") || name.startsWith("KARAF_")) {
                environment.remove(name);
            }
        }
        environment.putIfAbsent("PATH", "/usr/bin:/bin");
        environment.putAll(env);
        builder.redirectErrorStream(true);

        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(60, TimeUnit.SECONDS), "script did not terminate");
        return new Result(process.exitValue(), output);
    }

    private static final class Result {
        private final int exitCode;
        private final String output;

        private Result(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }
}
