<!--
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
  -->


Apache Unomi Weather Update
=================================

This extension will retrieve the weather associated with the resolved location of the user (from his IP address)

## Getting started

1.  Create an new account :

        https://home.openweathermap.org/users/sign_up
           
2.  Generate a new API Key, or get the default

        https://home.openweathermap.org/api_keys

2.  Configure the Apache Unomi Weather Update. In the etc/org.apache.unomi.weatherUpdate.cfg file 
change the following settings:

         weatherUpdate.apiKey=YOUR_WEATHER_APIKEY
  
           
3.  Deploy into Apache Unomi using the following commands from the Apache Karaf shell:

        feature:repo-add mvn:org.apache.unomi/unomi-weather-update-karaf-kar/${project.version}/xml/features
        feature:install unomi-weather-update-karaf-kar
