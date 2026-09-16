package com.custom.orderhistory.populator;

import de.hybris.platform.commercefacades.order.data.OrderHistoryData;
import de.hybris.platform.commercefacades.product.PriceDataFactory;
import de.hybris.platform.commercefacades.product.data.PriceDataType;
import de.hybris.platform.converters.Populator;
import de.hybris.platform.servicelayer.dto.converter.ConversionException;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import com.custom.orderhistory.dto.CustomOrderDto;


/**
 * Maps an external {@link CustomOrderDto} onto the platform {@link OrderHistoryData}
 * (NET-8938 &sect;6).
 * <p>
 * <code>total</code> is built from <code>totalPrice</code> and the per-order <code>currency</code>
 * and is taken <em>as delivered</em> - it is never recalculated from the positions and the session
 * currency is not used. <code>guid</code> stays <code>null</code>.
 */
public class CustomOrderHistoryPopulator implements Populator<CustomOrderDto, OrderHistoryData>
{
	private static final Logger LOG = LoggerFactory.getLogger(CustomOrderHistoryPopulator.class);

	protected static final String ORDER_DATE_PATTERN = "yyyy-MM-dd";

	private CustomOrderStatusMapper customOrderStatusMapper;
	private PriceDataFactory priceDataFactory;

	@Override
	public void populate(final CustomOrderDto source, final OrderHistoryData target) throws ConversionException
	{
		Assert.notNull(source, "Parameter source cannot be null.");
		Assert.notNull(target, "Parameter target cannot be null.");

		target.setCode(source.getOrderNumber());
		target.setPlaced(parseOrderDate(source.getOrderDate(), source.getOrderNumber()));
		target.setStatus(getCustomOrderStatusMapper().mapStatus(source.getStatus()));
		target.setStatusDisplay(getCustomOrderStatusMapper().mapStatusDisplay(source.getStatus()));
		target.setTotal(createTotal(source));
		// guid: not applicable for an external order (NET-8938 section 6), left null
	}

	/**
	 * Parses the ISO date <code>yyyy-MM-dd</code>. The resulting time component is 00:00 in the
	 * platform timezone.
	 */
	protected Date parseOrderDate(final String orderDate, final String orderNumber)
	{
		if (StringUtils.isBlank(orderDate))
		{
			LOG.warn("External order [{}] has no orderDate - the order is shown without a date", orderNumber);
			return null;
		}
		final SimpleDateFormat format = new SimpleDateFormat(ORDER_DATE_PATTERN);
		format.setLenient(false);
		try
		{
			return format.parse(StringUtils.trim(orderDate));
		}
		catch (final ParseException e)
		{
			throw new ConversionException(
					"Could not parse orderDate [" + orderDate + "] of external order [" + orderNumber + "] as " + ORDER_DATE_PATTERN,
					e);
		}
	}

	/**
	 * Formats <code>totalPrice</code> with the currency delivered for that order. Taken as
	 * delivered - no recalculation from the positions (NET-8938 &sect;4).
	 */
	protected de.hybris.platform.commercefacades.product.data.PriceData createTotal(final CustomOrderDto source)
	{
		if (source.getTotalPrice() == null || StringUtils.isBlank(source.getCurrency()))
		{
			LOG.warn("External order [{}] has no totalPrice or no currency - the order is shown without a total",
					source.getOrderNumber());
			return null;
		}
		return getPriceDataFactory().create(PriceDataType.BUY, source.getTotalPrice(),
				StringUtils.trim(source.getCurrency()));
	}

	protected CustomOrderStatusMapper getCustomOrderStatusMapper()
	{
		return customOrderStatusMapper;
	}

	public void setCustomOrderStatusMapper(final CustomOrderStatusMapper customOrderStatusMapper)
	{
		this.customOrderStatusMapper = customOrderStatusMapper;
	}

	protected PriceDataFactory getPriceDataFactory()
	{
		return priceDataFactory;
	}

	public void setPriceDataFactory(final PriceDataFactory priceDataFactory)
	{
		this.priceDataFactory = priceDataFactory;
	}
}
