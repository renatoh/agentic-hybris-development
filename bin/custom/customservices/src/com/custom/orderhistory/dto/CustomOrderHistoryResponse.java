package com.custom.orderhistory.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;


/**
 * Root payload of the external order history endpoint (NET-8938 &sect;4).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CustomOrderHistoryResponse implements Serializable
{
	private static final long serialVersionUID = 1L;

	private String customerNumber;
	private List<CustomOrderDto> orders = new ArrayList<>();

	public String getCustomerNumber()
	{
		return customerNumber;
	}

	public void setCustomerNumber(final String customerNumber)
	{
		this.customerNumber = customerNumber;
	}

	public List<CustomOrderDto> getOrders()
	{
		return orders;
	}

	public void setOrders(final List<CustomOrderDto> orders)
	{
		this.orders = orders;
	}
}
