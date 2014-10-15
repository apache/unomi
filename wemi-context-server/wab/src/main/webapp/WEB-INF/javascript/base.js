// base Javascript tag container

/*
 * Recursively merge properties of two objects
 */
wemi.merge = function (obj1, obj2) {

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

wemi.createCORSRequest = function (method, url) {
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

wemi.loadXMLDoc = function (url, successCallBack) {
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

wemi.collectEvent = function (eventType, parameters, successCallBack) {
    // @todo we should pass the parameters as an array or a map instead of a string
    var xhr = this.createCORSRequest("GET", window.digitalData.contextServerPublicUrl + "/eventcollector/" + eventType + "?sessionId=" + wemi.sessionId + "&" + parameters);
    if (!xhr) {
        alert("CORS not supported by browser!");
    }
    xhr.onreadystatechange = function () {
        if (xhr.readyState == 4 && xhr.status == 200) {
            var jsonResponse = JSON.parse(xhr.responseText);
            successCallBack(xhr);
        }
    }
    xhr.send();
};

wemi.createCookie = function (name, value, days) {
    if (days) {
        var date = new Date();
        date.setTime(date.getTime() + (days * 24 * 60 * 60 * 1000));
        var expires = "; expires=" + date.toGMTString();
    }
    else var expires = "";
    document.cookie = name + "=" + value + expires + "; path=/";
};

wemi.readCookie = function (name) {
    var nameEQ = name + "=";
    var ca = document.cookie.split(';');
    for (var i = 0; i < ca.length; i++) {
        var c = ca[i];
        while (c.charAt(0) == ' ') c = c.substring(1, c.length);
        if (c.indexOf(nameEQ) == 0) return c.substring(nameEQ.length, c.length);
    }
    return null;
};

wemi.eraseCookie = function (name) {
    createCookie(name, "", -1);
};


if (window.digitalData.loadCallbacks && window.digitalData.loadCallbacks.length > 0) {
    console.log("wemi: Found WEMI context load callbacks, calling now...");
    if ( window.digitalData.loadCallbacks) {
        for (var i = 0; i < window.digitalData.loadCallbacks.length; i++) {
            window.digitalData.loadCallbacks[i](digitalData);
        }
    }
    if ( window.digitalData.filterCallback) {
        for (var i = 0; i < window.digitalData.filterCallback.length; i++) {
            window.digitalData.filterCallback[i].callback(wemi.filteringResults[window.digitalData.filterCallback[i].filter.filterid]);
        }
    }
}

console.log("wemi: WEMI context script successfully initialized");
