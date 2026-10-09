package com.custom.setup;

import de.hybris.platform.commerceservices.setup.AbstractSystemSetup;
import de.hybris.platform.core.initialization.SystemSetup;
import de.hybris.platform.core.initialization.SystemSetup.Process;
import de.hybris.platform.core.initialization.SystemSetup.Type;
import de.hybris.platform.core.initialization.SystemSetupContext;
import de.hybris.platform.core.initialization.SystemSetupParameter;
import de.hybris.platform.core.initialization.SystemSetupParameterMethod;

import java.util.Collections;
import java.util.List;

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
	/** &sect;4.2: the electronics store's indexer queries, re-imported with the {@code ServiceProduct} exclusion. */
	protected static final String SOLR_IMPEX = "/customservices/impex/customservices-productservices-solr.impex";
	/** The catalog the sample data lives in; the ImpEx names the same catalog. */
	protected static final String SAMPLE_CATALOG = "electronicsProductCatalog";

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
	 * <p>
	 * &sect;4.2: the Solr ImpEx overwrites the electronics store's indexer queries, so it has to run after the store's own
	 * project data (which creates them); re-importing the store's Solr ImpEx later reverts the exclusion.
	 */
	@SystemSetup(type = Type.PROJECT, process = Process.ALL)
	public void createProjectData(final SystemSetupContext context)
	{
		importImpexFile(context, SAMPLE_IMPEX);
		importImpexFile(context, SOLR_IMPEX);
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
}
