/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.facades.savedforlater.impl;

import de.hybris.platform.commercefacades.order.CartFacade;
import de.hybris.platform.commercefacades.product.ProductFacade;
import de.hybris.platform.commercefacades.product.ProductOption;
import de.hybris.platform.commerceservices.order.CommerceCartModificationException;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.order.CartService;
import de.hybris.platform.servicelayer.user.UserService;

import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Required;

import com.custom.model.SavedForLaterEntryModel;
import com.custom.facades.savedforlater.SavedForLaterFacade;
import com.custom.facades.savedforlater.data.SavedForLaterEntryData;
import com.custom.savedforlater.service.SavedForLaterService;


public class DefaultSavedForLaterFacade implements SavedForLaterFacade
{
	private static final List<ProductOption> PRODUCT_OPTIONS = Arrays.asList(ProductOption.BASIC, ProductOption.PRICE);

	private SavedForLaterService savedForLaterService;
	private CartFacade cartFacade;
	private CartService cartService;
	private ProductFacade productFacade;
	private UserService userService;

	@Override
	public void saveCartEntryForLater(final long entryNumber)
	{
		final AbstractOrderEntryModel cartEntry = findCartEntry(entryNumber);
		final ProductModel product = cartEntry.getProduct();
		final long quantity = cartEntry.getQuantity();

		try
		{
			getCartFacade().updateCartEntry(entryNumber, 0);
		}
		catch (final CommerceCartModificationException e)
		{
			throw new IllegalStateException("Could not remove cart entry " + entryNumber + " for save-for-later", e);
		}

		getSavedForLaterService().saveForLater(getCurrentCustomer(), product, quantity);
	}

	@Override
	public List<SavedForLaterEntryData> getSavedForLaterEntries()
	{
		return getSavedForLaterService().getSavedItems(getCurrentCustomer())
				.stream().map(this::toData).collect(Collectors.toList());
	}

	@Override
	public void moveToCart(final String productCode)
	{
		final SavedForLaterEntryModel entry = findSavedEntry(productCode);
		getSavedForLaterService().moveToCart(getCurrentCustomer(), entry);
	}

	@Override
	public void removeSavedItem(final String productCode)
	{
		final SavedForLaterEntryModel entry = findSavedEntry(productCode);
		getSavedForLaterService().removeSavedItem(getCurrentCustomer(), entry);
	}

	protected AbstractOrderEntryModel findCartEntry(final long entryNumber)
	{
		final CartModel cart = getCartService().getSessionCart();
		return cart.getEntries().stream().filter(entry -> entry.getEntryNumber() == entryNumber).findFirst()
				.orElseThrow(() -> new NoSuchElementException("No cart entry with number " + entryNumber));
	}

	protected SavedForLaterEntryModel findSavedEntry(final String productCode)
	{
		return getSavedForLaterService().getSavedItems(getCurrentCustomer())
				.stream().filter(entry -> productCode.equals(entry.getProduct().getCode())).findFirst()
				.orElseThrow(() -> new NoSuchElementException("No saved-for-later entry for product " + productCode));
	}

	protected SavedForLaterEntryData toData(final SavedForLaterEntryModel entry)
	{
		final SavedForLaterEntryData data = new SavedForLaterEntryData();
		data.setProductCode(entry.getProduct().getCode());
		data.setProduct(getProductFacade().getProductForCodeAndOptions(entry.getProduct().getCode(), PRODUCT_OPTIONS));
		data.setQuantity(entry.getQuantity());
		return data;
	}

	protected CustomerModel getCurrentCustomer()
	{
		return (CustomerModel) getUserService().getCurrentUser();
	}

	protected SavedForLaterService getSavedForLaterService()
	{
		return savedForLaterService;
	}

	@Required
	public void setSavedForLaterService(final SavedForLaterService savedForLaterService)
	{
		this.savedForLaterService = savedForLaterService;
	}

	protected CartFacade getCartFacade()
	{
		return cartFacade;
	}

	@Required
	public void setCartFacade(final CartFacade cartFacade)
	{
		this.cartFacade = cartFacade;
	}

	protected CartService getCartService()
	{
		return cartService;
	}

	@Required
	public void setCartService(final CartService cartService)
	{
		this.cartService = cartService;
	}

	protected ProductFacade getProductFacade()
	{
		return productFacade;
	}

	@Required
	public void setProductFacade(final ProductFacade productFacade)
	{
		this.productFacade = productFacade;
	}

	protected UserService getUserService()
	{
		return userService;
	}

	@Required
	public void setUserService(final UserService userService)
	{
		this.userService = userService;
	}
}
