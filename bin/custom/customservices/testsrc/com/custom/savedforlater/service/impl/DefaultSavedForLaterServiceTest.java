/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.savedforlater.service.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.catalog.model.CatalogModel;
import de.hybris.platform.catalog.model.CatalogVersionModel;
import de.hybris.platform.commerceservices.order.CommerceCartModification;
import de.hybris.platform.commerceservices.order.CommerceCartModificationException;
import de.hybris.platform.commerceservices.order.CommerceCartModificationStatus;
import de.hybris.platform.commerceservices.order.CommerceCartService;
import de.hybris.platform.commerceservices.service.data.CommerceCartParameter;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.order.CartService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.store.BaseStoreModel;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.custom.model.SavedForLaterEntryModel;


/**
 * Covers {@link DefaultSavedForLaterService#saveForLater}, {@code getSavedItems}, {@code
 * moveToCart} and {@code removeSavedItem} (NET-8941 section 4/5.3, acceptance criteria 2, 5, 6, 8).
 */
@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class DefaultSavedForLaterServiceTest
{
	@Mock
	private ModelService modelService;
	@Mock
	private CartService cartService;
	@Mock
	private CommerceCartService commerceCartService;
	@Mock
	private CustomerModel customer;

	private DefaultSavedForLaterService savedForLaterService;

	@Before
	public void setUp()
	{
		savedForLaterService = new DefaultSavedForLaterService();
		savedForLaterService.setModelService(modelService);
		savedForLaterService.setCartService(cartService);
		savedForLaterService.setCommerceCartService(commerceCartService);
	}

	private ProductModel product(final CatalogModel catalog)
	{
		final ProductModel product = mock(ProductModel.class);
		final CatalogVersionModel catalogVersion = mock(CatalogVersionModel.class);
		given(catalogVersion.getCatalog()).willReturn(catalog);
		given(product.getCatalogVersion()).willReturn(catalogVersion);
		return product;
	}

	private SavedForLaterEntryModel entry(final ProductModel product, final long quantity)
	{
		final SavedForLaterEntryModel entry = mock(SavedForLaterEntryModel.class);
		given(entry.getProduct()).willReturn(product);
		given(entry.getQuantity()).willReturn(quantity);
		return entry;
	}

	@Test
	public void shouldCreateANewEntryWhenSavingAProductWithNoExistingEntry()
	{
		given(customer.getSavedForLaterEntries()).willReturn(List.of());
		final ProductModel product = product(mock(CatalogModel.class));
		final SavedForLaterEntryModel createdEntry = mock(SavedForLaterEntryModel.class);
		given(modelService.create(SavedForLaterEntryModel.class)).willReturn(createdEntry);

		savedForLaterService.saveForLater(customer, product, 3L);

		verify(createdEntry).setCustomer(customer);
		verify(createdEntry).setProduct(product);
		verify(createdEntry).setQuantity(3L);
		verify(modelService).save(createdEntry);
	}

	@Test
	public void shouldIncrementTheExistingEntryRatherThanCreatingADuplicateWhenTheProductIsAlreadySaved()
	{
		final ProductModel product = product(mock(CatalogModel.class));
		final SavedForLaterEntryModel existingEntry = entry(product, 2L);
		given(customer.getSavedForLaterEntries()).willReturn(List.of(existingEntry));

		savedForLaterService.saveForLater(customer, product, 3L);

		verify(existingEntry).setQuantity(5L);
		verify(modelService).save(existingEntry);
		verify(modelService, never()).create(SavedForLaterEntryModel.class);
	}

	@Test
	public void shouldOnlyReturnSavedEntriesWhoseProductCatalogBelongsToTheGivenStore()
	{
		final CatalogModel storeCatalog = mock(CatalogModel.class);
		final CatalogModel otherCatalog = mock(CatalogModel.class);
		final SavedForLaterEntryModel entryInStore = entry(product(storeCatalog), 1L);
		final SavedForLaterEntryModel entryNotInStore = entry(product(otherCatalog), 1L);
		given(customer.getSavedForLaterEntries()).willReturn(List.of(entryInStore, entryNotInStore));

		final BaseStoreModel baseStore = mock(BaseStoreModel.class);
		given(baseStore.getCatalogs()).willReturn(List.of(storeCatalog));

		final List<SavedForLaterEntryModel> result = savedForLaterService.getSavedItems(customer, baseStore);

		assertEquals(List.of(entryInStore), result);
	}

	@Test
	public void shouldReturnAnEmptyListWhenTheCustomerHasNoSavedEntries()
	{
		given(customer.getSavedForLaterEntries()).willReturn(null);
		final BaseStoreModel baseStore = mock(BaseStoreModel.class);
		given(baseStore.getCatalogs()).willReturn(List.of(mock(CatalogModel.class)));

		final List<SavedForLaterEntryModel> result = savedForLaterService.getSavedItems(customer, baseStore);

		assertTrue(result.isEmpty());
	}

	@Test
	public void shouldAddTheEntryToTheSessionCartAndRemoveItFromTheSavedListWhenMovedToCart() throws CommerceCartModificationException
	{
		final CartModel sessionCart = mock(CartModel.class);
		given(cartService.getSessionCart()).willReturn(sessionCart);
		final ProductModel product = mock(ProductModel.class);
		final SavedForLaterEntryModel entry = entry(product, 4L);
		final CommerceCartModification success = modification(CommerceCartModificationStatus.SUCCESS, 4L);
		given(commerceCartService.addToCart(any(CommerceCartParameter.class))).willReturn(success);

		savedForLaterService.moveToCart(customer, entry);

		final ArgumentCaptor<CommerceCartParameter> captor = ArgumentCaptor.forClass(CommerceCartParameter.class);
		verify(commerceCartService).addToCart(captor.capture());
		final CommerceCartParameter parameter = captor.getValue();
		assertEquals(sessionCart, parameter.getCart());
		assertEquals(product, parameter.getProduct());
		assertEquals(4L, parameter.getQuantity());
		verify(modelService).remove(entry);
	}

	@Test
	public void shouldNotRemoveTheEntryWhenAddingToCartThrows() throws CommerceCartModificationException
	{
		given(cartService.getSessionCart()).willReturn(mock(CartModel.class));
		final ProductModel product = mock(ProductModel.class);
		final SavedForLaterEntryModel entry = entry(product, 1L);
		final CommerceCartModificationException failure = new CommerceCartModificationException("invalid quantity");
		doThrow(failure).when(commerceCartService).addToCart(any(CommerceCartParameter.class));

		try
		{
			savedForLaterService.moveToCart(customer, entry);
			fail("expected CommerceCartModificationException to propagate");
		}
		catch (final CommerceCartModificationException expected)
		{
			assertEquals(failure, expected);
		}

		verify(modelService, never()).remove(entry);
	}

	@Test
	public void shouldNotRemoveTheEntryWhenAddToCartReturnsANonSuccessStatusWithoutThrowing() throws CommerceCartModificationException
	{
		// DefaultCommerceAddToCartStrategy does not throw for an ordinary stock/availability
		// failure - it returns a non-SUCCESS status with quantityAdded=0 instead. This is the
		// actual shape of AC5's "now out of stock" scenario, not a thrown exception.
		given(cartService.getSessionCart()).willReturn(mock(CartModel.class));
		final ProductModel product = mock(ProductModel.class);
		final SavedForLaterEntryModel entry = entry(product, 1L);
		final CommerceCartModification outOfStock = modification(CommerceCartModificationStatus.NO_STOCK, 0L);
		given(commerceCartService.addToCart(any(CommerceCartParameter.class))).willReturn(outOfStock);

		try
		{
			savedForLaterService.moveToCart(customer, entry);
			fail("expected CommerceCartModificationException for a non-success status");
		}
		catch (final CommerceCartModificationException expected)
		{
			// expected
		}

		verify(modelService, never()).remove(entry);
	}

	private CommerceCartModification modification(final String statusCode, final long quantityAdded)
	{
		final CommerceCartModification modification = mock(CommerceCartModification.class);
		given(modification.getStatusCode()).willReturn(statusCode);
		given(modification.getQuantityAdded()).willReturn(quantityAdded);
		return modification;
	}

	@Test
	public void shouldRemoveTheEntryWithoutTouchingTheCart() throws CommerceCartModificationException
	{
		final SavedForLaterEntryModel entry = mock(SavedForLaterEntryModel.class);

		savedForLaterService.removeSavedItem(customer, entry);

		verify(modelService).remove(entry);
		verify(cartService, never()).getSessionCart();
		verify(commerceCartService, never()).addToCart(any(CommerceCartParameter.class));
	}
}
