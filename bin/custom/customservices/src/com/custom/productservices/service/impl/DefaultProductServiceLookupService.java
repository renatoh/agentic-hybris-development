package com.custom.productservices.service.impl;

import de.hybris.platform.catalog.enums.ArticleApprovalStatus;
import de.hybris.platform.catalog.enums.ProductReferenceTypeEnum;
import de.hybris.platform.catalog.model.ProductReferenceModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.europe1.enums.ProductPriceGroup;
import de.hybris.platform.jalo.order.price.PriceInformation;
import de.hybris.platform.order.exceptions.CalculationException;
import de.hybris.platform.order.strategies.calculation.pdt.FindPDTValueInfoStrategy;
import de.hybris.platform.order.strategies.calculation.pdt.criteria.PDTCriteriaFactory;
import de.hybris.platform.order.strategies.calculation.pdt.criteria.PriceValueInfoCriteria;
import de.hybris.platform.product.BaseCriteria;
import de.hybris.platform.servicelayer.exceptions.UnknownIdentifierException;
import de.hybris.platform.search.restriction.SearchRestrictionService;
import de.hybris.platform.servicelayer.session.SessionExecutionBody;
import de.hybris.platform.servicelayer.session.SessionService;
import de.hybris.platform.servicelayer.time.TimeService;
import de.hybris.platform.servicelayer.type.TypeService;
import de.hybris.platform.servicelayer.user.UserNetCheckingStrategy;
import de.hybris.platform.servicelayer.user.UserService;
import de.hybris.platform.enumeration.EnumerationService;
import de.hybris.platform.util.PriceValue;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.custom.core.enums.ServicePriceCondition;
import com.custom.core.model.ServiceProductModel;
import com.custom.productservices.pricing.ServicePriceCriteria;
import com.custom.productservices.service.ProductServiceLookupService;


/**
 * NET-8943 &sect;5.1. No custom price matching: the standard criteria are built by the platform's
 * {@link PDTCriteriaFactory} and only the product price group is replaced.
 */
public class DefaultProductServiceLookupService implements ProductServiceLookupService
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultProductServiceLookupService.class);

	private EnumerationService enumerationService;
	private PDTCriteriaFactory pdtCriteriaFactory;
	private FindPDTValueInfoStrategy<PriceValue, PriceInformation, PriceValueInfoCriteria> findPriceValueInfoStrategy;
	private UserService userService;
	private UserNetCheckingStrategy userNetCheckingStrategy;
	private TimeService timeService;
	private SessionService sessionService;
	private SearchRestrictionService searchRestrictionService;

	@Override
	public List<ServiceProductModel> getAvailableServices(final ProductModel product)
	{
		if (product == null || product instanceof ServiceProductModel || product.getServicePriceCondition() == null)
		{
			return Collections.emptyList();
		}
		// Service products are hidden from storefront searches (Frontend_ServiceProduct), and reading a product's
		// references applies the search restrictions too, so references to hidden service products would disappear.
		// This is an internal read of the product's own references, so it runs without search restrictions.
		return sessionService.executeInLocalView(new SessionExecutionBody()
		{
			@Override
			public Object execute()
			{
				searchRestrictionService.disableSearchRestrictions();
				return readServices(product);
			}
		});
	}

	@SuppressWarnings("unchecked")
	private List<ServiceProductModel> readServices(final ProductModel product)
	{
		final Collection<ProductReferenceModel> references = product.getProductReferences();
		if (references == null)
		{
			return Collections.emptyList();
		}
		return references.stream() //
				.filter(ref -> Boolean.TRUE.equals(ref.getActive())) //
				.filter(ref -> ProductReferenceTypeEnum.SERVICE.equals(ref.getReferenceType())) //
				.map(ProductReferenceModel::getTarget) //
				.filter(ServiceProductModel.class::isInstance) //
				.map(ServiceProductModel.class::cast) //
				.filter(service -> ArticleApprovalStatus.APPROVED.equals(service.getApprovalStatus())) //
				.collect(Collectors.toList());
	}

	@Override
	public Optional<ProductPriceGroup> getServicePriceGroup(final ServiceProductModel service,
			final ServicePriceCondition condition)
	{
		if (service == null || condition == null)
		{
			return Optional.empty();
		}
		final String code = service.getCode() + "_" + condition.getCode();
		try
		{
			return Optional.of(enumerationService.getEnumerationValue(ProductPriceGroup.class, code));
		}
		catch (final UnknownIdentifierException e)
		{
			LOG.debug("No ProductPriceGroup '{}'", code);
			return Optional.empty();
		}
	}

	@Override
	public Optional<PriceInformation> getServicePrice(final ServiceProductModel service, final ProductModel product)
	{
		if (service == null || product == null)
		{
			return Optional.empty();
		}
		final Optional<ProductPriceGroup> group = getServicePriceGroup(service, product.getServicePriceCondition());
		if (group.isEmpty())
		{
			return Optional.empty();
		}
		try
		{
			final PriceValueInfoCriteria standard = pdtCriteriaFactory.priceInfoCriteriaFromBaseCriteria(new SessionBaseCriteria(service));
			final List<PriceInformation> prices = findPriceValueInfoStrategy
					.getPDTInformation(ServicePriceCriteria.withProductPriceGroup(standard, group.get()));
			return prices == null || prices.isEmpty() ? Optional.empty() : Optional.of(prices.get(0));
		}
		catch (final CalculationException e)
		{
			LOG.warn("Could not determine price of service {} for product {}", service.getCode(), product.getCode(), e);
			return Optional.empty();
		}
	}

	/** Product and date/net of the current session, as {@code DefaultPriceService} would pass them. */
	private final class SessionBaseCriteria implements BaseCriteria
	{
		private final ProductModel criteriaProduct;

		private SessionBaseCriteria(final ProductModel criteriaProduct)
		{
			this.criteriaProduct = criteriaProduct;
		}

		@Override
		public ProductModel getProduct()
		{
			return criteriaProduct;
		}

		@Override
		public Date getDate()
		{
			return timeService.getCurrentTime();
		}

		@Override
		public Boolean isNet()
		{
			return Boolean.valueOf(userNetCheckingStrategy.isNetUser(userService.getCurrentUser()));
		}
	}

	public void setEnumerationService(final EnumerationService enumerationService)
	{
		this.enumerationService = enumerationService;
	}

	public void setPdtCriteriaFactory(final PDTCriteriaFactory pdtCriteriaFactory)
	{
		this.pdtCriteriaFactory = pdtCriteriaFactory;
	}

	public void setFindPriceValueInfoStrategy(
			final FindPDTValueInfoStrategy<PriceValue, PriceInformation, PriceValueInfoCriteria> findPriceValueInfoStrategy)
	{
		this.findPriceValueInfoStrategy = findPriceValueInfoStrategy;
	}

	public void setUserService(final UserService userService)
	{
		this.userService = userService;
	}

	public void setUserNetCheckingStrategy(final UserNetCheckingStrategy userNetCheckingStrategy)
	{
		this.userNetCheckingStrategy = userNetCheckingStrategy;
	}

	public void setSessionService(final SessionService sessionService)
	{
		this.sessionService = sessionService;
	}

	public void setSearchRestrictionService(final SearchRestrictionService searchRestrictionService)
	{
		this.searchRestrictionService = searchRestrictionService;
	}

	public void setTimeService(final TimeService timeService)
	{
		this.timeService = timeService;
	}
}
