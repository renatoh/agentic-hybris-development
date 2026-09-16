package com.custom.orderhistory.facade;

import de.hybris.platform.commercefacades.order.data.OrderData;
import de.hybris.platform.commercefacades.order.data.OrderHistoryData;
import de.hybris.platform.commercefacades.order.impl.DefaultOrderFacade;
import de.hybris.platform.commerceservices.search.pagedata.PageableData;
import de.hybris.platform.commerceservices.search.pagedata.PaginationData;
import de.hybris.platform.commerceservices.search.pagedata.SearchPageData;
import de.hybris.platform.core.enums.OrderStatus;
import de.hybris.platform.core.model.user.UserModel;
import de.hybris.platform.servicelayer.dto.converter.Converter;
import de.hybris.platform.servicelayer.exceptions.UnknownIdentifierException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.custom.orderhistory.dto.CustomOrderDto;
import com.custom.orderhistory.dto.CustomOrderHistoryResponse;
import com.custom.orderhistory.service.CustomOrderHistoryService;


/**
 * Order facade that serves the My Account order history and order detail from the external system
 * (NET-8938 &sect;5.3, &sect;5.5).
 * <p>
 * Only {@link #getPagedOrderHistory(PageableData)}, {@link #getPagedOrderHistoryForStatuses}
 * and {@link #getOrderDetailsForCode(String)} are overridden. Every other method - in particular the
 * guest/GUID detail variants {@code getOrderDetailsForCodeWithoutUser} and
 * {@code getOrderDetailsForGUID} - keeps the unchanged platform behaviour and still reads from the
 * Commerce DB (NET-8938 &sect;3, &sect;8 q7).
 * <p>
 * <code>super</code> is deliberately <em>not</em> called in the overridden methods: the order
 * history stored in the Commerce DB is ignored, not merged and not used as a fallback.
 * <p>
 * B2C only - no B2B order history variant (approval workflows, organisation-wide lists).
 */
public class CustomOrderFacade extends DefaultOrderFacade
{
	private static final Logger LOG = LoggerFactory.getLogger(CustomOrderFacade.class);

	protected static final int DEFAULT_PAGE_SIZE = 5;

	private CustomOrderHistoryService customOrderHistoryService;
	private Converter<CustomOrderDto, OrderHistoryData> customOrderHistoryConverter;
	private Converter<CustomOrderDto, OrderData> customOrderDetailConverter;

	/**
	 * Paged order history without a status filter.
	 * <p>
	 * NOTE: this method is <em>not</em> declared by
	 * {@link de.hybris.platform.commercefacades.order.OrderFacade} in SAP Commerce 2211, so it cannot
	 * carry {@code @Override}. It is kept as the convenience entry point named in NET-8938 &sect;5.3
	 * and simply delegates to {@link #getPagedOrderHistoryForStatuses(PageableData, OrderStatus...)}.
	 */
	public SearchPageData<OrderHistoryData> getPagedOrderHistory(final PageableData pageableData)
	{
		return getPagedOrderHistoryForStatuses(pageableData);
	}

	@Override
	public SearchPageData<OrderHistoryData> getPagedOrderHistoryForStatuses(final PageableData pageableData,
			final OrderStatus... statuses)
	{
		final List<OrderHistoryData> converted = getConvertedOrderHistory();
		final List<OrderHistoryData> filtered = filterByStatuses(converted, statuses);
		sortByPlacedDescending(filtered);
		return buildPage(filtered, pageableData);
	}

	/**
	 * Reads the full history from the external system and converts it to {@link OrderHistoryData}.
	 */
	protected List<OrderHistoryData> getConvertedOrderHistory()
	{
		final CustomOrderHistoryResponse response = getCustomOrderHistoryService()
				.getOrderHistory(resolveCurrentCustomerNumber());
		final List<CustomOrderDto> orders = response == null || response.getOrders() == null ? Collections.emptyList()
				: response.getOrders();

		final List<OrderHistoryData> result = new ArrayList<>(orders.size());
		for (final CustomOrderDto order : orders)
		{
			if (order != null)
			{
				result.add(getCustomOrderHistoryConverter().convert(order));
			}
		}
		return result;
	}

	/**
	 * Order detail (NET-8938 &sect;5.5). <code>super.getOrderDetailsForCode(...)</code> is never
	 * called - there is no DB fallback, that is the whole point of this change.
	 * <p>
	 * An unknown order code throws {@link UnknownIdentifierException}, exactly what
	 * {@code AccountPageController.order(...)} already catches to redirect to the order history page
	 * with the standard not-found message - no new exception type, no new JSP.
	 */
	@Override
	public OrderData getOrderDetailsForCode(final String code)
	{
		return getCustomOrderHistoryService().getOrderDetail(resolveCurrentCustomerNumber(), code)
				.map(getCustomOrderDetailConverter()::convert)
				.orElseThrow(() -> new UnknownIdentifierException("Order [" + code + "] not found"));
	}

	/**
	 * Which Commerce customer attribute carries the external <code>customerNumber</code> is still
	 * open (NET-8938 &sect;8 question 1); the current user's uid is used until that is decided. The
	 * mock implementation returns its bundled file regardless of the value.
	 */
	protected String resolveCurrentCustomerNumber()
	{
		final UserModel currentUser = getUserService().getCurrentUser();
		return currentUser == null ? null : currentUser.getUid();
	}

	/**
	 * Filters on the <em>mapped</em> hybris {@link OrderStatus} (NET-8938 &sect;6.1). No statuses
	 * given means no filter.
	 */
	protected List<OrderHistoryData> filterByStatuses(final List<OrderHistoryData> orders, final OrderStatus... statuses)
	{
		if (statuses == null || statuses.length == 0)
		{
			return new ArrayList<>(orders);
		}
		final Set<OrderStatus> wanted = new HashSet<>(Arrays.asList(statuses));
		final List<OrderHistoryData> result = new ArrayList<>();
		for (final OrderHistoryData order : orders)
		{
			if (order.getStatus() != null && wanted.contains(order.getStatus()))
			{
				result.add(order);
			}
		}
		return result;
	}

	/**
	 * Newest first. Orders without a date sort last.
	 */
	protected void sortByPlacedDescending(final List<OrderHistoryData> orders)
	{
		orders.sort(Comparator.comparing(OrderHistoryData::getPlaced, Comparator.nullsLast(Comparator.<Date> reverseOrder())));
	}

	/**
	 * Applies paging in memory and fills {@link SearchPageData#getPagination()} completely so that
	 * the existing paging tags in the order history JSP keep working.
	 */
	protected SearchPageData<OrderHistoryData> buildPage(final List<OrderHistoryData> orders, final PageableData pageableData)
	{
		final int totalResults = orders.size();
		final int pageSize = resolvePageSize(pageableData, totalResults);
		final int numberOfPages = pageSize <= 0 ? 0 : (int) Math.ceil((double) totalResults / (double) pageSize);

		int currentPage = pageableData == null ? 0 : Math.max(0, pageableData.getCurrentPage());
		if (numberOfPages > 0 && currentPage > numberOfPages - 1)
		{
			LOG.debug("Requested page [{}] is beyond the last page [{}] - falling back to the last page", currentPage,
					numberOfPages - 1);
			currentPage = numberOfPages - 1;
		}

		final int fromIndex = Math.min(currentPage * pageSize, totalResults);
		final int toIndex = Math.min(fromIndex + pageSize, totalResults);

		final PaginationData pagination = new PaginationData();
		pagination.setPageSize(pageSize);
		pagination.setCurrentPage(currentPage);
		pagination.setNumberOfPages(numberOfPages);
		pagination.setTotalNumberOfResults(totalResults);
		pagination.setSort(pageableData == null ? null : pageableData.getSort());

		final SearchPageData<OrderHistoryData> result = new SearchPageData<>();
		result.setResults(new ArrayList<>(orders.subList(fromIndex, toIndex)));
		result.setPagination(pagination);
		result.setSorts(Collections.emptyList());
		return result;
	}

	protected int resolvePageSize(final PageableData pageableData, final int totalResults)
	{
		if (pageableData != null && pageableData.getPageSize() > 0)
		{
			return pageableData.getPageSize();
		}
		// "show all" passes a non-positive page size - render everything on a single page
		return totalResults > 0 ? totalResults : DEFAULT_PAGE_SIZE;
	}

	protected CustomOrderHistoryService getCustomOrderHistoryService()
	{
		return customOrderHistoryService;
	}

	public void setCustomOrderHistoryService(final CustomOrderHistoryService customOrderHistoryService)
	{
		this.customOrderHistoryService = customOrderHistoryService;
	}

	protected Converter<CustomOrderDto, OrderHistoryData> getCustomOrderHistoryConverter()
	{
		return customOrderHistoryConverter;
	}

	public void setCustomOrderHistoryConverter(final Converter<CustomOrderDto, OrderHistoryData> customOrderHistoryConverter)
	{
		this.customOrderHistoryConverter = customOrderHistoryConverter;
	}

	protected Converter<CustomOrderDto, OrderData> getCustomOrderDetailConverter()
	{
		return customOrderDetailConverter;
	}

	public void setCustomOrderDetailConverter(final Converter<CustomOrderDto, OrderData> customOrderDetailConverter)
	{
		this.customOrderDetailConverter = customOrderDetailConverter;
	}
}
