/*
 * Copyright (c) 2019 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.fulfilmentprocess.jalo;

import de.hybris.platform.jalo.JaloSession;
import de.hybris.platform.jalo.extension.ExtensionManager;
import com.custom.fulfilmentprocess.constants.CustomFulfilmentProcessConstants;

public class CustomFulfilmentProcessManager extends GeneratedCustomFulfilmentProcessManager
{
	public static final CustomFulfilmentProcessManager getInstance()
	{
		ExtensionManager em = JaloSession.getCurrentSession().getExtensionManager();
		return (CustomFulfilmentProcessManager) em.getExtension(CustomFulfilmentProcessConstants.EXTENSIONNAME);
	}
	
}
