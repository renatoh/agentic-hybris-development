/*
 * Copyright (c) 2021 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.constants;

/**
 * Global class for all Customservices constants. You can add global constants for your extension into this class.
 */
public final class CustomservicesConstants extends GeneratedCustomservicesConstants
{
	public static final String EXTENSIONNAME = "customservices";

	private CustomservicesConstants()
	{
		//empty to avoid instantiating this constant class
	}

	// implement here constants used by this extension

	/**
	 * NET-8943: session attribute (a {@code List<String>} of service names) holding services that a cart calculation removed
	 * because they were no longer valid. The cart page shows a message for them and clears the attribute.
	 */
	public static final String REMOVED_SERVICES_SESSION_ATTRIBUTE = "productServices.removedServiceNames";
}
