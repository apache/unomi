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
/**
 * Analytics.js
 *
 * (C) 2017 Segment Inc.
 */

var analytics = require('@segment/analytics.js-core');
var Integrations = require('./integrations');

/**
 * Expose the `analytics` singleton.
 */

module.exports = exports = analytics;

/**
 * Expose require.
 */

analytics.require = require

/**
 * Expose `VERSION`.
 */

exports.VERSION = require('../package.json').version;

/**
 * Add integrations.
 */

for (var integration in Integrations) {
    analytics.use(Integrations[integration]);
}