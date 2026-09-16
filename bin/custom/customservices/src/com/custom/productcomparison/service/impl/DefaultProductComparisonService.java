/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.productcomparison.service.impl;

import static de.hybris.platform.servicelayer.util.ServicesUtil.validateParameterNotNull;

import de.hybris.platform.category.model.CategoryModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.servicelayer.session.SessionService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Required;

import com.custom.productcomparison.constants.ProductComparisonConstants;
import com.custom.productcomparison.model.ProductComparisonList;
import com.custom.productcomparison.service.ProductComparisonGroupingService;
import com.custom.productcomparison.service.ProductComparisonService;


/**
 * Default implementation of {@link ProductComparisonService} (NET-8940 section 5.1). Holds state
 * as a single session attribute - a {@code List<ProductComparisonList>} - exactly like the guest
 * cart: nothing here ever reaches the database, so a new session (or session invalidation) starts
 * with zero lists (acceptance criterion 9).
 * <p>
 * A list retrieved via {@code SessionService.getAttribute(...)} on a later request is not
 * guaranteed to be the same mutable {@code ArrayList} instance that was stored earlier - it can
 * come back backed by an unmodifiable snapshot. Every method here therefore works on a freshly
 * copied, known-mutable list and explicitly calls {@code setAttribute(...)} again after any
 * structural change (a list added or removed), rather than mutating the retrieved reference in
 * place and assuming that persists.
 */
public class DefaultProductComparisonService implements ProductComparisonService
{
	private SessionService sessionService;
	private ProductComparisonGroupingService productComparisonGroupingService;

	@Override
	public Optional<ProductComparisonList> addProduct(final ProductModel product)
	{
		validateParameterNotNull(product, "product must not be null");

		final Optional<CategoryModel> groupingCategory = getProductComparisonGroupingService().resolveGroupingCategory(product);
		if (groupingCategory.isEmpty())
		{
			return Optional.empty();
		}

		final List<ProductComparisonList> lists = getOrCreateLists();
		ProductComparisonList targetList = findList(lists, groupingCategory.get());
		if (targetList == null)
		{
			targetList = new ProductComparisonList(groupingCategory.get());
			lists.add(targetList);
		}

		targetList.addProductIfAbsent(product);
		targetList.touch();

		persistLists(lists);

		return Optional.of(targetList);
	}

	@Override
	public List<ProductComparisonList> getLists()
	{
		return getOrCreateLists();
	}

	@Override
	public Optional<ProductComparisonList> getList(final String listId)
	{
		return getOrCreateLists().stream().filter(list -> list.getId().equals(listId)).findFirst();
	}

	@Override
	public Optional<ProductComparisonList> getMostRecentlyTouchedList()
	{
		return getOrCreateLists().stream().max((a, b) -> Long.compare(a.getLastTouched(), b.getLastTouched()));
	}

	@Override
	public void removeProduct(final String listId, final ProductModel product)
	{
		validateParameterNotNull(listId, "listId must not be null");
		validateParameterNotNull(product, "product must not be null");

		final List<ProductComparisonList> lists = getOrCreateLists();
		final ProductComparisonList list = lists.stream().filter(existing -> existing.getId().equals(listId)).findFirst()
				.orElse(null);
		if (list == null)
		{
			return;
		}

		list.removeProduct(product);
		if (list.isEmpty())
		{
			lists.remove(list);
		}
		else
		{
			list.touch();
		}

		persistLists(lists);
	}

	protected ProductComparisonList findList(final List<ProductComparisonList> lists, final CategoryModel groupingCategory)
	{
		return lists.stream().filter(list -> list.getGroupingCategory().getPk().equals(groupingCategory.getPk())).findFirst()
				.orElse(null);
	}

	/**
	 * Always returns - and, if nothing was stored yet, persists - a fresh, known-mutable
	 * {@code ArrayList} copy. Never returns the raw session attribute value directly (see class
	 * javadoc).
	 */
  
	protected List<ProductComparisonList> getOrCreateLists()
	{
		final List<ProductComparisonList> stored = getSessionService()
				.getAttribute(ProductComparisonConstants.SESSION_ATTRIBUTE_COMPARISON_LISTS);
		final List<ProductComparisonList> lists = stored == null ? new ArrayList<>() : new ArrayList<>(stored);
		if (stored == null)
		{
			persistLists(lists);
		}
		return lists;
	}

	protected void persistLists(final List<ProductComparisonList> lists)
	{
		getSessionService().setAttribute(ProductComparisonConstants.SESSION_ATTRIBUTE_COMPARISON_LISTS, lists);
	}

	protected SessionService getSessionService()
	{
		return sessionService;
	}

	@Required
	public void setSessionService(final SessionService sessionService)
	{
		this.sessionService = sessionService;
	}

	protected ProductComparisonGroupingService getProductComparisonGroupingService()
	{
		return productComparisonGroupingService;
	}

	@Required
	public void setProductComparisonGroupingService(final ProductComparisonGroupingService productComparisonGroupingService)
	{
		this.productComparisonGroupingService = productComparisonGroupingService;
	}
}
