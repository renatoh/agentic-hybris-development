package com.custom.competitorpricing.job;

import de.hybris.platform.catalog.model.CatalogVersionModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.cronjob.enums.CronJobResult;
import de.hybris.platform.cronjob.enums.CronJobStatus;
import de.hybris.platform.product.ProductService;
import de.hybris.platform.servicelayer.cronjob.AbstractJobPerformable;
import de.hybris.platform.servicelayer.cronjob.PerformResult;
import de.hybris.platform.servicelayer.exceptions.AmbiguousIdentifierException;
import de.hybris.platform.servicelayer.exceptions.UnknownIdentifierException;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.custom.competitorpricing.dto.CompetitorPriceDto;
import com.custom.competitorpricing.service.CompetitorPricingException;
import com.custom.competitorpricing.service.CompetitorPricingService;
import com.custom.model.CompetitorPricingImportCronJobModel;


/**
 * Nightly import of competitor pricing onto {@link ProductModel} (NET-8939 revised &sect;5.3).
 * <p>
 * One Spring bean serves every tenant/store: each run takes its tenant id and catalog version
 * entirely from the {@link CompetitorPricingImportCronJobModel} instance it is handed, never from
 * shared/static/config state - that is what makes the one-bean-many-instances pattern safe under
 * concurrent or overlapping runs across tenants.
 * <p>
 * Either the full competitor price list is fetched and applied, or none of it is - a
 * {@link CompetitorPricingException} from
 * {@link CompetitorPricingService#getCompetitorPricing(String)} aborts the whole run (for that
 * instance only) before any {@code modelService.save()} call is made. A single product code that
 * does not resolve is logged and skipped; it does not abort the run.
 */
public class CompetitorPricingImportJob extends AbstractJobPerformable<CompetitorPricingImportCronJobModel>
{
	private static final Logger LOG = LoggerFactory.getLogger(CompetitorPricingImportJob.class);

	private CompetitorPricingService competitorPricingService;
	private ProductService productService;

	@Override
	public PerformResult perform(final CompetitorPricingImportCronJobModel job)
	{
		final String tenantId = job.getTenantId();
		final CatalogVersionModel catalogVersion = job.getCatalogVersion();

		final List<CompetitorPriceDto> competitorPrices;
		try
		{
			competitorPrices = getCompetitorPricingService().getCompetitorPricing(tenantId);
		}
		catch (final CompetitorPricingException e)
		{
			LOG.error("Could not fetch competitor pricing for tenant [{}] - the import run is aborted", tenantId, e);
			return new PerformResult(CronJobResult.ERROR, CronJobStatus.FINISHED);
		}

		final List<ProductModel> productsToSave = new ArrayList<>(competitorPrices.size());
		for (final CompetitorPriceDto competitorPrice : competitorPrices)
		{
			final ProductModel product = applyToProduct(competitorPrice, catalogVersion);
			if (product != null)
			{
				productsToSave.add(product);
			}
		}
		// one batched write instead of one modelService.save() per product - same ServiceLayer
		// transaction, far fewer persistence round-trips for a run of any real size.
		modelService.saveAll(productsToSave);

		return new PerformResult(CronJobResult.SUCCESS, CronJobStatus.FINISHED);
	}

	/**
	 * Resolves the product and sets the three competitor price attributes on it. Does **not** save -
	 * the caller batches every product from one run into a single {@code modelService.saveAll()}
	 * call (see {@link #perform(CompetitorPricingImportCronJobModel)}).
	 *
	 * @return the mutated, not-yet-saved product, or {@code null} if the code did not resolve
	 */
	protected ProductModel applyToProduct(final CompetitorPriceDto competitorPrice, final CatalogVersionModel catalogVersion)
	{
		final String productCode = competitorPrice.getProductCode();
		final ProductModel product;
		try
		{
			product = getProductService().getProductForCode(catalogVersion, productCode);
		}
		catch (final UnknownIdentifierException | AmbiguousIdentifierException e)
		{
			LOG.warn("Skipping competitor pricing for product code [{}] - it does not resolve to exactly one product in "
					+ "catalog version [{}:{}]", productCode, catalogVersion.getCatalog().getId(), catalogVersion.getVersion());
			return null;
		}

		product.setCompetitorAveragePrice(competitorPrice.getAveragePrice());
		product.setCompetitorMinPrice(competitorPrice.getMinPrice());
		product.setCompetitorMaxPrice(competitorPrice.getMaxPrice());
		return product;
	}

	protected CompetitorPricingService getCompetitorPricingService()
	{
		return competitorPricingService;
	}

	public void setCompetitorPricingService(final CompetitorPricingService competitorPricingService)
	{
		this.competitorPricingService = competitorPricingService;
	}

	protected ProductService getProductService()
	{
		return productService;
	}

	public void setProductService(final ProductService productService)
	{
		this.productService = productService;
	}
}
