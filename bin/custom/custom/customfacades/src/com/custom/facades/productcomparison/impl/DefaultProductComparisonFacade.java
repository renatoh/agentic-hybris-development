/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.facades.productcomparison.impl;

import de.hybris.platform.catalog.model.classification.ClassAttributeAssignmentModel;
import de.hybris.platform.catalog.model.classification.ClassificationClassModel;
import de.hybris.platform.classification.ClassificationService;
import de.hybris.platform.classification.features.Feature;
import de.hybris.platform.classification.features.FeatureList;
import de.hybris.platform.classification.features.FeatureValue;
import de.hybris.platform.commercefacades.product.ProductFacade;
import de.hybris.platform.commercefacades.product.ProductOption;
import de.hybris.platform.commercefacades.product.data.ProductData;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.product.ProductService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Required;

import com.custom.facades.productcomparison.ProductComparisonFacade;
import com.custom.facades.productcomparison.data.ProductComparisonListData;
import com.custom.facades.productcomparison.data.ProductComparisonRowData;
import com.custom.facades.productcomparison.data.ProductComparisonTableData;
import com.custom.productcomparison.model.ProductComparisonList;
import com.custom.productcomparison.service.ProductComparisonService;


/**
 * Default implementation of {@link ProductComparisonFacade} (NET-8940 section 5.3/5.4).
 * <p>
 * The comparison table's rows come from the {@code ClassificationClassModel}s carried by the
 * list's products, not a direct category-to-classification-system lookup: an ordinary navigation
 * category (the grouping category) has no built-in relation to a {@code ClassificationSystemVersion}
 * in the platform data model - only {@code ClassificationClassModel} (a distinct category subtype,
 * explicitly excluded from grouping-category resolution, and unrelated to it in the demo catalog's
 * own data - see electronicsstore's {@code products-classifications.impex}, which assigns
 * classification features straight onto each product with no category involved at all) carries
 * classification attributes. {@code buildRows} therefore reads each product's actual
 * {@code ClassAttributeAssignment}s (via {@code ClassificationService.getFeatures}) only to
 * discover which {@code ClassificationClassModel}(s) the list's products belong to, then asks each
 * such class for its <em>complete</em> attribute set via
 * {@code ClassificationClassModel.getAllClassificationAttributeAssignments()}, rather than only
 * the attributes some product in the list happens to carry a value for.
 * <p>
 * Row construction is then a required two-step process (spec section 5.3, and the two distinct
 * regressions in section 8.6 from getting either step wrong): (1) resolve candidate rows from that
 * full assignment set - the correctness fix above, which must not be narrowed back to a
 * union-of-observed-values shortcut; (2) filter the candidates, dropping only a row that is blank
 * for <em>every</em> product currently in the list - applied strictly after step 1, never as a
 * substitute for it. A row with a value for at least one product still shows, with an empty cell
 * only for the products actually missing that value.
 */
public class DefaultProductComparisonFacade implements ProductComparisonFacade
{
	private static final List<ProductOption> PRODUCT_OPTIONS = Arrays
			.asList(ProductOption.BASIC, ProductOption.PRICE, ProductOption.SUMMARY);

	private ProductComparisonService productComparisonService;
	private ProductService productService;
	private ProductFacade productFacade;
	private ClassificationService classificationService;

	@Override
	public boolean addToCompare(final String productCode)
	{
		final ProductModel product = getProductService().getProductForCode(productCode);
		return getProductComparisonService().addProduct(product).isPresent();
	}

	@Override
	public void removeFromCompare(final String listId, final String productCode)
	{
		final ProductModel product = getProductService().getProductForCode(productCode);
		getProductComparisonService().removeProduct(listId, product);
	}

	@Override
	public boolean hasLists()
	{
		return !getProductComparisonService().getLists().isEmpty();
	}

	@Override
	public List<ProductComparisonListData> getLists()
	{
		return getProductComparisonService().getLists().stream().map(this::toListData).collect(Collectors.toList());
	}

	@Override
	public Optional<ProductComparisonTableData> getComparisonTable(final String listId)
	{
		final Optional<ProductComparisonList> list = StringUtils.isNotBlank(listId)
				? getProductComparisonService().getList(listId)
				: getProductComparisonService().getMostRecentlyTouchedList();
		return list.map(this::toTableData);
	}

	protected ProductComparisonListData toListData(final ProductComparisonList list)
	{
		final ProductComparisonListData data = new ProductComparisonListData();
		data.setId(list.getId());
		data.setLabel(list.getGroupingCategory().getName());
		data.setProductCount(list.getProducts().size());
		return data;
	}

	protected ProductComparisonTableData toTableData(final ProductComparisonList list)
	{
		final ProductComparisonTableData table = new ProductComparisonTableData();
		table.setListId(list.getId());
		table.setLabel(list.getGroupingCategory().getName());

		final List<ProductData> productDataList = list.getProducts().stream()
				.map(product -> getProductFacade().getProductForCodeAndOptions(product.getCode(), PRODUCT_OPTIONS))
				.collect(Collectors.toList());
		table.setProducts(productDataList);

		table.setRows(buildRows(list.getProducts()));
		return table;
	}

	protected List<ProductComparisonRowData> buildRows(final List<ProductModel> products)
	{
		final Map<ProductModel, FeatureList> featuresByProduct = new LinkedHashMap<>();
		final Set<ClassificationClassModel> classificationClasses = new LinkedHashSet<>();
		for (final ProductModel product : products)
		{
			final FeatureList features = getClassificationService().getFeatures(product);
			featuresByProduct.put(product, features);
			features.getClassAttributeAssignments()
					.forEach(assignment -> classificationClasses.add(assignment.getClassificationClass()));
		}

		final Set<ClassAttributeAssignmentModel> assignments = new LinkedHashSet<>();
		for (final ClassificationClassModel classificationClass : classificationClasses)
		{
			assignments.addAll(classificationClass.getAllClassificationAttributeAssignments());
		}

		final List<ProductComparisonRowData> rows = new ArrayList<>();
		for (final ClassAttributeAssignmentModel assignment : assignments)
		{
			final ProductComparisonRowData row = new ProductComparisonRowData();
			row.setAttributeName(assignment.getClassificationAttribute().getName());
			final List<String> values = new ArrayList<>();
			for (final ProductModel product : products)
			{
				final Feature feature = featuresByProduct.get(product).getFeatureByAssignment(assignment);
				values.add(feature == null ? StringUtils.EMPTY : formatFeature(feature));
			}

			// Step 2 (spec section 5.3): drop a candidate row only if EVERY product in the list is
			// blank for it - applied after step 1's full-assignment resolution above, not as a
			// substitute for it.
			if (values.stream().anyMatch(StringUtils::isNotBlank))
			{
				row.setValues(values);
				rows.add(row);
			}
		}
		return rows;
	}

	protected String formatFeature(final Feature feature)
	{
		return feature.getValues().stream().map(this::formatFeatureValue).filter(StringUtils::isNotBlank)
				.collect(Collectors.joining(", "));
	}

	protected String formatFeatureValue(final FeatureValue featureValue)
	{
		final Object rawValue = featureValue.getValue();
		final String formatted = rawValue == null ? StringUtils.EMPTY : rawValue.toString();
		if (featureValue.getUnit() != null && StringUtils.isNotBlank(featureValue.getUnit().getSymbol()))
		{
			return formatted + " " + featureValue.getUnit().getSymbol();
		}
		return formatted;
	}

	protected ProductComparisonService getProductComparisonService()
	{
		return productComparisonService;
	}

	@Required
	public void setProductComparisonService(final ProductComparisonService productComparisonService)
	{
		this.productComparisonService = productComparisonService;
	}

	protected ProductService getProductService()
	{
		return productService;
	}

	@Required
	public void setProductService(final ProductService productService)
	{
		this.productService = productService;
	}

	protected ProductFacade getProductFacade()
	{
		return productFacade;
	}

	@Required
	public void setProductFacade(final ProductFacade productFacade)
	{
		this.productFacade = productFacade;
	}

	protected ClassificationService getClassificationService()
	{
		return classificationService;
	}

	@Required
	public void setClassificationService(final ClassificationService classificationService)
	{
		this.classificationService = classificationService;
	}
}
