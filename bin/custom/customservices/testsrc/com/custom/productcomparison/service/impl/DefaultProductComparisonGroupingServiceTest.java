/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.productcomparison.service.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.category.model.CategoryModel;
import de.hybris.platform.commerceservices.helper.ProductAndCategoryHelper;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.servicelayer.config.ConfigurationService;

import java.util.Collections;
import java.util.Optional;

import org.apache.commons.configuration.Configuration;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.custom.productcomparison.constants.ProductComparisonConstants;


/**
 * Covers {@link DefaultProductComparisonGroupingService#resolveGroupingCategory(ProductModel)} and
 * the recursive {@code buildPathFromRoot(...)} it relies on (NET-8940 section 5.2 / section 4).
 * <p>
 * Uses a real {@link ProductAndCategoryHelper} (not a mock) with an empty product-category
 * blacklist - it is plain pass-through logic ({@code getBaseProduct}/{@code isValidProductCategory})
 * and mocking it would just restate its own body.
 */
@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class DefaultProductComparisonGroupingServiceTest
{
	@Mock
	private ConfigurationService configurationService;
	@Mock
	private Configuration configuration;

	private final ProductAndCategoryHelper productAndCategoryHelper = new ProductAndCategoryHelper();

	private DefaultProductComparisonGroupingService groupingService;

	@Before
	public void setUp()
	{
		productAndCategoryHelper.setProductCategoryBlacklist(Collections.emptyList());

		groupingService = new DefaultProductComparisonGroupingService();
		groupingService.setConfigurationService(configurationService);
		groupingService.setProductAndCategoryHelper(productAndCategoryHelper);

		given(configurationService.getConfiguration()).willReturn(configuration);
	}

	private void givenConfiguredDepth(final int depth)
	{
		given(configuration.getInt(ProductComparisonConstants.GROUPING_CATEGORY_DEPTH_PROPERTY,
				ProductComparisonConstants.DEFAULT_GROUPING_CATEGORY_DEPTH)).willReturn(depth);
	}

	/**
	 * Builds a synthetic-root -&gt; L1 -&gt; L2 -&gt; L3 chain (the catalog root has no
	 * supercategories of its own, per {@code buildRootToLeafPath}'s base case) and assigns the leaf
	 * (L3) as the product's own category.
	 */
	private CategoryModel[] threeLevelChain()
	{
		final CategoryModel root = mock(CategoryModel.class, "root");
		final CategoryModel l1 = mock(CategoryModel.class, "l1-cameras");
		final CategoryModel l2 = mock(CategoryModel.class, "l2-digitalCameras");
		final CategoryModel l3 = mock(CategoryModel.class, "l3-digitalCompacts");

		given(root.getSupercategories()).willReturn(Collections.emptyList());
		given(l1.getSupercategories()).willReturn(Collections.singletonList(root));
		given(l2.getSupercategories()).willReturn(Collections.singletonList(l1));
		given(l3.getSupercategories()).willReturn(Collections.singletonList(l2));

		return new CategoryModel[] { root, l1, l2, l3 };
	}

	private ProductModel productAssignedTo(final CategoryModel assignedCategory)
	{
		final ProductModel product = mock(ProductModel.class);
		given(product.getSupercategories()).willReturn(Collections.singletonList(assignedCategory));
		return product;
	}

	@Test
	public void shouldResolveTheCategoryAtTheConfiguredDepthForADeepEnoughPath()
	{
		final CategoryModel[] chain = threeLevelChain();
		final CategoryModel l3 = chain[3];
		final ProductModel product = productAssignedTo(l3);
		givenConfiguredDepth(3);

		final Optional<CategoryModel> result = groupingService.resolveGroupingCategory(product);

		assertTrue(result.isPresent());
		assertEquals(l3, result.get());
	}

	@Test
	public void shouldResolveADifferentCategoryWhenTheConfiguredDepthChangesWithNoCodeChange()
	{
		final CategoryModel[] chain = threeLevelChain();
		final CategoryModel l2 = chain[2];
		final CategoryModel l3 = chain[3];
		final ProductModel product = productAssignedTo(l3);

		givenConfiguredDepth(3);
		final Optional<CategoryModel> depth3Result = groupingService.resolveGroupingCategory(product);

		givenConfiguredDepth(2);
		final Optional<CategoryModel> depth2Result = groupingService.resolveGroupingCategory(product);

		assertEquals(l3, depth3Result.get());
		assertEquals(l2, depth2Result.get());
		assertNotEquals(depth3Result.get(), depth2Result.get());
	}

	@Test
	public void shouldFallBackToTheDeepestAvailableCategoryWhenThePathIsShorterThanTheConfiguredDepth()
	{
		final CategoryModel[] chain = threeLevelChain();
		final CategoryModel l2 = chain[2];
		final ProductModel product = productAssignedTo(l2);
		givenConfiguredDepth(3);

		final Optional<CategoryModel> result = groupingService.resolveGroupingCategory(product);

		assertTrue(result.isPresent());
		assertEquals(l2, result.get());
	}

	@Test
	public void shouldReturnEmptyForAProductWithNoAssignedCategoriesAtAll()
	{
		final ProductModel product = mock(ProductModel.class);
		given(product.getSupercategories()).willReturn(Collections.emptyList());
		givenConfiguredDepth(3);

		final Optional<CategoryModel> result = groupingService.resolveGroupingCategory(product);

		assertFalse(result.isPresent());
	}

	/**
	 * Regression test for NET-8940 section 8, open question 5: the catalog's synthetic root must be
	 * excluded from the counted path, so depth 1 resolves to the top-level navigation category
	 * ("Cameras"), not the root.
	 */
	@Test
	public void shouldExcludeTheSyntheticCatalogRootFromTheDepthCount()
	{
		final CategoryModel[] chain = threeLevelChain();
		final CategoryModel l1 = chain[1];
		final CategoryModel l3 = chain[3];
		final ProductModel product = productAssignedTo(l3);
		givenConfiguredDepth(1);

		final Optional<CategoryModel> result = groupingService.resolveGroupingCategory(product);

		assertTrue(result.isPresent());
		assertEquals(l1, result.get());
	}
}
