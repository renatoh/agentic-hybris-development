/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.savedforlater.interceptor;

import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.servicelayer.interceptor.InterceptorContext;
import de.hybris.platform.servicelayer.interceptor.InterceptorException;
import de.hybris.platform.servicelayer.interceptor.RemoveInterceptor;
import de.hybris.platform.servicelayer.model.ModelService;

import java.util.List;


/**
 * Cascade-deletes a customer's {@code SavedForLaterEntry} rows when the customer is removed
 * (NET-8941 section 5.1a / section 8 item 3). The {@code Customer2SavedForLaterEntryRelation} does
 * not carry {@code partof} on its target element - verified against
 * {@code de.hybris.platform.servicelayer.interceptor.impl.PartOfModelRegisterForRemoveInterceptor},
 * the platform's only cascade-on-remove mechanism for a relation, which is driven specifically by
 * that modifier - so without it the relation link is cleaned up automatically but the
 * {@code SavedForLaterEntry} items themselves are not, and are left orphaned unless removed here
 * explicitly.
 */
public class SavedForLaterEntryCustomerRemoveInterceptor implements RemoveInterceptor<CustomerModel>
{
	private ModelService modelService;

	@Override
	public void onRemove(final CustomerModel customer, final InterceptorContext ctx) throws InterceptorException
	{
		final List<?> entries = customer.getSavedForLaterEntries();
		if (entries != null && !entries.isEmpty())
		{
			getModelService().removeAll(entries);
		}
	}

	protected ModelService getModelService()
	{
		return modelService;
	}

	public void setModelService(final ModelService modelService)
	{
		this.modelService = modelService;
	}
}
