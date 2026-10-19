/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.facades.populators;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commercefacades.order.data.OrderData;
import de.hybris.platform.commercefacades.order.data.PaymentModeData;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.order.payment.CreditCardPaymentInfoModel;
import de.hybris.platform.core.model.order.payment.PaymentModeModel;
import de.hybris.platform.servicelayer.dto.converter.Converter;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;


/**
 * Unit test for {@link PaymentModeOrderPopulator} (NET-8942 section 5.3, AC 3, 5): advance payment
 * has no {@code CCPaymentInfoData}, so the summary, the order confirmation and the order history
 * show it from the cart's/order's payment mode. Card payments carry no payment mode.
 */
@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class PaymentModeOrderPopulatorTest
{
	@Mock
	private Converter<PaymentModeModel, PaymentModeData> paymentModeConverter;

	private PaymentModeOrderPopulator populator;

	@Before
	public void setUp()
	{
		populator = new PaymentModeOrderPopulator();
		populator.setPaymentModeConverter(paymentModeConverter);
	}

	@Test
	public void shouldMapThePaymentModeOfAnOrder()
	{
		final PaymentModeModel advanceMode = new PaymentModeModel();
		advanceMode.setCode("advance");
		final OrderModel order = new OrderModel();
		order.setPaymentMode(advanceMode);
		final PaymentModeData advanceModeData = new PaymentModeData();
		given(paymentModeConverter.convert(advanceMode)).willReturn(advanceModeData);
		final OrderData target = new OrderData();

		populator.populate(order, target);

		assertSame(advanceModeData, target.getPaymentMode());
	}

	@Test
	public void shouldMapThePaymentModeOfACart()
	{
		final PaymentModeModel advanceMode = new PaymentModeModel();
		final CartModel cart = new CartModel();
		cart.setPaymentMode(advanceMode);
		final PaymentModeData advanceModeData = new PaymentModeData();
		given(paymentModeConverter.convert(advanceMode)).willReturn(advanceModeData);
		final CartData target = new CartData();

		populator.populate(cart, target);

		assertSame(advanceModeData, target.getPaymentMode());
	}

	@Test
	public void shouldLeaveThePaymentModeEmptyForACardOrder()
	{
		final OrderModel order = new OrderModel();
		order.setPaymentInfo(new CreditCardPaymentInfoModel());
		final OrderData target = new OrderData();

		populator.populate(order, target);

		assertNull(target.getPaymentMode());
		verify(paymentModeConverter, never()).convert(any());
	}

	@Test
	public void shouldNotOverwriteAnAlreadyPopulatedPaymentModeWhenTheOrderHasNone()
	{
		final PaymentModeData existing = new PaymentModeData();
		final OrderData target = new OrderData();
		target.setPaymentMode(existing);

		populator.populate(new OrderModel(), target);

		assertSame(existing, target.getPaymentMode());
		verify(paymentModeConverter, never()).convert(any());
	}
}
