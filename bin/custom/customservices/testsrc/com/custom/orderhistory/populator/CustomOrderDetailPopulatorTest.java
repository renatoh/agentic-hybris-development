package com.custom.orderhistory.populator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commercefacades.order.data.OrderData;
import de.hybris.platform.commercefacades.order.data.OrderEntryData;
import de.hybris.platform.commercefacades.product.PriceDataFactory;
import de.hybris.platform.commercefacades.product.data.PriceData;
import de.hybris.platform.commercefacades.product.data.PriceDataType;
import de.hybris.platform.core.enums.OrderStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.custom.orderhistory.dto.CustomOrderDto;
import com.custom.orderhistory.dto.CustomOrderPositionDto;


/**
 * Covers the mapping onto {@link OrderData} for the order detail page (NET-8938 &sect;6.2,
 * acceptance criterion 15): the position &rarr; {@link OrderEntryData} mapping including the
 * 1-based to 0-based {@code entryNumber} conversion, and that the status mapping is the same
 * {@link CustomOrderStatusMapper} as the order history conversion.
 */
@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class CustomOrderDetailPopulatorTest
{
	@Mock
	private PriceDataFactory priceDataFactory;
	@Mock
	private PriceData priceData;

	private CustomOrderDetailPopulator populator;

	@Before
	public void setUp()
	{
		populator = new CustomOrderDetailPopulator();
		populator.setPriceDataFactory(priceDataFactory);
		populator.setCustomOrderStatusMapper(new CustomOrderStatusMapper());
	}

	private CustomOrderPositionDto position(final int positionNumber, final String productReference, final int quantity,
			final String price)
	{
		final CustomOrderPositionDto dto = new CustomOrderPositionDto();
		dto.setPositionNumber(positionNumber);
		dto.setProductReference(productReference);
		dto.setQuantity(quantity);
		dto.setPrice(new BigDecimal(price));
		return dto;
	}

	private CustomOrderDto order(final String number, final String date, final String status, final String currency,
			final String total, final CustomOrderPositionDto... positions)
	{
		final CustomOrderDto dto = new CustomOrderDto();
		dto.setOrderNumber(number);
		dto.setOrderDate(date);
		dto.setStatus(status);
		dto.setCurrency(currency);
		dto.setTotalPrice(total == null ? null : new BigDecimal(total));
		final List<CustomOrderPositionDto> list = new ArrayList<>();
		for (final CustomOrderPositionDto p : positions)
		{
			list.add(p);
		}
		dto.setPositions(list);
		return dto;
	}

	@Test
	public void shouldMapHeaderFields()
	{
		given(priceDataFactory.create(eq(PriceDataType.BUY), any(BigDecimal.class), eq("EUR"))).willReturn(priceData);
		final CustomOrderDto source = order("SO-100001", "2026-01-15", "COMPLETED", "EUR", "169.56",
				position(1, "SKU-10001", 3, "56.52"));
		final OrderData target = new OrderData();

		populator.populate(source, target);

		assertEquals("SO-100001", target.getCode());
		assertEquals(OrderStatus.COMPLETED, target.getStatus());
		assertEquals("completed", target.getStatusDisplay());
		assertSame(priceData, target.getTotalPrice());
		// NET-8938 section 6.2: both fields are read by different JSPs and must carry the same value.
		assertSame(priceData, target.getTotalPriceWithTax());
		assertEquals(Boolean.FALSE, Boolean.valueOf(target.isGuestCustomer()));
		assertNotNull(target.getConsignments());
		assertTrue("consignments must be empty, not null (NET-8938 section 6.2)", target.getConsignments().isEmpty());
	}

	/**
	 * NET-8938 section 6.2: the mapping reuses {@link CustomOrderStatusMapper} - not a copy of it.
	 */
	@Test
	public void shouldReuseTheStatusMapperForAnUnknownStatus()
	{
		final CustomOrderDto source = order("SO-100011", "2026-05-01", "ON_HOLD", "EUR", "10.00",
				position(1, "SKU-1", 1, "10.00"));
		given(priceDataFactory.create(eq(PriceDataType.BUY), any(BigDecimal.class), eq("EUR"))).willReturn(priceData);
		final OrderData target = new OrderData();

		populator.populate(source, target);

		assertEquals(null, target.getStatus());
		assertEquals(CustomOrderStatusMapper.DISPLAY_UNKNOWN, target.getStatusDisplay());
	}

	/**
	 * NET-8938 acceptance criterion 13 / section 6.2: every position becomes a line item, the
	 * 1-based {@code positionNumber} converts to the platform's 0-based {@code entryNumber}, and
	 * {@code productReference} is shown as the product identifier without a catalog lookup.
	 */
	@Test
	public void shouldMapEveryPositionToAnOrderEntry()
	{
		given(priceDataFactory.create(eq(PriceDataType.BUY), any(BigDecimal.class), eq("EUR"))).willReturn(priceData);
		final CustomOrderDto source = order("SO-100002", "2026-01-26", "COMPLETED", "EUR", "148.20",
				position(1, "SKU-10002", 5, "26.51"), position(2, "SKU-30450", 1, "15.65"));
		final OrderData target = new OrderData();

		populator.populate(source, target);

		final List<OrderEntryData> entries = target.getUnconsignedEntries();
		assertEquals(2, entries.size());
		// entries must be populated too, for any tag that reads it (NET-8938 section 6.2)
		assertEquals(entries, target.getEntries());

		final OrderEntryData first = entries.get(0);
		assertEquals(Integer.valueOf(0), first.getEntryNumber());
		assertEquals(Long.valueOf(5L), first.getQuantity());
		assertSame(priceData, first.getBasePrice());
		assertSame(priceData, first.getTotalPrice());
		assertEquals("SKU-10002", first.getProduct().getCode());
		assertEquals("SKU-10002", first.getProduct().getName());

		final OrderEntryData second = entries.get(1);
		assertEquals(Integer.valueOf(1), second.getEntryNumber());
		assertEquals("SKU-30450", second.getProduct().getCode());
	}

	@Test
	public void shouldTolerateAnOrderWithoutPositions()
	{
		given(priceDataFactory.create(eq(PriceDataType.BUY), any(BigDecimal.class), eq("EUR"))).willReturn(priceData);
		final CustomOrderDto source = order("SO-1", "2026-01-15", "COMPLETED", "EUR", "10.00");
		final OrderData target = new OrderData();

		populator.populate(source, target);

		assertTrue(target.getUnconsignedEntries().isEmpty());
	}
}
