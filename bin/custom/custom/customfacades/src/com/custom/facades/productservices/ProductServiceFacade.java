package com.custom.facades.productservices;

import java.util.List;

import com.custom.productservices.exceptions.CartServiceSelectionException;


/**
 * NET-8943 &sect;5.7: cart-page operations on the session cart's services.
 */
public interface ProductServiceFacade
{
	/** Attaches the service to the session cart's product line. */
	void addServiceToCart(int productEntryNumber, String serviceCode) throws CartServiceSelectionException;

	/** Detaches the service from the session cart's product line. */
	void removeServiceFromCart(int productEntryNumber, String serviceCode) throws CartServiceSelectionException;

	/**
	 * Drops services of the session cart that are no longer valid (reference or condition removed, no price).
	 *
	 * @return the names of the dropped services, empty if nothing was dropped. Never throws: the cart must always load.
	 */
	List<String> removeInvalidServicesFromCart();

	/** @return true if the session cart's entry with that number is a service entry (its quantity is not editable). */
	boolean isServiceEntry(int entryNumber);
}
