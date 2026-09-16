/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.facades.productcomparison.data;

import java.io.Serializable;


/**
 * One entry in the comparison page's list-switcher dropdown (NET-8940 section 5.4).
 */
public class ProductComparisonListData implements Serializable
{
	private static final long serialVersionUID = 1L;

	private String id;
	private String label;
	private int productCount;

	public String getId()
	{
		return id;
	}

	public void setId(final String id)
	{
		this.id = id;
	}

	public String getLabel()
	{
		return label;
	}

	public void setLabel(final String label)
	{
		this.label = label;
	}

	public int getProductCount()
	{
		return productCount;
	}

	public void setProductCount(final int productCount)
	{
		this.productCount = productCount;
	}
}
