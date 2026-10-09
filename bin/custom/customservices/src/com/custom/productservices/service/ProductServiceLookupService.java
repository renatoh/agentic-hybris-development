package com.custom.productservices.service;

import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.europe1.enums.ProductPriceGroup;
import de.hybris.platform.jalo.order.price.PriceInformation;

import java.util.List;
import java.util.Optional;

import com.custom.core.enums.ServicePriceCondition;
import com.custom.core.model.ServiceProductModel;


/**
 * NET-8943 &sect;5.1: which services a product offers and what they cost for it.
 */
public interface ProductServiceLookupService
{
	/**
	 * @param product
	 *           a physical product
	 * @return the targets of the product's active {@code SERVICE} {@code ProductReference}s that are
	 *         {@link ServiceProductModel}s, in reference order. Empty if the product has no
	 *         {@code servicePriceCondition}.
	 */
	List<ServiceProductModel> getAvailableServices(ProductModel product);

	/**
	 * @return the {@code ProductPriceGroup} with code {@code <serviceCode>_<conditionCode>}, or empty if there is none
	 */
	Optional<ProductPriceGroup> getServicePriceGroup(ServiceProductModel service, ServicePriceCondition condition);

	/**
	 * Single source of the displayed service price for the session currency, user and date. Empty if the product has no
	 * condition, no price group exists, or no price row matches.
	 */
	Optional<PriceInformation> getServicePrice(ServiceProductModel service, ProductModel product);
}
