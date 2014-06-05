<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="jcr" uri="http://www.jahia.org/tags/jcr" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<jcr:nodeProperty node="${currentNode}" name="j:segments" var="segmentList"/>

<script type="text/javascript">

    (function () {

        var segmentList = [
            <c:forEach items="${segmentList}" var="segment" varStatus="segmentStatus">
            '${segment.string}' <c:if test="${!segmentStatus.last}">, </c:if>
            </c:forEach>
        ];

        console.log('Server segmentList=' + segmentList);

        function contains(a, obj) {
            for (var i = 0; i < a.length; i++) {
                if (a[i] === obj) {
                    return true;
                }
            }
            return false;
        }

        function waitForDigitalData() {
            if (window.digitalData) {
                console.log("Found digitalData object !");
                digitalDataFound();
                return;
            }
            console.log("Waiting for digitalData object...");
            setTimeout(waitForDigitalData, 200);
        }

        function digitalDataFound() {
            if (window.digitalData) {
                console.log("Digital data object is present");
                if (digitalData.user && digitalData.user[0]) {
                    if (digitalData.user[0].profiles && digitalData.user[0].profiles[0]) {
                        userSegments = digitalData.user[0].profiles[0].profileInfo.segments;
                        console.log("User segments=" + userSegments);
                        var found = false;
                        for (var i=0; i < segmentList.length; i++) {
                            if (contains(userSegments, segmentList[i])) {
                                console.log(segmentList[i] + " is part of user segments " + userSegments);
                                document.writeln('<div class="segmentContainer">');
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            console.log("No matching segment found !");
                            document.writeln('<div class="segmentContainer" style="display:none">');
                        }
                    }
                }
            }
        }

        waitForDigitalData();

    })();

</script>
    <div class="segmentContent">
      ${wrappedContent}
    </div>
    <div class="segmentOverlay"> <fmt:message key="label.segments.selected.overlay"/> </div>
</div>