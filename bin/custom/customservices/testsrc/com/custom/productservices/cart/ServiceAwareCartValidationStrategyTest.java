/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.productservices.cart;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commerceservices.order.CommerceCartModification;
import de.hybris.platform.commerceservices.order.CommerceCartModificationStatus;
import de.hybris.platform.commerceservices.stock.CommerceStockService;
import de.hybris.platform.core.model.order.CartEntryModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.order.CartService;
import de.hybris.platform.product.ProductService;
import de.hybris.platform.servicelayer.exceptions.UnknownIdentifierException;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.user.UserService;
import de.hybris.platform.store.services.BaseStoreService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.custom.core.model.ServiceProductModel;


/**
 * NET-8943: on {@code /cart/checkout} a service entry is valid without any product lookup or stock check (the platform
 * would remove it, service products being hidden from storefront lookups); every other entry goes through the platform.
 */
@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class ServiceAwareCartValidationStrategyTest
{
	@Mock
	private ProductService productService;
	@Mock
	private CommerceStockService commerceStockService;
	@Mock
	private ModelService modelService;
	@Mock
	private BaseStoreService baseStoreService;
	@Mock
	private CartService cartService;
	@Mock
	private UserService userService;

	private ServiceAwareCartValidationStrategy strategy;

	private final CartModel cart = new CartModel();

	@Before
	public void setUp()
	{
		strategy = new ServiceAwareCartValidationStrategy();
		strategy.setProductService(productService);
		strategy.setCommerceStockService(commerceStockService);
		strategy.setModelService(modelService);
		strategy.setBaseStoreService(baseStoreService);
		strategy.setCartService(cartService);
		strategy.setUserService(userService);
	}

	private static CartEntryModel entry(final ProductModel product, final long quantity)
	{
		final CartEntryModel entry = new CartEntryModel();
		entry.setProduct(product);
		entry.setQuantity(Long.valueOf(quantity));
		return entry;
	}

	@Test
	public void shouldAcceptAServiceEntryWithoutTouchingThePlatformCollaborators()
	{
		final ServiceProductModel installation = new ServiceProductModel();
		installation.setCode("SRV_INSTALL");
		final CartEntryModel serviceEntry = entry(installation, 2);

		final CommerceCartModification modification = strategy.validateCartEntry(cart, serviceEntry);

		assertEquals(CommerceCartModificationStatus.SUCCESS, modification.getStatusCode());
		assertEquals(2L, modification.getQuantityAdded());
		assertSame(serviceEntry, modification.getEntry());
		verifyNoInteractions(productService, commerceStockService, modelService, baseStoreService, cartService, userService);
	}

	@Test
	public void shouldDelegateAPlainProductEntryToThePlatform()
	{
		final ProductModel dishwasher = new ProductModel();
		dishwasher.setCode("DISHWASHER");
		final CartEntryModel productEntry = entry(dishwasher, 1);
		given(productService.getProductForCode("DISHWASHER")).willThrow(new UnknownIdentifierException("gone"));

		final CommerceCartModification modification = strategy.validateCartEntry(cart, productEntry);

		// platform DefaultCartValidationStrategy: unknown product -> entry removed, UNAVAILABLE
		assertEquals(CommerceCartModificationStatus.UNAVAILABLE, modification.getStatusCode());
		assertEquals(0L, modification.getQuantityAdded());
		verify(modelService).remove(productEntry);
		verify(modelService).refresh(cart);
	}
}
