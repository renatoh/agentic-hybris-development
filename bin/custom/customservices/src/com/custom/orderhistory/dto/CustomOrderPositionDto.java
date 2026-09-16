package com.custom.orderhistory.dto;

import java.io.Serializable;
import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;


/**
 * One position (line) of an external order as delivered by the external order history endpoint.
 * <p>
 * Plain Jackson POJO - deliberately not a hybris <code>-beans.xml</code> type and not an item type,
 * nothing from NET-8938 is persisted.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CustomOrderPositionDto implements Serializable
{
	private static final long serialVersionUID = 1L;

	private Integer positionNumber;
	private String productReference;
	private Integer quantity;
	private BigDecimal price;

	public Integer getPositionNumber()
	{
		return positionNumber;
	}

	public void setPositionNumber(final Integer positionNumber)
	{
		this.positionNumber = positionNumber;
	}

	public String getProductReference()
	{
		return productReference;
	}

	public void setProductReference(final String productReference)
	{
		this.productReference = productReference;
	}

	public Integer getQuantity()
	{
		return quantity;
	}

	public void setQuantity(final Integer quantity)
	{
		this.quantity = quantity;
	}

	public BigDecimal getPrice()
	{
		return price;
	}

	public void setPrice(final BigDecimal price)
	{
		this.price = price;
	}
}
