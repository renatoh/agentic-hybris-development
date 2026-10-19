/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.facades.payment.impl;

import de.hybris.platform.acceleratorfacades.order.impl.DefaultAcceleratorCheckoutFacade;
import de.hybris.platform.commercefacades.order.data.PaymentModeData;
import de.hybris.platform.commerceservices.customer.CustomerEmailResolutionService;
import de.hybris.platform.commerceservices.service.data.CommerceCheckoutParameter;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.order.payment.AdvancePaymentInfoModel;
import de.hybris.platform.core.model.order.payment.PaymentInfoModel;
import de.hybris.platform.core.model.order.payment.PaymentModeModel;
import de.hybris.platform.core.model.user.AddressModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.order.PaymentModeService;
import de.hybris.platform.servicelayer.dto.converter.Converter;
import de.hybris.platform.servicelayer.exceptions.AmbiguousIdentifierException;
import de.hybris.platform.servicelayer.exceptions.UnknownIdentifierException;
import de.hybris.platform.servicelayer.user.AddressService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Required;

import com.custom.facades.payment.AdvancePaymentCheckoutFacade;


/**
 * Takes over the {@code acceleratorCheckoutFacade} alias to add advance payment (NET-8942) to the
 * multi-step checkout.
 * <p>
 * Besides the new {@link AdvancePaymentCheckoutFacade} methods it overrides the platform methods that
 * assume a credit card:
 * <ul>
 * <li>{@link #hasNoPaymentInfo()} - the platform only converts a {@code CreditCardPaymentInfo} into
 * {@code CartData.paymentInfo}, so an advance-payment cart would count as having no payment info and
 * the step validators would send the shopper back. {@code CartData.paymentInfo} itself stays empty
 * for advance payment (as for any non-card payment info); pages use {@code CartData.paymentMode}.</li>
 * <li>{@link #authorizePayment(String)} - the platform casts the cart's payment info to
 * {@code CreditCardPaymentInfoModel}; advance payment needs no authorization and creates no payment
 * transaction. <b>Upgrade check:</b> compare with {@code DefaultCheckoutFacade.authorizePayment} on
 * every platform upgrade.</li>
 * <li>{@link #setPaymentDetails(String)} / {@link #setDefaultPaymentInfoForCheckout()} - choosing a
 * card after advance payment clears the payment mode and removes the then-orphaned
 * {@code AdvancePaymentInfo}.</li>
 * </ul>
 */
public class DefaultAdvancePaymentCheckoutFacade extends DefaultAcceleratorCheckoutFacade implements AdvancePaymentCheckoutFacade
{
	private static final Logger LOG = Logger.getLogger(DefaultAdvancePaymentCheckoutFacade.class);

	private PaymentModeService paymentModeService;
	private Converter<PaymentModeModel, PaymentModeData> paymentModeConverter;
	private AddressService addressService;
	private CustomerEmailResolutionService customerEmailResolutionService;

	@Override
	public Optional<PaymentModeData> getAdvancePaymentMode()
	{
		return findActiveAdvancePaymentMode().map(getPaymentModeConverter()::convert);
	}

	@Override
	public boolean isAdvancePaymentAvailable()
	{
		return findActiveAdvancePaymentMode().isPresent();
	}

	@Override
	public boolean setAdvancePayment()
	{
		final Optional<PaymentModeModel> paymentMode = findActiveAdvancePaymentMode();
		final CartModel cart = getCart();
		if (paymentMode.isEmpty() || cart == null || !checkIfCurrentUserIsTheCartUser() || cart.getDeliveryAddress() == null)
		{
			return false;
		}

		final PaymentInfoModel previousPaymentInfo = cart.getPaymentInfo();
		final AdvancePaymentInfoModel paymentInfo = createAdvancePaymentInfo(cart);

		cart.setPaymentMode(paymentMode.get());
		// the payment mode feeds into the payment cost - force setPaymentInfo's calculation to run
		cart.setCalculated(Boolean.FALSE);

		final CommerceCheckoutParameter parameter = createCommerceCheckoutParameter(cart, true);
		parameter.setPaymentInfo(paymentInfo);
		final boolean paymentInfoSet = getCommerceCheckoutService().setPaymentInfo(parameter);

		if (paymentInfoSet && previousPaymentInfo instanceof AdvancePaymentInfoModel)
		{
			removeAdvancePaymentInfo((AdvancePaymentInfoModel) previousPaymentInfo);
		}
		return paymentInfoSet;
	}

	/**
	 * @return {@code true} if the session cart has an {@code AdvancePaymentInfo} and the payment mode {@code advance}
	 */
	@Override
	public boolean isAdvancePaymentSelected()
	{
		final CartModel cart = getCart();
		return getAdvancePaymentInfo(cart) != null && cart.getPaymentMode() != null
				&& ADVANCE_PAYMENT_MODE_CODE.equals(cart.getPaymentMode().getCode());
	}

	@Override
	public boolean hasNoPaymentInfo()
	{
		return super.hasNoPaymentInfo() && !isAdvancePaymentSelected();
	}

	@Override
	public boolean authorizePayment(final String securityCode)
	{
		if (getAdvancePaymentInfo(getCart()) != null)
		{
			// Advance payment: the money is collected outside SAP Commerce - no authorization, no PaymentTransaction.
			// Never handed to super, which casts the payment info to CreditCardPaymentInfoModel.
			return isAdvancePaymentSelected() && checkIfCurrentUserIsTheCartUser() && isAdvancePaymentAvailable();
		}
		return super.authorizePayment(securityCode);
	}

	@Override
	public boolean setPaymentDetails(final String paymentInfoId)
	{
		final AdvancePaymentInfoModel previousAdvancePaymentInfo = getAdvancePaymentInfo(getCart());
		final boolean paymentInfoSet = super.setPaymentDetails(paymentInfoId);
		if (paymentInfoSet && previousAdvancePaymentInfo != null)
		{
			clearAdvancePayment(previousAdvancePaymentInfo);
		}
		return paymentInfoSet;
	}

	@Override
	public boolean setDefaultPaymentInfoForCheckout()
	{
		final AdvancePaymentInfoModel previousAdvancePaymentInfo = getAdvancePaymentInfo(getCart());
		final boolean paymentInfoSet = super.setDefaultPaymentInfoForCheckout();
		if (paymentInfoSet && previousAdvancePaymentInfo != null)
		{
			clearAdvancePayment(previousAdvancePaymentInfo);
		}
		return paymentInfoSet;
	}

	protected Optional<PaymentModeModel> findActiveAdvancePaymentMode()
	{
		final PaymentModeModel paymentMode;
		try
		{
			paymentMode = getPaymentModeService().getPaymentModeForCode(ADVANCE_PAYMENT_MODE_CODE);
		}
		catch (final UnknownIdentifierException | AmbiguousIdentifierException e)
		{
			LOG.debug("Payment mode [" + ADVANCE_PAYMENT_MODE_CODE + "] not found - advance payment is not offered", e);
			return Optional.empty();
		}
		return Boolean.TRUE.equals(paymentMode.getActive()) ? Optional.of(paymentMode) : Optional.empty();
	}

	protected AdvancePaymentInfoModel createAdvancePaymentInfo(final CartModel cart)
	{
		final AdvancePaymentInfoModel paymentInfo = getModelService().create(AdvancePaymentInfoModel.class);
		paymentInfo.setCode(cart.getUser().getUid() + "_" + UUID.randomUUID());
		paymentInfo.setUser(cart.getUser());
		paymentInfo.setOwner(cart);
		// never listed as a saved payment method in My Account
		paymentInfo.setSaved(false);

		final AddressModel billingAddress = getAddressService().cloneAddressForOwner(cart.getDeliveryAddress(), paymentInfo);
		billingAddress.setBillingAddress(Boolean.TRUE);
		billingAddress.setShippingAddress(Boolean.FALSE);
		billingAddress.setVisibleInAddressBook(Boolean.FALSE);
		// A card's billing address gets the email from the payment form; a delivery address usually has none. The
		// billing address becomes the order's payment address, so give it the customer's email (guests included).
		if (StringUtils.isBlank(billingAddress.getEmail()) && cart.getUser() instanceof CustomerModel)
		{
			billingAddress.setEmail(getCustomerEmailResolutionService().getEmailForCustomer((CustomerModel) cart.getUser()));
		}
		paymentInfo.setBillingAddress(billingAddress);

		getModelService().saveAll(billingAddress, paymentInfo);
		return paymentInfo;
	}

	/**
	 * After a card replaced advance payment on the cart: card payments carry no payment mode, and the
	 * old {@code AdvancePaymentInfo} is no longer referenced.
	 */
	protected void clearAdvancePayment(final AdvancePaymentInfoModel previousAdvancePaymentInfo)
	{
		final CartModel cart = getCart();
		if (cart != null)
		{
			cart.setPaymentMode(null);
			cart.setCalculated(Boolean.FALSE);
			getModelService().save(cart);
			getCommerceCheckoutService().calculateCart(createCommerceCheckoutParameter(cart, true));
		}
		removeAdvancePaymentInfo(previousAdvancePaymentInfo);
	}

	protected void removeAdvancePaymentInfo(final AdvancePaymentInfoModel paymentInfo)
	{
		final List<Object> toRemove = new ArrayList<>(2);
		if (paymentInfo.getBillingAddress() != null)
		{
			toRemove.add(paymentInfo.getBillingAddress());
		}
		toRemove.add(paymentInfo);
		getModelService().removeAll(toRemove);
	}

	protected AdvancePaymentInfoModel getAdvancePaymentInfo(final CartModel cart)
	{
		return cart != null && cart.getPaymentInfo() instanceof AdvancePaymentInfoModel
				? (AdvancePaymentInfoModel) cart.getPaymentInfo()
				: null;
	}

	protected PaymentModeService getPaymentModeService()
	{
		return paymentModeService;
	}

	@Required
	public void setPaymentModeService(final PaymentModeService paymentModeService)
	{
		this.paymentModeService = paymentModeService;
	}

	protected Converter<PaymentModeModel, PaymentModeData> getPaymentModeConverter()
	{
		return paymentModeConverter;
	}

	@Required
	public void setPaymentModeConverter(final Converter<PaymentModeModel, PaymentModeData> paymentModeConverter)
	{
		this.paymentModeConverter = paymentModeConverter;
	}

	protected AddressService getAddressService()
	{
		return addressService;
	}

	@Required
	public void setAddressService(final AddressService addressService)
	{
		this.addressService = addressService;
	}

	protected CustomerEmailResolutionService getCustomerEmailResolutionService()
	{
		return customerEmailResolutionService;
	}

	@Required
	public void setCustomerEmailResolutionService(final CustomerEmailResolutionService customerEmailResolutionService)
	{
		this.customerEmailResolutionService = customerEmailResolutionService;
	}
}
