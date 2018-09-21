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
window.unomiTracker || (window.unomiTracker = {});
(function () {
    var unomiTracker_queue = [];

    var methods = ['trackSubmit', 'trackClick', 'trackLink', 'trackForm', 'initialize', 'pageview', 'identify', 'reset', 'group', 'track', 'ready', 'alias', 'debug', 'page', 'once', 'off', 'on', 'personalize'];

    var factory = function (method) {
        return function () {
            var args = Array.prototype.slice.call(arguments);
            args.unshift(method);
            unomiTracker_queue.push(args);
            return window.unomiTracker;
        };
    };

    // For each of our methods, generate a queueing stub.
    for (var i = 0; i < methods.length; i++) {
        var method = methods[i];
        window.unomiTracker[method] = factory(method);
    }

    // Define a method to load Analytics.js from our CDN,
    // and that will be sure to only ever load it once.
    unomiTracker.load = function (callback, option) {
        // Create an async script element based on your key.
        var script = document.createElement('script');
        script.type = 'text/javascript';
        script.async = true;
        // TODO we might want to add a check on the url to see if it ends with / or not
        script.src = option.url + '/tracker/unomi-tracker.min.js';

        if (script.addEventListener) {
            script.addEventListener('load', function (e) {
                if (typeof callback === 'function') {
                    callback(e);
                }
            }, false);
        } else {
            script.onreadystatechange = function () {
                if (this.readyState == 'complete' || this.readyState == 'loaded') {
                    callback(window.event);
                }
            };
        }

        // Insert our script next to the first script element.
        var first = document.getElementsByTagName('script')[0];
        first.parentNode.insertBefore(script, first);
    };

    unomiTracker.load(function (option) {
        unomiTracker.initialize({
            'Apache Unomi': option
        });

        // Loop through the interim analytics queue and reapply the calls to their
        // proper analytics.js method.
        while (unomiTracker_queue.length > 0) {
            var item = unomiTracker_queue.shift();
            var method = item.shift();
            if (unomiTracker[method]) {
                unomiTracker[method].apply(unomiTracker, item);
            }
        }
    }, unomiOption);

    unomiTracker.page();
})();
