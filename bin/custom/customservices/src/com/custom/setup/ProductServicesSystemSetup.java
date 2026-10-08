package com.custom.setup;

import de.hybris.platform.commerceservices.setup.AbstractSystemSetup;
import de.hybris.platform.core.initialization.SystemSetup;
import de.hybris.platform.core.initialization.SystemSetup.Process;
import de.hybris.platform.core.initialization.SystemSetup.Type;
import de.hybris.platform.core.initialization.SystemSetupContext;
import de.hybris.platform.core.initialization.SystemSetupParameter;
import de.hybris.platform.core.initialization.SystemSetupParameterMethod;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.solrfacetsearch.model.config.SolrIndexerQueryModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Required;

import com.custom.constants.CustomservicesConstants;


/**
 * NET-8943: project data for services sold with products.
 * <p>
 * Registered as its own Spring bean ({@code productServicesSystemSetup}); a {@code @SystemSetup} class without a bean is
 * never instantiated by the platform.
 */
@SystemSetup(extension = CustomservicesConstants.EXTENSIONNAME)
public class ProductServicesSystemSetup extends AbstractSystemSetup
{
	protected static final String SAMPLE_IMPEX = "/customservices/impex/customservices-productservices-sampledata.impex";
	/** The catalog the sample data lives in; the ImpEx names the same catalog. */
	protected static final String SAMPLE_CATALOG = "electronicsProductCatalog";

	private FlexibleSearchService flexibleSearchService;
	private ModelService modelService;

	@Override
	@SystemSetupParameterMethod
	public List<SystemSetupParameter> getInitializationOptions()
	{
		return Collections.emptyList();
	}

	/**
	 * &sect;5.9: service products, price groups and rows and SERVICE references are imported into the Staged catalog version
	 * and published to Online through the catalog sync. {@code custominitialdata} is not an active extension in this
	 * installation, so its {@code InitialDataSystemSetup} cannot host this.
	 */
	@SystemSetup(type = Type.PROJECT, process = Process.ALL)
	public void createProjectData(final SystemSetupContext context)
	{
		importImpexFile(context, SAMPLE_IMPEX);
		excludeServiceProductsFromSolrIndex(context);
		try
		{
			executeCatalogSyncJob(context, SAMPLE_CATALOG);
		}
		catch (final RuntimeException e)
		{
			logError(context, "NET-8943: could not synchronize catalog " + SAMPLE_CATALOG
					+ " after importing the product services sample data", e);
		}
	}

	/**
	 * &sect;4.2: adds the {@code ServiceProduct} exclusion to every Product-based indexer query. Idempotent.
	 */
	protected void excludeServiceProductsFromSolrIndex(final SystemSetupContext context)
	{
		final List<SolrIndexerQueryModel> changed = new ArrayList<>();
		for (final SolrIndexerQueryModel indexerQuery : flexibleSearchService
				.<SolrIndexerQueryModel> search(new FlexibleSearchQuery("SELECT {PK} FROM {SolrIndexerQuery}")).getResult())
		{
			final String adjusted = ServiceProductSolrQueryAdjuster.excludeServiceProducts(indexerQuery.getQuery());
			if (adjusted != null && !adjusted.equals(indexerQuery.getQuery()))
			{
				indexerQuery.setQuery(adjusted);
				changed.add(indexerQuery);
			}
		}
		modelService.saveAll(changed);
		logInfo(context, "NET-8943: excluded service products from " + changed.size() + " Solr indexer queries");
	}

	@Required
	public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
	{
		this.flexibleSearchService = flexibleSearchService;
	}

	@Required
	public void setModelService(final ModelService modelService)
	{
		this.modelService = modelService;
	}
}
