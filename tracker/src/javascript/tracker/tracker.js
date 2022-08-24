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
import {Crawler} from "./crawler";

export const initializeTracker = () => {
    window.wem = {
        enableWem: () => {
            wem._enableWem(true);
        },
        disableWem: () => {
            wem._enableWem(false);
        },
        /**
         * This function initialize the context in the page it is called internally and should not be called twice in the same page
         *
         * @param {string} contextServerUrl
         * @param {string} proxyServletUrl
         * @param {boolean} isPreview
         * @param {number} timeoutInMilliseconds
         * @param {string} dxUsername
         */
        init: function () {
            const {
                contextServerUrl,
                proxyServletUrl,
                isPreview,
                timeoutInMilliseconds,
                dxUsername,
                contextServerCookieName,
                activateWem
            } = window.digitalData.wemInitConfig;
            wem.contextServerCookieName = contextServerCookieName;
            wem.contextServerUrl = contextServerUrl;
            wem.proxyServletUrl = proxyServletUrl;
            wem.dxUsername = dxUsername;
            wem.timeoutInMilliseconds = timeoutInMilliseconds;
            wem.formNamesToWatch = [];
            wem.eventsPrevented = [];
            wem.sessionID = wem.getCookie('wem-session-id');
            wem.fallback = false;
            if (wem.sessionID === null) {
                console.warn('[WEM] sessionID is null !');
            } else if (!wem.sessionID || wem.sessionID === '') {
                console.warn('[WEM] empty sessionID, setting to null !');
                wem.sessionID = null;
            }

            if (isPreview) {
                // do not execute fallback for preview!
                return;
            }

            let cookieDisabled = !navigator.cookieEnabled;
            let noSessionID = !wem.sessionID || wem.sessionID === '';
            let crawlerDetected = navigator.userAgent;
            if (crawlerDetected) {
                const browserDetector = new Crawler();
                crawlerDetected = browserDetector.isCrawler(navigator.userAgent);
            }
            if (cookieDisabled || noSessionID || crawlerDetected) {
                document.addEventListener('DOMContentLoaded', function () {
                    wem._executeFallback('navigator cookie disabled: ' + cookieDisabled + ', no sessionID: ' + noSessionID + ', web crawler detected: ' + crawlerDetected);
                });
                return;
            }

            // this is the event to get form factory data when submitting
            document.addEventListener('ffFormReady', wem._formFactorySubmitEventListener);

            wem._registerCallback(function () {
                if (cxs.profileId) {
                    wem.setCookie('wem-profile-id', cxs.profileId);
                }
                if (!cxs.profileId) {
                    wem.removeCookie('wem-profile-id');
                }
                // process tracked events
                var videoNamesToWatch = [];
                var clickToWatch = [];

                if (cxs.trackedConditions && cxs.trackedConditions.length > 0) {
                    for (var i = 0; i < cxs.trackedConditions.length; i++) {
                        switch (cxs.trackedConditions[i].type) {
                            case 'formEventCondition':
                                if (cxs.trackedConditions[i].parameterValues && cxs.trackedConditions[i].parameterValues.formId) {
                                    wem.formNamesToWatch.push(cxs.trackedConditions[i].parameterValues.formId);
                                }
                                break;
                            case 'videoViewEventCondition':
                                if (cxs.trackedConditions[i].parameterValues && cxs.trackedConditions[i].parameterValues.videoId) {
                                    videoNamesToWatch.push(cxs.trackedConditions[i].parameterValues.videoId);
                                }
                                break;
                            case 'clickOnLinkEventCondition':
                                if (cxs.trackedConditions[i].parameterValues && cxs.trackedConditions[i].parameterValues.itemId) {
                                    clickToWatch.push(cxs.trackedConditions[i].parameterValues.itemId);
                                }
                                break;
                        }
                    }
                }

                var forms = document.querySelectorAll('form');
                for (var formIndex = 0; formIndex < forms.length; formIndex++) {
                    var form = forms.item(formIndex);
                    var formName = form.getAttribute('name') ? form.getAttribute('name') : form.getAttribute('id');
                    // test attribute data-form-id to not add a listener on FF form
                    if (formName && wem.formNamesToWatch.indexOf(formName) > -1 && form.getAttribute('data-form-id') == null) {
                        // add submit listener on form that we need to watch only
                        console.info('[WEM] watching form ' + formName);
                        form.addEventListener('submit', wem._formSubmitEventListener, true);
                    }
                }

                for (var videoIndex = 0; videoIndex < videoNamesToWatch.length; videoIndex++) {
                    var videoName = videoNamesToWatch[videoIndex];
                    var video = document.getElementById(videoName) || document.getElementById(wem._resolveId(videoName));

                    if (video) {
                        video.addEventListener('play', wem.sendVideoEvent);
                        video.addEventListener('ended', wem.sendVideoEvent);
                        console.info('[WEM] watching video ' + videoName);
                    } else {
                        console.warn('[WEM] unable to watch video ' + videoName + ', video not found in the page');
                    }
                }

                for (var clickIndex = 0; clickIndex < clickToWatch.length; clickIndex++) {
                    var clickIdName = clickToWatch[clickIndex];
                    var click = (document.getElementById(clickIdName) || document.getElementById(wem._resolveId(clickIdName)))
                        ? (document.getElementById(clickIdName) || document.getElementById(wem._resolveId(clickIdName)))
                        : document.getElementsByName(clickIdName)[0];
                    if (click) {
                        click.addEventListener('click', wem.sendClickEvent);
                        console.info('[WEM] watching click ' + clickIdName);
                    } else {
                        console.warn('[WEM] unable to watch click ' + clickIdName + ', element not found in the page');
                    }
                }

                wem.checkProfileValidity();
            });

            // Load the context once document is ready
            document.addEventListener('DOMContentLoaded', function () {
                wem.DOMLoaded = true;

                // enrich digital data considering extensions
                wem._handleDigitalDataOverrides();

                // complete already registered events
                wem._checkUncompleteRegisteredEvents();

                // Dispatch javascript events for the experience (perso/opti displayed from SSR, based on unomi events)
                wem._dispatchJSExperienceDisplayedEvents();

                // Some event may not need to be send to unomi, check for them and filter them out.
                wem._filterUnomiEvents();

                // Add referrer info into digitalData.page object.
                wem._processReferrer();

                // Build view event
                const viewEvent = wem.buildEvent('view', wem.buildTargetPage(), wem.buildSource(window.digitalData.site.siteInfo.siteID, 'site'));
                viewEvent.flattenedProperties = {};

                // Add URLParameters
                if (location.search) {
                    viewEvent.flattenedProperties['URLParameters'] = wem.convertUrlParametersToObj(location.search);
                }
                // Add interests
                if (window.digitalData.interests) {
                    viewEvent.flattenedProperties['interests'] = window.digitalData.interests;
                }

                // Register the page view event, it's unshift because it should be the first event, this is just for logical purpose. (page view comes before perso displayed event for example)
                wem._registerEvent(viewEvent, true);

                if (activateWem || window.activateWem) {
                    wem.loadContext();
                } else {
                    wem._executeFallback('wem is not activated on current page');
                }
            });
        },
        convertUrlParametersToObj: function (searchString) {
            if (!searchString) {
                return null;
            }

            return searchString
                .replace(/^\?/, '') // Only trim off a single leading interrobang.
                .split('&')
                .reduce((result, next) => {
                        if (next === '') {
                            return result;
                        }
                        let pair = next.split('=');
                        let key = decodeURIComponent(pair[0]);
                        let value = typeof pair[1] !== 'undefined' && decodeURIComponent(pair[1]) || undefined;
                        if (Object.prototype.hasOwnProperty.call(result, key)) { // Check to see if this property has been met before.
                            if (Array.isArray(result[key])) { // Is it already an array?
                                result[key].push(value);
                            } else { // Make it an array.
                                result[key] = [result[key], value];
                            }
                        } else { // First time seen, just add it.
                            result[key] = value;
                        }

                        return result;
                    }, {}
                );
        },
        /**
         * This function will get the targets for the filter
         *
         * @returns {Array}
         */
        getFilterTargets: function () {
            var targets = [];
            if (window.digitalData.filterCallback) {
                for (var i = 0; i < window.digitalData.filterCallback.length; i++) {
                    var currentNodeFilters = window.digitalData.filterCallback[i].filter;
                    for (var j = 0; j < currentNodeFilters.filters.length; j++) {
                        var currentNodeFilter = currentNodeFilters.filters[j];
                        for (var k = 0; k < currentNodeFilter.appliesOn.length; k++) {
                            var applyOnEntry = currentNodeFilter.appliesOn[k];
                            targets.push(applyOnEntry);
                        }
                    }
                }
            }
            return targets;
        },

        /**
         * This function will register a personalization
         *
         * @param {object} personalization
         * @param {object} variants
         * @param {boolean} [ajax] Deprecated: Ajax rendering is not supported anymore
         * @param {function} [resultCallback]
         */
        registerPersonalizationObject: function (personalization, variants, ajax, resultCallback) {
            var target = personalization.id;
            wem._registerPersonalizationCallback(personalization, function (result) {
                var successfulFilters = [];
                for (var i = 0; i < result.length; i++) {
                    successfulFilters.push(variants[result[i]]);
                }

                var selectedFilter = null;
                if (successfulFilters.length > 0) {
                    selectedFilter = successfulFilters[0];
                    var minPos = successfulFilters[0].position;
                    if (minPos >= 0) {
                        for (var j = 1; j < successfulFilters.length; j++) {
                            if (successfulFilters[j].position < minPos) {
                                selectedFilter = successfulFilters[j];
                            }
                        }
                    }
                }

                if (resultCallback) {
                    // execute callback
                    resultCallback(successfulFilters, selectedFilter);
                } else {
                    if (selectedFilter) {
                        var targetFilters = document.getElementById(target).children;
                        for (var fIndex in targetFilters) {
                            var filter = targetFilters.item(fIndex);
                            if (filter) {
                                filter.style.display = (filter.id === selectedFilter.content) ? '' : 'none';
                            }
                        }

                        // we now add control group information to event if the user is in the control group.
                        if (wem._isInControlGroup(target)) {
                            console.info('[WEM] Profile is in control group for target: ' + target + ', adding to personalization event...');
                            selectedFilter.event.target.properties.inControlGroup = true;
                            if (selectedFilter.event.target.properties.variants) {
                                selectedFilter.event.target.properties.variants.forEach(variant => variant.inControlGroup = true);
                            }
                        }

                        // send event to unomi
                        wem.collectEvent(wem._completeEvent(selectedFilter.event), function () {
                            console.info('[WEM] Personalization event successfully collected.');
                        }, function () {
                            console.error('[WEM] Could not send personalization event.');
                        });

                        //Trigger variant display event for personalization
                        wem._dispatchJSExperienceDisplayedEvent(selectedFilter.event);
                    } else {
                        var elements = document.getElementById(target).children;
                        for (var eIndex in elements) {
                            var el = elements.item(eIndex);
                            el.style.display = 'none';
                        }
                    }
                }
            });
        },

        /**
         * This function will anonymize the current profile
         *
         * @param {function} successCallback will be executed if case of success
         * @param {function} errorCallback will be executed if case of error
         */
        anonymizeProfile: function (successCallback, errorCallback) {
            wem.ajax({
                url: wem.proxyServletUrl + '/cxs/privacy/profiles/' + cxs.profileId + '/anonymize?scope=' + window.digitalData.scope,
                type: 'POST',
                async: true,
                contentType: 'application/x-www-form-urlencoded',
                dataType: 'application/json',
                data: '',
                success: successCallback,
                error: errorCallback
            });
        },

        /**
         * This function will change the location of the current window to display the current profile information
         */
        downloadMyProfile: function () {
            window.location = wem.contextServerUrl + '/client/myprofile.text';
        },

        /**
         * This function will toggle the private browsing functionality
         *
         * @param {function} successCallback will be executed if case of success
         * @param {function} errorCallback will be executed if case of error
         */
        togglePrivateBrowsing: function (successCallback, errorCallback) {
            if (cxs.anonymousBrowsing) {
                wem.ajax({
                    url: wem.proxyServletUrl + '/cxs/privacy/profiles/' + cxs.profileId + '/anonymousBrowsing?scope=' + window.digitalData.scope,
                    type: 'DELETE',
                    async: true,
                    contentType: 'application/x-www-form-urlencoded',
                    dataType: 'application/json',
                    success: successCallback,
                    error: errorCallback
                });
            } else {
                wem.ajax({
                    url: wem.proxyServletUrl + '/cxs/privacy/profiles/' + cxs.profileId + '/anonymousBrowsing?anonymizePastBrowsing=true&scope=' + window.digitalData.scope,
                    type: 'POST',
                    async: true,
                    contentType: 'application/x-www-form-urlencoded',
                    dataType: 'application/json',
                    data: '',
                    success: successCallback,
                    error: errorCallback
                });
            }
        },

        /**
         * This function will register an optimization test or A/B test
         *
         * @param {string} optimizationTestNodeId
         * @param {string} goalId
         * @param {string} containerId
         * @param {object} variants
         * @param {boolean} [ajax] Deprecated: Ajax rendering is not supported anymore
         * @param {object} [variantsTraffic]
         */
        registerOptimizationTest: function (optimizationTestNodeId, goalId, containerId, variants, ajax, variantsTraffic) {

            // check persona panel forced variant
            var selectedVariantId = wem.getUrlParameter('wemSelectedVariantId-' + optimizationTestNodeId);

            // check already resolved variant stored in local
            if (selectedVariantId === null) {
                if (wem.storageAvailable('sessionStorage')) {
                    selectedVariantId = sessionStorage.getItem(optimizationTestNodeId);
                } else {
                    selectedVariantId = wem.getCookie('selectedVariantId');
                    if (selectedVariantId != null && selectedVariantId === '') {
                        selectedVariantId = null;
                    }
                }
            }

            // select random variant and call unomi
            if (!(selectedVariantId && variants[selectedVariantId])) {
                var keys = Object.keys(variants);
                if (variantsTraffic) {
                    var rand = 100 * Math.random() << 0;
                    for (var nodeIdentifier in variantsTraffic) {
                        if ((rand -= variantsTraffic[nodeIdentifier]) < 0 && selectedVariantId == null) {
                            selectedVariantId = nodeIdentifier;
                        }
                    }
                } else {
                    selectedVariantId = keys[keys.length * Math.random() << 0];
                }
                if (wem.storageAvailable('sessionStorage')) {
                    sessionStorage.setItem(optimizationTestNodeId, selectedVariantId);
                } else {
                    wem.setCookie('selectedVariantId', selectedVariantId, 1);
                }

                // spread event to unomi
                wem._registerEvent(wem._completeEvent(variants[selectedVariantId].event));
            }

            //Trigger variant display event for optimization
            // (Wrapped in DOMContentLoaded because opti are resulted synchronously at page load, so we dispatch the JS even after page load, to be sure that listeners are ready)
            window.addEventListener('DOMContentLoaded', () => {
                wem._dispatchJSExperienceDisplayedEvent(variants[selectedVariantId].event);
            });
            if (selectedVariantId) {
                // update persona panel selected variant
                if (window.optimizedContentAreas && window.optimizedContentAreas[optimizationTestNodeId]) {
                    window.optimizedContentAreas[optimizationTestNodeId].selectedVariant = selectedVariantId;
                }

                // display the good variant
                document.getElementById(variants[selectedVariantId].content).style.display = '';
            }
        },

        /**
         * @deprecated the variant JS event is now sent automatically from the unomi event.
         */
        dispatchVariantJSEvent: function (variantData, experienceType) {
            // do nothing
        },

        /**
         * This function is used to load the current context in the page
         *
         * @param {boolean} [skipEvents=false] Should we send the events
         * @param {boolean} [invalidate=false] Should we invalidate the current context
         */
        loadContext: function (skipEvents, invalidate) {
            if (wem.contextLoaded) {
                console.log('Context already requested by', wem.contextLoaded);
                return;
            }
            var jsonData = {
                requiredProfileProperties: window.digitalData.wemInitConfig.requiredProfileProperties,
                requiredSessionProperties: window.digitalData.wemInitConfig.requiredSessionProperties,
                requireSegments: window.digitalData.wemInitConfig.requireSegments,
                requireScores: window.digitalData.wemInitConfig.requireScores,
                source: wem.buildSourcePage()
            };
            if (!skipEvents) {
                jsonData.events = window.digitalData.events;
            }
            if (window.digitalData.personalizationCallback) {
                jsonData.personalizations = window.digitalData.personalizationCallback.map(function (x) {
                    return x.personalization;
                });
            }

            jsonData.sessionId = wem.sessionID;

            var contextUrl = wem.contextServerUrl + '/context.json';
            if (invalidate) {
                contextUrl += '?invalidateSession=true&invalidateProfile=true';
            }
            wem.ajax({
                url: contextUrl,
                type: 'POST',
                async: true,
                contentType: 'text/plain;charset=UTF-8', // Use text/plain to avoid CORS preflight
                jsonData: jsonData,
                dataType: 'application/json',
                invalidate: invalidate,
                success: wem._onSuccess,
                error: function () {
                    wem._executeFallback('error during context loading');
                }
            });
            wem.contextLoaded = Error().stack;
            console.info('[WEM] context loading...');
        },

        /**
         * This function is used internally to load a modified context, for preview testing mainly
         * and should not be used outside of this context unless you really know what you are doing.
         *
         * @param {string} personaId
         * @param {object} personaOverrides
         */
        loadPersonaContext: function (personaId, personaOverrides) {
            var profileOverrides = {
                itemId: personaId,
                itemType: 'persona',
                properties: personaOverrides.properties ? personaOverrides.properties : null,
                segments: personaOverrides.segments ? personaOverrides.segments : null,
                scores: personaOverrides.scores ? personaOverrides.scores : null
            };
            var jsonData = {
                source: wem.buildSourcePage(),
                requireSegments: true,
                requiredProfileProperties: ['*'],
                requiredSessionProperties: ['*'],
                events: window.digitalData.events,
                profileOverrides: profileOverrides,
                sessionPropertiesOverrides: personaOverrides.sessionProperties
            };
            if (window.digitalData.personalizationCallback) {
                jsonData.personalizations = window.digitalData.personalizationCallback.map(function (x) {
                    return x.personalization;
                });
            }

            var url = wem.contextServerUrl + '/context.json';
            if (personaId) {
                url += '?personaId=' + personaId;
            }

            if (wem.sessionID) {
                jsonData.sessionId = wem.sessionID;
            }

            wem.ajax({
                url: url,
                type: 'POST',
                async: true,
                contentType: 'text/plain;charset=UTF-8', // Use text/plain to avoid CORS preflight
                jsonData: jsonData,
                dataType: 'application/json',
                success: wem._onSuccess,
                error: function () {
                    wem._executeFallback('error during persona context loading');
                }
            });

            console.info('[WEM] persona context loading...');
        },

        /**
         * This function will send an event to Apache Unomi
         * @param {object} event The event object to send, you can build it using wem.buildEvent(eventType, target, source)
         * @param {function} successCallback will be executed in case of success
         * @param {function} errorCallback will be executed in case of error
         */
        collectEvent: function (event, successCallback, errorCallback) {
            wem.collectEvents({events: [event]}, successCallback, errorCallback);
        },

        /**
         * This function will send the events to Apache Unomi
         *
         * @param {object} events Javascript object { events: [event1, event2] }
         * @param {function} successCallback will be executed in case of success
         * @param {function} errorCallback will be executed in case of error
         */
        collectEvents: function (events, successCallback, errorCallback) {
            if (wem.fallback) {
                // in case of fallback we dont want to collect any events
                return;
            }

            events.sessionId = wem.sessionID ? wem.sessionID : '';

            var data = JSON.stringify(events);
            wem.ajax({
                url: wem.contextServerUrl + '/eventcollector',
                type: 'POST',
                async: true,
                contentType: 'text/plain;charset=UTF-8', // Use text/plain to avoid CORS preflight
                data: data,
                dataType: 'application/json',
                success: successCallback,
                error: errorCallback
            });
        },

        /**
         * This function will send an event of type form, but only if there is a mapping associate to the form, it should be used when submitting a form with AJAX/Javascript
         *
         * @param {object} form element get using document.getElementBy...
         * @param {function} successCallback will be executed in case of success
         * @param {function} errorCallback will be executed in case of error
         */
        sendAjaxFormEvent: function (form, successCallback, errorCallback) {
            var formName = form.getAttribute('name') ? form.getAttribute('name') : form.getAttribute('id');
            if (formName && wem.formNamesToWatch.indexOf(formName) > -1) {
                console.info('[WEM] catching form ' + formName);
                var formEvent = wem.buildFormEvent(formName);
                // merge form properties with event properties
                formEvent.flattenedProperties = {
                    fields: wem._extractFormData(form)
                };

                wem.collectEvent(formEvent, successCallback, errorCallback);
            } else {
                console.info('[WEM] There is no associated form mapping with this form');
                if (successCallback) {
                    successCallback();
                }
            }
        },

        /**
         * This function will build an event of type click and send it to Apache Unomi
         *
         * @param {object} event javascript
         * @param {function} [successCallback] will be executed if case of success
         * @param {function} [errorCallback] will be executed if case of error
         */
        sendClickEvent: function (event, successCallback, errorCallback) {
            if (event.target.id || event.target.name) {
                console.info('[WEM] Send click event');
                var targetId = event.target.id ? event.target.id : event.target.name;
                var clickEvent = wem.buildEvent('click',
                    wem.buildTarget(targetId, event.target.localName),
                    wem.buildSourcePage());

                var eventIndex = wem.eventsPrevented.indexOf(targetId);
                if (eventIndex !== -1) {
                    wem.eventsPrevented.splice(eventIndex, 0);
                } else {
                    wem.eventsPrevented.push(targetId);

                    event.preventDefault();

                    var target = event.target;

                    wem.collectEvent(clickEvent, function (xhr) {
                        console.info('[WEM] Click event successfully collected.');
                        if (successCallback) {
                            successCallback(xhr);
                        } else {
                            target.click();
                        }
                    }, function (xhr) {
                        console.error('[WEM] Could not send click event.');
                        if (errorCallback) {
                            errorCallback(xhr);
                        } else {
                            target.click();
                        }
                    });
                }
            }
        },

        /**
         * This function will build an event of type video and send it to Apache Unomi
         *
         * @param {object} event javascript
         * @param {function} [successCallback] will be executed if case of success
         * @param {function} [errorCallback] will be executed if case of error
         */
        sendVideoEvent: function (event, successCallback, errorCallback) {
            console.info('[WEM] catching video event');
            var videoEvent = wem.buildEvent('video', wem.buildTarget(event.target.id, 'video', {action: event.type}), wem.buildSourcePage());

            wem.collectEvent(videoEvent, function (xhr) {
                console.info('[WEM] Video event successfully collected.');
                if (successCallback) {
                    successCallback(xhr);
                }
            }, function (xhr) {
                console.error('[WEM] Could not send video event.');
                if (errorCallback) {
                    errorCallback(xhr);
                }
            });
        },

        /**
         * This function will invalidate the Apache Unomi session and profile,
         * by removing the associated cookies, set the loaded context to undefined
         * and set the wem-session-id cookie with a newly generated ID
         */
        invalidateSessionAndProfile: function () {
            'use strict';
            wem.sessionID = wem.generateGuid() + '-browser-generated';
            wem.setCookie('wem-session-id', wem.sessionID, 1);
            wem.removeCookie(wem.contextServerCookieName);
            wem.removeCookie('wem-profile-id');
            window.cxs = undefined;
        },

        /**
         * This function will check if the currently logged user in DX is equal to the username stored in the Apache Unomi profile.
         * If not we will invalidate both the session and the profile and reload the context. This is related
         * to https://jira.jahia.org/browse/DMF-1468 and other reported issues about errors in profile switching,
         */
        checkProfileValidity: function () {
            'use strict';
            if (window.cxs) {
                if (cxs.profileProperties && cxs.profileProperties['j:nodename']) {
                    var cxsDXNodeName = cxs.profileProperties['j:nodename'];
                    if (cxsDXNodeName && wem.dxUsername !== 'guest' && cxsDXNodeName !== wem.dxUsername) {
                        console.warn('[WEM] Logged in DX with username (' + wem.dxUsername + ') does not correspond to Apache Unomi profile DX username (' + cxsDXNodeName + '). Invalidating session and profiles and reloading context.');
                        wem.invalidateSessionAndProfile();
                        wem._registerEvent(wem.buildEvent('invalidProfileForDXUser', wem.buildTarget(cxsDXNodeName + '-' + wem.dxUsername, 'invalidProfileError', {
                            unomiNodeName: cxsDXNodeName,
                            dxUsername: wem.dxUsername
                        })));
                        wem.loadContext();
                    }
                }
            }
        },

        /**
         * This function return the basic structure for an event, it must be adapted to your need
         *
         * @param {string} eventType The name of your event
         * @param {object} [target] The target object for your event can be build with wem.buildTarget(targetId, targetType, targetProperties)
         * @param {object} [source] The source object for your event can be build with wem.buildSource(sourceId, sourceType, sourceProperties)
         * @returns {{eventType: *, scope}}
         */
        buildEvent: function (eventType, target, source) {
            var event = {
                eventType: eventType,
                scope: window.digitalData.scope
            };

            if (target) {
                event.target = target;
            }

            if (source) {
                event.source = source;
            }

            return event;
        },

        /**
         * This function return an event of type form
         *
         * @param {string} formName The HTML name of id of the form to use in the target of the event
         * @returns {*|{eventType: *, scope, source: {scope, itemId: string, itemType: string, properties: {}}, target: {scope, itemId: string, itemType: string, properties: {}}}}
         */
        buildFormEvent: function (formName) {
            return wem.buildEvent('form', wem.buildTarget(formName, 'form'), wem.buildSourcePage());
        },

        /**
         * This function return the source object for a source of type page
         *
         * @returns {*|{scope, itemId: *, itemType: *}}
         */
        buildTargetPage: function () {
            return wem.buildTarget(window.digitalData.page.pageInfo.pageID, 'page', window.digitalData.page);
        },

        /**
         * This function return the source object for a source of type page
         *
         * @returns {*|{scope, itemId: *, itemType: *}}
         */
        buildSourcePage: function () {
            return wem.buildSource(window.digitalData.page.pageInfo.pageID, 'page', window.digitalData.page);
        },

        /**
         * This function return the basic structure for the target of your event
         *
         * @param {string} targetId The ID of the target
         * @param {string} targetType The type of the target
         * @param {object} [targetProperties] The optional properties of the target
         * @returns {{scope, itemId: *, itemType: *}}
         */
        buildTarget: function (targetId, targetType, targetProperties) {
            return wem._buildObject(targetId, targetType, targetProperties);
        },

        /**
         * This function return the basic structure for the source of your event
         *
         * @param {string} sourceId The ID of the source
         * @param {string} sourceType The type of the source
         * @param {object} [sourceProperties] The optional properties of the source
         * @returns {{scope, itemId: *, itemType: *}}
         */
        buildSource: function (sourceId, sourceType, sourceProperties) {
            return wem._buildObject(sourceId, sourceType, sourceProperties);
        },

        /*************************************/
        /* Utility functions under this line */
        /*************************************/

        /**
         * This is an utility function to set a cookie
         *
         * @param {string} cookieName name of the cookie
         * @param {string} cookieValue value of the cookie
         * @param {number} [expireDays] number of days to set the expire date
         */
        setCookie: function (cookieName, cookieValue, expireDays) {
            var expires = '';
            if (expireDays) {
                var d = new Date();
                d.setTime(d.getTime() + (expireDays * 24 * 60 * 60 * 1000));
                expires = '; expires=' + d.toUTCString();
            }
            document.cookie = cookieName + '=' + cookieValue + expires + '; path=/; SameSite=Strict';
        },

        /**
         * This is an utility function to get a cookie
         *
         * @param {string} cookieName name of the cookie to get
         * @returns {*} the value of the first cookie with the corresponding name or null if not found
         */
        getCookie: function (cookieName) {
            var name = cookieName + '=';
            var ca = document.cookie.split(';');
            for (var i = 0; i < ca.length; i++) {
                var c = ca[i];
                while (c.charAt(0) == ' ') {
                    c = c.substring(1);
                }
                if (c.indexOf(name) == 0) {
                    return c.substring(name.length, c.length);
                }
            }
            return null;
        },

        /**
         * This is an utility function to remove a cookie
         *
         * @param {string} cookieName the name of the cookie to rename
         */
        removeCookie: function (cookieName) {
            'use strict';
            wem.setCookie(cookieName, '', -1);
        },

        /**
         * This is an utility function to execute AJAX call
         *
         * @param {object} options
         */
        ajax: function (options) {
            var xhr = new XMLHttpRequest();
            if ('withCredentials' in xhr) {
                xhr.open(options.type, options.url, options.async);
                xhr.withCredentials = true;
            } else if (typeof XDomainRequest != 'undefined') {
                /* global XDomainRequest */
                xhr = new XDomainRequest();
                xhr.open(options.type, options.url);
            }

            if (options.contentType) {
                xhr.setRequestHeader('Content-Type', options.contentType);
            }
            if (options.dataType) {
                xhr.setRequestHeader('Accept', options.dataType);
            }

            if (options.responseType) {
                xhr.responseType = options.responseType;
            }

            var requestExecuted = false;
            if (wem.timeoutInMilliseconds !== -1) {
                setTimeout(function () {
                    if (!requestExecuted) {
                        console.error('[WEM] XML request timeout, url: ' + options.url);
                        requestExecuted = true;
                        if (options.error) {
                            options.error(xhr);
                        }
                    }
                }, wem.timeoutInMilliseconds);
            }

            xhr.onreadystatechange = function () {
                if (!requestExecuted) {
                    if (xhr.readyState === 4) {
                        if (xhr.status === 200 || xhr.status === 204 || xhr.status === 304) {
                            if (xhr.responseText != null) {
                                requestExecuted = true;
                                if (options.success) {
                                    options.success(xhr);
                                }
                            }
                        } else {
                            requestExecuted = true;
                            if (options.error) {
                                options.error(xhr);
                            }
                            console.error('[WEM] XML request error: ' + xhr.statusText + ' (' + xhr.status + ')');
                        }
                    }
                }
            };

            if (options.jsonData) {
                xhr.send(JSON.stringify(options.jsonData));
            } else if (options.data) {
                xhr.send(options.data);
            } else {
                xhr.send();
            }
        },

        /**
         * This is an utility function to load HTML content from the URL into the container,
         * The content of the container will be replaced by the loaded content
         *
         * @param {string} url to load the content
         * @param {string} parentSelector HTML ID of the container
         */
        loadContent: function (url, parentSelector) {
            var xhr = new XMLHttpRequest();
            var finished = false;
            xhr.onabort = xhr.onerror = function xhrError() {
                finished = true;
            };

            xhr.onreadystatechange = function xhrStateChange() {
                if (xhr.readyState === 4 && !finished) {
                    finished = true;
                    try {
                        var fragment = xhr.responseXML;
                        var documentHead = document.getElementsByTagName('head')[0];
                        var fragmentHeadChildNodes = fragment.getElementsByTagName('head')[0].childNodes;
                        for (var cIndex = 0; cIndex < fragmentHeadChildNodes.length; cIndex++) {
                            var childNode = fragmentHeadChildNodes[cIndex];
                            // Some browser don't use upper case so let's make sure everything match
                            if (childNode.nodeName.toUpperCase() === 'LINK'
                                && childNode.nodeType === 1) {
                                documentHead.appendChild(childNode);
                            } else if (childNode.nodeName.toUpperCase() === 'SCRIPT'
                                && childNode.nodeType === 1
                                && childNode.src) {
                                documentHead.appendChild(childNode);
                                // here we need to load the script asynchronously to ensure the fragment is working
                                // because they won't be loaded otherwise
                                wem._loadScript(childNode.src);
                            }
                        }

                        var parent = document.getElementById(parentSelector);
                        parent.innerHTML = fragment.getElementsByTagName('body')[0].innerHTML;
                        var scripts = parent.getElementsByTagName('script');
                        for (var sIndex = 0; sIndex < scripts.length; sIndex++) {
                            var script = scripts[sIndex];
                            if (script.src) {
                                // in case there is a script with src that is not in the head
                                wem._loadScript(script.src);
                            } else {
                                window['eval'].call(window, script.text || script.textContent || script.innerHTML || '');
                            }
                        }
                    } catch (e) {
                        console.error('[WEM] ' + e);
                    }
                }
            };

            xhr.open('GET', url + '?includeJavascripts=true&mainResource=' + window.digitalData.page.pageInfo.pageID);
            // The responseType must be set here after the open otherwise it won't work on IE and old version of Firefox
            xhr.responseType = 'document';
            xhr.send();
        },

        /**
         * This is an utility function to extend a JS object
         *
         * @returns {{}}
         */
        extend: function () {
            // Variables
            var extended = {};
            var deep = false;
            var i = 0;
            var length = arguments.length;

            // Check if a deep merge
            if (Object.prototype.toString.call(arguments[0]) === '[object Boolean]') {
                deep = arguments[0];
                i++;
            }

            // Merge the object into the extended object
            var merge = function (obj) {
                for (var prop in obj) {
                    if (Object.prototype.hasOwnProperty.call(obj, prop)) {
                        // If deep merge and property is an object, merge properties
                        if (deep && Object.prototype.toString.call(obj[prop]) === '[object Object]') {
                            extended[prop] = wem.extend(true, extended[prop], obj[prop]);
                        } else {
                            extended[prop] = obj[prop];
                        }
                    }
                }
            };

            // Loop through each object and conduct a merge
            for (; i < length; i++) {
                var obj = arguments[i];
                merge(obj);
            }

            return extended;
        },

        /**
         * This is an utility function to generate a new UUID
         *
         * @returns {string}
         */
        generateGuid: function () {
            function s4() {
                return Math.floor((1 + Math.random()) * 0x10000)
                    .toString(16)
                    .substring(1);
            }

            return s4() + s4() + '-' + s4() + '-' + s4() + '-' +
                s4() + '-' + s4() + s4() + s4();
        },

        /**
         * This is an utility function to check if the local storage is available or not
         * @param type
         * @returns {boolean}
         */
        storageAvailable: function (type) {
            try {
                var storage = window[type],
                    x = '__storage_test__';
                storage.setItem(x, x);
                storage.removeItem(x);
                return true;
            } catch (e) {
                return false;
            }
        },

        dispatchJSEvent: function (name, canBubble, cancelable, detail) {
            var event = document.createEvent('CustomEvent');
            event.initCustomEvent(name, canBubble, cancelable, detail);
            document.dispatchEvent(event);
        },

        /**
         * This is an utility function to get current url parameter value
         * @param name, the name of the parameter
         * @returns {string}
         */
        getUrlParameter: function (name) {
            name = name.replace(/[\[]/, '\\[').replace(/[\]]/, '\\]');
            var regex = new RegExp('[\\?&]' + name + '=([^&#]*)');
            var results = regex.exec(window.location.search);
            return results === null ? null : decodeURIComponent(results[1].replace(/\+/g, ' '));
        },

        /*************************************/
        /* Private functions under this line */
        /*************************************/
        _handleDigitalDataOverrides: function () {
            if (window.digitalDataOverrides && window.digitalDataOverrides.length > 0) {
                for (const digitalDataOverride of window.digitalDataOverrides) {
                    window.digitalData = wem._deepMergeObjects(digitalDataOverride, window.digitalData);
                }
            }
        },

        _checkUncompleteRegisteredEvents: function () {
            if (window.digitalData && window.digitalData.events) {
                for (const event of window.digitalData.events) {
                    wem._completeEvent(event);
                }
            }
        },

        _dispatchJSExperienceDisplayedEvents: () => {
            if (window.digitalData && window.digitalData.events) {
                for (const event of window.digitalData.events) {
                    if (event.eventType === 'optimizationTestEvent' || event.eventType === 'personalizationEvent') {
                        wem._dispatchJSExperienceDisplayedEvent(event);
                    }
                }
            }
        },

        _dispatchJSExperienceDisplayedEvent: experienceUnomiEvent => {
            if (!wem.fallback &&
                experienceUnomiEvent &&
                experienceUnomiEvent.target &&
                experienceUnomiEvent.target.properties &&
                experienceUnomiEvent.target.properties.variants &&
                experienceUnomiEvent.target.properties.variants.length > 0) {

                let typeMapper = {
                    optimizationTestEvent: 'optimization',
                    personalizationEvent: 'personalization'
                };
                for (const variant of experienceUnomiEvent.target.properties.variants) {
                    let jsEventDetail = {
                        id: variant.id,
                        name: variant.systemName,
                        displayableName: variant.displayableName,
                        path: variant.path,
                        type: typeMapper[experienceUnomiEvent.eventType],
                        variantType: experienceUnomiEvent.target.properties.type,
                        tags: variant.tags,
                        nodeType: variant.nodeType,
                        wrapper: {
                            id: experienceUnomiEvent.target.itemId,
                            name: experienceUnomiEvent.target.properties.systemName,
                            displayableName: experienceUnomiEvent.target.properties.displayableName,
                            path: experienceUnomiEvent.target.properties.path,
                            tags: experienceUnomiEvent.target.properties.tags,
                            nodeType: experienceUnomiEvent.target.properties.nodeType
                        }
                    };

                    wem.dispatchJSEvent('displayWemVariant', false, false, jsEventDetail);
                }
            }
        },

        _filterUnomiEvents: () => {
            if (window.digitalData && window.digitalData.events) {
                window.digitalData.events = window.digitalData.events
                    .filter(event => !event.properties || !event.properties.doNotSendToUnomi)
                    .map(event => {
                        if (event.properties) {
                            delete event.properties.doNotSendToUnomi;
                        }
                        return event;
                    });
            }
        },

        _completeEvent: function (event) {
            if (!event.source) {
                event.source = wem.buildSourcePage();
            }
            if (!event.scope) {
                event.scope = window.digitalData.scope;
            }
            if (event.target && !event.target.scope) {
                event.target.scope = window.digitalData.scope;
            }
            return event;
        },

        _registerEvent: function (event, unshift) {
            if (window.digitalData) {
                if (window.cxs) {
                    console.error('[WEM] already loaded, too late...');
                    return;
                }
            } else {
                window.digitalData = {};
            }

            window.digitalData.events = window.digitalData.events || [];
            if (unshift) {
                window.digitalData.events.unshift(event);
            } else {
                window.digitalData.events.push(event);
            }
        },

        _registerCallback: function (onLoadCallback) {
            if (window.digitalData) {
                if (window.cxs) {
                    console.info('[WEM] digitalData object loaded, calling on load callback immediately and registering update callback...');
                    if (onLoadCallback) {
                        onLoadCallback(window.digitalData);
                    }
                } else {
                    console.info('[WEM] digitalData object present but not loaded, registering load callback...');
                    if (onLoadCallback) {
                        window.digitalData.loadCallbacks = window.digitalData.loadCallbacks || [];
                        window.digitalData.loadCallbacks.push(onLoadCallback);
                    }
                }
            } else {
                console.info('[WEM] No digital data object found, creating and registering update callback...');
                window.digitalData = {};
                if (onLoadCallback) {
                    window.digitalData.loadCallbacks = [];
                    window.digitalData.loadCallbacks.push(onLoadCallback);
                }
            }
        },

        _registerPersonalizationCallback: function (personalization, callback) {
            if (window.digitalData) {
                if (window.cxs) {
                    console.error('[WEM] already loaded, too late...');
                } else {
                    console.info('[WEM] digitalData object present but not loaded, registering sort callback...');
                    window.digitalData.personalizationCallback = window.digitalData.personalizationCallback || [];
                    window.digitalData.personalizationCallback.push({personalization: personalization, callback: callback});
                }
            } else {
                window.digitalData = {};
                window.digitalData.personalizationCallback = window.digitalData.personalizationCallback || [];
                window.digitalData.personalizationCallback.push({personalization: personalization, callback: callback});
            }
        },

        _buildObject: function (itemId, itemType, properties) {
            var object = {
                scope: window.digitalData.scope,
                itemId: itemId,
                itemType: itemType
            };

            if (properties) {
                object.properties = properties;
            }

            return object;
        },

        _onSuccess: function (xhr) {
            window.cxs = JSON.parse(xhr.responseText);

            if (window.digitalData.loadCallbacks && window.digitalData.loadCallbacks.length > 0) {
                console.info('[WEM] Found context server load callbacks, calling now...');
                if (window.digitalData.loadCallbacks) {
                    for (var i = 0; i < window.digitalData.loadCallbacks.length; i++) {
                        window.digitalData.loadCallbacks[i](digitalData);
                    }
                }
                if (window.digitalData.personalizationCallback) {
                    for (var j = 0; j < window.digitalData.personalizationCallback.length; j++) {
                        window.digitalData.personalizationCallback[j].callback(cxs.personalizations[window.digitalData.personalizationCallback[j].personalization.id]);
                    }
                }
            }
            // Put a marker to be able to know when wem is full loaded, context is loaded, and callbacks have been executed.
            window.wemLoaded = true;
        },

        _executeFallback: function (logMessage) {
            console.warn('[WEM] execute fallback' + (logMessage ? (': ' + logMessage) : ''));
            wem.fallback = true;
            window.cxs = {};
            for (var index in window.digitalData.loadCallbacks) {
                window.digitalData.loadCallbacks[index]();
            }
            if (window.digitalData.personalizationCallback) {
                for (var i = 0; i < window.digitalData.personalizationCallback.length; i++) {
                    window.digitalData.personalizationCallback[i].callback([window.digitalData.personalizationCallback[i].personalization.strategyOptions.fallback]);
                }
            }
        },

        _processReferrer: function () {
            var referrerURL = digitalData.page.pageInfo.referringURL || document.referrer;
            var sameDomainReferrer = false;
            if (referrerURL) {
                // parse referrer URL
                var referrer = new URL(referrerURL);
                // Set sameDomainReferrer property
                sameDomainReferrer = referrer.host === window.location.host;

                // only process referrer if it's not coming from the same site as the current page
                if (!sameDomainReferrer) {
                    // get search element if it exists and extract search query if available
                    var search = referrer.search;
                    var query = undefined;
                    if (search && search != '') {
                        // parse parameters
                        var queryParams = [], param;
                        var queryParamPairs = search.slice(1).split('&');
                        for (var i = 0; i < queryParamPairs.length; i++) {
                            param = queryParamPairs[i].split('=');
                            queryParams.push(param[0]);
                            queryParams[param[0]] = param[1];
                        }

                        // try to extract query: q is Google-like (most search engines), p is Yahoo
                        query = queryParams.q || queryParams.p;
                        query = decodeURIComponent(query).replace(/\+/g, ' ');
                    }

                    // register referrer event
                    // Create deep copy of window.digitalData.page and add data to pageInfo sub object
                    if (window.digitalData && window.digitalData.page && window.digitalData.page.pageInfo) {
                        window.digitalData.page.pageInfo.referrerHost = referrer.host;
                        window.digitalData.page.pageInfo.referrerQuery = query;
                    }
                }
            }
            window.digitalData.page.pageInfo.sameDomainReferrer = sameDomainReferrer;
        },

        _formFactorySubmitEventListener: function (event) {
            console.info('[WEM] Registring Form Factory event callback');
            window.ffCallbacks.registerCallback(event.detail, function (formData, formInfo) {
                if (wem.formNamesToWatch.indexOf(formInfo.formName) > -1) {
                    console.info('[WEM] catching FF form ' + formInfo.formName);
                    var formEvent = wem.buildFormEvent(formInfo.formName);
                    formEvent.flattenedProperties = {
                        fields: transformer(formData.resultData)
                    };

                    var events = [];
                    events.push(formEvent);

                    if (window.consentCallbacks) {
                        // create array to keep track of consent callback that have been executed to avoid duplication
                        var executed = [];
                        for (var cIndex in window.consentCallbacks) {
                            var consentCallback = window.consentCallbacks[cIndex];
                            var consentIdentifier = consentCallback.formName + '_' + consentCallback.inputName;
                            // check form name of consent type to make sure we resolve current form consent only
                            if (consentCallback.formName === formInfo.formName && executed.indexOf(consentIdentifier) === -1) {
                                executed.push(consentIdentifier);
                                var consentTypeEvent = consentCallback.getConsentTypeEvent(formData, formInfo,
                                    consentCallback.inputName, consentCallback.consentTypeId);
                                events.push(consentTypeEvent);
                            }
                        }
                    }

                    wem.collectEvents({events: events},
                        function () {
                            console.info('[WEM] Form Factory event successfully submitted.');
                            formInfo.notifyFormFactoryOfCompletion('MFcallback');
                        },
                        function (xhr) {
                            console.error('[WEM] Error while collecting Form Factory event, XHR status: ' + xhr.status + ' ' + xhr.statusText);
                            xhr.abort();
                            formInfo.notifyFormFactoryOfCompletion('MFcallback');
                        }
                    );
                }

                function transformer(data) {
                    for (var key in data) {
                        if (Object.prototype.hasOwnProperty.call(data, key)) {
                            var obj = data[key];
                            if (obj.rendererName && obj.rendererName === 'country') {
                                // Grab country name only!!!
                                data[key] = obj.country.key;
                            }
                            if (obj.rendererName && obj.value) {
                                // Generic object with value key (rating etc.)
                                data[key] = obj.value;
                            }
                        }
                    }
                    return data;
                }
            });
        },

        _formSubmitEventListener: function (event) {
            console.info('[WEM] Registering form event callback');
            var form = event.target;
            var formName = form.getAttribute('name') ? form.getAttribute('name') : form.getAttribute('id');
            if (formName && wem.formNamesToWatch.indexOf(formName) > -1) {
                console.info('[WEM] catching form ' + formName);

                var eventCopy = document.createEvent('Event');
                // Define that the event name is 'build'.
                eventCopy.initEvent('submit', event.bubbles, event.cancelable);

                event.stopImmediatePropagation();
                event.preventDefault();

                var formEvent = wem.buildFormEvent(formName);
                // merge form properties with event properties
                formEvent.flattenedProperties = {
                    fields: wem._extractFormData(form)
                };

                wem.collectEvent(formEvent,
                    function () {
                        form.removeEventListener('submit', wem._formSubmitEventListener, true);
                        form.dispatchEvent(eventCopy);
                        if (!eventCopy.defaultPrevented && !eventCopy.cancelBubble) {
                            form.submit();
                        }
                        form.addEventListener('submit', wem._formSubmitEventListener, true);
                    },
                    function (xhr) {
                        console.error('[WEM] Error while collecting form event: ' + xhr.status + ' ' + xhr.statusText);
                        xhr.abort();
                        form.removeEventListener('submit', wem._formSubmitEventListener, true);
                        form.dispatchEvent(eventCopy);
                        if (!eventCopy.defaultPrevented && !eventCopy.cancelBubble) {
                            form.submit();
                        }
                        form.addEventListener('submit', wem._formSubmitEventListener, true);
                    }
                );
            }
        },

        _extractFormData: function (form) {
            var params = {};
            for (var i = 0; i < form.elements.length; i++) {
                var e = form.elements[i];
                // ignore empty and undefined key (e.name)
                if (e.name) {
                    switch (e.nodeName) {
                        case 'TEXTAREA':
                        case 'INPUT':
                            switch (e.type) {
                                case 'checkbox':
                                    var checkboxes = document.querySelectorAll('input[name="' + e.name + '"]');
                                    if (checkboxes.length > 1) {
                                        if (!params[e.name]) {
                                            params[e.name] = [];
                                        }
                                        if (e.checked) {
                                            params[e.name].push(e.value);
                                        }

                                    }
                                    break;
                                case 'radio':
                                    if (e.checked) {
                                        params[e.name] = e.value;
                                    }
                                    break;
                                default:
                                    if (!e.value || e.value == '') {
                                        // ignore element if no value is provided
                                        break;
                                    }
                                    params[e.name] = e.value;
                            }
                            break;
                        case 'SELECT':
                            if (e.options && e.options[e.selectedIndex]) {
                                if (e.multiple) {
                                    params[e.name] = [];
                                    for (var j = 0; j < e.options.length; j++) {
                                        if (e.options[j].selected) {
                                            params[e.name].push(e.options[j].value);
                                        }
                                    }
                                } else {
                                    params[e.name] = e.options[e.selectedIndex].value;
                                }
                            }
                            break;
                    }
                }
            }
            return params;
        },

        _createElementFromHTML: function (htmlString) {
            var div = document.createElement('div');
            div.innerHTML = htmlString.trim();

            return div;
        },

        _loadScript: function (url) {
            wem.ajax({
                url: url,
                type: 'GET',
                async: false,
                dataType: 'text/javascript, application/javascript, application/ecmascript, application/x-ecmascript, */*; q=0.01',
                success: function (response) {
                    // We need to call eval like this otherwise the object won't be link to the window
                    window['eval'].call(window, response.responseText);
                }
            });
        },

        _resolveId: function (id) {
            var source = Object.keys(digitalData.sourceLocalIdentifierMap).filter(function (source) {
                return id.indexOf(source) > 0;
            });
            return source ? id.replace(source, digitalData.sourceLocalIdentifierMap[source]) : id;

        },

        _enableWem: enable => {
            // display fallback if wem is not enable
            wem.fallback = !enable;
            // remove cookies, reset cxs
            if (!enable) {
                wem.cxs = {};
                document.cookie = 'wem-profile-id=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;';
                document.cookie = 'context-profile-id=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;';
                delete wem.contextLoaded;
            } else {
                if (wem.DOMLoaded) {
                    wem.loadContext();
                } else {
                    // As Dom loaded listener not triggered, enable global value.
                    window.activateWem = true;
                }
            }

            wem.ajax({
                url: `${window.digitalData.wemInitConfig.enableWemActionUrl}?wemEnabled=${enable}`,
                type: 'POST',
                async: true,
                success: () => {
                    console.log(`Wem SSR ${enable ? 'enabled' : 'disabled'}`);
                },
                error: () => {
                    console.error(`Error when ${enable ? 'enabling' : 'disabling'} Wem SSR`);
                }
            });
            console.log(`Wem ${enable ? 'enabled' : 'disabled'}`);
        },

        _deepMergeObjects: function (source, target) {
            if (!wem._isObject(target) || !wem._isObject(source)) {
                return source;
            }

            Object.keys(source).forEach(key => {
                const targetValue = target[key];
                const sourceValue = source[key];

                // concat arrays || merge objects || add new props
                if (Array.isArray(targetValue) && Array.isArray(sourceValue)) {
                    target[key] = targetValue.concat(sourceValue);
                } else if (wem._isObject(targetValue) && wem._isObject(sourceValue)) {
                    target[key] = wem._deepMergeObjects(sourceValue, Object.assign({}, targetValue));
                } else {
                    target[key] = sourceValue;
                }
            });

            return target;
        },

        _isObject: function (obj) {
            return obj && typeof obj === 'object';
        },

        _isInControlGroup: function (id) {
            if (window.cxs.profileProperties && window.cxs.profileProperties.unomiControlGroups) {
                let controlGroup = window.cxs.profileProperties.unomiControlGroups.find(controlGroup => controlGroup.id === id);
                if (controlGroup) {
                    return true;
                }
            }
            if (window.cxs.sessionProperties && window.cxs.sessionProperties.unomiControlGroups) {
                let controlGroup = window.cxs.sessionProperties.unomiControlGroups.find(controlGroup => controlGroup.id === id);
                if (controlGroup) {
                    return true;
                }
            }
            return false;
        }
    }
}
