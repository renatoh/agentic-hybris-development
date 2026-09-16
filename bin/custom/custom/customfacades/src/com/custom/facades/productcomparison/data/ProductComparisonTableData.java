/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.facades.productcomparison.data;

import de.hybris.platform.commercefacades.product.data.ProductData;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;


/**
 * The comparison table for one list (NET-8940 section 5.3/5.4): one column per product
 * ({@code products}, carrying name/image/price via {@code ProductData}), one row per
 * classification attribute ({@code rows}).
 */
public class ProductComparisonTableData implements Serializable
{
	private static final long serialVersionUID = 1L;

	private String listId;
	private String label;
	private List<ProductData> products = new ArrayList<>();
	private List<ProductComparisonRowData> rows = new ArrayList<>();

	public String getListId()
	{
		return listId;
	}

	public void setListId(final String listId)
	{
		this.listId = listId;
	}

	public String getLabel()
	{
		return label;
	}

	public void setLabel(final String label)
	{
		this.label = label;
	}

	public List<ProductData> getProducts()
	{
		return products;
	}

	public void setProducts(final List<ProductData> products)
	{
		this.products = products;
	}

	public List<ProductComparisonRowData> getRows()
	{
		return rows;
	}

	public void setRows(final List<ProductComparisonRowData> rows)
	{
		this.rows = rows;
	}
}
