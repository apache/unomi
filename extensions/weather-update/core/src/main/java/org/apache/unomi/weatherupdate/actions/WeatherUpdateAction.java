/*

        ~ Licensed to the Apache Software Foundation (ASF) under one or more
        ~ contributor license agreements.  See the NOTICE file distributed with
        ~ this work for additional information regarding copyright ownership.
        ~ The ASF licenses this file to You under the Apache License, Version 2.0
        ~ (the "License"); you may not use this file except in compliance with
        ~ the License.  You may obtain a copy of the License at
        ~
        ~      http://www.apache.org/licenses/LICENSE-2.0
        ~
        ~ Unless required by applicable law or agreed to in writing, software
        ~ distributed under the License is distributed on an "AS IS" BASIS,
        ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
        ~ See the License for the specific language governing permissions and
        ~ limitations under the License.
*/

package org.apache.unomi.weatherupdate.actions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.Session;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.actions.ActionExecutor;
import org.apache.unomi.api.services.EventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

/**
 * Get the weather location of the users by an API
 *
 * @author dsalhotra
 */
public class WeatherUpdateAction implements ActionExecutor {
    private static Logger logger = LoggerFactory.getLogger(WeatherUpdateAction.class);
    private CloseableHttpClient httpClient;

    private String weatherApiKey;
    private String weatherUrlBase;
    private String weatherUrlAttributes;

    @Override
    public int execute(Action action, Event event) {
        if (httpClient == null) {
            httpClient = HttpClients.createDefault();
        }

        Session session = event.getSession();
        if (!(weatherApiKey == null || weatherUrlBase == null || weatherUrlAttributes == null)) {
            Map<String, Object> sessionProperties = session.getProperties();
            if (sessionProperties.containsKey("location")) {
                Map<String, Double> location = (Map<String, Double>) session.getProperty("location");
                HttpGet httpGet = new HttpGet(weatherUrlBase + "/" + weatherUrlAttributes +
                        "?lat=" + location.get("lat") + "&lon=" + location.get("lon") + "&appid=" + weatherApiKey);
                JsonNode jsonNode = null;
                CloseableHttpResponse response = null;
                try {
                    response = httpClient.execute(httpGet);
                    if (response != null) {
                        HttpEntity entity = response.getEntity();
                        String responseString;
                        if (entity != null) {
                            try {
                                responseString = EntityUtils.toString(entity);
                                ObjectMapper objectMapper = new ObjectMapper();
                                jsonNode = objectMapper.readTree(responseString);
                            } catch (IOException e) {
                                logger.error("Error : With the API json response.", e.getMessage());
                            }
                        }
                    }
                } catch (IOException e) {
                    logger.error("Error : With the Http Request execution. Wrong parameters given", e.getMessage());
                } finally {
                    if (response != null) {
                        EntityUtils.consumeQuietly(response.getEntity());
                    }
                }

                if (jsonNode.has("cod")) {
                    if (jsonNode.get("cod").asText().equals("200")) {
                        String temperature = extractTemperature(jsonNode);
                        String weatherLike = extractWeatherLike(jsonNode);
                        String windDirection = extractWindDirection(jsonNode);
                        String windSpeed = extractWindSpeed(jsonNode);
                        if (temperature != null) {
                            fillPropreties(session, "weatherTemperature", temperature);
                        }
                        if (weatherLike != null) {
                            fillPropreties(session, "weatherLike", weatherLike);
                        }
                        if (windDirection != null) {
                            fillPropreties(session, "weatherWindDirection", windDirection);
                        }
                        if (windSpeed != null) {
                            fillPropreties(session, "weatherWindSpeed", windSpeed);
                        }
                        return EventService.SESSION_UPDATED;
                    }
                }
            }
        }
        logger.info("No update made.");
        return EventService.NO_CHANGE;
    }

    /**
     * Extract the temperature property from the response
     *
     * @param jsonNode
     * @return String temperature in celsius
     */
    private String extractTemperature(JsonNode jsonNode) {
        float temperature;
        if (jsonNode.has("main") && jsonNode.get("main").has("temp")) {
            String responseString = jsonNode.get("main").get("temp").asText();
            temperature = Float.parseFloat(responseString);
            temperature -= 273.15;
            int temperatureTreated = (int) temperature;
            if (temperature - temperatureTreated > 0.5) {
                temperatureTreated++;
            }
            logger.debug("Temperature: " + temperatureTreated);
            return temperatureTreated + "";
        }
        logger.info("API Response doesn't contains the temperature");
        return null;
    }

    /**
     * Extract the wind speed property from the response
     *
     * @param jsonNode
     * @return String wind speed in km/h
     */
    private String extractWindSpeed(JsonNode jsonNode) {
        JsonNode WindInfoSpeed;
        if (jsonNode.has("wind") && jsonNode.get("wind").has("speed")) {
            WindInfoSpeed = jsonNode.get("wind").get("speed");
            float speed = Float.parseFloat(WindInfoSpeed.toString());
            speed *= 3.6;
            int speedTreated = (int) speed;
            logger.debug("Wind speed: " + speedTreated);
            return speedTreated + "";
        }
        logger.info("API Response doesn't contains the wind speed");
        return null;

    }

    /**
     * Extract the wind direction property from the response
     *
     * @param jsonNode
     * @return String wind direction in cardinal points format
     */
    private String extractWindDirection(JsonNode jsonNode) {
        JsonNode windInfoDirection;
        String direction = "";
        if (jsonNode.has("wind")) {
            windInfoDirection = jsonNode.get("wind").get("deg");
            if (windInfoDirection != null) {

                float deg = Float.parseFloat(windInfoDirection.toString());
                if (340 < deg && deg < 360 || 0 < deg && deg < 20) {
                    direction = ("N");
                } else if (20 < deg && deg < 70) {
                    direction = ("NE");
                } else if (70 < deg && deg < 110) {
                    direction = ("E");
                } else if (110 < deg && deg < 160) {
                    direction = ("SE");
                } else if (160 < deg && deg < 200) {
                    direction = ("S");
                } else if (200 < deg && deg < 245) {
                    direction = ("SW");
                } else if (245 < deg && deg < 290) {
                    direction = ("W");
                } else if (290 < deg && deg < 340) {
                    direction = ("NW");
                }
                logger.debug("Wind direction: " + direction);
                return direction;
            }
        }
        logger.info("API Response doesn't contains the wind direction");
        return null;
    }

    /**
     * Extract the weather like property from the response
     *
     * @param jsonNode
     * @return String weather like
     */
    private String extractWeatherLike(JsonNode jsonNode) {
        JsonNode weatherLike;
        if (jsonNode.has("weather")) {
            weatherLike = jsonNode.get("weather");
            if (weatherLike.size() > 0) {
                weatherLike = weatherLike.get(0).get("main");
                logger.debug("Weather like: " + weatherLike);
                return weatherLike.asText();
            }
        }
        logger.info("API Response doesn't contains the weather description");
        return null;
    }

    /**
     * @param session  the current session
     * @param property session property to fill
     * @param value    of property
     */
    private void fillPropreties(Session session, String property, String value) {
        session.setProperty(property, value);
    }

    //Setters

    /**
     * +
     * Set the weatherApiKey
     *
     * @param weatherApiKey
     */
    public void setWeatherApiKey(String weatherApiKey) {
        this.weatherApiKey = weatherApiKey;
    }

    /**
     * Set the weatherUrlBase
     *
     * @param weatherUrlBase
     */
    public void setWeatherUrlBase(String weatherUrlBase) {
        this.weatherUrlBase = weatherUrlBase;
    }

    /**
     * Set the weatherUrlAttributes
     *
     * @param weatherUrlAttributes
     */
    public void setWeatherUrlAttributes(String weatherUrlAttributes) {
        this.weatherUrlAttributes = weatherUrlAttributes;
    }
}


