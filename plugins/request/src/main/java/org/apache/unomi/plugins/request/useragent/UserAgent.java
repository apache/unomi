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

package org.apache.unomi.plugins.request.useragent;

/**
 * Basic information about a User Agent
 */
public class UserAgent {

    private String operatingSystemFamily;
    private String operatingSystemName;
    private String userAgentName;
    private String userAgentVersion;
    private String deviceCategory;
    private String deviceBrand;
    private String deviceName;

    public String getOperatingSystemFamily() {
        return operatingSystemFamily;
    }

    public void setOperatingSystemFamily(String operatingSystemFamily) {
        this.operatingSystemFamily = operatingSystemFamily;
    }

    public String getOperatingSystemName() {
        return operatingSystemName;
    }

    public void setOperatingSystemName(String operatingSystemName) {
        this.operatingSystemName = operatingSystemName;
    }

    public String getUserAgentName() {
        return userAgentName;
    }

    public void setUserAgentName(String userAgentName) {
        this.userAgentName = userAgentName;
    }

    public String getUserAgentVersion() {
        return userAgentVersion;
    }

    public void setUserAgentVersion(String userAgentVersion) {
        this.userAgentVersion = userAgentVersion;
    }

    public String getDeviceCategory() {
        return deviceCategory;
    }

    public void setDeviceCategory(String deviceCategory) {
        this.deviceCategory = deviceCategory;
    }

    public String getDeviceBrand() {
        return deviceBrand;
    }

    public void setDeviceBrand(String deviceBrand) {
        this.deviceBrand = deviceBrand;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getUserAgentNameAndVersion() {
        return this.userAgentName + "@@" + this.userAgentVersion;
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("User-Agent { \n");
        sb.append("agent.name: " + this.getUserAgentName() + ",\n");
        sb.append("agent.version: " + this.getUserAgentVersion() + ",\n");
        sb.append("operatingsystem.family: " + this.getOperatingSystemFamily() + ",\n");
        sb.append("operatingsystem.name: " + this.getOperatingSystemName() + ",\n");
        sb.append("device.category: " + this.getDeviceCategory() + " \n}");
        sb.append("device.brand: " + this.getDeviceBrand() + " \n}");
        sb.append("device.name: " + this.getDeviceName() + " \n}");
        return super.toString();
    }
}
