/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.savedforlater.service.impl;

import static de.hybris.platform.servicelayer.util.ServicesUtil.validateParameterNotNullStandardMessage;

import de.hybris.platform.commerceservices.order.CommerceCartModification;
import de.hybris.platform.commerceservices.order.CommerceCartModificationException;
import de.hybris.platform.commerceservices.order.CommerceCartModificationStatus;
import de.hybris.platform.commerceservices.order.CommerceCartService;
import de.hybris.platform.commerceservices.service.data.CommerceCartParameter;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.order.CartService;
import de.hybris.platform.product.ProductService;
import de.hybris.platform.servicelayer.exceptions.AmbiguousIdentifierException;
import de.hybris.platform.servicelayer.exceptions.UnknownIdentifierException;
import de.hybris.platform.servicelayer.model.ModelService;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.custom.model.SavedForLaterEntryModel;
import com.custom.savedforlater.service.SavedForLaterService;


public class DefaultSavedForLaterService implements SavedForLaterService
{
	private ModelService modelService;
	private CartService cartService;
	private CommerceCartService commerceCartService;
	private ProductService productService;

	@Override
	public void saveForLater(final CustomerModel customer, final ProductModel product, final long quantity)
	{
		validateParameterNotNullStandardMessage("customer", customer);
		validateParameterNotNullStandardMessage("product", product);

		final Optional<SavedForLaterEntryModel> existingEntry = findEntryForProduct(customer, product);
		if (existingEntry.isPresent())
		{
			final SavedForLaterEntryModel entry = existingEntry.get();
			entry.setQuantity(entry.getQuantity() + quantity);
			getModelService().save(entry);
		}
		else
		{
			final SavedForLaterEntryModel entry = getModelService().create(SavedForLaterEntryModel.class);
			entry.setCustomer(customer);
			entry.setProduct(product);
			entry.setQuantity(quantity);
			entry.setDateSaved(new Date());
			getModelService().save(entry);
		}
	}

	@Override
	public List<SavedForLaterEntryModel> getSavedItems(final CustomerModel customer)
	{
		validateParameterNotNullStandardMessage("customer", customer);

		return getEntries(customer).stream()
				.filter(entry -> isVisibleInCurrentCatalog(entry.getProduct()))
				.collect(Collectors.toList());
	}

	@Override
	public void moveToCart(final CustomerModel customer, final SavedForLaterEntryModel entry)
	{
		validateParameterNotNullStandardMessage("customer", customer);
		validateParameterNotNullStandardMessage("entry", entry);

		final CartModel cart = getCartService().getSessionCart();

		final CommerceCartParameter parameter = new CommerceCartParameter();
		parameter.setEnableHooks(true);
		parameter.setCart(cart);
		parameter.setProduct(entry.getProduct());
		parameter.setQuantity(entry.getQuantity());

		final CommerceCartModification modification;
		try
		{
			modification = getCommerceCartService().addToCart(parameter);
		}
		catch (final CommerceCartModificationException e)
		{
			throw new IllegalStateException("Could not move saved-for-later entry to cart", e);
		}

		// DefaultCommerceAddToCartStrategy does not throw for an ordinary stock/availability failure
		// (e.g. now out of stock) - it returns a non-SUCCESS status with quantityAdded=0 instead. The
		// entry must stay in the saved list for that case too, not just when addToCart throws
		// (NET-8941 acceptance criterion 5).
		if (!CommerceCartModificationStatus.SUCCESS.equals(modification.getStatusCode())
				|| modification.getQuantityAdded() < entry.getQuantity())
		{
			throw new IllegalStateException(
					"Could not move saved-for-later entry to cart, status: " + modification.getStatusCode());
		}

		getModelService().remove(entry);
	}

	/**
	 * @param product a saved entry's product
	 * @return {@code true} if the product resolves via {@code ProductService.getProductForCode},
	 *         which scopes to the current session's own catalog automatically (NET-8941 section
	 *         5.3) - no explicit {@code BaseStoreModel}/{@code CatalogVersionModel} lookup needed in
	 *         this request-scoped storefront code (PR review, see the sap-commerce-cloud skill)
	 */
	protected boolean isVisibleInCurrentCatalog(final ProductModel product)
	{
		try
		{
			getProductService().getProductForCode(product.getCode());
			return true;
		}
		catch (final UnknownIdentifierException e)
		{
			return false;
		}
		catch (final AmbiguousIdentifierException e)
		{
			return true;
		}
	}

	@Override
	public void removeSavedItem(final CustomerModel customer, final SavedForLaterEntryModel entry)
	{
		validateParameterNotNullStandardMessage("customer", customer);
		validateParameterNotNullStandardMessage("entry", entry);

		getModelService().remove(entry);
	}

	protected Optional<SavedForLaterEntryModel> findEntryForProduct(final CustomerModel customer, final ProductModel product)
	{
		return getEntries(customer).stream().filter(entry -> product.equals(entry.getProduct())).findFirst();
	}

	protected List<SavedForLaterEntryModel> getEntries(final CustomerModel customer)
	{
		final List<SavedForLaterEntryModel> entries = customer.getSavedForLaterEntries();
		return entries == null ? List.of() : entries;
	}

	protected ModelService getModelService()
	{
		return modelService;
	}

	public void setModelService(final ModelService modelService)
	{
		this.modelService = modelService;
	}

	protected CartService getCartService()
	{
		return cartService;
	}

	public void setCartService(final CartService cartService)
	{
		this.cartService = cartService;
	}

	protected CommerceCartService getCommerceCartService()
	{
		return commerceCartService;
	}

	public void setCommerceCartService(final CommerceCartService commerceCartService)
	{
		this.commerceCartService = commerceCartService;
	}

	protected ProductService getProductService()
	{
		return productService;
	}

	public void setProductService(final ProductService productService)
	{
		this.productService = productService;
	}
}
