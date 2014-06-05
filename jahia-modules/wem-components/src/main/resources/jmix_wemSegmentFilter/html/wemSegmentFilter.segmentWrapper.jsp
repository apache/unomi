<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="jcr" uri="http://www.jahia.org/tags/jcr" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<script type="text/javascript">
    alert('segmentList=${currentResource.moduleParams.segmentList}');

    <jcr:nodeProperty node="${currentNode}" name="j:segments" var="segmentList"/>
    <c:forEach items="${segmentList}" var="segment">
      alert('segment=${segment.string}');
    </c:forEach>

</script>
<div class="segmentContainer">
    <div class="segmentContent"> ${wrappedContent} </div>
    <div class="segmentOverlay"> <fmt:message key="label.segments.selected.overlay"/> </div>
</div>