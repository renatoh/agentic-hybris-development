/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.productcomparison.constants;

/**
 * Constants for the session-scoped product comparison feature (NET-8940).
 */
public interface ProductComparisonConstants
{
	/**
	 * Property read via {@code ConfigurationService} that defines the category-tree depth (counted
	 * from the first real navigation category below the catalog's synthetic root, which is
	 * excluded, so depth 1 is that first real category)
	 * used to resolve a product's grouping category. Never hardcode this depth in resolution code -
	 * see NET-8940 section 5.2 / acceptance criterion 12.
	 */
	String GROUPING_CATEGORY_DEPTH_PROPERTY = "customservices.productcomparison.grouping.category.depth";

	int DEFAULT_GROUPING_CATEGORY_DEPTH = 3;

	/**
	 * HTTP session attribute holding the shopper's current {@code List<ProductComparisonList>}.
	 * Session-scoped only - never persisted (NET-8940 section 5.1).
	 */
	String SESSION_ATTRIBUTE_COMPARISON_LISTS = "customservices.productcomparison.lists";
}
