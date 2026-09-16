/*
 * Copyright (c) 2025 SAP SE or an SAP affiliate company. All rights reserved.
 */
package de.hybris.platform.sap.productconfig.frontend.util.impl;

import de.hybris.platform.sap.productconfig.frontend.OverviewUiData;

import org.apache.commons.collections4.CollectionUtils;


/**
 * Utility class for jsps and tags related to the Configuration Overview Page.
 */
public class ConfigOverviewFilterEvaluator
{

	private ConfigOverviewFilterEvaluator()
	{
		// Utility class - private constructor
	}

	/**
	 * @param overviewData
	 * @return <code>true</code> only if there is a least either one cstic- or one group-related filter active
	 */
	public static boolean hasAppliedFilters(final OverviewUiData overviewData)
	{
		if (CollectionUtils.isNotEmpty(overviewData.getCsticFilterList())
				|| CollectionUtils.isNotEmpty(overviewData.getGroupFilterList()))
		{
			return overviewData.getCsticFilterList().stream().anyMatch(filter -> filter.isSelected())
					|| overviewData.getGroupFilterList().stream().anyMatch(filter -> filter.isSelected());
		}
		return false;
	}
}
