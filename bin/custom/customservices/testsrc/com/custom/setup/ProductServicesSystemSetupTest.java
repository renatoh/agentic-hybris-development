/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.setup;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commerceservices.setup.SetupImpexService;
import de.hybris.platform.commerceservices.setup.SetupSyncJobService;
import de.hybris.platform.core.initialization.SystemSetupContext;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;


/**
 * NET-8943 &sect;5.9 / review round 1: the PROJECT step imports the sample data ImpEx, then the Solr ImpEx, then
 * synchronizes the sample catalog; a failing sync is logged, never propagated.
 */
@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class ProductServicesSystemSetupTest
{
	private static final String SAMPLE_IMPEX = "/customservices/impex/customservices-productservices-sampledata.impex";
	private static final String SOLR_IMPEX = "/customservices/impex/customservices-productservices-solr.impex";
	private static final String SAMPLE_CATALOG = "electronicsProductCatalog";

	@Mock
	private SetupImpexService setupImpexService;
	@Mock
	private SetupSyncJobService setupSyncJobService;
	@Mock
	private SystemSetupContext context;

	private ProductServicesSystemSetup setup;

	@Before
	public void setUp()
	{
		setup = new ProductServicesSystemSetup();
		setup.setSetupImpexService(setupImpexService);
		setup.setSetupSyncJobService(setupSyncJobService);
	}

	@Test
	public void shouldImportSampleDataBeforeSolrDataAndThenSyncTheCatalog()
	{
		setup.createProjectData(context);

		final InOrder order = inOrder(setupImpexService, setupSyncJobService);
		order.verify(setupImpexService).importImpexFile(SAMPLE_IMPEX, true);
		order.verify(setupImpexService).importImpexFile(SOLR_IMPEX, true);
		order.verify(setupSyncJobService).executeCatalogSyncJob(SAMPLE_CATALOG);
	}

	@Test
	public void shouldImportNoOtherImpex()
	{
		setup.createProjectData(context);

		verify(setupImpexService).importImpexFile(SAMPLE_IMPEX, true);
		verify(setupImpexService).importImpexFile(SOLR_IMPEX, true);
		verifyNoMoreInteractions(setupImpexService);
	}

	@Test
	public void shouldSyncOnlyTheSampleCatalog()
	{
		setup.createProjectData(context);

		verify(setupSyncJobService).executeCatalogSyncJob(SAMPLE_CATALOG);
		verifyNoMoreInteractions(setupSyncJobService);
	}

	@Test
	public void shouldNotPropagateASyncFailure()
	{
		given(setupSyncJobService.executeCatalogSyncJob(anyString())).willThrow(new IllegalStateException("sync failed"));

		setup.createProjectData(context);

		verify(setupImpexService).importImpexFile(SAMPLE_IMPEX, true);
		verify(setupImpexService).importImpexFile(SOLR_IMPEX, true);
		verify(setupSyncJobService).executeCatalogSyncJob(SAMPLE_CATALOG);
	}

	@Test
	public void shouldOfferNoInitializationOptions()
	{
		assertTrue(setup.getInitializationOptions().isEmpty());
	}
}
