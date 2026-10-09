package com.custom.productservices.pricing;

import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.order.exceptions.CalculationException;
import de.hybris.platform.order.strategies.calculation.FindPriceHook;
import de.hybris.platform.order.strategies.calculation.impl.servicelayer.DefaultSLFindPriceStrategy;
import de.hybris.platform.util.PriceValue;

import java.util.Optional;

import com.custom.productservices.hook.ServiceFindPriceHook;


/**
 * NET-8943 &sect;5.2a. {@link DefaultSLFindPriceStrategy} runs its default lookup <em>before</em> the hooks, and that
 * lookup throws for a service product (no product-specific price row). For entries where the
 * {@link ServiceFindPriceHook} is applicable the default lookup is therefore skipped. Every other entry, and every other
 * hook, goes through the platform code unchanged.
 * <p>
 * Upgrade check: re-compare with {@code DefaultSLFindPriceStrategy.findBasePrice} on every SAP upgrade.
 */
public class ServiceAwareSLFindPriceStrategy extends DefaultSLFindPriceStrategy
{
	@Override
	public PriceValue findBasePrice(final AbstractOrderEntryModel entry) throws CalculationException
	{
		final Optional<FindPriceHook> serviceHook = getFindPriceHooks().stream() //
				.filter(ServiceFindPriceHook.class::isInstance) //
				.filter(hook -> hook.isApplicable(entry)) //
				.findFirst();
		if (serviceHook.isPresent())
		{
			return serviceHook.get().findCustomBasePrice(entry, null);
		}
		return super.findBasePrice(entry);
	}
}
