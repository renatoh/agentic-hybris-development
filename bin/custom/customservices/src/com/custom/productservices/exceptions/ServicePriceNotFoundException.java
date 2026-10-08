package com.custom.productservices.exceptions;

import de.hybris.platform.servicelayer.exceptions.SystemException;


/**
 * NET-8943 &sect;5.2: thrown by the service price hook when a service cart entry cannot be priced (no linked product
 * entry, no price condition, no price group or no matching price row). A service is never charged at a fallback price,
 * so the calculation fails instead.
 */
public class ServicePriceNotFoundException extends SystemException
{
	public ServicePriceNotFoundException(final String message)
	{
		super(message);
	}
}
