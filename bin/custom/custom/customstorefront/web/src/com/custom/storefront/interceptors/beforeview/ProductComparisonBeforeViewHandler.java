/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.storefront.interceptors.beforeview;

import de.hybris.platform.acceleratorstorefrontcommons.interceptors.BeforeViewHandler;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Required;
import org.springframework.web.servlet.ModelAndView;

import com.custom.facades.productcomparison.ProductComparisonFacade;
import com.custom.facades.productcomparison.data.ProductComparisonListData;


/**
 * Makes the shopper's comparison state available on every page (NET-8940 section 5.4), so the
 * header icon - included next to the minicart in header.tag on every page, not just the
 * comparison page itself - can show its badge and be hidden/disabled when there are no lists
 * (acceptance criterion 5). The badge shows the total product count across all lists, not the
 * list count (NET-8940 section 8, open question 2).
 */
public class ProductComparisonBeforeViewHandler implements BeforeViewHandler
{
	private ProductComparisonFacade productComparisonFacade;

	@Override
	public void beforeView(final HttpServletRequest request, final HttpServletResponse response, final ModelAndView modelAndView)
			throws Exception
	{
		final List<ProductComparisonListData> lists = getProductComparisonFacade().getLists();
		final int listCount = lists.size();
		final int productCount = lists.stream().mapToInt(ProductComparisonListData::getProductCount).sum();

		modelAndView.getModel().put("productComparisonListCount", Integer.valueOf(listCount));
		modelAndView.getModel().put("productComparisonProductCount", Integer.valueOf(productCount));
	}

	protected ProductComparisonFacade getProductComparisonFacade()
	{
		return productComparisonFacade;
	}

	@Required
	public void setProductComparisonFacade(final ProductComparisonFacade productComparisonFacade)
	{
		this.productComparisonFacade = productComparisonFacade;
	}
}
