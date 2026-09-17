/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.storefront.controllers.misc;

import de.hybris.platform.acceleratorstorefrontcommons.controllers.AbstractController;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.util.GlobalMessages;
import de.hybris.platform.commerceservices.url.UrlResolver;
import de.hybris.platform.servicelayer.exceptions.AmbiguousIdentifierException;
import de.hybris.platform.servicelayer.exceptions.UnknownIdentifierException;

import javax.annotation.Resource;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.custom.facades.productcomparison.ProductComparisonFacade;


/**
 * "Add to compare" / "remove from compare" actions (NET-8940 section 5.4). The PDP is the only
 * entry point for adding (acceptance criterion / section 3); removal happens from the comparison
 * page.
 * <p>
 * Deliberately mapped outside the "/p/" namespace ("/compare/add/{productCode}", not
 * "/p/{productCode}/compare/add"): {@code ProductPageController}'s own PDP mapping is
 * "/&#42;&#42;/p/{productCode:.*}" - a greedy regex path variable with no trailing literal segment
 * to anchor against - which swallows any deeper path under "/p/" (e.g.
 * "/p/300938/compare/add") for GET, and left no matching POST handler for that same path, so a
 * mapping nested under "/p/" here was returning 405 instead of ever reaching this controller.
 */
@Controller
public class AddToCompareController extends AbstractController
{
	private static final String COMPARISON_PAGE_URL = "/product-compare";

	@Resource(name = "productComparisonFacade")
	private ProductComparisonFacade productComparisonFacade;

	@Resource(name = "sapProductCodeUrlResolver")
	private UrlResolver<String> sapProductCodeUrlResolver;

	@PostMapping(value = "/compare/add/{productCode}")
	public String addToCompare(@PathVariable("productCode") final String productCode,
			final RedirectAttributes redirectAttrs)
	{
		try
		{
			final boolean added = productComparisonFacade.addToCompare(productCode);
			if (added)
			{
				GlobalMessages.addFlashMessage(redirectAttrs, GlobalMessages.CONF_MESSAGES_HOLDER, "product.compare.addedToList");
			}
			else
			{
				GlobalMessages.addFlashMessage(redirectAttrs, GlobalMessages.ERROR_MESSAGES_HOLDER, "product.compare.notAvailable");
			}
			return REDIRECT_PREFIX + sapProductCodeUrlResolver.resolve(productCode);
		}
		catch (final UnknownIdentifierException | AmbiguousIdentifierException ex)
		{
			// stale/tampered productCode - no product to redirect to, so fall back to the homepage
			// rather than letting the exception propagate
			GlobalMessages.addFlashMessage(redirectAttrs, GlobalMessages.ERROR_MESSAGES_HOLDER, "product.compare.notAvailable");
			return REDIRECT_PREFIX + "/";
		}
	}

	@PostMapping(value = "/compare/{listId}/remove/{productCode}")
	public String removeFromCompare(@PathVariable("listId") final String listId,
			@PathVariable("productCode") final String productCode)
	{
		try
		{
			productComparisonFacade.removeFromCompare(listId, productCode);
		}
		catch (final UnknownIdentifierException | AmbiguousIdentifierException ex)
		{
			// stale/tampered productCode - nothing to remove, fall through to the comparison page as usual
		}
		return REDIRECT_PREFIX + COMPARISON_PAGE_URL;
	}

	@PostMapping(value = "/compare/{listId}/delete")
	public String deleteList(@PathVariable("listId") final String listId, final RedirectAttributes redirectAttrs)
	{
		productComparisonFacade.deleteList(listId);
		GlobalMessages.addFlashMessage(redirectAttrs, GlobalMessages.CONF_MESSAGES_HOLDER, "product.compare.listDeleted");
		// no listId query param - ProductComparisonFacade.getComparisonTable(null) already defaults
		// to the most recently touched remaining list, or the empty state if none are left
		// (acceptance criterion 8a)
		return REDIRECT_PREFIX + COMPARISON_PAGE_URL;
	}
}
