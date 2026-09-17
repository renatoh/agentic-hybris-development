/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.savedforlater.service;

import de.hybris.platform.commerceservices.order.CommerceCartModificationException;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.store.BaseStoreModel;

import java.util.List;

import com.custom.model.SavedForLaterEntryModel;


/**
 * Persistent per-customer "save for later" list (NET-8941). A dedicated item type, not a hidden
 * second {@code CartModel} per customer (NET-8941 section 5.1) - moving an item back into the cart
 * still goes through the platform's normal add-to-cart path, so stock/price validation is the
 * platform's own code, not reimplemented.
 */
public interface SavedForLaterService
{
	/**
	 * Creates a new saved entry for the given customer/product, or increments the quantity of the
	 * existing entry if that product is already saved for this customer (NET-8941 section 4,
	 * "merge on duplicate" - acceptance criterion 2).
	 *
	 * @param customer the owning customer
	 * @param product  the product to save
	 * @param quantity the quantity that was in the cart when saved
	 */
	void saveForLater(CustomerModel customer, ProductModel product, long quantity);

	/**
	 * @param customer  the owning customer
	 * @param baseStore filters the result to entries whose product's {@code catalogVersion} belongs
	 *                  to one of this store's catalogs (NET-8941 section 5.3) - a query-time filter,
	 *                  not part of the entry's stored identity
	 * @return the customer's saved entries visible for that store
	 */
	List<SavedForLaterEntryModel> getSavedItems(CustomerModel customer, BaseStoreModel baseStore);

	/**
	 * Moves a saved entry back into the customer's cart via the platform's normal add-to-cart path
	 * (merges with an existing cart entry of the same product, standard cart behaviour) and then
	 * deletes the persistent entry. If the add-to-cart call fails (e.g. the product is no longer
	 * purchasable or out of stock), the entry is left untouched in the saved list (NET-8941
	 * acceptance criterion 5).
	 *
	 * @param customer the owning customer
	 * @param entry    the saved entry to move back into the cart
	 * @throws CommerceCartModificationException if the platform's add-to-cart call fails
	 */
	void moveToCart(CustomerModel customer, SavedForLaterEntryModel entry) throws CommerceCartModificationException;

	/**
	 * Deletes a saved entry without touching the cart (NET-8941 acceptance criterion 6).
	 *
	 * @param customer the owning customer
	 * @param entry    the saved entry to delete
	 */
	void removeSavedItem(CustomerModel customer, SavedForLaterEntryModel entry);
}
