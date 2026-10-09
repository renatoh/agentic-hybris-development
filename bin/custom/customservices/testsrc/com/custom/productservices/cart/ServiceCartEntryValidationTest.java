/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.productservices.cart;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commerceservices.order.CommerceCartModification;
import de.hybris.platform.commerceservices.order.CommerceCartModificationStatus;
import de.hybris.platform.core.model.order.CartEntryModel;
import de.hybris.platform.core.model.product.ProductModel;

import org.junit.Test;

import com.custom.core.model.ServiceProductModel;


/**
 * NET-8943 review round 1: the behaviour shared by both service-aware cart validation strategies — what counts as a
 * service entry, and the SUCCESS modification reported for it.
 */
@UnitTest
public class ServiceCartEntryValidationTest
{
	private static CartEntryModel entry(final ProductModel product, final long quantity)
	{
		final CartEntryModel entry = new CartEntryModel();
		entry.setProduct(product);
		entry.setQuantity(Long.valueOf(quantity));
		return entry;
	}

	@Test
	public void shouldRecogniseAServiceEntry()
	{
		assertTrue(ServiceCartEntryValidation.isServiceEntry(entry(new ServiceProductModel(), 1)));
	}

	@Test
	public void shouldNotTreatAPlainProductEntryAsAService()
	{
		assertFalse(ServiceCartEntryValidation.isServiceEntry(entry(new ProductModel(), 1)));
	}

	@Test
	public void shouldNotTreatAnEntryWithoutProductAsAService()
	{
		assertFalse(ServiceCartEntryValidation.isServiceEntry(entry(null, 1)));
	}

	@Test
	public void shouldNotTreatANullEntryAsAService()
	{
		assertFalse(ServiceCartEntryValidation.isServiceEntry(null));
	}

	@Test
	public void shouldReportTheServiceEntryAsSuccessfulWithItsFullQuantity()
	{
		final CartEntryModel installation = entry(new ServiceProductModel(), 3);

		final CommerceCartModification modification = ServiceCartEntryValidation.validServiceEntry(installation);

		assertEquals(CommerceCartModificationStatus.SUCCESS, modification.getStatusCode());
		assertEquals(3L, modification.getQuantityAdded());
		assertEquals(3L, modification.getQuantity());
		assertSame(installation, modification.getEntry());
	}
}
