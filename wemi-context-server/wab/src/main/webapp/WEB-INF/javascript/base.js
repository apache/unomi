// base Javascript tag container

var wemi = {
    loadXMLDoc: function (url, successCallBack) {
        var xhr;
        if (window.XMLHttpRequest) {// code for IE7+, Firefox, Chrome, Opera, Safari
            xhr = new XMLHttpRequest();
        }
        else {// code for IE6, IE5
            xhr = new ActiveXObject("Microsoft.XMLHTTP");
        }
        xhr.onreadystatechange = function () {
            if (xhr.readyState == 4 && xhr.status == 200) {
                successCallBack(xhr);
            }
        }
        xhr.open("GET", url, true);
        xhr.send();
    },

    saveContext: function (url, data, successCallback) {
        var xhr;
        if (window.XMLHttpRequest) {// code for IE7+, Firefox, Chrome, Opera, Safari
            xhr = new XMLHttpRequest();
        }
        else {// code for IE6, IE5
            xhr = new ActiveXObject("Microsoft.XMLHTTP");
        }
        xhr.onreadystatechange = function () {
            if (xhr.readyState == 4 && xhr.status == 200) {
                successCallBack(xhr);
            }
        }

        xhr.open("POST", url, true);
        xhr.setRequestHeader('Content-Type', 'application/json; charset=UTF-8');

        // send the collected data as JSON
        // @todo should we  add support for IE 6 and 7 that do not support JSON.stringify (neither does IE8 in compatibility mode)
        // see http://stackoverflow.com/questions/3326893/is-json-stringify-supported-by-ie-8
        xhr.send(JSON.stringify(data));

    }

};