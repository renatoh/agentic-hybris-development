package com.custom.competitorpricing.job;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.catalog.model.CatalogModel;
import de.hybris.platform.catalog.model.CatalogVersionModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.cronjob.enums.CronJobResult;
import de.hybris.platform.cronjob.enums.CronJobStatus;
import de.hybris.platform.servicelayer.cronjob.PerformResult;
import de.hybris.platform.servicelayer.exceptions.UnknownIdentifierException;
import de.hybris.platform.servicelayer.model.ModelService;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.custom.competitorpricing.dto.CompetitorPriceDto;
import com.custom.competitorpricing.service.CompetitorPricingException;
import com.custom.competitorpricing.service.CompetitorPricingService;
import com.custom.model.CompetitorPricingImportCronJobModel;


/**
 * Covers the per-product update including the skip-and-continue behaviour for an unresolved product
 * code, and the abort-without-saving behaviour when the competitor pricing fetch itself fails
 * (NET-8939 acceptance criterion 8).
 * <p>
 * Revised for multi-tenancy: {@code tenantId} and {@code catalogVersion} are read directly off the
 * {@link CompetitorPricingImportCronJobModel} instance handed to {@code perform(...)} - not resolved
 * via {@code CatalogVersionService}/{@code ConfigurationService}, which are being removed from the
 * job class (NET-8939 revised &sect;5.3 steps 1-3, acceptance criterion 8).
 * <p>
 * This test was written against the class shape agreed in NET-8939 &sect;5.3 while
 * {@code CompetitorPricingImportJob.perform(...)} was still being migrated off the old
 * config/catalog-service-based lookup - if it fails to compile or the assertions below don't match,
 * that migration is not finished yet on the {@code src/} side.
 */
@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class CompetitorPricingImportJobTest
{
	private static final String TENANT_ID = "electronics";
	private static final String CATALOG_ID = "electronicsProductCatalog";
	private static final String CATALOG_VERSION_NAME = "Staged";

	@Mock
	private CompetitorPricingService competitorPricingService;
	@Mock
	private de.hybris.platform.product.ProductService productService;
	@Mock
	private ModelService modelService;
	@Mock
	private CompetitorPricingImportCronJobModel cronJob;
	@Mock
	private CatalogVersionModel catalogVersion;
	@Mock
	private CatalogModel catalog;

	private CompetitorPricingImportJob job;

	@Before
	public void setUp()
	{
		job = new CompetitorPricingImportJob();
		job.setCompetitorPricingService(competitorPricingService);
		job.setProductService(productService);
		job.setModelService(modelService);

		given(cronJob.getTenantId()).willReturn(TENANT_ID);
		given(cronJob.getCatalogVersion()).willReturn(catalogVersion);
		given(catalogVersion.getCatalog()).willReturn(catalog);
		given(catalog.getId()).willReturn(CATALOG_ID);
		given(catalogVersion.getVersion()).willReturn(CATALOG_VERSION_NAME);
	}

	private CompetitorPriceDto price(final String productCode, final String average, final String min, final String max)
	{
		final CompetitorPriceDto dto = new CompetitorPriceDto();
		dto.setProductCode(productCode);
		dto.setAveragePrice(new BigDecimal(average));
		dto.setMinPrice(new BigDecimal(min));
		dto.setMaxPrice(new BigDecimal(max));
		return dto;
	}

	/**
	 * NET-8939 &sect;5.3, acceptance criterion 2: a matching product gets all three prices and is
	 * saved. Saving is batched via {@code modelService.saveAll(...)} - one call per run, not one
	 * {@code save()} per product - so the assertion checks the batch contains exactly this product.
	 */
	@Test
	public void shouldWriteAllThreePricesOntoAMatchingProduct()
	{
		final CompetitorPriceDto priceDto = price("1934793", "899.00", "849.50", "949.90");
		given(competitorPricingService.getCompetitorPricing(TENANT_ID)).willReturn(Collections.singletonList(priceDto));
		final ProductModel product = new ProductModel();
		given(productService.getProductForCode(catalogVersion, "1934793")).willReturn(product);

		final PerformResult result = job.perform(cronJob);

		assertEquals(0, new BigDecimal("899.00").compareTo(product.getCompetitorAveragePrice()));
		assertEquals(0, new BigDecimal("849.50").compareTo(product.getCompetitorMinPrice()));
		assertEquals(0, new BigDecimal("949.90").compareTo(product.getCompetitorMaxPrice()));
		verify(modelService).saveAll(Collections.singletonList(product));
		verify(modelService, never()).save(any());
		assertEquals(CronJobResult.SUCCESS, result.getResult());
		assertEquals(CronJobStatus.FINISHED, result.getStatus());
	}

	/**
	 * NET-8939 &sect;6 acceptance criterion 3: an unresolved product code is skipped and logged, and
	 * the run still finishes with SUCCESS while the other products are still saved.
	 */
	@Test
	public void shouldSkipAnUnresolvedProductCodeAndContinueWithTheRest()
	{
		final CompetitorPriceDto unresolvable = price("9999999-unknown", "199.00", "179.00", "219.00");
		final CompetitorPriceDto resolvable = price("1099285", "249.99", "219.00", "279.50");
		given(competitorPricingService.getCompetitorPricing(TENANT_ID)).willReturn(Arrays.asList(unresolvable, resolvable));
		given(productService.getProductForCode(catalogVersion, "9999999-unknown"))
				.willThrow(new UnknownIdentifierException("no product with code 9999999-unknown"));
		final ProductModel matchedProduct = new ProductModel();
		given(productService.getProductForCode(catalogVersion, "1099285")).willReturn(matchedProduct);

		final PerformResult result = job.perform(cronJob);

		verify(modelService).saveAll(Collections.singletonList(matchedProduct));
		verify(modelService, never()).save(any());
		assertEquals(0, new BigDecimal("249.99").compareTo(matchedProduct.getCompetitorAveragePrice()));
		assertEquals(CronJobResult.SUCCESS, result.getResult());
		assertEquals(CronJobStatus.FINISHED, result.getStatus());
	}

	/**
	 * NET-8939 &sect;6 acceptance criterion 8: a {@link CompetitorPricingException} from the service
	 * results in {@code CronJobResult.ERROR} and no persistence call is ever made for that run.
	 */
	@Test
	public void shouldReturnErrorAndNeverSaveWhenTheFetchFails()
	{
		given(competitorPricingService.getCompetitorPricing(TENANT_ID))
				.willThrow(new CompetitorPricingException("endpoint unreachable"));

		final PerformResult result = job.perform(cronJob);

		assertEquals(CronJobResult.ERROR, result.getResult());
		assertEquals(CronJobStatus.FINISHED, result.getStatus());
		verify(modelService, never()).save(any());
		verify(modelService, never()).saveAll(any(Collection.class));
	}

	/**
	 * NET-8939 acceptance criterion 8: {@code tenantId} must come from the
	 * {@code CompetitorPricingImportCronJobModel} instance passed into {@code perform(...)}, not from
	 * any shared/static/config source - proven by using a distinct tenantId per test instance and
	 * verifying the service is called with exactly that value.
	 */
	@Test
	public void shouldReadTenantIdFromTheCronJobInstancePassedToPerform()
	{
		given(cronJob.getTenantId()).willReturn("furniture");
		given(competitorPricingService.getCompetitorPricing("furniture")).willReturn(Collections.emptyList());

		job.perform(cronJob);

		verify(competitorPricingService).getCompetitorPricing(eq("furniture"));
		verify(competitorPricingService, never()).getCompetitorPricing(eq(TENANT_ID));
	}

	/**
	 * NET-8939 acceptance criterion 8: {@code catalogVersion} must come from the
	 * {@code CompetitorPricingImportCronJobModel} instance passed into {@code perform(...)}, not from
	 * any shared/static/config source - proven by using a distinct catalogVersion instance per test
	 * and verifying the product lookup is called with exactly that instance.
	 */
	@Test
	public void shouldReadCatalogVersionFromTheCronJobInstancePassedToPerform()
	{
		final CatalogVersionModel otherCatalogVersion = org.mockito.Mockito.mock(CatalogVersionModel.class);
		given(cronJob.getCatalogVersion()).willReturn(otherCatalogVersion);

		final CompetitorPriceDto priceDto = price("1934793", "899.00", "849.50", "949.90");
		given(competitorPricingService.getCompetitorPricing(TENANT_ID)).willReturn(Collections.singletonList(priceDto));
		final ProductModel product = new ProductModel();
		given(productService.getProductForCode(otherCatalogVersion, "1934793")).willReturn(product);

		job.perform(cronJob);

		verify(productService).getProductForCode(eq(otherCatalogVersion), eq("1934793"));
		verify(productService, never()).getProductForCode(eq(catalogVersion), any());
	}
}
