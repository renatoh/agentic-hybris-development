/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.setup;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.catalog.CatalogVersionService;
import de.hybris.platform.catalog.model.CatalogVersionModel;
import de.hybris.platform.commerceservices.setup.SetupImpexService;
import de.hybris.platform.commerceservices.setup.SetupSyncJobService;
import de.hybris.platform.core.initialization.SystemSetupContext;
import de.hybris.platform.servicelayer.exceptions.UnknownIdentifierException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;


/**
 * NET-8943 &sect;5.9 / review round 2: the PROJECT step only imports when the store's Staged catalog already exists (on a
 * fresh initialize it doesn't yet — the store's data-import events take over); the sample data step imports the sample
 * ImpEx, then the Solr ImpEx, then synchronizes, never propagating a sync failure.
 */
@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class ProductServicesSystemSetupTest
{
	private static final String SAMPLE_IMPEX = "/customservices/impex/customservices-productservices-sampledata.impex";
	private static final String SOLR_IMPEX = "/customservices/impex/customservices-productservices-solr.impex";
	private static final String SOLR_VISIBILITY_IMPEX = "/customservices/impex/customservices-productservices-solr-visibility.impex";
	private static final String SAMPLE_CATALOG = "electronicsProductCatalog";
	private static final String SAMPLE_CATALOG_VERSION = "Staged";

	@Mock
	private SetupImpexService setupImpexService;
	@Mock
	private SetupSyncJobService setupSyncJobService;
	@Mock
	private CatalogVersionService catalogVersionService;
	@Mock
	private SystemSetupContext context;

	private ProductServicesSystemSetup setup;

	@Before
	public void setUp()
	{
		setup = new ProductServicesSystemSetup();
		setup.setSetupImpexService(setupImpexService);
		setup.setSetupSyncJobService(setupSyncJobService);
		setup.setCatalogVersionService(catalogVersionService);
	}

	private void givenTheSampleCatalogExists()
	{
		given(catalogVersionService.getCatalogVersion(SAMPLE_CATALOG, SAMPLE_CATALOG_VERSION))
				.willReturn(new CatalogVersionModel());
	}

	// --- createProjectData -------------------------------------------------------------------------------------------

	@Test
	public void shouldDeferEverythingWhenTheSampleCatalogDoesNotExistYet()
	{
		given(catalogVersionService.getCatalogVersion(SAMPLE_CATALOG, SAMPLE_CATALOG_VERSION))
				.willThrow(new UnknownIdentifierException("no catalog yet"));

		setup.createProjectData(context);

		verifyNoInteractions(setupImpexService, setupSyncJobService);
	}

	@Test
	public void shouldImportSampleDataSolrQueriesAndVisibilityWhenTheSampleCatalogExists()
	{
		givenTheSampleCatalogExists();

		setup.createProjectData(context);

		final InOrder order = inOrder(setupImpexService, setupSyncJobService);
		order.verify(setupImpexService).importImpexFile(SAMPLE_IMPEX, true);
		order.verify(setupImpexService).importImpexFile(SOLR_IMPEX, true);
		order.verify(setupSyncJobService).executeCatalogSyncJob(SAMPLE_CATALOG);
		order.verify(setupImpexService).importImpexFile(SOLR_VISIBILITY_IMPEX, true);
		verifyNoMoreInteractions(setupImpexService, setupSyncJobService);
	}

	@Test
	public void shouldStillImportTheVisibilityQueriesWhenTheSyncFails()
	{
		givenTheSampleCatalogExists();
		given(setupSyncJobService.executeCatalogSyncJob(anyString())).willThrow(new IllegalStateException("sync failed"));

		setup.createProjectData(context);

		verify(setupImpexService).importImpexFile(SOLR_VISIBILITY_IMPEX, true);
	}

	// --- importSampleData ----------------------------------------------------------------------------------------------

	@Test
	public void shouldImportSampleDataBeforeSolrQueriesAndThenSyncTheCatalog()
	{
		setup.importSampleData(context);

		final InOrder order = inOrder(setupImpexService, setupSyncJobService);
		order.verify(setupImpexService).importImpexFile(SAMPLE_IMPEX, true);
		order.verify(setupImpexService).importImpexFile(SOLR_IMPEX, true);
		order.verify(setupSyncJobService).executeCatalogSyncJob(SAMPLE_CATALOG);
		verifyNoMoreInteractions(setupImpexService, setupSyncJobService);
		verifyNoInteractions(catalogVersionService);
	}

	@Test
	public void shouldNotPropagateASyncFailureOfTheSampleData()
	{
		given(setupSyncJobService.executeCatalogSyncJob(anyString())).willThrow(new IllegalStateException("sync failed"));

		setup.importSampleData(context);

		verify(setupImpexService).importImpexFile(SAMPLE_IMPEX, true);
		verify(setupImpexService).importImpexFile(SOLR_IMPEX, true);
		verify(setupSyncJobService).executeCatalogSyncJob(SAMPLE_CATALOG);
	}

	// --- importSolrQueries ---------------------------------------------------------------------------------------------

	@Test
	public void shouldImportOnlyTheSolrQueries()
	{
		setup.importSolrQueries(context);

		verify(setupImpexService).importImpexFile(SOLR_IMPEX, true);
		verifyNoMoreInteractions(setupImpexService);
		verifyNoInteractions(setupSyncJobService, catalogVersionService);
	}

	@Test
	public void shouldOfferNoInitializationOptions()
	{
		assertTrue(setup.getInitializationOptions().isEmpty());
	}
}
