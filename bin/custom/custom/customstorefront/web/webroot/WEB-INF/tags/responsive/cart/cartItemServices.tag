<%@ tag body-content="empty" trimDirectiveWhitespaces="true" %>
<%@ attribute name="entry" required="true" type="de.hybris.platform.commercefacades.order.data.OrderEntryData" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<%@ taglib prefix="format" tagdir="/WEB-INF/tags/shared/format" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>

<%--
    NET-8943: services (warranty extension, installation) the shopper can tick for a cart line.
    Ticking posts to /cart/entry/{entryNumber}/services/{serviceCode}/add, unticking to .../remove (CSRF token via form:form).
--%>

<spring:htmlEscape defaultHtmlEscape="true" />

<c:if test="${not empty entry.availableServices}">
    <div class="item__services" data-entry-number="${fn:escapeXml(entry.entryNumber)}" style="margin: 10px 0 0 0;">
        <div class="item__services--title"><strong><spring:theme code="basket.page.services"/></strong></div>
        <c:forEach items="${entry.availableServices}" var="service">
            <c:set var="serviceAction" value="${service.selected ? 'remove' : 'add'}"/>
            <spring:url value="/cart/entry/{entryNumber}/services/{serviceCode}/{action}" var="serviceUrl" htmlEscape="false">
                <spring:param name="entryNumber" value="${entry.entryNumber}"/>
                <spring:param name="serviceCode" value="${service.code}"/>
                <spring:param name="action" value="${serviceAction}"/>
            </spring:url>
            <form:form action="${serviceUrl}" method="post" cssClass="js-product-service-form" style="margin: 0;">
                <label class="item__service" style="font-weight: normal;">
                    <input type="checkbox" class="js-product-service-toggle" name="service_${fn:escapeXml(service.code)}"
                           ${service.selected ? 'checked="checked"' : ''} onchange="this.form.submit()"/>
                    <span class="item__service--name">${fn:escapeXml(service.name)}</span>,
                    <span class="item__service--price"><format:price priceData="${service.price}"/></span>
                    <c:if test="${service.quantity > 1}">
                        <spring:theme code="basket.page.services.each"/>
                        (<format:price priceData="${service.totalPrice}"/>)
                    </c:if>
                </label>
            </form:form>
        </c:forEach>
    </div>
</c:if>
