package com.custom.orderhistory.populator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commercefacades.order.data.OrderHistoryData;
import de.hybris.platform.commercefacades.product.PriceDataFactory;
import de.hybris.platform.commercefacades.product.data.PriceData;
import de.hybris.platform.commercefacades.product.data.PriceDataType;
import de.hybris.platform.core.enums.OrderStatus;
import de.hybris.platform.servicelayer.dto.converter.ConversionException;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.custom.orderhistory.dto.CustomOrderDto;


/**
 * Covers the date parsing and the mapping onto {@link OrderHistoryData} (NET-8938 section 6,
 * acceptance criterion 8).
 */
@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class CustomOrderHistoryPopulatorTest
{
	@Mock
	private PriceDataFactory priceDataFactory;
	@Mock
	private PriceData priceData;

	private CustomOrderHistoryPopulator populator;

	@Before
	public void setUp()
	{
		populator = new CustomOrderHistoryPopulator();
		populator.setPriceDataFactory(priceDataFactory);
		populator.setCustomOrderStatusMapper(new CustomOrderStatusMapper());
	}

	private CustomOrderDto order(final String number, final String date, final String status, final String currency,
			final String total)
	{
		final CustomOrderDto dto = new CustomOrderDto();
		dto.setOrderNumber(number);
		dto.setOrderDate(date);
		dto.setStatus(status);
		dto.setCurrency(currency);
		dto.setTotalPrice(total == null ? null : new BigDecimal(total));
		return dto;
	}

	@Test
	public void shouldMapAllFields()
	{
		given(priceDataFactory.create(eq(PriceDataType.BUY), any(BigDecimal.class), eq("EUR"))).willReturn(priceData);
		final CustomOrderDto source = order("SO-100001", "2026-01-15", "COMPLETED", "EUR", "169.56");
		final OrderHistoryData target = new OrderHistoryData();

		populator.populate(source, target);

		assertEquals("SO-100001", target.getCode());
		assertEquals(OrderStatus.COMPLETED, target.getStatus());
		assertEquals("completed", target.getStatusDisplay());
		assertSame(priceData, target.getTotal());
		// NET-8938 section 6: guid is not applicable for an external order.
		assertNull(target.getGuid());
	}

	@Test
	public void shouldParseTheOrderDateAsMidnightLocalTime() throws Exception
	{
		given(priceDataFactory.create(eq(PriceDataType.BUY), any(BigDecimal.class), eq("EUR"))).willReturn(priceData);
		final OrderHistoryData target = new OrderHistoryData();

		populator.populate(order("SO-100001", "2026-01-15", "COMPLETED", "EUR", "169.56"), target);

		assertEquals(new SimpleDateFormat("yyyy-MM-dd").parse("2026-01-15"), target.getPlaced());

		final Calendar calendar = Calendar.getInstance();
		calendar.setTime(target.getPlaced());
		assertEquals(2026, calendar.get(Calendar.YEAR));
		assertEquals(Calendar.JANUARY, calendar.get(Calendar.MONTH));
		assertEquals(15, calendar.get(Calendar.DAY_OF_MONTH));
		assertEquals(0, calendar.get(Calendar.HOUR_OF_DAY));
		assertEquals(0, calendar.get(Calendar.MINUTE));
	}

	@Test
	public void shouldLeaveThePlacedDateNullWhenTheOrderDateIsMissing()
	{
		given(priceDataFactory.create(eq(PriceDataType.BUY), any(BigDecimal.class), eq("EUR"))).willReturn(priceData);
		final OrderHistoryData target = new OrderHistoryData();

		populator.populate(order("SO-100001", null, "COMPLETED", "EUR", "169.56"), target);

		assertNull(target.getPlaced());
	}

	@Test(expected = ConversionException.class)
	public void shouldRejectAnUnparseableOrderDate()
	{
		populator.populate(order("SO-100001", "15.01.2026", "COMPLETED", "EUR", "169.56"), new OrderHistoryData());
	}

	@Test(expected = ConversionException.class)
	public void shouldRejectAnInvalidCalendarDate()
	{
		populator.populate(order("SO-100001", "2026-02-31", "COMPLETED", "EUR", "169.56"), new OrderHistoryData());
	}

	/**
	 * NET-8938 section 4: totalPrice is handed to the PriceDataFactory exactly as delivered, with
	 * the currency of that order - never recalculated, never the session currency.
	 */
	@Test
	public void shouldPassTotalPriceThroughUnchangedWithThePerOrderCurrency()
	{
		given(priceDataFactory.create(eq(PriceDataType.BUY), eq(new BigDecimal("1100.79")), eq("CHF"))).willReturn(priceData);
		final OrderHistoryData target = new OrderHistoryData();

		populator.populate(order("SO-100009", "2026-04-13", "IN_PROCESS", "CHF", "1100.79"), target);

		verify(priceDataFactory).create(PriceDataType.BUY, new BigDecimal("1100.79"), "CHF");
		assertSame(priceData, target.getTotal());
	}

	@Test
	public void shouldLeaveTheTotalNullWhenTotalPriceOrCurrencyIsMissing()
	{
		final OrderHistoryData noTotal = new OrderHistoryData();
		populator.populate(order("SO-1", "2026-01-15", "COMPLETED", "EUR", null), noTotal);
		assertNull(noTotal.getTotal());

		final OrderHistoryData noCurrency = new OrderHistoryData();
		populator.populate(order("SO-2", "2026-01-15", "COMPLETED", null, "10.00"), noCurrency);
		assertNull(noCurrency.getTotal());

		verifyNoInteractions(priceDataFactory);
	}

	@Test
	public void shouldStillMapAnOrderWithAnUnknownStatus()
	{
		given(priceDataFactory.create(eq(PriceDataType.BUY), any(BigDecimal.class), eq("EUR"))).willReturn(priceData);
		final OrderHistoryData target = new OrderHistoryData();

		populator.populate(order("SO-100011", "2026-05-01", "ON_HOLD", "EUR", "10.00"), target);

		assertEquals("SO-100011", target.getCode());
		assertNull(target.getStatus());
		assertEquals(CustomOrderStatusMapper.DISPLAY_UNKNOWN, target.getStatusDisplay());
	}

	@Test(expected = IllegalArgumentException.class)
	public void shouldRejectANullSource()
	{
		populator.populate(null, new OrderHistoryData());
	}
}
