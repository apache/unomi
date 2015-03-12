/*
 * #%L
 * context-server-wab
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2014 - 2015 Jahia Solutions
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
// base Javascript tag container

/*
 * Recursively merge properties of two objects
 */
cxs.merge = function (obj1, obj2) {

    for (var obj2Property in obj2) {
        try {
            // Property in destination object set; update its value.
            if (obj2[obj2Property].constructor == Object) {
                obj1[obj2Property] = this.mergeObjects(obj1[obj2Property], obj2[obj2Property]);

            } else {
                obj1[obj2Property] = obj2[obj2Property];

            }

        } catch (e) {
            // Property in destination object not set; create it and set its value.
            obj1[obj2Property] = obj2[obj2Property];

        }
    }

    return obj1;
};

cxs.createCORSRequest = function (method, url) {
    var xhr = new XMLHttpRequest();
    if ("withCredentials" in xhr) {

        // Check if the XMLHttpRequest object has a "withCredentials" property.
        // "withCredentials" only exists on XMLHTTPRequest2 objects.
        xhr.withCredentials = true;
        xhr.open(method, url, true);

    } else if (typeof XDomainRequest != "undefined") {

        // Otherwise, check if XDomainRequest.
        // XDomainRequest only exists in IE, and is IE's way of making CORS requests.
        xhr = new XDomainRequest();
        xhr.open(method, url);

    } else {

        // Otherwise, CORS is not supported by the browser.
        xhr = null;

    }
    return xhr;
};

cxs.loadXMLDoc = function (url, successCallBack) {
    var xhr = this.createCORSRequest("GET", url);
    if (!xhr) {
        alert("CORS not supported by browser!");
    }
    xhr.onreadystatechange = function () {
        if (xhr.readyState == 4 && xhr.status == 200) {
            successCallBack(xhr);
        }
    }
    xhr.send();
};

cxs.collectEvent = function (event, successCallBack) {

};

cxs.collectEvents = function(events, successCallBack) {

};

cxs.createCookie = function (name, value, days) {
    if (days) {
        var date = new Date();
        date.setTime(date.getTime() + (days * 24 * 60 * 60 * 1000));
        var expires = "; expires=" + date.toGMTString();
    }
    else var expires = "";
    document.cookie = name + "=" + value + expires + "; path=/";
};

cxs.readCookie = function (name) {
    var nameEQ = name + "=";
    var ca = document.cookie.split(';');
    for (var i = 0; i < ca.length; i++) {
        var c = ca[i];
        while (c.charAt(0) == ' ') c = c.substring(1, c.length);
        if (c.indexOf(nameEQ) == 0) return c.substring(nameEQ.length, c.length);
    }
    return null;
};

cxs.eraseCookie = function (name) {
    createCookie(name, "", -1);
};


if (window.digitalData.loadCallbacks && window.digitalData.loadCallbacks.length > 0) {
    console.log("cxs: Found context server load callbacks, calling now...");
    if ( window.digitalData.loadCallbacks) {
        for (var i = 0; i < window.digitalData.loadCallbacks.length; i++) {
            window.digitalData.loadCallbacks[i](digitalData);
        }
    }
    if ( window.digitalData.filterCallback) {
        for (var i = 0; i < window.digitalData.filterCallback.length; i++) {
            window.digitalData.filterCallback[i].callback(cxs.filteringResults[window.digitalData.filterCallback[i].filter.filterid]);
        }
    }
}

console.log("cxs: context server script successfully initialized");
