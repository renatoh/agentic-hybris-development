/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.productservices.cart;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.basecommerce.enums.StockLevelStatus;
import de.hybris.platform.commerceservices.stock.strategies.CommerceAvailabilityCalculationStrategy;
import de.hybris.platform.commerceservices.stock.strategies.WarehouseSelectionStrategy;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.ordersplitting.model.StockLevelModel;
import de.hybris.platform.ordersplitting.model.WarehouseModel;
import de.hybris.platform.stock.StockService;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.storelocator.model.PointOfServiceModel;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.custom.core.model.ServiceProductModel;


/**
 * NET-8943 &sect;5.5, AC7: a service product is always "force in stock" (null level / INSTOCK) without looking at any
 * warehouse; every other product goes through the platform implementation.
 */
@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class ServiceAwareCommerceStockServiceTest
{
	@Mock
	private StockService stockService;
	@Mock
	private CommerceAvailabilityCalculationStrategy commerceStockLevelCalculationStrategy;
	@Mock
	private WarehouseSelectionStrategy warehouseSelectionStrategy;

	private ServiceAwareCommerceStockService stockServiceUnderTest;

	private final ServiceProductModel installation = new ServiceProductModel();
	private final ProductModel dishwasher = new ProductModel();
	private final BaseStoreModel store = new BaseStoreModel();
	private final PointOfServiceModel pos = new PointOfServiceModel();
	private final WarehouseModel warehouse = new WarehouseModel();
	private final Collection<StockLevelModel> levels = Collections.singletonList(new StockLevelModel());

	@Before
	public void setUp()
	{
		stockServiceUnderTest = new ServiceAwareCommerceStockService();
		stockServiceUnderTest.setStockService(stockService);
		stockServiceUnderTest.setCommerceStockLevelCalculationStrategy(commerceStockLevelCalculationStrategy);
		stockServiceUnderTest.setWarehouseSelectionStrategy(warehouseSelectionStrategy);
		pos.setWarehouses(Collections.singletonList(warehouse));
	}

	@Test
	public void shouldForceAServiceInStockForABaseStore()
	{
		assertNull(stockServiceUnderTest.getStockLevelForProductAndBaseStore(installation, store));
		assertEquals(StockLevelStatus.INSTOCK, stockServiceUnderTest.getStockLevelStatusForProductAndBaseStore(installation, store));
		verifyNoInteractions(stockService, commerceStockLevelCalculationStrategy, warehouseSelectionStrategy);
	}

	@Test
	public void shouldForceAServiceInStockForAPointOfService()
	{
		assertNull(stockServiceUnderTest.getStockLevelForProductAndPointOfService(installation, pos));
		assertEquals(StockLevelStatus.INSTOCK,
				stockServiceUnderTest.getStockLevelStatusForProductAndPointOfService(installation, pos));
		verifyNoInteractions(stockService, commerceStockLevelCalculationStrategy, warehouseSelectionStrategy);
	}

	@Test
	public void shouldForceAServiceInStockEvenWithoutAnyWarehouse()
	{
		final PointOfServiceModel noWarehouses = new PointOfServiceModel();
		noWarehouses.setWarehouses(Collections.emptyList());

		assertEquals(StockLevelStatus.INSTOCK,
				stockServiceUnderTest.getStockLevelStatusForProductAndPointOfService(installation, noWarehouses));
	}

	@Test
	public void shouldDelegateTheLevelOfANormalProductForABaseStore()
	{
		final List<WarehouseModel> warehouses = Collections.singletonList(warehouse);
		given(warehouseSelectionStrategy.getWarehousesForBaseStore(store)).willReturn(warehouses);
		given(stockService.getStockLevels(dishwasher, warehouses)).willReturn(levels);
		given(commerceStockLevelCalculationStrategy.calculateAvailability(levels)).willReturn(Long.valueOf(99L));

		assertEquals(Long.valueOf(99L), stockServiceUnderTest.getStockLevelForProductAndBaseStore(dishwasher, store));
	}

	@Test
	public void shouldDelegateTheStatusOfANormalProductForABaseStore()
	{
		final List<WarehouseModel> warehouses = Collections.singletonList(warehouse);
		given(warehouseSelectionStrategy.getWarehousesForBaseStore(store)).willReturn(warehouses);
		given(stockService.getProductStatus(dishwasher, warehouses)).willReturn(StockLevelStatus.OUTOFSTOCK);

		assertEquals(StockLevelStatus.OUTOFSTOCK, stockServiceUnderTest.getStockLevelStatusForProductAndBaseStore(dishwasher, store));
	}

	@Test
	public void shouldDelegateTheLevelOfANormalProductForAPointOfService()
	{
		given(stockService.getStockLevels(dishwasher, pos.getWarehouses())).willReturn(levels);
		given(commerceStockLevelCalculationStrategy.calculateAvailability(levels)).willReturn(Long.valueOf(0L));

		assertEquals(Long.valueOf(0L), stockServiceUnderTest.getStockLevelForProductAndPointOfService(dishwasher, pos));
	}

	@Test
	public void shouldDelegateTheStatusOfANormalProductForAPointOfService()
	{
		given(stockService.getProductStatus(dishwasher, pos.getWarehouses())).willReturn(StockLevelStatus.LOWSTOCK);

		assertEquals(StockLevelStatus.LOWSTOCK,
				stockServiceUnderTest.getStockLevelStatusForProductAndPointOfService(dishwasher, pos));
	}

	@Test(expected = IllegalArgumentException.class)
	public void shouldKeepThePlatformNullCheckForANormalProduct()
	{
		stockServiceUnderTest.getStockLevelForProductAndBaseStore(dishwasher, null);
	}
}
