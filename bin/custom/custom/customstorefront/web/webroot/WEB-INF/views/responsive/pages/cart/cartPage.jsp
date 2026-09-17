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
	     minicart. Only rendered for a logged-in customer - populated in the model only then. --%>
	<c:if test="${not empty savedForLaterEntries}">
		<div class="cart-saved-for-later">
			<h3><spring:theme code="basket.page.savedForLater.title"/></h3>
			<ul class="item__list">
				<c:forEach items="${savedForLaterEntries}" var="savedEntry">
					<c:set var="savedProductCodeHtml" value="${fn:escapeXml(savedEntry.productCode)}"/>
					<li class="item__list--item">
						<div class="item__image">
							<a href="${fn:escapeXml(savedEntry.product.url)}">
								<product:productPrimaryImage product="${savedEntry.product}" format="thumbnail"/>
							</a>
						</div>
						<div class="item__info">
							<a href="${fn:escapeXml(savedEntry.product.url)}">
								<span class="item__name">${fn:escapeXml(savedEntry.product.name)}</span>
							</a>
							<div class="item__code">${savedProductCodeHtml}</div>
						</div>
						<div class="item__price">
							<format:price priceData="${savedEntry.product.price}" displayFreeForZero="true"/>
						</div>
						<div class="item__quantity">
							<span class="qtyValue">${fn:escapeXml(savedEntry.quantity)}</span>
						</div>
						<div class="item__menu">
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
					</li>
				</c:forEach>
			</ul>
		</div>
	</c:if>
</template:page>
