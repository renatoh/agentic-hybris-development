/*
 * Copyright (c) 2019 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.core.jalo;

import de.hybris.platform.jalo.JaloSession;
import de.hybris.platform.jalo.extension.ExtensionManager;
import com.custom.core.constants.CustomCoreConstants;
import com.custom.core.setup.CoreSystemSetup;


/**
 * Do not use, please use {@link CoreSystemSetup} instead.
 * 
 */
public class CustomCoreManager extends GeneratedCustomCoreManager
{
	public static final CustomCoreManager getInstance()
	{
		final ExtensionManager em = JaloSession.getCurrentSession().getExtensionManager();
		return (CustomCoreManager) em.getExtension(CustomCoreConstants.EXTENSIONNAME);
	}
}
