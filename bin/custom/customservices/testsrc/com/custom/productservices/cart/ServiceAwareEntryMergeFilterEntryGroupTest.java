/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.productservices.cart;

import static org.junit.Assert.assertEquals;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.core.enums.GroupType;
import de.hybris.platform.core.model.order.CartEntryModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.order.EntryGroup;

import java.util.Arrays;
import java.util.HashSet;

import org.junit.Before;
import org.junit.Test;

import com.custom.core.model.ServiceProductModel;


/**
 * NET-8943 &sect;5.3, AC17: SERVICE groups must not prevent a plain add of a product from merging into its existing
 * line; every other group type keeps the platform's "identical group numbers" rule; service entries never merge.
 */
@UnitTest
public class ServiceAwareEntryMergeFilterEntryGroupTest
{
	private static final int SERVICE_GROUP = 1;
	private static final int OTHER_SERVICE_GROUP = 2;
	private static final int BUNDLE_GROUP = 3;

	private final ServiceAwareEntryMergeFilterEntryGroup filter = new ServiceAwareEntryMergeFilterEntryGroup();
	private CartModel cart;
	private ProductModel dishwasher;

	@Before
	public void setUp()
	{
		cart = new CartModel();
		cart.setEntryGroups(Arrays.asList(group(SERVICE_GROUP, GroupType.SERVICE), group(OTHER_SERVICE_GROUP, GroupType.SERVICE),
				group(BUNDLE_GROUP, GroupType.STANDALONE)));
		dishwasher = new ProductModel();
		dishwasher.setCode("DISHWASHER");
	}

	private static EntryGroup group(final int number, final GroupType type)
	{
		final EntryGroup group = new EntryGroup();
		group.setGroupNumber(Integer.valueOf(number));
		group.setGroupType(type);
		return group;
	}

	private CartEntryModel entry(final ProductModel product, final Integer... groupNumbers)
	{
		final CartEntryModel entry = new CartEntryModel();
		entry.setProduct(product);
		entry.setOrder(cart);
		entry.setEntryGroupNumbers(groupNumbers == null ? null : new HashSet<>(Arrays.asList(groupNumbers)));
		return entry;
	}

	@Test
	public void shouldMergeAPlainAddIntoALineThatCarriesAServiceGroup()
	{
		assertEquals(Boolean.TRUE, filter.apply(entry(dishwasher), entry(dishwasher, SERVICE_GROUP)));
		assertEquals(Boolean.TRUE, filter.apply(entry(dishwasher, SERVICE_GROUP), entry(dishwasher)));
	}

	@Test
	public void shouldIgnoreServiceGroupsOnBothSides()
	{
		assertEquals(Boolean.TRUE, filter.apply(entry(dishwasher, SERVICE_GROUP), entry(dishwasher, OTHER_SERVICE_GROUP)));
	}

	@Test
	public void shouldTreatAMissingGroupSetLikeAnEmptyOneWhenAServiceGroupIsStripped()
	{
		final CartEntryModel plainAdd = entry(dishwasher);
		plainAdd.setEntryGroupNumbers(null);

		assertEquals(Boolean.TRUE, filter.apply(plainAdd, entry(dishwasher, SERVICE_GROUP)));
	}

	@Test
	public void shouldKeepThePlatformRuleForDifferingNonServiceGroups()
	{
		assertEquals(Boolean.FALSE, filter.apply(entry(dishwasher), entry(dishwasher, BUNDLE_GROUP)));
		assertEquals(Boolean.FALSE, filter.apply(entry(dishwasher, BUNDLE_GROUP), entry(dishwasher)));
	}

	@Test
	public void shouldStillRequireTheSameNonServiceGroupWhenAServiceGroupIsAlsoPresent()
	{
		assertEquals(Boolean.TRUE, filter.apply(entry(dishwasher, BUNDLE_GROUP), entry(dishwasher, BUNDLE_GROUP, SERVICE_GROUP)));
		assertEquals(Boolean.FALSE, filter.apply(entry(dishwasher), entry(dishwasher, BUNDLE_GROUP, SERVICE_GROUP)));
	}

	@Test
	public void shouldTreatAGroupNumberUnknownToTheCartAsANonServiceGroup()
	{
		assertEquals(Boolean.FALSE, filter.apply(entry(dishwasher), entry(dishwasher, 99)));
	}

	@Test
	public void shouldNeverMergeAServiceEntry()
	{
		final ServiceProductModel installation = new ServiceProductModel();
		installation.setCode("SVC_INSTALLATION");

		assertEquals(Boolean.FALSE, filter.apply(entry(installation, SERVICE_GROUP), entry(installation, SERVICE_GROUP)));
		assertEquals(Boolean.FALSE, filter.apply(entry(installation), entry(dishwasher)));
		assertEquals(Boolean.FALSE, filter.apply(entry(dishwasher), entry(installation)));
	}

	@Test
	public void shouldMergeTwoUngroupedEntries()
	{
		assertEquals(Boolean.TRUE, filter.apply(entry(dishwasher), entry(dishwasher)));
	}
}
