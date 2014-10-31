package org.oasis_open.wemi.context.server.itests;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.oasis_open.wemi.context.server.ContextRequest;
import org.oasis_open.wemi.context.server.ContextResponse;
import org.oasis_open.wemi.context.server.api.Metadata;
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

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.ops4j.pax.exam.CoreOptions.*;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.karafDistributionConfiguration;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.keepRuntimeFolder;

/**
 * Created by loom on 04.08.14.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class BasicTest {
    private final static Logger LOGGER = LoggerFactory.getLogger(BasicTest.class);
    private static final String JSON_MYME_TYPE = "application/json";

    @Inject
    protected SegmentService segmentService;

    private ObjectMapper objectMapper = new ObjectMapper();

    @Configuration
    public Option[] config() {
        MavenArtifactUrlReference karafUrl = maven()
                .groupId("org.apache.karaf")
                .artifactId("apache-karaf")
                .version("3.0.2")
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
        return new Option[]{
                KarafDistributionOption.debugConfiguration("5005", false),
                karafDistributionConfiguration()
                        .frameworkUrl(karafUrl)
                        .unpackDirectory(new File("target/exam"))
                        .useDeployFolder(false),
                keepRuntimeFolder(),
                KarafDistributionOption.features(karafPaxWebRepo, "war"),
                KarafDistributionOption.features(karafCxfRepo, "cxf"),
                KarafDistributionOption.features(karafStandardRepo, "openwebbeans"),
                KarafDistributionOption.features(karafStandardRepo, "pax-cdi-web-openwebbeans"),
                KarafDistributionOption.features(wemiServerRepo, "wemi-context-server-kar"),
                // we need to wrap the HttpComponents libraries ourselves since the OSGi bundles provided by the project are incorrect
                wrappedBundle(mavenBundle("org.apache.httpcomponents",
                        "httpcore").versionAsInProject()),
                wrappedBundle(mavenBundle("org.apache.httpcomponents",
                        "httpmime").versionAsInProject()),
                wrappedBundle(mavenBundle("org.apache.httpcomponents",
                        "httpclient").versionAsInProject())
        };
    }

    @Test
    public void testSegments() {
        Assert.assertNotNull("Segment service should be available", segmentService);
        Set<Metadata> segmentMetadatas = segmentService.getSegmentMetadatas();
        Assert.assertNotEquals("Segment metadata list should not be empty", 0, segmentMetadatas.size());
        LOGGER.info("Retrieved " + segmentMetadatas.size() + " segment metadata entries");
    }

    @Test
    public void testContextJS() throws IOException {
        HttpUriRequest request = new HttpGet("http://localhost:8181/context.js?sessionId=aa3b04bd-8f4d-4a07-8e96-d33ffa04d3d9");
        CloseableHttpResponse response = HttpClientBuilder.create().build().execute(request);
        // The underlying HTTP connection is still held by the response object
        // to allow the response content to be streamed directly from the network socket.
        // In order to ensure correct deallocation of system resources
        // the user MUST call CloseableHttpResponse#close() from a finally clause.
        // Please note that if response content is not fully consumed the underlying
        // connection cannot be safely re-used and will be shut down and discarded
        // by the connection manager.
        String responseContent = null;
        try {
            System.out.println(response.getStatusLine());
            HttpEntity entity = response.getEntity();
            // do something useful with the response body
            // and ensure it is fully consumed
            responseContent = EntityUtils.toString(entity);
        } finally {
            response.close();
        }
        Assert.assertTrue("Response should contain context object", responseContent.contains("window.digitalData = window.digitalData || {};"));
        // @todo we should check the validity of the context object, but this is rather complex since it would
        // potentially require parsing the Javascript !
    }

    @Test
    public void testContextJSON() throws IOException {
        String sessionId = "aa3b04bd-8f4d-4a07-8e96-d33ffa04d3d9";
        ContextRequest contextRequest = new ContextRequest();
        contextRequest.setPageId("af6f393a-a537-4586-991b-8521b9c7b05b");
        HttpPost request = new HttpPost("http://localhost:8181/context.json?sessionId=" + sessionId);
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.create("application/json")));
        CloseableHttpResponse response = HttpClientBuilder.create().build().execute(request);

        try {
            // validate mimeType
            String mimeType = ContentType.getOrDefault(response.getEntity()).getMimeType();
            Assert.assertEquals("Response content type should be " + JSON_MYME_TYPE, JSON_MYME_TYPE, mimeType);

            // validate context
            ContextResponse context = TestUtils.retrieveResourceFromResponse(response, ContextResponse.class);
            Assert.assertNotNull("Context should not be null", context);
            Assert.assertNotNull("Context userId should not be null", context.getUserId());
            Assert.assertEquals("Context sessionId should be the same as the sessionId used to request the context", sessionId, context.getSessionId());
        } finally {
            response.close();
        }
    }

}
