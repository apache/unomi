// base Javascript tag container

var wemi = {

    createCORSRequest : function (method, url) {
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
                successCallBack(xhr);
            }
        }

        xhr.setRequestHeader('Content-Type', 'application/json; charset=UTF-8');

        // send the collected data as JSON
        // @todo should we  add support for IE 6 and 7 that do not support JSON.stringify (neither does IE8 in compatibility mode)
        // see http://stackoverflow.com/questions/3326893/is-json-stringify-supported-by-ie-8
        xhr.send(JSON.stringify(data));

    }

};

console.log("WEMI context script successfully initialized");
