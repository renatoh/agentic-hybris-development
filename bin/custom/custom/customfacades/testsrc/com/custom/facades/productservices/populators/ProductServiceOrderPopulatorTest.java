/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.facades.productservices.populators;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.withSettings;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commercefacades.order.EntryGroupData;
import de.hybris.platform.commercefacades.order.data.AbstractOrderData;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commercefacades.order.data.ConsignmentData;
import de.hybris.platform.commercefacades.order.data.ConsignmentEntryData;
import de.hybris.platform.commercefacades.order.data.DeliveryOrderEntryGroupData;
import de.hybris.platform.commercefacades.order.data.OrderData;
import de.hybris.platform.commercefacades.order.data.OrderEntryData;
import de.hybris.platform.commercefacades.order.data.PickupOrderEntryGroupData;
import de.hybris.platform.commercefacades.product.PriceDataFactory;
import de.hybris.platform.commercefacades.product.data.PriceData;
import de.hybris.platform.commercefacades.product.data.PriceDataType;
import de.hybris.platform.core.enums.GroupType;
import de.hybris.platform.core.model.c2l.CurrencyModel;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.order.CartEntryModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.order.OrderEntryModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.order.EntryGroup;
import de.hybris.platform.jalo.order.price.PriceInformation;
import de.hybris.platform.util.PriceValue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.custom.core.model.ServiceProductModel;
import com.custom.facades.productservices.data.ProductServiceData;
import com.custom.productservices.service.ProductServiceLookupService;
import com.custom.productservices.service.impl.DefaultServiceEntryGroupService;


/**
 * NET-8943 &sect;5.7, AC1-AC3, AC8, AC10: services are shown under their product line (selected = charged price,
 * available = cart only), never as lines of their own, in every place the platform lists entries (entries, unconsigned
 * entries, consignments, root groups, delivery/pickup groups), and line counts exclude them.
 * <p>
 * Uses the real {@link DefaultServiceEntryGroupService}. Service products are mocks only because their localized
 * name/description getters need a session context; entries, carts, orders and all DTOs are real.
 */
@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class ProductServiceOrderPopulatorTest
{
	private static final String INSTALLATION = "SVC_INSTALLATION";
	private static final String WARRANTY = "SVC_WARRANTY_3Y";

	@Mock
	private ProductServiceLookupService productServiceLookupService;
	@Mock
	private PriceDataFactory priceDataFactory;

	private ProductServiceOrderPopulator<AbstractOrderModel, AbstractOrderData> populator;

	private final CurrencyModel eur = new CurrencyModel();
	private ProductModel dishwasher;
	private ProductModel toaster;
	private ServiceProductModel installation;
	private ServiceProductModel warranty;

	@Before
	public void setUp()
	{
		populator = new ProductServiceOrderPopulator<>();
		populator.setServiceEntryGroupService(new DefaultServiceEntryGroupService());
		populator.setProductServiceLookupService(productServiceLookupService);
		populator.setPriceDataFactory(priceDataFactory);

		eur.setIsocode("EUR");
		dishwasher = new ProductModel();
		dishwasher.setCode("DISHWASHER");
		toaster = new ProductModel();
		toaster.setCode("TOASTER");
		installation = service(INSTALLATION, "Installation");
		warranty = service(WARRANTY, "3-year warranty");
	}

	// --- fixtures -------------------------------------------------------------------------------------------------

	private static ServiceProductModel service(final String code, final String name)
	{
		// lenient: not every test reads name/description
		final ServiceProductModel service = mock(ServiceProductModel.class, withSettings().name(code).lenient());
		given(service.getCode()).willReturn(code);
		given(service.getName()).willReturn(name);
		given(service.getDescription()).willReturn(name + " description");
		return service;
	}

	/** priceDataFactory builds a real PriceData carrying the value and currency it was asked for. */
	private void givenPriceDataFactory()
	{
		given(priceDataFactory.create(any(PriceDataType.class), any(BigDecimal.class), anyString())).willAnswer(inv -> {
			final PriceData data = new PriceData();
			data.setPriceType(inv.getArgument(0));
			data.setValue(inv.getArgument(1));
			data.setCurrencyIso(inv.getArgument(2));
			return data;
		});
	}

	private static <O extends AbstractOrderModel> O withGroups(final O order, final int... serviceGroupNumbers)
	{
		final List<EntryGroup> groups = new ArrayList<>();
		for (final int number : serviceGroupNumbers)
		{
			final EntryGroup group = new EntryGroup();
			group.setGroupNumber(Integer.valueOf(number));
			group.setGroupType(GroupType.SERVICE);
			groups.add(group);
		}
		order.setEntryGroups(groups);
		order.setEntries(new ArrayList<>());
		return order;
	}

	private static void add(final AbstractOrderModel order, final AbstractOrderEntryModel entry, final int entryNumber,
			final ProductModel product, final long quantity, final Double basePrice, final Integer... groupNumbers)
	{
		entry.setEntryNumber(Integer.valueOf(entryNumber));
		entry.setProduct(product);
		entry.setQuantity(Long.valueOf(quantity));
		entry.setBasePrice(basePrice);
		entry.setTotalPrice(basePrice == null ? null : Double.valueOf(basePrice.doubleValue() * quantity));
		entry.setOrder(order);
		entry.setEntryGroupNumbers(new HashSet<>(Arrays.asList(groupNumbers)));
		final List<AbstractOrderEntryModel> entries = new ArrayList<>(order.getEntries());
		entries.add(entry);
		order.setEntries(entries);
	}

	/** Cart: dishwasher x2 (0) + installation x2 at 120 (1) in SERVICE group 1, toaster x1 (2). */
	private CartModel cartWithInstallation()
	{
		final CartModel cart = withGroups(new CartModel(), 1);
		cart.setCurrency(eur);
		add(cart, new CartEntryModel(), 0, dishwasher, 2L, Double.valueOf(499d), 1);
		add(cart, new CartEntryModel(), 1, installation, 2L, Double.valueOf(120d), 1);
		add(cart, new CartEntryModel(), 2, toaster, 1L, Double.valueOf(30d));
		return cart;
	}

	/** Order with the same lines as {@link #cartWithInstallation()}. */
	private OrderModel orderWithInstallation()
	{
		final OrderModel order = withGroups(new OrderModel(), 1);
		order.setCurrency(eur);
		add(order, new OrderEntryModel(), 0, dishwasher, 2L, Double.valueOf(499d), 1);
		add(order, new OrderEntryModel(), 1, installation, 2L, Double.valueOf(120d), 1);
		add(order, new OrderEntryModel(), 2, toaster, 1L, Double.valueOf(30d));
		return order;
	}

	private static OrderEntryData entryData(final int entryNumber, final long quantity)
	{
		final OrderEntryData data = new OrderEntryData();
		data.setEntryNumber(Integer.valueOf(entryNumber));
		data.setQuantity(Long.valueOf(quantity));
		return data;
	}

	/** Fresh OrderEntryData instances for entries 0, 1, 2, like the platform builds per list. */
	private static List<OrderEntryData> entryDataList()
	{
		return new ArrayList<>(Arrays.asList(entryData(0, 2L), entryData(1, 2L), entryData(2, 1L)));
	}

	/** Counts as the platform computes them, services included: 3 lines, 5 units. */
	private static void platformCounts(final AbstractOrderData target)
	{
		target.setTotalItems(Integer.valueOf(3));
		target.setTotalUnitCount(Integer.valueOf(5));
		target.setDeliveryItemsQuantity(Long.valueOf(5L));
	}

	private static List<Integer> numbers(final Collection<OrderEntryData> entries)
	{
		return entries.stream().map(OrderEntryData::getEntryNumber).collect(Collectors.toList());
	}

	private static void assertPrice(final double expected, final PriceData price)
	{
		assertEquals(0, BigDecimal.valueOf(expected).compareTo(price.getValue()));
		assertEquals("EUR", price.getCurrencyIso());
		assertEquals(PriceDataType.BUY, price.getPriceType());
	}

	private static void assertInstallationCharged(final List<ProductServiceData> services)
	{
		assertEquals(1, services.size());
		final ProductServiceData selected = services.get(0);
		assertEquals(INSTALLATION, selected.getCode());
		assertEquals("Installation", selected.getName());
		assertEquals("Installation description", selected.getDescription());
		assertEquals(Long.valueOf(2L), selected.getQuantity());
		assertPrice(120d, selected.getPrice());
		assertPrice(240d, selected.getTotalPrice());
		assertTrue(selected.isSelected());
	}

	// --- cart -----------------------------------------------------------------------------------------------------

	@Test
	public void shouldShowSelectedAndAvailableServicesUnderTheCartLine()
	{
		givenPriceDataFactory();
		given(productServiceLookupService.getAvailableServices(dishwasher)).willReturn(Arrays.asList(installation, warranty));
		given(productServiceLookupService.getServicePrice(warranty, dishwasher))
				.willReturn(Optional.of(new PriceInformation(new PriceValue("EUR", 79d, false))));
		given(productServiceLookupService.getAvailableServices(toaster)).willReturn(Collections.emptyList());
		final CartData target = new CartData();
		target.setEntries(entryDataList());

		populator.populate(cartWithInstallation(), target);

		assertEquals(Arrays.asList(0, 2), numbers(target.getEntries()));
		final OrderEntryData dishwasherLine = target.getEntries().get(0);
		assertInstallationCharged(dishwasherLine.getSelectedServices());

		final List<ProductServiceData> available = dishwasherLine.getAvailableServices();
		assertEquals("reference order", Arrays.asList(INSTALLATION, WARRANTY),
				available.stream().map(ProductServiceData::getCode).collect(Collectors.toList()));
		assertTrue("the selected one shows its charged price", available.get(0).isSelected());
		assertPrice(120d, available.get(0).getPrice());
		final ProductServiceData offered = available.get(1);
		assertFalse(offered.isSelected());
		assertEquals(Long.valueOf(2L), offered.getQuantity());
		assertPrice(79d, offered.getPrice());
		assertPrice(158d, offered.getTotalPrice());

		final OrderEntryData toasterLine = target.getEntries().get(1);
		assertTrue(toasterLine.getSelectedServices().isEmpty());
		assertTrue(toasterLine.getAvailableServices().isEmpty());
	}

	@Test
	public void shouldNotOfferAServiceWithoutAPrice()
	{
		givenPriceDataFactory();
		given(productServiceLookupService.getAvailableServices(dishwasher)).willReturn(Arrays.asList(installation, warranty));
		given(productServiceLookupService.getServicePrice(warranty, dishwasher)).willReturn(Optional.empty());
		final CartData target = new CartData();
		target.setEntries(entryDataList());

		populator.populate(cartWithInstallation(), target);

		assertEquals(Collections.singletonList(INSTALLATION), target.getEntries().get(0).getAvailableServices().stream()
				.map(ProductServiceData::getCode).collect(Collectors.toList()));
	}

	@Test
	public void shouldOfferServicesOnACartWithoutSelectedServices()
	{
		givenPriceDataFactory();
		final CartModel cart = withGroups(new CartModel());
		cart.setCurrency(eur);
		add(cart, new CartEntryModel(), 0, dishwasher, 3L, Double.valueOf(499d));
		given(productServiceLookupService.getAvailableServices(dishwasher)).willReturn(Collections.singletonList(warranty));
		given(productServiceLookupService.getServicePrice(warranty, dishwasher))
				.willReturn(Optional.of(new PriceInformation(new PriceValue("EUR", 79d, false))));
		final CartData target = new CartData();
		target.setEntries(new ArrayList<>(Collections.singletonList(entryData(0, 3L))));
		target.setTotalItems(Integer.valueOf(1));
		target.setTotalUnitCount(Integer.valueOf(3));

		populator.populate(cart, target);

		final ProductServiceData offered = target.getEntries().get(0).getAvailableServices().get(0);
		assertPrice(237d, offered.getTotalPrice());
		assertTrue(target.getEntries().get(0).getSelectedServices().isEmpty());
		assertEquals("counts untouched without service entries", Integer.valueOf(1), target.getTotalItems());
		assertEquals(Integer.valueOf(3), target.getTotalUnitCount());
	}

	@Test
	public void shouldCountOnlyProductLinesAndUnits()
	{
		givenPriceDataFactory();
		final CartData target = new CartData();
		target.setEntries(entryDataList());
		platformCounts(target);

		populator.populate(cartWithInstallation(), target);

		assertEquals(Integer.valueOf(2), target.getTotalItems());
		assertEquals(Integer.valueOf(3), target.getTotalUnitCount());
		assertEquals(Long.valueOf(3L), target.getDeliveryItemsQuantity());
	}

	@Test
	public void shouldOnlyAdjustTheCountsWhenThereAreNoEntryDtos()
	{
		final CartData miniCart = new CartData();
		platformCounts(miniCart);

		populator.populate(cartWithInstallation(), miniCart);

		assertEquals(Integer.valueOf(2), miniCart.getTotalItems());
		assertEquals(Integer.valueOf(3), miniCart.getTotalUnitCount());
		assertNull(miniCart.getEntries());
		verifyNoInteractions(productServiceLookupService, priceDataFactory);
	}

	@Test
	public void shouldShowAServiceRootGroupAsAStandaloneLineWithoutItsServices()
	{
		givenPriceDataFactory();
		final CartData target = new CartData();
		target.setEntries(entryDataList());
		final EntryGroupData serviceGroup = new EntryGroupData();
		serviceGroup.setGroupNumber(Integer.valueOf(1));
		serviceGroup.setGroupType(GroupType.SERVICE);
		serviceGroup.setLabel("SERVICE");
		serviceGroup.setOrderEntries(new ArrayList<>(Arrays.asList(target.getEntries().get(0), target.getEntries().get(1))));
		final EntryGroupData standalone = new EntryGroupData();
		standalone.setGroupType(GroupType.STANDALONE);
		standalone.setLabel("toaster");
		standalone.setOrderEntries(new ArrayList<>(Collections.singletonList(target.getEntries().get(2))));
		target.setRootGroups(new ArrayList<>(Arrays.asList(serviceGroup, standalone)));

		populator.populate(cartWithInstallation(), target);

		assertEquals(GroupType.STANDALONE, serviceGroup.getGroupType());
		assertEquals("", serviceGroup.getLabel());
		assertEquals(Collections.singletonList(0), numbers(serviceGroup.getOrderEntries()));
		assertEquals("toaster", standalone.getLabel());
		assertEquals(Collections.singletonList(2), numbers(standalone.getOrderEntries()));
	}

	@Test
	public void shouldRemoveServicesFromDeliveryAndPickupGroups()
	{
		givenPriceDataFactory();
		final CartData target = new CartData();
		target.setEntries(entryDataList());
		final DeliveryOrderEntryGroupData delivery = new DeliveryOrderEntryGroupData();
		delivery.setEntries(new ArrayList<>(target.getEntries()));
		delivery.setQuantity(Long.valueOf(5L));
		final PickupOrderEntryGroupData pickup = new PickupOrderEntryGroupData();
		pickup.setEntries(new ArrayList<>(Arrays.asList(entryData(1, 2L), entryData(2, 1L))));
		pickup.setQuantity(Long.valueOf(3L));
		target.setDeliveryOrderGroups(new ArrayList<>(Collections.singletonList(delivery)));
		target.setPickupOrderGroups(new ArrayList<>(Collections.singletonList(pickup)));

		populator.populate(cartWithInstallation(), target);

		assertEquals(Arrays.asList(0, 2), numbers(delivery.getEntries()));
		assertEquals(Long.valueOf(3L), delivery.getQuantity());
		assertEquals(Collections.singletonList(2), numbers(pickup.getEntries()));
		assertEquals(Long.valueOf(1L), pickup.getQuantity());
	}

	// --- order ----------------------------------------------------------------------------------------------------

	@Test
	public void shouldAttachSelectedServicesToEntriesAndUnconsignedEntriesOfAnOrder()
	{
		givenPriceDataFactory();
		final OrderData target = new OrderData();
		target.setEntries(entryDataList());
		target.setUnconsignedEntries(entryDataList());

		populator.populate(orderWithInstallation(), target);

		assertEquals(Arrays.asList(0, 2), numbers(target.getEntries()));
		assertEquals(Arrays.asList(0, 2), numbers(target.getUnconsignedEntries()));
		assertInstallationCharged(target.getEntries().get(0).getSelectedServices());
		assertInstallationCharged(target.getUnconsignedEntries().get(0).getSelectedServices());
		assertNull("orders offer nothing", target.getEntries().get(0).getAvailableServices());
		assertNull(target.getUnconsignedEntries().get(0).getAvailableServices());
		assertTrue(target.getUnconsignedEntries().get(1).getSelectedServices().isEmpty());
		verifyNoInteractions(productServiceLookupService);
	}

	@Test
	public void shouldAttachSelectedServicesInsideConsignmentsAndDropServiceConsignmentEntries()
	{
		givenPriceDataFactory();
		final OrderData target = new OrderData();
		target.setEntries(entryDataList());
		final ConsignmentData consignment = new ConsignmentData();
		final List<ConsignmentEntryData> consignmentEntries = new ArrayList<>();
		for (final OrderEntryData orderEntry : entryDataList())
		{
			final ConsignmentEntryData consignmentEntry = new ConsignmentEntryData();
			consignmentEntry.setOrderEntry(orderEntry);
			consignmentEntries.add(consignmentEntry);
		}
		final ConsignmentEntryData withoutOrderEntry = new ConsignmentEntryData();
		consignmentEntries.add(withoutOrderEntry);
		consignment.setEntries(consignmentEntries);
		target.setConsignments(new ArrayList<>(Collections.singletonList(consignment)));

		populator.populate(orderWithInstallation(), target);

		final List<ConsignmentEntryData> remaining = consignment.getEntries();
		assertEquals(3, remaining.size());
		assertEquals(Integer.valueOf(0), remaining.get(0).getOrderEntry().getEntryNumber());
		assertEquals(Integer.valueOf(2), remaining.get(1).getOrderEntry().getEntryNumber());
		assertSame(withoutOrderEntry, remaining.get(2));
		assertInstallationCharged(remaining.get(0).getOrderEntry().getSelectedServices());
		assertNull(remaining.get(0).getOrderEntry().getAvailableServices());
	}

	@Test
	public void shouldLeaveAnOrderWithoutServicesUntouched()
	{
		final OrderModel order = withGroups(new OrderModel());
		order.setCurrency(eur);
		add(order, new OrderEntryModel(), 0, toaster, 1L, Double.valueOf(30d));
		final OrderData target = new OrderData();
		final OrderEntryData toasterLine = entryData(0, 1L);
		target.setEntries(new ArrayList<>(Collections.singletonList(toasterLine)));
		target.setTotalItems(Integer.valueOf(1));

		populator.populate(order, target);

		assertNull(toasterLine.getSelectedServices());
		assertEquals(Integer.valueOf(1), target.getTotalItems());
		verifyNoInteractions(productServiceLookupService, priceDataFactory);
	}

	@Test
	public void shouldPriceAServiceWithoutAChargedPriceAtZero()
	{
		givenPriceDataFactory();
		final OrderModel order = withGroups(new OrderModel(), 1);
		order.setCurrency(eur);
		add(order, new OrderEntryModel(), 0, dishwasher, 1L, Double.valueOf(499d), 1);
		add(order, new OrderEntryModel(), 1, installation, 1L, null, 1);
		final OrderData target = new OrderData();
		target.setEntries(new ArrayList<>(Arrays.asList(entryData(0, 1L), entryData(1, 1L))));

		populator.populate(order, target);

		final ProductServiceData selected = target.getEntries().get(0).getSelectedServices().get(0);
		assertPrice(0d, selected.getPrice());
		assertPrice(0d, selected.getTotalPrice());
	}

	// --- null source ----------------------------------------------------------------------------------------------

	@Test
	public void shouldDoNothingForANullSourceOrSourceEntries()
	{
		final CartData target = new CartData();
		target.setEntries(entryDataList());
		platformCounts(target);
		final CartModel noEntries = new CartModel();
		noEntries.setEntries(null);

		populator.populate(null, target);
		populator.populate(noEntries, target);

		assertEquals(3, target.getEntries().size());
		assertEquals(Integer.valueOf(3), target.getTotalItems());
		verifyNoInteractions(productServiceLookupService, priceDataFactory);
	}
}
