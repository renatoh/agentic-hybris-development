package com.custom.orderhistory.facade;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commercefacades.order.data.OrderData;
import de.hybris.platform.commercefacades.order.data.OrderHistoryData;
import de.hybris.platform.commerceservices.customer.CustomerAccountService;
import de.hybris.platform.commerceservices.search.pagedata.PageableData;
import de.hybris.platform.commerceservices.search.pagedata.SearchPageData;
import de.hybris.platform.converters.Populator;
import de.hybris.platform.converters.impl.AbstractPopulatingConverter;
import de.hybris.platform.core.enums.OrderStatus;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.servicelayer.dto.converter.Converter;
import de.hybris.platform.servicelayer.exceptions.UnknownIdentifierException;
import de.hybris.platform.servicelayer.user.UserService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.custom.orderhistory.dto.CustomOrderDto;
import com.custom.orderhistory.dto.CustomOrderHistoryResponse;
import com.custom.orderhistory.populator.CustomOrderDetailPopulator;
import com.custom.orderhistory.populator.CustomOrderHistoryPopulator;
import com.custom.orderhistory.populator.CustomOrderStatusMapper;
import com.custom.orderhistory.service.CustomOrderHistoryService;


/**
 * Covers the in-memory sorting and paging of the facade (NET-8938 section 5.3, acceptance
 * criterion 8), the order detail lookup (section 5.5, acceptance criterion 15), and the fact that
 * the Commerce DB is never consulted by either (acceptance criteria 3 and 11).
 */
@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class CustomOrderFacadeTest
{
	private static final OrderStatus PROCESSING = OrderStatus.valueOf("PROCESSING");

	@Mock
	private CustomOrderHistoryService customOrderHistoryService;
	@Mock
	private UserService userService;
	@Mock
	private CustomerModel currentUser;
	/** The platform collaborator the DB history would come from - it must never be touched. */
	@Mock
	private CustomerAccountService customerAccountService;

	private CustomOrderFacade facade;

	@Before
	public void setUp()
	{
		facade = new CustomOrderFacade();
		facade.setCustomOrderHistoryService(customOrderHistoryService);
		facade.setCustomOrderHistoryConverter(newConverter());
		facade.setCustomOrderDetailConverter(newDetailConverter());
		facade.setUserService(userService);
		facade.setCustomerAccountService(customerAccountService);
	}

	/**
	 * The real populator behind a minimal Converter, so the test exercises the actual mapping.
	 * The total is left out on purpose - PriceDataFactory is not the subject of this test and the
	 * populator leaves the total null when the currency is absent.
	 */
	private Converter<CustomOrderDto, OrderHistoryData> newConverter()
	{
		final CustomOrderHistoryPopulator populator = new CustomOrderHistoryPopulator();
		populator.setCustomOrderStatusMapper(new CustomOrderStatusMapper());

		final AbstractPopulatingConverter<CustomOrderDto, OrderHistoryData> converter = new AbstractPopulatingConverter<>();
		converter.setTargetClass(OrderHistoryData.class);
		converter.setPopulators(Collections.<Populator<CustomOrderDto, OrderHistoryData>> singletonList(populator));
		return converter;
	}

	/**
	 * The real detail populator behind a minimal Converter (NET-8938 &sect;5.5, &sect;6.2).
	 */
	private Converter<CustomOrderDto, OrderData> newDetailConverter()
	{
		final CustomOrderDetailPopulator populator = new CustomOrderDetailPopulator();
		populator.setCustomOrderStatusMapper(new CustomOrderStatusMapper());

		final AbstractPopulatingConverter<CustomOrderDto, OrderData> converter = new AbstractPopulatingConverter<>();
		converter.setTargetClass(OrderData.class);
		converter.setPopulators(Collections.<Populator<CustomOrderDto, OrderData>> singletonList(populator));
		return converter;
	}

	private CustomOrderDto order(final String number, final String date, final String status)
	{
		final CustomOrderDto dto = new CustomOrderDto();
		dto.setOrderNumber(number);
		dto.setOrderDate(date);
		dto.setStatus(status);
		return dto;
	}

	private void givenOrders(final CustomOrderDto... orders)
	{
		final CustomOrderHistoryResponse response = new CustomOrderHistoryResponse();
		response.setOrders(new ArrayList<>(Arrays.asList(orders)));
		given(userService.getCurrentUser()).willReturn(currentUser);
		given(currentUser.getUid()).willReturn("customer@example.com");
		given(customOrderHistoryService.getOrderHistory("customer@example.com")).willReturn(response);
	}

	/** The 10 mock orders, deliberately handed over in ascending date order. */
	private void givenTheTenMockOrders()
	{
		givenOrders(order("SO-100001", "2026-01-15", "COMPLETED"), order("SO-100002", "2026-01-26", "COMPLETED"),
				order("SO-100003", "2026-02-06", "COMPLETED"), order("SO-100004", "2026-02-17", "COMPLETED"),
				order("SO-100005", "2026-02-28", "COMPLETED"), order("SO-100006", "2026-03-11", "SHIPPED"),
				order("SO-100007", "2026-03-22", "CANCELLED"), order("SO-100008", "2026-04-02", "SHIPPED"),
				order("SO-100009", "2026-04-13", "IN_PROCESS"), order("SO-100010", "2026-04-24", "OPEN"));
	}

	private PageableData pageable(final int currentPage, final int pageSize)
	{
		final PageableData pageableData = new PageableData();
		pageableData.setCurrentPage(currentPage);
		pageableData.setPageSize(pageSize);
		return pageableData;
	}

	private List<String> codesOf(final SearchPageData<OrderHistoryData> page)
	{
		final List<String> codes = new ArrayList<>();
		for (final OrderHistoryData data : page.getResults())
		{
			codes.add(data.getCode());
		}
		return codes;
	}

	@Test
	public void shouldReturnTheNewestOrdersFirstOnTheFirstPage()
	{
		givenTheTenMockOrders();

		final SearchPageData<OrderHistoryData> page = facade.getPagedOrderHistoryForStatuses(pageable(0, 5));

		assertEquals(Arrays.asList("SO-100010", "SO-100009", "SO-100008", "SO-100007", "SO-100006"), codesOf(page));
	}

	@Test
	public void shouldReturnTheRemainingOrdersOnTheSecondPage()
	{
		givenTheTenMockOrders();

		final SearchPageData<OrderHistoryData> page = facade.getPagedOrderHistoryForStatuses(pageable(1, 5));

		assertEquals(Arrays.asList("SO-100005", "SO-100004", "SO-100003", "SO-100002", "SO-100001"), codesOf(page));
	}

	@Test
	public void shouldFillThePaginationCompletely()
	{
		givenTheTenMockOrders();

		final SearchPageData<OrderHistoryData> page = facade.getPagedOrderHistoryForStatuses(pageable(1, 5));

		assertEquals(10, page.getPagination().getTotalNumberOfResults());
		assertEquals(2, page.getPagination().getNumberOfPages());
		assertEquals(1, page.getPagination().getCurrentPage());
		assertEquals(5, page.getPagination().getPageSize());
	}

	@Test
	public void shouldHandleATrailingPartialPage()
	{
		givenTheTenMockOrders();

		final SearchPageData<OrderHistoryData> page = facade.getPagedOrderHistoryForStatuses(pageable(3, 3));

		assertEquals(Collections.singletonList("SO-100001"), codesOf(page));
		assertEquals(4, page.getPagination().getNumberOfPages());
		assertEquals(10, page.getPagination().getTotalNumberOfResults());
	}

	@Test
	public void shouldClampAPageRequestBeyondTheLastPage()
	{
		givenTheTenMockOrders();

		final SearchPageData<OrderHistoryData> page = facade.getPagedOrderHistoryForStatuses(pageable(99, 5));

		assertEquals(1, page.getPagination().getCurrentPage());
		assertEquals(Arrays.asList("SO-100005", "SO-100004", "SO-100003", "SO-100002", "SO-100001"), codesOf(page));
	}

	/** "Show all" passes a non-positive page size - everything must land on a single page. */
	@Test
	public void shouldRenderEverythingOnOnePageForShowAll()
	{
		givenTheTenMockOrders();

		final SearchPageData<OrderHistoryData> page = facade.getPagedOrderHistoryForStatuses(pageable(0, 0));

		assertEquals(10, page.getResults().size());
		assertEquals(1, page.getPagination().getNumberOfPages());
		assertEquals(10, page.getPagination().getPageSize());
	}

	@Test
	public void shouldReturnAnEmptyPageWhenTheCustomerHasNoOrders()
	{
		givenOrders();

		final SearchPageData<OrderHistoryData> page = facade.getPagedOrderHistoryForStatuses(pageable(0, 5));

		assertTrue(page.getResults().isEmpty());
		assertEquals(0, page.getPagination().getTotalNumberOfResults());
		assertEquals(0, page.getPagination().getNumberOfPages());
	}

	/** NET-8938 section 5.3: the filter runs on the MAPPED hybris status. */
	@Test
	public void shouldFilterOnTheMappedHybrisStatus()
	{
		givenTheTenMockOrders();

		final SearchPageData<OrderHistoryData> page = facade.getPagedOrderHistoryForStatuses(pageable(0, 20),
				OrderStatus.COMPLETED);

		// the five COMPLETED plus the two SHIPPED, which map onto COMPLETED as well
		assertEquals(Arrays.asList("SO-100008", "SO-100006", "SO-100005", "SO-100004", "SO-100003", "SO-100002", "SO-100001"),
				codesOf(page));
	}

	@Test
	public void shouldFilterOnSeveralStatuses()
	{
		givenTheTenMockOrders();

		final SearchPageData<OrderHistoryData> page = facade.getPagedOrderHistoryForStatuses(pageable(0, 20),
				OrderStatus.CANCELLED, PROCESSING);

		assertEquals(Arrays.asList("SO-100009", "SO-100007"), codesOf(page));
	}

	@Test
	public void shouldNotFilterWhenNoStatusIsGiven()
	{
		givenTheTenMockOrders();

		assertEquals(10, facade.getPagedOrderHistoryForStatuses(pageable(0, 20)).getResults().size());
	}

	@Test
	public void shouldSortOrdersWithoutADateLast()
	{
		givenOrders(order("SO-1", "2026-01-15", "OPEN"), order("SO-NO-DATE", null, "OPEN"),
				order("SO-2", "2026-03-15", "OPEN"));

		final SearchPageData<OrderHistoryData> page = facade.getPagedOrderHistoryForStatuses(pageable(0, 20));

		assertEquals(Arrays.asList("SO-2", "SO-1", "SO-NO-DATE"), codesOf(page));
	}

	@Test
	public void shouldKeepAnOrderWithAnUnknownStatusButExcludeItFromAFilteredList()
	{
		givenOrders(order("SO-ODD", "2026-01-15", "ON_HOLD"));

		assertEquals(Collections.singletonList("SO-ODD"), codesOf(facade.getPagedOrderHistoryForStatuses(pageable(0, 20))));
		assertTrue(facade.getPagedOrderHistoryForStatuses(pageable(0, 20), OrderStatus.CREATED).getResults().isEmpty());
	}

	/**
	 * NET-8938 acceptance criterion 3: the DB history is ignored, not merged and not a fallback.
	 * super.getPagedOrderHistoryForStatuses would go through the CustomerAccountService.
	 */
	@Test
	public void shouldNeverConsultTheCommerceDatabase()
	{
		givenTheTenMockOrders();

		facade.getPagedOrderHistoryForStatuses(pageable(0, 5));

		verifyNoInteractions(customerAccountService);
	}

	/** The convenience method of NET-8938 section 5.3 must behave like the unfiltered call. */
	@Test
	public void shouldDelegateGetPagedOrderHistoryToTheUnfilteredCall()
	{
		givenTheTenMockOrders();

		assertEquals(codesOf(facade.getPagedOrderHistoryForStatuses(pageable(0, 5))),
				codesOf(facade.getPagedOrderHistory(pageable(0, 5))));
	}

	@Test
	public void shouldTolerateAResponseWithoutAnOrderList()
	{
		final CustomOrderHistoryResponse response = new CustomOrderHistoryResponse();
		response.setOrders(null);
		given(userService.getCurrentUser()).willReturn(currentUser);
		given(currentUser.getUid()).willReturn("customer@example.com");
		given(customOrderHistoryService.getOrderHistory("customer@example.com")).willReturn(response);

		assertTrue(facade.getPagedOrderHistoryForStatuses(pageable(0, 5)).getResults().isEmpty());
	}

	/**
	 * NET-8938 acceptance criterion 12: the detail page is built from the external order.
	 */
	@Test
	public void shouldReturnTheConvertedOrderDetail()
	{
		given(userService.getCurrentUser()).willReturn(currentUser);
		given(currentUser.getUid()).willReturn("customer@example.com");
		given(customOrderHistoryService.getOrderDetail("customer@example.com", "SO-100001"))
				.willReturn(Optional.of(order("SO-100001", "2026-01-15", "COMPLETED")));

		final OrderData orderData = facade.getOrderDetailsForCode("SO-100001");

		assertEquals("SO-100001", orderData.getCode());
	}

	/**
	 * NET-8938 acceptance criterion 14: an unknown order code throws exactly the exception
	 * {@code AccountPageController.order(...)} already catches - no new exception type.
	 */
	@Test(expected = UnknownIdentifierException.class)
	public void shouldThrowUnknownIdentifierExceptionForAnUnknownOrderCode()
	{
		given(userService.getCurrentUser()).willReturn(currentUser);
		given(currentUser.getUid()).willReturn("customer@example.com");
		given(customOrderHistoryService.getOrderDetail("customer@example.com", "SO-999999")).willReturn(Optional.empty());

		facade.getOrderDetailsForCode("SO-999999");
	}

	/**
	 * NET-8938 acceptance criterion 11: the detail page reaches no DB order read.
	 */
	@Test
	public void shouldNeverConsultTheCommerceDatabaseForTheOrderDetail()
	{
		given(userService.getCurrentUser()).willReturn(currentUser);
		given(currentUser.getUid()).willReturn("customer@example.com");
		given(customOrderHistoryService.getOrderDetail("customer@example.com", "SO-100001"))
				.willReturn(Optional.of(order("SO-100001", "2026-01-15", "COMPLETED")));

		facade.getOrderDetailsForCode("SO-100001");

		verifyNoInteractions(customerAccountService);
	}
}
