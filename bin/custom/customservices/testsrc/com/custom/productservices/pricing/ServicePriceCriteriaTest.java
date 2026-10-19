/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.productservices.pricing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.core.model.c2l.CurrencyModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.product.UnitModel;
import de.hybris.platform.core.model.user.UserModel;
import de.hybris.platform.europe1.enums.ProductPriceGroup;
import de.hybris.platform.europe1.enums.UserPriceGroup;
import de.hybris.platform.order.strategies.calculation.pdt.criteria.PDTCriteria.PDTCriteriaTarget;
import de.hybris.platform.order.strategies.calculation.pdt.criteria.PriceValueInfoCriteria;
import de.hybris.platform.order.strategies.calculation.pdt.criteria.impl.DefaultPriceValueInfoCriteria;

import java.util.Date;

import org.junit.Test;


/**
 * NET-8943 &sect;5.1/&sect;5.2: the copied criteria must be identical to the platform-built one except for the product
 * price group, and must keep its purpose (VALUE for cart calculation, INFORMATION for display).
 */
@UnitTest
public class ServicePriceCriteriaTest
{
	private static final ProductPriceGroup ORIGINAL_GROUP = ProductPriceGroup.valueOf("SOME_PRODUCT_GROUP");
	private static final ProductPriceGroup SERVICE_GROUP = ProductPriceGroup.valueOf("SVC_INSTALLATION_MEDIUM");
	private static final UserPriceGroup USER_GROUP = UserPriceGroup.valueOf("B2C_VIP");

	private final ProductModel product = new ProductModel();
	private final UserModel user = new UserModel();
	private final CurrencyModel currency = new CurrencyModel();
	private final UnitModel unit = new UnitModel();
	private final Date date = new Date(1_700_000_000_000L);

	private PriceValueInfoCriteria fullySpecified(final DefaultPriceValueInfoCriteria.Builder builder)
	{
		return builder.withProduct(product) //
				.withProductPriceGroup(ORIGINAL_GROUP) //
				.withUser(user) //
				.withUserPriceGroup(USER_GROUP) //
				.withCurrency(currency) //
				.withQuantity(3L) //
				.withUnit(unit) //
				.withDate(date) //
				.withNet(Boolean.TRUE) //
				.withGiveAwayMode(true) //
				.withEntryRejected(true) //
				.build();
	}

	@Test
	public void shouldReplaceOnlyTheProductPriceGroupAndKeepTheValuePurpose()
	{
		final PriceValueInfoCriteria source = fullySpecified(DefaultPriceValueInfoCriteria.buildForValue());

		final PriceValueInfoCriteria copy = ServicePriceCriteria.withProductPriceGroup(source, SERVICE_GROUP);

		assertNotSame(source, copy);
		assertEquals(PDTCriteriaTarget.VALUE, copy.getPDTCriteriaTarget());
		assertSame(SERVICE_GROUP, copy.getProductGroup());
		assertAllOtherFieldsCopied(copy);
		assertSame("source must be left untouched", ORIGINAL_GROUP, source.getProductGroup());
	}

	@Test
	public void shouldReplaceOnlyTheProductPriceGroupAndKeepTheInformationPurpose()
	{
		final PriceValueInfoCriteria source = fullySpecified(DefaultPriceValueInfoCriteria.buildForInfo());

		final PriceValueInfoCriteria copy = ServicePriceCriteria.withProductPriceGroup(source, SERVICE_GROUP);

		assertEquals(PDTCriteriaTarget.INFORMATION, copy.getPDTCriteriaTarget());
		assertSame(SERVICE_GROUP, copy.getProductGroup());
		assertAllOtherFieldsCopied(copy);
	}

	@Test
	public void shouldSetTheGroupWhenTheSourceHadNone()
	{
		final PriceValueInfoCriteria source = DefaultPriceValueInfoCriteria.buildForValue() //
				.withProduct(product).withUser(user).withCurrency(currency).withQuantity(1L).withUnit(unit).withDate(date)
				.withNet(Boolean.FALSE).build();

		final PriceValueInfoCriteria copy = ServicePriceCriteria.withProductPriceGroup(source, SERVICE_GROUP);

		assertSame(SERVICE_GROUP, copy.getProductGroup());
		assertEquals(Boolean.FALSE, copy.isNet());
		assertEquals(1L, copy.getQuantity());
	}

	private void assertAllOtherFieldsCopied(final PriceValueInfoCriteria copy)
	{
		assertSame(product, copy.getProduct());
		assertSame(user, copy.getUser());
		assertSame(USER_GROUP, copy.getUserGroup());
		assertSame(currency, copy.getCurrency());
		assertEquals(3L, copy.getQuantity());
		assertSame(unit, copy.getUnit());
		assertEquals(date, copy.getDate());
		assertEquals(Boolean.TRUE, copy.isNet());
		assertTrue(copy.isGiveAwayMode());
		assertTrue(copy.isEntryRejected());
	}
}
