/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.productservices.hook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.core.model.c2l.CurrencyModel;
import de.hybris.platform.core.model.order.CartEntryModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.product.UnitModel;
import de.hybris.platform.core.model.user.UserModel;
import de.hybris.platform.europe1.enums.ProductPriceGroup;
import de.hybris.platform.europe1.enums.UserPriceGroup;
import de.hybris.platform.jalo.order.price.PriceInformation;
import de.hybris.platform.order.exceptions.CalculationException;
import de.hybris.platform.order.strategies.calculation.pdt.FindPDTValueInfoStrategy;
import de.hybris.platform.order.strategies.calculation.pdt.criteria.PDTCriteriaFactory;
import de.hybris.platform.order.strategies.calculation.pdt.criteria.PDTCriteria.PDTCriteriaTarget;
import de.hybris.platform.order.strategies.calculation.pdt.criteria.PriceValueInfoCriteria;
import de.hybris.platform.order.strategies.calculation.pdt.criteria.impl.DefaultPriceValueInfoCriteria;
import de.hybris.platform.util.PriceValue;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.custom.core.enums.ServicePriceCondition;
import com.custom.core.model.ServiceProductModel;
import com.custom.productservices.exceptions.ServicePriceNotFoundException;
import com.custom.productservices.service.ProductServiceLookupService;
import com.custom.productservices.service.ServiceEntryGroupService;


/**
 * NET-8943 &sect;5.2, AC14: the hook prices a service entry from the group resolved via the linked product's
 * condition, using the platform-built criteria with only the product price group replaced. It never falls back to
 * {@code defaultPrice}; anything unpriceable fails the calculation.
 */
@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class ServiceFindPriceHookTest
{
	private static final PriceValue DEFAULT_PRICE = new PriceValue("EUR", 1.0d, false);

	@Mock
	private ServiceEntryGroupService serviceEntryGroupService;
	@Mock
	private ProductServiceLookupService productServiceLookupService;
	@Mock
	private PDTCriteriaFactory pdtCriteriaFactory;
	@Mock
	private FindPDTValueInfoStrategy<PriceValue, PriceInformation, PriceValueInfoCriteria> findPriceValueInfoStrategy;

	private ServiceFindPriceHook hook;

	private ServiceProductModel installation;
	private ProductModel dishwasher;
	private CartEntryModel serviceEntry;
	private CartEntryModel productEntry;

	private final UserModel user = new UserModel();
	private final CurrencyModel currency = new CurrencyModel();
	private final UnitModel unit = new UnitModel();
	private final Date date = new Date(1_700_000_000_000L);
	private final UserPriceGroup userGroup = UserPriceGroup.valueOf("B2C_VIP");

	@Before
	public void setUp()
	{
		hook = new ServiceFindPriceHook();
		hook.setServiceEntryGroupService(serviceEntryGroupService);
		hook.setProductServiceLookupService(productServiceLookupService);
		hook.setPdtCriteriaFactory(pdtCriteriaFactory);
		hook.setFindPriceValueInfoStrategy(findPriceValueInfoStrategy);

		installation = new ServiceProductModel();
		installation.setCode("SVC_INSTALLATION");
		dishwasher = new ProductModel();
		dishwasher.setCode("DISHWASHER");

		productEntry = new CartEntryModel();
		productEntry.setEntryNumber(Integer.valueOf(0));
		productEntry.setProduct(dishwasher);

		serviceEntry = new CartEntryModel();
		serviceEntry.setEntryNumber(Integer.valueOf(1));
		serviceEntry.setProduct(installation);
	}

	/** What {@code DefaultPDTCriteriaFactory.priceValueCriteriaFromOrderEntry} would build for the service entry. */
	private PriceValueInfoCriteria standardValueCriteria()
	{
		return DefaultPriceValueInfoCriteria.buildForValue() //
				.withProduct(installation) //
				.withUser(user) //
				.withUserPriceGroup(userGroup) //
				.withCurrency(currency) //
				.withQuantity(2L) //
				.withUnit(unit) //
				.withDate(date) //
				.withNet(Boolean.FALSE) //
				.build();
	}

	private void givenLinkedProductWithCondition(final ServicePriceCondition condition)
	{
		dishwasher.setServicePriceCondition(condition);
		given(serviceEntryGroupService.getProductEntry(serviceEntry)).willReturn(Optional.of(productEntry));
	}

	private ProductPriceGroup givenGroupFor(final ServicePriceCondition condition)
	{
		final ProductPriceGroup group = ProductPriceGroup.valueOf("SVC_INSTALLATION_" + condition.getCode());
		given(productServiceLookupService.getServicePriceGroup(installation, condition)).willReturn(Optional.of(group));
		return group;
	}

	private void givenStandardCriteria() throws CalculationException
	{
		given(pdtCriteriaFactory.priceValueCriteriaFromOrderEntry(serviceEntry)).willReturn(standardValueCriteria());
	}

	private void assertFailsWithServicePriceNotFound(final PriceValue defaultPrice)
	{
		try
		{
			hook.findCustomBasePrice(serviceEntry, defaultPrice);
			fail("expected ServicePriceNotFoundException - a service must never be charged at defaultPrice");
		}
		catch (final ServicePriceNotFoundException e)
		{
			assertNotNull(e.getMessage());
		}
	}

	// --- isApplicable ---------------------------------------------------------------------------------------------

	@Test
	public void shouldBeApplicableOnlyToServiceProductEntries()
	{
		assertTrue(hook.isApplicable(serviceEntry));
		assertFalse(hook.isApplicable(productEntry));

		final CartEntryModel noProduct = new CartEntryModel();
		assertFalse(hook.isApplicable(noProduct));
		assertFalse(hook.isApplicable(null));
	}

	// --- each condition -------------------------------------------------------------------------------------------

	@Test
	public void shouldPriceTheServiceFromTheLowGroup() throws CalculationException
	{
		assertPricedFromOwnGroup(ServicePriceCondition.LOW, 90.0d);
	}

	@Test
	public void shouldPriceTheServiceFromTheMediumGroup() throws CalculationException
	{
		assertPricedFromOwnGroup(ServicePriceCondition.MEDIUM, 120.0d);
	}

	@Test
	public void shouldPriceTheServiceFromTheHighGroup() throws CalculationException
	{
		assertPricedFromOwnGroup(ServicePriceCondition.HIGH, 150.0d);
	}

	private void assertPricedFromOwnGroup(final ServicePriceCondition condition, final double amount)
			throws CalculationException
	{
		givenLinkedProductWithCondition(condition);
		final ProductPriceGroup group = givenGroupFor(condition);
		givenStandardCriteria();
		final PriceValue expected = new PriceValue("EUR", amount, false);
		given(findPriceValueInfoStrategy.getPDTValues(any(PriceValueInfoCriteria.class)))
				.willReturn(Arrays.asList(expected, new PriceValue("EUR", 9999.0d, false)));

		assertSame(expected, hook.findCustomBasePrice(serviceEntry, null));

		final ArgumentCaptor<PriceValueInfoCriteria> captor = ArgumentCaptor.forClass(PriceValueInfoCriteria.class);
		verify(findPriceValueInfoStrategy).getPDTValues(captor.capture());
		assertSame(group, captor.getValue().getProductGroup());
	}

	@Test
	public void shouldReturnTheGroupPriceEvenWhenTheDefaultLookupFoundOne() throws CalculationException
	{
		givenLinkedProductWithCondition(ServicePriceCondition.MEDIUM);
		givenGroupFor(ServicePriceCondition.MEDIUM);
		givenStandardCriteria();
		final PriceValue groupPrice = new PriceValue("EUR", 120.0d, false);
		given(findPriceValueInfoStrategy.getPDTValues(any(PriceValueInfoCriteria.class)))
				.willReturn(Collections.singletonList(groupPrice));

		assertSame(groupPrice, hook.findCustomBasePrice(serviceEntry, DEFAULT_PRICE));
	}

	@Test
	public void shouldReplaceOnlyTheProductPriceGroupInTheStandardCriteria() throws CalculationException
	{
		givenLinkedProductWithCondition(ServicePriceCondition.MEDIUM);
		final ProductPriceGroup group = givenGroupFor(ServicePriceCondition.MEDIUM);
		final PriceValueInfoCriteria standard = standardValueCriteria();
		given(pdtCriteriaFactory.priceValueCriteriaFromOrderEntry(serviceEntry)).willReturn(standard);
		given(findPriceValueInfoStrategy.getPDTValues(any(PriceValueInfoCriteria.class)))
				.willReturn(Collections.singletonList(new PriceValue("EUR", 120.0d, false)));

		hook.findCustomBasePrice(serviceEntry, null);

		final ArgumentCaptor<PriceValueInfoCriteria> captor = ArgumentCaptor.forClass(PriceValueInfoCriteria.class);
		verify(findPriceValueInfoStrategy).getPDTValues(captor.capture());
		final PriceValueInfoCriteria passed = captor.getValue();
		assertEquals(PDTCriteriaTarget.VALUE, passed.getPDTCriteriaTarget());
		assertSame(group, passed.getProductGroup());
		assertSame("the service product is priced, not the linked physical product", installation, passed.getProduct());
		assertSame(user, passed.getUser());
		assertSame(userGroup, passed.getUserGroup());
		assertSame(currency, passed.getCurrency());
		assertEquals(2L, passed.getQuantity());
		assertSame(unit, passed.getUnit());
		assertEquals(date, passed.getDate());
		assertEquals(Boolean.FALSE, passed.isNet());
	}

	// --- failure paths: never fall back to defaultPrice -----------------------------------------------------------

	@Test
	public void shouldFailWhenThereIsNoLinkedProductEntry()
	{
		given(serviceEntryGroupService.getProductEntry(serviceEntry)).willReturn(Optional.empty());

		assertFailsWithServicePriceNotFound(DEFAULT_PRICE);
		assertFailsWithServicePriceNotFound(null);
		verifyNoInteractions(productServiceLookupService, findPriceValueInfoStrategy);
	}

	@Test
	public void shouldFailWhenTheLinkedProductHasNoCondition()
	{
		givenLinkedProductWithCondition(null);

		assertFailsWithServicePriceNotFound(DEFAULT_PRICE);
		assertFailsWithServicePriceNotFound(null);
		verifyNoInteractions(findPriceValueInfoStrategy);
	}

	@Test
	public void shouldFailWhenThereIsNoPriceGroup()
	{
		givenLinkedProductWithCondition(ServicePriceCondition.HIGH);
		given(productServiceLookupService.getServicePriceGroup(installation, ServicePriceCondition.HIGH))
				.willReturn(Optional.empty());

		assertFailsWithServicePriceNotFound(DEFAULT_PRICE);
		assertFailsWithServicePriceNotFound(null);
		verifyNoInteractions(findPriceValueInfoStrategy);
	}

	@Test
	public void shouldFailWhenNoPriceRowMatches() throws CalculationException
	{
		givenLinkedProductWithCondition(ServicePriceCondition.LOW);
		givenGroupFor(ServicePriceCondition.LOW);
		givenStandardCriteria();
		given(findPriceValueInfoStrategy.getPDTValues(any(PriceValueInfoCriteria.class)))
				.willReturn(Collections.emptyList());

		assertFailsWithServicePriceNotFound(DEFAULT_PRICE);
		assertFailsWithServicePriceNotFound(null);
	}

	@Test
	public void shouldFailWhenThePriceStrategyReturnsNull() throws CalculationException
	{
		givenLinkedProductWithCondition(ServicePriceCondition.LOW);
		givenGroupFor(ServicePriceCondition.LOW);
		givenStandardCriteria();
		given(findPriceValueInfoStrategy.getPDTValues(any(PriceValueInfoCriteria.class))).willReturn(null);

		assertFailsWithServicePriceNotFound(DEFAULT_PRICE);
	}

	@Test
	public void shouldFailWhenThePriceLookupThrows() throws CalculationException
	{
		givenLinkedProductWithCondition(ServicePriceCondition.LOW);
		givenGroupFor(ServicePriceCondition.LOW);
		givenStandardCriteria();
		willThrow(new CalculationException("boom")).given(findPriceValueInfoStrategy)
				.getPDTValues(any(PriceValueInfoCriteria.class));

		assertFailsWithServicePriceNotFound(DEFAULT_PRICE);
	}

	@Test
	public void shouldFailWhenTheCriteriaCannotBeBuilt() throws CalculationException
	{
		givenLinkedProductWithCondition(ServicePriceCondition.LOW);
		givenGroupFor(ServicePriceCondition.LOW);
		willThrow(new CalculationException("no currency")).given(pdtCriteriaFactory).priceValueCriteriaFromOrderEntry(serviceEntry);

		assertFailsWithServicePriceNotFound(DEFAULT_PRICE);
		verifyNoInteractions(findPriceValueInfoStrategy);
	}

	@Test(expected = ServicePriceNotFoundException.class)
	public void shouldThrowAnUncheckedServicePriceNotFoundException()
	{
		given(serviceEntryGroupService.getProductEntry(serviceEntry)).willReturn(Optional.empty());

		hook.findCustomBasePrice(serviceEntry, DEFAULT_PRICE);
	}
}
