<%@ page trimDirectiveWhitespaces="true"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags"%>
<%@ taglib prefix="template" tagdir="/WEB-INF/tags/responsive/template"%>
<%@ taglib prefix="cms" uri="http://hybris.com/tld/cmstags"%>
<%@ taglib prefix="cart" tagdir="/WEB-INF/tags/responsive/cart" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="ycommerce" uri="http://hybris.com/tld/ycommercetags" %>
<%@ taglib prefix="product" tagdir="/WEB-INF/tags/responsive/product" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<%@ taglib prefix="format" tagdir="/WEB-INF/tags/shared/format" %>

<spring:htmlEscape defaultHtmlEscape="true" />

<template:page pageTitle="${pageTitle}">

	<cart:cartValidation/>
	<cart:cartPickupValidation/>

	<div class="cart-top-bar">
        <div class="text-right">
            <spring:theme var="textHelpHtml" code="text.help" />
            <a href="" class="help js-cart-help" data-help="${fn:escapeXml(textHelpHtml)}">${textHelpHtml}
                <span class="glyphicon glyphicon-info-sign"></span>
            </a>
            <div class="help-popup-content-holder js-help-popup-content">
                <div class="help-popup-content">
                    <strong>${fn:escapeXml(cartData.code)}</strong>
                    <spring:theme var="cartHelpContentVar" code="basket.page.cartHelpContent" htmlEscape="false" />
                    <c:set var="cartHelpContentVarSanitized" value="${ycommerce:sanitizeHTML(cartHelpContentVar)}" />
                    <div>${cartHelpContentVarSanitized}</div>
                </div>
            </div>
		</div>
	</div>

	<div>
		<div>
            <cms:pageSlot position="TopContent" var="feature">
                <cms:component component="${feature}" element="div" class="yComponentWrapper"/>
            </cms:pageSlot>
		</div>

	   <c:if test="${not empty cartData.rootGroups}">
           <cms:pageSlot position="CenterLeftContentSlot" var="feature">
                <cms:component component="${feature}" element="div" class="yComponentWrapper"/>
           </cms:pageSlot>
        </c:if>
		
		 <c:if test="${not empty cartData.rootGroups}">
            <cms:pageSlot position="CenterRightContentSlot" var="feature">
                <cms:component component="${feature}" element="div" class="yComponentWrapper"/>
            </cms:pageSlot>
            <cms:pageSlot position="BottomContentSlot" var="feature">
                <cms:component component="${feature}" element="div" class="yComponentWrapper"/>
            </cms:pageSlot>
		</c:if>
				
		<c:if test="${empty cartData.rootGroups}">
            <cms:pageSlot position="EmptyCartMiddleContent" var="feature">
                <cms:component component="${feature}" element="div" class="yComponentWrapper content__empty"/>
            </cms:pageSlot>
		</c:if>
	</div>

	<%-- Saved-for-later section (NET-8941 section 3): bottom of the cart page only, never the
	     minicart. Only rendered for a logged-in customer - populated in the model only then.

	     Deliberately its OWN row layout/CSS (saved-for-later-*), not a reuse of the cart entry's
	     item__list/item__list--item classes: those assume a fixed, larger set of sibling columns
	     (toggle, stock, delivery, total, ...) that this row doesn't have, so borrowing them squeezed
	     the name/price into the wrong column width and cut the text off (PR review). --%>
	<c:if test="${not empty savedForLaterEntries}">
		<style>
			.saved-for-later { margin-top: 30px; }
			.saved-for-later__title { font-size: 18px; margin-bottom: 15px; }
			.saved-for-later__row {
				display: flex;
				align-items: center;
				flex-wrap: wrap;
				gap: 15px;
				padding: 15px 0;
				border-bottom: 1px solid #e5e5e5;
			}
			.saved-for-later__image { flex: 0 0 auto; width: 80px; }
			.saved-for-later__image img { max-width: 100%; height: auto; }
			.saved-for-later__info {
				flex: 1 1 240px;
				min-width: 0;
				font-size: 14px;
				line-height: 1.4;
				white-space: normal;
				overflow: visible;
				word-break: break-word;
			}
			.saved-for-later__name { display: block; font-size: 14px; font-weight: bold; }
			.saved-for-later__code { font-size: 12px; color: #767676; }
			.saved-for-later__price { flex: 0 0 auto; font-size: 14px; white-space: nowrap; }
			.saved-for-later__quantity { flex: 0 0 auto; font-size: 14px; white-space: nowrap; }
			.saved-for-later__actions { flex: 0 0 auto; display: flex; align-items: center; gap: 10px; }
			.saved-for-later__actions form { display: inline; margin: 0; }
		</style>
		<div class="saved-for-later">
			<h3 class="saved-for-later__title"><spring:theme code="basket.page.savedForLater.title"/></h3>
			<c:forEach items="${savedForLaterEntries}" var="savedEntry">
				<c:set var="savedProductCodeHtml" value="${fn:escapeXml(savedEntry.productCode)}"/>
				<div class="saved-for-later__row">
					<div class="saved-for-later__image">
						<a href="${fn:escapeXml(savedEntry.product.url)}">
							<product:productPrimaryImage product="${savedEntry.product}" format="thumbnail"/>
						</a>
					</div>
					<div class="saved-for-later__info">
						<a href="${fn:escapeXml(savedEntry.product.url)}">
							<span class="saved-for-later__name">${fn:escapeXml(savedEntry.product.name)}</span>
						</a>
						<div class="saved-for-later__code">${savedProductCodeHtml}</div>
					</div>
					<div class="saved-for-later__price">
						<format:price priceData="${savedEntry.product.price}" displayFreeForZero="true"/>
					</div>
					<div class="saved-for-later__quantity">
						<spring:theme code="basket.page.qty"/>: ${fn:escapeXml(savedEntry.quantity)}
					</div>
					<div class="saved-for-later__actions">
						<c:url value="/cart/saved-for-later/${savedProductCodeHtml}/move-to-cart" var="moveToCartUrl"/>
						<form:form action="${moveToCartUrl}" method="post">
							<button type="submit" class="btn btn-primary js-saved-for-later-move-to-cart">
								<spring:theme code="basket.page.savedForLater.moveToCart"/>
							</button>
						</form:form>
						<c:url value="/cart/saved-for-later/${savedProductCodeHtml}/remove" var="removeSavedUrl"/>
						<form:form action="${removeSavedUrl}" method="post">
							<button type="submit" class="btn btn-link js-saved-for-later-remove">
								<spring:theme code="basket.page.savedForLater.remove"/>
							</button>
						</form:form>
					</div>
				</div>
			</c:forEach>
		</div>
	</c:if>
</template:page>
