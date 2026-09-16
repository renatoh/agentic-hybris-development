package com.custom.orderhistory.service.impl;

import de.hybris.platform.servicelayer.config.ConfigurationService;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.custom.orderhistory.dto.CustomOrderDto;
import com.custom.orderhistory.dto.CustomOrderHistoryResponse;
import com.custom.orderhistory.service.CustomOrderHistoryException;
import com.custom.orderhistory.service.CustomOrderHistoryService;
import com.fasterxml.jackson.databind.ObjectMapper;


/**
 * Mock implementation of {@link CustomOrderHistoryService} (NET-8938 &sect;5.1).
 * <p>
 * Reads the bundled JSON file from the <em>classpath</em> so that it also works in a packaged
 * deployment. The real endpoint does not exist yet; the file is returned regardless of the
 * requested customer number (NET-8938 &sect;6, open question 1).
 */
public class MockCustomOrderHistoryService implements CustomOrderHistoryService
{
	private static final Logger LOG = LoggerFactory.getLogger(MockCustomOrderHistoryService.class);

	protected static final String MOCK_RESOURCE_PROPERTY = "customservices.orderhistory.mock.resource";
	protected static final String MOCK_RESOURCE_DEFAULT = "/mockorderhistory/order_history_mock.json";

	private ConfigurationService configurationService;
	private ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public CustomOrderHistoryResponse getOrderHistory(final String customerNumber)
	{
		final String resource = getMockResource();
		LOG.debug("Reading mock order history for customer [{}] from classpath resource [{}]", customerNumber, resource);

		try (InputStream in = MockCustomOrderHistoryService.class.getResourceAsStream(resource))
		{
			if (in == null)
			{
				throw new CustomOrderHistoryException("Mock order history resource [" + resource + "] not found on the classpath");
			}
			final CustomOrderHistoryResponse response = getObjectMapper().readValue(in, CustomOrderHistoryResponse.class);
			if (response == null)
			{
				throw new CustomOrderHistoryException("Mock order history resource [" + resource + "] is empty");
			}
			return response;
		}
		catch (final IOException e)
		{
			throw new CustomOrderHistoryException("Could not read mock order history resource [" + resource + "]", e);
		}
	}

	/**
	 * Resolved per call so that the value can be changed in HAC without a restart (NET-8938 &sect;5.4).
	 */
	protected String getMockResource()
	{
		final String configured = getConfigurationService().getConfiguration().getString(MOCK_RESOURCE_PROPERTY,
				MOCK_RESOURCE_DEFAULT);
		return StringUtils.isBlank(configured) ? MOCK_RESOURCE_DEFAULT : configured.trim();
	}

	protected ConfigurationService getConfigurationService()
	{
		return configurationService;
	}

	public void setConfigurationService(final ConfigurationService configurationService)
	{
		this.configurationService = configurationService;
	}

	/**
	 * Iterates the same bundled payload used by {@link #getOrderHistory(String)} and returns the
	 * first order whose {@code orderNumber} matches exactly (NET-8938 &sect;5.1). No caching, no
	 * index - the payload is 10 orders.
	 */
	@Override
	public Optional<CustomOrderDto> getOrderDetail(final String customerNumber, final String orderNumber)
	{
		final List<CustomOrderDto> orders = getOrderHistory(customerNumber).getOrders();
		if (orders == null)
		{
			return Optional.empty();
		}
		for (final CustomOrderDto order : orders)
		{
			if (order != null && Objects.equals(orderNumber, order.getOrderNumber()))
			{
				return Optional.of(order);
			}
		}
		return Optional.empty();
	}

	protected ObjectMapper getObjectMapper()
	{
		return objectMapper;
	}

	public void setObjectMapper(final ObjectMapper objectMapper)
	{
		this.objectMapper = objectMapper;
	}
}
