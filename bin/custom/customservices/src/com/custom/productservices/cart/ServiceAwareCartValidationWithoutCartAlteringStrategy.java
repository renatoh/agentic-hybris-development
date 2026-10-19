package com.custom.productservices.cart;

import de.hybris.platform.commerceservices.order.CommerceCartModification;
import de.hybris.platform.commerceservices.strategies.impl.CartValidationWithoutCartAlteringStrategy;
import de.hybris.platform.core.model.order.CartEntryModel;


/**
 * NET-8943: read-only twin of {@link ServiceAwareCartValidationStrategy}; without it a service entry is reported as
 * unavailable. See {@link ServiceCartEntryValidation}. Upgrade check: compare with
 * {@code CartValidationWithoutCartAlteringStrategy.validateCartEntry}.
 */
public class ServiceAwareCartValidationWithoutCartAlteringStrategy extends CartValidationWithoutCartAlteringStrategy
{
	@Override
	protected CommerceCartModification validateCartEntry(final CartEntryModel cartEntryModel)
	{
		if (ServiceCartEntryValidation.isServiceEntry(cartEntryModel))
		{
			return ServiceCartEntryValidation.validServiceEntry(cartEntryModel);
		}
		return super.validateCartEntry(cartEntryModel);
	}
}
