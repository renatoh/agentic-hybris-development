/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.facades.savedforlater;

import java.util.List;

import com.custom.facades.savedforlater.data.SavedForLaterEntryData;


/**
 * Presentation-layer entry point for the save-for-later feature (NET-8941 section 5.4), fronting
 * {@code SavedForLaterService} (customservices) for the cart page controller. Every method here
 * assumes an authenticated, registered customer - the anonymous-shopper handoff across the login
 * redirect (NET-8941 section 5.4, acceptance criterion 1a) is orchestrated by the controller, not
 * this facade.
 */
public interface SavedForLaterFacade
{
	/**
	 * Removes the given cart entry from the current cart (via the existing cart-entry-removal path)
	 * and adds it to the current customer's saved-for-later list, merging into an existing saved
	 * entry for the same product if one already exists.
	 *
	 * @param entryNumber the cart entry to save for later
	 */
	void saveCartEntryForLater(long entryNumber);

	/**
	 * @return the current customer's saved-for-later entries whose product resolves in the current
	 *         session's own catalog (NET-8941 section 5.3 / acceptance criterion 8)
	 */
	List<SavedForLaterEntryData> getSavedForLaterEntries();

	/**
	 * Moves a saved entry back into the cart via the platform's normal add-to-cart path and removes
	 * it from the saved list. A failure (e.g. now out of stock) leaves the entry in the saved list
	 * (NET-8941 acceptance criterion 5) - the caller is expected to report it the same way a normal
	 * add-to-cart failure would be.
	 *
	 * @param productCode identifies the saved entry (product code is unique per customer - "merge on
	 *                     duplicate" guarantees at most one saved entry per product)
	 * @throws IllegalStateException if the add-to-cart call fails
	 */
	void moveToCart(String productCode);

	/**
	 * Deletes a saved entry without touching the cart (NET-8941 acceptance criterion 6).
	 *
	 * @param productCode identifies the saved entry, see {@link #moveToCart(String)}
	 */
	void removeSavedItem(String productCode);
}
