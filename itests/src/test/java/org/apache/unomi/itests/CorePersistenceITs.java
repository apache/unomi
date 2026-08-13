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
package org.apache.unomi.itests;

import org.apache.unomi.itests.migration.Migrate16xToCurrentVersionIT;
import org.apache.unomi.itests.graphql.*;
import org.apache.unomi.itests.migration.MigrationIT;
import org.apache.unomi.itests.shell.*;
import org.junit.runner.RunWith;
import org.junit.runners.Suite.SuiteClasses;

/**
 * Full behavioural IT suite for any {@code PersistenceService} provider.
 * <p>
 * Membership matches {@link AllITs}. Tests that need HTTP admin / snapshot / rollover
 * APIs use {@link org.junit.Assume} on {@link org.apache.unomi.itests.persistence.PersistenceITCapabilities}
 * so unsupported backends <em>skip</em> (not fail, not false-pass). Prefer this suite for
 * PostgreSQL, in-memory, JDBC, etc.; Elasticsearch / OpenSearch CI may keep using {@link AllITs}.
 */
@RunWith(ProgressSuite.class)
@SuiteClasses({
        Migrate16xToCurrentVersionIT.class,
        MigrationIT.class,
        PersistenceServiceIT.class,
        BasicIT.class,
        ConditionEvaluatorIT.class,
        ConditionQueryBuilderIT.class,
        SegmentIT.class,
        ProfileServiceIT.class,
        PersonaIT.class,
        ProfileImportBasicIT.class,
        ProfileImportSurfersIT.class,
        ProfileImportRankingIT.class,
        ProfileImportActorsIT.class,
        ProfileExportIT.class,
        ProfileMergeIT.class,
        EventServiceIT.class,
        PropertiesUpdateActionIT.class,
        CopyPropertiesActionIT.class,
        IncrementPropertyIT.class,
        InputValidationIT.class,
        RestCreateValidationIT.class,
        ModifyConsentIT.class,
        PatchIT.class,
        ContextServletIT.class,
        SecurityIT.class,
        RuleServiceIT.class,
        PrivacyServiceIT.class,
        GroovyActionsServiceIT.class,
        GroovyActionsEndpointRoleSecurityIT.class,
        GraphQLEventIT.class,
        GraphQLListIT.class,
        GraphQLProfileIT.class,
        GraphQLProfilePropertiesIT.class,
        GraphQLSegmentIT.class,
        GraphQLWebSocketIT.class,
        JSONSchemaIT.class,
        GraphQLProfileAliasesIT.class,
        SendEventActionIT.class,
        ScopeIT.class,
        V2CompatibilityModeIT.class,
        CrudCommandsIT.class,
        CacheCommandsIT.class,
        TailCommandsIT.class,
        SchedulerCommandsIT.class,
        TenantCommandsIT.class,
        RuleStatisticsCommandsIT.class,
        OtherCommandsIT.class,
        LegacyQueryBuilderMappingIT.class,
        TenantIT.class,
        SchedulerIT.class,
        EventsCollectorIT.class,
        RolloverIT.class,
        HealthCheckIT.class
})
public class CorePersistenceITs {
}
