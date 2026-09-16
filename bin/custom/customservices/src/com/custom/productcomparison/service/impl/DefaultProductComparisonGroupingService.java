/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.productcomparison.service.impl;

import static de.hybris.platform.servicelayer.util.ServicesUtil.validateParameterNotNull;

import de.hybris.platform.category.model.CategoryModel;
import de.hybris.platform.commerceservices.helper.ProductAndCategoryHelper;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.servicelayer.config.ConfigurationService;

import java.util.LinkedList;
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
			if (pathFromRoot.size() >= depth)
			{
				return Optional.of(pathFromRoot.get(depth - 1));
			}
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
	 *
	 * @return the path with the first real navigation category at index 0 and {@code leaf} as the
	 *         last element
	 */
	protected List<CategoryModel> buildPathFromRoot(final CategoryModel leaf)
	{
		final LinkedList<CategoryModel> path = new LinkedList<>();
		CategoryModel current = leaf;
		while (current != null)
		{
			path.addFirst(current);
			final List<CategoryModel> supercategories = current.getSupercategories();
			current = CollectionUtils.isEmpty(supercategories) ? null : supercategories.get(0);
		}
		if (!path.isEmpty())
		{
			path.removeFirst();
		}
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
