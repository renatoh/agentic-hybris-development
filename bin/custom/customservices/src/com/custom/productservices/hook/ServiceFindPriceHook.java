package com.custom.productservices.hook;

import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.europe1.enums.ProductPriceGroup;
import de.hybris.platform.jalo.order.price.PriceInformation;
import de.hybris.platform.order.exceptions.CalculationException;
import de.hybris.platform.order.strategies.calculation.FindPriceHook;
import de.hybris.platform.order.strategies.calculation.pdt.FindPDTValueInfoStrategy;
import de.hybris.platform.order.strategies.calculation.pdt.criteria.PDTCriteriaFactory;
import de.hybris.platform.order.strategies.calculation.pdt.criteria.PriceValueInfoCriteria;
import de.hybris.platform.util.PriceValue;

import java.util.List;
import java.util.Optional;

import com.custom.core.model.ServiceProductModel;
import com.custom.productservices.exceptions.ServicePriceNotFoundException;
import com.custom.productservices.pricing.ServicePriceCriteria;
import com.custom.productservices.service.ProductServiceLookupService;
import com.custom.productservices.service.ServiceEntryGroupService;


/**
 * NET-8943 &sect;5.2: supplies the base price of a service entry. The standard price criteria for the entry are built
 * by the platform and only the product price group is replaced by the one resolved from the linked product's
 * {@code servicePriceCondition}; the platform's own price lookup then ranks the rows. If nothing can be priced the
 * calculation fails: a service is never charged at {@code defaultPrice}.
 */
public class ServiceFindPriceHook implements FindPriceHook
{
	private ServiceEntryGroupService serviceEntryGroupService;
	private ProductServiceLookupService productServiceLookupService;
	private PDTCriteriaFactory pdtCriteriaFactory;
	private FindPDTValueInfoStrategy<PriceValue, PriceInformation, PriceValueInfoCriteria> findPriceValueInfoStrategy;

	@Override
	public boolean isApplicable(final AbstractOrderEntryModel entry)
	{
		return entry != null && entry.getProduct() instanceof ServiceProductModel;
	}

	@Override
	public PriceValue findCustomBasePrice(final AbstractOrderEntryModel entry, final PriceValue defaultPrice)
	{
		final ServiceProductModel service = (ServiceProductModel) entry.getProduct();

		final ProductModel product = serviceEntryGroupService.getProductEntry(entry).map(AbstractOrderEntryModel::getProduct)
				.orElseThrow(() -> failure(entry, "no linked product entry"));
		if (product.getServicePriceCondition() == null)
		{
			throw failure(entry, "product " + product.getCode() + " has no servicePriceCondition");
		}
		final ProductPriceGroup group = productServiceLookupService.getServicePriceGroup(service, product.getServicePriceCondition())
				.orElseThrow(() -> failure(entry, "no ProductPriceGroup for condition " + product.getServicePriceCondition()));

		final List<PriceValue> values;
		try
		{
			final PriceValueInfoCriteria standard = pdtCriteriaFactory.priceValueCriteriaFromOrderEntry(entry);
			values = findPriceValueInfoStrategy.getPDTValues(ServicePriceCriteria.withProductPriceGroup(standard, group));
		}
		catch (final CalculationException e)
		{
			throw new ServicePriceNotFoundException("Cannot price service entry " + entry.getEntryNumber() + ": " + e.getMessage());
		}
		return Optional.ofNullable(values).filter(v -> !v.isEmpty()).map(v -> v.get(0))
				.orElseThrow(() -> failure(entry, "no price row for group " + group.getCode()));
	}

	private ServicePriceNotFoundException failure(final AbstractOrderEntryModel entry, final String reason)
	{
		return new ServicePriceNotFoundException(
				"Cannot price service " + entry.getProduct().getCode() + " (entry " + entry.getEntryNumber() + "): " + reason);
	}

	public void setServiceEntryGroupService(final ServiceEntryGroupService serviceEntryGroupService)
	{
		this.serviceEntryGroupService = serviceEntryGroupService;
	}

	public void setProductServiceLookupService(final ProductServiceLookupService productServiceLookupService)
	{
		this.productServiceLookupService = productServiceLookupService;
	}

	public void setPdtCriteriaFactory(final PDTCriteriaFactory pdtCriteriaFactory)
	{
		this.pdtCriteriaFactory = pdtCriteriaFactory;
	}

	public void setFindPriceValueInfoStrategy(
			final FindPDTValueInfoStrategy<PriceValue, PriceInformation, PriceValueInfoCriteria> findPriceValueInfoStrategy)
	{
		this.findPriceValueInfoStrategy = findPriceValueInfoStrategy;
	}
}
