package com.custom.productservices.interceptor;

import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.CartEntryModel;
import de.hybris.platform.servicelayer.interceptor.InterceptorContext;
import de.hybris.platform.servicelayer.interceptor.InterceptorException;
import de.hybris.platform.servicelayer.interceptor.RemoveInterceptor;
import de.hybris.platform.servicelayer.interceptor.PersistenceOperation;

import com.custom.productservices.service.ServiceEntryGroupService;


/**
 * NET-8943 &sect;5.4 cascade: whatever removes a product cart entry (quantity 0, remove link, empty cart, restoration),
 * its service entries go with it. Empty {@code SERVICE} groups are cleaned by
 * {@code CartServiceSelectionService.removeEmptyServiceGroups}.
 */
public class ServiceEntryRemoveInterceptor implements RemoveInterceptor<CartEntryModel>
{
	private ServiceEntryGroupService serviceEntryGroupService;

	@Override
	public void onRemove(final CartEntryModel entry, final InterceptorContext ctx) throws InterceptorException
	{
		if (serviceEntryGroupService.isServiceEntry(entry) || entry.getOrder() == null)
		{
			return;
		}
		for (final AbstractOrderEntryModel serviceEntry : serviceEntryGroupService.getServiceEntries(entry))
		{
			if (!ctx.isRemoved(serviceEntry))
			{
				ctx.registerElementFor(serviceEntry, PersistenceOperation.DELETE);
			}
		}
	}

	public void setServiceEntryGroupService(final ServiceEntryGroupService serviceEntryGroupService)
	{
		this.serviceEntryGroupService = serviceEntryGroupService;
	}
}
