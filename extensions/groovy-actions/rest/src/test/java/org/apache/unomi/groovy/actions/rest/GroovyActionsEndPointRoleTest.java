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
package org.apache.unomi.groovy.actions.rest;

import org.apache.unomi.api.security.UnomiRoles;
import org.apache.unomi.rest.security.RequiresRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Regression: Groovy upload/delete must stay restricted to system administrators.
 */
class GroovyActionsEndPointRoleTest {

    @Test
    void endpointRequiresSystemAdministratorRole() {
        RequiresRole requiresRole = GroovyActionsEndPoint.class.getAnnotation(RequiresRole.class);
        assertNotNull(requiresRole, "GroovyActionsEndPoint must declare @RequiresRole");
        assertArrayEquals(new String[]{UnomiRoles.ADMINISTRATOR}, requiresRole.value());
    }
}
