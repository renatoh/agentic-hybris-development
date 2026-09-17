/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.productcomparison.service;

import de.hybris.platform.category.model.CategoryModel;
import de.hybris.platform.core.model.product.ProductModel;

import java.util.Optional;


/**
 * Resolves the "grouping category" (NET-8940 section 4/5.2) that decides which comparison list a
 * product belongs to.
 */
public interface ProductComparisonGroupingService
{
	/**
	 * Resolves the category at the configured depth
	 * ({@code customservices.productcomparison.grouping.category.depth}, counted from the first
	 * real navigation category below the catalog's synthetic root - which is excluded - so depth 1
	 * is that first real category, e.g. "Cameras") along the
	 * product's category path - or, if that path is shallower than the configured depth, the
	 * deepest category actually on the path instead (NET-8940 section 8.4).
	 * <p>
	 * A product can carry several assigned category paths; the first path that resolves a category
	 * (at the configured depth, or via the shallower-path fallback above) is used. An empty result
	 * means the product has no valid assigned category path at all - no real navigation category
	 * resolves - and it is not available for comparison.
	 *
	 * @param product the product to resolve the grouping category for
	 * @return the grouping category, or {@link Optional#empty()} if the product has no valid assigned
	 *         category path at all
	 */
	Optional<CategoryModel> resolveGroupingCategory(ProductModel product);
}
