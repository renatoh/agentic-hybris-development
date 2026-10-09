/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.productservices.service.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.core.enums.GroupType;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.CartEntryModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.order.EntryGroup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;

import com.custom.core.model.ServiceProductModel;


/**
 * NET-8943 &sect;5.3: a service entry is linked to its product entry through an {@link EntryGroup} of type
 * {@code GroupType.SERVICE}. No collaborators - real models and real {@link EntryGroup} DTOs.
 */
@UnitTest
public class DefaultServiceEntryGroupServiceTest
{
	private DefaultServiceEntryGroupService service;
	private CartModel cart;

	@Before
	public void setUp()
	{
		service = new DefaultServiceEntryGroupService();
		cart = new CartModel();
		cart.setEntries(new ArrayList<>());
		cart.setEntryGroups(new ArrayList<>());
	}

	private ProductModel product(final String code)
	{
		final ProductModel product = new ProductModel();
		product.setCode(code);
		return product;
	}

	private ServiceProductModel serviceProduct(final String code)
	{
		final ServiceProductModel product = new ServiceProductModel();
		product.setCode(code);
		return product;
	}

	private CartEntryModel entry(final int entryNumber, final ProductModel product, final Integer... groupNumbers)
	{
		final CartEntryModel entry = new CartEntryModel();
		entry.setEntryNumber(Integer.valueOf(entryNumber));
		entry.setProduct(product);
		entry.setOrder(cart);
		entry.setEntryGroupNumbers(new HashSet<>(Arrays.asList(groupNumbers)));
		final List<AbstractOrderEntryModel> entries = new ArrayList<>(cart.getEntries());
		entries.add(entry);
		cart.setEntries(entries);
		return entry;
	}

	private EntryGroup group(final int number, final GroupType type)
	{
		final EntryGroup group = new EntryGroup();
		group.setGroupNumber(Integer.valueOf(number));
		group.setGroupType(type);
		final List<EntryGroup> groups = new ArrayList<>(cart.getEntryGroups());
		groups.add(group);
		cart.setEntryGroups(groups);
		return group;
	}

	@Test
	public void shouldRecogniseAServiceEntryByItsProductType()
	{
		assertTrue(service.isServiceEntry(entry(0, serviceProduct("SVC_INSTALLATION"))));
		assertFalse(service.isServiceEntry(entry(1, product("DISHWASHER"))));
		assertFalse(service.isServiceEntry(entry(2, null)));
		assertFalse(service.isServiceEntry(null));
	}

	@Test
	public void shouldFindNoGroupWhenTheCartHasNoGroups()
	{
		final CartEntryModel productEntry = entry(0, product("DISHWASHER"), 1);
		cart.setEntryGroups(null);

		assertFalse(service.getServiceGroup(productEntry).isPresent());
		assertFalse(service.getProductEntry(entry(1, serviceProduct("SVC_INSTALLATION"), 1)).isPresent());
		assertTrue(service.getServiceEntries(productEntry).isEmpty());
	}

	@Test
	public void shouldFindNoGroupWhenTheEntryIsInNoGroup()
	{
		group(1, GroupType.SERVICE);
		final CartEntryModel productEntry = entry(0, product("DISHWASHER"));
		final CartEntryModel nullNumbers = entry(1, product("FRIDGE"));
		nullNumbers.setEntryGroupNumbers(null);

		assertFalse(service.getServiceGroup(productEntry).isPresent());
		assertFalse(service.getServiceGroup(nullNumbers).isPresent());
		assertTrue(service.getServiceEntries(productEntry).isEmpty());
	}

	@Test
	public void shouldIgnoreAGroupOfAnotherType()
	{
		group(1, GroupType.STANDALONE);
		final CartEntryModel productEntry = entry(0, product("DISHWASHER"), 1);
		final CartEntryModel serviceEntry = entry(1, serviceProduct("SVC_INSTALLATION"), 1);

		assertFalse(service.getServiceGroup(productEntry).isPresent());
		assertFalse(service.getProductEntry(serviceEntry).isPresent());
		assertTrue(service.getServiceEntries(productEntry).isEmpty());
	}

	@Test
	public void shouldPickTheServiceGroupWhenTheEntryIsAlsoInAnotherTypeOfGroup()
	{
		group(1, GroupType.STANDALONE);
		final EntryGroup serviceGroup = group(2, GroupType.SERVICE);
		final CartEntryModel productEntry = entry(0, product("DISHWASHER"), 1, 2);

		final Optional<EntryGroup> found = service.getServiceGroup(productEntry);

		assertTrue(found.isPresent());
		assertSame(serviceGroup, found.get());
	}

	@Test
	public void shouldLinkTwoServicesInOneGroupToTheirProductInEntryNumberOrder()
	{
		group(1, GroupType.SERVICE);
		// added out of entry-number order on purpose
		final CartEntryModel warranty = entry(2, serviceProduct("SVC_WARRANTY_3Y"), 1);
		final CartEntryModel productEntry = entry(0, product("DISHWASHER"), 1);
		final CartEntryModel installation = entry(1, serviceProduct("SVC_INSTALLATION"), 1);

		assertEquals(Arrays.asList(installation, warranty), service.getServiceEntries(productEntry));
		assertSame(productEntry, service.getProductEntry(installation).get());
		assertSame(productEntry, service.getProductEntry(warranty).get());
	}

	@Test
	public void shouldNeverLinkAServiceToAnotherProductLinesEntry()
	{
		group(1, GroupType.SERVICE);
		group(2, GroupType.SERVICE);
		final CartEntryModel dishwasher = entry(0, product("DISHWASHER"), 1);
		final CartEntryModel dishwasherInstallation = entry(1, serviceProduct("SVC_INSTALLATION"), 1);
		final CartEntryModel fridge = entry(2, product("FRIDGE"), 2);
		final CartEntryModel fridgeInstallation = entry(3, serviceProduct("SVC_INSTALLATION"), 2);
		final CartEntryModel ungrouped = entry(4, product("TOASTER"));

		assertSame(dishwasher, service.getProductEntry(dishwasherInstallation).get());
		assertSame(fridge, service.getProductEntry(fridgeInstallation).get());
		assertEquals(Collections.singletonList(dishwasherInstallation), service.getServiceEntries(dishwasher));
		assertEquals(Collections.singletonList(fridgeInstallation), service.getServiceEntries(fridge));
		assertTrue(service.getServiceEntries(ungrouped).isEmpty());
	}

	@Test
	public void shouldFindNoProductEntryForAServiceWhoseGroupHasOnlyServices()
	{
		group(1, GroupType.SERVICE);
		final CartEntryModel orphan = entry(0, serviceProduct("SVC_INSTALLATION"), 1);
		entry(1, serviceProduct("SVC_WARRANTY_3Y"), 1);

		assertFalse(service.getProductEntry(orphan).isPresent());
	}

	@Test
	public void shouldReturnEmptyForAProductEntryWithAGroupButNoServices()
	{
		group(1, GroupType.SERVICE);
		final CartEntryModel productEntry = entry(0, product("DISHWASHER"), 1);

		assertTrue(service.getServiceEntries(productEntry).isEmpty());
	}

	@Test
	public void shouldTolerateANullEntry()
	{
		assertFalse(service.getServiceGroup(null).isPresent());
		assertFalse(service.getProductEntry(null).isPresent());
		assertTrue(service.getServiceEntries(null).isEmpty());
	}
}
