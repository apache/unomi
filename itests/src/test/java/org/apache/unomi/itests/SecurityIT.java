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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.unomi.api.ContextRequest;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.services.PersonalizationService;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertFalse;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class SecurityIT extends BaseIT {

    private static final String SESSION_ID = "vuln-session-id";

    private ObjectMapper objectMapper;

    @Before
    public void setUp() {
        objectMapper = CustomObjectMapper.getObjectMapper();
    }

    @Test
    public void testOGNLInjection() throws IOException {
        ContextRequest contextRequest = new ContextRequest();
        List<PersonalizationService.PersonalizationRequest> personalizations = new ArrayList<>();
        PersonalizationService.PersonalizationRequest personalizationRequest = new PersonalizationService.PersonalizationRequest();
        personalizationRequest.setId("vuln-test");
        personalizationRequest.setStrategy("matching-first");
        Map<String, Object> strategyOptions = new HashMap<>();
        strategyOptions.put("fallback", "var2");
        personalizationRequest.setStrategyOptions(strategyOptions);
        List<PersonalizationService.PersonalizedContent> personalizationContents = new ArrayList<>();
        PersonalizationService.PersonalizedContent var1Content = new PersonalizationService.PersonalizedContent();
        var1Content.setId("var1");
        List<PersonalizationService.Filter> filters = new ArrayList<>();
        PersonalizationService.Filter filter = new PersonalizationService.Filter();
        Condition condition = new Condition();
        File vulnFile = new File("target/vuln-file.txt");
        if (vulnFile.exists()) {
            vulnFile.delete();
        }
        condition.setConditionTypeId("profilePropertyCondition");
        condition.setParameter("propertyName", "@java.lang.Runtime@getRuntime().exec('touch " + vulnFile.getCanonicalPath() + "')");
        condition.setParameter("comparisonOperator", "equals");
        condition.setParameter("propertyValue",
                "script::java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.BufferedWriter(new java.io.FileWriter(\""
                        + vulnFile.getCanonicalPath() + "\", true)));\nwriter.println(\"test\");\nwriter.close();");
        filter.setCondition(condition);
        filters.add(filter);
        var1Content.setFilters(filters);
        personalizationContents.add(var1Content);
        PersonalizationService.PersonalizedContent var2Content = new PersonalizationService.PersonalizedContent();
        var2Content.setId("var2");
        personalizationContents.add(var2Content);
        personalizationRequest.setContents(personalizationContents);

        personalizations.add(personalizationRequest);
        contextRequest.setPersonalizations(personalizations);

        contextRequest.setSessionId(SESSION_ID);
        HttpPost request = new HttpPost(URL + "/cxs/context.json");
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.create("application/json")));

        TestUtils.RequestResponse response = executeContextJSONRequest(request, SESSION_ID);
        assertFalse("Vulnerability successfully executed ! File created at " + vulnFile.getCanonicalPath(), vulnFile.exists());
    }

    private TestUtils.RequestResponse executeContextJSONRequest(HttpPost request, String sessionId) throws IOException {
        return TestUtils.executeContextJSONRequest(request, sessionId);
    }

}
