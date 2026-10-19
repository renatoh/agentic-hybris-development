/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.productservices.interceptor;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.core.enums.GroupType;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.CartEntryModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.order.EntryGroup;
import de.hybris.platform.servicelayer.interceptor.InterceptorContext;
import de.hybris.platform.servicelayer.interceptor.InterceptorException;
import de.hybris.platform.servicelayer.interceptor.PersistenceOperation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.custom.core.model.ServiceProductModel;
import com.custom.productservices.service.impl.DefaultServiceEntryGroupService;


/**
 * NET-8943 &sect;5.4 cascade, AC4: removing a product cart entry (by any path) removes its service entries in the
 * same persistence operation - and only its own.
 */
@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class ServiceEntryRemoveInterceptorTest
{
	@Mock
	private InterceptorContext ctx;

	private ServiceEntryRemoveInterceptor interceptor;
	private CartModel cart;

	@Before
	public void setUp()
	{
		interceptor = new ServiceEntryRemoveInterceptor();
		interceptor.setServiceEntryGroupService(new DefaultServiceEntryGroupService());

		cart = new CartModel();
		cart.setEntries(new ArrayList<>());
		cart.setEntryGroups(Arrays.asList(serviceGroup(1), serviceGroup(2)));
	}

	private static EntryGroup serviceGroup(final int number)
	{
		final EntryGroup group = new EntryGroup();
		group.setGroupNumber(Integer.valueOf(number));
		group.setGroupType(GroupType.SERVICE);
		return group;
	}

	private CartEntryModel entry(final int entryNumber, final ProductModel product, final Integer... groupNumbers)
	{
		final CartEntryModel entry = new CartEntryModel();
		entry.setEntryNumber(Integer.valueOf(entryNumber));
		entry.setProduct(product);
		entry.setOrder(cart);
		entry.setEntryGroupNumbers(new HashSet<>(Arrays.asList(groupNumbers)));
		final List<AbstractOrderEntryModel> entries = new ArrayList<>(cart.getEntries());
		entries.add(entry);
		cart.setEntries(entries);
		return entry;
	}

	@Test
	public void shouldRegisterEveryServiceEntryOfTheProductForDeletion() throws InterceptorException
	{
		final CartEntryModel dishwasher = entry(0, new ProductModel(), 1);
		final CartEntryModel installation = entry(1, new ServiceProductModel(), 1);
		final CartEntryModel warranty = entry(2, new ServiceProductModel(), 1);

		interceptor.onRemove(dishwasher, ctx);

		verify(ctx).registerElementFor(installation, PersistenceOperation.DELETE);
		verify(ctx).registerElementFor(warranty, PersistenceOperation.DELETE);
	}

	@Test
	public void shouldSkipServiceEntriesThatAreAlreadyBeingRemoved() throws InterceptorException
	{
		final CartEntryModel dishwasher = entry(0, new ProductModel(), 1);
		final CartEntryModel installation = entry(1, new ServiceProductModel(), 1);
		final CartEntryModel warranty = entry(2, new ServiceProductModel(), 1);
		given(ctx.isRemoved(installation)).willReturn(true);

		interceptor.onRemove(dishwasher, ctx);

		verify(ctx, never()).registerElementFor(installation, PersistenceOperation.DELETE);
		verify(ctx).registerElementFor(warranty, PersistenceOperation.DELETE);
	}

	@Test
	public void shouldNotTouchAnotherProductLinesServices() throws InterceptorException
	{
		final CartEntryModel dishwasher = entry(0, new ProductModel(), 1);
		final CartEntryModel dishwasherInstallation = entry(1, new ServiceProductModel(), 1);
		entry(2, new ProductModel(), 2);
		final CartEntryModel fridgeInstallation = entry(3, new ServiceProductModel(), 2);

		interceptor.onRemove(dishwasher, ctx);

		verify(ctx).registerElementFor(dishwasherInstallation, PersistenceOperation.DELETE);
		verify(ctx, never()).registerElementFor(fridgeInstallation, PersistenceOperation.DELETE);
	}

	@Test
	public void shouldDoNothingForAProductWithoutServices() throws InterceptorException
	{
		final CartEntryModel toaster = entry(0, new ProductModel());

		interceptor.onRemove(toaster, ctx);

		verifyNoInteractions(ctx);
	}

	@Test
	public void shouldNotCascadeWhenAServiceEntryItselfIsRemoved() throws InterceptorException
	{
		entry(0, new ProductModel(), 1);
		final CartEntryModel installation = entry(1, new ServiceProductModel(), 1);
		entry(2, new ServiceProductModel(), 1);

		interceptor.onRemove(installation, ctx);

		verifyNoInteractions(ctx);
	}

	@Test
	public void shouldIgnoreAnEntryWithoutOrder() throws InterceptorException
	{
		final CartEntryModel orphan = new CartEntryModel();
		orphan.setProduct(new ProductModel());

		interceptor.onRemove(orphan, ctx);

		verifyNoInteractions(ctx);
	}
}
