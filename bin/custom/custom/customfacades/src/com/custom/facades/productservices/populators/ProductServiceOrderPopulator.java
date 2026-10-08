package com.custom.facades.productservices.populators;

import de.hybris.platform.commercefacades.order.EntryGroupData;
import de.hybris.platform.commercefacades.order.data.AbstractOrderData;
import de.hybris.platform.commercefacades.order.data.OrderEntryData;
import de.hybris.platform.commercefacades.order.data.OrderEntryGroupData;
import de.hybris.platform.commercefacades.product.PriceDataFactory;
import de.hybris.platform.commercefacades.product.data.PriceData;
import de.hybris.platform.commercefacades.product.data.PriceDataType;
import de.hybris.platform.converters.Populator;
import de.hybris.platform.core.enums.GroupType;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.jalo.order.price.PriceInformation;
import de.hybris.platform.servicelayer.dto.converter.ConversionException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.custom.core.model.ServiceProductModel;
import com.custom.facades.productservices.data.ProductServiceData;
import com.custom.productservices.service.ProductServiceLookupService;
import com.custom.productservices.service.ServiceEntryGroupService;


/**
 * NET-8943 &sect;5.7. Runs after the platform populators. Service entries are not separate lines: they are removed from the
 * entry lists and the {@code SERVICE} root groups are shown like any standalone line, and the services are attached to
 * their product line as {@code selectedServices} (charged price) and, for carts, {@code availableServices}. Line counts
 * reflect product lines only; totals are untouched and still include the services.
 */
public class ProductServiceOrderPopulator<S extends AbstractOrderModel, T extends AbstractOrderData> implements Populator<S, T>
{
	private ServiceEntryGroupService serviceEntryGroupService;
	private ProductServiceLookupService productServiceLookupService;
	private PriceDataFactory priceDataFactory;

	@Override
	public void populate(final S source, final T target) throws ConversionException
	{
		// the platform converts a null source for an empty session cart (DefaultCartFacade.createEmptyCart)
		if (source == null || source.getEntries() == null)
		{
			return;
		}
		final List<AbstractOrderEntryModel> serviceModels = source.getEntries().stream()
				.filter(serviceEntryGroupService::isServiceEntry).collect(Collectors.toList());
		if (serviceModels.isEmpty() && !(source instanceof CartModel))
		{
			return;
		}

		adjustCounts(target, serviceModels);

		if (target.getEntries() == null)
		{
			return; // e.g. mini cart: counts only
		}
		final Map<Integer, OrderEntryData> dataByNumber = new HashMap<>();
		target.getEntries().forEach(e -> dataByNumber.put(e.getEntryNumber(), e));

		final boolean isCart = source instanceof CartModel;
		for (final AbstractOrderEntryModel entry : source.getEntries())
		{
			final OrderEntryData data = dataByNumber.get(entry.getEntryNumber());
			if (data == null || serviceEntryGroupService.isServiceEntry(entry))
			{
				continue;
			}
			final List<AbstractOrderEntryModel> attached = serviceEntryGroupService.getServiceEntries(entry);
			data.setSelectedServices(attached.stream().map(e -> toChargedData(source, e)).collect(Collectors.toList()));
			if (isCart)
			{
				data.setAvailableServices(availableServices(source, entry, attached));
			}
		}

		final Set<Integer> serviceNumbers = serviceModels.stream().map(AbstractOrderEntryModel::getEntryNumber)
				.collect(Collectors.toSet());
		removeServiceEntries(target, serviceNumbers);
	}

	protected List<ProductServiceData> availableServices(final AbstractOrderModel source, final AbstractOrderEntryModel entry,
			final List<AbstractOrderEntryModel> attached)
	{
		final List<ProductServiceData> result = new ArrayList<>();
		for (final ServiceProductModel service : productServiceLookupService.getAvailableServices(entry.getProduct()))
		{
			final AbstractOrderEntryModel selected = attached.stream()
					.filter(e -> Objects.equals(e.getProduct().getCode(), service.getCode())).findFirst().orElse(null);
			if (selected != null)
			{
				result.add(toChargedData(source, selected));
			}
			else
			{
				productServiceLookupService.getServicePrice(service, entry.getProduct())
						.ifPresent(price -> result.add(toOfferedData(source, service, price, entry.getQuantity())));
			}
		}
		return result;
	}

	protected ProductServiceData toChargedData(final AbstractOrderModel order, final AbstractOrderEntryModel serviceEntry)
	{
		final ProductServiceData data = base((ServiceProductModel) serviceEntry.getProduct());
		data.setQuantity(serviceEntry.getQuantity());
		data.setPrice(price(order, serviceEntry.getBasePrice()));
		data.setTotalPrice(price(order, serviceEntry.getTotalPrice()));
		data.setSelected(true);
		return data;
	}

	protected ProductServiceData toOfferedData(final AbstractOrderModel order, final ServiceProductModel service,
			final PriceInformation price, final Long quantity)
	{
		final ProductServiceData data = base(service);
		final double unit = price.getPriceValue().getValue();
		data.setQuantity(quantity);
		data.setPrice(price(order, Double.valueOf(unit)));
		data.setTotalPrice(price(order, Double.valueOf(unit * quantity.longValue())));
		data.setSelected(false);
		return data;
	}

	private ProductServiceData base(final ServiceProductModel service)
	{
		final ProductServiceData data = new ProductServiceData();
		data.setCode(service.getCode());
		data.setName(service.getName());
		data.setDescription(service.getDescription());
		return data;
	}

	private PriceData price(final AbstractOrderModel order, final Double value)
	{
		return priceDataFactory.create(PriceDataType.BUY, BigDecimal.valueOf(value == null ? 0d : value.doubleValue()),
				order.getCurrency().getIsocode());
	}

	protected void adjustCounts(final T target, final List<AbstractOrderEntryModel> serviceModels)
	{
		if (serviceModels.isEmpty())
		{
			return;
		}
		final int quantity = serviceModels.stream().mapToInt(e -> e.getQuantity().intValue()).sum();
		if (target.getTotalItems() != null)
		{
			target.setTotalItems(Integer.valueOf(target.getTotalItems().intValue() - serviceModels.size()));
		}
		if (target.getTotalUnitCount() != null)
		{
			target.setTotalUnitCount(Integer.valueOf(target.getTotalUnitCount().intValue() - quantity));
		}
		if (target.getDeliveryItemsQuantity() != null)
		{
			target.setDeliveryItemsQuantity(Long.valueOf(target.getDeliveryItemsQuantity().longValue() - quantity));
		}
	}

	protected void removeServiceEntries(final T target, final Set<Integer> serviceNumbers)
	{
		if (serviceNumbers.isEmpty())
		{
			return;
		}
		target.setEntries(target.getEntries().stream().filter(e -> !serviceNumbers.contains(e.getEntryNumber()))
				.collect(Collectors.toList()));
		if (target.getRootGroups() != null)
		{
			target.getRootGroups().forEach(group -> showAsStandalone(group, serviceNumbers));
		}
		if (target.getDeliveryOrderGroups() != null)
		{
			target.getDeliveryOrderGroups().forEach(group -> removeFromGroup(group, serviceNumbers));
		}
		if (target.getPickupOrderGroups() != null)
		{
			target.getPickupOrderGroups().forEach(group -> removeFromGroup(group, serviceNumbers));
		}
	}

	private void removeFromGroup(final OrderEntryGroupData group, final Set<Integer> serviceNumbers)
	{
		if (group.getEntries() == null)
		{
			return;
		}
		final long removedQuantity = group.getEntries().stream().filter(e -> serviceNumbers.contains(e.getEntryNumber()))
				.mapToLong(e -> e.getQuantity() == null ? 0 : e.getQuantity().longValue()).sum();
		group.setEntries(group.getEntries().stream().filter(e -> !serviceNumbers.contains(e.getEntryNumber()))
				.collect(Collectors.toList()));
		if (group.getQuantity() != null)
		{
			group.setQuantity(Long.valueOf(group.getQuantity().longValue() - removedQuantity));
		}
	}

	/** A SERVICE root group is only a technical link: show its product line like a standalone one, without service lines. */
	private void showAsStandalone(final EntryGroupData group, final Set<Integer> serviceNumbers)
	{
		if (GroupType.SERVICE.equals(group.getGroupType()))
		{
			group.setGroupType(GroupType.STANDALONE);
			group.setLabel("");
		}
		if (group.getOrderEntries() != null)
		{
			group.setOrderEntries(group.getOrderEntries().stream().filter(e -> !serviceNumbers.contains(e.getEntryNumber()))
					.collect(Collectors.toList()));
		}
		final Collection<EntryGroupData> children = group.getChildren() == null ? Collections.emptyList() : group.getChildren();
		children.forEach(child -> showAsStandalone(child, serviceNumbers));
	}

	public void setServiceEntryGroupService(final ServiceEntryGroupService serviceEntryGroupService)
	{
		this.serviceEntryGroupService = serviceEntryGroupService;
	}

	public void setProductServiceLookupService(final ProductServiceLookupService productServiceLookupService)
	{
		this.productServiceLookupService = productServiceLookupService;
	}

	public void setPriceDataFactory(final PriceDataFactory priceDataFactory)
	{
		this.priceDataFactory = priceDataFactory;
	}
}
