package com.custom.orderhistory.service.impl;

import de.hybris.platform.servicelayer.config.ConfigurationService;

import java.util.Collections;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.custom.orderhistory.dto.CustomOrderDto;
import com.custom.orderhistory.dto.CustomOrderHistoryResponse;
import com.custom.orderhistory.service.CustomOrderHistoryException;
import com.custom.orderhistory.service.CustomOrderHistoryService;


/**
 * REST implementation of {@link CustomOrderHistoryService} (NET-8938 &sect;5.1).
 * <p>
 * The endpoint URL and both timeouts come from configuration and are resolved <em>per call</em>, so
 * they can be changed in HAC without a server restart (NET-8938 &sect;5.4). There is deliberately no
 * URL literal in this class.
 * <p>
 * On any failure a {@link CustomOrderHistoryException} is thrown - the service never returns an
 * empty list silently and never falls back to the order history in the Commerce DB.
 */
public class RestCustomOrderHistoryService implements CustomOrderHistoryService
{
	private static final Logger LOG = LoggerFactory.getLogger(RestCustomOrderHistoryService.class);

	protected static final String ENDPOINT_URL_PROPERTY = "customservices.orderhistory.rest.endpoint.url";
	protected static final String CONNECT_TIMEOUT_PROPERTY = "customservices.orderhistory.rest.connect.timeout.ms";
	protected static final String READ_TIMEOUT_PROPERTY = "customservices.orderhistory.rest.read.timeout.ms";

	protected static final int CONNECT_TIMEOUT_DEFAULT = 5000;
	protected static final int READ_TIMEOUT_DEFAULT = 10000;

	protected static final String CUSTOMER_NUMBER_PARAM = "customerNumber";

	private ConfigurationService configurationService;
	private RestTemplate restTemplate;

	@Override
	public CustomOrderHistoryResponse getOrderHistory(final String customerNumber)
	{
		final String endpointUrl = getEndpointUrl();
		if (StringUtils.isBlank(endpointUrl))
		{
			throw new CustomOrderHistoryException(
					"Property [" + ENDPOINT_URL_PROPERTY + "] is not configured - cannot read the order history");
		}

		applyTimeouts();

		final String url = UriComponentsBuilder.fromHttpUrl(endpointUrl).queryParam(CUSTOMER_NUMBER_PARAM, "{customerNumber}")
				.build().toUriString();

		try
		{
			LOG.debug("Reading order history for customer [{}] from [{}]", customerNumber, endpointUrl);
			final CustomOrderHistoryResponse response = getRestTemplate().getForObject(url, CustomOrderHistoryResponse.class,
					Collections.singletonMap(CUSTOMER_NUMBER_PARAM, customerNumber));
			if (response == null)
			{
				throw new CustomOrderHistoryException(
						"External order history endpoint [" + endpointUrl + "] returned an empty body");
			}
			return response;
		}
		catch (final RestClientException e)
		{
			throw new CustomOrderHistoryException("Call to the external order history endpoint [" + endpointUrl + "] for customer ["
					+ customerNumber + "] failed", e);
		}
	}

	/**
	 * {@code GET {endpoint.url}/{orderNumber}} - same {@link RestTemplate} and timeouts as
	 * {@link #getOrderHistory(String)} (NET-8938 &sect;5.1). A 404 is a normal miss and maps to
	 * {@link Optional#empty()}; any other non-2xx status or a transport error throws
	 * {@link CustomOrderHistoryException}.
	 */
	@Override
	public Optional<CustomOrderDto> getOrderDetail(final String customerNumber, final String orderNumber)
	{
		final String endpointUrl = getEndpointUrl();
		if (StringUtils.isBlank(endpointUrl))
		{
			throw new CustomOrderHistoryException(
					"Property [" + ENDPOINT_URL_PROPERTY + "] is not configured - cannot read the order detail");
		}

		applyTimeouts();

		final String url = UriComponentsBuilder.fromHttpUrl(endpointUrl).path("/{orderNumber}")
				.queryParam(CUSTOMER_NUMBER_PARAM, "{customerNumber}").buildAndExpand(orderNumber, customerNumber).toUriString();

		try
		{
			LOG.debug("Reading order [{}] of customer [{}] from [{}]", orderNumber, customerNumber, endpointUrl);
			final CustomOrderDto order = getRestTemplate().getForObject(url, CustomOrderDto.class);
			return Optional.ofNullable(order);
		}
		catch (final HttpClientErrorException e)
		{
			if (e.getStatusCode() == HttpStatus.NOT_FOUND)
			{
				return Optional.empty();
			}
			throw new CustomOrderHistoryException("Call to the external order history endpoint [" + endpointUrl + "] for order ["
					+ orderNumber + "] of customer [" + customerNumber + "] failed with status [" + e.getStatusCode() + "]", e);
		}
		catch (final RestClientException e)
		{
			throw new CustomOrderHistoryException("Call to the external order history endpoint [" + endpointUrl + "] for order ["
					+ orderNumber + "] of customer [" + customerNumber + "] failed", e);
		}
	}

	/**
	 * Resolved per call (NET-8938 &sect;5.4) - never cached in a field.
	 */
	protected String getEndpointUrl()
	{
		return StringUtils.trimToEmpty(getConfigurationService().getConfiguration().getString(ENDPOINT_URL_PROPERTY, null));
	}

	protected int getConnectTimeoutMs()
	{
		return getConfigurationService().getConfiguration().getInt(CONNECT_TIMEOUT_PROPERTY, CONNECT_TIMEOUT_DEFAULT);
	}

	protected int getReadTimeoutMs()
	{
		return getConfigurationService().getConfiguration().getInt(READ_TIMEOUT_PROPERTY, READ_TIMEOUT_DEFAULT);
	}

	/**
	 * Pushes the currently configured timeouts onto the request factory of the injected
	 * {@link RestTemplate}. Done per call so a timeout change in HAC takes effect without a restart.
	 */
	protected void applyTimeouts()
	{
		if (getRestTemplate().getRequestFactory() instanceof SimpleClientHttpRequestFactory)
		{
			final SimpleClientHttpRequestFactory requestFactory = (SimpleClientHttpRequestFactory) getRestTemplate()
					.getRequestFactory();
			requestFactory.setConnectTimeout(getConnectTimeoutMs());
			requestFactory.setReadTimeout(getReadTimeoutMs());
		}
	}

	protected ConfigurationService getConfigurationService()
	{
		return configurationService;
	}

	public void setConfigurationService(final ConfigurationService configurationService)
	{
		this.configurationService = configurationService;
	}

	protected RestTemplate getRestTemplate()
	{
		return restTemplate;
	}

	public void setRestTemplate(final RestTemplate restTemplate)
	{
		this.restTemplate = restTemplate;
	}
}
