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
package org.apache.unomi.router.core.route;

import org.apache.camel.component.jackson.JacksonDataFormat;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.unomi.router.api.ExportConfiguration;
import org.apache.unomi.router.api.ImportConfiguration;
import org.apache.unomi.router.api.ProfileToImport;
import org.apache.unomi.router.api.RouterConstants;
import org.apache.unomi.router.api.services.ImportExportConfigurationService;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.apache.unomi.router.core.route.RouterTestFixtures.TENANT;
import static org.apache.unomi.router.core.route.RouterTestFixtures.fileUri;
import static org.apache.unomi.router.core.route.RouterTestFixtures.noOpProfileService;
import static org.apache.unomi.router.core.route.RouterTestFixtures.recurrentExport;
import static org.apache.unomi.router.core.route.RouterTestFixtures.recurrentImport;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A recurrent import configuration names a {@code source}, and a recurrent export configuration
 * names a {@code destination}. Both are used as Apache Camel endpoint URIs. When the URI uses the
 * {@code file} scheme, it must resolve <em>inside</em> one of the base directories the deployment
 * permits: a {@code file} endpoint that resolves anywhere else is refused, and its route is not
 * built.
 *
 * <p>Containment covers the directory named by the URI <em>and</em> every path-bearing option it
 * carries ({@code fileName}, {@code move}, {@code moveFailed}, {@code preMove}, {@code doneFileName},
 * ...) — validating only the directory part would leave
 * {@code file:///permitted/?fileName=../../elsewhere} open. It is decided on what Camel will use, so
 * the URI is read the way Camel reads it: percent-encoded option names, {@code RAW()} values that
 * carry an ampersand, and the File Language expressions a path-bearing option may hold.
 *
 * <p>Selection options ({@code include}, {@code antInclude}) are patterns matched against the files
 * the directory already offers, not paths Camel resolves, and are not held to containment.
 *
 * <p>Remote schemes ({@code ftp}, {@code sftp}, {@code ftps}) address a remote server, so their
 * directory and their path-bearing options are remote and are not subject to directory containment;
 * the scheme allow-list keeps governing them. {@code localWorkDirectory} is the exception: it names a
 * local directory, which the remote components stage their downloads in, and it is confined whatever
 * the scheme.
 *
 * <p>These tests exercise route <em>construction</em>, which is where a configuration is turned into
 * a live route: a configuration whose endpoint is refused must leave no route behind, whether it
 * arrives through the REST API or was already persisted before the deployment was configured.
 */
public class FileEndpointContainmentTest {

    /** The shipped default. The containment rules must hold while {@code file} is an allowed scheme. */
    private static final String DEFAULT_ALLOWED_ENDPOINTS = "file,ftp,sftp,ftps";

    private static final Map<String, String> NO_KAFKA = new HashMap<>();

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private DefaultCamelContext camelContext;

    /** The deployment's import base directory. Each tenant is confined to a subdirectory of it. */
    private File importRoot;

    /** {@code importRoot/acme}, the directory tenant {@code acme} may use. */
    private File permittedImportDir;

    /** The deployment's export base directory. */
    private File exportRoot;

    /** {@code exportRoot/acme}. */
    private File permittedExportDir;

    /** A directory the deployment never permitted — the "arbitrary directory" of the defect. */
    private File arbitraryDir;

    /** A sibling whose path shares a textual prefix with the permitted import directory. */
    private File permittedImportDirLookalike;

    @Before
    public void setUp() throws Exception {
        importRoot = tmp.newFolder("permitted-import");
        permittedImportDir = new File(importRoot, TENANT);
        assertTrue(permittedImportDir.mkdirs());
        exportRoot = tmp.newFolder("permitted-export");
        permittedExportDir = new File(exportRoot, TENANT);
        assertTrue(permittedExportDir.mkdirs());
        arbitraryDir = tmp.newFolder("arbitrary");
        permittedImportDirLookalike = tmp.newFolder("permitted-import-evil");
        camelContext = new DefaultCamelContext();
    }

    @After
    public void tearDown() throws Exception {
        camelContext.stop();
    }

    // ---------------------------------------------------------------------------------------------
    // Import — the configured source is read by Unomi
    // ---------------------------------------------------------------------------------------------

    @Test
    public void importRouteIsBuiltWhenSourceIsInsidePermittedBaseDir() throws Exception {
        addImportRoutes(recurrentImport("in-bounds", fileUri(permittedImportDir, "?fileName=profiles.csv")));

        assertRouteBuilt("in-bounds", "a source inside the permitted base directory is legitimate");
    }

    @Test
    public void importRouteIsBuiltWhenSourceIsInAnExistingSubdirectoryOfPermittedBaseDir() throws Exception {
        File dropDir = new File(permittedImportDir, "incoming");
        assertTrue("could not prepare the test fixture", dropDir.mkdir());

        addImportRoutes(recurrentImport("subdirectory", fileUri(dropDir, "?fileName=profiles.csv")));

        assertRouteBuilt("subdirectory", "containment is recursive — a source at any depth under the base directory is legitimate");
    }

    @Test
    public void importRouteIsRefusedWhenSourceIsASymlinkPointingOutsidePermittedBaseDir() throws Exception {
        File link = new File(permittedImportDir, "elsewhere");
        try {
            Files.createSymbolicLink(link.toPath(), arbitraryDir.toPath());
        } catch (IOException | UnsupportedOperationException e) {
            Assume.assumeNoException("this file system does not support symbolic links", e);
        }

        addImportRoutes(recurrentImport("symlink", fileUri(link, "?fileName=profiles.csv")));

        assertRouteRefused("symlink", "the source leaves the permitted base directory once symbolic links are resolved");
    }

    @Test
    public void importRouteIsRefusedWhenSourceLeavesPermittedBaseDirThroughASymlinkAndAParentSegment() throws Exception {
        File outsideChild = new File(arbitraryDir, "child");
        assertTrue("could not prepare the test fixture", outsideChild.mkdir());
        File link = new File(permittedImportDir, "link");
        try {
            Files.createSymbolicLink(link.toPath(), outsideChild.toPath());
        } catch (IOException | UnsupportedOperationException e) {
            Assume.assumeNoException("this file system does not support symbolic links", e);
        }

        // the file system expands the link, then applies the parent segment to its target: the source
        // is the arbitrary directory. Collapsing the segment first would read it as the base directory.
        addImportRoutes(recurrentImport("symlink-parent", fileUri(new File(link, ".."), "?fileName=profiles.csv")));

        assertRouteRefused("symlink-parent",
                "a parent segment applies to the target of the link that precedes it, not to the link's own parent");
    }

    @Test
    public void moveFailedIsAppendedAsTheFirstOptionOfASourceThatCarriesNone() throws Exception {
        addImportRoutes(recurrentImport("first-option", fileUri(permittedImportDir, "")));

        assertEquals("the option must open the query, or it becomes part of the directory name",
                fileUri(permittedImportDir, "?initialDelay=0&moveFailed=.error"), builtEndpointUri("first-option"));
    }

    @Test
    public void moveFailedIsAppendedToASourceThatAlreadyCarriesAQuery() throws Exception {
        addImportRoutes(recurrentImport("next-option", fileUri(permittedImportDir, "?fileName=profiles.csv")));

        assertEquals("an existing query takes the option as another parameter",
                fileUri(permittedImportDir, "?fileName=profiles.csv&initialDelay=0&moveFailed=.error"),
                builtEndpointUri("next-option"));
    }

    @Test
    public void importRouteIsBuiltWhenSourceCarriesNoOption() throws Exception {
        // the router appends moveFailed itself; a source with no query has no separator to append to,
        // and the option would otherwise become part of the directory the endpoint names
        addImportRoutes(recurrentImport("no-option", fileUri(permittedImportDir, "")));

        assertRouteBuilt("no-option", "a source may name a directory and nothing else");
    }

    @Test
    public void importRouteIsBuiltWhenSourceIsASymlinkPointingInsidePermittedBaseDir() throws Exception {
        File target = new File(permittedImportDir, "incoming");
        assertTrue("could not prepare the test fixture", target.mkdir());
        File link = new File(permittedImportDir, "shortcut");
        try {
            Files.createSymbolicLink(link.toPath(), target.toPath());
        } catch (IOException | UnsupportedOperationException e) {
            Assume.assumeNoException("this file system does not support symbolic links", e);
        }

        addImportRoutes(recurrentImport("symlink-inside", fileUri(link, "?fileName=profiles.csv")));

        assertRouteBuilt("symlink-inside", "resolving a link must not refuse one that stays inside the base directory");
    }

    @Test
    public void importRouteIsBuiltWhenThePermittedBaseDirIsItselfASymlink() throws Exception {
        File link = new File(tmp.getRoot(), "permitted-import-link");
        try {
            Files.createSymbolicLink(link.toPath(), importRoot.toPath());
        } catch (IOException | UnsupportedOperationException e) {
            Assume.assumeNoException("this file system does not support symbolic links", e);
        }

        // the deployment names the link, the configuration names the directory it points at
        addImportRoutesInto(link, recurrentImport("base-dir-link", fileUri(permittedImportDir, "?fileName=profiles.csv")));

        assertRouteBuilt("base-dir-link",
                "both sides are resolved, so naming the same directory by two paths is the same directory");
    }

    @Test
    public void importRouteIsRefusedWhenAnOptionLeavesThroughASymlinkAndAParentSegment() throws Exception {
        File outsideChild = new File(arbitraryDir, "child");
        assertTrue("could not prepare the test fixture", outsideChild.mkdir());
        File link = new File(permittedImportDir, "option-link");
        try {
            Files.createSymbolicLink(link.toPath(), outsideChild.toPath());
        } catch (IOException | UnsupportedOperationException e) {
            Assume.assumeNoException("this file system does not support symbolic links", e);
        }

        addImportRoutes(recurrentImport("option-symlink-parent",
                fileUri(permittedImportDir, "?fileName=profiles.csv&move=option-link/..")));

        assertRouteRefused("option-symlink-parent",
                "a path-bearing option is resolved the same way the endpoint directory is");
    }

    @Test
    public void importRouteIsRefusedWhenSourceClimbsAboveTheRoot() throws Exception {
        addImportRoutes(recurrentImport("above-root", "file:///../../../../../../../../etc?fileName=profiles.csv"));

        assertRouteRefused("above-root",
                "more parent segments than the path has components is a refusal, not an exception");
    }

    @Test
    public void importRouteIsRefusedWhenSourceIsOutsidePermittedBaseDir() throws Exception {
        addImportRoutes(recurrentImport("arbitrary-dir", fileUri(arbitraryDir, "?fileName=profiles.csv")));

        assertRouteRefused("arbitrary-dir", "the source directory is not one the deployment permits");
    }

    @Test
    public void importRouteIsRefusedWhenSourceEscapesPermittedBaseDirWithParentSegments() throws Exception {
        String escaping = fileUri(permittedImportDir, "/../" + arbitraryDir.getName() + "?fileName=profiles.csv");

        addImportRoutes(recurrentImport("dot-dot", escaping));

        assertRouteRefused("dot-dot", "the source resolves outside the permitted base directory once normalized");
    }

    @Test
    public void importRouteIsRefusedWhenSourceEscapesPermittedBaseDirWithEncodedParentSegments() throws Exception {
        String escaping = fileUri(permittedImportDir, "/%2e%2e/" + arbitraryDir.getName() + "?fileName=profiles.csv");

        addImportRoutes(recurrentImport("encoded-dot-dot", escaping));

        assertRouteRefused("encoded-dot-dot", "percent-encoded parent segments must be decoded before containment is decided");
    }

    @Test
    public void importRouteIsRefusedWhenSourceDirectoryOnlySharesAPrefixWithPermittedBaseDir() throws Exception {
        addImportRoutes(recurrentImport("lookalike", fileUri(permittedImportDirLookalike, "?fileName=profiles.csv")));

        assertRouteRefused("lookalike", "containment compares path components, not string prefixes");
    }

    @Test
    public void importRouteIsRefusedWhenFileNameOptionEscapesPermittedBaseDir() throws Exception {
        addImportRoutes(recurrentImport("filename-escape",
                fileUri(permittedImportDir, "?fileName=../" + arbitraryDir.getName() + "/profiles.csv")));

        assertRouteRefused("filename-escape", "fileName carries a path and must be contained too");
    }

    @Test
    public void importRouteIsRefusedWhenMoveOptionEscapesPermittedBaseDir() throws Exception {
        addImportRoutes(recurrentImport("move-escape",
                fileUri(permittedImportDir, "?fileName=profiles.csv&move=../" + arbitraryDir.getName())));

        assertRouteRefused("move-escape", "move carries a path and must be contained too");
    }

    @Test
    public void importRouteIsRefusedWhenMoveFailedOptionEscapesPermittedBaseDir() throws Exception {
        addImportRoutes(recurrentImport("movefailed-escape",
                fileUri(permittedImportDir, "?fileName=profiles.csv&moveFailed=../" + arbitraryDir.getName())));

        assertRouteRefused("movefailed-escape",
                "moveFailed carries a path, and the route builder appends one of its own — every occurrence must be contained");
    }

    @Test
    public void importRouteIsRefusedWhenPreMoveOptionEscapesPermittedBaseDir() throws Exception {
        addImportRoutes(recurrentImport("premove-escape",
                fileUri(permittedImportDir, "?fileName=profiles.csv&preMove=../" + arbitraryDir.getName())));

        assertRouteRefused("premove-escape", "preMove carries a path and must be contained too");
    }

    @Test
    public void importRouteIsRefusedWhenDoneFileNameOptionEscapesPermittedBaseDir() throws Exception {
        addImportRoutes(recurrentImport("donefilename-escape",
                fileUri(permittedImportDir, "?fileName=profiles.csv&doneFileName=../" + arbitraryDir.getName() + "/done")));

        assertRouteRefused("donefilename-escape", "doneFileName carries a path and must be contained too");
    }

    @Test
    public void exportRouteIsRefusedWhenMoveExistingOptionEscapesPermittedBaseDir() throws Exception {
        addExportRoutes(recurrentExport("moveexisting-escape", fileUri(permittedExportDir,
                "?fileName=profiles.csv&fileExist=Move&moveExisting=../" + arbitraryDir.getName())));

        assertRouteRefused("moveexisting-escape", "moveExisting says where the file already there is moved to");
    }

    @Test
    public void exportRouteIsRefusedWhenTempPrefixOptionEscapesPermittedBaseDir() throws Exception {
        addExportRoutes(recurrentExport("tempprefix-escape", fileUri(permittedExportDir,
                "?fileName=profiles.csv&tempPrefix=../" + arbitraryDir.getName() + "/")));

        assertRouteRefused("tempprefix-escape", "tempPrefix is put in front of the name, directories included");
    }

    @Test
    public void importRouteIsRefusedWhenPathBearingOptionIsWrappedInRaw() throws Exception {
        addImportRoutes(recurrentImport("raw-escape",
                fileUri(permittedImportDir, "?fileName=RAW(../" + arbitraryDir.getName() + "/profiles.csv)")));

        assertRouteRefused("raw-escape", "RAW() only tells Camel not to decode the value — the path it carries is used as-is");
    }

    @Test
    public void importRouteIsBuiltWhenMoveOptionIsRelativeAndStaysInsidePermittedBaseDir() throws Exception {
        addImportRoutes(recurrentImport("relative-move",
                fileUri(permittedImportDir, "?fileName=profiles.csv&move=.done")));

        assertRouteBuilt("relative-move", "a relative move target inside the base directory is how the feature is normally used");
    }

    @Test
    public void importRouteIsRefusedWhenPathBearingOptionNameIsPercentEncoded() throws Exception {
        addImportRoutes(recurrentImport("encoded-option-name",
                fileUri(permittedImportDir, "?file%4Eame=../" + arbitraryDir.getName() + "/profiles.csv")));

        assertRouteRefused("encoded-option-name",
                "Camel decodes an option name before it binds it, so 'file%4Eame' is the fileName option");
    }

    @Test
    public void importRouteIsRefusedWhenRawOptionValueCarriesTheEscapeBehindAnAmpersand() throws Exception {
        addImportRoutes(recurrentImport("raw-ampersand",
                fileUri(permittedImportDir, "?fileName=RAW(x&/../../" + arbitraryDir.getName() + "/profiles.csv)")));

        assertRouteRefused("raw-ampersand",
                "a RAW value ends at the marker that closes it, so the ampersand it carries does not start a new option");
    }

    @Test
    public void importRouteIsRefusedWhenMoveOptionUsesAnExpressionThatLeavesPermittedBaseDir() throws Exception {
        addImportRoutes(recurrentImport("expression-escape",
                fileUri(permittedImportDir, "?fileName=profiles.csv&move=${file:parent}/../" + arbitraryDir.getName())));

        assertRouteRefused("expression-escape",
                "Camel evaluates move as an expression, and this one sends the consumed file to a sibling directory");
    }

    @Test
    public void importRouteIsRefusedWhenMoveOptionUsesAnExpressionThatCannotBeValidated() throws Exception {
        addImportRoutes(recurrentImport("opaque-expression",
                fileUri(permittedImportDir, "?fileName=profiles.csv&move=${header.destination}")));

        assertRouteRefused("opaque-expression", "a header can hold any path at all, so there is nothing left to validate");
    }

    @Test
    public void importRouteIsBuiltWhenMoveOptionUsesTheParentExpressionAndStaysInsidePermittedBaseDir() throws Exception {
        addImportRoutes(recurrentImport("parent-expression",
                fileUri(permittedImportDir, "?fileName=profiles.csv&move=${file:parent}/.done/${file:onlyname}")));

        assertRouteBuilt("parent-expression", "moving a consumed file next to itself is how the feature is normally used");
    }

    @Test
    public void importRouteIsBuiltWhenSelectionPatternsAreNotPaths() throws Exception {
        addImportRoutes(recurrentImport("selection-patterns",
                fileUri(permittedImportDir, "?include=..&antInclude=**/*.csv&consumer.delay=10m")));

        assertRouteBuilt("selection-patterns",
                "a selection pattern is matched against the files the directory offers, so '..' asks for a two-character "
                        + "name — it is not a path Camel resolves, and holding it to containment only refuses patterns");
    }

    @Test
    public void importRouteIsRefusedWhenSourceIsADanglingSymlinkInsidePermittedBaseDir() throws Exception {
        File link = new File(permittedImportDir, "dangling");
        try {
            Files.createSymbolicLink(link.toPath(), new File(tmp.getRoot(), "not-created-yet").toPath());
        } catch (IOException | UnsupportedOperationException e) {
            Assume.assumeNoException("this file system does not support symbolic links", e);
        }

        addImportRoutes(recurrentImport("dangling-symlink", fileUri(link, "?fileName=profiles.csv")));

        assertRouteRefused("dangling-symlink",
                "a link whose target cannot be resolved would leave the base directory as soon as its target is created");
    }

    @Test
    public void unusablePathIsSkippedWithoutPreventingTheOtherRoutesFromBeingBuilt() throws Exception {
        addImportRoutes(
                recurrentImport("nul-character", fileUri(permittedImportDir, "?fileName=profiles%00.csv")),
                recurrentImport("well-formed", fileUri(permittedImportDir, "?fileName=profiles.csv")));

        assertRouteRefused("nul-character", "the file system cannot use a path that holds a nul character");
        assertRouteBuilt("well-formed",
                "a path the file system rejects is a refusal, not an exception that costs the deployment its other routes");
    }

    @Test
    public void importRouteIsRefusedWhenSourceIsTheSharedBaseOrAnotherTenantsDirectory() throws Exception {
        File otherTenant = new File(importRoot, "beta");
        assertTrue(otherTenant.mkdirs());

        ImportConfiguration shared = recurrentImport("shared-base", fileUri(importRoot, "?fileName=profiles.csv"));
        ImportConfiguration foreign = recurrentImport("other-tenant", fileUri(otherTenant, "?fileName=profiles.csv"));
        ImportConfiguration own = recurrentImport("own-tenant", fileUri(permittedImportDir, "?fileName=profiles.csv"));
        ImportConfiguration beta = recurrentImport("beta-tenant", fileUri(otherTenant, "?fileName=profiles.csv"));
        beta.setTenantId("beta");

        addImportRoutes(shared, foreign, own, beta);

        assertRouteRefused("shared-base", "the shared base directory is not a tenant directory");
        assertRouteRefused("other-tenant", "acme may not read beta's directory");
        assertRouteBuilt("own-tenant", "acme may read its own directory");
        assertRouteBuilt("beta-tenant", "beta may read its own directory");
    }

    @Test
    public void exportRouteIsRefusedWhenDestinationIsAnotherTenantsDirectory() throws Exception {
        File otherTenant = new File(exportRoot, "beta");
        assertTrue(otherTenant.mkdirs());

        addExportRoutes(recurrentExport("other-tenant", fileUri(otherTenant, "?fileName=profiles.csv")));

        assertRouteRefused("other-tenant", "acme may not write into beta's directory");
    }

    @Test
    public void importRouteIsBuiltWhenPermittedBaseDirIsNonAsciiAndTheUriIsPartlyEncoded() throws Exception {
        File nonAsciiBaseDir = tmp.newFolder("caf\u00e9-import");
        Assume.assumeTrue("this file system does not keep non-ASCII directory names", nonAsciiBaseDir.isDirectory());
        File tenantDir = new File(nonAsciiBaseDir, TENANT);
        File dropDir = new File(tenantDir, "drop zone");
        assertTrue("could not prepare the test fixture", dropDir.mkdirs());

        addImportRoutesInto(nonAsciiBaseDir,
                recurrentImport("non-ascii", fileUri(tenantDir, "/drop%20zone?fileName=profiles.csv")));

        assertRouteBuilt("non-ascii",
                "decoding an escape must not corrupt the characters around it, or containment is decided on another path");
    }

    @Test
    public void remoteImportEndpointIsNotSubjectToDirectoryContainment() throws Exception {
        addImportRoutes(recurrentImport("remote", "ftp://ftp.example.com/profiles?fileName=profiles.csv"));

        assertRouteBuilt("remote", "ftp is an allowed scheme and carries no local path");
    }

    @Test
    public void importRouteIsRefusedWhenARemoteSourceStagesItsDownloadsOutsidePermittedBaseDir() throws Exception {
        addImportRoutes(recurrentImport("remote-staging", "ftp://ftp.example.com/profiles"
                + "?fileName=profiles.csv&localWorkDirectory=" + arbitraryDir.getAbsolutePath()));

        assertRouteRefused("remote-staging",
                "localWorkDirectory is a local directory: the remote server's content is written there, "
                        + "under the name the remote server chooses");
    }

    @Test
    public void importRouteIsBuiltWhenARemoteSourceStagesItsDownloadsInsidePermittedBaseDir() throws Exception {
        addImportRoutes(recurrentImport("remote-staging-in-bounds", "ftp://ftp.example.com/profiles"
                + "?fileName=profiles.csv&localWorkDirectory="
                + new File(permittedImportDir, "staging").getAbsolutePath()));

        assertRouteBuilt("remote-staging-in-bounds",
                "staging downloads inside the permitted base directory is legitimate, and the directory "
                        + "need not exist yet");
    }

    @Test
    public void importRouteIsRefusedWhenSourceStagesItsDownloadsOutsidePermittedBaseDir() throws Exception {
        addImportRoutes(recurrentImport("local-staging", fileUri(permittedImportDir,
                "?fileName=profiles.csv&localWorkDirectory=" + arbitraryDir.getAbsolutePath())));

        assertRouteRefused("local-staging",
                "the option is resolved on its own, not against the endpoint directory, so a permitted "
                        + "directory does not cover it");
    }

    @Test
    public void importRouteIsRefusedWhenSchemeIsNotAllowed() throws Exception {
        addImportRoutes("ftp,sftp,ftps", recurrentImport("scheme-denied", fileUri(permittedImportDir, "?fileName=profiles.csv")));

        assertRouteRefused("scheme-denied", "file is not in the configured scheme allow-list");
    }

    @Test
    public void importRouteIsRefusedWhenSchemeIsNotInTheCaseItIsAllowedIn() throws Exception {
        addImportRoutes(
                recurrentImport("upper-case-scheme", "FILE://" + permittedImportDir.getAbsolutePath() + "?fileName=profiles.csv"),
                recurrentImport("mixed-case-remote", "Ftp://ftp.example.com/profiles?fileName=profiles.csv"));

        assertRouteRefused("upper-case-scheme",
                "Camel looks a component up by the exact name, so FILE names none and the route could not be built");
        assertRouteRefused("mixed-case-remote", "the same holds for a remote scheme");
    }

    @Test
    public void importRouteIsRefusedWhenSourceNamesAHost() throws Exception {
        addImportRoutes(recurrentImport("host-authority",
                "file://localhost" + permittedImportDir.getAbsolutePath() + "?fileName=profiles.csv"));

        assertRouteRefused("host-authority",
                "Camel knows no authority: the host is the first segment of a path relative to the working directory");
    }

    @Test
    public void importRouteIsRefusedWhenSchemeIsOnlyASubstringOfAnAllowedScheme() throws Exception {
        addImportRoutes(recurrentImport("substring-scheme", "fil://" + permittedImportDir.getAbsolutePath() + "?fileName=profiles.csv"));

        assertRouteRefused("substring-scheme", "the allow-list is a set of schemes, not a string to search");
    }

    @Test
    public void malformedSourceIsSkippedWithoutPreventingTheOtherRoutesFromBeingBuilt() throws Exception {
        addImportRoutes(
                recurrentImport("no-scheme", permittedImportDir.getAbsolutePath() + "/profiles.csv"),
                recurrentImport("well-formed", fileUri(permittedImportDir, "?fileName=profiles.csv")));

        assertRouteRefused("no-scheme", "an endpoint without a scheme cannot be honoured");
        assertRouteBuilt("well-formed", "one malformed configuration must not cost the deployment its other routes");
    }

    // ---------------------------------------------------------------------------------------------
    // Export — the configured destination is written by Unomi
    // ---------------------------------------------------------------------------------------------

    @Test
    public void exportRouteIsBuiltWhenDestinationIsInsidePermittedBaseDir() throws Exception {
        addExportRoutes(recurrentExport("in-bounds", fileUri(permittedExportDir, "?fileName=profiles.csv")));

        assertRouteBuilt("in-bounds", "a destination inside the permitted base directory is legitimate");
    }

    @Test
    public void exportRouteIsBuiltWhenDestinationIsInASubdirectoryThatDoesNotExistYet() throws Exception {
        File notCreatedYet = new File(permittedExportDir, "2026-08");

        addExportRoutes(recurrentExport("subdirectory", fileUri(notCreatedYet, "?fileName=profiles.csv")));

        assertRouteBuilt("subdirectory", "an export destination is created on first write — containment must not require the directory to exist");
    }

    @Test
    public void exportRouteIsRefusedWhenDestinationIsOutsidePermittedBaseDir() throws Exception {
        addExportRoutes(recurrentExport("arbitrary-dir", fileUri(arbitraryDir, "?fileName=profiles.csv")));

        assertRouteRefused("arbitrary-dir", "the destination directory is not one the deployment permits");
    }

    @Test
    public void exportRouteIsRefusedWhenDestinationEscapesPermittedBaseDirWithParentSegments() throws Exception {
        String escaping = fileUri(permittedExportDir, "/../" + arbitraryDir.getName() + "?fileName=profiles.csv");

        addExportRoutes(recurrentExport("dot-dot", escaping));

        assertRouteRefused("dot-dot", "the destination resolves outside the permitted base directory once normalized");
    }

    @Test
    public void exportRouteIsRefusedWhenFileNameOptionEscapesPermittedBaseDir() throws Exception {
        addExportRoutes(recurrentExport("filename-escape",
                fileUri(permittedExportDir, "?fileName=../" + arbitraryDir.getName() + "/profiles.csv")));

        assertRouteRefused("filename-escape", "fileName carries a path and must be contained too");
    }

    @Test
    public void exportRouteIsRefusedWhenTempFileNameOptionEscapesPermittedBaseDir() throws Exception {
        addExportRoutes(recurrentExport("tempfilename-escape",
                fileUri(permittedExportDir, "?fileName=profiles.csv&tempFileName=../" + arbitraryDir.getName() + "/profiles.tmp")));

        assertRouteRefused("tempfilename-escape", "tempFileName carries a path and must be contained too");
    }

    @Test
    public void exportRouteIsRefusedWhenDoneFileNameOptionEscapesPermittedBaseDir() throws Exception {
        addExportRoutes(recurrentExport("donefilename-escape",
                fileUri(permittedExportDir, "?fileName=profiles.csv&doneFileName=../" + arbitraryDir.getName() + "/done")));

        assertRouteRefused("donefilename-escape", "doneFileName carries a path and must be contained too");
    }

    @Test
    public void exportRouteIsBuiltWhenFileNameOptionUsesADateExpression() throws Exception {
        addExportRoutes(recurrentExport("date-expression",
                fileUri(permittedExportDir, "?fileName=profiles-export-${date:now:yyyyMMddHHmm}.csv")));

        assertRouteBuilt("date-expression",
                "a date cannot hold a parent segment, and naming an export after it is what the documentation shows");
    }

    @Test
    public void remoteExportEndpointIsNotSubjectToDirectoryContainment() throws Exception {
        addExportRoutes(recurrentExport("remote", "ftp://ftp.example.com/profiles?fileName=profiles.csv"));

        assertRouteBuilt("remote", "ftp is an allowed scheme and carries no local path");
    }

    @Test
    public void exportRouteIsRefusedWhenDestinationStagesItsDownloadsOutsidePermittedBaseDir() throws Exception {
        addExportRoutes(recurrentExport("remote-staging", "ftp://ftp.example.com/profiles"
                + "?fileName=profiles.csv&localWorkDirectory=" + arbitraryDir.getAbsolutePath()));

        assertRouteRefused("remote-staging", "the option names a local directory in either direction");
    }

    @Test
    public void importRouteIsRefusedWhenTheStagingOptionIsSpeltInAnotherCase() throws Exception {
        addImportRoutes(recurrentImport("staging-case", "ftp://ftp.example.com/profiles"
                + "?fileName=profiles.csv&LOCALWORKDIRECTORY=" + arbitraryDir.getAbsolutePath()));

        assertRouteRefused("staging-case", "the option is matched on its name, not on how it is spelt");
    }

    @Test
    public void malformedDestinationIsSkippedWithoutPreventingTheOtherRoutesFromBeingBuilt() throws Exception {
        addExportRoutes(
                recurrentExport("blank", ""),
                recurrentExport("well-formed", fileUri(permittedExportDir, "?fileName=profiles.csv")));

        assertRouteRefused("blank", "a blank destination cannot be honoured");
        assertRouteBuilt("well-formed", "one malformed configuration must not cost the deployment its other routes");
    }

    // ---------------------------------------------------------------------------------------------
    // Property placeholders — expanded by Camel after validation, over the whole URI
    // ---------------------------------------------------------------------------------------------

    @Test
    public void importRouteIsRefusedWhenSourceDirectoryCarriesAPropertyPlaceholder() throws Exception {
        // Camel expands {{env:NAME:default}} before it parses the endpoint, and uses the default
        // verbatim when NAME is not set: what reaches the file system is acme/../beta. To a path walk
        // the first parent segment is fused to the token and is not a parent segment at all.
        addImportRoutes(recurrentImport("placeholder-directory",
                fileUri(permittedImportDir, "/{{env:UNOMI_UNSET_FOR_TEST:../beta}}?fileName=profiles.csv")));

        assertRouteRefused("placeholder-directory",
                "a placeholder is expanded after validation, so nothing about the path can be trusted");
    }

    @Test
    public void importRouteIsRefusedWhenAPathBearingOptionCarriesAPropertyPlaceholder() throws Exception {
        addImportRoutes(recurrentImport("placeholder-option",
                fileUri(permittedImportDir, "?fileName=profiles.csv&move={{sys:unomi.unset.for.test:../../beta}}")));

        assertRouteRefused("placeholder-option", "the placeholder's default is a parent segment the path walk never sees");
    }

    @Test
    public void importRouteIsRefusedWhenARemoteSourceCarriesAPropertyPlaceholder() throws Exception {
        // starts inside the tenant's directory, so that only the placeholder takes it out of there
        addImportRoutes(recurrentImport("placeholder-remote", "sftp://sftp.example.com/profiles?fileName=profiles.csv"
                + "&localWorkDirectory=" + permittedImportDir.getAbsolutePath() + "/{{env:UNOMI_UNSET_FOR_TEST:../../elsewhere}}"));

        assertRouteRefused("placeholder-remote", "a placeholder can put the staging directory anywhere, whatever the scheme");
    }

    @Test
    public void exportRouteIsRefusedWhenDestinationCarriesAPropertyPlaceholder() throws Exception {
        addExportRoutes(recurrentExport("placeholder-destination",
                fileUri(permittedExportDir, "?fileName={{env:UNOMI_UNSET_FOR_TEST:../../beta/profiles.csv}}")));

        assertRouteRefused("placeholder-destination", "an export destination is expanded by Camel the same way");
    }

    // ---------------------------------------------------------------------------------------------
    // The other expression syntax, and a tenant id that is a path
    // ---------------------------------------------------------------------------------------------

    @Test
    public void importRouteIsRefusedWhenMoveOptionUsesTheAlternateExpressionSyntaxToLeavePermittedBaseDir() throws Exception {
        addImportRoutes(recurrentImport("simple-syntax",
                fileUri(permittedImportDir, "?fileName=profiles.csv&move=$simple{file:parent}/../../elsewhere")));

        assertRouteRefused("simple-syntax", "$simple{...} is the same File Language expression as ${...}");
    }

    @Test
    public void importRouteIsBuiltWhenMoveOptionUsesTheAlternateExpressionSyntaxAndStaysInside() throws Exception {
        addImportRoutes(recurrentImport("simple-syntax-inside",
                fileUri(permittedImportDir, "?fileName=profiles.csv&move=.done/$simple{file:name.noext}-$simple{date:now:yyyyMMdd}.csv")));

        assertRouteBuilt("simple-syntax-inside", "name and date tokens contribute one component each, whatever the syntax");
    }

    @Test
    public void importRouteIsRefusedWhenTheTenantIdIsNotASingleDirectoryName() throws Exception {
        File otherTenant = new File(importRoot, "beta");
        assertTrue(otherTenant.mkdirs());
        ImportConfiguration configuration = recurrentImport("traversing-tenant", fileUri(otherTenant, "?fileName=profiles.csv"));
        configuration.setTenantId("acme/../beta");

        addImportRoutes(configuration);

        assertRouteRefused("traversing-tenant", "a tenant id that is a path of its own is not confined to a directory");
    }

    @Test
    public void importRouteIsRefusedWhenTheTenantIdNamesNoDirectoryOfItsOwn() throws Exception {
        // each of these would resolve to the shared base directory, or to its parent
        String[] tenantIds = {".", "..", "", " "};
        ImportConfiguration[] configurations = new ImportConfiguration[tenantIds.length];
        for (int i = 0; i < tenantIds.length; i++) {
            configurations[i] = recurrentImport("tenant-" + i, fileUri(importRoot, "?fileName=profiles.csv"));
            configurations[i].setTenantId(tenantIds[i]);
        }

        addImportRoutes(configurations);

        for (int i = 0; i < tenantIds.length; i++) {
            assertRouteRefused("tenant-" + i, "the tenant id '" + tenantIds[i] + "' is not the name of a directory");
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------------------------------------

    private void assertRouteBuilt(String routeId, String why) {
        assertNotNull("no route was built for configuration '" + routeId + "', although " + why,
                camelContext.getRouteDefinition(routeId));
    }

    /** The endpoint URI the route was actually built on, which is not the one that was configured. */
    private String builtEndpointUri(String routeId) {
        assertRouteBuilt(routeId, "there is no endpoint to look at otherwise");
        return camelContext.getRouteDefinition(routeId).getInputs().get(0).getUri();
    }

    private void assertRouteRefused(String routeId, String why) {
        assertNull("a route was built for configuration '" + routeId + "', although " + why,
                camelContext.getRouteDefinition(routeId));
    }

    private void addImportRoutes(ImportConfiguration... configurations) throws Exception {
        addImportRoutes(DEFAULT_ALLOWED_ENDPOINTS, configurations);
    }

    private void addImportRoutes(String allowedEndpoints, ImportConfiguration... configurations) throws Exception {
        ProfileImportFromSourceRouteBuilder builder = importRouteBuilder(allowedEndpoints, configurations);
        builder.setPermittedImportBaseDirs(importRoot.getAbsolutePath());
        builder.setContext(camelContext);
        camelContext.addRoutes(builder);
    }

    private ProfileImportFromSourceRouteBuilder importRouteBuilder(String allowedEndpoints, ImportConfiguration... configurations) {
        ProfileImportFromSourceRouteBuilder builder =
                new ProfileImportFromSourceRouteBuilder(NO_KAFKA, RouterConstants.CONFIG_TYPE_NOBROKER);
        builder.setImportConfigurationList(Arrays.asList(configurations));
        builder.setImportConfigurationService(discardingConfigurationService());
        builder.setProfileService(noOpProfileService());
        builder.setJacksonDataFormat(new JacksonDataFormat(ProfileToImport.class));
        builder.setAllowedEndpoints(allowedEndpoints);
        return builder;
    }

    private void addImportRoutesInto(File permittedBaseDir, ImportConfiguration... configurations) throws Exception {
        ProfileImportFromSourceRouteBuilder builder = importRouteBuilder(DEFAULT_ALLOWED_ENDPOINTS, configurations);
        builder.setPermittedImportBaseDirs(permittedBaseDir.getAbsolutePath());
        builder.setContext(camelContext);
        camelContext.addRoutes(builder);
    }

    private void addExportRoutes(ExportConfiguration... configurations) throws Exception {
        ProfileExportCollectRouteBuilder builder =
                new ProfileExportCollectRouteBuilder(NO_KAFKA, RouterConstants.CONFIG_TYPE_NOBROKER);
        builder.setExportConfigurationList(Arrays.asList(configurations));
        builder.setExportConfigurationService(discardingConfigurationService());
        builder.setJacksonDataFormat(new JacksonDataFormat(ProfileToImport.class));
        builder.setAllowedEndpoints(DEFAULT_ALLOWED_ENDPOINTS);
        builder.setPermittedExportBaseDirs(exportRoot.getAbsolutePath());
        builder.setContext(camelContext);
        camelContext.addRoutes(builder);
    }

    /**
     * A refused configuration is recorded, and these tests are not about that: what they observe is
     * whether the route was built.
     */
    @SuppressWarnings("unchecked")
    private static <T> ImportExportConfigurationService<T> discardingConfigurationService() {
        return (ImportExportConfigurationService<T>) Proxy.newProxyInstance(
                ImportExportConfigurationService.class.getClassLoader(),
                new Class<?>[]{ImportExportConfigurationService.class},
                (proxy, method, args) -> "save".equals(method.getName()) ? args[0] : null);
    }
}
