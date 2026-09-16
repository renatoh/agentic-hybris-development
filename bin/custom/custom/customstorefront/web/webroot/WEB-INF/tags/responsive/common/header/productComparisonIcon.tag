<%@ tag body-content="empty" trimDirectiveWhitespaces="true"%>

<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions"%>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags"%>
<%@ taglib prefix="ycommerce" uri="http://hybris.com/tld/ycommercetags"%>

<%-- NET-8940: header icon next to the minicart, linking to the comparison page. Hidden/disabled
     when the session has no comparison lists yet (acceptance criterion 5). productComparisonListCount
     and productComparisonProductCount are populated on every page by ProductComparisonBeforeViewHandler.
     The badge shows the total product count across all lists, not the list count (section 8, open
     question 2), while visibility is still gated on list count. --%>
<c:set var="productCompareListCount" value="${empty productComparisonListCount ? 0 : productComparisonListCount}"/>
<c:set var="productCompareProductCount" value="${empty productComparisonProductCount ? 0 : productComparisonProductCount}"/>

<c:if test="${productCompareListCount > 0}">
	<c:url value="/product-compare" var="productCompareUrl"/>
	<ycommerce:testId code="header_ProductCompare_link">
		<a href="${fn:escapeXml(productCompareUrl)}" class="js-product-compare-icon product-compare-icon">
			<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 384 512" width="24" height="24" alt="a"><path fill="currentColor" d="M145.5 68c5.3-20.7 24.1-36 46.5-36s41.2 15.3 46.5 36l3.1 12H288v48H96V80h46.4zM192 0c-32.8 0-61 19.8-73.3 48H64v32H0v432h384V80h-64V48h-54.7C253 19.8 224.8 0 192 0m128 144v-32h32v368H32V112h32v48h256zM208 80a16 16 0 1 0-32 0 16 16 0 1 0 32 0m-72 192a24 24 0 1 0-48 0 24 24 0 1 0 48 0m40-16h-16v32h128v-32H176m0 96h-16v32h128v-32H176m-64 40a24 24 0 1 0 0-48 24 24 0 1 0 0 48"></path></svg>
			<span class="product-compare-icon-badge">${productCompareProductCount}</span>
			<span class="sr-only"><spring:theme code="product.compare.icon.alt"/></span>
		</a>
	</ycommerce:testId>
</c:if>
