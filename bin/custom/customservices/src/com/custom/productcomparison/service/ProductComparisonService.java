/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.productcomparison.service;

import de.hybris.platform.core.model.product.ProductModel;

import java.util.List;
import java.util.Optional;

import com.custom.productcomparison.model.ProductComparisonList;


/**
 * Session-scoped product comparison lists (NET-8940 section 5.1). No persistence, no new item
 * type - state lives purely in the HTTP session via {@code SessionService}, exactly like the
 * guest cart.
 */
public interface ProductComparisonService
{
	/**
	 * Adds a product to the comparison list matching its grouping category, creating that list if
	 * none exists yet for the session. Adding a product already present in the target list is a
	 * no-op (acceptance criterion 4).
	 *
	 * @param product the product to add
	 * @return the list the product was added to (or already was in), or {@link Optional#empty()} if
	 *         the product has no valid grouping category at all and could not be added - a category
	 *         path shallower than the configured depth is not this case, since it falls back to the
	 *         deepest available category and the product is still added successfully (NET-8940
	 *         section 8.4)
	 */
	Optional<ProductComparisonList> addProduct(ProductModel product);

	/**
	 * @return all of the session's current comparison lists, in no particular guaranteed order
	 */
	List<ProductComparisonList> getLists();

	/**
	 * @param listId a {@link ProductComparisonList#getId()} value
	 * @return the matching list, or {@link Optional#empty()} if no such list exists in the session
	 */
	Optional<ProductComparisonList> getList(String listId);

	/**
	 * @return the list most recently added/removed to in this session, used as the comparison page's
	 *         default selection (NET-8940 section 8, open question 3)
	 */
	Optional<ProductComparisonList> getMostRecentlyTouchedList();

	/**
	 * Removes a product from a list. A list that drops to zero products is removed entirely
	 * (acceptance criterion 8).
	 *
	 * @param listId  a {@link ProductComparisonList#getId()} value
	 * @param product the product to remove
	 */
	void removeProduct(String listId, ProductModel product);

	/**
	 * Removes the entire list in one step, regardless of how many products it holds (acceptance
	 * criterion 8a). A {@code listId} that does not match any current list is a no-op.
	 *
	 * @param listId a {@link ProductComparisonList#getId()} value
	 */
	void deleteList(String listId);
}
