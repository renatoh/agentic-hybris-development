/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.facades.productcomparison;

import java.util.List;
import java.util.Optional;

import com.custom.facades.productcomparison.data.ProductComparisonListData;
import com.custom.facades.productcomparison.data.ProductComparisonTableData;


/**
 * Presentation-layer entry point for the product comparison feature (NET-8940 section 5.4),
 * fronting {@code ProductComparisonService} (customservices) for the storefront controllers.
 */
public interface ProductComparisonFacade
{
	/**
	 * Adds the given product to the shopper's session comparison lists.
	 *
	 * @param productCode the product to add
	 * @return {@code true} if the product was added (or already was in its list), {@code false} if
	 *         it has no grouping category at the configured depth and could not be added
	 */
	boolean addToCompare(String productCode);

	/**
	 * Removes a product from a list, deleting the list if it becomes empty.
	 */
	void removeFromCompare(String listId, String productCode);

	/**
	 * @return {@code true} if the session has at least one comparison list - drives whether the
	 *         header icon is shown enabled (acceptance criterion 5)
	 */
	boolean hasLists();

	/**
	 * @return every current list of the session, for the comparison page's dropdown
	 */
	List<ProductComparisonListData> getLists();

	/**
	 * @param listId a list id from {@link #getLists()}, or blank/{@code null} to default to the most
	 *               recently touched list (NET-8940 section 8, open question 3)
	 * @return the comparison table, or {@link Optional#empty()} if no such list exists (including
	 *         when the session has no lists at all)
	 */
	Optional<ProductComparisonTableData> getComparisonTable(String listId);
}
