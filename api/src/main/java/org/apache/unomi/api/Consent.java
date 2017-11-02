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
package org.apache.unomi.api;

import java.util.Date;

/**
 * A consent is an object attached to a profile that indicates whether the profile has agreed or denied a special
 * consent type. For example a user might have agreed to receiving a newsletter but might have not agreed to being
 * tracked.
 */
public class Consent extends Item {

    private String typeId; // types are defined and managed externally of Apache Unomi
    private ConsentGrant grant;
    private Date grantDate;
    private Date revokeDate;

    public Consent(String itemId, String typeId, ConsentGrant grant, Date grantDate, Date revokeDate) {
        super(itemId);
        this.typeId = typeId;
        this.grant = grant;
        this.grantDate = grantDate;
        this.revokeDate = revokeDate;
    }

    public void setTypeId(String typeId) {
        this.typeId = typeId;
    }

    public String getTypeId() {
        return typeId;
    }

    public ConsentGrant getGrant() {
        return grant;
    }

    public void setGrant(ConsentGrant grant) {
        this.grant = grant;
    }

    public Date getGrantDate() {
        return grantDate;
    }

    public void setGrantDate(Date grantDate) {
        this.grantDate = grantDate;
    }

    public Date getRevokeDate() {
        return revokeDate;
    }

    public void setRevokeDate(Date revokeDate) {
        this.revokeDate = revokeDate;
    }
}
