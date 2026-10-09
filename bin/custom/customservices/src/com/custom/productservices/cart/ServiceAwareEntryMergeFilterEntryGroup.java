package com.custom.productservices.cart;

import de.hybris.platform.commerceservices.order.impl.EntryMergeFilterEntryGroup;
import de.hybris.platform.core.enums.GroupType;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.order.EntryGroup;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import com.custom.core.model.ServiceProductModel;


/**
 * NET-8943 &sect;5.3: the platform filter only merges entries with identical group numbers, so a product line that
 * carries a {@code SERVICE} group would never merge with a plain add of the same product. This filter ignores
 * {@code SERVICE}-type groups when comparing and keeps the platform behaviour for every other group type. Service
 * entries are never merged: they are created and changed only by {@code CartServiceSelectionService}.
 */
public class ServiceAwareEntryMergeFilterEntryGroup extends EntryMergeFilterEntryGroup
{
	@Override
	public Boolean apply(@Nonnull final AbstractOrderEntryModel candidate, @Nonnull final AbstractOrderEntryModel target)
	{
		if (candidate.getProduct() instanceof ServiceProductModel || target.getProduct() instanceof ServiceProductModel)
		{
			return Boolean.FALSE;
		}
		final Set<Integer> candidateNumbers = withoutServiceGroups(candidate);
		final Set<Integer> targetNumbers = withoutServiceGroups(target);
		return Boolean.valueOf(candidateNumbers.equals(targetNumbers));
	}

	private Set<Integer> withoutServiceGroups(final AbstractOrderEntryModel entry)
	{
		final Set<Integer> numbers = entry.getEntryGroupNumbers() == null ? new HashSet<>()
				: new HashSet<>(entry.getEntryGroupNumbers());
		final AbstractOrderModel order = entry.getOrder();
		if (order != null && order.getEntryGroups() != null)
		{
			final Set<Integer> serviceGroups = order.getEntryGroups().stream() //
					.filter(g -> GroupType.SERVICE.equals(g.getGroupType())) //
					.map(EntryGroup::getGroupNumber).collect(Collectors.toSet());
			numbers.removeAll(serviceGroups);
		}
		return numbers;
	}
}
