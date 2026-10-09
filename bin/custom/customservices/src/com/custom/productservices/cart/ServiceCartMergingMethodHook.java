package com.custom.productservices.cart;

import de.hybris.platform.commerceservices.order.hook.CommerceCartMergingMethodHook;
import de.hybris.platform.core.enums.GroupType;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.order.EntryGroup;
import de.hybris.platform.servicelayer.model.ModelService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.custom.productservices.exceptions.CartServiceSelectionException;
import com.custom.productservices.service.ServiceEntryGroupService;


/**
 * NET-8943 &sect;5.4 cart merge on login. Before the platform merges the anonymous ("from") cart, the services are taken
 * out of it and remembered per product, so the platform merges plain product lines. Afterwards the services are
 * re-attached through {@link CartServiceSelectionService#addService}, which keeps one service entry per product line at
 * the merged quantity.
 */
public class ServiceCartMergingMethodHook implements CommerceCartMergingMethodHook
{
	private static final Logger LOG = LoggerFactory.getLogger(ServiceCartMergingMethodHook.class);

	/** from-cart code -> (product code -> selected service codes). Cleared in afterCartMerge. */
	private final ThreadLocal<Map<String, Map<String, List<String>>>> pending = ThreadLocal.withInitial(LinkedHashMap::new);

	private ServiceEntryGroupService serviceEntryGroupService;
	private CartServiceSelectionService cartServiceSelectionService;
	private ModelService modelService;

	@Override
	public void beforeCartMerge(final CartModel fromCart, final CartModel toCart)
	{
		pending.remove(); // never carry state over from a merge that did not reach afterCartMerge
		final Map<String, List<String>> selections = new LinkedHashMap<>();
		final List<AbstractOrderEntryModel> serviceEntries = new ArrayList<>();
		if (fromCart.getEntries() != null)
		{
			for (final AbstractOrderEntryModel entry : fromCart.getEntries())
			{
				if (!serviceEntryGroupService.isServiceEntry(entry))
				{
					final List<AbstractOrderEntryModel> services = serviceEntryGroupService.getServiceEntries(entry);
					if (!services.isEmpty())
					{
						selections.computeIfAbsent(entry.getProduct().getCode(), k -> new ArrayList<>())
								.addAll(services.stream().map(s -> s.getProduct().getCode()).collect(Collectors.toList()));
						serviceEntries.addAll(services);
					}
				}
			}
		}
		pending.get().put(fromCart.getCode(), selections);
		if (serviceEntries.isEmpty())
		{
			return;
		}
		modelService.removeAll(serviceEntries);
		// the cart's entry list still holds the removed models: reload it before the cart is saved again
		modelService.refresh(fromCart);
		stripServiceGroups(fromCart);
		modelService.refresh(fromCart);
	}

	@Override
	public void afterCartMerge(final CartModel fromCart, final CartModel toCart)
	{
		final Map<String, List<String>> selections = pending.get().remove(fromCart.getCode());
		if (pending.get().isEmpty())
		{
			pending.remove();
		}
		if (selections == null || selections.isEmpty())
		{
			return;
		}
		modelService.save(toCart);
		for (final Map.Entry<String, List<String>> selection : selections.entrySet())
		{
			for (final String serviceCode : new LinkedHashSet<>(selection.getValue()))
			{
				toCart.getEntries().stream() //
						.filter(e -> !serviceEntryGroupService.isServiceEntry(e)
								&& selection.getKey().equals(e.getProduct().getCode())) //
						.findFirst() //
						.ifPresent(productEntry -> attach(toCart, productEntry, serviceCode));
			}
		}
	}

	private void attach(final CartModel toCart, final AbstractOrderEntryModel productEntry, final String serviceCode)
	{
		try
		{
			cartServiceSelectionService.addService(toCart, productEntry.getEntryNumber().intValue(), serviceCode);
		}
		catch (final CartServiceSelectionException e)
		{
			LOG.warn("Could not re-attach service {} to {} after cart merge: {}", serviceCode,
					productEntry.getProduct().getCode(), e.getMessage());
		}
	}

	private void stripServiceGroups(final CartModel cart)
	{
		if (cart.getEntryGroups() == null)
		{
			return;
		}
		final Set<Integer> serviceGroups = cart.getEntryGroups().stream().filter(g -> GroupType.SERVICE.equals(g.getGroupType()))
				.map(EntryGroup::getGroupNumber).collect(Collectors.toSet());
		final List<AbstractOrderEntryModel> touched = new ArrayList<>();
		for (final AbstractOrderEntryModel entry : cart.getEntries())
		{
			if (entry.getEntryGroupNumbers() != null && entry.getEntryGroupNumbers().stream().anyMatch(serviceGroups::contains))
			{
				final Set<Integer> remaining = new HashSet<>(entry.getEntryGroupNumbers());
				remaining.removeAll(serviceGroups);
				entry.setEntryGroupNumbers(remaining);
				touched.add(entry);
			}
		}
		cart.setEntryGroups(cart.getEntryGroups().stream().filter(g -> !GroupType.SERVICE.equals(g.getGroupType()))
				.collect(Collectors.toList()));
		modelService.saveAll(touched);
		modelService.save(cart);
	}

	public void setServiceEntryGroupService(final ServiceEntryGroupService serviceEntryGroupService)
	{
		this.serviceEntryGroupService = serviceEntryGroupService;
	}

	public void setCartServiceSelectionService(final CartServiceSelectionService cartServiceSelectionService)
	{
		this.cartServiceSelectionService = cartServiceSelectionService;
	}

	public void setModelService(final ModelService modelService)
	{
		this.modelService = modelService;
	}
}
