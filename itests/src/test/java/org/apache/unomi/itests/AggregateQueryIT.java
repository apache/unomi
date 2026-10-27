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
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;

import static org.junit.Assert.assertEquals;

/**
 * Aggregate queries through the REST API ({@code POST /cxs/query/{type}/{property}}).
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class AggregateQueryIT extends BaseIT {

    @Test
    public void unsupportedDateIntervalIsABadRequest() throws Exception {
        assertEquals(400, dateAggregateStatus("2d"));
        assertEquals(400, dateAggregateStatus("day"));
        // not text: used to fail with a ClassCastException (HTTP 500)
        assertEquals(400, dateAggregateStatusForIntervalJson("2"));
    }

    @Test
    public void supportedDateIntervalsAreAccepted() throws Exception {
        assertEquals(200, dateAggregateStatus("1d"));
        assertEquals(200, dateAggregateStatus("Day"));
    }

    private int dateAggregateStatus(String interval) throws Exception {
        return dateAggregateStatusForIntervalJson("\"" + interval + "\"");
    }

    private int dateAggregateStatusForIntervalJson(String intervalJson) throws Exception {
        HttpPost request = new HttpPost(getFullUrl("/cxs/query/profile/properties.lastVisit"));
        request.setEntity(new StringEntity("{\"condition\":{\"type\":\"matchAllCondition\",\"parameterValues\":{}},"
                + "\"aggregate\":{\"type\":\"date\",\"parameters\":{\"interval\":" + intervalJson + "}}}",
                ContentType.APPLICATION_JSON));
        try (CloseableHttpResponse response = executeHttpRequest(request)) {
            return response.getStatusLine().getStatusCode();
        }
    }
}
