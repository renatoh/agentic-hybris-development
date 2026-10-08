/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.productservices.cart;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commerceservices.order.CommerceCartModificationException;
import de.hybris.platform.commerceservices.service.data.CommerceCartParameter;
import de.hybris.platform.core.model.product.ProductModel;

import org.junit.Test;

import com.custom.core.model.ServiceProductModel;


/**
 * NET-8943 &sect;4.2, AC6: the normal add-to-cart path rejects service products and leaves every other product alone.
 */
@UnitTest
public class ServiceProductAddToCartValidatorTest
{
	private final ServiceProductAddToCartValidator validator = new ServiceProductAddToCartValidator();

	private static CommerceCartParameter parameterFor(final ProductModel product)
	{
		final CommerceCartParameter parameter = new CommerceCartParameter();
		parameter.setProduct(product);
		return parameter;
	}

	@Test
	public void shouldSupportOnlyServiceProducts()
	{
		assertTrue(validator.supports(parameterFor(new ServiceProductModel())));
		assertFalse(validator.supports(parameterFor(new ProductModel())));
		assertFalse(validator.supports(parameterFor(null)));
		assertFalse(validator.supports(null));
	}

	@Test(expected = CommerceCartModificationException.class)
	public void shouldRejectAddingAServiceProduct() throws CommerceCartModificationException
	{
		validator.validate(parameterFor(new ServiceProductModel()));
	}
}
