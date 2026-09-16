package com.custom.orderhistory.populator;

import de.hybris.platform.commercefacades.order.data.OrderData;
import de.hybris.platform.commercefacades.order.data.OrderEntryData;
import de.hybris.platform.commercefacades.product.PriceDataFactory;
import de.hybris.platform.commercefacades.product.data.PriceData;
import de.hybris.platform.commercefacades.product.data.PriceDataType;
import de.hybris.platform.commercefacades.product.data.ProductData;
import de.hybris.platform.converters.Populator;
import de.hybris.platform.servicelayer.dto.converter.ConversionException;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import com.custom.orderhistory.dto.CustomOrderDto;
import com.custom.orderhistory.dto.CustomOrderPositionDto;


/**
 * Maps an external {@link CustomOrderDto} onto the platform {@link OrderData} for the order detail
 * page (NET-8938 &sect;6.2).
 * <p>
 * Reuses {@link CustomOrderStatusMapper} - the status mapping is defined once, in
 * {@link CustomOrderHistoryPopulator} and here alike.
 * <p>
 * The payload carries no delivery address, payment information, delivery mode or total breakdown
 * (NET-8938 &sect;4, &sect;8 q8); those fields are left unset. {@code consignments} is set to an
 * empty list, never <code>null</code>, because {@code accountOrderDetailItems.jsp} iterates it
 * directly. {@code productReference} is not resolved against the catalog (NET-8938 &sect;8 q6) - a
 * lookup that throws for one bad line would break the whole page.
 */
public class CustomOrderDetailPopulator implements Populator<CustomOrderDto, OrderData>
{
	private static final Logger LOG = LoggerFactory.getLogger(CustomOrderDetailPopulator.class);

	protected static final String ORDER_DATE_PATTERN = "yyyy-MM-dd";

	private CustomOrderStatusMapper customOrderStatusMapper;
	private PriceDataFactory priceDataFactory;

	@Override
	public void populate(final CustomOrderDto source, final OrderData target) throws ConversionException
	{
		Assert.notNull(source, "Parameter source cannot be null.");
		Assert.notNull(target, "Parameter target cannot be null.");

		target.setCode(source.getOrderNumber());
		target.setCreated(parseOrderDate(source.getOrderDate(), source.getOrderNumber()));
		target.setStatus(getCustomOrderStatusMapper().mapStatus(source.getStatus()));
		target.setStatusDisplay(getCustomOrderStatusMapper().mapStatusDisplay(source.getStatus()));

		/*
		 * accountOrderDetailsOverview.tag reads totalPriceWithTax, orderTotalsItem.tag reads
		 * totalPrice (order.net defaults to false) - both must carry the same value, taken as
		 * delivered, so the total shown matches the list and the payload exactly (NET-8938 AC 12).
		 */
		final PriceData total = createTotal(source);
		target.setTotalPrice(total);
		target.setTotalPriceWithTax(total);

		final List<OrderEntryData> entries = mapEntries(source);
		target.setUnconsignedEntries(entries);
		target.setEntries(entries);
		target.setConsignments(Collections.emptyList());

		target.setGuestCustomer(false);
		// deliveryAddress, paymentInfo, deliveryMode, deliveryStatus, subTotal, deliveryCost,
		// totalTax: no source in the payload (NET-8938 section 6.2) - left unset.
	}

	/**
	 * Parses the ISO date <code>yyyy-MM-dd</code>. The resulting time component is 00:00 in the
	 * platform timezone. Identical parsing to {@link CustomOrderHistoryPopulator}.
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
	protected PriceData createTotal(final CustomOrderDto source)
	{
		if (source.getTotalPrice() == null || StringUtils.isBlank(source.getCurrency()))
		{
			LOG.warn("External order [{}] has no totalPrice or no currency - the order is shown without a total",
					source.getOrderNumber());
			return null;
		}
		return getPriceDataFactory().create(PriceDataType.BUY, source.getTotalPrice(), StringUtils.trim(source.getCurrency()));
	}

	protected List<OrderEntryData> mapEntries(final CustomOrderDto source)
	{
		final List<CustomOrderPositionDto> positions = source.getPositions();
		if (positions == null)
		{
			return new ArrayList<>();
		}
		final List<OrderEntryData> entries = new ArrayList<>(positions.size());
		for (final CustomOrderPositionDto position : positions)
		{
			if (position != null)
			{
				entries.add(mapEntry(position, source));
			}
		}
		return entries;
	}

	protected OrderEntryData mapEntry(final CustomOrderPositionDto position, final CustomOrderDto order)
	{
		final OrderEntryData entry = new OrderEntryData();
		// the payload is 1-based (NET-8938 section 4), the platform convention is 0-based
		entry.setEntryNumber(position.getPositionNumber() == null ? null : position.getPositionNumber() - 1);
		entry.setQuantity(position.getQuantity() == null ? null : position.getQuantity().longValue());

		final PriceData linePrice = createLinePrice(position, order);
		entry.setBasePrice(linePrice);
		entry.setTotalPrice(linePrice);

		entry.setProduct(createStubProduct(position.getProductReference()));
		return entry;
	}

	/**
	 * The payload carries one figure per position (NET-8938 &sect;6.2) - it is used for both
	 * <code>basePrice</code> and <code>totalPrice</code>.
	 */
	protected PriceData createLinePrice(final CustomOrderPositionDto position, final CustomOrderDto order)
	{
		if (position.getPrice() == null || StringUtils.isBlank(order.getCurrency()))
		{
			LOG.warn("Position [{}] of external order [{}] has no price or the order has no currency - the line is shown without a price",
					position.getPositionNumber(), order.getOrderNumber());
			return null;
		}
		return getPriceDataFactory().create(PriceDataType.BUY, position.getPrice(), StringUtils.trim(order.getCurrency()));
	}

	/**
	 * <code>productReference</code> is not guaranteed to exist as a Commerce product (NET-8938
	 * &sect;4); a catalog lookup that throws for a missing SKU would break the whole page for one
	 * bad line. A stub with only <code>code</code> and <code>name</code> set renders the identifier
	 * and falls back to the standard missing-image placeholder (NET-8938 &sect;6.2, &sect;8 q6).
	 */
	protected ProductData createStubProduct(final String productReference)
	{
		final ProductData product = new ProductData();
		product.setCode(productReference);
		product.setName(productReference);
		return product;
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
