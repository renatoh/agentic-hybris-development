/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.productservices.cart.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commerceservices.order.CommerceCartService;
import de.hybris.platform.commerceservices.service.data.CommerceCartParameter;
import de.hybris.platform.core.enums.GroupType;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.CartEntryModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.product.UnitModel;
import de.hybris.platform.core.order.EntryGroup;
import de.hybris.platform.jalo.order.price.PriceInformation;
import de.hybris.platform.order.CartService;
import de.hybris.platform.order.EntryGroupService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.util.PriceValue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.custom.core.enums.ServicePriceCondition;
import com.custom.core.model.ServiceProductModel;
import com.custom.productservices.exceptions.CartServiceSelectionException;
import com.custom.productservices.service.ProductServiceLookupService;
import com.custom.productservices.service.impl.DefaultServiceEntryGroupService;


/**
 * NET-8943 &sect;5.4, AC3, AC4, AC11, AC14. Uses the real {@link DefaultServiceEntryGroupService} (pure logic over the
 * cart's entries and groups) so the assertions are on the resulting cart structure, not on internal calls. The
 * {@link ModelService} mock mirrors removals into the in-memory cart, the {@link CartService} mock appends a new entry.
 */
@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class DefaultCartServiceSelectionServiceTest
{
	private static final String INSTALLATION = "SVC_INSTALLATION";
	private static final String WARRANTY = "SVC_WARRANTY_3Y";

	@Mock
	private ProductServiceLookupService productServiceLookupService;
	@Mock
	private CartService cartService;
	@Mock
	private CommerceCartService commerceCartService;
	@Mock
	private EntryGroupService entryGroupService;
	@Mock
	private ModelService modelService;

	private DefaultCartServiceSelectionService selectionService;

	private CartModel cart;
	private ProductModel dishwasher;
	private ProductModel fridge;
	private ServiceProductModel installation;
	private ServiceProductModel warranty;
	private final UnitModel pieces = new UnitModel();
	private final PriceInformation aPrice = new PriceInformation(new PriceValue("EUR", 120.0d, false));

	@Before
	public void setUp()
	{
		selectionService = new DefaultCartServiceSelectionService();
		selectionService.setProductServiceLookupService(productServiceLookupService);
		selectionService.setServiceEntryGroupService(new DefaultServiceEntryGroupService());
		selectionService.setCartService(cartService);
		selectionService.setCommerceCartService(commerceCartService);
		selectionService.setEntryGroupService(entryGroupService);
		selectionService.setModelService(modelService);

		cart = new CartModel();
		cart.setCode("cart-1");
		cart.setEntries(new ArrayList<>());
		cart.setEntryGroups(new ArrayList<>());

		dishwasher = product("DISHWASHER");
		dishwasher.setServicePriceCondition(ServicePriceCondition.MEDIUM);
		fridge = product("FRIDGE");
		fridge.setServicePriceCondition(ServicePriceCondition.HIGH);
		installation = new ServiceProductModel();
		installation.setCode(INSTALLATION);
		warranty = new ServiceProductModel();
		warranty.setCode(WARRANTY);
	}

	// --- fixtures -------------------------------------------------------------------------------------------------

	private static ProductModel product(final String code)
	{
		final ProductModel product = new ProductModel();
		product.setCode(code);
		return product;
	}

	private CartEntryModel entry(final int entryNumber, final ProductModel product, final long quantity,
			final Integer... groupNumbers)
	{
		final CartEntryModel entry = new CartEntryModel();
		entry.setEntryNumber(Integer.valueOf(entryNumber));
		entry.setProduct(product);
		entry.setQuantity(Long.valueOf(quantity));
		entry.setUnit(pieces);
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

	/** cartService.addNewEntry appends an entry for the product, like the platform does (entryNumber -1 = append). */
	private void givenAddNewEntryAppends()
	{
		given(cartService.addNewEntry(any(CartModel.class), any(ProductModel.class), anyLong(), any(UnitModel.class),
				anyInt(), anyBoolean())).willAnswer(inv -> {
					final ProductModel product = inv.getArgument(1);
					final long quantity = inv.<Long> getArgument(2).longValue();
					final int next = cart.getEntries().stream().mapToInt(e -> e.getEntryNumber().intValue()).max().orElse(-1) + 1;
					return entry(next, product, quantity);
				});
	}

	/** modelService.remove / removeAll take the entries out of the in-memory cart, like a real remove + refresh. */
	private void givenRemovalsAreReflectedInTheCart()
	{
		lenient().doAnswer(inv -> {
			removeFromCart(Collections.singleton(inv.getArgument(0)));
			return null;
		}).when(modelService).remove(any(Object.class));
		lenient().doAnswer(inv -> {
			removeFromCart(inv.getArgument(0));
			return null;
		}).when(modelService).removeAll(anyCollection());
	}

	private void removeFromCart(final Collection<?> removed)
	{
		cart.setEntries(cart.getEntries().stream().filter(e -> !removed.contains(e)).collect(Collectors.toList()));
	}

	private void givenOffered(final ProductModel product, final ServiceProductModel... services)
	{
		given(productServiceLookupService.getAvailableServices(product)).willReturn(Arrays.asList(services));
	}

	private void givenPriced(final ServiceProductModel service, final ProductModel product)
	{
		given(productServiceLookupService.getServicePrice(service, product)).willReturn(Optional.of(aPrice));
	}

	private List<AbstractOrderEntryModel> serviceEntriesOf(final AbstractOrderEntryModel productEntry)
	{
		return new DefaultServiceEntryGroupService().getServiceEntries(productEntry);
	}

	private Set<Integer> serviceGroupNumbers()
	{
		return cart.getEntryGroups().stream().filter(g -> GroupType.SERVICE.equals(g.getGroupType()))
				.map(EntryGroup::getGroupNumber).collect(Collectors.toSet());
	}

	private void assertRecalculated()
	{
		final ArgumentCaptor<CommerceCartParameter> captor = ArgumentCaptor.forClass(CommerceCartParameter.class);
		verify(commerceCartService).calculateCart(captor.capture());
		assertSame(cart, captor.getValue().getCart());
		assertTrue("calculation hooks must run", captor.getValue().isEnableHooks());
	}

	private void assertAddFails(final int entryNumber, final String serviceCode)
	{
		try
		{
			selectionService.addService(cart, entryNumber, serviceCode);
			fail("expected CartServiceSelectionException");
		}
		catch (final CartServiceSelectionException expected)
		{
			// expected
		}
	}

	// --- addService -----------------------------------------------------------------------------------------------

	@Test
	public void shouldAddTheServiceAsItsOwnEntryWithTheProductQuantityInANewServiceGroup() throws Exception
	{
		final CartEntryModel productEntry = entry(0, dishwasher, 2L);
		givenOffered(dishwasher, installation);
		givenPriced(installation, dishwasher);
		given(entryGroupService.findMaxGroupNumber(anyList())).willReturn(Integer.valueOf(0));
		givenAddNewEntryAppends();

		selectionService.addService(cart, 0, INSTALLATION);

		verify(cartService).addNewEntry(cart, installation, 2L, pieces, -1, false);
		final List<AbstractOrderEntryModel> services = serviceEntriesOf(productEntry);
		assertEquals(1, services.size());
		assertSame(installation, services.get(0).getProduct());
		assertEquals(Long.valueOf(2L), services.get(0).getQuantity());
		assertEquals(Collections.singleton(Integer.valueOf(1)), serviceGroupNumbers());
		assertEquals(Collections.singleton(Integer.valueOf(1)), productEntry.getEntryGroupNumbers());
		assertEquals(Collections.singleton(Integer.valueOf(1)), services.get(0).getEntryGroupNumbers());
		assertEquals("the product entry is not touched otherwise", Long.valueOf(2L), productEntry.getQuantity());
		assertRecalculated();
	}

	@Test
	public void shouldNumberANewServiceGroupAfterTheExistingGroupsAndKeepTheProductsOtherGroups() throws Exception
	{
		group(4, GroupType.STANDALONE);
		final CartEntryModel productEntry = entry(0, dishwasher, 1L, 4);
		givenOffered(dishwasher, installation);
		givenPriced(installation, dishwasher);
		given(entryGroupService.findMaxGroupNumber(anyList())).willReturn(Integer.valueOf(4));
		givenAddNewEntryAppends();

		selectionService.addService(cart, 0, INSTALLATION);

		assertEquals(Collections.singleton(Integer.valueOf(5)), serviceGroupNumbers());
		assertEquals(new HashSet<>(Arrays.asList(4, 5)), productEntry.getEntryGroupNumbers());
		assertEquals(2, cart.getEntryGroups().size());
	}

	@Test
	public void shouldReuseTheExistingServiceGroupForASecondService() throws Exception
	{
		group(1, GroupType.SERVICE);
		final CartEntryModel productEntry = entry(0, dishwasher, 3L, 1);
		entry(1, installation, 3L, 1);
		givenOffered(dishwasher, installation, warranty);
		givenPriced(warranty, dishwasher);
		givenAddNewEntryAppends();

		selectionService.addService(cart, 0, WARRANTY);

		verify(entryGroupService, never()).findMaxGroupNumber(anyList());
		verify(cartService).addNewEntry(cart, warranty, 3L, pieces, -1, false);
		assertEquals(1, cart.getEntryGroups().size());
		final List<AbstractOrderEntryModel> services = serviceEntriesOf(productEntry);
		assertEquals(Arrays.asList(installation, warranty),
				services.stream().map(AbstractOrderEntryModel::getProduct).collect(Collectors.toList()));
		assertRecalculated();
	}

	@Test
	public void shouldDoNothingWhenTheServiceIsAlreadyAttached() throws Exception
	{
		group(1, GroupType.SERVICE);
		entry(0, dishwasher, 1L, 1);
		entry(1, installation, 1L, 1);
		givenOffered(dishwasher, installation);

		selectionService.addService(cart, 0, INSTALLATION);

		assertEquals(2, cart.getEntries().size());
		verifyNoInteractions(cartService, commerceCartService, modelService);
	}

	@Test
	public void shouldAttachTheSameServiceToEachProductLineSeparately() throws Exception
	{
		group(1, GroupType.SERVICE);
		entry(0, dishwasher, 1L, 1);
		entry(1, installation, 1L, 1);
		final CartEntryModel fridgeEntry = entry(2, fridge, 1L);
		givenOffered(fridge, installation);
		givenPriced(installation, fridge);
		given(entryGroupService.findMaxGroupNumber(anyList())).willReturn(Integer.valueOf(1));
		givenAddNewEntryAppends();

		selectionService.addService(cart, 2, INSTALLATION);

		assertEquals(new HashSet<>(Arrays.asList(1, 2)), serviceGroupNumbers());
		assertEquals(Collections.singleton(Integer.valueOf(2)), fridgeEntry.getEntryGroupNumbers());
		assertEquals(1, serviceEntriesOf(fridgeEntry).size());
	}

	@Test
	public void shouldRejectAnUnknownEntry()
	{
		entry(0, dishwasher, 1L);

		assertAddFails(7, INSTALLATION);
		verifyNoInteractions(cartService, commerceCartService, modelService, productServiceLookupService);
	}

	@Test
	public void shouldRejectAServiceEntryAsTarget()
	{
		group(1, GroupType.SERVICE);
		entry(0, dishwasher, 1L, 1);
		entry(1, installation, 1L, 1);

		assertAddFails(1, WARRANTY);
		verifyNoInteractions(cartService, commerceCartService, modelService, productServiceLookupService);
	}

	@Test
	public void shouldRejectAServiceThatIsNotOfferedForTheProduct()
	{
		final CartEntryModel productEntry = entry(0, dishwasher, 1L);
		givenOffered(dishwasher, warranty);

		assertAddFails(0, INSTALLATION);
		assertTrue(cart.getEntryGroups().isEmpty());
		assertTrue(productEntry.getEntryGroupNumbers().isEmpty());
		verifyNoInteractions(cartService, commerceCartService, modelService);
	}

	@Test
	public void shouldRejectAServiceWithoutAPriceAndLeaveTheCartUnchanged()
	{
		final CartEntryModel productEntry = entry(0, dishwasher, 1L);
		givenOffered(dishwasher, installation);
		given(productServiceLookupService.getServicePrice(installation, dishwasher)).willReturn(Optional.empty());

		assertAddFails(0, INSTALLATION);
		assertEquals(1, cart.getEntries().size());
		assertTrue(cart.getEntryGroups().isEmpty());
		assertTrue(productEntry.getEntryGroupNumbers().isEmpty());
		verifyNoInteractions(cartService, commerceCartService, modelService, entryGroupService);
	}

	// --- removeService --------------------------------------------------------------------------------------------

	@Test
	public void shouldRemoveOnlyThatServiceEntryAndRenumber() throws Exception
	{
		group(1, GroupType.SERVICE);
		final CartEntryModel productEntry = entry(0, dishwasher, 1L, 1);
		final CartEntryModel installationEntry = entry(1, installation, 1L, 1);
		final CartEntryModel warrantyEntry = entry(2, warranty, 1L, 1);
		final CartEntryModel toaster = entry(3, product("TOASTER"), 1L);
		givenRemovalsAreReflectedInTheCart();

		selectionService.removeService(cart, 0, INSTALLATION);

		verify(modelService).remove(installationEntry);
		assertEquals(Collections.singletonList(warrantyEntry), serviceEntriesOf(productEntry));
		assertEquals(Collections.singleton(Integer.valueOf(1)), serviceGroupNumbers());
		assertEquals(Integer.valueOf(0), productEntry.getEntryNumber());
		assertEquals(Integer.valueOf(1), warrantyEntry.getEntryNumber());
		assertEquals(Integer.valueOf(2), toaster.getEntryNumber());
		assertRecalculated();
	}

	@Test
	public void shouldRemoveTheServiceGroupWithItsLastService() throws Exception
	{
		group(1, GroupType.SERVICE);
		final CartEntryModel productEntry = entry(0, dishwasher, 1L, 1);
		entry(1, installation, 1L, 1);
		givenRemovalsAreReflectedInTheCart();

		selectionService.removeService(cart, 0, INSTALLATION);

		assertTrue(serviceGroupNumbers().isEmpty());
		assertTrue(productEntry.getEntryGroupNumbers().isEmpty());
		assertEquals(Collections.singletonList(productEntry), cart.getEntries());
		assertRecalculated();
	}

	@Test
	public void shouldNotTouchAnotherProductLinesService() throws Exception
	{
		group(1, GroupType.SERVICE);
		group(2, GroupType.SERVICE);
		entry(0, dishwasher, 1L, 1);
		entry(1, installation, 1L, 1);
		final CartEntryModel fridgeEntry = entry(2, fridge, 1L, 2);
		final CartEntryModel fridgeInstallation = entry(3, installation, 1L, 2);
		givenRemovalsAreReflectedInTheCart();

		selectionService.removeService(cart, 0, INSTALLATION);

		assertEquals(Collections.singletonList(fridgeInstallation), serviceEntriesOf(fridgeEntry));
		assertEquals(Collections.singleton(Integer.valueOf(2)), serviceGroupNumbers());
	}

	@Test
	public void shouldDoNothingWhenRemovingAServiceThatIsNotAttached() throws Exception
	{
		entry(0, dishwasher, 1L);

		selectionService.removeService(cart, 0, INSTALLATION);

		verifyNoInteractions(modelService, commerceCartService);
	}

	@Test(expected = CartServiceSelectionException.class)
	public void shouldRejectRemovingFromAnUnknownEntry() throws Exception
	{
		selectionService.removeService(cart, 0, INSTALLATION);
	}

	@Test(expected = CartServiceSelectionException.class)
	public void shouldRejectRemovingFromAServiceEntry() throws Exception
	{
		group(1, GroupType.SERVICE);
		entry(0, dishwasher, 1L, 1);
		entry(1, installation, 1L, 1);

		selectionService.removeService(cart, 1, INSTALLATION);
	}

	// --- syncServiceQuantities ------------------------------------------------------------------------------------

	@Test
	@SuppressWarnings("unchecked")
	public void shouldSetEveryServiceToTheProductQuantity()
	{
		group(1, GroupType.SERVICE);
		final CartEntryModel productEntry = entry(0, dishwasher, 4L, 1);
		final CartEntryModel installationEntry = entry(1, installation, 1L, 1);
		final CartEntryModel warrantyEntry = entry(2, warranty, 4L, 1);
		final CartEntryModel toaster = entry(3, product("TOASTER"), 1L);

		assertTrue(selectionService.syncServiceQuantities(productEntry));

		assertEquals(Long.valueOf(4L), installationEntry.getQuantity());
		assertEquals(Long.valueOf(4L), warrantyEntry.getQuantity());
		assertEquals(Long.valueOf(1L), toaster.getQuantity());
		final ArgumentCaptor<Collection<Object>> saved = ArgumentCaptor.forClass(Collection.class);
		verify(modelService).saveAll(saved.capture());
		assertEquals("only the changed entry is saved", Collections.singletonList(installationEntry),
				new ArrayList<>(saved.getValue()));
		verifyNoInteractions(commerceCartService);
	}

	@Test
	public void shouldReportNoChangeWhenServicesAreInSync()
	{
		group(1, GroupType.SERVICE);
		final CartEntryModel productEntry = entry(0, dishwasher, 2L, 1);
		entry(1, installation, 2L, 1);

		assertFalse(selectionService.syncServiceQuantities(productEntry));
	}

	@Test
	public void shouldReportNoChangeForAProductWithoutServices()
	{
		final CartEntryModel productEntry = entry(0, dishwasher, 2L);

		assertFalse(selectionService.syncServiceQuantities(productEntry));
	}

	// --- removeInvalidServices ------------------------------------------------------------------------------------

	@Test
	public void shouldLeaveACleanCartAloneAndNotRecalculate()
	{
		group(1, GroupType.SERVICE);
		entry(0, dishwasher, 1L, 1);
		entry(1, installation, 1L, 1);
		entry(2, fridge, 1L);
		givenOffered(dishwasher, installation);
		givenPriced(installation, dishwasher);

		assertTrue(selectionService.removeInvalidServices(cart).isEmpty());

		assertEquals(3, cart.getEntries().size());
		verifyNoInteractions(modelService, commerceCartService);
	}

	@Test
	public void shouldRemoveAServiceWhoseReferenceWasDeactivatedOrRemoved()
	{
		group(1, GroupType.SERVICE);
		final CartEntryModel productEntry = entry(0, dishwasher, 1L, 1);
		entry(1, installation, 1L, 1);
		final CartEntryModel warrantyEntry = entry(2, warranty, 1L, 1);
		// the lookup only returns active SERVICE references: installation's is gone
		givenOffered(dishwasher, warranty);
		givenPriced(warranty, dishwasher);
		givenRemovalsAreReflectedInTheCart();

		final List<ServiceProductModel> removed = selectionService.removeInvalidServices(cart);

		assertEquals(Collections.singletonList(installation), removed);
		assertEquals(Collections.singletonList(warrantyEntry), serviceEntriesOf(productEntry));
		assertEquals(Integer.valueOf(1), warrantyEntry.getEntryNumber());
		assertEquals(Collections.singleton(Integer.valueOf(1)), serviceGroupNumbers());
		assertRecalculated();
	}

	@Test
	public void shouldRemoveTheServicesOfAProductWhoseConditionWasRemoved()
	{
		dishwasher.setServicePriceCondition(null);
		group(1, GroupType.SERVICE);
		final CartEntryModel productEntry = entry(0, dishwasher, 1L, 1);
		entry(1, installation, 1L, 1);
		entry(2, warranty, 1L, 1);
		// a product without condition offers no services (DefaultProductServiceLookupService contract)
		given(productServiceLookupService.getAvailableServices(dishwasher)).willReturn(Collections.emptyList());
		givenRemovalsAreReflectedInTheCart();

		final List<ServiceProductModel> removed = selectionService.removeInvalidServices(cart);

		assertEquals(Arrays.asList(installation, warranty), removed);
		assertEquals(Collections.singletonList(productEntry), cart.getEntries());
		assertTrue(serviceGroupNumbers().isEmpty());
		assertTrue(productEntry.getEntryGroupNumbers().isEmpty());
		assertRecalculated();
	}

	@Test
	public void shouldRemoveAServiceThatNoLongerHasAPrice()
	{
		group(1, GroupType.SERVICE);
		entry(0, dishwasher, 1L, 1);
		entry(1, installation, 1L, 1);
		givenOffered(dishwasher, installation);
		given(productServiceLookupService.getServicePrice(installation, dishwasher)).willReturn(Optional.empty());
		givenRemovalsAreReflectedInTheCart();

		assertEquals(Collections.singletonList(installation), selectionService.removeInvalidServices(cart));
		assertRecalculated();
	}

	@Test
	public void shouldRemoveAnOrphanServiceEntryWithoutAProductEntry()
	{
		group(1, GroupType.SERVICE);
		entry(0, installation, 1L, 1);
		entry(1, warranty, 1L);
		final CartEntryModel toaster = entry(2, product("TOASTER"), 1L);
		givenRemovalsAreReflectedInTheCart();

		final List<ServiceProductModel> removed = selectionService.removeInvalidServices(cart);

		assertEquals(Arrays.asList(installation, warranty), removed);
		assertEquals(Collections.singletonList(toaster), cart.getEntries());
		assertEquals(Integer.valueOf(0), toaster.getEntryNumber());
		assertTrue(serviceGroupNumbers().isEmpty());
		verifyNoInteractions(productServiceLookupService);
		assertRecalculated();
	}

	@Test
	public void shouldOnlyRemoveTheInvalidServiceOfTheAffectedProductLine()
	{
		group(1, GroupType.SERVICE);
		group(2, GroupType.SERVICE);
		final CartEntryModel dishwasherEntry = entry(0, dishwasher, 1L, 1);
		final CartEntryModel dishwasherInstallation = entry(1, installation, 1L, 1);
		entry(2, fridge, 1L, 2);
		entry(3, installation, 1L, 2);
		givenOffered(dishwasher, installation);
		givenPriced(installation, dishwasher);
		given(productServiceLookupService.getAvailableServices(fridge)).willReturn(Collections.emptyList());
		givenRemovalsAreReflectedInTheCart();

		assertEquals(Collections.singletonList(installation), selectionService.removeInvalidServices(cart));

		assertEquals(Collections.singletonList(dishwasherInstallation), serviceEntriesOf(dishwasherEntry));
		assertEquals(Collections.singleton(Integer.valueOf(1)), serviceGroupNumbers());
		assertEquals(3, cart.getEntries().size());
	}

	@Test
	public void shouldDropALeftoverEmptyServiceGroupEvenWhenEveryServiceIsValid()
	{
		group(1, GroupType.SERVICE);
		group(2, GroupType.SERVICE);
		final CartEntryModel dishwasherEntry = entry(0, dishwasher, 1L, 1);
		entry(1, installation, 1L, 1);
		// fridge's services were removed with a path that did not clean up its group
		final CartEntryModel fridgeEntry = entry(2, fridge, 1L, 2);
		givenOffered(dishwasher, installation);
		givenPriced(installation, dishwasher);

		assertTrue(selectionService.removeInvalidServices(cart).isEmpty());

		assertEquals(Collections.singleton(Integer.valueOf(1)), serviceGroupNumbers());
		assertTrue(fridgeEntry.getEntryGroupNumbers().isEmpty());
		assertEquals(Collections.singleton(Integer.valueOf(1)), dishwasherEntry.getEntryGroupNumbers());
		verify(modelService).save(cart);
		verifyNoInteractions(commerceCartService);
	}

	@Test
	public void shouldDropALeftoverEmptyGroupAndAnInvalidServiceTogether()
	{
		group(1, GroupType.SERVICE);
		group(2, GroupType.SERVICE);
		final CartEntryModel dishwasherEntry = entry(0, dishwasher, 1L, 1);
		entry(1, installation, 1L, 1);
		final CartEntryModel fridgeEntry = entry(2, fridge, 1L, 2);
		givenOffered(dishwasher);
		givenRemovalsAreReflectedInTheCart();

		assertEquals(Collections.singletonList(installation), selectionService.removeInvalidServices(cart));

		assertTrue(serviceGroupNumbers().isEmpty());
		assertTrue(dishwasherEntry.getEntryGroupNumbers().isEmpty());
		assertTrue(fridgeEntry.getEntryGroupNumbers().isEmpty());
		assertEquals(Arrays.asList(dishwasherEntry, fridgeEntry), cart.getEntries());
		assertRecalculated();
	}

	@Test
	public void shouldTolerateACartWithoutEntries()
	{
		cart.setEntries(null);

		assertTrue(selectionService.removeInvalidServices(cart).isEmpty());
		verifyNoInteractions(modelService, commerceCartService);
	}

	// --- removeEmptyServiceGroups ---------------------------------------------------------------------------------

	@Test
	public void shouldRemoveOnlyServiceGroupsWithoutServiceEntries()
	{
		group(1, GroupType.SERVICE);
		group(2, GroupType.SERVICE);
		group(3, GroupType.STANDALONE);
		final CartEntryModel dishwasherEntry = entry(0, dishwasher, 1L, 1, 3);
		entry(1, installation, 1L, 1);
		final CartEntryModel fridgeEntry = entry(2, fridge, 1L, 2, 3);

		selectionService.removeEmptyServiceGroups(cart);

		assertEquals(Collections.singleton(Integer.valueOf(1)), serviceGroupNumbers());
		assertTrue("non-SERVICE groups are kept",
				cart.getEntryGroups().stream().anyMatch(g -> g.getGroupNumber().intValue() == 3));
		assertEquals(Collections.singleton(Integer.valueOf(3)), fridgeEntry.getEntryGroupNumbers());
		assertEquals(new HashSet<>(Arrays.asList(1, 3)), dishwasherEntry.getEntryGroupNumbers());
		verify(modelService).save(cart);
	}

	@Test
	public void shouldNotSaveWhenThereAreNoGroups()
	{
		entry(0, dishwasher, 1L);

		selectionService.removeEmptyServiceGroups(cart);

		verifyNoInteractions(modelService);
	}

	@Test
	public void shouldNotSaveWhenEveryServiceGroupStillHasServices()
	{
		group(1, GroupType.SERVICE);
		entry(0, dishwasher, 1L, 1);
		entry(1, installation, 1L, 1);

		selectionService.removeEmptyServiceGroups(cart);

		assertEquals(Collections.singleton(Integer.valueOf(1)), serviceGroupNumbers());
		verifyNoInteractions(modelService);
	}
}
