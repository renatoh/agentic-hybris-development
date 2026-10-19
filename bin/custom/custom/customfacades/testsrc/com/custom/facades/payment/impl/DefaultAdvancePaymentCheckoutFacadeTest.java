/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.facades.payment.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commercefacades.order.CartFacade;
import de.hybris.platform.commercefacades.order.data.CCPaymentInfoData;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commercefacades.order.data.PaymentModeData;
import de.hybris.platform.commerceservices.order.CommerceCheckoutService;
import de.hybris.platform.commerceservices.service.data.CommerceCheckoutParameter;
import de.hybris.platform.commerceservices.customer.CustomerAccountService;
import de.hybris.platform.commerceservices.customer.CustomerEmailResolutionService;
import de.hybris.platform.commerceservices.strategies.CheckoutCustomerStrategy;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.order.payment.AdvancePaymentInfoModel;
import de.hybris.platform.core.model.order.payment.CreditCardPaymentInfoModel;
import de.hybris.platform.core.model.order.payment.PaymentInfoModel;
import de.hybris.platform.core.model.order.payment.PaymentModeModel;
import de.hybris.platform.core.model.user.AddressModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.order.CartService;
import de.hybris.platform.order.PaymentModeService;
import de.hybris.platform.payment.dto.TransactionStatus;
import de.hybris.platform.payment.model.PaymentTransactionEntryModel;
import de.hybris.platform.servicelayer.dto.converter.Converter;
import de.hybris.platform.servicelayer.exceptions.AmbiguousIdentifierException;
import de.hybris.platform.servicelayer.exceptions.UnknownIdentifierException;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.user.AddressService;

import com.custom.facades.payment.AdvancePaymentCheckoutFacade;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.Invocation;
import org.mockito.junit.MockitoJUnitRunner;


/**
 * Unit test for {@link DefaultAdvancePaymentCheckoutFacade} (NET-8942, AC 2-4, 6, 7, 9, 11).
 * <p>
 * Models are real (detached) model instances so the test asserts on the state the facade leaves
 * on the cart and the new payment info, not on individual setter calls. Persistence calls on
 * {@link ModelService} are asserted overload-agnostically (save/saveAll, remove/removeAll) via
 * {@link #argumentsOf(Object, String...)}, since which overload the implementation uses is not
 * part of the contract.
 */
@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class DefaultAdvancePaymentCheckoutFacadeTest
{
	private static final String CARD_PAYMENT_INFO_ID = "8796093087786";

	@Mock
	private PaymentModeService paymentModeService;
	@Mock
	private Converter<PaymentModeModel, PaymentModeData> paymentModeConverter;
	@Mock
	private Converter<CreditCardPaymentInfoModel, CCPaymentInfoData> creditCardPaymentInfoConverter;
	@Mock
	private AddressService addressService;
	@Mock
	private CartFacade cartFacade;
	@Mock
	private CartService cartService;
	@Mock
	private ModelService modelService;
	@Mock
	private CommerceCheckoutService commerceCheckoutService;
	@Mock
	private CheckoutCustomerStrategy checkoutCustomerStrategy;
	@Mock
	private CustomerAccountService customerAccountService;
	@Mock
	private CustomerEmailResolutionService customerEmailResolutionService;

	private DefaultAdvancePaymentCheckoutFacade facade;

	private CartModel cart;
	private CustomerModel customer;
	private AddressModel deliveryAddress;
	private PaymentModeModel advanceMode;
	private final List<AdvancePaymentInfoModel> createdPaymentInfos = new ArrayList<>();
	private final List<AddressModel> clonedAddresses = new ArrayList<>();

	@Before
	public void setUp()
	{
		facade = new DefaultAdvancePaymentCheckoutFacade();
		facade.setPaymentModeService(paymentModeService);
		facade.setPaymentModeConverter(paymentModeConverter);
		facade.setAddressService(addressService);
		facade.setCartFacade(cartFacade);
		facade.setCartService(cartService);
		facade.setModelService(modelService);
		facade.setCommerceCheckoutService(commerceCheckoutService);
		facade.setCheckoutCustomerStrategy(checkoutCustomerStrategy);
		facade.setCustomerAccountService(customerAccountService);
		facade.setCreditCardPaymentInfoConverter(creditCardPaymentInfoConverter);
		facade.setCustomerEmailResolutionService(customerEmailResolutionService);

		customer = new CustomerModel();
		customer.setUid("shopper@example.com");

		deliveryAddress = new AddressModel();
		deliveryAddress.setStreetname("Delivery street");

		cart = new CartModel();
		cart.setCode("cart-1");
		cart.setUser(customer);
		cart.setDeliveryAddress(deliveryAddress);
		cart.setCalculated(Boolean.TRUE);

		advanceMode = new PaymentModeModel();
		advanceMode.setCode(AdvancePaymentCheckoutFacade.ADVANCE_PAYMENT_MODE_CODE);
		advanceMode.setActive(Boolean.TRUE);

		// Session cart / checkout user wiring - which of these an individual code path consults is an
		// implementation detail, hence lenient.
		lenient().when(Boolean.valueOf(cartFacade.hasSessionCart())).thenReturn(Boolean.TRUE);
		lenient().when(Boolean.valueOf(cartService.hasSessionCart())).thenReturn(Boolean.TRUE);
		lenient().when(cartService.getSessionCart()).thenReturn(cart);
		lenient().when(checkoutCustomerStrategy.getCurrentUserForCheckout()).thenReturn(customer);
		lenient().when(paymentModeService.getPaymentModeForCode(AdvancePaymentCheckoutFacade.ADVANCE_PAYMENT_MODE_CODE))
				.thenReturn(advanceMode);
		lenient().when(modelService.create(AdvancePaymentInfoModel.class)).thenAnswer(invocation -> {
			final AdvancePaymentInfoModel created = new AdvancePaymentInfoModel();
			createdPaymentInfos.add(created);
			return created;
		});
		lenient().when(addressService.cloneAddressForOwner(any(AddressModel.class), any())).thenAnswer(invocation -> {
			final AddressModel clone = new AddressModel();
			final AddressModel original = invocation.getArgument(0);
			clone.setStreetname(original.getStreetname());
			clone.setEmail(original.getEmail());
			clone.setOwner(invocation.getArgument(1));
			clonedAddresses.add(clone);
			return clone;
		});
		lenient().when(Boolean.valueOf(commerceCheckoutService.setPaymentInfo(any(CommerceCheckoutParameter.class))))
				.thenReturn(Boolean.TRUE);
		lenient().when(customerEmailResolutionService.getEmailForCustomer(customer)).thenReturn("shopper@example.com");
	}

	// --- getAdvancePaymentMode / isAdvancePaymentAvailable (AC 1) ---

	@Test
	public void shouldOfferTheAdvancePaymentModeWhenItIsActive()
	{
		final PaymentModeData modeData = new PaymentModeData();
		modeData.setCode(AdvancePaymentCheckoutFacade.ADVANCE_PAYMENT_MODE_CODE);
		given(paymentModeConverter.convert(advanceMode)).willReturn(modeData);

		final Optional<PaymentModeData> result = facade.getAdvancePaymentMode();

		assertTrue(result.isPresent());
		assertSame(modeData, result.get());
		assertTrue(facade.isAdvancePaymentAvailable());
	}

	@Test
	public void shouldNotOfferTheAdvancePaymentModeWhenItIsInactive()
	{
		advanceMode.setActive(Boolean.FALSE);

		assertFalse(facade.getAdvancePaymentMode().isPresent());
		assertFalse(facade.isAdvancePaymentAvailable());
		verify(paymentModeConverter, never()).convert(any());
	}

	@Test
	public void shouldTreatAnUnsetActiveFlagAsInactive()
	{
		advanceMode.setActive(null);

		assertFalse(facade.getAdvancePaymentMode().isPresent());
		assertFalse(facade.isAdvancePaymentAvailable());
	}

	@Test
	public void shouldNotOfferTheAdvancePaymentModeWhenItIsMissing()
	{
		given(paymentModeService.getPaymentModeForCode(AdvancePaymentCheckoutFacade.ADVANCE_PAYMENT_MODE_CODE))
				.willThrow(new UnknownIdentifierException("no payment mode advance"));

		assertFalse(facade.getAdvancePaymentMode().isPresent());
		assertFalse(facade.isAdvancePaymentAvailable());
	}

	@Test
	public void shouldNotOfferTheAdvancePaymentModeWhenTheCodeIsAmbiguous()
	{
		given(paymentModeService.getPaymentModeForCode(AdvancePaymentCheckoutFacade.ADVANCE_PAYMENT_MODE_CODE))
				.willThrow(new AmbiguousIdentifierException("two payment modes advance"));

		assertFalse(facade.getAdvancePaymentMode().isPresent());
		assertFalse(facade.isAdvancePaymentAvailable());
	}

	// --- setAdvancePayment (AC 2, 4, 7, 9) ---

	@Test
	public void shouldSetAnAdvancePaymentInfoAndThePaymentModeOnTheCart()
	{
		final boolean result = facade.setAdvancePayment();

		assertTrue(result);
		assertEquals(1, createdPaymentInfos.size());
		final AdvancePaymentInfoModel info = createdPaymentInfos.get(0);
		assertNotNull("payment info code must be set", info.getCode());
		assertSame(customer, info.getUser());
		assertSame(cart, info.getOwner());
		assertFalse("advance payment info must never be a saved payment method", info.isSaved());

		assertSame(advanceMode, cart.getPaymentMode());
		assertEquals(Boolean.FALSE, cart.getCalculated());

		final ArgumentCaptor<CommerceCheckoutParameter> parameter = ArgumentCaptor.forClass(CommerceCheckoutParameter.class);
		verify(commerceCheckoutService).setPaymentInfo(parameter.capture());
		assertSame(cart, parameter.getValue().getCart());
		assertSame(info, parameter.getValue().getPaymentInfo());
	}

	@Test
	public void shouldUseACopyOfTheDeliveryAddressAsBillingAddress()
	{
		facade.setAdvancePayment();

		final AdvancePaymentInfoModel info = createdPaymentInfos.get(0);
		verify(addressService).cloneAddressForOwner(deliveryAddress, info);
		assertEquals(1, clonedAddresses.size());
		final AddressModel billingAddress = clonedAddresses.get(0);
		assertSame(billingAddress, info.getBillingAddress());
		assertNotSameInstance("billing address must be a copy, not the delivery address itself", deliveryAddress,
				info.getBillingAddress());
		assertEquals(Boolean.TRUE, billingAddress.getBillingAddress());
		assertEquals(Boolean.FALSE, billingAddress.getShippingAddress());
		assertEquals(Boolean.FALSE, billingAddress.getVisibleInAddressBook());
	}

	@Test
	public void shouldFillTheBillingEmailFromTheCustomerWhenTheDeliveryAddressHasNone()
	{
		deliveryAddress.setEmail(null);

		assertTrue(facade.setAdvancePayment());

		// the guest order confirmation reads the email from paymentInfo.billingAddress
		assertEquals("shopper@example.com", createdPaymentInfos.get(0).getBillingAddress().getEmail());
		assertNull("the delivery address itself must not be changed", deliveryAddress.getEmail());
	}

	@Test
	public void shouldKeepTheEmailOfTheCopiedDeliveryAddress()
	{
		deliveryAddress.setEmail("delivery@example.com");

		assertTrue(facade.setAdvancePayment());

		assertEquals("delivery@example.com", createdPaymentInfos.get(0).getBillingAddress().getEmail());
		verify(customerEmailResolutionService, never()).getEmailForCustomer(any());
	}

	@Test
	public void shouldPersistTheNewPaymentInfoAndItsBillingAddress()
	{
		facade.setAdvancePayment();

		final List<Object> saved = argumentsOf(modelService, "save", "saveAll");
		assertContainsInstance(saved, createdPaymentInfos.get(0));
		assertContainsInstance(saved, clonedAddresses.get(0));
	}

	@Test
	public void shouldReturnTheResultOfSettingThePaymentInfo()
	{
		given(Boolean.valueOf(commerceCheckoutService.setPaymentInfo(any(CommerceCheckoutParameter.class))))
				.willReturn(Boolean.FALSE);

		assertFalse(facade.setAdvancePayment());
	}

	@Test
	public void shouldKeepThePreviousAdvancePaymentInfoWhenSettingThePaymentInfoFails()
	{
		final AdvancePaymentInfoModel previousInfo = givenAdvancePaymentOnTheCart();
		given(Boolean.valueOf(commerceCheckoutService.setPaymentInfo(any(CommerceCheckoutParameter.class))))
				.willReturn(Boolean.FALSE);

		assertFalse(facade.setAdvancePayment());

		// the cart still references it - removing it would leave the cart pointing at a deleted item
		final List<Object> removed = argumentsOf(modelService, "remove", "removeAll");
		assertNotContainsInstance(removed, previousInfo);
		assertNotContainsInstance(removed, previousInfo.getBillingAddress());
	}

	@Test
	public void shouldGiveEachAdvancePaymentInfoAUniqueCode()
	{
		facade.setAdvancePayment();
		facade.setAdvancePayment();

		assertEquals(2, createdPaymentInfos.size());
		assertNotNull(createdPaymentInfos.get(0).getCode());
		assertNotEquals(createdPaymentInfos.get(0).getCode(), createdPaymentInfos.get(1).getCode());
	}

	@Test
	public void shouldRemoveAPreviousAdvancePaymentInfoTogetherWithItsBillingAddress()
	{
		final AddressModel previousBillingAddress = new AddressModel();
		final AdvancePaymentInfoModel previousInfo = new AdvancePaymentInfoModel();
		previousInfo.setCode("previous-advance");
		previousInfo.setBillingAddress(previousBillingAddress);
		cart.setPaymentInfo(previousInfo);
		cart.setPaymentMode(advanceMode);

		assertTrue(facade.setAdvancePayment());

		final List<Object> removed = argumentsOf(modelService, "remove", "removeAll");
		assertContainsInstance(removed, previousInfo);
		assertContainsInstance(removed, previousBillingAddress);
		assertNotContainsInstance(removed, createdPaymentInfos.get(0));
	}

	@Test
	public void shouldNotRemoveAPreviousCreditCardPaymentInfo()
	{
		final CreditCardPaymentInfoModel previousCard = new CreditCardPaymentInfoModel();
		previousCard.setCode("customer-card");
		previousCard.setBillingAddress(new AddressModel());
		cart.setPaymentInfo(previousCard);

		assertTrue(facade.setAdvancePayment());

		final List<Object> removed = argumentsOf(modelService, "remove", "removeAll");
		assertNotContainsInstance(removed, previousCard);
		assertNotContainsInstance(removed, previousCard.getBillingAddress());
	}

	@Test
	public void shouldRejectAdvancePaymentWhenThePaymentModeIsInactive()
	{
		advanceMode.setActive(Boolean.FALSE);
		final CreditCardPaymentInfoModel previousCard = new CreditCardPaymentInfoModel();
		cart.setPaymentInfo(previousCard);

		assertFalse(facade.setAdvancePayment());

		assertCartUnchanged(previousCard, null);
	}

	@Test
	public void shouldRejectAdvancePaymentWhenThePaymentModeIsMissing()
	{
		given(paymentModeService.getPaymentModeForCode(AdvancePaymentCheckoutFacade.ADVANCE_PAYMENT_MODE_CODE))
				.willThrow(new UnknownIdentifierException("no payment mode advance"));

		assertFalse(facade.setAdvancePayment());

		assertCartUnchanged(null, null);
	}

	@Test
	public void shouldRejectAdvancePaymentWithoutASessionCart()
	{
		lenient().when(Boolean.valueOf(cartFacade.hasSessionCart())).thenReturn(Boolean.FALSE);
		lenient().when(Boolean.valueOf(cartService.hasSessionCart())).thenReturn(Boolean.FALSE);

		assertFalse(facade.setAdvancePayment());

		assertNothingPersisted();
	}

	@Test
	public void shouldRejectAdvancePaymentWhenTheCheckoutUserIsNotTheCartUser()
	{
		final CustomerModel otherCustomer = new CustomerModel();
		otherCustomer.setUid("someone.else@example.com");
		lenient().when(checkoutCustomerStrategy.getCurrentUserForCheckout()).thenReturn(otherCustomer);

		assertFalse(facade.setAdvancePayment());

		assertCartUnchanged(null, null);
	}

	@Test
	public void shouldRejectAdvancePaymentWhenTheCartHasNoDeliveryAddress()
	{
		cart.setDeliveryAddress(null);

		assertFalse(facade.setAdvancePayment());

		assertCartUnchanged(null, null);
	}

	// --- isAdvancePaymentSelected ---

	@Test
	public void shouldReportAdvancePaymentAsSelectedForAnAdvancePaymentInfoWithTheAdvancePaymentMode()
	{
		givenAdvancePaymentOnTheCart();

		assertTrue(facade.isAdvancePaymentSelected());
	}

	@Test
	public void shouldNotReportAdvancePaymentAsSelectedForAnAdvancePaymentInfoWithoutPaymentMode()
	{
		cart.setPaymentInfo(new AdvancePaymentInfoModel());

		assertFalse(facade.isAdvancePaymentSelected());
	}

	@Test
	public void shouldNotReportAdvancePaymentAsSelectedForAnAdvancePaymentInfoWithAnotherPaymentMode()
	{
		cart.setPaymentInfo(new AdvancePaymentInfoModel());
		cart.setPaymentMode(otherPaymentMode());

		assertFalse(facade.isAdvancePaymentSelected());
	}

	@Test
	public void shouldNotReportAdvancePaymentAsSelectedForACard()
	{
		cart.setPaymentInfo(new CreditCardPaymentInfoModel());

		assertFalse(facade.isAdvancePaymentSelected());
	}

	@Test
	public void shouldNotReportAdvancePaymentAsSelectedWithoutPaymentInfo()
	{
		cart.setPaymentMode(advanceMode);

		assertFalse(facade.isAdvancePaymentSelected());
	}

	@Test
	public void shouldNotReportAdvancePaymentAsSelectedWithoutASessionCart()
	{
		lenient().when(Boolean.valueOf(cartFacade.hasSessionCart())).thenReturn(Boolean.FALSE);
		lenient().when(Boolean.valueOf(cartService.hasSessionCart())).thenReturn(Boolean.FALSE);
		lenient().when(cartService.getSessionCart()).thenReturn(null);

		assertFalse(facade.isAdvancePaymentSelected());
	}

	// --- hasNoPaymentInfo (AC 2, 3): CartData.paymentInfo stays empty for advance payment ---

	@Test
	public void shouldNotReportMissingPaymentInfoForAnAdvancePaymentCart()
	{
		givenAdvancePaymentOnTheCart();
		givenACheckoutCartWithoutDeliveryAddress();

		assertFalse(facade.hasNoPaymentInfo());
		verify(creditCardPaymentInfoConverter, never()).convert(any());
	}

	@Test
	public void shouldLeaveTheCartDataPaymentInfoEmptyForAdvancePayment()
	{
		givenAdvancePaymentOnTheCart();
		givenACheckoutCartWithoutDeliveryAddress();

		assertNull(facade.getCheckoutCart().getPaymentInfo());
	}

	@Test
	public void shouldNotReportMissingPaymentInfoForACard()
	{
		final CreditCardPaymentInfoModel card = new CreditCardPaymentInfoModel();
		cart.setPaymentInfo(card);
		givenACheckoutCartWithoutDeliveryAddress();
		given(creditCardPaymentInfoConverter.convert(card)).willReturn(new CCPaymentInfoData());

		assertFalse(facade.hasNoPaymentInfo());
	}

	@Test
	public void shouldReportMissingPaymentInfoWhenTheCartHasNone()
	{
		givenACheckoutCartWithoutDeliveryAddress();

		assertTrue(facade.hasNoPaymentInfo());
	}

	@Test
	public void shouldReportMissingPaymentInfoForAnAdvancePaymentInfoWithoutTheAdvancePaymentMode()
	{
		cart.setPaymentInfo(new AdvancePaymentInfoModel());
		givenACheckoutCartWithoutDeliveryAddress();

		assertTrue(facade.hasNoPaymentInfo());
	}

	@Test
	public void shouldReportMissingPaymentInfoForAnAdvancePaymentInfoWithAnotherPaymentMode()
	{
		cart.setPaymentInfo(new AdvancePaymentInfoModel());
		cart.setPaymentMode(otherPaymentMode());
		givenACheckoutCartWithoutDeliveryAddress();

		assertTrue(facade.hasNoPaymentInfo());
	}

	// --- authorizePayment (AC 4, 6, 9) ---

	@Test
	public void shouldAuthorizeAdvancePaymentWithoutAPaymentTransaction()
	{
		cart.setPaymentInfo(new AdvancePaymentInfoModel());
		cart.setPaymentMode(advanceMode);

		assertTrue(facade.authorizePayment(null));

		assertNoPaymentAuthorization();
	}

	@Test
	public void shouldRefuseAdvancePaymentAuthorizationWhenThePaymentModeWasSwitchedOff()
	{
		cart.setPaymentInfo(new AdvancePaymentInfoModel());
		cart.setPaymentMode(advanceMode);
		advanceMode.setActive(Boolean.FALSE);

		assertFalse(facade.authorizePayment(null));

		assertNoPaymentAuthorization();
	}

	@Test
	public void shouldRefuseAdvancePaymentAuthorizationWhenTheCheckoutUserIsNotTheCartUser()
	{
		cart.setPaymentInfo(new AdvancePaymentInfoModel());
		cart.setPaymentMode(advanceMode);
		lenient().when(checkoutCustomerStrategy.getCurrentUserForCheckout()).thenReturn(new CustomerModel());

		assertFalse(facade.authorizePayment(null));

		assertNoPaymentAuthorization();
	}

	@Test
	public void shouldRefuseAnAdvancePaymentInfoWithoutTheAdvancePaymentMode()
	{
		cart.setPaymentInfo(new AdvancePaymentInfoModel());

		// must neither authorize nor reach the platform's card cast
		assertFalse(facade.authorizePayment(null));

		assertNoPaymentAuthorization();
	}

	@Test
	public void shouldStillAuthorizeACardThroughThePaymentService()
	{
		final CreditCardPaymentInfoModel card = new CreditCardPaymentInfoModel();
		card.setSubscriptionId("subscription-1");
		cart.setPaymentInfo(card);
		final PaymentTransactionEntryModel entry = new PaymentTransactionEntryModel();
		entry.setTransactionStatus(TransactionStatus.ACCEPTED.name());
		lenient().when(commerceCheckoutService.getPaymentProvider()).thenReturn("Mockup");
		given(commerceCheckoutService.authorizePayment(any(CommerceCheckoutParameter.class))).willReturn(entry);

		assertTrue(facade.authorizePayment("123"));

		final ArgumentCaptor<CommerceCheckoutParameter> parameter = ArgumentCaptor.forClass(CommerceCheckoutParameter.class);
		verify(commerceCheckoutService).authorizePayment(parameter.capture());
		assertSame(cart, parameter.getValue().getCart());
		assertEquals("123", parameter.getValue().getSecurityCode());
	}

	@Test
	public void shouldStillRefuseARejectedCardAuthorization()
	{
		final CreditCardPaymentInfoModel card = new CreditCardPaymentInfoModel();
		card.setSubscriptionId("subscription-1");
		cart.setPaymentInfo(card);
		final PaymentTransactionEntryModel entry = new PaymentTransactionEntryModel();
		entry.setTransactionStatus(TransactionStatus.REJECTED.name());
		lenient().when(commerceCheckoutService.getPaymentProvider()).thenReturn("Mockup");
		given(commerceCheckoutService.authorizePayment(any(CommerceCheckoutParameter.class))).willReturn(entry);

		assertFalse(facade.authorizePayment("123"));
	}

	// --- switching back to a card (AC 7) ---

	@Test
	public void shouldClearAdvancePaymentWhenASavedCardIsChosen()
	{
		final AdvancePaymentInfoModel previousInfo = givenAdvancePaymentOnTheCart();
		final CreditCardPaymentInfoModel card = new CreditCardPaymentInfoModel();
		card.setSaved(true);
		given(customerAccountService.getCreditCardPaymentInfoForCode(customer, CARD_PAYMENT_INFO_ID)).willReturn(card);

		assertTrue(facade.setPaymentDetails(CARD_PAYMENT_INFO_ID));

		assertAdvancePaymentCleared(previousInfo);
	}

	@Test
	public void shouldKeepAdvancePaymentWhenTheChosenCardIsNotFound()
	{
		final AdvancePaymentInfoModel previousInfo = givenAdvancePaymentOnTheCart();
		given(customerAccountService.getCreditCardPaymentInfoForCode(customer, CARD_PAYMENT_INFO_ID)).willReturn(null);

		assertFalse(facade.setPaymentDetails(CARD_PAYMENT_INFO_ID));

		assertCartUnchanged(previousInfo, advanceMode);
	}

	@Test
	public void shouldNotTouchThePaymentModeWhenSwitchingBetweenCards()
	{
		final CreditCardPaymentInfoModel previousCard = new CreditCardPaymentInfoModel();
		cart.setPaymentInfo(previousCard);
		final CreditCardPaymentInfoModel card = new CreditCardPaymentInfoModel();
		given(customerAccountService.getCreditCardPaymentInfoForCode(customer, CARD_PAYMENT_INFO_ID)).willReturn(card);

		assertTrue(facade.setPaymentDetails(CARD_PAYMENT_INFO_ID));

		assertNull(cart.getPaymentMode());
		assertTrue("no payment info may be removed when switching between cards",
				argumentsOf(modelService, "remove", "removeAll").isEmpty());
	}

	@Test
	public void shouldClearAdvancePaymentWhenTheDefaultCardIsApplied()
	{
		final AdvancePaymentInfoModel previousInfo = givenAdvancePaymentOnTheCart();
		final CreditCardPaymentInfoModel defaultCard = new CreditCardPaymentInfoModel();
		defaultCard.setSaved(true);
		customer.setDefaultPaymentInfo(defaultCard);

		assertTrue(facade.setDefaultPaymentInfoForCheckout());

		assertAdvancePaymentCleared(previousInfo);
	}

	@Test
	public void shouldKeepAdvancePaymentWhenThereIsNoDefaultCard()
	{
		final AdvancePaymentInfoModel previousInfo = givenAdvancePaymentOnTheCart();
		customer.setDefaultPaymentInfo(null);

		assertFalse(facade.setDefaultPaymentInfoForCheckout());

		assertCartUnchanged(previousInfo, advanceMode);
	}

	// --- helpers ---

	/**
	 * The inherited getDeliveryAddress() resolves the delivery address by PK, which a detached model
	 * does not have - the payment info is what these tests are about.
	 */
	private void givenACheckoutCartWithoutDeliveryAddress()
	{
		cart.setDeliveryAddress(null);
		given(cartFacade.getSessionCart()).willReturn(new CartData());
	}

	private AdvancePaymentInfoModel givenAdvancePaymentOnTheCart()
	{
		final AdvancePaymentInfoModel info = new AdvancePaymentInfoModel();
		info.setCode("advance-on-cart");
		info.setBillingAddress(new AddressModel());
		cart.setPaymentInfo(info);
		cart.setPaymentMode(advanceMode);
		return info;
	}

	private static PaymentModeModel otherPaymentMode()
	{
		final PaymentModeModel invoice = new PaymentModeModel();
		invoice.setCode("invoice");
		invoice.setActive(Boolean.TRUE);
		return invoice;
	}

	private void assertAdvancePaymentCleared(final AdvancePaymentInfoModel previousInfo)
	{
		assertNull("card payments carry no payment mode", cart.getPaymentMode());
		assertEquals(Boolean.FALSE, cart.getCalculated());
		assertContainsInstance(argumentsOf(modelService, "save", "saveAll"), cart);

		final ArgumentCaptor<CommerceCheckoutParameter> parameter = ArgumentCaptor.forClass(CommerceCheckoutParameter.class);
		verify(commerceCheckoutService).calculateCart(parameter.capture());
		assertSame(cart, parameter.getValue().getCart());

		final List<Object> removed = argumentsOf(modelService, "remove", "removeAll");
		assertContainsInstance(removed, previousInfo);
		assertContainsInstance(removed, previousInfo.getBillingAddress());
	}

	private void assertCartUnchanged(final PaymentInfoModel expectedPaymentInfo, final PaymentModeModel expectedPaymentMode)
	{
		assertSame(expectedPaymentInfo, cart.getPaymentInfo());
		assertSame(expectedPaymentMode, cart.getPaymentMode());
		assertEquals(Boolean.TRUE, cart.getCalculated());
		verify(commerceCheckoutService, never()).calculateCart(any(CommerceCheckoutParameter.class));
		assertNothingPersisted();
	}

	private void assertNothingPersisted()
	{
		assertTrue("no payment info may be created", createdPaymentInfos.isEmpty());
		assertTrue("nothing may be saved", argumentsOf(modelService, "save", "saveAll").isEmpty());
		assertTrue("nothing may be removed", argumentsOf(modelService, "remove", "removeAll").isEmpty());
		assertTrue("the payment info may not be replaced", argumentsOf(commerceCheckoutService, "setPaymentInfo").isEmpty());
	}

	private void assertNoPaymentAuthorization()
	{
		assertFalse("advance payment must never reach the payment service",
				invoked(commerceCheckoutService, "authorizePayment"));
	}

	private static boolean invoked(final Object mock, final String methodName)
	{
		return Mockito.mockingDetails(mock).getInvocations().stream()
				.anyMatch(invocation -> methodName.equals(invocation.getMethod().getName()));
	}

	/**
	 * All arguments passed to the named methods of a mock, with collections and arrays (varargs)
	 * flattened, so assertions hold regardless of which save/remove overload is used.
	 */
	private static List<Object> argumentsOf(final Object mock, final String... methodNames)
	{
		final Set<String> names = Set.of(methodNames);
		final List<Object> result = new ArrayList<>();
		for (final Invocation invocation : Mockito.mockingDetails(mock).getInvocations())
		{
			if (names.contains(invocation.getMethod().getName()))
			{
				for (final Object argument : invocation.getArguments())
				{
					flatten(argument, result);
				}
			}
		}
		return result;
	}

	private static void flatten(final Object argument, final List<Object> result)
	{
		if (argument instanceof Collection)
		{
			((Collection<?>) argument).forEach(element -> flatten(element, result));
		}
		else if (argument instanceof Object[])
		{
			Arrays.stream((Object[]) argument).forEach(element -> flatten(element, result));
		}
		else if (argument != null)
		{
			result.add(argument);
		}
	}

	private static void assertContainsInstance(final List<Object> objects, final Object expected)
	{
		assertTrue("expected " + expected + " in " + objects, objects.stream().anyMatch(object -> object == expected));
	}

	private static void assertNotContainsInstance(final List<Object> objects, final Object unexpected)
	{
		assertFalse("did not expect " + unexpected + " in " + objects,
				objects.stream().anyMatch(object -> object == unexpected));
	}

	private static void assertNotSameInstance(final String message, final Object unexpected, final Object actual)
	{
		assertFalse(message, unexpected == actual);
	}
}
