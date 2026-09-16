package com.custom.competitorpricing.service.impl;

import de.hybris.platform.servicelayer.config.ConfigurationService;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.custom.competitorpricing.dto.CompetitorPriceDto;
import com.custom.competitorpricing.service.CompetitorPricingException;
import com.custom.competitorpricing.service.CompetitorPricingService;


/**
 * REST implementation of {@link CompetitorPricingService} (NET-8939 &sect;5.2).
 * <p>
 * The endpoint URL and both timeouts come from configuration and are resolved <em>per call</em>, so
 * they can be changed in HAC without a server restart (NET-8939 &sect;5.4). There is deliberately no
 * URL literal in this class.
 * <p>
 * On any failure a {@link CompetitorPricingException} is thrown - the service never returns a
 * partial or empty list silently.
 */
public class RestCompetitorPricingService implements CompetitorPricingService
{
	private static final Logger LOG = LoggerFactory.getLogger(RestCompetitorPricingService.class);

	protected static final String ENDPOINT_URL_PROPERTY = "customservices.competitorpricing.rest.endpoint.url";
	protected static final String CONNECT_TIMEOUT_PROPERTY = "customservices.competitorpricing.rest.connect.timeout.ms";
	protected static final String READ_TIMEOUT_PROPERTY = "customservices.competitorpricing.rest.read.timeout.ms";

	protected static final int CONNECT_TIMEOUT_DEFAULT = 5000;
	protected static final int READ_TIMEOUT_DEFAULT = 10000;

	private ConfigurationService configurationService;
	private RestTemplate restTemplate;

	/**
	 * tenantId is passed to the external endpoint as the <code>tenantId</code> query parameter
	 * (NET-8939 revised section 5.2) - identifies the tenant/store towards the external system.
	 */
	@Override
	public List<CompetitorPriceDto> getCompetitorPricing(final String tenantId)
	{
		final String endpointUrl = getEndpointUrl(tenantId);
		if (StringUtils.isBlank(endpointUrl))
		{
			throw new CompetitorPricingException(
					"Property [" + ENDPOINT_URL_PROPERTY + "] is not configured - cannot read competitor pricing");
		}

		applyTimeouts();

		try
		{
			LOG.debug("Reading competitor pricing for tenant [{}] from [{}]", tenantId, endpointUrl);
			final CompetitorPriceDto[] prices = getRestTemplate().getForObject(endpointUrl, CompetitorPriceDto[].class);
			if (prices == null)
			{
				throw new CompetitorPricingException("External competitor pricing endpoint [" + endpointUrl + "] returned an empty body");
			}
			return Arrays.asList(prices);
		}
		catch (final RestClientException e)
		{
			throw new CompetitorPricingException("Call to the external competitor pricing endpoint [" + endpointUrl + "] failed", e);
		}
	}

	/**
	 * Resolved per call (NET-8939 &sect;5.4) - never cached in a field. Appends
	 * <code>tenantId</code> as a query parameter so the endpoint knows which tenant's competitor
	 * pricing is being requested.
	 */
	protected String getEndpointUrl(final String tenantId)
	{
		final String baseUrl = StringUtils
				.trimToEmpty(getConfigurationService().getConfiguration().getString(ENDPOINT_URL_PROPERTY, null));
		if (StringUtils.isBlank(baseUrl))
		{
			return baseUrl;
		}
		final String separator = baseUrl.contains("?") ? "&" : "?";
		return baseUrl + separator + "tenantId=" + tenantId;
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
