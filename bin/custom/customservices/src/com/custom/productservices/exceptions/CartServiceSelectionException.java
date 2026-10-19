package com.custom.productservices.exceptions;

/**
 * NET-8943 &sect;5.4: a service could not be added to or removed from a cart line. The cart is left unchanged.
 */
public class CartServiceSelectionException extends Exception
{
	public CartServiceSelectionException(final String message)
	{
		super(message);
	}
}
