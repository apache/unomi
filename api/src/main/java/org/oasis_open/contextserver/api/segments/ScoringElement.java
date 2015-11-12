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

package org.oasis_open.contextserver.api.segments;

import org.oasis_open.contextserver.api.conditions.Condition;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * A scoring dimension along profiles can be evaluated and associated value to be assigned.
 */
@XmlRootElement
public class ScoringElement {
    private Condition condition;
    private int value;

    /**
     * Instantiates a new Scoring element.
     */
    public ScoringElement() {
    }

    /**
     * Retrieves the condition.
     *
     * @return the condition
     */
    public Condition getCondition() {
        return condition;
    }

    /**
     * Sets the condition.
     *
     * @param condition the condition
     */
    public void setCondition(Condition condition) {
        this.condition = condition;
    }

    /**
     * Retrieves the value.
     *
     * @return the value
     */
    public int getValue() {
        return value;
    }

    /**
     * Sets the value.
     *
     * @param value the value
     */
    public void setValue(int value) {
        this.value = value;
    }
}
