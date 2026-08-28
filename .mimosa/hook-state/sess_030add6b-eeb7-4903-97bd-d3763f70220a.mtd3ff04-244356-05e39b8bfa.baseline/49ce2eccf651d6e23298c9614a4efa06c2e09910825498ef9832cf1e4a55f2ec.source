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

import javax.xml.bind.annotation.XmlTransient;
import java.io.Serializable;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A consent is an object attached to a profile that indicates whether the profile has agreed or denied a special
 * consent type. For example a user might have agreed to receiving a newsletter but might have not agreed to being
 * tracked.
 */
public class Consent implements Serializable {

    /**
     * Scope this consent applies to (often a site id).
     * @api.example mysite
     */
    private String scope;
    /**
     * Consent type id defined outside Unomi (newsletter, tracking, ...).
     * @api.example newsletter
     */
    private String typeIdentifier; // type identifiers are defined and managed externally of Apache Unomi
    /**
     * Consent status: {@code GRANTED}, {@code DENIED}, or {@code REVOKED}.
     * @api.example GRANTED
     */
    private ConsentStatus status;
    /**
     * When this status was recorded (ISO-8601 in JSON).
     * @api.example 2024-01-15T09:00:00.000Z
     */
    private Date statusDate;
    /**
     * Optional automatic revoke date; omitted when the consent does not expire.
     * @api.example 2025-01-15T09:00:00.000Z
     */
    private Date revokeDate;

    /**
     * Default constructor for JSON (de)serialization.
     */
    public Consent() {
    }

    /**
     * Creates a consent with all properties set.
     *
     * @param scope the scope for this consent
     * @param typeIdentifier the identifier of the type this consent applies to
     * @param status the consent status ({@link ConsentStatus#DENIED}, {@link ConsentStatus#GRANTED}, or {@link ConsentStatus#REVOKED})
     * @param statusDate the date when this consent was given
     * @param revokeDate the date when this consent will be automatically revoked
     */
    public Consent(String scope, String typeIdentifier, ConsentStatus status, Date statusDate, Date revokeDate) {
        this.scope = scope;
        this.typeIdentifier = typeIdentifier;
        this.status = status;
        this.statusDate = statusDate;
        this.revokeDate = revokeDate;
    }

    /**
     * Creates a consent from a deserialized property map.
     *
     * @param consentMap map with keys {@code typeIdentifier}, {@code status} (GRANTED, DENIED, or REVOKED),
     *                   {@code statusDate}, and {@code revokeDate} as ISO-8601 strings
     * @param dateFormat date format used to parse date strings
     * @throws ParseException if a date string cannot be parsed
     */
    public Consent(Map<String,Object> consentMap, DateFormat dateFormat) throws ParseException {
        if (consentMap.containsKey("scope")) {
            setScope((String) consentMap.get("scope"));
        }
        if (consentMap.containsKey("typeIdentifier")) {
            setTypeIdentifier((String) consentMap.get("typeIdentifier"));
        }
        if (consentMap.containsKey("status")) {
            String consentStatus = (String) consentMap.get("status");
            setStatus(ConsentStatus.valueOf(consentStatus));
        }
        if (consentMap.containsKey("statusDate")) {
            String statusDateStr = (String) consentMap.get("statusDate");
            if (statusDateStr != null && statusDateStr.trim().length() > 0) {
                setStatusDate(dateFormat.parse(statusDateStr));
            }
        }
        if (consentMap.containsKey("revokeDate")) {
            String revokeDateStr = (String) consentMap.get("revokeDate");
            if (revokeDateStr != null && revokeDateStr.trim().length() > 0) {
                setRevokeDate(dateFormat.parse(revokeDateStr));
            }
        }
    }

    /**
     * Scope this consent applies to.
     *
     * @return the scope identifier
     */
    public String getScope() {
        return scope;
    }

    /**
     * Sets the scope for this consent.
     *
     * @param scope the scope identifier
     */
    public void setScope(String scope) {
        this.scope = scope;
    }

    /**
     * Sets the consent type identifier (externally defined, not validated by Unomi).
     *
     * @param typeIdentifier the consent type identifier
     */
    public void setTypeIdentifier(String typeIdentifier) {
        this.typeIdentifier = typeIdentifier;
    }

    /**
     * Consent type identifier.
     *
     * @return the type identifier
     * @api.example newsletter
     */
    public String getTypeIdentifier() {
        return typeIdentifier;
    }

    /**
     * Current consent status.
     *
     * @return the consent status
     */
    public ConsentStatus getStatus() {
        return status;
    }

    /**
     * Sets the consent status. A status of {@link ConsentStatus#REVOKED} means the consent should be removed.
     *
     * @param status the consent status to set
     */
    public void setStatus(ConsentStatus status) {
        this.status = status;
    }

    /**
     * Date when this consent was given. Future dates mean the consent is not yet valid.
     *
     * @return the status date, or {@code null} if unset
     */
    public Date getStatusDate() {
        return statusDate;
    }

    /**
     * Sets the date from which this consent applies.
     *
     * @param statusDate the effective date, or {@code null} for immediate validity
     */
    public void setStatusDate(Date statusDate) {
        this.statusDate = statusDate;
    }

    /**
     * Expiration date after which this consent is no longer valid. {@code null} means no expiry.
     *
     * @return the revoke date, or {@code null} if unlimited
     */
    public Date getRevokeDate() {
        return revokeDate;
    }

    /**
     * Sets the expiration date for this consent. {@code null} means the consent never expires.
     *
     * @param revokeDate the revoke date, or {@code null} for unlimited validity
     */
    public void setRevokeDate(Date revokeDate) {
        this.revokeDate = revokeDate;
    }

    /**
     * Whether this consent is granted at the current time.
     *
     * @return {@code true} if the consent is granted now
     */
    @XmlTransient
    public boolean isConsentGrantedNow() {
        return isConsentGrantedAtDate(new Date());
    }

    /**
     * Whether this consent is granted at the given date.
     *
     * @param testDate the date to test against
     * @return {@code true} if the consent is granted at the specified date
     */
    @XmlTransient
    public boolean isConsentGrantedAtDate(Date testDate) {
        if (getStatusDate().before(testDate) && (getRevokeDate() == null || (getRevokeDate().after(testDate)))) {
            if (getStatus().equals(ConsentStatus.GRANTED)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Serializes this consent to a map using the same format as the map constructor.
     *
     * @param dateFormat date format for {@code statusDate} and {@code revokeDate} entries (ISO-8601 recommended)
     * @return map with keys {@code scope}, {@code typeIdentifier}, {@code status}, {@code statusDate}, and {@code revokeDate}
     */
    @XmlTransient
    public Map<String,Object> toMap(DateFormat dateFormat) {
        Map<String,Object> map = new LinkedHashMap<>();
        map.put("scope", scope);
        map.put("typeIdentifier", typeIdentifier);
        map.put("status", status.toString());
        if (statusDate != null) {
            map.put("statusDate", dateFormat.format(statusDate));
        }
        if (revokeDate != null) {
            map.put("revokeDate", dateFormat.format(revokeDate));
        }
        return map;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Consent{");
        sb.append("scope='").append(scope).append('\'');
        sb.append(", typeIdentifier='").append(typeIdentifier).append('\'');
        sb.append(", status=").append(status);
        sb.append(", statusDate=").append(statusDate);
        sb.append(", revokeDate=").append(revokeDate);
        sb.append('}');
        return sb.toString();
    }
}
