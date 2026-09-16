package com.custom.competitorpricing.service.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.BDDMockito.given;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.servicelayer.config.ConfigurationService;

import java.math.BigDecimal;
import java.util.List;

import org.apache.commons.configuration.Configuration;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.custom.competitorpricing.dto.CompetitorPriceDto;
import com.custom.competitorpricing.service.CompetitorPricingException;


/**
 * Covers the JSON deserialization of the bundled mock payload (NET-8939 acceptance criterion 8).
 * <p>
 * The file is read from the classpath exactly as the service reads it at runtime, so this test also
 * guards against the resource being renamed or dropped from the built jar. The bundled payload
 * (NET-8939 &sect;4) deliberately includes one product code, {@code 9999999-unknown}, that does not
 * resolve to a real product - that is exercised by {@link com.custom.competitorpricing.job.CompetitorPricingImportJobTest},
 * not here; this test only asserts on the deserialized DTOs.
 */
@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class MockCompetitorPricingServiceTest
{
	private static final int EXPECTED_PRICE_COUNT = 5;
	private static final String TENANT_ID = "electronics";
	private static final String OTHER_TENANT_ID = "furniture";

	@Mock
	private ConfigurationService configurationService;
	@Mock
	private Configuration configuration;

	private MockCompetitorPricingService service;

	@Before
	public void setUp()
	{
		service = new MockCompetitorPricingService();
		service.setConfigurationService(configurationService);
		given(configurationService.getConfiguration()).willReturn(configuration);
	}

	private void givenConfiguredResource(final String resource)
	{
		given(configuration.getString(MockCompetitorPricingService.MOCK_RESOURCE_PROPERTY,
				MockCompetitorPricingService.MOCK_RESOURCE_DEFAULT)).willReturn(resource);
	}

	@Test
	public void shouldDeserializeTheBundledMockFile()
	{
		givenConfiguredResource(MockCompetitorPricingService.MOCK_RESOURCE_DEFAULT);

		final List<CompetitorPriceDto> prices = service.getCompetitorPricing(TENANT_ID);

		assertNotNull("the mock service must never return null", prices);
		assertEquals(EXPECTED_PRICE_COUNT, prices.size());
	}

	@Test
	public void shouldDeserializeAllFieldsOfTheFirstEntry()
	{
		givenConfiguredResource(MockCompetitorPricingService.MOCK_RESOURCE_DEFAULT);

		final CompetitorPriceDto price = service.getCompetitorPricing(TENANT_ID).get(0);

		assertEquals("1934793", price.getProductCode());
		assertEquals(0, new BigDecimal("899.00").compareTo(price.getAveragePrice()));
		assertEquals(0, new BigDecimal("849.50").compareTo(price.getMinPrice()));
		assertEquals(0, new BigDecimal("949.90").compareTo(price.getMaxPrice()));
	}

	@Test
	public void shouldDeserializeEveryEntryCompletely()
	{
		givenConfiguredResource(MockCompetitorPricingService.MOCK_RESOURCE_DEFAULT);

		for (final CompetitorPriceDto price : service.getCompetitorPricing(TENANT_ID))
		{
			assertNotNull("productCode missing", price.getProductCode());
			assertNotNull("averagePrice missing on " + price.getProductCode(), price.getAveragePrice());
			assertNotNull("minPrice missing on " + price.getProductCode(), price.getMinPrice());
			assertNotNull("maxPrice missing on " + price.getProductCode(), price.getMaxPrice());
		}
	}

	/**
	 * The bundled payload includes an unresolvable product code on purpose (NET-8939 &sect;4), so the
	 * job under test has something to skip - the mock service itself must still deserialize it like
	 * any other entry.
	 */
	@Test
	public void shouldIncludeTheUnresolvableProductCodeAsAnOrdinaryEntry()
	{
		givenConfiguredResource(MockCompetitorPricingService.MOCK_RESOURCE_DEFAULT);

		final List<CompetitorPriceDto> prices = service.getCompetitorPricing(TENANT_ID);

		final boolean containsUnresolvable = prices.stream().anyMatch(p -> "9999999-unknown".equals(p.getProductCode()));
		assertTrue("mock payload must contain the unresolvable code used by the job's skip-and-continue test",
				containsUnresolvable);
	}

	@Test
	public void shouldFallBackToTheDefaultResourceWhenThePropertyIsBlank()
	{
		givenConfiguredResource("   ");

		assertEquals(EXPECTED_PRICE_COUNT, service.getCompetitorPricing(TENANT_ID).size());
	}

	/**
	 * NET-8939 revised &sect;5.2: the mock reads the one bundled payload regardless of the
	 * {@code tenantId} argument - there is no per-tenant mock data.
	 */
	@Test
	public void shouldIgnoreTheTenantIdArgumentAndReturnTheSameResultForAnyTenant()
	{
		givenConfiguredResource(MockCompetitorPricingService.MOCK_RESOURCE_DEFAULT);

		final List<CompetitorPriceDto> pricesForTenant = service.getCompetitorPricing(TENANT_ID);
		final List<CompetitorPriceDto> pricesForOtherTenant = service.getCompetitorPricing(OTHER_TENANT_ID);

		assertEquals("tenantId must not affect how many entries are returned", pricesForTenant.size(),
				pricesForOtherTenant.size());
		for (int i = 0; i < pricesForTenant.size(); i++)
		{
			final CompetitorPriceDto expected = pricesForTenant.get(i);
			final CompetitorPriceDto actual = pricesForOtherTenant.get(i);
			assertEquals("tenantId must not affect the productCode returned", expected.getProductCode(), actual.getProductCode());
			assertEquals("tenantId must not affect averagePrice", 0, expected.getAveragePrice().compareTo(actual.getAveragePrice()));
			assertEquals("tenantId must not affect minPrice", 0, expected.getMinPrice().compareTo(actual.getMinPrice()));
			assertEquals("tenantId must not affect maxPrice", 0, expected.getMaxPrice().compareTo(actual.getMaxPrice()));
		}
	}

	/**
	 * NET-8939 &sect;5.2: a failure must never be swallowed into an empty list.
	 */
	@Test(expected = CompetitorPricingException.class)
	public void shouldThrowWhenTheResourceIsMissing()
	{
		givenConfiguredResource("/competitorpricing/does_not_exist.json");

		service.getCompetitorPricing(TENANT_ID);
	}
}
