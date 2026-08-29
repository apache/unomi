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

package org.apache.unomi.geonames.services;

import org.apache.unomi.api.Item;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GeonameEntry extends Item {
    public static final String ITEM_TYPE = "geonameEntry";
    private static final long serialVersionUID = 1L;
    protected String name;
    protected String asciiname;
    protected List<String> alternatenames;

    protected Map<String, Double> location;

    protected String featureClass;
    protected String featureCode;
    protected String countryCode;
    protected List<String> cc2;
    protected String admin1Code;
    protected String admin2Code;
    protected String admin3Code;
    protected String admin4Code;
    protected Long population;
    protected Integer elevation;
    protected String dem;
    protected String timezone;
    protected Date modificationDate;

    public GeonameEntry() {
    }

    public GeonameEntry(String geonameId, String name, String asciiname, Double lat, Double lon, String featureClass, String featureCode, String countryCode, List<String> cc2, String admin1Code, String admin2Code, String admin3Code, String admin4Code, Long population, Integer elevation, String dem, String timezone, Date modificationDate) {
        super(geonameId);
        this.name = name;
        this.asciiname = asciiname;
//        this.alternatenames = alternatenames;
        this.location = new LinkedHashMap<>();
        this.location.put("lat",lat);
        this.location.put("lon",lon);
        this.featureClass = featureClass;
        this.featureCode = featureCode;
        this.countryCode = countryCode;
        this.cc2 = cc2;
        this.admin1Code = admin1Code;
        this.admin2Code = admin2Code;
        this.admin3Code = admin3Code;
        this.admin4Code = admin4Code;
        this.population = population;
        this.elevation = elevation;
        this.dem = dem;
        this.timezone = timezone;
        this.modificationDate = modificationDate;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAsciiname() {
        return asciiname;
    }

    public void setAsciiname(String asciiname) {
        this.asciiname = asciiname;
    }

//    public List<String> getAlternatenames() {
//        return alternatenames;
//    }
//
//    public void setAlternatenames(List<String> alternatenames) {
//        this.alternatenames = alternatenames;
//    }

    public Map<String, Double> getLocation() {
        return location;
    }

    public void setLocation(Map<String, Double> location) {
        this.location = location;
    }

    public String getFeatureClass() {
        return featureClass;
    }

    public void setFeatureClass(String featureClass) {
        this.featureClass = featureClass;
    }

    public String getFeatureCode() {
        return featureCode;
    }

    public void setFeatureCode(String featureCode) {
        this.featureCode = featureCode;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public List<String> getCc2() {
        return cc2;
    }

    public void setCc2(List<String> cc2) {
        this.cc2 = cc2;
    }

    public String getAdmin1Code() {
        return admin1Code;
    }

    public void setAdmin1Code(String admin1Code) {
        this.admin1Code = admin1Code;
    }

    public String getAdmin2Code() {
        return admin2Code;
    }

    public void setAdmin2Code(String admin2Code) {
        this.admin2Code = admin2Code;
    }

    public String getAdmin3Code() {
        return admin3Code;
    }

    public void setAdmin3Code(String admin3Code) {
        this.admin3Code = admin3Code;
    }

    public String getAdmin4Code() {
        return admin4Code;
    }

    public void setAdmin4Code(String admin4Code) {
        this.admin4Code = admin4Code;
    }

    public Long getPopulation() {
        return population;
    }

    public void setPopulation(Long population) {
        this.population = population;
    }

    public Integer getElevation() {
        return elevation;
    }

    public void setElevation(Integer elevation) {
        this.elevation = elevation;
    }

    public String getDem() {
        return dem;
    }

    public void setDem(String dem) {
        this.dem = dem;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public Date getModificationDate() {
        return modificationDate;
    }

    public void setModificationDate(Date modificationDate) {
        this.modificationDate = modificationDate;
    }

}
