/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.productcomparison.service.impl;

import static de.hybris.platform.servicelayer.util.ServicesUtil.validateParameterNotNull;

import de.hybris.platform.category.model.CategoryModel;
import de.hybris.platform.commerceservices.helper.ProductAndCategoryHelper;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.servicelayer.config.ConfigurationService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Required;

import com.custom.productcomparison.constants.ProductComparisonConstants;
import com.custom.productcomparison.service.ProductComparisonGroupingService;


/**
 * Default implementation of {@link ProductComparisonGroupingService} (NET-8940 section 5.2).
 * <p>
 * Reuses the platform's {@link ProductAndCategoryHelper} the same way
 * {@code ProductBreadcrumbBuilder} does: resolve the base product (for variants), walk each
 * assigned category up to its root via {@code getSupercategories()}, and pick the category at the
 * configured depth. The configured depth is read fresh on every call via
 * {@link ConfigurationService} - never hardcoded - so a HAC change to
 * {@value ProductComparisonConstants#GROUPING_CATEGORY_DEPTH_PROPERTY} takes effect immediately
 * (acceptance criterion 12).
 */
public class DefaultProductComparisonGroupingService implements ProductComparisonGroupingService
{
	private ConfigurationService configurationService;
	private ProductAndCategoryHelper productAndCategoryHelper;

	/**
	 * Resolves the grouping category for the given product's assigned category path, per
	 * {@link ProductComparisonGroupingService} (NET-8940 section 5.2).
	 * <p>
	 * If the path is shorter than the configured depth, falls back to the deepest category actually
	 * on the path - in practice, the product's own directly-assigned category (NET-8940 section 8,
	 * open question 4) - rather than {@link Optional#empty()}. {@link Optional#empty()} is now
	 * reserved for the case where the product has no valid assigned category path at all.
	 * <p>
	 * <b>Precondition this fallback relies on</b>: products must be assigned to leaf categories
	 * only, never to an internal category that also has its own subcategories carrying other
	 * products. Under that precondition a leaf never competes with a deeper sibling for the same
	 * grouping slot, so two products can never be split despite sharing a real ancestor. If it is
	 * ever violated (a product assigned directly to an internal category like {@code Digital Cameras}
	 * while other products go deeper into {@code Digital Compacts}/{@code Digital SLR}), the fallback
	 * can silently split products that should be grouped together - a catalog-authoring/data-quality
	 * concern to watch for, not something this method attempts to detect or validate defensively.
	 */
	@Override
	public Optional<CategoryModel> resolveGroupingCategory(final ProductModel product)
	{
		validateParameterNotNull(product, "product must not be null");

		final ProductModel baseProduct = getProductAndCategoryHelper().getBaseProduct(product);
		final int depth = getConfiguredDepth();

		if (CollectionUtils.isEmpty(baseProduct.getSupercategories()))
		{
			return Optional.empty();
		}

		for (final CategoryModel assignedCategory : baseProduct.getSupercategories())
		{
			if (!getProductAndCategoryHelper().isValidProductCategory(assignedCategory))
			{
				continue;
			}

			final List<CategoryModel> pathFromRoot = buildPathFromRoot(assignedCategory);
			if (pathFromRoot.isEmpty())
			{
				continue;
			}

			final int resolvedIndex = Math.min(depth, pathFromRoot.size()) - 1;
			return Optional.of(pathFromRoot.get(resolvedIndex));
		}

		return Optional.empty();
	}

	/**
	 * Walks from the given category up to the root via {@code getSupercategories()}, following the
	 * first supercategory at each step (NET-8940 section 4 assumes - and section 8.1 verifies for
	 * the demo catalog - a single-parent chain from leaf to root), then drops the catalog's
	 * synthetic root category (the topmost element, identified by having no supercategories of its
	 * own - e.g. category "1" in {@code electronicsProductCatalog}) - depth counts from the first
	 * real navigation category below it, not from that synthetic root (NET-8940 section 4, revised:
	 * counting the synthetic root made two unrelated top-level categories - "Cameras" and
	 * "Digital Cameras" - resolve to the same grouping category).
	 * <p>
	 * Delegates to {@link #buildRootToLeafPath(CategoryModel)}, which recurses to the root first and
	 * appends each category on the way back down, then strips that root off the front.
	 *
	 * @return the path with the first real navigation category at index 0 and {@code leaf} as the
	 *         last element
	 */
	protected List<CategoryModel> buildPathFromRoot(final CategoryModel leaf)
	{
		final List<CategoryModel> path = buildRootToLeafPath(leaf);
		if (!path.isEmpty())
		{
			path.remove(0);
		}
		return path;
	}

	/**
	 * Recursively builds the full path from the catalog's synthetic root down to {@code category},
	 * root at index 0. Base case: a category with no supercategories of its own is the synthetic
	 * root itself, returned as a single-element list. Otherwise recurses on the first supercategory
	 * (see {@link #buildPathFromRoot(CategoryModel)} on the single-parent-chain assumption) and
	 * appends {@code category} to the result on the way back up - a plain {@link ArrayList} append,
	 * since recursing-then-appending naturally produces root-to-leaf order without an
	 * {@code addFirst}/{@code LinkedList} trick.
	 */
	protected List<CategoryModel> buildRootToLeafPath(final CategoryModel category)
	{
		final List<CategoryModel> supercategories = category.getSupercategories();
		if (CollectionUtils.isEmpty(supercategories))
		{
			final List<CategoryModel> root = new ArrayList<>();
			root.add(category);
			return root;
		}

		final List<CategoryModel> path = buildRootToLeafPath(supercategories.get(0));
		path.add(category);
		return path;
	}

	protected int getConfiguredDepth()
	{
		return getConfigurationService().getConfiguration().getInt(
				ProductComparisonConstants.GROUPING_CATEGORY_DEPTH_PROPERTY,
				ProductComparisonConstants.DEFAULT_GROUPING_CATEGORY_DEPTH);
	}

	protected ConfigurationService getConfigurationService()
	{
		return configurationService;
	}

	@Required
	public void setConfigurationService(final ConfigurationService configurationService)
	{
		this.configurationService = configurationService;
	}

	protected ProductAndCategoryHelper getProductAndCategoryHelper()
	{
		return productAndCategoryHelper;
	}

	@Required
	public void setProductAndCategoryHelper(final ProductAndCategoryHelper productAndCategoryHelper)
	{
		this.productAndCategoryHelper = productAndCategoryHelper;
	}
}
