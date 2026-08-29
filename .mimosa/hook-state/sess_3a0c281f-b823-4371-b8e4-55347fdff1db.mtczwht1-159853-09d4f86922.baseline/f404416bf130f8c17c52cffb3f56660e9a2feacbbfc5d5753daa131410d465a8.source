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

(function () {

    // We use this method to generate unique sessions IDs
    function generateGuid() {
        function s4() {
            var array = new Uint16Array(1);
            window.crypto.getRandomValues(array);
            return array[0].toString(16).padStart(4, '0');
        }

        return s4() + s4() + '-' + s4() + '-' + s4() + '-' +
            s4() + '-' + s4() + s4() + s4();
    }

    // -- COOKIE HELPER METHODS ---

    function createCookie(name, value, days) {
        var expires;

        if (days) {
            var date = new Date();
            date.setTime(date.getTime() + (days * 24 * 60 * 60 * 1000));
            expires = "; expires=" + date.toGMTString();
        } else {
            expires = "";
        }
        document.cookie = encodeURIComponent(name) + "=" + encodeURIComponent(value) + expires + "; path=/";
    }

    function readCookie(name) {
        var nameEQ = encodeURIComponent(name) + "=";
        var ca = document.cookie.split(';');
        for (var i = 0; i < ca.length; i++) {
            var c = ca[i];
            while (c.charAt(0) === ' ') c = c.substring(1, c.length);
            if (c.indexOf(nameEQ) === 0) return decodeURIComponent(c.substring(nameEQ.length, c.length));
        }
        return null;
    }

    function eraseCookie(name) {
        createCookie(name, "", -1);
    }

    // -- BOOTSTRAP HELPER METHODS ---

    bootstrapAlert = {};
    bootstrapAlert.success = function (message) {
        $('#alert_placeholder').html('<div class="alert alert-success"><a class="close" data-dismiss="alert">×</a><span>' + message + '</span></div>')
    };
    bootstrapAlert.danger = function (message) {
        $('#alert_placeholder').html('<div class="alert alert-danger"><a class="close" data-dismiss="alert">×</a><span>' + message + '</span></div>')
    };

    $(document).ready(function () {

        // first we check if we have an existing session ID cookie, if not we generate a new session identifier and
        // store it in the cookie.
        var unomiSessionId = readCookie('unomi-session-id');
        if (!unomiSessionId) {
            unomiSessionId = generateGuid();
            console.log("No existing session cookie found, creating a new one with value " + unomiSessionId);
            createCookie('unomi-session-id', unomiSessionId, 1);
        }
        console.log("Setting up form listener...");
        $("#loginForm").submit(function (event) {
            var email = $('#email').val();
            var firstName = $('#firstname').val();
            var lastName = $('#lastname').val();
            var password = $('#password').val();
            if (password != 'test1234') {
                bootstrapAlert.danger("Wrong password (default is : test1234)");
                event.preventDefault();
                return false;
            }
            var contextRequest = {
                source: { // the source is required for the request to be process properly
                    itemId: location.pathname,
                    itemType: 'webpage',
                    scope: 'test' // the scope is used to regroup events and sessions into sub-groups (eg sites)
                },
                events: [{ // here we provide a simple login event, but as this is actually an array we could provide other events at the same time (page view, clicks, mouse movements, ...)
                    eventType: "login",
                    properties: {},
                    target: {
                        itemId: email,
                        itemType: "exampleUser",
                        properties: {
                            preferredLanguage: "en",
                            email: email,
                            firstName: firstName,
                            lastName: lastName
                        }
                    }
                }],
                requiredProfileProperties: ['*'], // this tells Unomi to send us back all the profile properties (by default none are returned)
                requiredSessionProperties: ['*']  // this tells Unomi to send us back all the session properties (by default none are returned)
            };
            // now let's perform the actual call to Apache Unomi, asking it to process the events and give us back the updated (or created) profile.
            // as we have a rule listening to a login event, it will be executed and its actions will be processed.
            $.ajax({
                url: "http://localhost:8181/cxs/context.json?sessionId=" + unomiSessionId,
                type: 'POST',
                data: JSON.stringify(contextRequest), // make sure you sent JSON and not form-encoded, otherwise Unomi will generate an error
                contentType: 'application/json; charset=utf-8',
                dataType: 'json',
                async: false,
                headers : {
                    'X-Unomi-Api-Key' : '670c26d1cc413346c3b2fd9ce65dab41' // this is configured in the etc/org.apache.unomi.thirdparty.cfg
                },
                success: function (data) {
                    console.log("Unomi response:", data);
                    bootstrapAlert.success("Successfully sent login event to Apache Unomi ! (profileId=" + data.profileId + ",properties.email=" + data.profileProperties.email + ",nbOfVisits=" + data.profileProperties.nbOfVisits + ")");
                }
            });
            event.preventDefault();
            return false;
        });
    });

})();

