<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="jcr" uri="http://www.jahia.org/tags/jcr" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="functions" uri="http://www.jahia.org/tags/functions" %>
<%@ taglib prefix="template" uri="http://www.jahia.org/tags/templateLib" %>
<template:addResources type="css" resources="wem.css" />
<template:addResources type="javascript" resources="wem.js" />
<jcr:nodeProperty node="${currentNode}" name="j:propertyName" var="propertyName"/>
<jcr:nodeProperty node="${currentNode}" name="j:propertyValue" var="propertyValue"/>

${wrappedContent}

${propertyName} = ${propertyValue}

<script type="text/javascript">

    (function () {

        var editMode = ${renderContext.editMode ? 'true' : 'false'};

        function digitalDataLoaded(digitalData) {
            console.log("Digital data object is loaded");
            if (digitalData.user && digitalData.user[0]) {
                if (digitalData.user[0].profiles && digitalData.user[0].profiles[0]) {
                    digitalData.user[0].profiles[0].profileInfo.${propertyName} = ${propertyValue};
                    wemi.saveContext("${wemiContextServerURL}/context.js", digitalData, function (xhr) {
                        console.log("User context updated successfully.");
                    });
            }
        }

        wem.registerCallbacks(digitalDataLoaded, null);

    })();

</script>
