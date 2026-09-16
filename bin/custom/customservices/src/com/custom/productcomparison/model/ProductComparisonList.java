/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.productcomparison.model;

import de.hybris.platform.category.model.CategoryModel;
import de.hybris.platform.core.model.product.ProductModel;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;


/**
 * A single session-held product comparison list (NET-8940 section 5.1). Not an item type - pure
 * session/service-layer state, held as an HTTP session attribute value, never persisted.
 * <p>
 * {@code products} is never mutated in place: a {@code ProductComparisonList} round-trips through
 * {@code SessionService} as part of the outer lists collection and can come back backed by an
 * unmodifiable snapshot (see {@code DefaultProductComparisonService.getOrCreateLists()}), so every
 * mutation here copies into a fresh {@code ArrayList} and swaps the field instead of assuming the
 * current list is mutable.
 */
public class ProductComparisonList implements Serializable
{
	private final CategoryModel groupingCategory;
	private List<ProductModel> products = new ArrayList<>();
	private long lastTouched;

	public ProductComparisonList(final CategoryModel groupingCategory)
	{
		this.groupingCategory = groupingCategory;
		touch();
	}

	/**
	 * Identifier used to select this list from the storefront (e.g. the comparison page dropdown
	 * and query parameter) - the grouping category's PK, unique per session and stable across the
	 * whole session lifetime.
	 */
	public String getId()
	{
		return groupingCategory.getPk().toString();
	}

	public CategoryModel getGroupingCategory()
	{
		return groupingCategory;
	}

	public List<ProductModel> getProducts()
	{
		return products;
	}

	public boolean containsProduct(final ProductModel product)
	{
		return products.stream().anyMatch(existing -> existing.getPk().equals(product.getPk()));
	}

	/**
	 * Appends the product unless it is already in this list (acceptance criterion 4).
	 */
	public void addProductIfAbsent(final ProductModel product)
	{
		if (!containsProduct(product))
		{
			final List<ProductModel> copy = new ArrayList<>(products);
			copy.add(product);
			products = copy;
		}
	}

	/**
	 * Removes the product, if present.
	 */
	public void removeProduct(final ProductModel product)
	{
		final List<ProductModel> copy = new ArrayList<>(products);
		copy.removeIf(existing -> existing.getPk().equals(product.getPk()));
		products = copy;
	}

	public long getLastTouched()
	{
		return lastTouched;
	}

	public void touch()
	{
		this.lastTouched = System.currentTimeMillis();
	}

	public boolean isEmpty()
	{
		return products.isEmpty();
	}
}
