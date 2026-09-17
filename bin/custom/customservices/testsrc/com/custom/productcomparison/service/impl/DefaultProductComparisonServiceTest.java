/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.productcomparison.service.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.category.model.CategoryModel;
import de.hybris.platform.core.PK;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.servicelayer.session.SessionService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.custom.productcomparison.constants.ProductComparisonConstants;
import com.custom.productcomparison.model.ProductComparisonList;
import com.custom.productcomparison.service.ProductComparisonGroupingService;


/**
 * Covers {@link DefaultProductComparisonService#addProduct}, {@code removeProduct}, {@code
 * deleteList} and {@code getLists} (NET-8940 section 5.1, acceptance criteria 1-4, 8, 8a).
 * <p>
 * The session attribute is simulated with a plain field ({@code sessionStore}) that {@code
 * getAttribute}/{@code setAttribute} read and write, mirroring how the real {@code SessionService}
 * round-trips a value across requests within one session.
 */
@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class DefaultProductComparisonServiceTest
{
	@Mock
	private SessionService sessionService;
	@Mock
	private ProductComparisonGroupingService productComparisonGroupingService;

	private List<ProductComparisonList> sessionStore;

	private DefaultProductComparisonService productComparisonService;

	@Before
	public void setUp()
	{
		sessionStore = null;

		given(sessionService.getAttribute(ProductComparisonConstants.SESSION_ATTRIBUTE_COMPARISON_LISTS))
				.will(invocation -> sessionStore);
		doAnswer(invocation -> {
			sessionStore = invocation.getArgument(1);
			return null;
		}).when(sessionService).setAttribute(eq(ProductComparisonConstants.SESSION_ATTRIBUTE_COMPARISON_LISTS), any());

		productComparisonService = new DefaultProductComparisonService();
		productComparisonService.setSessionService(sessionService);
		productComparisonService.setProductComparisonGroupingService(productComparisonGroupingService);
	}

	private CategoryModel category()
	{
		final CategoryModel category = mock(CategoryModel.class);
		given(category.getPk()).willReturn(mock(PK.class));
		return category;
	}

	private ProductModel product()
	{
		final ProductModel product = mock(ProductModel.class);
		given(product.getPk()).willReturn(mock(PK.class));
		return product;
	}

	private void givenGroupingCategory(final ProductModel product, final CategoryModel groupingCategory)
	{
		given(productComparisonGroupingService.resolveGroupingCategory(product)).willReturn(Optional.of(groupingCategory));
	}

	@Test
	public void shouldCreateANewListWhenAddingAProductWithNoExistingListForItsGroupingCategory()
	{
		final CategoryModel categoryA = category();
		final ProductModel product1 = product();
		givenGroupingCategory(product1, categoryA);

		final Optional<ProductComparisonList> result = productComparisonService.addProduct(product1);

		assertTrue(result.isPresent());
		assertTrue(result.get().containsProduct(product1));
		assertEquals(1, productComparisonService.getLists().size());
	}

	@Test
	public void shouldAppendToTheExistingListWhenAddingASecondProductWithTheSameGroupingCategory()
	{
		final CategoryModel categoryA = category();
		final ProductModel product1 = product();
		final ProductModel product2 = product();
		givenGroupingCategory(product1, categoryA);
		givenGroupingCategory(product2, categoryA);

		productComparisonService.addProduct(product1);
		productComparisonService.addProduct(product2);

		final List<ProductComparisonList> lists = productComparisonService.getLists();
		assertEquals(1, lists.size());
		assertTrue(lists.get(0).containsProduct(product1));
		assertTrue(lists.get(0).containsProduct(product2));
	}

	@Test
	public void shouldCreateASecondIndependentListWhenAddingAProductWithADifferentGroupingCategory()
	{
		final CategoryModel categoryA = category();
		final CategoryModel categoryB = category();
		final ProductModel product1 = product();
		final ProductModel product2 = product();
		givenGroupingCategory(product1, categoryA);
		givenGroupingCategory(product2, categoryB);

		productComparisonService.addProduct(product1);
		productComparisonService.addProduct(product2);

		assertEquals(2, productComparisonService.getLists().size());
	}

	@Test
	public void shouldNotDuplicateWhenAddingTheSameProductTwiceToTheSameList()
	{
		final CategoryModel categoryA = category();
		final ProductModel product1 = product();
		givenGroupingCategory(product1, categoryA);

		productComparisonService.addProduct(product1);
		productComparisonService.addProduct(product1);

		final List<ProductComparisonList> lists = productComparisonService.getLists();
		assertEquals(1, lists.size());
		assertEquals(1, lists.get(0).getProducts().size());
	}

	@Test
	public void shouldRemoveTheListEntirelyWhenItsLastProductIsRemoved()
	{
		final CategoryModel categoryA = category();
		final ProductModel product1 = product();
		givenGroupingCategory(product1, categoryA);
		final String listId = productComparisonService.addProduct(product1).get().getId();

		productComparisonService.removeProduct(listId, product1);

		assertTrue(productComparisonService.getLists().isEmpty());
	}

	@Test
	public void deleteListShouldRemoveTheWholeListRegardlessOfHowManyProductsItHolds()
	{
		final CategoryModel categoryA = category();
		final ProductModel product1 = product();
		final ProductModel product2 = product();
		final ProductModel product3 = product();
		givenGroupingCategory(product1, categoryA);
		givenGroupingCategory(product2, categoryA);
		givenGroupingCategory(product3, categoryA);
		productComparisonService.addProduct(product1);
		productComparisonService.addProduct(product2);
		final String listId = productComparisonService.addProduct(product3).get().getId();

		productComparisonService.deleteList(listId);

		assertTrue(productComparisonService.getLists().isEmpty());
	}

	/**
	 * A list retrieved via {@code SessionService.getAttribute(...)} is not safe to mutate directly
	 * (see the class javadoc on {@link DefaultProductComparisonService}) - it can come back backed by
	 * an unmodifiable snapshot. This pins that the service copies before mutating rather than calling
	 * {@code add(...)} on the stored reference itself, which would throw here.
	 */
	@Test
	public void shouldNotMutateTheStoredSessionListInPlaceWhenAddingAProduct()
	{
		sessionStore = Collections.unmodifiableList(new ArrayList<>());
		final CategoryModel categoryA = category();
		final ProductModel product1 = product();
		givenGroupingCategory(product1, categoryA);

		final Optional<ProductComparisonList> result = productComparisonService.addProduct(product1);

		assertTrue(result.isPresent());
		assertFalse(productComparisonService.getLists().isEmpty());
	}
}
