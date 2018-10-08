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
'use strict';

var integration = require('@segment/analytics.js-integration');

var Unomi = module.exports = integration('Apache Unomi')
    .assumesPageview()
    .readyOnLoad()
    .global('cxs')
    .option('scope', 'systemscope')
    .option('url', 'http://localhost:8181')
    .option('timeoutInMilliseconds', 1500)
    .option('sessionCookieName', 'unomiSessionId')
    .option('sessionId');

/**
 * Initialize.
 *
 * @api public
 */
Unomi.prototype.initialize = function(page) {
    var self = this;
    this.analytics.on('invoke', function(msg) {
        var action = msg.action();
        var listener = 'on' + msg.action();
        self.debug('%s %o', action, msg);
        if (self[listener]) self[listener](msg);
    });

    this.analytics.personalize = function(personalization, callback) {
        this.emit('invoke', {action:function() {return "personalize"}, personalization:personalization, callback:callback});
    };

    // Standard to check if cookies are enabled in this browser
    if (!navigator.cookieEnabled) {
        this.executeFallback();
        return;
    }

    // digitalData come from a standard so we can keep the logic around it which can allow complex website to load more complex data
    window.digitalData = window.digitalData || {
        scope: this.options.scope
    };

    if (page) {
        var props = page.json().properties;
        var unomiPage = window.digitalData.page;
        if (!unomiPage) {
            unomiPage = window.digitalData.page = { pageInfo:{} }
        }
        this.fillPageData(unomiPage, props);
        window.digitalData.events = window.digitalData.events || [];
        window.digitalData.events.push(this.buildEvent('view', this.buildPage(unomiPage), this.buildSource(this.options.scope, 'site')))
    }

    if (!this.options.sessionId) {
        var cookie = require('component-cookie');

        this.sessionId = cookie(this.options.sessionCookieName);
        // so we should not need to implement our own
        if (!this.sessionId || this.sessionId === '') {
            this.sessionId = this.generateGuid();
            cookie(this.options.sessionCookieName, this.sessionId);
        }
    } else {
        this.sessionId = this.options.sessionId;
    }

    setTimeout(this.loadContext.bind(this), 0);
};

/**
 * Loaded.
 *
 * @api private
 * @return {boolean}
 */
Unomi.prototype.loaded = function() {
    return !!window.cxs;
};

/**
 * Page.
 *
 * @api public
 * @param {Page} page
 */
Unomi.prototype.onpage = function(page) {
    var unomiPage = { pageInfo:{} };
    this.fillPageData(unomiPage, page.json().properties);

    this.collectEvent(this.buildEvent('view', this.buildPage(unomiPage), this.buildSource(this.options.scope, 'site')));
};

Unomi.prototype.fillPageData = function(unomiPage, props) {
    unomiPage.attributes = [];
    unomiPage.consentTypes = [];
    unomiPage.pageInfo.pageName = unomiPage.pageInfo.pageName || props.title;
    unomiPage.pageInfo.pageID = unomiPage.pageInfo.pageID || props.path;
    unomiPage.pageInfo.pagePath = unomiPage.pageInfo.pagePath || props.path;
    unomiPage.pageInfo.destinationURL = unomiPage.pageInfo.destinationURL || props.url;
    unomiPage.pageInfo.referringURL = unomiPage.pageInfo.referringURL || props.referrer;
};


/**
 * Identify.
 *
 * @api public
 * @param {Identify} identify
 */
Unomi.prototype.onidentify = function(identify) {
    console.log('onidentify');
    console.log(identify);
    // this.collectEvent(identify.json());
};

/**
 * ontrack.
 *
 * @api private
 * @param {Track} track
 */
// TODO: figure out why we need this.
Unomi.prototype.ontrack = function(track) {
    console.log('ontrack');
    console.log(track);
    // var json = track.json();

    // delete json.traits;
    // this.collectEvent(json);
};

/**
 * This function is used to load the current context in the page
 *
 * @param {boolean} [skipEvents=false] Should we send the events
 * @param {boolean} [invalidate=false] Should we invalidate the current context
 */
Unomi.prototype.loadContext = function (skipEvents, invalidate) {
    this.contextLoaded = true;
    var jsonData = {
        requiredProfileProperties: ['j:nodename'],
        source: this.buildPage(window.digitalData.page)
    };
    if (!skipEvents) {
        jsonData.events = window.digitalData.events
    }
    if (window.digitalData.personalizationCallback) {
        jsonData.personalizations = window.digitalData.personalizationCallback.map(function (x) {
            return x.personalization
        })
    }

    jsonData.sessionId = this.sessionId;

    var contextUrl = this.options.url + '/context.json';
    if (invalidate) {
        contextUrl += '?invalidateSession=true&invalidateProfile=true';
    }

    var self = this;

    var onSuccess = function (xhr) {

        window.cxs = JSON.parse(xhr.responseText);

        self.ready();

        if (window.digitalData.loadCallbacks) {
            console.info('[UNOMI] Found context server load callbacks, calling now...');
            for (var i = 0; i < window.digitalData.loadCallbacks.length; i++) {
                window.digitalData.loadCallbacks[i](digitalData);
            }
        }
        if (window.digitalData.personalizationCallback) {
            console.info('[UNOMI] Found context server personalization, calling now...');
            for (var i = 0; i < window.digitalData.personalizationCallback.length; i++) {
                window.digitalData.personalizationCallback[i].callback(cxs.personalizations[window.digitalData.personalizationCallback[i].personalization.id]);
            }
        }
    };

    this.ajax({
        url: contextUrl,
        type: 'POST',
        async: true,
        contentType: 'text/plain;charset=UTF-8', // Use text/plain to avoid CORS preflight
        jsonData: jsonData,
        dataType: 'application/json',
        invalidate: invalidate,
        success: onSuccess,
        error: this.executeFallback
    });

    console.info('[UNOMI] Context loading...');
};

Unomi.prototype.onpersonalize = function (msg) {
    if (this.contextLoaded) {
        console.error('[UNOMI] Already loaded, too late...');
        return;
    }
    window.digitalData = window.digitalData || {
        scope: this.options.scope
    };
    window.digitalData.personalizationCallback = window.digitalData.personalizationCallback || [];
    window.digitalData.personalizationCallback.push({personalization: msg.personalization, callback: msg.callback});
};

/**
 * This function return the basic structure for an event, it must be adapted to your need
 *
 * @param {string} eventType The name of your event
 * @param {object} [target] The target object for your event can be build with this.buildTarget(targetId, targetType, targetProperties)
 * @param {object} [source] The source object for your event can be build with this.buildSource(sourceId, sourceType, sourceProperties)
 * @returns {{eventType: *, scope}}
 */
Unomi.prototype.buildEvent = function (eventType, target, source) {
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
};

/**
 * This function return an event of type form
 *
 * @param {string} formName The HTML name of id of the form to use in the target of the event
 * @returns {*|{eventType: *, scope, source: {scope, itemId: string, itemType: string, properties: {}}, target: {scope, itemId: string, itemType: string, properties: {}}}}
 */
Unomi.prototype.buildFormEvent = function (formName) {
    return this.buildEvent('form', this.buildTarget(formName, 'form'), this.buildSourcePage());
};

/**
 * This function return the source object for a source of type page
 *
 * @returns {*|{scope, itemId: *, itemType: *}}
 */
Unomi.prototype.buildTargetPage = function () {
    return this.buildTarget(window.digitalData.page.pageInfo.pageID, 'page', window.digitalData.page);
};

/**
 * This function return the source object for a source of type page
 *
 * @returns {*|{scope, itemId: *, itemType: *}}
 */
Unomi.prototype.buildPage = function (page) {
    return this.buildSource(page.pageInfo.pageID, 'page', page);
};

/**
 * This function return the basic structure for the target of your event
 *
 * @param {string} targetId The ID of the target
 * @param {string} targetType The type of the target
 * @param {object} [targetProperties] The optional properties of the target
 * @returns {{scope, itemId: *, itemType: *}}
 */
Unomi.prototype.buildTarget = function (targetId, targetType, targetProperties) {
    return this.buildObject(targetId, targetType, targetProperties);
};

/**
 * This function return the basic structure for the source of your event
 *
 * @param {string} sourceId The ID of the source
 * @param {string} sourceType The type of the source
 * @param {object} [sourceProperties] The optional properties of the source
 * @returns {{scope, itemId: *, itemType: *}}
 */
Unomi.prototype.buildSource = function (sourceId, sourceType, sourceProperties) {
    return this.buildObject(sourceId, sourceType, sourceProperties);
};


/**
 * This function will send an event to Apache Unomi
 * @param {object} event The event object to send, you can build it using this.buildEvent(eventType, target, source)
 * @param {function} successCallback will be executed in case of success
 * @param {function} errorCallback will be executed in case of error
 */
Unomi.prototype.collectEvent = function (event, successCallback, errorCallback) {
    this.collectEvents({events: [event]}, successCallback, errorCallback);
};

/**
 * This function will send the events to Apache Unomi
 *
 * @param {object} events Javascript object { events: [event1, event2] }
 * @param {function} successCallback will be executed in case of success
 * @param {function} errorCallback will be executed in case of error
 */
Unomi.prototype.collectEvents = function (events, successCallback, errorCallback) {
    events.sessionId = this.sessionId;

    var data = JSON.stringify(events);
    this.ajax({
        url: this.options.url + '/eventcollector',
        type: 'POST',
        async: true,
        contentType: 'text/plain;charset=UTF-8', // Use text/plain to avoid CORS preflight
        data: data,
        dataType: 'application/json',
        success: successCallback,
        error: errorCallback
    });
};

/*******************************/
/* Private Function under this */
/*******************************/

Unomi.prototype.registerEvent = function (event) {
    if (window.digitalData) {
        if (window.cxs) {
            console.error('[UNOMI] already loaded, too late...');
        } else {
            window.digitalData.events = window.digitalData.events || [];
            window.digitalData.events.push(event);
        }
    } else {
        window.digitalData = {};
        window.digitalData.events = window.digitalData.events || [];
        window.digitalData.events.push(event);
    }
};

Unomi.prototype.registerCallback = function (onLoadCallback) {
    if (window.digitalData) {
        if (window.cxs) {
            console.info('[UNOMI] digitalData object loaded, calling on load callback immediately and registering update callback...');
            if (onLoadCallback) {
                onLoadCallback(window.digitalData);
            }
        } else {
            console.info('[UNOMI] digitalData object present but not loaded, registering load callback...');
            if (onLoadCallback) {
                window.digitalData.loadCallbacks = window.digitalData.loadCallbacks || [];
                window.digitalData.loadCallbacks.push(onLoadCallback);
            }
        }
    } else {
        console.info('[UNOMI] No digital data object found, creating and registering update callback...');
        window.digitalData = {};
        if (onLoadCallback) {
            window.digitalData.loadCallbacks = [];
            window.digitalData.loadCallbacks.push(onLoadCallback);
        }
    }
};

/**
 * This is an utility function to generate a new UUID
 *
 * @returns {string}
 */
Unomi.prototype.generateGuid = function () {
    function s4() {
        return Math.floor((1 + Math.random()) * 0x10000)
            .toString(16)
            .substring(1);
    }

    return s4() + s4() + '-' + s4() + '-' + s4() + '-' +
        s4() + '-' + s4() + s4() + s4();
};

Unomi.prototype.buildObject = function (itemId, itemType, properties) {
    var object = {
        scope: window.digitalData.scope,
        itemId: itemId,
        itemType: itemType
    };

    if (properties) {
        object.properties = properties;
    }

    return object;
};

/**
 * This is an utility function to execute AJAX call
 *
 * @param {object} ajaxOptions
 */
Unomi.prototype.ajax = function (ajaxOptions) {
    var xhr = new XMLHttpRequest();
    if ('withCredentials' in xhr) {
        xhr.open(ajaxOptions.type, ajaxOptions.url, ajaxOptions.async);
        xhr.withCredentials = true;
    } else if (typeof XDomainRequest != 'undefined') {
        xhr = new XDomainRequest();
        xhr.open(ajaxOptions.type, ajaxOptions.url);
    }

    if (ajaxOptions.contentType) {
        xhr.setRequestHeader('Content-Type', ajaxOptions.contentType);
    }
    if (ajaxOptions.dataType) {
        xhr.setRequestHeader('Accept', ajaxOptions.dataType);
    }

    if (ajaxOptions.responseType) {
        xhr.responseType = ajaxOptions.responseType;
    }

    var requestExecuted = false;
    if (this.options.timeoutInMilliseconds !== -1) {
        setTimeout(function () {
            if (!requestExecuted) {
                console.error('[UNOMI] XML request timeout, url: ' + ajaxOptions.url);
                requestExecuted = true;
                if (ajaxOptions.error) {
                    ajaxOptions.error(xhr);
                }
            }
        }, this.options.timeoutInMilliseconds);
    }

    xhr.onreadystatechange = function () {
        if (!requestExecuted) {
            if (xhr.readyState === 4) {
                if (xhr.status === 200 || xhr.status === 204 || xhr.status === 304) {
                    if (xhr.responseText != null) {
                        requestExecuted = true;
                        if (ajaxOptions.success) {
                            ajaxOptions.success(xhr);
                        }
                    }
                } else {
                    requestExecuted = true;
                    if (ajaxOptions.error) {
                        ajaxOptions.error(xhr);
                    }
                    console.error('[UNOMI] XML request error: ' + xhr.statusText + ' (' + xhr.status + ')');
                }
            }
        }
    };

    if (ajaxOptions.jsonData) {
        xhr.send(JSON.stringify(ajaxOptions.jsonData));
    } else if (ajaxOptions.data) {
        xhr.send(ajaxOptions.data);
    } else {
        xhr.send();
    }
};

Unomi.prototype.executeFallback = function () {
    console.warn('[UNOMI] execute fallback');
    window.cxs = {};
    for (var index in window.digitalData.loadCallbacks) {
        window.digitalData.loadCallbacks[index]();
    }
    if (window.digitalData.personalizationCallback) {
        for (var i = 0; i < window.digitalData.personalizationCallback.length; i++) {
            window.digitalData.personalizationCallback[i].callback([window.digitalData.personalizationCallback[i].personalization.strategyOptions.fallback]);
        }
    }
};
