package com.custom.productservices.pricing;

import de.hybris.platform.europe1.enums.ProductPriceGroup;
import de.hybris.platform.order.strategies.calculation.pdt.criteria.PriceValueInfoCriteria;
import de.hybris.platform.order.strategies.calculation.pdt.criteria.impl.DefaultPriceValueInfoCriteria;
import de.hybris.platform.order.strategies.calculation.pdt.criteria.PDTCriteria.PDTCriteriaTarget;


/**
 * NET-8943 &sect;5.1/&sect;5.2: copies the standard price criteria built by the platform and replaces only the product
 * price group. Used by both the displayed price and the charged price so the two cannot diverge.
 */
public final class ServicePriceCriteria
{
	private ServicePriceCriteria()
	{
		// static helper
	}

	/**
	 * @param source
	 *           criteria as built by {@code PDTCriteriaFactory}
	 * @param priceGroup
	 *           the service's {@code <serviceCode>_<conditionCode>} group
	 * @return a copy of {@code source} (same purpose, VALUE or INFORMATION) whose product price group is
	 *         {@code priceGroup}
	 */
	public static PriceValueInfoCriteria withProductPriceGroup(final PriceValueInfoCriteria source,
			final ProductPriceGroup priceGroup)
	{
		final DefaultPriceValueInfoCriteria.Builder builder = source.getPDTCriteriaTarget() == PDTCriteriaTarget.VALUE
				? DefaultPriceValueInfoCriteria.buildForValue()
				: DefaultPriceValueInfoCriteria.buildForInfo();
		return builder //
				.withProduct(source.getProduct()) //
				.withProductPriceGroup(priceGroup) //
				.withUser(source.getUser()) //
				.withUserPriceGroup(source.getUserGroup()) //
				.withCurrency(source.getCurrency()) //
				.withQuantity(source.getQuantity()) //
				.withUnit(source.getUnit()) //
				.withDate(source.getDate()) //
				.withNet(source.isNet()) //
				.withGiveAwayMode(source.isGiveAwayMode()) //
				.withEntryRejected(source.isEntryRejected()) //
				.build();
	}
}
