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
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;

import java.io.IOException;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class SecurityIT extends BaseIT {

    private static final String SESSION_ID = "vuln-session-id";

    private ObjectMapper objectMapper;

    @Before
    public void setUp() {
        objectMapper = CustomObjectMapper.getObjectMapper();
    }

    private TestUtils.RequestResponse executeContextJSONRequest(HttpPost request, String sessionId) throws IOException {
        return TestUtils.executeContextJSONRequest(request, sessionId);
    }

}
