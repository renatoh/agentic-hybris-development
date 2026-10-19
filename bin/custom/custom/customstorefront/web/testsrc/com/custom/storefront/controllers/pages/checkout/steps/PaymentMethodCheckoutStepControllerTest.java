/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.storefront.controllers.pages.checkout.steps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.BDDMockito.given;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.acceleratorfacades.flow.CheckoutFlowFacade;
import de.hybris.platform.acceleratorstorefrontcommons.breadcrumb.ResourceBreadcrumbBuilder;
import de.hybris.platform.acceleratorstorefrontcommons.checkout.steps.CheckoutStep;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.util.GlobalMessage;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.util.GlobalMessages;
import de.hybris.platform.cms2.model.pages.AbstractPageModel;
import de.hybris.platform.cms2.model.pages.ContentPageModel;
import de.hybris.platform.commercefacades.order.data.PaymentModeData;

import com.custom.facades.payment.AdvancePaymentCheckoutFacade;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
 * Unit test for the NET-8942 additions to {@link PaymentMethodCheckoutStepController}: the
 * advance-payment endpoint (AC 2, 9) and the model attributes the payment step renders the choice
 * from (AC 1). The card endpoints are unchanged accelerator code and not covered here.
 */
@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class PaymentMethodCheckoutStepControllerTest
{
	private static final String CURRENT_STEP = "redirect:/checkout/multi/payment-method/add";
	private static final String NEXT_STEP = "redirect:/checkout/multi/summary/view";
	private static final String NOT_AVAILABLE_MESSAGE = "checkout.multi.paymentMethod.advancePayment.notAvailable";

	@Mock
	private AdvancePaymentCheckoutFacade advancePaymentCheckoutFacade;
	@Mock
	private CheckoutFlowFacade checkoutFlowFacade;
	@Mock
	private ResourceBreadcrumbBuilder resourceBreadcrumbBuilder;

	private CheckoutStep checkoutStep;
	private PaymentMethodCheckoutStepController controller;

	@Before
	public void setUp()
	{
		checkoutStep = new CheckoutStep();
		final Map<String, String> transitions = new HashMap<>();
		transitions.put(CheckoutStep.PREVIOUS, "redirect:/checkout/multi/delivery-method/choose");
		transitions.put(CheckoutStep.CURRENT, CURRENT_STEP);
		transitions.put(CheckoutStep.NEXT, NEXT_STEP);
		checkoutStep.setTransitions(transitions);
		checkoutStep.setProgressBarId("paymentMethod");

		controller = getPaymentMethodCheckoutStepController();
	}

	@Test
	public void shouldMoveOnToTheSummaryWhenAdvancePaymentIsSet()
	{
		given(Boolean.valueOf(advancePaymentCheckoutFacade.setAdvancePayment())).willReturn(Boolean.TRUE);
		final RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();

		assertEquals(NEXT_STEP, controller.selectAdvancePayment(redirectAttributes));
		assertTrue(redirectAttributes.getFlashAttributes().isEmpty());
	}

	@Test
	public void shouldStayOnThePaymentStepWhenAdvancePaymentIsRejected()
	{
		given(Boolean.valueOf(advancePaymentCheckoutFacade.setAdvancePayment())).willReturn(Boolean.FALSE);
		final RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();

		assertEquals(CURRENT_STEP, controller.selectAdvancePayment(redirectAttributes));
		assertErrorMessage(redirectAttributes.getFlashAttributes(), NOT_AVAILABLE_MESSAGE);
	}

	@Test
	public void shouldOfferAdvancePaymentOnThePaymentStepWhenItIsAvailable() throws Exception
	{
		final PaymentModeData advanceMode = new PaymentModeData();
		advanceMode.setCode(AdvancePaymentCheckoutFacade.ADVANCE_PAYMENT_MODE_CODE);
		advanceMode.setName("Advance payment");
		given(advancePaymentCheckoutFacade.getAdvancePaymentMode()).willReturn(Optional.of(advanceMode));
		given(Boolean.valueOf(advancePaymentCheckoutFacade.isAdvancePaymentSelected())).willReturn(Boolean.TRUE);
		final Model model = new ExtendedModelMap();

		controller.setupAddPaymentPage(model);

		assertSame(advanceMode, model.asMap().get("advancePaymentMode"));
		assertEquals(Boolean.TRUE, model.asMap().get("advancePaymentSelected"));
	}

	@Test
	public void shouldOnlyOfferCardsWhenAdvancePaymentIsNotAvailable() throws Exception
	{
		given(advancePaymentCheckoutFacade.getAdvancePaymentMode()).willReturn(Optional.empty());
		given(Boolean.valueOf(advancePaymentCheckoutFacade.isAdvancePaymentSelected())).willReturn(Boolean.FALSE);
		final Model model = new ExtendedModelMap();

		controller.setupAddPaymentPage(model);

		assertTrue(model.asMap().containsKey("advancePaymentMode"));
		assertNull(model.asMap().get("advancePaymentMode"));
		assertEquals(Boolean.FALSE, model.asMap().get("advancePaymentSelected"));
	}

	private static void assertErrorMessage(final Map<String, ?> attributes, final String expectedKey)
	{
		@SuppressWarnings("unchecked")
		final List<GlobalMessage> messages = (List<GlobalMessage>) attributes.get(GlobalMessages.ERROR_MESSAGES_HOLDER);
		assertTrue("expected error message " + expectedKey,
				messages != null && messages.stream().anyMatch(message -> expectedKey.equals(message.getCode())));
	}

	/**
	 * Anonymous subclass to reach the collaborators of the accelerator base controllers (same
	 * approach as {@link MultiStepCheckoutControllerTest}); the CMS/breadcrumb plumbing is stubbed out.
	 */
	private PaymentMethodCheckoutStepController getPaymentMethodCheckoutStepController()
	{
		return new PaymentMethodCheckoutStepController()
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
			protected CheckoutStep getCheckoutStep()
			{
				return checkoutStep;
			}

			@Override
			protected ResourceBreadcrumbBuilder getResourceBreadcrumbBuilder()
			{
				return resourceBreadcrumbBuilder;
			}

			@Override
			protected void prepareDataForPage(final Model model)
			{
				// countries, express checkout flags - not part of NET-8942
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
