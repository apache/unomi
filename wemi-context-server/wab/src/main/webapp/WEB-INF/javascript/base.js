// base Javascript tag container

var wemi = {

    /*
     * Recursively merge properties of two objects
     */
    merge: function (obj1, obj2) {

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
    },

    createCORSRequest: function (method, url) {
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
    },

    loadXMLDoc: function (url, successCallBack) {
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
    },

    saveContext: function (url, data, successCallBack) {
        var xhr = this.createCORSRequest("POST", url);
        if (!xhr) {
            alert("CORS not supported by browser!");
        }
        xhr.onreadystatechange = function () {
            if (xhr.readyState == 4 && xhr.status == 200) {
                var newDigitalData = JSON.parse(xhr.responseText);
                newDigitalData.loaded = window.digitalData.loaded;
                newDigitalData.loadCallbacks = window.digitalData.loadCallbacks;
                newDigitalData.updateCallbacks = window.digitalData.updateCallbacks;
                window.digitalData = newDigitalData;
                successCallBack(xhr);
                if (window.digitalData.updateCallbacks && window.digitalData.updateCallbacks.length > 0) {
                    console.log("wemi: Found WEMI context update callbacks, calling now...");
                    for (var i=0; i < window.digitalData.updateCallbacks.length; i++) {
                        window.digitalData.updateCallbacks[i](digitalData);
                    }
                }
            }
        }

        xhr.setRequestHeader('Content-Type', 'application/json; charset=UTF-8');

        // send the collected data as JSON
        // @todo should we  add support for IE 6 and 7 that do not support JSON.stringify (neither does IE8 in compatibility mode)
        // see http://stackoverflow.com/questions/3326893/is-json-stringify-supported-by-ie-8
        xhr.send(JSON.stringify(data));

    },

    collectEvent: function(baseURL, eventType, parameters, successCallBack) {
        // @todo we should pass the parameters as an array or a map instead of a string
        var xhr = this.createCORSRequest("GET", baseURL + "/" + eventType + "?" + parameters);
        if (!xhr) {
            alert("CORS not supported by browser!");
        }
        xhr.onreadystatechange = function () {
            if (xhr.readyState == 4 && xhr.status == 200) {
                var jsonResponse = JSON.parse(xhr.responseText);
                if (jsonResponse['updated']) {
                    var newDigitalData = jsonResponse['digitalData'];
                    newDigitalData.loaded = window.digitalData.loaded;
                    newDigitalData.loadCallbacks = window.digitalData.loadCallbacks;
                    newDigitalData.updateCallbacks = window.digitalData.updateCallbacks;
                    window.digitalData = newDigitalData;
                    successCallBack(xhr);
                    if (window.digitalData.updateCallbacks && window.digitalData.updateCallbacks.length > 0) {
                        console.log("wemi: Found WEMI context update callbacks, calling now...");
                        for (var i=0; i < window.digitalData.updateCallbacks.length; i++) {
                            window.digitalData.updateCallbacks[i](digitalData);
                        }
                    }
                } else {
                    successCallBack(xhr);
                }
            }
        }
        xhr.send();
    },

    createCookie : function (name,value,days) {
        if (days) {
            var date = new Date();
            date.setTime(date.getTime()+(days*24*60*60*1000));
            var expires = "; expires="+date.toGMTString();
        }
        else var expires = "";
        document.cookie = name+"="+value+expires+"; path=/";
    },

    readCookie : function (name) {
        var nameEQ = name + "=";
        var ca = document.cookie.split(';');
        for(var i=0;i < ca.length;i++) {
            var c = ca[i];
            while (c.charAt(0)==' ') c = c.substring(1,c.length);
            if (c.indexOf(nameEQ) == 0) return c.substring(nameEQ.length,c.length);
        }
        return null;
    },

    eraseCookie : function (name) {
        createCookie(name,"",-1);
    }

};

wemi.merge(window.digitalData, wemiDigitalData);

if (window.digitalData.loadCallbacks && window.digitalData.loadCallbacks.length > 0) {
    console.log("wemi: Found WEMI context load callbacks, calling now...");
    for (var i=0; i < window.digitalData.loadCallbacks.length; i++) {
        window.digitalData.loadCallbacks[i](digitalData);
    }
}

// set the cookie on the current page since it is not set by the server serving the page content
if (!wemi.readCookie("wemi-profileID")) {
    wemi.createCookie("wemi-profileID", window.digitalData.user[0].profiles[0].profileInfo.profileId);
}

console.log("wemi: wemi-profileID=" + wemi.readCookie("wemi-profileID"));

console.log("wemi: WEMI context script successfully initialized");
