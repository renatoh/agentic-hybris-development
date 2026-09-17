<%@ page trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%@ taglib prefix="template" tagdir="/WEB-INF/tags/responsive/template" %>
<%@ taglib prefix="product" tagdir="/WEB-INF/tags/responsive/product" %>

<spring:htmlEscape defaultHtmlEscape="true" />

<template:page pageTitle="${pageTitle}">

	<jsp:body>

		<div class="product-comparison-page">

			<h1 class="page-title"><spring:theme code="product.compare.page.title"/></h1>

			<c:choose>
				<c:when test="${empty comparisonLists}">
					<p class="product-comparison-empty"><spring:theme code="product.compare.page.empty"/></p>
				</c:when>
				<c:otherwise>

					<c:if test="${fn:length(comparisonLists) > 1}">
						<form action="" method="get" class="product-comparison-list-switcher">
							<label for="product-comparison-list-select"><spring:theme code="product.compare.page.selectList"/></label>
							<select id="product-comparison-list-select" name="list" class="form-control"
									onchange="location.href = '?list=' + this.value;">
								<c:forEach items="${comparisonLists}" var="list">
									<option value="${fn:escapeXml(list.id)}" ${list.id eq selectedListId ? 'selected="selected"' : ''}>
										${fn:escapeXml(list.label)} (${list.productCount})
									</option>
								</c:forEach>
							</select>
						</form>
					</c:if>

					<c:if test="${not empty comparisonTable}">
						<h2 class="product-comparison-list-label">${fn:escapeXml(comparisonTable.label)}</h2>

						<c:url value="/compare/${fn:escapeXml(comparisonTable.listId)}/delete" var="deleteListUrl"/>
						<form action="${fn:escapeXml(deleteListUrl)}" method="post" class="product-comparison-delete-list-form">
							<sec:csrfInput/>
							<button type="submit" class="btn btn-danger product-comparison-delete-list-button js-product-comparison-delete-list"
									data-confirm-message="<spring:theme code="product.compare.page.deleteList.confirm"/>">
								<spring:theme code="product.compare.page.deleteList"/>
							</button>
						</form>

						<div class="product-comparison-table-wrap">
							<table class="product-comparison-table">
								<thead>
									<tr>
										<th class="product-comparison-row-label">&nbsp;</th>
										<c:forEach items="${comparisonTable.products}" var="compareProduct">
											<th>
												<c:url value="${compareProduct.url}" var="compareProductUrl"/>
												<a href="${fn:escapeXml(compareProductUrl)}"><product:productPrimaryImage product="${compareProduct}" format="thumbnail"/></a>
												<div class="product-comparison-product-name">
													<a href="${fn:escapeXml(compareProductUrl)}">${fn:escapeXml(compareProduct.name)}</a>
												</div>
												<div class="product-comparison-product-price">${fn:escapeXml(compareProduct.price.formattedValue)}</div>
												<c:url value="/compare/${fn:escapeXml(comparisonTable.listId)}/remove/${fn:escapeXml(compareProduct.code)}" var="removeFromCompareUrl"/>
												<form action="${fn:escapeXml(removeFromCompareUrl)}" method="post" class="product-comparison-remove-form">
													<sec:csrfInput/>
													<button type="submit" class="btn btn-link js-product-comparison-remove">
														<spring:theme code="product.compare.page.remove"/>
													</button>
												</form>
											</th>
										</c:forEach>
									</tr>
								</thead>
								<tbody>
									<c:forEach items="${comparisonTable.rows}" var="row">
										<tr>
											<th class="product-comparison-row-label">${fn:escapeXml(row.attributeName)}</th>
											<c:forEach items="${row.values}" var="value">
												<td>${fn:escapeXml(value)}</td>
											</c:forEach>
										</tr>
									</c:forEach>
								</tbody>
							</table>
						</div>
					</c:if>

				</c:otherwise>
			</c:choose>

		</div>

		<script>
			(function () {
				// Bulk delete-list action needs its own confirmation, distinct from the per-product
				// remove buttons - reads the message via getAttribute (already HTML-decoded by the
				// browser) rather than inlining it into a JS string literal, so it is safe regardless
				// of quotes/apostrophes in the translated text.
				var deleteButtons = document.querySelectorAll('.js-product-comparison-delete-list');
				for (var i = 0; i < deleteButtons.length; i++) {
					deleteButtons[i].addEventListener('click', function (event) {
						var message = this.getAttribute('data-confirm-message');
						if (message && !window.confirm(message)) {
							event.preventDefault();
						}
					});
				}
			})();
		</script>

	</jsp:body>

</template:page>
