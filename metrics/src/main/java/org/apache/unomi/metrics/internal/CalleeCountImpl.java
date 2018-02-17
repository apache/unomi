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
package org.apache.unomi.metrics.internal;

import org.apache.unomi.metrics.CalleeCount;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class CalleeCountImpl implements CalleeCount {

    private String hash;
    private List<String> callee;
    private AtomicLong count = new AtomicLong();
    private AtomicLong totalTime = new AtomicLong();

    public CalleeCountImpl(String hash, List<String> callee) {
        this.hash = hash;
        this.callee = callee;
    }

    @Override
    public String getHash() {
        return hash;
    }

    @Override
    public List<String> getCallee() {
        return callee;
    }

    @Override
    public long getCount() {
        return count.get();
    }

    @Override
    public long incCount() {
        return count.incrementAndGet();
    }

    @Override
    public long getTotalTime() {
        return totalTime.get();
    }

    @Override
    public long addTime(long time) {
        return totalTime.addAndGet(time);
    }

}
