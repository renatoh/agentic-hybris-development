/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.productservices.cart;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commerceservices.service.data.CommerceCartParameter;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.CartEntryModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.product.ProductModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.custom.core.model.ServiceProductModel;
import com.custom.productservices.service.impl.DefaultServiceEntryGroupService;


/**
 * NET-8943 &sect;5.4 quantity sync and stale-service cleanup, AC4, AC11, AC17: before every commerce cart calculation,
 * invalid service entries are removed first (they cannot be priced and would fail the calculation) and then the
 * services of each product line are brought to that line's quantity. Uses the real
 * {@link DefaultServiceEntryGroupService} to tell service entries from product entries.
 */
@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class ServiceQuantitySyncCalculationHookTest
{
	@Mock
	private CartServiceSelectionService cartServiceSelectionService;

	private ServiceQuantitySyncCalculationHook hook;
	private CartModel cart;
	private CommerceCartParameter parameter;

	@Before
	public void setUp()
	{
		hook = new ServiceQuantitySyncCalculationHook();
		hook.setServiceEntryGroupService(new DefaultServiceEntryGroupService());
		hook.setCartServiceSelectionService(cartServiceSelectionService);

		cart = new CartModel();
		cart.setEntries(new ArrayList<>());
		parameter = new CommerceCartParameter();
		parameter.setCart(cart);
	}

	private CartEntryModel entry(final ProductModel product)
	{
		final CartEntryModel entry = new CartEntryModel();
		entry.setProduct(product);
		entry.setOrder(cart);
		final List<AbstractOrderEntryModel> entries = new ArrayList<>(cart.getEntries());
		entries.add(entry);
		cart.setEntries(entries);
		return entry;
	}

	@Test
	public void shouldRemoveInvalidServicesFirstAndThenSyncEveryProductLine()
	{
		final CartEntryModel dishwasher = entry(new ProductModel());
		final CartEntryModel installation = entry(new ServiceProductModel());
		final CartEntryModel toaster = entry(new ProductModel());

		hook.beforeCalculate(parameter);

		final InOrder order = inOrder(cartServiceSelectionService);
		order.verify(cartServiceSelectionService).removeInvalidServicesBeforeCalculation(cart);
		order.verify(cartServiceSelectionService).syncServiceQuantities(dishwasher);
		order.verify(cartServiceSelectionService).syncServiceQuantities(toaster);
		verify(cartServiceSelectionService, never()).syncServiceQuantities(installation);
		verifyNoMoreInteractions(cartServiceSelectionService);
	}

	@Test
	public void shouldStillCleanUpACartWithOnlyServiceEntries()
	{
		entry(new ServiceProductModel());
		entry(new ServiceProductModel());

		hook.beforeCalculate(parameter);

		verify(cartServiceSelectionService).removeInvalidServicesBeforeCalculation(cart);
		verifyNoMoreInteractions(cartServiceSelectionService);
	}

	@Test
	public void shouldOnlyRunTheCleanupForAnEmptyCart()
	{
		cart.setEntries(Collections.emptyList());

		hook.beforeCalculate(parameter);

		verify(cartServiceSelectionService).removeInvalidServicesBeforeCalculation(cart);
		verifyNoMoreInteractions(cartServiceSelectionService);
	}

	@Test
	public void shouldDoNothingWithoutEntries()
	{
		cart.setEntries(null);

		hook.beforeCalculate(parameter);

		verifyNoInteractions(cartServiceSelectionService);
	}

	@Test
	public void shouldDoNothingWithoutParameterOrCart()
	{
		hook.beforeCalculate(null);
		hook.beforeCalculate(new CommerceCartParameter());

		verifyNoInteractions(cartServiceSelectionService);
	}

	@Test
	public void shouldDoNothingAfterCalculating()
	{
		entry(new ProductModel());

		hook.afterCalculate(parameter);
		hook.afterCalculate(null);

		verifyNoInteractions(cartServiceSelectionService);
	}
}
