<%@ tag body-content="empty" trimDirectiveWhitespaces="true" %>
<%@ attribute name="entry" required="true" type="de.hybris.platform.commercefacades.order.data.OrderEntryData" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="format" tagdir="/WEB-INF/tags/shared/format" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>

<%--
    NET-8943: read-only list of the services bought with an order/cart line, with the charged price.
--%>

<spring:htmlEscape defaultHtmlEscape="true" />

<c:if test="${not empty entry.selectedServices}">
    <ul class="item__services--selected" style="list-style: none; padding: 0; margin: 6px 0 0 0;">
        <c:forEach items="${entry.selectedServices}" var="service">
            <li class="item__service">
                <span class="item__service--name">${fn:escapeXml(service.name)}</span>,
                <span class="item__service--price"><format:price priceData="${service.totalPrice}"/></span>
            </li>
        </c:forEach>
    </ul>
</c:if>
