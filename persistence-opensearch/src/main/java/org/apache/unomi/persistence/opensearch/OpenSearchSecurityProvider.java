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
package org.apache.unomi.persistence.opensearch;

import org.apache.unomi.persistence.spi.SecurityProvider;
import org.apache.unomi.persistence.spi.Query;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.ResponseException;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Reference;
import java.io.IOException;

@Component(property = {"search.engine=opensearch"})
public class OpenSearchSecurityProvider implements SecurityProvider {

    @Reference
    private RestHighLevelClient client;

    @Override
    public void setupTenant(String tenantId, String tenantName) throws IOException {
        // Create role
        Request createRoleRequest = new Request("PUT",
            "/_plugins/_security/api/roles/tenant_" + tenantId);
        createRoleRequest.setJsonEntity(String.format(
            "{\"cluster_permissions\":[\"cluster:monitor/*\"]," +
            "\"index_permissions\":[{" +
            "\"index_patterns\":[\"unomi-*\"]," +
            "\"dls\":\"{\\\"term\\\":{\\\"tenantId\\\":\\\"%s\\\"}}\"," +
            "\"allowed_actions\":[\"read\",\"write\",\"delete\"]}]}", tenantId));

        Response roleResponse = client.getLowLevelClient().performRequest(createRoleRequest);
        if (roleResponse.getStatusLine().getStatusCode() != 200) {
            throw new IOException("Failed to create role for tenants " + tenantId);
        }
    }

    @Override
    public Query addTenantSecurity(Query query, String tenantId) {
        // OpenSearch handles security at the cluster level
        return query;
    }
}
