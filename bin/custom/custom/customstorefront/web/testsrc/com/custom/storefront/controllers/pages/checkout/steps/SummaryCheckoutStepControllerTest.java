/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.storefront.controllers.pages.checkout.steps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.acceleratorfacades.flow.CheckoutFlowFacade;
import de.hybris.platform.acceleratorfacades.order.AcceleratorCheckoutFacade;
import de.hybris.platform.acceleratorservices.enums.CheckoutPciOptionEnum;
import de.hybris.platform.acceleratorstorefrontcommons.breadcrumb.ResourceBreadcrumbBuilder;
import de.hybris.platform.acceleratorstorefrontcommons.checkout.steps.CheckoutStep;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.util.GlobalMessage;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.util.GlobalMessages;
import de.hybris.platform.acceleratorstorefrontcommons.forms.PlaceOrderForm;
import de.hybris.platform.cms2.model.pages.AbstractPageModel;
import de.hybris.platform.cms2.model.pages.ContentPageModel;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commercefacades.order.data.OrderData;
import de.hybris.platform.commerceservices.strategies.CheckoutCustomerStrategy;

import com.custom.facades.payment.AdvancePaymentCheckoutFacade;
import com.custom.storefront.controllers.ControllerConstants;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;


/**
 * Unit test for the NET-8942 additions to {@link SummaryCheckoutStepController}: no security code
 * for advance payment (AC 3, 6), refusing an order once advance payment was switched off (AC 9), and
 * placing orders for both payment types (AC 4, 6, 11).
 */
@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class SummaryCheckoutStepControllerTest
{
	private static final String PAYMENT_STEP = "redirect:/checkout/multi/payment-method/add";
	private static final String SUMMARY_PAGE = ControllerConstants.Views.Pages.MultiStepCheckout.CheckoutSummaryPage;
	private static final String NO_SECURITY_CODE_MESSAGE = "checkout.paymentMethod.noSecurityCode";
	private static final String NOT_AVAILABLE_MESSAGE = "checkout.multi.paymentMethod.advancePayment.notAvailable";

	@Mock
	private AdvancePaymentCheckoutFacade advancePaymentCheckoutFacade;
	@Mock
	private CheckoutFlowFacade checkoutFlowFacade;
	@Mock
	private AcceleratorCheckoutFacade checkoutFacade;
	@Mock
	private CheckoutCustomerStrategy checkoutCustomerStrategy;
	@Mock
	private ResourceBreadcrumbBuilder resourceBreadcrumbBuilder;

	private final Map<String, CheckoutStep> checkoutSteps = new HashMap<>();
	private CartData cartData;
	private boolean cartInvalid;
	private SummaryCheckoutStepController controller;

	@Before
	public void setUp()
	{
		checkoutSteps.put("summary", checkoutStep("redirect:/checkout/multi/summary/view"));
		checkoutSteps.put("payment-method", checkoutStep(PAYMENT_STEP));

		cartData = new CartData();
		cartData.setCode("cart-1");
		cartData.setCalculated(true);

		// A cart that passes every order form check except, depending on the test, the security code.
		lenient().when(Boolean.valueOf(checkoutFlowFacade.hasValidCart())).thenReturn(Boolean.TRUE);
		lenient().when(Boolean.valueOf(checkoutFlowFacade.hasNoDeliveryAddress())).thenReturn(Boolean.FALSE);
		lenient().when(Boolean.valueOf(checkoutFlowFacade.hasNoDeliveryMode())).thenReturn(Boolean.FALSE);
		lenient().when(Boolean.valueOf(checkoutFlowFacade.hasNoPaymentInfo())).thenReturn(Boolean.FALSE);
		lenient().when(checkoutFlowFacade.getSubscriptionPciOption()).thenReturn(CheckoutPciOptionEnum.DEFAULT);
		lenient().when(checkoutFacade.getCheckoutCart()).thenReturn(cartData);
		lenient().when(Boolean.valueOf(checkoutFacade.containsTaxValues())).thenReturn(Boolean.TRUE);

		controller = getSummaryCheckoutStepController();
	}

	// --- validateOrderForm (AC 3, 6) ---

	@Test
	public void shouldNotAskForASecurityCodeWithAdvancePayment()
	{
		givenAdvancePayment(true);
		final Model model = new ExtendedModelMap();

		final boolean invalid = controller.validateOrderForm(placeOrderForm(null), model);

		assertFalse(invalid);
		assertNoErrorMessage(model.asMap(), NO_SECURITY_CODE_MESSAGE);
	}

	@Test
	public void shouldStillAskForTheSecurityCodeOfACard()
	{
		givenCardPayment();
		final Model model = new ExtendedModelMap();

		final boolean invalid = controller.validateOrderForm(placeOrderForm(" "), model);

		assertTrue(invalid);
		assertErrorMessage(model.asMap(), NO_SECURITY_CODE_MESSAGE);
	}

	@Test
	public void shouldAcceptACardWithItsSecurityCode()
	{
		givenCardPayment();
		final Model model = new ExtendedModelMap();

		assertFalse(controller.validateOrderForm(placeOrderForm("123"), model));
	}

	@Test
	public void shouldNotAskForTheSecurityCodeOfACardWithoutTheDefaultPciOption()
	{
		givenCardPayment();
		lenient().when(checkoutFlowFacade.getSubscriptionPciOption()).thenReturn(CheckoutPciOptionEnum.SOP);
		final Model model = new ExtendedModelMap();

		assertFalse(controller.validateOrderForm(placeOrderForm(null), model));
	}

	// --- enterStep (AC 3) ---

	@Test
	public void shouldNotRequestASecurityCodeOnTheSummaryForAdvancePayment() throws Exception
	{
		givenAdvancePayment(true);
		final Model model = new ExtendedModelMap();

		assertEquals(SUMMARY_PAGE, controller.enterStep(model, new RedirectAttributesModelMap()));
		assertEquals(Boolean.FALSE, model.asMap().get("requestSecurityCode"));
	}

	@Test
	public void shouldStillRequestTheSecurityCodeOnTheSummaryForACard() throws Exception
	{
		givenCardPayment();
		final Model model = new ExtendedModelMap();

		controller.enterStep(model, new RedirectAttributesModelMap());

		assertEquals(Boolean.TRUE, model.asMap().get("requestSecurityCode"));
	}

	// --- placeOrder (AC 4, 6, 9) ---

	@Test
	public void shouldPlaceAnAdvancePaymentOrder() throws Exception
	{
		givenAdvancePayment(true);
		given(Boolean.valueOf(checkoutFacade.authorizePayment(any()))).willReturn(Boolean.TRUE);
		given(checkoutFacade.placeOrder()).willReturn(order("00001000"));

		final String result = controller.placeOrder(placeOrderForm(null), new ExtendedModelMap(), null,
				new RedirectAttributesModelMap());

		assertEquals("redirect:/checkout/orderConfirmation/00001000", result);
		verify(checkoutFacade).placeOrder();
	}

	@Test
	public void shouldRefuseAnAdvancePaymentOrderOnceAdvancePaymentWasSwitchedOff() throws Exception
	{
		givenAdvancePayment(false);
		final RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();

		final String result = controller.placeOrder(placeOrderForm(null), new ExtendedModelMap(), null, redirectAttributes);

		assertEquals(PAYMENT_STEP, result);
		assertErrorMessage(redirectAttributes.getFlashAttributes(), NOT_AVAILABLE_MESSAGE);
		verify(checkoutFacade, never()).authorizePayment(any());
		verify(checkoutFacade, never()).placeOrder();
	}

	@Test
	public void shouldPlaceACardOrderAfterAuthorization() throws Exception
	{
		givenCardPayment();
		given(Boolean.valueOf(checkoutFacade.authorizePayment("123"))).willReturn(Boolean.TRUE);
		given(checkoutFacade.placeOrder()).willReturn(order("00001001"));

		final String result = controller.placeOrder(placeOrderForm("123"), new ExtendedModelMap(), null,
				new RedirectAttributesModelMap());

		assertEquals("redirect:/checkout/orderConfirmation/00001001", result);
		verify(checkoutFacade).authorizePayment("123");
	}

	@Test
	public void shouldNotPlaceACardOrderWhenAuthorizationFails() throws Exception
	{
		givenCardPayment();
		given(Boolean.valueOf(checkoutFacade.authorizePayment("123"))).willReturn(Boolean.FALSE);
		final Model model = new ExtendedModelMap();

		final String result = controller.placeOrder(placeOrderForm("123"), model, null, new RedirectAttributesModelMap());

		assertEquals(SUMMARY_PAGE, result);
		assertErrorMessage(model.asMap(), "checkout.error.authorization.failed");
		verify(checkoutFacade, never()).placeOrder();
	}

	@Test
	public void shouldBounceAnInvalidCartBackToTheCartPage() throws Exception
	{
		givenAdvancePayment(false);
		cartInvalid = true;

		final String result = controller.placeOrder(placeOrderForm(null), new ExtendedModelMap(), null,
				new RedirectAttributesModelMap());

		assertEquals("redirect:/cart", result);
		verify(checkoutFacade, never()).placeOrder();
	}

	// --- helpers ---

	private void givenAdvancePayment(final boolean available)
	{
		lenient().when(Boolean.valueOf(advancePaymentCheckoutFacade.isAdvancePaymentSelected())).thenReturn(Boolean.TRUE);
		lenient().when(Boolean.valueOf(advancePaymentCheckoutFacade.isAdvancePaymentAvailable()))
				.thenReturn(Boolean.valueOf(available));
	}

	private void givenCardPayment()
	{
		lenient().when(Boolean.valueOf(advancePaymentCheckoutFacade.isAdvancePaymentSelected())).thenReturn(Boolean.FALSE);
		lenient().when(Boolean.valueOf(advancePaymentCheckoutFacade.isAdvancePaymentAvailable())).thenReturn(Boolean.TRUE);
	}

	private static PlaceOrderForm placeOrderForm(final String securityCode)
	{
		final PlaceOrderForm form = new PlaceOrderForm();
		form.setSecurityCode(securityCode);
		form.setTermsCheck(true);
		return form;
	}

	private static OrderData order(final String code)
	{
		final OrderData order = new OrderData();
		order.setCode(code);
		order.setGuid("guid-" + code);
		return order;
	}

	private static CheckoutStep checkoutStep(final String current)
	{
		final CheckoutStep step = new CheckoutStep();
		final Map<String, String> transitions = new HashMap<>();
		transitions.put(CheckoutStep.PREVIOUS, "redirect:/previous");
		transitions.put(CheckoutStep.CURRENT, current);
		transitions.put(CheckoutStep.NEXT, "redirect:/next");
		step.setTransitions(transitions);
		step.setProgressBarId("step");
		return step;
	}

	private static void assertErrorMessage(final Map<String, ?> attributes, final String expectedKey)
	{
		assertTrue("expected error message " + expectedKey + " in " + attributes, hasErrorMessage(attributes, expectedKey));
	}

	private static void assertNoErrorMessage(final Map<String, ?> attributes, final String unexpectedKey)
	{
		assertFalse("did not expect error message " + unexpectedKey, hasErrorMessage(attributes, unexpectedKey));
	}

	@SuppressWarnings("unchecked")
	private static boolean hasErrorMessage(final Map<String, ?> attributes, final String key)
	{
		final List<GlobalMessage> messages = (List<GlobalMessage>) attributes.get(GlobalMessages.ERROR_MESSAGES_HOLDER);
		return messages != null && messages.stream().anyMatch(message -> key.equals(message.getCode()));
	}

	/**
	 * Anonymous subclass to reach the collaborators of the accelerator base controllers (same
	 * approach as {@link MultiStepCheckoutControllerTest}); CMS/breadcrumb plumbing is stubbed out.
	 */
	private SummaryCheckoutStepController getSummaryCheckoutStepController()
	{
		lenient().when(resourceBreadcrumbBuilder.getBreadcrumbs(anyString())).thenReturn(List.of());
		lenient().when(Boolean.valueOf(checkoutCustomerStrategy.isAnonymousCheckout())).thenReturn(Boolean.FALSE);

		return new SummaryCheckoutStepController()
		{
			@Override
			protected AdvancePaymentCheckoutFacade getAdvancePaymentCheckoutFacade()
			{
				return advancePaymentCheckoutFacade;
			}

			@Override
			protected CheckoutFlowFacade getCheckoutFlowFacade()
			{
				return checkoutFlowFacade;
			}

			@Override
			protected AcceleratorCheckoutFacade getCheckoutFacade()
			{
				return checkoutFacade;
			}

			@Override
			protected CheckoutCustomerStrategy getCheckoutCustomerStrategy()
			{
				return checkoutCustomerStrategy;
			}

			@Override
			protected CheckoutStep getCheckoutStep(final String currentController)
			{
				return checkoutSteps.get(currentController);
			}

			@Override
			protected boolean validateCart(final RedirectAttributes redirectModel)
			{
				return cartInvalid;
			}

			@Override
			protected ResourceBreadcrumbBuilder getResourceBreadcrumbBuilder()
			{
				return resourceBreadcrumbBuilder;
			}

			@Override
			protected ContentPageModel getContentPageForLabelOrId(final String labelOrId)
			{
				return null;
			}

			@Override
			protected void storeCmsPageInModel(final Model model, final AbstractPageModel cmsPage)
			{
				// CMS plumbing
			}

			@Override
			protected void setUpMetaDataForContentPage(final Model model, final ContentPageModel contentPage)
			{
				// CMS plumbing
			}
		};
	}
}
