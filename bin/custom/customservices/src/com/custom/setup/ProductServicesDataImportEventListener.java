package com.custom.setup;

import de.hybris.platform.commerceservices.setup.data.ImportData;
import de.hybris.platform.commerceservices.setup.events.AbstractDataImportEvent;
import de.hybris.platform.commerceservices.setup.events.CoreDataImportedEvent;
import de.hybris.platform.commerceservices.setup.events.SampleDataImportedEvent;
import de.hybris.platform.core.initialization.SystemSetupContext;
import de.hybris.platform.servicelayer.event.impl.AbstractEventListener;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Required;


/**
 * NET-8943: imports the product services data right after the store it belongs to has imported its own data. Needed
 * because {@code customservices} runs its project data before the store's on a fresh {@code ant initialize} (build
 * order), see {@link ProductServicesSystemSetup}.
 * <ul>
 * <li>{@link CoreDataImportedEvent}: the store's core data has just re-created its Solr indexer queries from SAP's text,
 * so the {@code ServiceProduct} exclusion is applied again before the store's sample data runs its full index. The
 * store's core-data step itself already ran a full index (when {@code activateSolrCronJobs} is set) before this event:
 * re-running only the store's core data on a system that already has services indexes them until the next full
 * index.</li>
 * <li>{@link SampleDataImportedEvent}: the store's products exist, so the services, their prices and references and the
 * Solr exclusion are imported and the catalog is synchronized.</li>
 * </ul>
 * Only for the store whose {@code ImportData} names the configured product catalog, and only if the store actually
 * imported that data (its own {@code importCoreData} / {@code importSampleData} setup parameter). Both events are
 * synchronous, so this runs inside the store's setup step. Everything imported is idempotent.
 */
public class ProductServicesDataImportEventListener extends AbstractEventListener<AbstractDataImportEvent>
{
	/** The store's own setup parameter keys ({@code CoreDataImportService} / {@code SampleDataImportService}). */
	static final String IMPORT_CORE_DATA = "importCoreData";
	static final String IMPORT_SAMPLE_DATA = "importSampleData";
	private static final String BOOLEAN_FALSE = "no";

	private ProductServicesSystemSetup productServicesSystemSetup;
	private String productCatalogName;

	@Override
	protected void onEvent(final AbstractDataImportEvent event)
	{
		if (event == null || !isForConfiguredCatalog(event))
		{
			return;
		}
		if (event instanceof CoreDataImportedEvent && isImported(event.getContext(), IMPORT_CORE_DATA))
		{
			productServicesSystemSetup.importSolrQueries(event.getContext());
		}
		else if (event instanceof SampleDataImportedEvent && isImported(event.getContext(), IMPORT_SAMPLE_DATA))
		{
			productServicesSystemSetup.importSampleData(event.getContext());
		}
	}

	protected boolean isForConfiguredCatalog(final AbstractDataImportEvent event)
	{
		return CollectionUtils.isNotEmpty(event.getImportData()) && event.getImportData().stream()
				.anyMatch(data -> data != null && productCatalogName.equals(data.getProductCatalogName()));
	}

	/**
	 * Same rule as {@code AbstractSystemSetup.getBooleanSystemSetupParameter}, read from the store's context; a missing
	 * parameter counts as imported because the stores default both options to {@code true}.
	 */
	protected boolean isImported(final SystemSetupContext context, final String flag)
	{
		return context != null && !BOOLEAN_FALSE.equals(context.getParameter(context.getExtensionName() + "_" + flag));
	}

	@Required
	public void setProductServicesSystemSetup(final ProductServicesSystemSetup productServicesSystemSetup)
	{
		this.productServicesSystemSetup = productServicesSystemSetup;
	}

	@Required
	public void setProductCatalogName(final String productCatalogName)
	{
		this.productCatalogName = productCatalogName;
	}
}
