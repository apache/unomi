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

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;

import java.nio.charset.StandardCharsets;

/**
 * HTTP-level checks that system-admin-only REST endpoints reject tenant private keys
 * (including multipart upload / oneshot paths).
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class RestEndpointRoleSecurityIT extends BaseIT {

    @Test
    public void importConfiguration_requiresSystemAdministrator() throws Exception {
        try (CloseableHttpResponse tenantAdmin = executeHttpRequest(
                new HttpGet(getFullUrl("/cxs/importConfiguration")), AuthType.PRIVATE_KEY)) {
            Assert.assertEquals("Tenant private key must not list import configurations",
                    403, tenantAdmin.getStatusLine().getStatusCode());
        }

        try (CloseableHttpResponse jaasAdmin = executeHttpRequest(
                new HttpGet(getFullUrl("/cxs/importConfiguration")), AuthType.JAAS_ADMIN)) {
            Assert.assertEquals("JAAS admin should list import configurations",
                    200, jaasAdmin.getStatusLine().getStatusCode());
        }
    }

    @Test
    public void exportConfiguration_requiresSystemAdministrator() throws Exception {
        try (CloseableHttpResponse tenantAdmin = executeHttpRequest(
                new HttpGet(getFullUrl("/cxs/exportConfiguration")), AuthType.PRIVATE_KEY)) {
            Assert.assertEquals(403, tenantAdmin.getStatusLine().getStatusCode());
        }

        try (CloseableHttpResponse jaasAdmin = executeHttpRequest(
                new HttpGet(getFullUrl("/cxs/exportConfiguration")), AuthType.JAAS_ADMIN)) {
            Assert.assertEquals(200, jaasAdmin.getStatusLine().getStatusCode());
        }
    }

    @Test
    public void importConfiguration_oneshotUpload_requiresSystemAdministrator() throws Exception {
        HttpPost oneshot = multipartPost(getFullUrl("/cxs/importConfiguration/oneshot"),
                "----UnomiImportBoundary",
                part("importConfigId", "text/plain", "rest-role-security-oneshot"),
                filePart("file", "probe.csv", "text/csv", "col1\nvalue1\n"));

        try (CloseableHttpResponse tenantAdmin = executeHttpRequest(oneshot, AuthType.PRIVATE_KEY)) {
            Assert.assertEquals(403, tenantAdmin.getStatusLine().getStatusCode());
        }

        HttpPost oneshotJaas = multipartPost(getFullUrl("/cxs/importConfiguration/oneshot"),
                "----UnomiImportBoundaryJaas",
                part("importConfigId", "text/plain", "rest-role-security-oneshot"),
                filePart("file", "probe.csv", "text/csv", "col1\nvalue1\n"));
        try (CloseableHttpResponse jaasAdmin = executeHttpRequest(oneshotJaas, AuthType.JAAS_ADMIN)) {
            // Role gate is what we care about; missing config may yield 500 after auth succeeds.
            Assert.assertNotEquals(403, jaasAdmin.getStatusLine().getStatusCode());
            Assert.assertNotEquals(401, jaasAdmin.getStatusLine().getStatusCode());
        }
    }

    @Test
    public void exportConfiguration_oneshot_requiresSystemAdministrator() throws Exception {
        String body = "{\"itemId\":\"rest-role-security-export\",\"itemType\":\"exportConfig\"}";
        HttpPost oneshot = new HttpPost(getFullUrl("/cxs/exportConfiguration/oneshot"));
        oneshot.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));

        try (CloseableHttpResponse tenantAdmin = executeHttpRequest(oneshot, AuthType.PRIVATE_KEY)) {
            Assert.assertEquals(403, tenantAdmin.getStatusLine().getStatusCode());
        }

        HttpPost oneshotJaas = new HttpPost(getFullUrl("/cxs/exportConfiguration/oneshot"));
        oneshotJaas.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
        try (CloseableHttpResponse jaasAdmin = executeHttpRequest(oneshotJaas, AuthType.JAAS_ADMIN)) {
            Assert.assertNotEquals(403, jaasAdmin.getStatusLine().getStatusCode());
            Assert.assertNotEquals(401, jaasAdmin.getStatusLine().getStatusCode());
        }
    }

    @Test
    public void groovyActions_requiresSystemAdministrator() throws Exception {
        String path = getFullUrl("/cxs/groovyActions/rest-role-security-it-missing-action");

        try (CloseableHttpResponse tenantAdmin = executeHttpRequest(new HttpDelete(path), AuthType.PRIVATE_KEY)) {
            Assert.assertEquals("Tenant private key must not delete groovy actions",
                    403, tenantAdmin.getStatusLine().getStatusCode());
        }

        try (CloseableHttpResponse jaasAdmin = executeHttpRequest(new HttpDelete(path), AuthType.JAAS_ADMIN)) {
            int status = jaasAdmin.getStatusLine().getStatusCode();
            Assert.assertTrue("JAAS admin delete should be allowed (got " + status + ")",
                    status == 200 || status == 204 || status == 404);
        }
    }

    @Test
    public void groovyActions_upload_requiresSystemAdministrator() throws Exception {
        String script = "// RestEndpointRoleSecurityIT probe\nvoid execute() {}\n";
        HttpPost upload = multipartPost(getFullUrl("/cxs/groovyActions/"),
                "----UnomiGroovyBoundary",
                filePart("file", "RestRoleSecurityITProbe.groovy", "text/plain", script));

        try (CloseableHttpResponse tenantAdmin = executeHttpRequest(upload, AuthType.PRIVATE_KEY)) {
            Assert.assertEquals("Tenant private key must not upload groovy actions",
                    403, tenantAdmin.getStatusLine().getStatusCode());
        }

        HttpPost uploadJaas = multipartPost(getFullUrl("/cxs/groovyActions/"),
                "----UnomiGroovyBoundaryJaas",
                filePart("file", "RestRoleSecurityITProbe.groovy", "text/plain", script));
        try (CloseableHttpResponse jaasAdmin = executeHttpRequest(uploadJaas, AuthType.JAAS_ADMIN)) {
            Assert.assertEquals("JAAS admin should be allowed to upload groovy actions",
                    200, jaasAdmin.getStatusLine().getStatusCode());
        }

        try (CloseableHttpResponse cleanup = executeHttpRequest(
                new HttpDelete(getFullUrl("/cxs/groovyActions/RestRoleSecurityITProbe")), AuthType.JAAS_ADMIN)) {
            int status = cleanup.getStatusLine().getStatusCode();
            Assert.assertTrue(status == 200 || status == 204 || status == 404);
        }
    }

    private static HttpPost multipartPost(String url, String boundary, String... parts) {
        HttpPost post = new HttpPost(url);
        StringBuilder body = new StringBuilder();
        for (String part : parts) {
            body.append("--").append(boundary).append("\r\n").append(part);
        }
        body.append("--").append(boundary).append("--\r\n");
        post.setHeader("Content-Type", "multipart/form-data; boundary=" + boundary);
        post.setEntity(new ByteArrayEntity(body.toString().getBytes(StandardCharsets.UTF_8)));
        return post;
    }

    private static String part(String name, String contentType, String value) {
        return "Content-Disposition: form-data; name=\"" + name + "\"\r\n"
                + "Content-Type: " + contentType + "\r\n\r\n"
                + value + "\r\n";
    }

    private static String filePart(String name, String filename, String contentType, String value) {
        return "Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: " + contentType + "\r\n\r\n"
                + value + "\r\n";
    }
}
