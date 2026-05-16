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
package org.apache.unomi.api.conditions;

import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.utils.YamlUtils;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link Condition} behavior (parameters, YAML, deep copy).
 */
public class ConditionTest {

    @Test
    public void testSetParameterValuesNullReplacesWithEmptyMap() {
        Condition c = new Condition();
        c.setConditionTypeId("t");
        c.getParameterValues().put("k", "v");
        c.setParameterValues(null);
        assertNotNull(c.getParameterValues());
        assertTrue(c.getParameterValues().isEmpty());
        assertFalse(c.containsParameter("k"));
        assertNull(c.getParameter("k"));
    }

    @Test
    public void testSetParameterAfterClearingParameterValues() {
        Condition c = new Condition();
        c.setConditionTypeId("t");
        c.setParameterValues(null);
        c.setParameter("x", 1);
        assertEquals(Integer.valueOf(1), c.getParameter("x"));
        assertTrue(c.containsParameter("x"));
    }

    @Test
    public void testToYamlMaxDepthZeroUsesPlaceholder() {
        Condition c = new Condition();
        c.setConditionTypeId("myType");
        c.getParameterValues().put("p", "v");
        Map<String, Object> y = c.toYaml(null, 0);
        assertEquals("myType", y.get("type"));
        assertEquals("<max depth exceeded>", y.get("parameterValues"));
    }

    @Test
    public void testToYamlMaxDepthZeroDefaultTypeWhenIdMissing() {
        Condition c = new Condition();
        Map<String, Object> y = c.toYaml(null, 0);
        assertEquals("Condition", y.get("type"));
    }

    @Test
    public void testToYamlWhenAlreadyVisitedReturnsCircularMarker() {
        Condition c = new Condition();
        c.setConditionTypeId("t");
        Set<Object> visited = YamlUtils.newIdentityVisitedSet();
        visited.add(c);
        Map<String, Object> y = c.toYaml(visited, 5);
        assertEquals("circular", y.get("$ref"));
    }

    @Test
    public void testToYamlOmitsParameterValuesWhenEmpty() {
        Condition c = new Condition();
        c.setConditionTypeId("onlyType");
        Map<String, Object> y = c.toYaml(null, 10);
        assertFalse(y.containsKey("parameterValues"));
    }

    @Test
    public void testToStringUsesYamlFormat() {
        Condition c = new Condition();
        c.setConditionTypeId("ctype");
        String s = c.toString();
        assertNotNull(s);
        assertTrue(s.contains("ctype"));
    }

    @Test
    public void testDeepCopyPreservesConditionTypeIdOnly() {
        Condition c = new Condition();
        c.setConditionTypeId("idOnly");
        Condition copy = c.deepCopy();
        assertNotSame(c, copy);
        assertEquals("idOnly", copy.getConditionTypeId());
        assertNull(copy.getConditionType());
    }

    @Test
    public void testDeepCopyPreservesConditionTypeReference() {
        ConditionType ct = new ConditionType(new Metadata("meta-ct"));
        ct.setItemId("evaluatorType");
        Condition c = new Condition(ct);
        Condition copy = c.deepCopy();
        assertSame(ct, copy.getConditionType());
        assertEquals("evaluatorType", copy.getConditionTypeId());
    }

    @Test
    public void testDeepCopyNestedConditionInSetBecomesArrayList() {
        Condition inner = new Condition();
        inner.setConditionTypeId("inner");
        Condition outer = new Condition();
        outer.setConditionTypeId("outer");
        Set<Condition> nested = new LinkedHashSet<>();
        nested.add(inner);
        outer.getParameterValues().put("conds", nested);

        Condition copy = outer.deepCopy();
        Object copiedVal = copy.getParameterValues().get("conds");
        assertTrue(copiedVal instanceof ArrayList);
        @SuppressWarnings("unchecked")
        Collection<Condition> col = (Collection<Condition>) copiedVal;
        assertEquals(1, col.size());
        Condition copyInner = col.iterator().next();
        assertNotSame(inner, copyInner);
        assertEquals("inner", copyInner.getConditionTypeId());
    }

    @Test(expected = IllegalStateException.class)
    public void testDeepCopyRejectsSelfReferenceInParameterMap() {
        Condition c = new Condition();
        c.setConditionTypeId("self");
        c.getParameterValues().put("me", c);
        c.deepCopy();
    }

    @Test(expected = IllegalStateException.class)
    public void testDeepCopyRejectsSelfInSingletonCollection() {
        Condition c = new Condition();
        c.setConditionTypeId("self");
        c.getParameterValues().put("list", Collections.singletonList(c));
        c.deepCopy();
    }

    @Test
    public void testEqualsAndHashCode() {
        Condition a = new Condition();
        a.setConditionTypeId("t");
        a.getParameterValues().put("k", 1);
        Condition b = new Condition();
        b.setConditionTypeId("t");
        b.getParameterValues().put("k", 1);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
