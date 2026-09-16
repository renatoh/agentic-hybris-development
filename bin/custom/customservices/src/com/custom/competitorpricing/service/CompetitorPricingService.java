package com.custom.competitorpricing.service;

import java.util.List;

import com.custom.competitorpricing.dto.CompetitorPriceDto;


/**
 * Supplies competitor pricing for every product known to the external pricing system (NET-8939).
 * <p>
 * Which implementation is active is decided purely by the Spring alias
 * <code>competitorPricingService</code> in <code>customservices-spring.xml</code>.
 */
public interface CompetitorPricingService
{
	/**
	 * Returns competitor pricing for all products of one tenant in one call.
	 * <p>
	 * Either the full list is returned, or none of it is - a transport/parse failure always throws
	 * {@link CompetitorPricingException} rather than returning a partial or empty list.
	 *
	 * @param tenantId
	 *           identifies the tenant/store towards the external pricing system - not a hybris
	 *           concept (not a <code>BaseStore.uid</code>), supplied per <code>CronJob</code>
	 *           instance
	 * @return the competitor prices, never <code>null</code>
	 * @throws CompetitorPricingException
	 *            if the competitor pricing cannot be retrieved
	 */
	List<CompetitorPriceDto> getCompetitorPricing(String tenantId);
}
