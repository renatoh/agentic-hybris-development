/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.setup;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commerceservices.setup.data.ImportData;
import de.hybris.platform.commerceservices.setup.events.AbstractDataImportEvent;
import de.hybris.platform.commerceservices.setup.events.CoreDataImportedEvent;
import de.hybris.platform.commerceservices.setup.events.SampleDataImportedEvent;
import de.hybris.platform.core.initialization.SystemSetupContext;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;


/**
 * NET-8943 review round 2: the product services data follows the store's own data import — Solr queries after the
 * store's core data, sample data after the store's sample data — but only for the configured product catalog, and only
 * when the store actually imported that data (its {@code <extension>_importCoreData}/{@code _importSampleData} flag).
 */
@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class ProductServicesDataImportEventListenerTest
{
	private static final String CATALOG = "electronics";
	private static final String STORE_EXTENSION = "electronicsstore";
	private static final String CORE_FLAG = STORE_EXTENSION + "_importCoreData";
	private static final String SAMPLE_FLAG = STORE_EXTENSION + "_importSampleData";

	@Mock
	private ProductServicesSystemSetup setup;
	@Mock
	private SystemSetupContext context;

	private ProductServicesDataImportEventListener listener;

	@Before
	public void setUp()
	{
		listener = new ProductServicesDataImportEventListener();
		listener.setProductServicesSystemSetup(setup);
		listener.setProductCatalogName(CATALOG);
	}

	private static ImportData importData(final String productCatalogName)
	{
		final ImportData data = new ImportData();
		data.setProductCatalogName(productCatalogName);
		return data;
	}

	private static List<ImportData> catalogs(final String... productCatalogNames)
	{
		final ImportData[] data = new ImportData[productCatalogNames.length];
		for (int i = 0; i < productCatalogNames.length; i++)
		{
			data[i] = importData(productCatalogNames[i]);
		}
		return Arrays.asList(data);
	}

	private void givenTheStoreFlag(final String key, final String value)
	{
		given(context.getExtensionName()).willReturn(STORE_EXTENSION);
		given(context.getParameter(key)).willReturn(value);
	}

	// --- dispatch by event type ----------------------------------------------------------------------------------------

	@Test
	public void shouldImportTheSolrQueriesAfterTheStoresCoreData()
	{
		givenTheStoreFlag(CORE_FLAG, "yes");

		listener.onEvent(new CoreDataImportedEvent(context, catalogs(CATALOG)));

		verify(setup).importSolrQueries(context);
		verifyNoMoreInteractions(setup);
	}

	@Test
	public void shouldImportTheSampleDataAfterTheStoresSampleData()
	{
		givenTheStoreFlag(SAMPLE_FLAG, "yes");

		listener.onEvent(new SampleDataImportedEvent(context, catalogs(CATALOG)));

		verify(setup).importSampleData(context);
		verifyNoMoreInteractions(setup);
	}

	@Test
	public void shouldIgnoreAnyOtherDataImportEvent()
	{
		listener.onEvent(new AbstractDataImportEvent(context, catalogs(CATALOG)));

		verifyNoInteractions(setup);
	}

	@Test
	public void shouldIgnoreANullEvent()
	{
		listener.onEvent(null);

		verifyNoInteractions(setup);
	}

	// --- the store's own import flag -----------------------------------------------------------------------------------

	@Test
	public void shouldSkipTheSolrQueriesWhenTheStoreSkippedItsCoreData()
	{
		givenTheStoreFlag(CORE_FLAG, "no");

		listener.onEvent(new CoreDataImportedEvent(context, catalogs(CATALOG)));

		verifyNoInteractions(setup);
	}

	@Test
	public void shouldSkipTheSampleDataWhenTheStoreSkippedItsSampleData()
	{
		givenTheStoreFlag(SAMPLE_FLAG, "no");

		listener.onEvent(new SampleDataImportedEvent(context, catalogs(CATALOG)));

		verifyNoInteractions(setup);
	}

	@Test
	public void shouldProceedWhenTheStoreFlagIsMissing()
	{
		givenTheStoreFlag(SAMPLE_FLAG, null);

		listener.onEvent(new SampleDataImportedEvent(context, catalogs(CATALOG)));

		verify(setup).importSampleData(context);
		verifyNoMoreInteractions(setup);
	}

	// --- scoped to the configured product catalog ----------------------------------------------------------------------

	@Test
	public void shouldIgnoreAnotherStoresCatalog()
	{
		listener.onEvent(new SampleDataImportedEvent(context, catalogs("apparel")));
		listener.onEvent(new CoreDataImportedEvent(context, catalogs("apparel")));

		verifyNoInteractions(setup);
	}

	@Test
	public void shouldProceedWhenOneOfSeveralCatalogsMatches()
	{
		givenTheStoreFlag(SAMPLE_FLAG, "yes");

		listener.onEvent(new SampleDataImportedEvent(context, catalogs("apparel", CATALOG, null)));

		verify(setup).importSampleData(context);
		verifyNoMoreInteractions(setup);
	}

	@Test
	public void shouldIgnoreAnEventWithoutImportData()
	{
		listener.onEvent(new SampleDataImportedEvent(context, Collections.emptyList()));
		listener.onEvent(new SampleDataImportedEvent(context, null));
		listener.onEvent(new CoreDataImportedEvent(context, null));

		verifyNoInteractions(setup);
	}
}
