package com.custom.orderhistory.populator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.core.enums.OrderStatus;

import org.junit.Before;
import org.junit.Test;


/**
 * Covers the complete status mapping table of NET-8938 section 6.1 including the unknown-status
 * case (acceptance criterion 8).
 */
@UnitTest
public class CustomOrderStatusMapperTest
{
	/**
	 * OrderStatus.PROCESSING is not a generated constant in this installation - it is declared in
	 * acceleratorservices, which is not part of the active extension set. OrderStatus is a dynamic
	 * hybris enum, so valueOf gives the same cached instance the mapper uses.
	 */
	private static final OrderStatus PROCESSING = OrderStatus.valueOf("PROCESSING");

	private CustomOrderStatusMapper mapper;

	@Before
	public void setUp()
	{
		mapper = new CustomOrderStatusMapper();
	}

	@Test
	public void shouldMapOpenToCreated()
	{
		assertEquals(OrderStatus.CREATED, mapper.mapStatus("OPEN"));
		assertEquals("created", mapper.mapStatusDisplay("OPEN"));
	}

	@Test
	public void shouldMapInProcessToProcessing()
	{
		assertEquals(PROCESSING, mapper.mapStatus("IN_PROCESS"));
		assertEquals("processing", mapper.mapStatusDisplay("IN_PROCESS"));
	}

	@Test
	public void shouldMapShippedToCompletedButKeepItsOwnDisplay()
	{
		assertEquals(OrderStatus.COMPLETED, mapper.mapStatus("SHIPPED"));
		assertEquals("shipped", mapper.mapStatusDisplay("SHIPPED"));
	}

	@Test
	public void shouldMapCompletedToCompleted()
	{
		assertEquals(OrderStatus.COMPLETED, mapper.mapStatus("COMPLETED"));
		assertEquals("completed", mapper.mapStatusDisplay("COMPLETED"));
	}

	@Test
	public void shouldMapCancelledToCancelled()
	{
		assertEquals(OrderStatus.CANCELLED, mapper.mapStatus("CANCELLED"));
		assertEquals("cancelled", mapper.mapStatusDisplay("CANCELLED"));
	}

	/**
	 * SHIPPED and COMPLETED share the hybris status but must stay distinguishable in the UI.
	 */
	@Test
	public void shouldKeepShippedAndCompletedApartInTheDisplayValue()
	{
		assertEquals(mapper.mapStatus("SHIPPED"), mapper.mapStatus("COMPLETED"));
		assertEquals("shipped", mapper.mapStatusDisplay("SHIPPED"));
		assertEquals("completed", mapper.mapStatusDisplay("COMPLETED"));
	}

	@Test
	public void shouldMapAnUnknownStatusToNullAndUnknown()
	{
		assertNull(mapper.mapStatus("SOMETHING_ELSE"));
		assertEquals(CustomOrderStatusMapper.DISPLAY_UNKNOWN, mapper.mapStatusDisplay("SOMETHING_ELSE"));
	}

	@Test
	public void shouldMapAMissingStatusToNullAndUnknown()
	{
		assertNull(mapper.mapStatus(null));
		assertEquals(CustomOrderStatusMapper.DISPLAY_UNKNOWN, mapper.mapStatusDisplay(null));

		assertNull(mapper.mapStatus(""));
		assertEquals(CustomOrderStatusMapper.DISPLAY_UNKNOWN, mapper.mapStatusDisplay(""));

		assertNull(mapper.mapStatus("   "));
		assertEquals(CustomOrderStatusMapper.DISPLAY_UNKNOWN, mapper.mapStatusDisplay("   "));
	}

	@Test
	public void shouldTolerateSurroundingWhitespaceAndLowerCase()
	{
		assertEquals(OrderStatus.CANCELLED, mapper.mapStatus("  cancelled  "));
		assertEquals("cancelled", mapper.mapStatusDisplay("  cancelled  "));
	}
}
