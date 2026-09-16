/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.storefront.controllers.pages;

import de.hybris.platform.acceleratorstorefrontcommons.controllers.pages.AbstractPageController;
import de.hybris.platform.cms2.exceptions.CMSItemNotFoundException;
import de.hybris.platform.cms2.model.pages.ContentPageModel;

import java.util.Optional;

import javax.annotation.Resource;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import com.custom.facades.productcomparison.ProductComparisonFacade;
import com.custom.facades.productcomparison.data.ProductComparisonTableData;
import com.custom.storefront.controllers.ControllerConstants;


/**
 * Renders the product comparison page (NET-8940 section 5.4): one list shown at a time, with a
 * dropdown to switch between the session's current lists.
 * <p>
 * Resolves and stores the CMS content page the same way every other simple page controller in
 * this codebase does (see {@code StoreLocatorPageController.getStoreFinderPage()}) - required so
 * that {@code header.tag}/{@code footer.tag}'s {@code <cms:pageSlot>} tags have a
 * {@code CmsPageRequestContextData} to read from; skipping it throws a NullPointerException from
 * those tags at render time. The backing {@code ContentPage} (uid {@code productComparePage},
 * label {@value #CMS_PAGE_LABEL}) is imported by
 * {@code resources/impex/customstorefront-productcomparison-cms.impex}, reusing the platform's
 * existing generic {@code ContentPage1Template}.
 */
@Controller
@RequestMapping(value = "/product-compare")
public class ProductComparisonPageController extends AbstractPageController
{
	private static final String CMS_PAGE_LABEL = "product-compare";

	@Resource(name = "productComparisonFacade")
	private ProductComparisonFacade productComparisonFacade;

	@RequestMapping(method = RequestMethod.GET)
	public String comparisonPage(@RequestParam(value = "list", required = false) final String listId, final Model model)
			throws CMSItemNotFoundException
	{
		model.addAttribute("comparisonLists", productComparisonFacade.getLists());

		final Optional<ProductComparisonTableData> table = productComparisonFacade.getComparisonTable(listId);
		model.addAttribute("comparisonTable", table.orElse(null));
		model.addAttribute("selectedListId", table.map(ProductComparisonTableData::getListId).orElse(null));

		final ContentPageModel cmsPage = getContentPageForLabelOrId(CMS_PAGE_LABEL);
		storeCmsPageInModel(model, cmsPage);
		setUpMetaDataForContentPage(model, cmsPage);
		storeContentPageTitleInModel(model,
				getMessageSource().getMessage("product.compare.page.title", null, getI18nService().getCurrentLocale()));

		return ControllerConstants.Views.Pages.Product.ComparisonPage;
	}
}
