package com.custom.setup;

import de.hybris.platform.commerceservices.setup.AbstractSystemSetup;
import de.hybris.platform.core.initialization.SystemSetup;
import de.hybris.platform.core.initialization.SystemSetup.Process;
import de.hybris.platform.core.initialization.SystemSetup.Type;
import de.hybris.platform.core.initialization.SystemSetupContext;
import de.hybris.platform.core.initialization.SystemSetupParameter;
import de.hybris.platform.core.initialization.SystemSetupParameterMethod;
import de.hybris.platform.servicelayer.exceptions.UnknownIdentifierException;

import java.util.Collections;
import java.util.List;

import com.custom.constants.CustomservicesConstants;


/**
 * NET-8943: project data for services sold with products.
 * <p>
 * Registered as its own Spring bean ({@code productServicesSystemSetup}); a {@code @SystemSetup} class without a bean is
 * never instantiated by the platform.
 * <p>
 * Ordering: extensions run their setup in build order and {@code customservices} comes before {@code electronicsstore},
 * so on a fresh {@code ant initialize} the store's catalog and Solr queries do not exist yet when
 * {@link #createProjectData} runs. That case is handled by {@link ProductServicesDataImportEventListener}, which calls
 * {@link #importSampleData} / {@link #importSolrQueries} right after the store's own data import.
 */
@SystemSetup(extension = CustomservicesConstants.EXTENSIONNAME)
public class ProductServicesSystemSetup extends AbstractSystemSetup
{
	protected static final String SAMPLE_IMPEX = "/customservices/impex/customservices-productservices-sampledata.impex";
	/** &sect;4.2: the electronics store's indexer queries, re-imported with the {@code ServiceProduct} exclusion. */
	protected static final String SOLR_IMPEX = "/customservices/impex/customservices-productservices-solr.impex";
	/** &sect;4.2: the same for the visibility index; only importable here (see the file's header). */
	protected static final String SOLR_VISIBILITY_IMPEX = "/customservices/impex/customservices-productservices-solr-visibility.impex";
	/**
	 * The catalog the sample data lives in; the ImpEx names the same catalog, and the event listener bean's
	 * {@code productCatalogName} ({@code electronics}) must name the same store.
	 */
	protected static final String SAMPLE_CATALOG = "electronicsProductCatalog";
	protected static final String SAMPLE_CATALOG_VERSION = "Staged";

	@Override
	@SystemSetupParameterMethod
	public List<SystemSetupParameter> getInitializationOptions()
	{
		return Collections.emptyList();
	}

	/**
	 * &sect;5.9: on a system that already has the electronics store (e.g. {@code ant updatesystem} with the
	 * {@code customservices} project data selected). Skipped when the catalog does not exist yet: the event listener then
	 * imports the data once the store has created it.
	 */
	@SystemSetup(type = Type.PROJECT, process = Process.ALL)
	public void createProjectData(final SystemSetupContext context)
	{
		try
		{
			getCatalogVersionService().getCatalogVersion(SAMPLE_CATALOG, SAMPLE_CATALOG_VERSION);
		}
		catch (final UnknownIdentifierException e)
		{
			logInfo(context, "NET-8943: " + SAMPLE_CATALOG + " does not exist yet, product services data is imported after the"
					+ " store's data import instead");
			return;
		}
		importSampleData(context);
		importImpexFile(context, SOLR_VISIBILITY_IMPEX);
	}

	/**
	 * &sect;5.9: service products, price groups and rows and SERVICE references are imported into the Staged catalog version
	 * and published to Online through the catalog sync; plus the Solr query exclusion. Idempotent (INSERT_UPDATE / UPDATE
	 * only).
	 */
	public void importSampleData(final SystemSetupContext context)
	{
		importImpexFile(context, SAMPLE_IMPEX);
		importSolrQueries(context);
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

	/** &sect;4.2: re-applies the {@code ServiceProduct} exclusion to the electronics store's indexer queries. */
	public void importSolrQueries(final SystemSetupContext context)
	{
		importImpexFile(context, SOLR_IMPEX);
	}
}
