package com.custom.productservices.cart;

import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.CartModel;

import java.util.List;

import com.custom.core.model.ServiceProductModel;
import com.custom.productservices.exceptions.CartServiceSelectionException;


/**
 * NET-8943 &sect;5.4: attaches services to and detaches them from cart lines. A selected service is its own cart entry
 * for the shared service product, tied to its product entry by an entry group of type {@code SERVICE}.
 */
public interface CartServiceSelectionService
{
	/**
	 * Adds the service to the product entry. Does nothing if it is already attached. The new entry's quantity equals the
	 * product entry's quantity and the cart is recalculated.
	 *
	 * @throws CartServiceSelectionException
	 *            unknown entry, entry is itself a service entry, service not offered for the product, or no price exists
	 */
	void addService(CartModel cart, int productEntryNumber, String serviceCode) throws CartServiceSelectionException;

	/**
	 * Removes that service entry only (and the now empty {@code SERVICE} group). Does nothing if it is not attached.
	 *
	 * @throws CartServiceSelectionException
	 *            unknown entry or entry is itself a service entry
	 */
	void removeService(CartModel cart, int productEntryNumber, String serviceCode) throws CartServiceSelectionException;

	/**
	 * Sets every service entry of the product entry to the product entry's quantity. Saves, does not recalculate.
	 *
	 * @return true if any quantity changed
	 */
	boolean syncServiceQuantities(AbstractOrderEntryModel productEntry);

	/**
	 * Removes service entries that are no longer valid (reference deactivated or removed, product condition removed, no
	 * matching price row, or no linked product entry) and recalculates if anything was removed.
	 *
	 * @return the removed services, empty if the cart was clean
	 */
	List<ServiceProductModel> removeInvalidServices(CartModel cart);

	/**
	 * Same as {@link #removeInvalidServices(CartModel)} but without the recalculation. For calculation hooks: the cart is
	 * about to be calculated anyway, and an invalid service entry would make that calculation fail.
	 *
	 * @return the removed services
	 */
	List<ServiceProductModel> removeInvalidServicesBeforeCalculation(CartModel cart);

	/**
	 * @return true if any service entry of the cart is not valid (see {@link #removeInvalidServices(CartModel)})
	 */
	boolean hasInvalidServices(CartModel cart);

	/**
	 * Removes {@code SERVICE} groups that have no service entries any more, and their numbers from remaining entries.
	 */
	void removeEmptyServiceGroups(CartModel cart);
}
