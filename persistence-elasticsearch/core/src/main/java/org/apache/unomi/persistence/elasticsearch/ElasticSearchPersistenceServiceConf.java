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
package org.apache.unomi.persistence.elasticsearch;

import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "ElasticSearch persistence service config", description = "The configuration for the ElasticSearch persistence service")
public @interface ElasticSearchPersistenceServiceConf {

    String minimalElasticSearchVersion() default "7.0.0";
    String maximalElasticSearchVersion() default "8.0.0";
    String cluster_name() default "contextElasticSearch";
    String elasticSearchAddresses() default "localhost:9200";
    String index_prefix() default "context";
    String username();
    String password();
    boolean sslEnable() default false;
    boolean sslTrustAllCertificates() default false;
    boolean throwExceptions() default false;
    boolean alwaysOverwrite() default true;
    String logLevelRestClient() default "ERROR";
    String fatalIllegalStateErrors();

    String numberOfShards() default "5";
    String numberOfReplicas() default "0";
    String indexMappingTotalFieldsLimit() default "1000";
    String indexMaxDocValueFieldsSearch() default "1000";

    String monthlyIndex_numberOfShards() default "3"; /* Deprecate use rollover prop instead */
    String monthlyIndex_numberOfReplicas() default "0"; /* Deprecate use rollover prop instead */
    String monthlyIndex_indexMappingTotalFieldsLimit() default "1000"; /* Deprecate use rollover prop instead */
    String monthlyIndex_indexMaxDocValueFieldsSearch() default "1000"; /* Deprecate use rollover prop instead */
    String monthlyIndex_itemsMonthlyIndexedOverride() default "event,session"; /* Deprecate use rollover prop instead */
    String rollover_numberOfShards();
    String rollover_numberOfReplicas();
    String rollover_indexMappingTotalFieldsLimit();
    String rollover_indexMaxDocValueFieldsSearch();
    String rollover_indices();
    String rollover_maxSize();
    String rollover_maxAge() default "365d";
    String rollover_maxDocs();

    int defaultQueryLimit() default 10;
    int removeByQueryTimeoutInMinutes() default  10;
    int aggregateQueryBucketSize() default 5000;
    int clientSocketTimeout() default -1;
    int aggQueryMaxResponseSizeHttp() default -1;
    boolean aggQueryThrowOnMissingDocs() default false;
    boolean useBatchingForSave() default false;
    boolean useBatchingForUpdate() default true;
    String itemTypeToRefreshPolicy();

    int bulkProcessor_concurrentRequests() default 1;
    int bulkProcessor_bulkActions() default 1000;
    String bulkProcessor_bulkSize() default "5MB";
    String bulkProcessor_flushInterval() default "5s";
    String bulkProcessor_backoffPolicy() default "exponential";
}