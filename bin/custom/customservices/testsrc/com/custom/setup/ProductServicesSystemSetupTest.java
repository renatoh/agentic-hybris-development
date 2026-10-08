/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.setup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.core.initialization.SystemSetupContext;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.search.impl.SearchResultImpl;
import de.hybris.platform.solrfacetsearch.model.config.SolrIndexerQueryModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;


/**
 * NET-8943 &sect;4.2, AC6: the ESSENTIAL step re-applies the {@code ServiceProduct} exclusion to every Product-based
 * Solr indexer query on each system update, saves only the queries it actually changed, and is idempotent.
 */
@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class ProductServicesSystemSetupTest
{
	private static final String FULL_QUERY = "SELECT {PK} FROM {Product} WHERE {code} NOT IN( {{ SELECT {code} FROM {GenericVariantProduct} }})";
	private static final String ORDER_QUERY = "SELECT {PK} FROM {Order}";

	@Mock
	private FlexibleSearchService flexibleSearchService;
	@Mock
	private ModelService modelService;
	@Mock
	private SystemSetupContext context;

	private ProductServicesSystemSetup setup;

	@Before
	public void setUp()
	{
		setup = new ProductServicesSystemSetup();
		setup.setFlexibleSearchService(flexibleSearchService);
		setup.setModelService(modelService);
	}

	private static SolrIndexerQueryModel indexerQuery(final String query)
	{
		final SolrIndexerQueryModel model = new SolrIndexerQueryModel();
		model.setQuery(query);
		return model;
	}

	private void givenIndexerQueries(final SolrIndexerQueryModel... queries)
	{
		final List<SolrIndexerQueryModel> list = Arrays.asList(queries);
		given(flexibleSearchService.<SolrIndexerQueryModel> search(any(FlexibleSearchQuery.class)))
				.willReturn(new SearchResultImpl<>(list, list.size(), list.size(), 0));
	}

	@SuppressWarnings("unchecked")
	private List<Object> savedQueries()
	{
		final ArgumentCaptor<Collection<Object>> saved = ArgumentCaptor.forClass(Collection.class);
		verify(modelService).saveAll(saved.capture());
		return new ArrayList<>(saved.getValue());
	}

	@Test
	public void shouldExcludeServiceProductsFromProductQueriesOnly()
	{
		final SolrIndexerQueryModel productQuery = indexerQuery(FULL_QUERY);
		final SolrIndexerQueryModel orderQuery = indexerQuery(ORDER_QUERY);
		final SolrIndexerQueryModel emptyQuery = indexerQuery(null);
		givenIndexerQueries(productQuery, orderQuery, emptyQuery);

		setup.createEssentialData(context);

		assertEquals(Collections.singletonList(productQuery), savedQueries());
		assertEquals(ServiceProductSolrQueryAdjuster.excludeServiceProducts(FULL_QUERY), productQuery.getQuery());
		assertTrue(productQuery.getQuery().contains("{ServiceProduct}"));
		assertEquals(ORDER_QUERY, orderQuery.getQuery());
	}

	@Test
	public void shouldSaveNothingWhenTheExclusionIsAlreadyThere()
	{
		final String alreadyExcluded = ServiceProductSolrQueryAdjuster.excludeServiceProducts(FULL_QUERY);
		final SolrIndexerQueryModel productQuery = indexerQuery(alreadyExcluded);
		givenIndexerQueries(productQuery);

		setup.createEssentialData(context);

		assertTrue(savedQueries().isEmpty());
		assertEquals(alreadyExcluded, productQuery.getQuery());
	}

	@Test
	public void shouldSaveNothingWithoutIndexerQueries()
	{
		givenIndexerQueries();

		setup.createEssentialData(context);

		assertTrue(savedQueries().isEmpty());
	}
}
