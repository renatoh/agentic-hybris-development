<%@ tag body-content="empty" trimDirectiveWhitespaces="true" %>
<%@ attribute name="advancePaymentMode" required="false" type="de.hybris.platform.commercefacades.order.data.PaymentModeData" %>
<%@ attribute name="advancePaymentSelected" required="false" type="java.lang.Boolean" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>

<%--
	NET-8942: choice between credit card and advance payment on the payment step. Renders nothing when
	the advance payment mode is missing or inactive, so the page stays exactly as before. The card part
	of the page must be wrapped in an element with the class js-card-payment-section; it is hidden while
	advance payment is chosen. Credit card is pre-selected unless the cart already has advance payment.
--%>
<spring:htmlEscape defaultHtmlEscape="true" />

<c:if test="${not empty advancePaymentMode}">
	<div class="checkout-payment-type">
		<div class="radio">
			<label>
				<input type="radio" name="checkoutPaymentType" value="CARD" class="js-checkout-payment-type"
					${advancePaymentSelected ? '' : 'checked="checked"'}/>
				<spring:theme code="checkout.multi.paymentMethod.type.card"/>
			</label>
		</div>
		<div class="radio">
			<label>
				<input type="radio" name="checkoutPaymentType" value="ADVANCE_PAYMENT" class="js-checkout-payment-type"
					${advancePaymentSelected ? 'checked="checked"' : ''}/>
				${fn:escapeXml(advancePaymentMode.name)}
			</label>
		</div>
	</div>

	<div class="js-advance-payment-section" ${advancePaymentSelected ? '' : 'style="display:none"'}>
		<div class="checkout-indent">
			<p>${fn:escapeXml(advancePaymentMode.description)}</p>
		</div>
		<c:url value="/checkout/multi/payment-method/advance" var="advancePaymentUrl"/>
		<form:form id="advancePaymentForm" action="${advancePaymentUrl}" method="post">
			<button type="submit" class="btn btn-primary btn-block checkout-next">
				<spring:theme code="checkout.multi.paymentMethod.continue"/>
			</button>
		</form:form>
	</div>

	<script>
		(function () {
			var radios = document.querySelectorAll('.js-checkout-payment-type');
			function showSelectedPaymentType() {
				var advance = document.querySelector('.js-checkout-payment-type[value="ADVANCE_PAYMENT"]').checked;
				var cardSections = document.querySelectorAll('.js-card-payment-section');
				for (var i = 0; i < cardSections.length; i++) {
					cardSections[i].style.display = advance ? 'none' : '';
				}
				document.querySelector('.js-advance-payment-section').style.display = advance ? '' : 'none';
			}
			for (var i = 0; i < radios.length; i++) {
				radios[i].addEventListener('change', showSelectedPaymentType);
			}
			// one advance-payment request per click - a double submit would race on the cart's payment info
			document.getElementById('advancePaymentForm').addEventListener('submit', function () {
				this.querySelector('button[type="submit"]').disabled = true;
			});
			if (document.readyState === 'loading') {
				document.addEventListener('DOMContentLoaded', showSelectedPaymentType);
			} else {
				showSelectedPaymentType();
			}
		})();
	</script>
</c:if>
