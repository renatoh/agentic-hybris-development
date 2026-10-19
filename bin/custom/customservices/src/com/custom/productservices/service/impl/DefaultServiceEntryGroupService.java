package com.custom.productservices.service.impl;

import de.hybris.platform.core.enums.GroupType;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.order.EntryGroup;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.custom.core.model.ServiceProductModel;
import com.custom.productservices.service.ServiceEntryGroupService;


/**
 * NET-8943 &sect;5.3.
 */
public class DefaultServiceEntryGroupService implements ServiceEntryGroupService
{
	@Override
	public boolean isServiceEntry(final AbstractOrderEntryModel entry)
	{
		return entry != null && entry.getProduct() instanceof ServiceProductModel;
	}

	@Override
	public Optional<EntryGroup> getServiceGroup(final AbstractOrderEntryModel entry)
	{
		final AbstractOrderModel order = entry == null ? null : entry.getOrder();
		final Set<Integer> numbers = entry == null ? null : entry.getEntryGroupNumbers();
		if (order == null || numbers == null || numbers.isEmpty() || order.getEntryGroups() == null)
		{
			return Optional.empty();
		}
		return order.getEntryGroups().stream() //
				.filter(group -> GroupType.SERVICE.equals(group.getGroupType()) && numbers.contains(group.getGroupNumber())) //
				.findFirst();
	}

	@Override
	public Optional<AbstractOrderEntryModel> getProductEntry(final AbstractOrderEntryModel serviceEntry)
	{
		return getServiceGroup(serviceEntry).flatMap(
				group -> membersOf(serviceEntry.getOrder(), group).stream().filter(e -> !isServiceEntry(e)).findFirst());
	}

	@Override
	public List<AbstractOrderEntryModel> getServiceEntries(final AbstractOrderEntryModel productEntry)
	{
		return getServiceGroup(productEntry) //
				.map(group -> membersOf(productEntry.getOrder(), group).stream().filter(this::isServiceEntry)
						.collect(Collectors.toList())) //
				.orElse(Collections.emptyList());
	}

	protected List<AbstractOrderEntryModel> membersOf(final AbstractOrderModel order, final EntryGroup group)
	{
		return order.getEntries().stream() //
				.filter(e -> e.getEntryGroupNumbers() != null && e.getEntryGroupNumbers().contains(group.getGroupNumber())) //
				.sorted(Comparator.comparing(AbstractOrderEntryModel::getEntryNumber, Comparator.nullsLast(Comparator.naturalOrder()))) //
				.collect(Collectors.toList());
	}
}
