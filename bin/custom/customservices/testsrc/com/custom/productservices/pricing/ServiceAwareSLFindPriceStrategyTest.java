/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.productservices.pricing;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.core.model.order.CartEntryModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.jalo.order.price.PriceInformation;
import de.hybris.platform.order.exceptions.CalculationException;
import de.hybris.platform.order.strategies.calculation.FindPriceHook;
import de.hybris.platform.order.strategies.calculation.pdt.FindPDTValueInfoStrategy;
import de.hybris.platform.order.strategies.calculation.pdt.criteria.PDTCriteriaFactory;
import de.hybris.platform.order.strategies.calculation.pdt.criteria.PriceValueInfoCriteria;
import de.hybris.platform.order.strategies.calculation.pdt.criteria.impl.DefaultPriceValueInfoCriteria;
import de.hybris.platform.util.PriceValue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.custom.core.model.ServiceProductModel;
import com.custom.productservices.exceptions.ServicePriceNotFoundException;
import com.custom.productservices.hook.ServiceFindPriceHook;


/**
 * NET-8943 &sect;5.2a, AC16: the default lookup is skipped only when the {@link ServiceFindPriceHook} is applicable.
 * Every other entry, and every other applicable hook, gets the platform order: default lookup first, then the hook
 * with that default price.
 */
@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class ServiceAwareSLFindPriceStrategyTest
{
	@Mock
	private PDTCriteriaFactory pdtCriteriaFactory;
	@Mock
	private FindPDTValueInfoStrategy<PriceValue, PriceInformation, PriceValueInfoCriteria> findPriceValueInfoStrategy;
	@Mock
	private ServiceFindPriceHook serviceHook;
	@Mock
	private FindPriceHook otherHook;

	private ServiceAwareSLFindPriceStrategy strategy;

	private CartEntryModel serviceEntry;
	private CartEntryModel productEntry;
	private final PriceValue defaultPrice = new PriceValue("EUR", 86.86d, false);

	@Before
	public void setUp()
	{
		strategy = new ServiceAwareSLFindPriceStrategy();
		strategy.setPdtCriteriaFactory(pdtCriteriaFactory);
		strategy.setFindPriceValueInfoStrategy(findPriceValueInfoStrategy);

		serviceEntry = new CartEntryModel();
		serviceEntry.setProduct(new ServiceProductModel());
		productEntry = new CartEntryModel();
		productEntry.setProduct(new ProductModel());
	}

	private void givenDefaultLookupFor(final CartEntryModel entry) throws CalculationException
	{
		final PriceValueInfoCriteria criteria = DefaultPriceValueInfoCriteria.buildForValue().withProduct(entry.getProduct())
				.build();
		given(pdtCriteriaFactory.priceValueCriteriaFromOrderEntry(entry)).willReturn(criteria);
		given(findPriceValueInfoStrategy.getPDTValues(criteria)).willReturn(Collections.singletonList(defaultPrice));
	}

	@Test
	public void shouldSkipTheDefaultLookupWhenTheServiceHookIsApplicable() throws CalculationException
	{
		strategy.setFindPriceHooks(Collections.singletonList(serviceHook));
		final PriceValue servicePrice = new PriceValue("EUR", 120.0d, false);
		given(serviceHook.isApplicable(serviceEntry)).willReturn(true);
		given(serviceHook.findCustomBasePrice(serviceEntry, null)).willReturn(servicePrice);

		assertSame(servicePrice, strategy.findBasePrice(serviceEntry));

		verify(serviceHook).findCustomBasePrice(serviceEntry, null);
		verifyNoInteractions(pdtCriteriaFactory, findPriceValueInfoStrategy);
	}

	@Test
	public void shouldLetTheServiceHookFailureFailTheCalculationWithoutADefaultLookup() throws CalculationException
	{
		strategy.setFindPriceHooks(Collections.singletonList(serviceHook));
		given(serviceHook.isApplicable(serviceEntry)).willReturn(true);
		willThrow(new ServicePriceNotFoundException("no price")).given(serviceHook).findCustomBasePrice(serviceEntry, null);

		try
		{
			strategy.findBasePrice(serviceEntry);
			fail("expected ServicePriceNotFoundException");
		}
		catch (final ServicePriceNotFoundException expected)
		{
			verifyNoInteractions(pdtCriteriaFactory, findPriceValueInfoStrategy);
		}
	}

	@Test
	public void shouldRunTheDefaultLookupForANonServiceEntry() throws CalculationException
	{
		strategy.setFindPriceHooks(Collections.singletonList(serviceHook));
		given(serviceHook.isApplicable(productEntry)).willReturn(false);
		givenDefaultLookupFor(productEntry);

		assertSame(defaultPrice, strategy.findBasePrice(productEntry));

		verify(serviceHook, never()).findCustomBasePrice(any(), any());
	}

	@Test
	public void shouldRunTheDefaultLookupWhenThereAreNoHooks() throws CalculationException
	{
		strategy.setFindPriceHooks(Collections.emptyList());
		givenDefaultLookupFor(productEntry);

		assertSame(defaultPrice, strategy.findBasePrice(productEntry));
	}

	@Test
	public void shouldKeepThePlatformOrderForAnotherApplicableHook() throws CalculationException
	{
		strategy.setFindPriceHooks(Arrays.asList(serviceHook, otherHook));
		final PriceValue hookPrice = new PriceValue("EUR", 50.0d, false);
		given(serviceHook.isApplicable(productEntry)).willReturn(false);
		given(otherHook.isApplicable(productEntry)).willReturn(true);
		givenDefaultLookupFor(productEntry);
		given(otherHook.findCustomBasePrice(productEntry, defaultPrice)).willReturn(hookPrice);

		assertSame(hookPrice, strategy.findBasePrice(productEntry));

		final InOrder order = inOrder(findPriceValueInfoStrategy, otherHook);
		order.verify(findPriceValueInfoStrategy).getPDTValues(any());
		order.verify(otherHook).findCustomBasePrice(productEntry, defaultPrice);
		verify(serviceHook, never()).findCustomBasePrice(any(), any());
	}

	@Test
	public void shouldReturnTheDefaultPriceWhenNoHookIsApplicable() throws CalculationException
	{
		strategy.setFindPriceHooks(Arrays.asList(otherHook, serviceHook));
		given(otherHook.isApplicable(productEntry)).willReturn(false);
		given(serviceHook.isApplicable(productEntry)).willReturn(false);
		givenDefaultLookupFor(productEntry);

		assertSame(defaultPrice, strategy.findBasePrice(productEntry));
		verify(otherHook, never()).findCustomBasePrice(any(), any());
	}

	@Test
	public void shouldReturnNullLikeThePlatformWhenTheDefaultLookupFindsNothingForAProduct() throws CalculationException
	{
		strategy.setFindPriceHooks(Collections.emptyList());
		final PriceValueInfoCriteria criteria = DefaultPriceValueInfoCriteria.buildForValue().build();
		given(pdtCriteriaFactory.priceValueCriteriaFromOrderEntry(productEntry)).willReturn(criteria);
		given(findPriceValueInfoStrategy.getPDTValues(criteria)).willReturn(Collections.emptyList());

		assertNull(strategy.findBasePrice(productEntry));
	}

	@Test(expected = CalculationException.class)
	public void shouldPropagateTheDefaultLookupFailureForANonServiceEntry() throws CalculationException
	{
		strategy.setFindPriceHooks(Collections.singletonList(serviceHook));
		given(serviceHook.isApplicable(productEntry)).willReturn(false);
		final PriceValueInfoCriteria criteria = DefaultPriceValueInfoCriteria.buildForValue().build();
		given(pdtCriteriaFactory.priceValueCriteriaFromOrderEntry(productEntry)).willReturn(criteria);
		willThrow(new CalculationException("No price defined")).given(findPriceValueInfoStrategy).getPDTValues(criteria);

		strategy.findBasePrice(productEntry);
	}
}
