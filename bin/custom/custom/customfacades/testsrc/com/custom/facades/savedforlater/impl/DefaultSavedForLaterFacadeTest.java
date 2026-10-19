/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.facades.savedforlater.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commercefacades.order.CartFacade;
import de.hybris.platform.commercefacades.product.ProductFacade;
import de.hybris.platform.commerceservices.order.CommerceCartModificationException;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.CartEntryModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.order.CartService;
import de.hybris.platform.servicelayer.user.UserService;

import java.util.Arrays;
import java.util.NoSuchElementException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.custom.core.model.ServiceProductModel;
import com.custom.productservices.service.ServiceEntryGroupService;
import com.custom.savedforlater.service.SavedForLaterService;


/**
 * NET-8941 save-for-later, NET-8943: a product cart line is moved from the cart to the customer's saved-for-later list;
 * a service line (warranty, installation) belongs to its product line and cannot be saved on its own — it is treated like
 * an unknown entry number.
 */
@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class DefaultSavedForLaterFacadeTest
{
	private static final long PRODUCT_ENTRY = 0;
	private static final long SERVICE_ENTRY = 1;
	private static final long UNKNOWN_ENTRY = 7;

	@Mock
	private SavedForLaterService savedForLaterService;
	@Mock
	private CartFacade cartFacade;
	@Mock
	private CartService cartService;
	@Mock
	private ProductFacade productFacade;
	@Mock
	private UserService userService;
	@Mock
	private ServiceEntryGroupService serviceEntryGroupService;

	private DefaultSavedForLaterFacade facade;

	private final CustomerModel customer = new CustomerModel();
	private final ProductModel dishwasher = new ProductModel();
	private final CartEntryModel productEntry = entry(dishwasher, PRODUCT_ENTRY, 2);
	private final CartEntryModel serviceEntry = entry(new ServiceProductModel(), SERVICE_ENTRY, 2);

	@Before
	public void setUp()
	{
		facade = new DefaultSavedForLaterFacade();
		facade.setSavedForLaterService(savedForLaterService);
		facade.setCartFacade(cartFacade);
		facade.setCartService(cartService);
		facade.setProductFacade(productFacade);
		facade.setUserService(userService);
		facade.setServiceEntryGroupService(serviceEntryGroupService);

		final CartModel cart = new CartModel();
		cart.setEntries(Arrays.<AbstractOrderEntryModel> asList(productEntry, serviceEntry));
		given(cartService.getSessionCart()).willReturn(cart);
	}

	private static CartEntryModel entry(final ProductModel product, final long entryNumber, final long quantity)
	{
		final CartEntryModel entry = new CartEntryModel();
		entry.setProduct(product);
		entry.setEntryNumber(Integer.valueOf((int) entryNumber));
		entry.setQuantity(Long.valueOf(quantity));
		return entry;
	}

	@Test
	public void shouldRemoveTheProductEntryFromTheCartAndSaveItForLater() throws Exception
	{
		given(Boolean.valueOf(serviceEntryGroupService.isServiceEntry(productEntry))).willReturn(Boolean.FALSE);
		given(userService.getCurrentUser()).willReturn(customer);

		facade.saveCartEntryForLater(PRODUCT_ENTRY);

		final InOrder order = inOrder(cartFacade, savedForLaterService);
		order.verify(cartFacade).updateCartEntry(PRODUCT_ENTRY, 0);
		order.verify(savedForLaterService).saveForLater(customer, dishwasher, 2L);
	}

	@Test
	public void shouldRefuseToSaveAServiceEntryForLater() throws Exception
	{
		given(Boolean.valueOf(serviceEntryGroupService.isServiceEntry(serviceEntry))).willReturn(Boolean.TRUE);

		try
		{
			facade.saveCartEntryForLater(SERVICE_ENTRY);
			fail("expected NoSuchElementException for a service entry");
		}
		catch (final NoSuchElementException e)
		{
			assertEquals("No product cart entry with number " + SERVICE_ENTRY, e.getMessage());
		}
		verify(cartFacade, never()).updateCartEntry(anyLong(), anyLong());
		verifyNoInteractions(savedForLaterService);
	}

	@Test
	public void shouldRejectAnUnknownEntryNumber() throws Exception
	{
		try
		{
			facade.saveCartEntryForLater(UNKNOWN_ENTRY);
			fail("expected NoSuchElementException for an unknown entry number");
		}
		catch (final NoSuchElementException e)
		{
			assertEquals("No product cart entry with number " + UNKNOWN_ENTRY, e.getMessage());
		}
		verify(cartFacade, never()).updateCartEntry(anyLong(), anyLong());
		verifyNoInteractions(savedForLaterService);
	}

	@Test
	public void shouldNotSaveForLaterWhenTheCartEntryCannotBeRemoved() throws Exception
	{
		given(Boolean.valueOf(serviceEntryGroupService.isServiceEntry(productEntry))).willReturn(Boolean.FALSE);
		given(cartFacade.updateCartEntry(PRODUCT_ENTRY, 0)).willThrow(new CommerceCartModificationException("locked"));

		try
		{
			facade.saveCartEntryForLater(PRODUCT_ENTRY);
			fail("expected IllegalStateException when the cart entry cannot be removed");
		}
		catch (final IllegalStateException e)
		{
			assertEquals(CommerceCartModificationException.class, e.getCause().getClass());
		}
		verify(savedForLaterService, never()).saveForLater(any(), any(), anyLong());
	}
}
