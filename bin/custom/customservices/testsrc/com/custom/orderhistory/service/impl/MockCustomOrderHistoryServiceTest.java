package com.custom.orderhistory.service.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.BDDMockito.given;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.servicelayer.config.ConfigurationService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.apache.commons.configuration.Configuration;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.custom.orderhistory.dto.CustomOrderDto;
import com.custom.orderhistory.dto.CustomOrderHistoryResponse;
import com.custom.orderhistory.dto.CustomOrderPositionDto;
import com.custom.orderhistory.service.CustomOrderHistoryException;


/**
 * Covers the JSON deserialization of the bundled mock payload (NET-8938 acceptance criterion 8).
 * <p>
 * The file is read from the classpath exactly as the service reads it at runtime, so this test also
 * guards against the resource being renamed or dropped from the built jar.
 */
@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class MockCustomOrderHistoryServiceTest
{
	private static final String CUSTOMER_NUMBER = "C-100234";
	private static final int EXPECTED_ORDER_COUNT = 10;

	@Mock
	private ConfigurationService configurationService;
	@Mock
	private Configuration configuration;

	private MockCustomOrderHistoryService service;

	@Before
	public void setUp()
	{
		service = new MockCustomOrderHistoryService();
		service.setConfigurationService(configurationService);
		given(configurationService.getConfiguration()).willReturn(configuration);
	}

	private void givenConfiguredResource(final String resource)
	{
		given(configuration.getString(MockCustomOrderHistoryService.MOCK_RESOURCE_PROPERTY,
				MockCustomOrderHistoryService.MOCK_RESOURCE_DEFAULT)).willReturn(resource);
	}

	@Test
	public void shouldDeserializeTheBundledMockFile()
	{
		givenConfiguredResource(MockCustomOrderHistoryService.MOCK_RESOURCE_DEFAULT);

		final CustomOrderHistoryResponse response = service.getOrderHistory(CUSTOMER_NUMBER);

		assertNotNull("the mock service must never return null", response);
		assertEquals(CUSTOMER_NUMBER, response.getCustomerNumber());
		assertEquals(EXPECTED_ORDER_COUNT, response.getOrders().size());
	}

	@Test
	public void shouldDeserializeAllFieldsOfTheFirstOrder()
	{
		givenConfiguredResource(MockCustomOrderHistoryService.MOCK_RESOURCE_DEFAULT);

		final CustomOrderDto order = service.getOrderHistory(CUSTOMER_NUMBER).getOrders().get(0);

		assertEquals("SO-100001", order.getOrderNumber());
		assertEquals("2026-01-15", order.getOrderDate());
		assertEquals("COMPLETED", order.getStatus());
		assertEquals("EUR", order.getCurrency());
		// NET-8938 section 4: totalPrice is authoritative and must survive deserialization exactly.
		assertEquals(0, new BigDecimal("169.56").compareTo(order.getTotalPrice()));

		final List<CustomOrderPositionDto> positions = order.getPositions();
		assertEquals(1, positions.size());
		final CustomOrderPositionDto position = positions.get(0);
		assertEquals(Integer.valueOf(1), position.getPositionNumber());
		assertEquals("SKU-10001", position.getProductReference());
		assertEquals(Integer.valueOf(3), position.getQuantity());
		assertEquals(0, new BigDecimal("56.52").compareTo(position.getPrice()));
	}

	@Test
	public void shouldDeserializeEveryOrderCompletely()
	{
		givenConfiguredResource(MockCustomOrderHistoryService.MOCK_RESOURCE_DEFAULT);

		for (final CustomOrderDto order : service.getOrderHistory(CUSTOMER_NUMBER).getOrders())
		{
			assertNotNull("orderNumber missing", order.getOrderNumber());
			assertNotNull("orderDate missing on " + order.getOrderNumber(), order.getOrderDate());
			assertNotNull("status missing on " + order.getOrderNumber(), order.getStatus());
			assertNotNull("currency missing on " + order.getOrderNumber(), order.getCurrency());
			assertNotNull("totalPrice missing on " + order.getOrderNumber(), order.getTotalPrice());
			assertTrue("positions missing on " + order.getOrderNumber(), !order.getPositions().isEmpty());
		}
	}

	@Test
	public void shouldFallBackToTheDefaultResourceWhenThePropertyIsBlank()
	{
		givenConfiguredResource("   ");

		assertEquals(EXPECTED_ORDER_COUNT, service.getOrderHistory(CUSTOMER_NUMBER).getOrders().size());
	}

	@Test
	public void shouldIgnoreUnknownFieldsInThePayload()
	{
		givenConfiguredResource("/customservices/test/order_history_unknown_fields_test.json");

		final CustomOrderHistoryResponse response = service.getOrderHistory(CUSTOMER_NUMBER);

		assertEquals(1, response.getOrders().size());
		assertEquals("SO-999001", response.getOrders().get(0).getOrderNumber());
	}

	/**
	 * NET-8938 section 5.1: a failure must never be swallowed into an empty list.
	 */
	@Test(expected = CustomOrderHistoryException.class)
	public void shouldThrowWhenTheResourceIsMissing()
	{
		givenConfiguredResource("/mockorderhistory/does_not_exist.json");

		service.getOrderHistory(CUSTOMER_NUMBER);
	}

	/**
	 * NET-8938 acceptance criterion 15: the single-order lookup by orderNumber.
	 */
	@Test
	public void shouldFindAnOrderByExactOrderNumber()
	{
		givenConfiguredResource(MockCustomOrderHistoryService.MOCK_RESOURCE_DEFAULT);

		final Optional<CustomOrderDto> order = service.getOrderDetail(CUSTOMER_NUMBER, "SO-100006");

		assertTrue(order.isPresent());
		assertEquals("SO-100006", order.get().getOrderNumber());
		assertEquals("SHIPPED", order.get().getStatus());
	}

	@Test
	public void shouldReturnEmptyForAnUnknownOrderNumber()
	{
		givenConfiguredResource(MockCustomOrderHistoryService.MOCK_RESOURCE_DEFAULT);

		assertFalse(service.getOrderDetail(CUSTOMER_NUMBER, "SO-999999").isPresent());
	}

	/**
	 * The match is exact and case-sensitive (NET-8938 section 5.1).
	 */
	@Test
	public void shouldNotMatchOnDifferentCase()
	{
		givenConfiguredResource(MockCustomOrderHistoryService.MOCK_RESOURCE_DEFAULT);

		assertFalse(service.getOrderDetail(CUSTOMER_NUMBER, "so-100001").isPresent());
	}
}
