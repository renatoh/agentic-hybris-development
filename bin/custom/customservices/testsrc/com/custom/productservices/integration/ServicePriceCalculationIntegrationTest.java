/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.productservices.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import de.hybris.bootstrap.annotations.IntegrationTest;
import de.hybris.platform.catalog.CatalogVersionService;
import de.hybris.platform.catalog.model.CatalogVersionModel;
import de.hybris.platform.core.enums.GroupType;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.CartEntryModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.product.UnitModel;
import de.hybris.platform.core.model.user.UserModel;
import de.hybris.platform.core.order.EntryGroup;
import de.hybris.platform.order.CalculationService;
import de.hybris.platform.order.CartService;
import de.hybris.platform.order.strategies.calculation.FindPriceStrategy;
import de.hybris.platform.product.ProductService;
import de.hybris.platform.product.UnitService;
import de.hybris.platform.servicelayer.ServicelayerTransactionalTest;
import de.hybris.platform.servicelayer.i18n.CommonI18NService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.user.UserService;
import de.hybris.platform.util.PriceValue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.annotation.Resource;

import org.junit.Before;
import org.junit.Test;

import com.custom.core.model.ServiceProductModel;
import com.custom.productservices.pricing.ServiceAwareSLFindPriceStrategy;


/**
 * NET-8943 &sect;5.2/&sect;5.2a, AC14, AC16: cart calculation through the real {@link CalculationService} with the
 * real Spring wiring ({@code slFindPriceStrategy} alias, {@code findPriceHooks} list, Europe1 row matching).
 * <p>
 * <b>Written, not executed:</b> this installation has no junit tenant, so these tests have never been run. The
 * equivalent flows were proven by the backend developer on the live server. Everything below uses its own catalog and
 * product codes ({@code NET8943_*}), never the electronics sample data.
 */
@IntegrationTest
public class ServicePriceCalculationIntegrationTest extends ServicelayerTransactionalTest
{
	private static final String CATALOG = "net8943TestCatalog";
	private static final String VERSION = "Online";
	private static final double DELTA = 0.0001d;

	private static final long DISHWASHER_PRICE = 499L;
	private static final long TOASTER_PRICE = 30L;
	private static final long INSTALL_LOW = 90L;
	private static final long INSTALL_MEDIUM = 120L;
	private static final long INSTALL_HIGH = 150L;
	private static final long WARRANTY_MEDIUM = 79L;
	private static final long ACCIDENTAL_PRODUCT_ROW = 999L;

	private static final String IMPEX = String.join("\n", //
			"INSERT_UPDATE Unit;unitType[unique=true];code[unique=true];conversion", //
			";pieces;pieces;1", //
			"INSERT_UPDATE Catalog;id[unique=true]", //
			";" + CATALOG, //
			"INSERT_UPDATE CatalogVersion;catalog(id)[unique=true];version[unique=true];active;languages(isocode)", //
			";" + CATALOG + ";" + VERSION + ";true;en", //
			"$cv=catalogVersion(catalog(id),version)[unique=true,default=" + CATALOG + ":" + VERSION + "]", //
			"INSERT_UPDATE Product;code[unique=true];$cv;unit(code);approvalStatus(code);servicePriceCondition(code)", //
			";NET8943_DISHWASHER;;pieces;approved;MEDIUM", //
			";NET8943_DISHWASHER_LOW;;pieces;approved;LOW", //
			";NET8943_DISHWASHER_HIGH;;pieces;approved;HIGH", //
			";NET8943_TOASTER;;pieces;approved;", //
			"INSERT_UPDATE ServiceProduct;code[unique=true];$cv;unit(code);approvalStatus(code)", //
			";NET8943_SVC_INSTALL;;pieces;approved", //
			";NET8943_SVC_WARRANTY;;pieces;approved", //
			"INSERT_UPDATE ProductPriceGroup;code[unique=true]", //
			";NET8943_SVC_INSTALL_LOW", //
			";NET8943_SVC_INSTALL_MEDIUM", //
			";NET8943_SVC_INSTALL_HIGH", //
			";NET8943_SVC_WARRANTY_MEDIUM", //
			"# product-specific rows for the physical products only (whole prices: no locale-dependent decimal separator)", //
			"$product=product(code,catalogVersion(catalog(id),version))", //
			"INSERT PriceRow;$product;unit(code);currency(isocode);price;minqtd;unitFactor;net", //
			";NET8943_DISHWASHER:" + CATALOG + ":" + VERSION + ";pieces;EUR;" + DISHWASHER_PRICE + ";1;1;false", //
			";NET8943_DISHWASHER_LOW:" + CATALOG + ":" + VERSION + ";pieces;EUR;" + DISHWASHER_PRICE + ";1;1;false", //
			";NET8943_DISHWASHER_HIGH:" + CATALOG + ":" + VERSION + ";pieces;EUR;" + DISHWASHER_PRICE + ";1;1;false", //
			";NET8943_TOASTER:" + CATALOG + ":" + VERSION + ";pieces;EUR;" + TOASTER_PRICE + ";1;1;false", //
			"# group-only rows (no product) for the services, spec 4.4", //
			"INSERT PriceRow;pg(code);unit(code);currency(isocode);price;minqtd;unitFactor;net", //
			";NET8943_SVC_INSTALL_LOW;pieces;EUR;" + INSTALL_LOW + ";1;1;false", //
			";NET8943_SVC_INSTALL_MEDIUM;pieces;EUR;" + INSTALL_MEDIUM + ";1;1;false", //
			";NET8943_SVC_INSTALL_HIGH;pieces;EUR;" + INSTALL_HIGH + ";1;1;false", //
			";NET8943_SVC_WARRANTY_MEDIUM;pieces;EUR;" + WARRANTY_MEDIUM + ";1;1;false", //
			"INSERT_UPDATE Customer;uid[unique=true];name", //
			";net8943-customer@example.com;NET8943 Customer", //
			"");

	@Resource
	private ModelService modelService;
	@Resource
	private CartService cartService;
	@Resource
	private CalculationService calculationService;
	@Resource
	private CatalogVersionService catalogVersionService;
	@Resource
	private ProductService productService;
	@Resource
	private UnitService unitService;
	@Resource
	private CommonI18NService commonI18NService;
	@Resource
	private UserService userService;
	@Resource(name = "slFindPriceStrategy")
	private FindPriceStrategy slFindPriceStrategy;
	@Resource(name = "defaultSLFindPriceStrategy")
	private FindPriceStrategy platformSLFindPriceStrategy;

	private CatalogVersionModel catalogVersion;
	private UnitModel pieces;
	private UserModel customer;

	@Before
	public void setUp() throws Exception
	{
		createCoreData();
		importStream(new ByteArrayInputStream(IMPEX.getBytes(StandardCharsets.UTF_8)), "UTF-8", "net8943-integration-test");

		catalogVersion = catalogVersionService.getCatalogVersion(CATALOG, VERSION);
		pieces = unitService.getUnitForCode("pieces");
		customer = userService.getUserForUID("net8943-customer@example.com");
	}

	// --- fixtures -------------------------------------------------------------------------------------------------

	private ProductModel product(final String code)
	{
		return productService.getProductForCode(catalogVersion, code);
	}

	private CartModel newCart()
	{
		final CartModel cart = modelService.create(CartModel.class);
		cart.setCode("net8943-" + System.nanoTime());
		cart.setUser(customer);
		cart.setCurrency(commonI18NService.getCurrency("EUR"));
		cart.setDate(new Date());
		cart.setNet(Boolean.FALSE);
		modelService.save(cart);
		return cart;
	}

	private CartEntryModel addEntry(final CartModel cart, final String productCode, final long quantity)
	{
		final CartEntryModel entry = cartService.addNewEntry(cart, product(productCode), quantity, pieces);
		modelService.save(entry);
		return entry;
	}

	/** Links the product entry and its service entries through a new SERVICE entry group, as spec 5.3 describes. */
	private void linkServices(final CartModel cart, final int groupNumber, final AbstractOrderEntryModel productEntry,
			final AbstractOrderEntryModel... serviceEntries)
	{
		final EntryGroup group = new EntryGroup();
		group.setGroupNumber(Integer.valueOf(groupNumber));
		group.setGroupType(GroupType.SERVICE);
		group.setPriority(Integer.valueOf(groupNumber));
		group.setErroneous(Boolean.FALSE);
		final List<EntryGroup> groups = cart.getEntryGroups() == null ? new ArrayList<>() : new ArrayList<>(cart.getEntryGroups());
		groups.add(group);
		cart.setEntryGroups(groups);
		productEntry.setEntryGroupNumbers(Collections.singleton(Integer.valueOf(groupNumber)));
		for (final AbstractOrderEntryModel serviceEntry : serviceEntries)
		{
			serviceEntry.setEntryGroupNumbers(Collections.singleton(Integer.valueOf(groupNumber)));
			modelService.save(serviceEntry);
		}
		modelService.save(productEntry);
		modelService.save(cart);
	}

	private void calculate(final CartModel cart) throws Exception
	{
		calculationService.calculate(cart);
		modelService.refresh(cart);
	}

	// --- wiring ---------------------------------------------------------------------------------------------------

	@Test
	public void shouldResolveTheSlFindPriceStrategyAliasToTheServiceAwareSubclass()
	{
		assertTrue("slFindPriceStrategy must be the NET-8943 subclass, got " + slFindPriceStrategy.getClass(),
				slFindPriceStrategy instanceof ServiceAwareSLFindPriceStrategy);
	}

	// --- (a) service entries priced from the condition's group ----------------------------------------------------

	@Test
	public void shouldPriceServiceEntriesFromTheMediumGroupAndLeaveTheProductPriceUnchanged() throws Exception
	{
		final CartModel cart = newCart();
		final CartEntryModel dishwasher = addEntry(cart, "NET8943_DISHWASHER", 2L);
		final CartEntryModel installation = addEntry(cart, "NET8943_SVC_INSTALL", 2L);
		final CartEntryModel warranty = addEntry(cart, "NET8943_SVC_WARRANTY", 2L);
		linkServices(cart, 1, dishwasher, installation, warranty);

		calculate(cart);

		assertTrue(installation.getProduct() instanceof ServiceProductModel);
		assertEquals(INSTALL_MEDIUM, installation.getBasePrice().doubleValue(), DELTA);
		assertEquals(2 * INSTALL_MEDIUM, installation.getTotalPrice().doubleValue(), DELTA);
		assertEquals(WARRANTY_MEDIUM, warranty.getBasePrice().doubleValue(), DELTA);
		assertEquals("the product's own entry is priced as before", DISHWASHER_PRICE, dishwasher.getBasePrice().doubleValue(),
				DELTA);
		assertEquals(2.0d * (DISHWASHER_PRICE + INSTALL_MEDIUM + WARRANTY_MEDIUM), cart.getSubtotal().doubleValue(), DELTA);
	}

	@Test
	public void shouldPriceTheSameServiceByEachLinkedProductsCondition() throws Exception
	{
		final CartModel cart = newCart();
		final CartEntryModel low = addEntry(cart, "NET8943_DISHWASHER_LOW", 1L);
		final CartEntryModel lowInstall = addEntry(cart, "NET8943_SVC_INSTALL", 1L);
		final CartEntryModel high = addEntry(cart, "NET8943_DISHWASHER_HIGH", 1L);
		final CartEntryModel highInstall = addEntry(cart, "NET8943_SVC_INSTALL", 1L);
		linkServices(cart, 1, low, lowInstall);
		linkServices(cart, 2, high, highInstall);

		calculate(cart);

		assertEquals(INSTALL_LOW, lowInstall.getBasePrice().doubleValue(), DELTA);
		assertEquals(INSTALL_HIGH, highInstall.getBasePrice().doubleValue(), DELTA);
	}

	// --- (b) AC16: a cart without services is priced exactly like the platform would --------------------------------

	@Test
	public void shouldCalculateACartWithoutServicesExactlyLikeThePlatformStrategy() throws Exception
	{
		final CartModel cart = newCart();
		final CartEntryModel dishwasher = addEntry(cart, "NET8943_DISHWASHER", 2L);
		final CartEntryModel toaster = addEntry(cart, "NET8943_TOASTER", 3L);

		calculate(cart);

		for (final CartEntryModel entry : List.of(dishwasher, toaster))
		{
			final PriceValue platform = platformSLFindPriceStrategy.findBasePrice(entry);
			final PriceValue ours = slFindPriceStrategy.findBasePrice(entry);
			assertNotNull(platform);
			assertEquals(platform.getValue(), ours.getValue(), DELTA);
			assertEquals(platform.getCurrencyIso(), ours.getCurrencyIso());
			assertEquals(platform.isNet(), ours.isNet());
			assertEquals(platform.getValue(), entry.getBasePrice().doubleValue(), DELTA);
		}
		assertEquals(DISHWASHER_PRICE, dishwasher.getBasePrice().doubleValue(), DELTA);
		assertEquals(TOASTER_PRICE, toaster.getBasePrice().doubleValue(), DELTA);
		assertEquals(2 * DISHWASHER_PRICE + 3 * TOASTER_PRICE, cart.getSubtotal().doubleValue(), DELTA);
	}

	// --- (c) documents Europe1 ranking: an accidental product-specific row on a service wins -----------------------

	/**
	 * Documents why sample data must never create a product-specific PriceRow for a service product (spec 4.4).
	 * <p>
	 * Expected result derived from the platform source, NOT from a run: {@code PriceRowPrepareInterceptor
	 * .calculateMatchValue} gives a product-only row matchValue 5 and a product-price-group-only row 4;
	 * {@code DefaultPriceQueryProvider} fetches product rows and group rows in one query (the hook's criteria carries
	 * both the service product and the group), and {@code PriceRowValueMatchComparator} sorts by matchValue
	 * descending before currency/net/unit/minqtd. So the product row overrides every condition's group price.
	 * <b>This test has not been executed</b> (no junit tenant in this installation).
	 */
	@Test
	public void shouldLetAnAccidentalProductSpecificRowOnAServiceOverrideTheGroupPrice() throws Exception
	{
		importStream(new ByteArrayInputStream(String.join("\n", //
				"INSERT PriceRow;product(code,catalogVersion(catalog(id),version));unit(code);currency(isocode);price;minqtd;unitFactor;net", //
				";NET8943_SVC_INSTALL:" + CATALOG + ":" + VERSION + ";pieces;EUR;" + ACCIDENTAL_PRODUCT_ROW + ";1;1;false", //
				"").getBytes(StandardCharsets.UTF_8)), "UTF-8", "net8943-accidental-row");

		final CartModel cart = newCart();
		final CartEntryModel dishwasher = addEntry(cart, "NET8943_DISHWASHER", 1L);
		final CartEntryModel installation = addEntry(cart, "NET8943_SVC_INSTALL", 1L);
		linkServices(cart, 1, dishwasher, installation);

		calculate(cart);

		assertEquals("product-specific row (matchValue 5) outranks the group row (matchValue 4)", ACCIDENTAL_PRODUCT_ROW,
				installation.getBasePrice().doubleValue(), DELTA);
		assertEquals(DISHWASHER_PRICE, dishwasher.getBasePrice().doubleValue(), DELTA);
	}

	// --- (d) group-only rows never leak into a normal product's price ------------------------------------------------

	@Test
	public void shouldNotLetGroupOnlyRowsChangeANormalProductsPrice() throws Exception
	{
		final CartModel cart = newCart();
		final CartEntryModel dishwasher = addEntry(cart, "NET8943_DISHWASHER", 1L);
		final CartEntryModel installation = addEntry(cart, "NET8943_SVC_INSTALL", 1L);
		final CartEntryModel toaster = addEntry(cart, "NET8943_TOASTER", 2L);
		linkServices(cart, 1, dishwasher, installation);

		calculate(cart);

		assertEquals(DISHWASHER_PRICE, dishwasher.getBasePrice().doubleValue(), DELTA);
		assertEquals(TOASTER_PRICE, toaster.getBasePrice().doubleValue(), DELTA);
		assertEquals(2 * TOASTER_PRICE, toaster.getTotalPrice().doubleValue(), DELTA);
		// the platform's own lookup for the linked physical product never picks a group row: it carries no price group
		assertEquals(DISHWASHER_PRICE, platformSLFindPriceStrategy.findBasePrice(dishwasher).getValue(), DELTA);
	}
}
