/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.facades.savedforlater.data;

import de.hybris.platform.commercefacades.product.data.ProductData;

import java.io.Serializable;


/**
 * One row of the "Saved for later" section (NET-8941 section 3): the saved product (for its
 * image/name/price) plus the quantity that was in the cart when it was saved.
 */
public class SavedForLaterEntryData implements Serializable
{
	private String productCode;
	private ProductData product;
	private long quantity;

	public String getProductCode()
	{
		return productCode;
	}

	public void setProductCode(final String productCode)
	{
		this.productCode = productCode;
	}

	public ProductData getProduct()
	{
		return product;
	}

	public void setProduct(final ProductData product)
	{
		this.product = product;
	}

	public long getQuantity()
	{
		return quantity;
	}

	public void setQuantity(final long quantity)
	{
		this.quantity = quantity;
	}
}
