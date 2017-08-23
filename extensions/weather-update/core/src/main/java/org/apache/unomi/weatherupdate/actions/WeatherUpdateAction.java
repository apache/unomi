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

    private static final double KELVIN = 273.15;
    private static final double ROUND_TO_THE_TENTH = 0.5;
    private static final double SECOND_TO_HOUR = 3.6;
    private static final String MAIN_INFO_WEATHER = "main";
    private static final String SPEED = "speed";
    private static final String STATUS_CODE = "cod";
    private static final String TEMPERATURE_VALUE = "temp";
    private static final String WEATHER_LIKE_INFO = "weather";
    private static final String WIND = "wind";
    private static final String WIND_DIRECTION_INFO = "deg";
    private static final String WEATHER_TEMPERATURE = "weatherTemperature";
    private static final String WEATHER_LIKE = "weatherLike";
    private static final String WEATHER_WIND_DIRECTION = "weatherWindDirection";
    private static final String WEATHER_WIND_SPEED = "weatherWindSpeed";
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
        if (weatherApiKey == null || weatherUrlBase == null || weatherUrlAttributes == null) {
            logger.warn("Configuration incomplete.");
            return EventService.NO_CHANGE;
        }

        Map<String, Object> sessionProperties = session.getProperties();
        if (!sessionProperties.containsKey("location")) {
            logger.warn("No location info found in the session.");
            return EventService.NO_CHANGE;
        }

        Map<String, Double> location = (Map<String, Double>) session.getProperty("location");

        JsonNode currentWeatherData = getWeather(location);


        if (currentWeatherData.has(STATUS_CODE) && currentWeatherData.get(STATUS_CODE).asText().equals("200")) {
            updateSessionWithWeatherData(currentWeatherData,session);
            return EventService.SESSION_UPDATED;
        }else {
            if (currentWeatherData.has("message"))
                logger.warn(currentWeatherData.get("message").asText());
        }

        logger.warn("No update made.");
        return EventService.NO_CHANGE;
    }

    private JsonNode getWeather(Map<String, Double> location) {
        //Call to OpenWeatherMap
        HttpGet httpGet = new HttpGet(weatherUrlBase + "/" + weatherUrlAttributes +
                "?lat=" + location.get("lat") + "&lon=" + location.get("lon") + "&appid=" + weatherApiKey);
        JsonNode currentWeatherData = null;
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
                        currentWeatherData = objectMapper.readTree(responseString);
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
        return currentWeatherData;
    }

    private void updateSessionWithWeatherData(JsonNode currentWeatherData, Session session) {
        String temperature = extractTemperature(currentWeatherData);
        String weatherLike = extractWeatherLike(currentWeatherData);
        String windDirection = extractWindDirection(currentWeatherData);
        String windSpeed = extractWindSpeed(currentWeatherData);
        if (temperature != null) {
            fillPropreties(session, WEATHER_TEMPERATURE, temperature);
        }
        if (weatherLike != null) {
            fillPropreties(session, WEATHER_LIKE, weatherLike);
        }
        if (windDirection != null) {
            fillPropreties(session, WEATHER_WIND_DIRECTION, windDirection);
        }
        if (windSpeed != null) {
            fillPropreties(session, WEATHER_WIND_SPEED, windSpeed);
        }
    }

    /**
     * Extract the temperature property from the response
     *
     * @param currentWeatherData
     * @return String temperature in celsius
     */
    private String extractTemperature(JsonNode currentWeatherData) {
        float temperature;
        if (currentWeatherData.has(MAIN_INFO_WEATHER) && currentWeatherData.get(MAIN_INFO_WEATHER).has(TEMPERATURE_VALUE)) {
            String responseString = currentWeatherData.get(MAIN_INFO_WEATHER).get(TEMPERATURE_VALUE).asText();
            temperature = Float.parseFloat(responseString);
            temperature -= KELVIN;
            int temperatureTreated = (int) temperature;
            if (temperature - temperatureTreated > ROUND_TO_THE_TENTH) {
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
     * @param currentWeatherData
     * @return String wind speed in km/h
     */
    private String extractWindSpeed(JsonNode currentWeatherData) {
        JsonNode WindInfoSpeed;
        if (currentWeatherData.has(WIND) && currentWeatherData.get(WIND).has(SPEED)) {
            WindInfoSpeed = currentWeatherData.get(WIND).get(SPEED);
            float speed = Float.parseFloat(WindInfoSpeed.toString());
            speed *= SECOND_TO_HOUR;
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
     * @param currentWeatherData
     * @return String wind direction in cardinal points format
     */
    private String extractWindDirection(JsonNode currentWeatherData) {
        JsonNode windInfoDirection;
        String direction = "";
        if (currentWeatherData.has(WIND)) {
            if (currentWeatherData.get(WIND).has(WIND_DIRECTION_INFO)) {
                windInfoDirection = currentWeatherData.get(WIND).get(WIND_DIRECTION_INFO);
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
        }
        logger.info("API Response doesn't contains the wind direction");
        return null;
    }

    /**
     * Extract the weather like property from the response
     *
     * @param currentWeatherData
     * @return String weather like
     */
    private String extractWeatherLike(JsonNode currentWeatherData) {
        JsonNode weatherLike;
        if (currentWeatherData.has(WEATHER_LIKE_INFO)) {
            weatherLike = currentWeatherData.get(WEATHER_LIKE_INFO);
            if (weatherLike.size() > 0) {
                weatherLike = weatherLike.get(0).get(MAIN_INFO_WEATHER);
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


