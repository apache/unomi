// base Javascript tag container

function loadXMLDoc(url, successCallBack) {
    var xmlHttp;
    if (window.XMLHttpRequest) {// code for IE7+, Firefox, Chrome, Opera, Safari
        xmlHttp = new XMLHttpRequest();
    }
    else {// code for IE6, IE5
        xmlHttp = new ActiveXObject("Microsoft.XMLHTTP");
    }
    xmlHttp.onreadystatechange = function () {
        if (xmlHttp.readyState == 4 && xmlHttp.status == 200) {
            successCallBack(xmlHttp);
        }
    }
    xmlHttp.open("GET", url, true);
    xmlHttp.send();
}