/*
 * Copyright (c) 2025 SAP SE or an SAP affiliate company. All rights reserved.
 */
package de.hybris.platform.sap.productconfig.frontend.util.impl;

import de.hybris.platform.sap.productconfig.facades.UiType;


/**
 * Utility class granting access to UI-Connstants directly to the xhtml UI-components (tags)
 */
public class ConstantHandler
{
	/**
	 * General group, used for all cstic, which are not assigned to another group
	 */
	public static final String GENERAL_GROUP_NAME = "_GEN";

	private ConstantHandler()
	{
		// private constructor - Utility class should hide public constructor
	}

	/**
	 * @return name of the general group
	 */
	public static String getGeneralGroupName()
	{
		return GENERAL_GROUP_NAME;
	}

	/**
	 * @return UI-type name for non implemented cstics
	 */
	public static String getNotImplemented()
	{
		return UiType.NOT_IMPLEMENTED.toString();
	}
}
