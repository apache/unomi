package org.oasis_open.contextserver.itests;

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
import org.oasis_open.contextserver.api.ContextRequest;
import org.oasis_open.contextserver.api.ContextResponse;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class BasicTest extends BaseTest{
    private final static Logger LOGGER = LoggerFactory.getLogger(BasicTest.class);
    private static final String JSON_MYME_TYPE = "application/json";

    private ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testContextJS() throws IOException {
        HttpUriRequest request = new HttpGet(URL + "/context.js?sessionId=aa3b04bd-8f4d-4a07-8e96-d33ffa04d3d9");
        CloseableHttpResponse response = HttpClientBuilder.create().build().execute(request);
        // The underlying HTTP connection is still held by the response object
        // to allow the response content to be streamed directly from the network socket.
        // In order to ensure correct deallocation of system resources
        // the profile MUST call CloseableHttpResponse#close() from a finally clause.
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
        HttpPost request = new HttpPost(URL + "/context.json?sessionId=" + sessionId);
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.create("application/json")));
        CloseableHttpResponse response = HttpClientBuilder.create().build().execute(request);

        try {
            // validate mimeType
            String mimeType = ContentType.getOrDefault(response.getEntity()).getMimeType();
            Assert.assertEquals("Response content type should be " + JSON_MYME_TYPE, JSON_MYME_TYPE, mimeType);

            // validate context
            ContextResponse context = TestUtils.retrieveResourceFromResponse(response, ContextResponse.class);
            Assert.assertNotNull("Context should not be null", context);
            Assert.assertNotNull("Context profileId should not be null", context.getProfileId());
            Assert.assertEquals("Context sessionId should be the same as the sessionId used to request the context", sessionId, context.getSessionId());
        } finally {
            response.close();
        }
    }

}
