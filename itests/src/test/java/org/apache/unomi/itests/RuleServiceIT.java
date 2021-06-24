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

import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.rules.Rule;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.RulesService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.junit.Before;
import org.junit.Test;
import org.ops4j.pax.exam.util.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Set;

import static org.junit.Assert.assertEquals;

/**
 * Integration tests for the Unomi rule service.
 */
public class RuleServiceIT extends BaseIT {

    private final static Logger LOGGER = LoggerFactory.getLogger(RuleServiceIT.class);

    private final static String TEST_RULE_ID = "test-rule-id";
    public static final String TEST_SCOPE = "test-scope";

    @Inject
    @Filter(timeout = 600000)
    protected RulesService rulesService;

    @Inject
    @Filter(timeout = 600000)
    protected PersistenceService persistenceService;

    @Inject
    @Filter(timeout = 600000)
    protected DefinitionsService definitionsService;

    @Before
    public void setUp() {
        TestUtils.removeAllProfiles(definitionsService, persistenceService);
    }

    @Test
    public void testRuleWithNullActions() throws InterruptedException {
        Set<Metadata> ruleMetadatas = rulesService.getRuleMetadatas();
        int initialRuleCount = ruleMetadatas.size();
        Metadata metadata = new Metadata(TEST_RULE_ID);
        metadata.setName(TEST_RULE_ID + "_name");
        metadata.setDescription(TEST_RULE_ID + "_description");
        metadata.setScope(TEST_SCOPE);
        Rule nullRule = new Rule(metadata);
        nullRule.setCondition(null);
        nullRule.setActions(null);
        rulesService.setRule(nullRule);
        LOGGER.info("Waiting for rules to refresh from persistence...");
        int loopCount = 0;
        int lastRuleCount = initialRuleCount;
        while (loopCount < 20 && lastRuleCount == initialRuleCount) {
            Thread.sleep(1000);
            ruleMetadatas = rulesService.getRuleMetadatas();
            lastRuleCount = ruleMetadatas.size();
            loopCount++;
        }
        assertEquals("Rule not properly saved", initialRuleCount + 1, lastRuleCount);
    }
}
