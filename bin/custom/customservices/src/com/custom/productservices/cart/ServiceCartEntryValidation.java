package com.custom.productservices.cart;

import de.hybris.platform.commerceservices.order.CommerceCartModification;
import de.hybris.platform.commerceservices.order.CommerceCartModificationStatus;
import de.hybris.platform.core.model.order.CartEntryModel;

import com.custom.core.model.ServiceProductModel;


/**
 * NET-8943: the "a service entry is always valid" rule shared by {@link ServiceAwareCartValidationStrategy} and
 * {@link ServiceAwareCartValidationWithoutCartAlteringStrategy}. The platform validation looks every entry's product up
 * by code, and service products are hidden from storefront lookups ({@code Frontend_ServiceProduct}), so a service entry
 * would be removed or reported as unavailable. Services are not physical, so there is no stock to check either. Whether a
 * service is still valid is checked by the calculation hook and the place-order guard.
 */
public final class ServiceCartEntryValidation
{
	private ServiceCartEntryValidation()
	{
		// static helper
	}

	public static boolean isServiceEntry(final CartEntryModel entry)
	{
		return entry != null && entry.getProduct() instanceof ServiceProductModel;
	}

	/**
	 * @return a {@link CommerceCartModificationStatus#SUCCESS} modification that keeps the entry's quantity unchanged
	 */
	public static CommerceCartModification validServiceEntry(final CartEntryModel entry)
	{
		final long quantity = entry.getQuantity().longValue();
		final CommerceCartModification modification = new CommerceCartModification();
		modification.setStatusCode(CommerceCartModificationStatus.SUCCESS);
		modification.setQuantityAdded(quantity);
		modification.setQuantity(quantity);
		modification.setEntry(entry);
		return modification;
	}
}
