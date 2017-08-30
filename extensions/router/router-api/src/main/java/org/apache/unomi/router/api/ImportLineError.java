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

package org.apache.unomi.router.api;

/**
 * A line error object to carry failure
 */
public class ImportLineError {

    private long lineNb;
    private String errorCode;
    private String lineContent;

    /**
     * Retrieves the number of the line which failed to be imported
     * @return lineNb
     */
    public long getLineNb() {
        return lineNb;
    }

    /**
     * Sets the number of the line which failed to be imported
     * @param lineNb new value for lineNb
     */
    public void setLineNb(long lineNb) {
        this.lineNb = lineNb;
    }

    /**
     * Retrieves the error code
     * @return errorCode
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * Sets the error code
     * @param errorCode new value for errorCode
     */
    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    /**
     * Retrieves the line original content
     * @return lineContent
     */
    public String getLineContent() {
        return lineContent;
    }

    /**
     * Sets the line content
     * @param lineContent cd per    new value for lineContent
     */
    public void setLineContent(String lineContent) {
        this.lineContent = lineContent;
    }
}
