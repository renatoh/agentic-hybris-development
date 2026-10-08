package com.custom.productservices.service;

import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.order.EntryGroup;

import java.util.List;
import java.util.Optional;


/**
 * NET-8943 &sect;5.3: reads the linkage between a product entry and its service entries, which is expressed through an
 * {@link EntryGroup} of type {@code GroupType.SERVICE}. Read-only.
 */
public interface ServiceEntryGroupService
{
	/**
	 * @return true if the entry's product is a {@code ServiceProduct}
	 */
	boolean isServiceEntry(AbstractOrderEntryModel entry);

	/**
	 * @return the {@code SERVICE} group the entry belongs to, if any
	 */
	Optional<EntryGroup> getServiceGroup(AbstractOrderEntryModel entry);

	/**
	 * @param serviceEntry
	 *           an entry for a service product
	 * @return the single non-service entry sharing the service entry's {@code SERVICE} group, if there is one
	 */
	Optional<AbstractOrderEntryModel> getProductEntry(AbstractOrderEntryModel serviceEntry);

	/**
	 * @param productEntry
	 *           a non-service entry
	 * @return the service entries sharing the product entry's {@code SERVICE} group, in entry number order; empty if
	 *         none
	 */
	List<AbstractOrderEntryModel> getServiceEntries(AbstractOrderEntryModel productEntry);
}
