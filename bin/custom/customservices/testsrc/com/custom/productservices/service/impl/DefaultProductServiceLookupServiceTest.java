/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.productservices.service.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.catalog.enums.ArticleApprovalStatus;
import de.hybris.platform.catalog.enums.ProductReferenceTypeEnum;
import de.hybris.platform.catalog.model.ProductReferenceModel;
import de.hybris.platform.core.model.c2l.CurrencyModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.product.UnitModel;
import de.hybris.platform.core.model.user.UserModel;
import de.hybris.platform.enumeration.EnumerationService;
import de.hybris.platform.europe1.enums.ProductPriceGroup;
import de.hybris.platform.europe1.enums.UserPriceGroup;
import de.hybris.platform.jalo.order.price.PriceInformation;
import de.hybris.platform.order.exceptions.CalculationException;
import de.hybris.platform.order.strategies.calculation.pdt.FindPDTValueInfoStrategy;
import de.hybris.platform.order.strategies.calculation.pdt.criteria.PDTCriteriaFactory;
import de.hybris.platform.order.strategies.calculation.pdt.criteria.PDTCriteria.PDTCriteriaTarget;
import de.hybris.platform.order.strategies.calculation.pdt.criteria.PriceValueInfoCriteria;
import de.hybris.platform.order.strategies.calculation.pdt.criteria.impl.DefaultPriceValueInfoCriteria;
import de.hybris.platform.product.BaseCriteria;
import de.hybris.platform.servicelayer.exceptions.UnknownIdentifierException;
import de.hybris.platform.search.restriction.SearchRestrictionService;
import de.hybris.platform.servicelayer.session.SessionExecutionBody;
import de.hybris.platform.servicelayer.session.SessionService;
import de.hybris.platform.servicelayer.time.TimeService;
import de.hybris.platform.servicelayer.user.UserNetCheckingStrategy;
import de.hybris.platform.servicelayer.user.UserService;
import de.hybris.platform.util.PriceValue;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.custom.core.enums.ServicePriceCondition;
import com.custom.core.model.ServiceProductModel;


/**
 * NET-8943 &sect;5.1, AC1, AC2, AC10. The service price is looked up by the platform's own strategy with the
 * platform-built criteria; only the product price group is swapped for {@code <serviceCode>_<conditionCode>}.
 */
@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class DefaultProductServiceLookupServiceTest
{
	private static final String INSTALLATION = "SVC_INSTALLATION";
	private static final ProductPriceGroup INSTALLATION_MEDIUM = ProductPriceGroup.valueOf("SVC_INSTALLATION_MEDIUM");

	@Mock
	private EnumerationService enumerationService;
	@Mock
	private PDTCriteriaFactory pdtCriteriaFactory;
	@Mock
	private FindPDTValueInfoStrategy<PriceValue, PriceInformation, PriceValueInfoCriteria> findPriceValueInfoStrategy;
	@Mock
	private UserService userService;
	@Mock
	private UserNetCheckingStrategy userNetCheckingStrategy;
	@Mock
	private TimeService timeService;
	@Mock
	private SessionService sessionService;
	@Mock
	private SearchRestrictionService searchRestrictionService;

	private DefaultProductServiceLookupService lookupService;

	private ProductModel dishwasher;
	private ServiceProductModel installation;
	private ServiceProductModel warranty;

	private final UserModel user = new UserModel();
	private final CurrencyModel currency = new CurrencyModel();
	private final UnitModel unit = new UnitModel();
	private final Date date = new Date(1_700_000_000_000L);

	@Before
	public void setUp()
	{
		lookupService = new DefaultProductServiceLookupService();
		lookupService.setEnumerationService(enumerationService);
		lookupService.setPdtCriteriaFactory(pdtCriteriaFactory);
		lookupService.setFindPriceValueInfoStrategy(findPriceValueInfoStrategy);
		lookupService.setUserService(userService);
		lookupService.setUserNetCheckingStrategy(userNetCheckingStrategy);
		lookupService.setTimeService(timeService);
		lookupService.setSessionService(sessionService);
		lookupService.setSearchRestrictionService(searchRestrictionService);

		dishwasher = new ProductModel();
		dishwasher.setCode("DISHWASHER");
		dishwasher.setServicePriceCondition(ServicePriceCondition.MEDIUM);

		installation = new ServiceProductModel();
		installation.setCode(INSTALLATION);
		warranty = new ServiceProductModel();
		warranty.setCode("SVC_WARRANTY_3Y");
		installation.setApprovalStatus(ArticleApprovalStatus.APPROVED);
		warranty.setApprovalStatus(ArticleApprovalStatus.APPROVED);
	}

	private ProductReferenceModel reference(final ProductModel target, final ProductReferenceTypeEnum type,
			final Boolean active)
	{
		final ProductReferenceModel reference = new ProductReferenceModel();
		reference.setSource(dishwasher);
		reference.setTarget(target);
		reference.setReferenceType(type);
		reference.setActive(active);
		return reference;
	}

	private PriceValueInfoCriteria standardInfoCriteria()
	{
		return DefaultPriceValueInfoCriteria.buildForInfo() //
				.withProduct(installation) //
				.withUser(user) //
				.withUserPriceGroup(UserPriceGroup.valueOf("B2C_VIP")) //
				.withCurrency(currency) //
				.withQuantity(1L) //
				.withUnit(unit) //
				.withDate(date) //
				.withNet(Boolean.FALSE) //
				.build();
	}

	private void givenGroup(final String code, final ProductPriceGroup group)
	{
		given(enumerationService.getEnumerationValue(ProductPriceGroup.class, code)).willReturn(group);
	}

	// --- getAvailableServices -------------------------------------------------------------------------------------

	/**
	 * Runs the body passed to executeInLocalView, and fails if search restrictions are disabled anywhere but inside
	 * that body: disabling them in the caller's session would leak into the storefront request.
	 */
	private void givenLocalViewRunsTheBody()
	{
		final boolean[] inLocalView = { false };
		given(sessionService.executeInLocalView(any(SessionExecutionBody.class))).willAnswer(inv -> {
			inLocalView[0] = true;
			try
			{
				return inv.<SessionExecutionBody> getArgument(0).execute();
			}
			finally
			{
				inLocalView[0] = false;
			}
		});
		willAnswer(inv -> {
			assertTrue("search restrictions may only be disabled inside the local view", inLocalView[0]);
			return null;
		}).given(searchRestrictionService).disableSearchRestrictions();
	}

	private void assertReadInLocalViewWithoutRestrictions()
	{
		final InOrder order = inOrder(sessionService, searchRestrictionService);
		order.verify(sessionService).executeInLocalView(any(SessionExecutionBody.class));
		order.verify(searchRestrictionService).disableSearchRestrictions();
	}

	@Test
	public void shouldReturnTheActiveServiceReferenceTargetsInReferenceOrder()
	{
		givenLocalViewRunsTheBody();
		dishwasher.setProductReferences(Arrays.asList( //
				reference(warranty, ProductReferenceTypeEnum.SERVICE, Boolean.TRUE), //
				reference(installation, ProductReferenceTypeEnum.SERVICE, Boolean.TRUE)));

		assertEquals(Arrays.asList(warranty, installation), lookupService.getAvailableServices(dishwasher));
		assertReadInLocalViewWithoutRestrictions();
	}

	@Test
	public void shouldReadTheReferencesOnlyInsideTheLocalView()
	{
		// a local view that does not run its body: nothing may be read or disabled outside it
		dishwasher.setProductReferences(
				Collections.singletonList(reference(installation, ProductReferenceTypeEnum.SERVICE, Boolean.TRUE)));
		given(sessionService.executeInLocalView(any(SessionExecutionBody.class))).willReturn(Collections.emptyList());

		assertTrue(lookupService.getAvailableServices(dishwasher).isEmpty());
		verifyNoInteractions(searchRestrictionService);
	}

	@Test
	public void shouldOfferNoServicesWhenTheProductHasNoConditionWithoutOpeningALocalView()
	{
		dishwasher.setServicePriceCondition(null);
		dishwasher.setProductReferences(
				Collections.singletonList(reference(installation, ProductReferenceTypeEnum.SERVICE, Boolean.TRUE)));

		assertTrue(lookupService.getAvailableServices(dishwasher).isEmpty());
		verifyNoInteractions(sessionService, searchRestrictionService);
	}

	@Test
	public void shouldNotOpenALocalViewForANullProductOrAServiceProduct()
	{
		installation.setServicePriceCondition(ServicePriceCondition.LOW);

		assertTrue(lookupService.getAvailableServices(null).isEmpty());
		assertTrue(lookupService.getAvailableServices(installation).isEmpty());
		verifyNoInteractions(sessionService, searchRestrictionService);
	}

	@Test
	public void shouldSkipAnInactiveReference()
	{
		givenLocalViewRunsTheBody();
		dishwasher.setProductReferences(Arrays.asList( //
				reference(warranty, ProductReferenceTypeEnum.SERVICE, Boolean.FALSE), //
				reference(warranty, ProductReferenceTypeEnum.SERVICE, null), //
				reference(installation, ProductReferenceTypeEnum.SERVICE, Boolean.TRUE)));

		assertEquals(Collections.singletonList(installation), lookupService.getAvailableServices(dishwasher));
	}

	@Test
	public void shouldSkipAServiceThatIsNotApproved()
	{
		givenLocalViewRunsTheBody();
		final ServiceProductModel unapproved = serviceWithStatus("SVC_UNAPPROVED", ArticleApprovalStatus.UNAPPROVED);
		final ServiceProductModel inCheck = serviceWithStatus("SVC_CHECK", ArticleApprovalStatus.CHECK);
		final ServiceProductModel noStatus = serviceWithStatus("SVC_NO_STATUS", null);
		dishwasher.setProductReferences(Arrays.asList( //
				reference(unapproved, ProductReferenceTypeEnum.SERVICE, Boolean.TRUE), //
				reference(installation, ProductReferenceTypeEnum.SERVICE, Boolean.TRUE), //
				reference(inCheck, ProductReferenceTypeEnum.SERVICE, Boolean.TRUE), //
				reference(noStatus, ProductReferenceTypeEnum.SERVICE, Boolean.TRUE)));

		assertEquals(Collections.singletonList(installation), lookupService.getAvailableServices(dishwasher));
	}

	private static ServiceProductModel serviceWithStatus(final String code, final ArticleApprovalStatus status)
	{
		final ServiceProductModel service = new ServiceProductModel();
		service.setCode(code);
		service.setApprovalStatus(status);
		return service;
	}

	@Test
	public void shouldSkipAReferenceOfAnotherType()
	{
		givenLocalViewRunsTheBody();
		dishwasher.setProductReferences(Arrays.asList( //
				reference(warranty, ProductReferenceTypeEnum.ACCESSORIES, Boolean.TRUE), //
				reference(warranty, ProductReferenceTypeEnum.SIMILAR, Boolean.TRUE), //
				reference(installation, ProductReferenceTypeEnum.SERVICE, Boolean.TRUE)));

		assertEquals(Collections.singletonList(installation), lookupService.getAvailableServices(dishwasher));
	}

	@Test
	public void shouldSkipAServiceReferenceWhoseTargetIsNotAServiceProduct()
	{
		givenLocalViewRunsTheBody();
		final ProductModel physical = new ProductModel();
		physical.setCode("RINSE_AID");
		dishwasher.setProductReferences(Arrays.asList( //
				reference(physical, ProductReferenceTypeEnum.SERVICE, Boolean.TRUE), //
				reference(installation, ProductReferenceTypeEnum.SERVICE, Boolean.TRUE)));

		assertEquals(Collections.singletonList(installation), lookupService.getAvailableServices(dishwasher));
	}

	@Test
	public void shouldReturnEmptyWhenTheProductHasNoReferences()
	{
		givenLocalViewRunsTheBody();
		dishwasher.setProductReferences(null);
		assertTrue(lookupService.getAvailableServices(dishwasher).isEmpty());

		dishwasher.setProductReferences(Collections.emptyList());
		assertTrue(lookupService.getAvailableServices(dishwasher).isEmpty());
	}

	// --- getServicePriceGroup -------------------------------------------------------------------------------------

	@Test
	public void shouldResolveThePriceGroupNamedAfterServiceAndCondition()
	{
		final ProductPriceGroup low = ProductPriceGroup.valueOf("SVC_INSTALLATION_LOW");
		final ProductPriceGroup high = ProductPriceGroup.valueOf("SVC_INSTALLATION_HIGH");
		givenGroup("SVC_INSTALLATION_LOW", low);
		givenGroup("SVC_INSTALLATION_MEDIUM", INSTALLATION_MEDIUM);
		givenGroup("SVC_INSTALLATION_HIGH", high);

		assertSame(low, lookupService.getServicePriceGroup(installation, ServicePriceCondition.LOW).get());
		assertSame(INSTALLATION_MEDIUM, lookupService.getServicePriceGroup(installation, ServicePriceCondition.MEDIUM).get());
		assertSame(high, lookupService.getServicePriceGroup(installation, ServicePriceCondition.HIGH).get());
	}

	@Test
	public void shouldReturnNoGroupWhenTheEnumValueIsUnknown()
	{
		willThrow(new UnknownIdentifierException("no such value")).given(enumerationService)
				.getEnumerationValue(ProductPriceGroup.class, "SVC_INSTALLATION_MEDIUM");

		assertFalse(lookupService.getServicePriceGroup(installation, ServicePriceCondition.MEDIUM).isPresent());
	}

	@Test
	public void shouldReturnNoGroupWithoutACondition()
	{
		assertFalse(lookupService.getServicePriceGroup(installation, null).isPresent());
		assertFalse(lookupService.getServicePriceGroup(null, ServicePriceCondition.LOW).isPresent());
		verifyNoInteractions(enumerationService);
	}

	// --- getServicePrice ------------------------------------------------------------------------------------------

	@Test
	public void shouldReturnNoPriceWhenTheProductHasNoCondition() throws CalculationException
	{
		dishwasher.setServicePriceCondition(null);

		assertFalse(lookupService.getServicePrice(installation, dishwasher).isPresent());
		verifyNoInteractions(findPriceValueInfoStrategy);
	}

	@Test
	public void shouldReturnNoPriceWhenThereIsNoPriceGroup() throws CalculationException
	{
		willThrow(new UnknownIdentifierException("no such value")).given(enumerationService)
				.getEnumerationValue(ProductPriceGroup.class, "SVC_INSTALLATION_MEDIUM");

		assertFalse(lookupService.getServicePrice(installation, dishwasher).isPresent());
		verifyNoInteractions(findPriceValueInfoStrategy);
	}

	@Test
	public void shouldReturnNoPriceWhenNoRowMatches() throws CalculationException
	{
		givenGroup("SVC_INSTALLATION_MEDIUM", INSTALLATION_MEDIUM);
		given(pdtCriteriaFactory.priceInfoCriteriaFromBaseCriteria(any(BaseCriteria.class))).willReturn(standardInfoCriteria());
		given(findPriceValueInfoStrategy.getPDTInformation(any(PriceValueInfoCriteria.class)))
				.willReturn(Collections.emptyList());

		assertFalse(lookupService.getServicePrice(installation, dishwasher).isPresent());
	}

	@Test
	public void shouldReturnNoPriceWhenTheStrategyReturnsNull() throws CalculationException
	{
		givenGroup("SVC_INSTALLATION_MEDIUM", INSTALLATION_MEDIUM);
		given(pdtCriteriaFactory.priceInfoCriteriaFromBaseCriteria(any(BaseCriteria.class))).willReturn(standardInfoCriteria());
		given(findPriceValueInfoStrategy.getPDTInformation(any(PriceValueInfoCriteria.class))).willReturn(null);

		assertFalse(lookupService.getServicePrice(installation, dishwasher).isPresent());
	}

	@Test
	public void shouldReturnNoPriceWhenTheLookupFails() throws CalculationException
	{
		givenGroup("SVC_INSTALLATION_MEDIUM", INSTALLATION_MEDIUM);
		given(pdtCriteriaFactory.priceInfoCriteriaFromBaseCriteria(any(BaseCriteria.class))).willReturn(standardInfoCriteria());
		willThrow(new CalculationException("boom")).given(findPriceValueInfoStrategy)
				.getPDTInformation(any(PriceValueInfoCriteria.class));

		assertFalse(lookupService.getServicePrice(installation, dishwasher).isPresent());
	}

	@Test
	public void shouldReturnTheFirstPriceFoundWithOnlyTheProductGroupReplaced() throws CalculationException
	{
		final PriceInformation first = new PriceInformation(new PriceValue("EUR", 120.0d, false));
		final PriceInformation second = new PriceInformation(new PriceValue("EUR", 999.0d, false));
		final PriceValueInfoCriteria standard = standardInfoCriteria();
		givenGroup("SVC_INSTALLATION_MEDIUM", INSTALLATION_MEDIUM);
		given(pdtCriteriaFactory.priceInfoCriteriaFromBaseCriteria(any(BaseCriteria.class))).willReturn(standard);
		given(findPriceValueInfoStrategy.getPDTInformation(any(PriceValueInfoCriteria.class)))
				.willReturn(Arrays.asList(first, second));

		final Optional<PriceInformation> price = lookupService.getServicePrice(installation, dishwasher);

		assertSame(first, price.get());

		final ArgumentCaptor<PriceValueInfoCriteria> captor = ArgumentCaptor.forClass(PriceValueInfoCriteria.class);
		verify(findPriceValueInfoStrategy).getPDTInformation(captor.capture());
		final PriceValueInfoCriteria passed = captor.getValue();
		assertSame(INSTALLATION_MEDIUM, passed.getProductGroup());
		assertEquals(PDTCriteriaTarget.INFORMATION, passed.getPDTCriteriaTarget());
		assertSame(standard.getProduct(), passed.getProduct());
		assertSame(standard.getUser(), passed.getUser());
		assertSame(standard.getUserGroup(), passed.getUserGroup());
		assertSame(standard.getCurrency(), passed.getCurrency());
		assertEquals(standard.getQuantity(), passed.getQuantity());
		assertSame(standard.getUnit(), passed.getUnit());
		assertEquals(standard.getDate(), passed.getDate());
		assertEquals(standard.isNet(), passed.isNet());
	}

	@Test
	public void shouldBuildTheStandardCriteriaForTheServiceProductWithSessionDateAndNet() throws CalculationException
	{
		givenGroup("SVC_INSTALLATION_MEDIUM", INSTALLATION_MEDIUM);
		given(pdtCriteriaFactory.priceInfoCriteriaFromBaseCriteria(any(BaseCriteria.class))).willReturn(standardInfoCriteria());
		given(findPriceValueInfoStrategy.getPDTInformation(any(PriceValueInfoCriteria.class)))
				.willReturn(Collections.emptyList());
		given(timeService.getCurrentTime()).willReturn(date);
		given(userService.getCurrentUser()).willReturn(user);
		given(userNetCheckingStrategy.isNetUser(user)).willReturn(true);

		lookupService.getServicePrice(installation, dishwasher);

		final ArgumentCaptor<BaseCriteria> captor = ArgumentCaptor.forClass(BaseCriteria.class);
		verify(pdtCriteriaFactory).priceInfoCriteriaFromBaseCriteria(captor.capture());
		final BaseCriteria base = captor.getValue();
		assertSame("the service, not the physical product, is priced", installation, base.getProduct());
		assertEquals(date, base.getDate());
		assertEquals(Boolean.TRUE, base.isNet());
	}

	@Test
	public void shouldReturnNoPriceForNullArguments()
	{
		assertFalse(lookupService.getServicePrice(null, dishwasher).isPresent());
		assertFalse(lookupService.getServicePrice(installation, null).isPresent());
		verifyNoInteractions(enumerationService, pdtCriteriaFactory, findPriceValueInfoStrategy);
	}
}
