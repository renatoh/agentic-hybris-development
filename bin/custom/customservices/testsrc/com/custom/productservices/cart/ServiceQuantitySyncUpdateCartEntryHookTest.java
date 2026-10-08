/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.productservices.cart;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commerceservices.order.CommerceCartModification;
import de.hybris.platform.commerceservices.order.CommerceCartService;
import de.hybris.platform.commerceservices.service.data.CommerceCartParameter;
import de.hybris.platform.core.model.order.CartEntryModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.servicelayer.model.ModelService;

import java.util.function.BiConsumer;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.custom.core.model.ServiceProductModel;
import com.custom.productservices.service.impl.DefaultServiceEntryGroupService;


/**
 * NET-8943 &sect;5.4 quantity sync and cascade, AC4, AC17. The same logic runs after a cart entry update and after an
 * add-to-cart (adding a product already in the cart merges into its line without calling the update hooks): services
 * follow the product quantity and the cart is recalculated only when something changed; when the product entry was
 * removed (quantity 0) the now empty SERVICE group is cleaned up.
 */
@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class ServiceQuantitySyncUpdateCartEntryHookTest
{
	@Mock
	private CartServiceSelectionService cartServiceSelectionService;
	@Mock
	private CommerceCartService commerceCartService;
	@Mock
	private ModelService modelService;

	private ServiceQuantitySyncUpdateCartEntryHook hook;

	private CartModel cart;
	private CartEntryModel productEntry;
	private CommerceCartParameter parameter;

	@Before
	public void setUp()
	{
		hook = new ServiceQuantitySyncUpdateCartEntryHook();
		hook.setCartServiceSelectionService(cartServiceSelectionService);
		hook.setServiceEntryGroupService(new DefaultServiceEntryGroupService());
		hook.setCommerceCartService(commerceCartService);
		hook.setModelService(modelService);

		cart = new CartModel();
		productEntry = new CartEntryModel();
		productEntry.setProduct(new ProductModel());
		productEntry.setOrder(cart);
		parameter = new CommerceCartParameter();
		parameter.setCart(cart);
	}

	private static CommerceCartModification resultFor(final CartEntryModel entry)
	{
		final CommerceCartModification result = new CommerceCartModification();
		result.setEntry(entry);
		return result;
	}

	/** The update path and the add-to-cart path must behave identically. */
	private BiConsumer<CommerceCartParameter, CommerceCartModification> afterUpdate()
	{
		return hook::afterUpdateCartEntry;
	}

	private BiConsumer<CommerceCartParameter, CommerceCartModification> afterAdd()
	{
		return hook::afterAddToCart;
	}

	// --- quantity changed / unchanged -----------------------------------------------------------------------------

	private void assertSyncsAndRecalculates(final BiConsumer<CommerceCartParameter, CommerceCartModification> path)
	{
		given(modelService.isNew(productEntry)).willReturn(false);
		given(cartServiceSelectionService.syncServiceQuantities(productEntry)).willReturn(true);

		path.accept(parameter, resultFor(productEntry));

		final ArgumentCaptor<CommerceCartParameter> captor = ArgumentCaptor.forClass(CommerceCartParameter.class);
		final InOrder order = inOrder(cartServiceSelectionService, modelService, commerceCartService);
		order.verify(cartServiceSelectionService).syncServiceQuantities(productEntry);
		order.verify(modelService).refresh(cart);
		order.verify(commerceCartService).calculateCart(captor.capture());
		assertSame(cart, captor.getValue().getCart());
		assertTrue(captor.getValue().isEnableHooks());
		verify(cartServiceSelectionService, never()).removeEmptyServiceGroups(cart);
	}

	@Test
	public void shouldSyncAndRecalculateAfterAnUpdateThatChangedAServiceQuantity()
	{
		assertSyncsAndRecalculates(afterUpdate());
	}

	@Test
	public void shouldSyncAndRecalculateAfterAnAddThatMergedIntoAnExistingLine()
	{
		assertSyncsAndRecalculates(afterAdd());
	}

	private void assertNoRecalculationWithoutChange(final BiConsumer<CommerceCartParameter, CommerceCartModification> path)
	{
		given(modelService.isNew(productEntry)).willReturn(false);
		given(cartServiceSelectionService.syncServiceQuantities(productEntry)).willReturn(false);

		path.accept(parameter, resultFor(productEntry));

		verifyNoInteractions(commerceCartService);
		verify(modelService, never()).refresh(cart);
	}

	@Test
	public void shouldNotRecalculateAfterAnUpdateWhenNothingChanged()
	{
		assertNoRecalculationWithoutChange(afterUpdate());
	}

	@Test
	public void shouldNotRecalculateAfterAnAddWhenNothingChanged()
	{
		assertNoRecalculationWithoutChange(afterAdd());
	}

	// --- removed product entry: clean up the empty SERVICE group --------------------------------------------------

	private void assertCleansUpEmptyGroups()
	{
		final InOrder order = inOrder(modelService, cartServiceSelectionService);
		order.verify(modelService).refresh(cart);
		order.verify(cartServiceSelectionService).removeEmptyServiceGroups(cart);
		verify(cartServiceSelectionService, never()).syncServiceQuantities(productEntry);
		verifyNoInteractions(commerceCartService);
	}

	@Test
	public void shouldCleanUpEmptyServiceGroupsWhenTheUpdatedEntryWasRemoved()
	{
		productEntry.setOrder(null);
		given(modelService.isNew(productEntry)).willReturn(false);

		hook.afterUpdateCartEntry(parameter, resultFor(productEntry));

		assertCleansUpEmptyGroups();
	}

	@Test
	public void shouldCleanUpEmptyServiceGroupsWhenTheResultHasNoEntry()
	{
		hook.afterUpdateCartEntry(parameter, resultFor(null));

		assertCleansUpEmptyGroups();
	}

	@Test
	public void shouldCleanUpEmptyServiceGroupsWhenThereIsNoResult()
	{
		hook.afterAddToCart(parameter, null);

		assertCleansUpEmptyGroups();
	}

	@Test
	public void shouldCleanUpEmptyServiceGroupsForANewEntry()
	{
		given(modelService.isNew(productEntry)).willReturn(true);

		hook.afterAddToCart(parameter, resultFor(productEntry));

		assertCleansUpEmptyGroups();
	}

	// --- ignored --------------------------------------------------------------------------------------------------

	@Test
	public void shouldIgnoreAMissingParameterOrCart()
	{
		hook.afterUpdateCartEntry(null, resultFor(productEntry));
		hook.afterUpdateCartEntry(new CommerceCartParameter(), resultFor(productEntry));
		hook.afterAddToCart(null, resultFor(productEntry));
		hook.afterAddToCart(new CommerceCartParameter(), resultFor(productEntry));

		verifyNoInteractions(cartServiceSelectionService, commerceCartService, modelService);
	}

	@Test
	public void shouldIgnoreAServiceEntry()
	{
		final CartEntryModel serviceEntry = new CartEntryModel();
		serviceEntry.setProduct(new ServiceProductModel());
		serviceEntry.setOrder(cart);
		given(modelService.isNew(serviceEntry)).willReturn(false);

		hook.afterUpdateCartEntry(parameter, resultFor(serviceEntry));
		hook.afterAddToCart(parameter, resultFor(serviceEntry));

		verifyNoInteractions(cartServiceSelectionService, commerceCartService);
		verify(modelService, never()).refresh(cart);
	}

	@Test
	public void shouldDoNothingBeforeTheUpdateOrTheAdd()
	{
		hook.beforeUpdateCartEntry(parameter);
		hook.beforeAddToCart(parameter);

		verifyNoInteractions(cartServiceSelectionService, commerceCartService, modelService);
	}
}
