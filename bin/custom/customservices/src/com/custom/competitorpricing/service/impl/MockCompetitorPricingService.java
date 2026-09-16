package com.custom.competitorpricing.service.impl;

import de.hybris.platform.servicelayer.config.ConfigurationService;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.custom.competitorpricing.dto.CompetitorPriceDto;
import com.custom.competitorpricing.service.CompetitorPricingException;
import com.custom.competitorpricing.service.CompetitorPricingService;
import com.fasterxml.jackson.databind.ObjectMapper;


/**
 * Mock implementation of {@link CompetitorPricingService} (NET-8939 &sect;5.2).
 * <p>
 * Reads the bundled JSON file from the <em>classpath</em> so that it also works in a packaged
 * deployment. The real endpoint does not exist yet.
 */
public class MockCompetitorPricingService implements CompetitorPricingService
{
	private static final Logger LOG = LoggerFactory.getLogger(MockCompetitorPricingService.class);

	protected static final String MOCK_RESOURCE_PROPERTY = "customservices.competitorpricing.mock.resource";
	protected static final String MOCK_RESOURCE_DEFAULT = "/competitorpricing/competitor_pricing_mock.json";

	private ConfigurationService configurationService;
	private ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public List<CompetitorPriceDto> getCompetitorPricing(final String tenantId)
	{
		// tenantId is intentionally ignored - there is only one bundled mock payload
		// regardless of tenant (NET-8939 revised section 5.2).
		final String resource = getMockResource();
		LOG.debug("Reading mock competitor pricing from classpath resource [{}]", resource);

		try (InputStream in = MockCompetitorPricingService.class.getResourceAsStream(resource))
		{
			if (in == null)
			{
				throw new CompetitorPricingException("Mock competitor pricing resource [" + resource + "] not found on the classpath");
			}
			final CompetitorPriceDto[] prices = getObjectMapper().readValue(in, CompetitorPriceDto[].class);
			if (prices == null)
			{
				throw new CompetitorPricingException("Mock competitor pricing resource [" + resource + "] is empty");
			}
			return Arrays.asList(prices);
		}
		catch (final IOException e)
		{
			throw new CompetitorPricingException("Could not read mock competitor pricing resource [" + resource + "]", e);
		}
	}

	/**
	 * Resolved per call so that the value can be changed in HAC without a restart (NET-8939 &sect;5.4).
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

	protected ObjectMapper getObjectMapper()
	{
		return objectMapper;
	}

	public void setObjectMapper(final ObjectMapper objectMapper)
	{
		this.objectMapper = objectMapper;
	}
}
