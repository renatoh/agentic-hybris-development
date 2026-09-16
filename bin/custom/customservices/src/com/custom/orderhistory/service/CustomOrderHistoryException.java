package com.custom.orderhistory.service;

/**
 * Thrown when the order history of a customer cannot be retrieved from the external system.
 * <p>
 * NET-8938 &sect;5.1: a failure must never be swallowed into an empty list and must never fall back
 * to the order history stored in the Commerce DB.
 */
public class CustomOrderHistoryException extends RuntimeException
{
	private static final long serialVersionUID = 1L;

	public CustomOrderHistoryException(final String message)
	{
		super(message);
	}

	public CustomOrderHistoryException(final String message, final Throwable cause)
	{
		super(message, cause);
	}
}
