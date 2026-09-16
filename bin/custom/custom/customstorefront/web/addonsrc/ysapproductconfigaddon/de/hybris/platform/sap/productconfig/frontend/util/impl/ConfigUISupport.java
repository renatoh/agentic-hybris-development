/*
 * Copyright (c) 2025 SAP SE or an SAP affiliate company. All rights reserved.
 */
package de.hybris.platform.sap.productconfig.frontend.util.impl;

import de.hybris.platform.core.Registry;
import de.hybris.platform.sap.productconfig.facades.ConfigurationExpertModeFacade;
import de.hybris.platform.sap.productconfig.facades.CsticData;

import java.util.List;


/**
 * Utility class to be called directly from the xhtml UI-components (tags)
 */
public class ConfigUISupport
{

	private static ConfigurationExpertModeFacade configurationExpertModeFacade;

	private ConfigUISupport()
	{
		// Utility class - private constructor
	}

	/**
	 * @return The status of the expert mode
	 */
	public static boolean isExpertModeEnabled()
	{
		return getExpertModeFacade().isExpertModeActive();
	}

	/**
	 * @param cstics
	 *           list of cstics
	 * @return <code>true</code>, only if the list of cstics has at least one required cstic
	 */
	public static boolean hasRequiredCstic(final List<CsticData> cstics)
	{
		if (cstics == null)
		{
			return false;
		}

		return cstics.stream().anyMatch(cstic -> cstic.isRequired());
	}

	protected static ConfigurationExpertModeFacade getExpertModeFacade()
	{
		if (configurationExpertModeFacade == null)
		{
			configurationExpertModeFacade = (ConfigurationExpertModeFacade) Registry.getApplicationContext().getBean("sapProductConfigExpertModeFacade");
		}
		return configurationExpertModeFacade;
	}
}
