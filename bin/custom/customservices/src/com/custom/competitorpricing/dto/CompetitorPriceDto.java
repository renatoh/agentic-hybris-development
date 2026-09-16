package com.custom.competitorpricing.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;


/**
 * Competitor pricing for a single product, as returned by the external pricing endpoint
 * (NET-8939 &sect;4). Plain Jackson POJO, deliberately not a {@code -beans.xml} type.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CompetitorPriceDto
{
	private String productCode;
	private BigDecimal averagePrice;
	private BigDecimal minPrice;
	private BigDecimal maxPrice;

	public String getProductCode()
	{
		return productCode;
	}

	public void setProductCode(final String productCode)
	{
		this.productCode = productCode;
	}

	public BigDecimal getAveragePrice()
	{
		return averagePrice;
	}

	public void setAveragePrice(final BigDecimal averagePrice)
	{
		this.averagePrice = averagePrice;
	}

	public BigDecimal getMinPrice()
	{
		return minPrice;
	}

	public void setMinPrice(final BigDecimal minPrice)
	{
		this.minPrice = minPrice;
	}

	public BigDecimal getMaxPrice()
	{
		return maxPrice;
	}

	public void setMaxPrice(final BigDecimal maxPrice)
	{
		this.maxPrice = maxPrice;
	}
}
