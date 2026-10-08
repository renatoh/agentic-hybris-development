/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.productservices.cart;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.core.enums.GroupType;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.CartEntryModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.order.EntryGroup;
import de.hybris.platform.servicelayer.model.ModelService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.custom.core.model.ServiceProductModel;
import com.custom.productservices.exceptions.CartServiceSelectionException;
import com.custom.productservices.service.impl.DefaultServiceEntryGroupService;


/**
 * NET-8943 &sect;5.4 cart merge on login, AC9: services are taken out of the anonymous cart before the platform merges
 * plain product lines, then re-attached once per product/service to the merged line.
 */
@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class ServiceCartMergingMethodHookTest
{
	private static final String INSTALLATION = "SVC_INSTALLATION";
	private static final String WARRANTY = "SVC_WARRANTY_3Y";

	@Mock
	private CartServiceSelectionService cartServiceSelectionService;
	@Mock
	private ModelService modelService;

	private ServiceCartMergingMethodHook hook;

	private ProductModel dishwasher;
	private ProductModel fridge;
	private ProductModel toaster;
	private ServiceProductModel installation;
	private ServiceProductModel warranty;

	@Before
	public void setUp()
	{
		hook = new ServiceCartMergingMethodHook();
		hook.setServiceEntryGroupService(new DefaultServiceEntryGroupService());
		hook.setCartServiceSelectionService(cartServiceSelectionService);
		hook.setModelService(modelService);

		dishwasher = product("DISHWASHER");
		fridge = product("FRIDGE");
		toaster = product("TOASTER");
		installation = new ServiceProductModel();
		installation.setCode(INSTALLATION);
		warranty = new ServiceProductModel();
		warranty.setCode(WARRANTY);
	}

	private static ProductModel product(final String code)
	{
		final ProductModel product = new ProductModel();
		product.setCode(code);
		return product;
	}

	private static CartModel cart(final String code)
	{
		final CartModel cart = new CartModel();
		cart.setCode(code);
		cart.setEntries(new ArrayList<>());
		cart.setEntryGroups(new ArrayList<>());
		return cart;
	}

	private static CartEntryModel entry(final CartModel cart, final int entryNumber, final ProductModel product,
			final Integer... groupNumbers)
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

	private static void group(final CartModel cart, final int number, final GroupType type)
	{
		final EntryGroup group = new EntryGroup();
		group.setGroupNumber(Integer.valueOf(number));
		group.setGroupType(type);
		final List<EntryGroup> groups = new ArrayList<>(cart.getEntryGroups());
		groups.add(group);
		cart.setEntryGroups(groups);
	}

	/** Anonymous cart: dishwasher + installation + warranty (SERVICE group 1), toaster in a STANDALONE group 5. */
	private CartModel anonymousCartWithServices(final String code)
	{
		final CartModel fromCart = cart(code);
		group(fromCart, 1, GroupType.SERVICE);
		group(fromCart, 5, GroupType.STANDALONE);
		entry(fromCart, 0, dishwasher, 1);
		entry(fromCart, 1, installation, 1);
		entry(fromCart, 2, warranty, 1);
		entry(fromCart, 3, toaster, 5);
		return fromCart;
	}

	/** Customer cart after the platform merge: toaster at 0, the merged dishwasher line at 1. */
	private CartModel mergedCustomerCart()
	{
		final CartModel toCart = cart("customer");
		entry(toCart, 0, toaster);
		entry(toCart, 1, dishwasher);
		return toCart;
	}

	// --- beforeCartMerge ------------------------------------------------------------------------------------------

	@Test
	@SuppressWarnings("unchecked")
	public void shouldTakeTheServicesOutOfTheAnonymousCartBeforeTheMerge()
	{
		final CartModel fromCart = anonymousCartWithServices("anon");
		final AbstractOrderEntryModel dishwasherEntry = fromCart.getEntries().get(0);
		final AbstractOrderEntryModel installationEntry = fromCart.getEntries().get(1);
		final AbstractOrderEntryModel warrantyEntry = fromCart.getEntries().get(2);
		final AbstractOrderEntryModel toasterEntry = fromCart.getEntries().get(3);

		hook.beforeCartMerge(fromCart, cart("customer"));

		final ArgumentCaptor<Collection<Object>> removed = ArgumentCaptor.forClass(Collection.class);
		verify(modelService).removeAll(removed.capture());
		assertEquals(new HashSet<>(Arrays.asList(installationEntry, warrantyEntry)), new HashSet<>(removed.getValue()));
		assertTrue("SERVICE group numbers are stripped", dishwasherEntry.getEntryGroupNumbers().isEmpty());
		assertEquals("other groups are kept on the entry", new HashSet<>(Arrays.asList(5)), toasterEntry.getEntryGroupNumbers());
		assertEquals(1, fromCart.getEntryGroups().size());
		assertEquals(GroupType.STANDALONE, fromCart.getEntryGroups().get(0).getGroupType());
		verify(modelService).save(fromCart);
		verify(modelService, times(2)).refresh(fromCart);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void shouldReloadTheAnonymousCartAfterRemovingServicesAndBeforeSavingIt()
	{
		final CartModel fromCart = anonymousCartWithServices("anon");

		hook.beforeCartMerge(fromCart, cart("customer"));

		final InOrder order = inOrder(modelService);
		order.verify(modelService).removeAll(anyCollection());
		order.verify(modelService).refresh(fromCart);
		order.verify(modelService).saveAll(anyCollection());
		order.verify(modelService).save(fromCart);
		order.verify(modelService).refresh(fromCart);
		order.verifyNoMoreInteractions();
	}

	@Test
	@SuppressWarnings("unchecked")
	public void shouldNeverSaveARemovedServiceEntry()
	{
		final CartModel fromCart = anonymousCartWithServices("anon");
		final AbstractOrderEntryModel dishwasherEntry = fromCart.getEntries().get(0);
		final AbstractOrderEntryModel toasterEntry = fromCart.getEntries().get(3);
		// like the real ModelService: removeAll deletes, refresh reloads the cart's entry list without them
		final Set<Object> removed = new HashSet<>();
		willAnswer(inv -> {
			removed.addAll(inv.<Collection<?>> getArgument(0));
			return null;
		}).given(modelService).removeAll(anyCollection());
		willAnswer(inv -> {
			fromCart.setEntries(fromCart.getEntries().stream().filter(e -> !removed.contains(e)).collect(Collectors.toList()));
			return null;
		}).given(modelService).refresh(fromCart);
		final List<Object> entriesInCartWhenSaved = new ArrayList<>();
		willAnswer(inv -> {
			entriesInCartWhenSaved.addAll(fromCart.getEntries());
			return null;
		}).given(modelService).save(fromCart);

		hook.beforeCartMerge(fromCart, cart("customer"));

		final ArgumentCaptor<Collection<Object>> saved = ArgumentCaptor.forClass(Collection.class);
		verify(modelService).saveAll(saved.capture());
		assertEquals("only the stripped product entry is saved", Collections.singletonList(dishwasherEntry),
				new ArrayList<>(saved.getValue()));
		assertEquals("the cart is saved without the removed entries", Arrays.asList(dishwasherEntry, toasterEntry),
				entriesInCartWhenSaved);
	}

	@Test
	public void shouldLeaveAnAnonymousCartWithoutServicesAlone()
	{
		final CartModel fromCart = cart("anon");
		entry(fromCart, 0, toaster);

		hook.beforeCartMerge(fromCart, cart("customer"));

		verifyNoInteractions(modelService);
	}

	// --- afterCartMerge -------------------------------------------------------------------------------------------

	@Test
	public void shouldReattachEachServiceToTheMergedProductLine() throws CartServiceSelectionException
	{
		final CartModel fromCart = anonymousCartWithServices("anon");
		final CartModel toCart = mergedCustomerCart();
		hook.beforeCartMerge(fromCart, toCart);

		hook.afterCartMerge(fromCart, toCart);

		verify(cartServiceSelectionService).addService(toCart, 1, INSTALLATION);
		verify(cartServiceSelectionService).addService(toCart, 1, WARRANTY);
		verifyNoMoreInteractions(cartServiceSelectionService);
		verify(modelService).save(toCart);
	}

	@Test
	public void shouldReattachAServiceOnlyOnceWhenTheProductWasInTwoLines() throws CartServiceSelectionException
	{
		final CartModel fromCart = cart("anon");
		group(fromCart, 1, GroupType.SERVICE);
		group(fromCart, 2, GroupType.SERVICE);
		entry(fromCart, 0, dishwasher, 1);
		entry(fromCart, 1, installation, 1);
		entry(fromCart, 2, dishwasher, 2);
		entry(fromCart, 3, installation, 2);
		final CartModel toCart = mergedCustomerCart();
		hook.beforeCartMerge(fromCart, toCart);

		hook.afterCartMerge(fromCart, toCart);

		verify(cartServiceSelectionService, times(1)).addService(toCart, 1, INSTALLATION);
		verifyNoMoreInteractions(cartServiceSelectionService);
	}

	@Test
	public void shouldNotAttachServicesToAnotherProduct() throws CartServiceSelectionException
	{
		final CartModel fromCart = cart("anon");
		group(fromCart, 1, GroupType.SERVICE);
		entry(fromCart, 0, fridge, 1);
		entry(fromCart, 1, installation, 1);
		final CartModel toCart = mergedCustomerCart();
		hook.beforeCartMerge(fromCart, toCart);

		hook.afterCartMerge(fromCart, toCart);

		verify(cartServiceSelectionService, never()).addService(eq(toCart), anyInt(), anyString());
	}

	@Test
	public void shouldNotAttachAServiceToAServiceEntryOfTheSameCode() throws CartServiceSelectionException
	{
		final CartModel fromCart = cart("anon");
		group(fromCart, 1, GroupType.SERVICE);
		entry(fromCart, 0, dishwasher, 1);
		entry(fromCart, 1, installation, 1);
		final CartModel toCart = cart("customer");
		group(toCart, 7, GroupType.SERVICE);
		entry(toCart, 0, dishwasher, 7);
		entry(toCart, 1, installation, 7);
		hook.beforeCartMerge(fromCart, toCart);

		hook.afterCartMerge(fromCart, toCart);

		verify(cartServiceSelectionService).addService(toCart, 0, INSTALLATION);
		verifyNoMoreInteractions(cartServiceSelectionService);
	}

	@Test
	public void shouldLogAndContinueWhenAServiceCannotBeReattached() throws CartServiceSelectionException
	{
		final CartModel fromCart = anonymousCartWithServices("anon");
		final CartModel toCart = mergedCustomerCart();
		willThrow(new CartServiceSelectionException("not offered")).given(cartServiceSelectionService).addService(toCart, 1,
				INSTALLATION);
		hook.beforeCartMerge(fromCart, toCart);

		hook.afterCartMerge(fromCart, toCart);

		verify(cartServiceSelectionService).addService(toCart, 1, WARRANTY);
	}

	@Test
	public void shouldDoNothingAfterAMergeWithoutServices()
	{
		final CartModel fromCart = cart("anon");
		entry(fromCart, 0, toaster);
		final CartModel toCart = mergedCustomerCart();
		hook.beforeCartMerge(fromCart, toCart);

		hook.afterCartMerge(fromCart, toCart);

		verifyNoInteractions(cartServiceSelectionService, modelService);
	}

	@Test
	public void shouldDoNothingAfterAMergeThatWasNeverPrepared()
	{
		hook.afterCartMerge(cart("anon"), mergedCustomerCart());

		verifyNoInteractions(cartServiceSelectionService, modelService);
	}

	@Test
	public void shouldNotLeakSelectionsIntoTheNextMerge() throws CartServiceSelectionException
	{
		final CartModel firstFrom = anonymousCartWithServices("anon-1");
		final CartModel firstTo = mergedCustomerCart();
		hook.beforeCartMerge(firstFrom, firstTo);
		hook.afterCartMerge(firstFrom, firstTo);

		final CartModel secondFrom = cart("anon-2");
		entry(secondFrom, 0, toaster);
		final CartModel secondTo = mergedCustomerCart();
		hook.beforeCartMerge(secondFrom, secondTo);
		hook.afterCartMerge(secondFrom, secondTo);
		// a repeated afterCartMerge of the first cart must not replay its selections either
		hook.afterCartMerge(firstFrom, firstTo);

		verify(cartServiceSelectionService, never()).addService(eq(secondTo), anyInt(), anyString());
		verify(cartServiceSelectionService, times(1)).addService(firstTo, 1, INSTALLATION);
		verify(cartServiceSelectionService, times(1)).addService(firstTo, 1, WARRANTY);
	}

	@Test
	public void shouldDropTheStateOfAMergeThatNeverReachedAfterCartMerge() throws CartServiceSelectionException
	{
		final CartModel abandonedFrom = anonymousCartWithServices("anon-a");
		final CartModel abandonedTo = mergedCustomerCart();
		final CartModel fromB = cart("anon-b");
		group(fromB, 1, GroupType.SERVICE);
		entry(fromB, 0, toaster, 1);
		entry(fromB, 1, installation, 1);
		final CartModel toB = mergedCustomerCart();

		// the platform merge of A failed between before and after: A's afterCartMerge never ran
		hook.beforeCartMerge(abandonedFrom, abandonedTo);
		hook.beforeCartMerge(fromB, toB);
		hook.afterCartMerge(fromB, toB);
		// a late afterCartMerge for A finds nothing to replay
		hook.afterCartMerge(abandonedFrom, abandonedTo);

		verify(cartServiceSelectionService).addService(toB, 0, INSTALLATION);
		verify(cartServiceSelectionService, never()).addService(eq(abandonedTo), anyInt(), anyString());
		verifyNoMoreInteractions(cartServiceSelectionService);
	}

	@Test
	public void shouldReattachServicesInTheOrderTheyWereFirstSeen() throws CartServiceSelectionException
	{
		final CartModel fromCart = cart("anon");
		group(fromCart, 1, GroupType.SERVICE);
		group(fromCart, 2, GroupType.SERVICE);
		// line 1: warranty before installation; line 2 (same product): installation again, then warranty again
		entry(fromCart, 0, dishwasher, 1);
		entry(fromCart, 1, warranty, 1);
		entry(fromCart, 2, installation, 1);
		entry(fromCart, 3, dishwasher, 2);
		entry(fromCart, 4, installation, 2);
		entry(fromCart, 5, warranty, 2);
		final CartModel toCart = mergedCustomerCart();
		hook.beforeCartMerge(fromCart, toCart);

		hook.afterCartMerge(fromCart, toCart);

		final InOrder order = inOrder(cartServiceSelectionService);
		order.verify(cartServiceSelectionService).addService(toCart, 1, WARRANTY);
		order.verify(cartServiceSelectionService).addService(toCart, 1, INSTALLATION);
		verifyNoMoreInteractions(cartServiceSelectionService);
	}

	@Test
	public void shouldReattachServicesOfSeveralProductsInFirstSeenProductOrder() throws CartServiceSelectionException
	{
		final CartModel fromCart = cart("anon");
		group(fromCart, 1, GroupType.SERVICE);
		group(fromCart, 2, GroupType.SERVICE);
		entry(fromCart, 0, fridge, 1);
		entry(fromCart, 1, installation, 1);
		entry(fromCart, 2, dishwasher, 2);
		entry(fromCart, 3, warranty, 2);
		final CartModel toCart = mergedCustomerCart();
		entry(toCart, 2, fridge);
		hook.beforeCartMerge(fromCart, toCart);

		hook.afterCartMerge(fromCart, toCart);

		final InOrder order = inOrder(cartServiceSelectionService);
		order.verify(cartServiceSelectionService).addService(toCart, 2, INSTALLATION);
		order.verify(cartServiceSelectionService).addService(toCart, 1, WARRANTY);
		verifyNoMoreInteractions(cartServiceSelectionService);
	}
}
