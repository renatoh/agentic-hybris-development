package com.custom.orderhistory.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;


/**
 * One external order as delivered by the external order history endpoint (NET-8938 &sect;4).
 * <p>
 * <code>orderDate</code> is kept as the raw <code>yyyy-MM-dd</code> string and parsed in the
 * populator, <code>totalPrice</code> is authoritative and is never recalculated from the positions.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CustomOrderDto implements Serializable
{
	private static final long serialVersionUID = 1L;

	private String orderNumber;
	private String orderDate;
	private String status;
	private String currency;
	private BigDecimal totalPrice;
	private List<CustomOrderPositionDto> positions = new ArrayList<>();

	public String getOrderNumber()
	{
		return orderNumber;
	}

	public void setOrderNumber(final String orderNumber)
	{
		this.orderNumber = orderNumber;
	}

	public String getOrderDate()
	{
		return orderDate;
	}

	public void setOrderDate(final String orderDate)
	{
		this.orderDate = orderDate;
	}

	public String getStatus()
	{
		return status;
	}

	public void setStatus(final String status)
	{
		this.status = status;
	}

	public String getCurrency()
	{
		return currency;
	}

	public void setCurrency(final String currency)
	{
		this.currency = currency;
	}

	public BigDecimal getTotalPrice()
	{
		return totalPrice;
	}

	public void setTotalPrice(final BigDecimal totalPrice)
	{
		this.totalPrice = totalPrice;
	}

	public List<CustomOrderPositionDto> getPositions()
	{
		return positions;
	}

	public void setPositions(final List<CustomOrderPositionDto> positions)
	{
		this.positions = positions;
	}
}
