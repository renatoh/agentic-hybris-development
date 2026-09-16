package com.custom.orderhistory.service;

import java.util.Optional;

import com.custom.orderhistory.dto.CustomOrderDto;
import com.custom.orderhistory.dto.CustomOrderHistoryResponse;


/**
 * Supplies the order history of a customer from the external system that owns the orders
 * (NET-8938).
 * <p>
 * Which implementation is active is decided purely by the Spring alias
 * <code>customOrderHistoryService</code> in <code>customservices-spring.xml</code>.
 */
public interface CustomOrderHistoryService
{
	/**
	 * Returns the complete order history of the given customer.
	 *
	 * @param customerNumber
	 *           external customer number
	 * @return the response, never <code>null</code>
	 * @throws CustomOrderHistoryException
	 *            if the history cannot be retrieved
	 */
	CustomOrderHistoryResponse getOrderHistory(String customerNumber);

	/**
	 * Returns a single order of the given customer (NET-8938 &sect;5.1).
	 * <p>
	 * An empty {@link Optional} means the order simply does not exist in the external system - a
	 * normal outcome, not an error. Only a transport/parse failure throws
	 * {@link CustomOrderHistoryException}, so that "order not found" stays distinct from "the
	 * backend is broken".
	 *
	 * @param customerNumber
	 *           external customer number
	 * @param orderNumber
	 *           external order number, matched exactly and case-sensitively
	 * @return the order, or {@link Optional#empty()} if no such order exists
	 * @throws CustomOrderHistoryException
	 *            if the order history cannot be retrieved
	 */
	Optional<CustomOrderDto> getOrderDetail(String customerNumber, String orderNumber);
}
