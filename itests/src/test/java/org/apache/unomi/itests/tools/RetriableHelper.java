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

package org.apache.unomi.itests.tools;

import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;

/**
 * Just an utility class to do some retriable stuff in ITests
 * Useful when you are waiting for something to be indexed in ES for exemple, you just retry until your object is available
 * @param <T> The type of object you are expecting to return from this retry instance
 */
public class RetriableHelper<T> implements Callable<T> {

    private final static Logger LOGGER = LoggerFactory.getLogger(RetriableHelper.class);

    private final Callable<T> task;
    private final long timeToWait;
    private final String key;

    private int numberOfTriesLeft;


    public RetriableHelper(String key, int numberOfRetries, long timeToWait, Callable<T> task) {
        this.key = key;
        this.numberOfTriesLeft = numberOfRetries;
        this.timeToWait = timeToWait;
        this.task = task;
    }

    public T call() throws Exception {
        while (true) {
            try {
                return task.call();
            } catch (InterruptedException | CancellationException e) {
                throw e;
            } catch (Exception e) {
                numberOfTriesLeft--;
                LOGGER.warn("RETRY: {} failed, number of tries left: {}, will wait {}ms before next try", key, numberOfTriesLeft, timeToWait);
                if (numberOfTriesLeft == 0) {
                    Assert.fail("RETRY LIMIT REACH: " + e.getMessage());
                }
                Thread.sleep(timeToWait);
            }
        }
    }
}