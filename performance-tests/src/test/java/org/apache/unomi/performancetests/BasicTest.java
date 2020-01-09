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
 * limitations under the License
 */

package org.apache.unomi.performancetests;

import com.carrotsearch.junitbenchmarks.BenchmarkOptions;
import com.carrotsearch.junitbenchmarks.BenchmarkRule;
import com.carrotsearch.junitbenchmarks.WriterConsumer;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.services.SegmentService;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.karaf.options.KarafDistributionOption;
import org.ops4j.pax.exam.options.MavenArtifactUrlReference;
import org.ops4j.pax.exam.options.MavenUrlReference;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import static org.ops4j.pax.exam.CoreOptions.*;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.karafDistributionConfiguration;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.keepRuntimeFolder;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class BasicTest {

    private final static Logger LOGGER = LoggerFactory.getLogger(BasicTest.class);
    @Inject
    protected SegmentService segmentService;

    /**
     * Enables the benchmark rule.
     */
    @Rule
    public TestRule getBenchmarkRun() {
        try {
            File benchmarks = new File("../../benchmarks");
            benchmarks.mkdirs();
            return new BenchmarkRule(new WriterConsumer(new FileWriter(new File(benchmarks,"benchmark.txt"),true)));
        } catch (IOException e) {
            LOGGER.error("Cannot get benchamrks",e);
        }
        return null;
    }

    @Configuration
    public Option[] config() {
        MavenArtifactUrlReference karafUrl = maven()
                .groupId("org.apache.unomi")
                .artifactId("unomi")
                .type("tar.gz")
                .versionAsInProject();

        String localRepository = System.getProperty("org.ops4j.pax.url.mvn.localRepository");
        if (localRepository == null) {
            localRepository = "";
        }

        List<Option> options = new ArrayList<>();

        Option[] commonOptions = new Option[]{
                KarafDistributionOption.debugConfiguration("5005", false),
                KarafDistributionOption.logLevel(LogLevel.INFO),
                KarafDistributionOption.editConfigurationFilePut("etc/org.ops4j.pax.url.mvn.cfg", "org.ops4j.pax.url.mvn.localRepository", localRepository),
                karafDistributionConfiguration()
                        .frameworkUrl(karafUrl)
                        .unpackDirectory(new File("target/exam"))
                        .useDeployFolder(false),
                keepRuntimeFolder(),
                // we need to wrap the HttpComponents libraries ourselves since the OSGi bundles provided by the project are incorrect
                wrappedBundle(mavenBundle("org.apache.httpcomponents",
                        "httpcore").versionAsInProject()),
                wrappedBundle(mavenBundle("org.apache.httpcomponents",
                        "httpmime").versionAsInProject()),
                wrappedBundle(mavenBundle("org.apache.httpcomponents",
                        "httpclient").versionAsInProject()),
                wrappedBundle(mavenBundle("com.carrotsearch",
                        "junit-benchmarks", "0.7.2")),
//                wrappedBundle(mavenBundle("com.h2database",
//                        "h2", "1.4.181"))
        };

        options.addAll(Arrays.asList(commonOptions));

        if (JavaVersionUtil.getMajorVersion() >= 9) {
            Option[] jdk9PlusOptions = new Option[]{
                    new VMOption("--add-reads=java.xml=java.logging"),
                    new VMOption("--add-exports=java.base/org.apache.karaf.specs.locator=java.xml,ALL-UNNAMED"),
                    new VMOption("--patch-module"),
                    new VMOption("java.base=lib/endorsed/org.apache.karaf.specs.locator-"
                            + System.getProperty("karaf.version") + ".jar"),
                    new VMOption("--patch-module"), new VMOption("java.xml=lib/endorsed/org.apache.karaf.specs.java.xml-"
                    + System.getProperty("karaf.version") + ".jar"),
                    new VMOption("--add-opens"),
                    new VMOption("java.base/java.security=ALL-UNNAMED"),
                    new VMOption("--add-opens"),
                    new VMOption("java.base/java.net=ALL-UNNAMED"),
                    new VMOption("--add-opens"),
                    new VMOption("java.base/java.lang=ALL-UNNAMED"),
                    new VMOption("--add-opens"),
                    new VMOption("java.base/java.util=ALL-UNNAMED"),
                    new VMOption("--add-opens"),
                    new VMOption("java.naming/javax.naming.spi=ALL-UNNAMED"),
                    new VMOption("--add-opens"),
                    new VMOption("java.rmi/sun.rmi.transport.tcp=ALL-UNNAMED"),
                    new VMOption("--add-exports=java.base/sun.net.www.protocol.http=ALL-UNNAMED"),
                    new VMOption("--add-exports=java.base/sun.net.www.protocol.https=ALL-UNNAMED"),
                    new VMOption("--add-exports=java.base/sun.net.www.protocol.jar=ALL-UNNAMED"),
                    new VMOption("--add-exports=jdk.naming.rmi/com.sun.jndi.url.rmi=ALL-UNNAMED"),
                    new VMOption("-classpath"),
                    new VMOption("lib/jdk9plus/*" + File.pathSeparator + "lib/boot/*")

            };
            options.addAll(Arrays.asList(jdk9PlusOptions));
        }

        return options.toArray(new Option[0]);
    }

    @BenchmarkOptions(benchmarkRounds = 100, warmupRounds = 0, concurrency = 10)
    @Test
    public void testContext() throws IOException {

        CloseableHttpClient httpclient = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet("http://localhost:8181/context.js");
        CloseableHttpResponse response = httpclient.execute(httpGet);
        // The underlying HTTP connection is still held by the response object
        // to allow the response content to be streamed directly from the network socket.
        // In order to ensure correct deallocation of system resources
        // the user MUST call CloseableHttpResponse#close() from a finally clause.
        // Please note that if response content is not fully consumed the underlying
        // connection cannot be safely re-used and will be shut down and discarded
        // by the connection manager.
        String responseContent = null;
        try {
            HttpEntity entity = response.getEntity();
            // do something useful with the response body
            // and ensure it is fully consumed
            responseContent = EntityUtils.toString(entity);
        } finally {
            response.close();
        }
    }

    @BenchmarkOptions(benchmarkRounds = 100, warmupRounds = 0, concurrency = 10)
    @Test
    public void testSegments() {
        Assert.assertNotNull("Segment service should be available", segmentService);
        List<Metadata> segmentMetadatas = segmentService.getSegmentMetadatas(0, 50, null).getList();
        Assert.assertNotEquals("Segment metadata list should not be empty", 0, segmentMetadatas.size());
        LOGGER.info("Retrieved " + segmentMetadatas.size() + " segment metadata entries");
    }



}
