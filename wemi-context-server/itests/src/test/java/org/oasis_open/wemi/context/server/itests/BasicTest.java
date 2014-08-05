package org.oasis_open.wemi.context.server.itests;

import static org.ops4j.pax.exam.CoreOptions.maven;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.karafDistributionConfiguration;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.keepRuntimeFolder;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import javax.inject.Inject;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.oasis_open.wemi.context.server.api.SegmentID;
import org.oasis_open.wemi.context.server.api.services.SegmentService;
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

/**
 * Created by loom on 04.08.14.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class BasicTest {

    private final static Logger LOGGER = LoggerFactory.getLogger(BasicTest.class);

    @Inject
    protected SegmentService segmentService;

    @Configuration
    public Option[] config() {
        MavenArtifactUrlReference karafUrl = maven()
            .groupId("org.apache.karaf")
            .artifactId("apache-karaf")
            .version("3.0.1")
            .type("tar.gz");

        MavenUrlReference karafStandardRepo = maven()
            .groupId("org.apache.karaf.features")
            .artifactId("standard")
            .classifier("features")
            .type("xml")
            .versionAsInProject();
        MavenUrlReference karafPaxWebRepo = maven()
            .groupId("org.ops4j.pax.web")
            .artifactId("pax-web-features")
            .classifier("features")
            .type("xml")
            .versionAsInProject();
        MavenUrlReference karafSpringRepo = maven()
            .groupId("org.apache.karaf.features")
            .artifactId("spring")
            .classifier("features")
            .type("xml")
            .versionAsInProject();
        MavenUrlReference karafCxfRepo = maven()
            .groupId("org.apache.cxf.karaf")
            .artifactId("apache-cxf")
            .classifier("features")
            .type("xml")
            .versionAsInProject();
        MavenUrlReference karafEnterpriseRepo = maven()
            .groupId("org.apache.karaf.features")
            .artifactId("enterprise")
            .classifier("features")
            .type("xml")
            .versionAsInProject();
        MavenUrlReference wemiServerRepo = maven()
            .groupId("org.oasis-open.wemi")
            .artifactId("wemi-context-server-kar")
            .classifier("features")
            .type("xml")
            .versionAsInProject();
        return new Option[] {
            KarafDistributionOption.debugConfiguration("5005", false),
            karafDistributionConfiguration()
                .frameworkUrl(karafUrl)
                .unpackDirectory(new File("target/exam"))
                .useDeployFolder(false),
            keepRuntimeFolder(),
            KarafDistributionOption.features(karafPaxWebRepo, "war"),
            KarafDistributionOption.features(karafCxfRepo, "cxf"),
            KarafDistributionOption.features(karafStandardRepo, "openwebbeans"),
            KarafDistributionOption.features(karafStandardRepo , "pax-cdi-web-openwebbeans"),
            KarafDistributionOption.features(wemiServerRepo , "wemi-context-server-kar"),
            mavenBundle()
                .groupId("org.apache.httpcomponents")
                .artifactId("httpcore-osgi")
                .versionAsInProject().start(),
            mavenBundle()
                .groupId("org.apache.httpcomponents")
                .artifactId("httpclient-osgi")
                .versionAsInProject().start(),
       };
    }

    @Test
    public void testSegments() {
        Assert.assertNotNull("Segment service should be available", segmentService);
        Set<SegmentID> segmentIDs = segmentService.getSegmentIDs();
        Assert.assertNotEquals("Segment ID list should not be empty", 0, segmentIDs.size());
        LOGGER.info("Retrieved " + segmentIDs.size() + " segment IDs");
    }

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
        try {
            System.out.println(response.getStatusLine());
            HttpEntity entity1 = response.getEntity();
            // do something useful with the response body
            // and ensure it is fully consumed
            EntityUtils.consume(entity1);
        } finally {
            response.close();
        }

    }

}
