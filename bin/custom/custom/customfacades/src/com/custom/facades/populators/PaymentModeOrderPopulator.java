/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.facades.populators;

import de.hybris.platform.commercefacades.order.data.AbstractOrderData;
import de.hybris.platform.commercefacades.order.data.PaymentModeData;
import de.hybris.platform.converters.Populator;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.order.payment.PaymentModeModel;
import de.hybris.platform.servicelayer.dto.converter.Converter;

import org.springframework.beans.factory.annotation.Required;


/**
 * NET-8942: copies the cart's/order's payment mode (code, name, description) to
 * {@code AbstractOrderData.paymentMode}. Advance payment has no {@code CCPaymentInfoData} - the
 * platform only converts credit cards into {@code paymentInfo} - so the pages show it by the payment
 * mode's name. Card payments carry no payment mode; the field then stays empty.
 */
public class PaymentModeOrderPopulator implements Populator<AbstractOrderModel, AbstractOrderData>
{
	private Converter<PaymentModeModel, PaymentModeData> paymentModeConverter;

	@Override
	public void populate(final AbstractOrderModel source, final AbstractOrderData target)
	{
		if (source.getPaymentMode() != null)
		{
			target.setPaymentMode(getPaymentModeConverter().convert(source.getPaymentMode()));
		}
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
}
