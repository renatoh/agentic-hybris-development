/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.facades.productcomparison.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;


/**
 * One classification-attribute row of the comparison table (NET-8940 section 5.3). {@code values}
 * is aligned by index with {@link ProductComparisonTableData#getProducts()} - an empty string
 * means that product has no value for this attribute, rather than the row being excluded.
 */
public class ProductComparisonRowData implements Serializable
{
	private static final long serialVersionUID = 1L;

	private String attributeName;
	private List<String> values = new ArrayList<>();

	public String getAttributeName()
	{
		return attributeName;
	}

	public void setAttributeName(final String attributeName)
	{
		this.attributeName = attributeName;
	}

	public List<String> getValues()
	{
		return values;
	}

	public void setValues(final List<String> values)
	{
		this.values = values;
	}
}
