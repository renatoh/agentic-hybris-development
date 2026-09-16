package com.custom.orderhistory.populator;

import de.hybris.platform.core.enums.OrderStatus;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Single place that maps the external order status onto the hybris {@link OrderStatus} and onto the
 * <code>statusDisplay</code> suffix used by the <code>text.account.order.status.display.*</code>
 * message keys (NET-8938 &sect;6.1).
 * <p>
 * An unknown or missing external status maps to <code>null</code> / {@link #DISPLAY_UNKNOWN}, is
 * logged at WARN level, and the order is still shown.
 */
public class CustomOrderStatusMapper
{
	private static final Logger LOG = LoggerFactory.getLogger(CustomOrderStatusMapper.class);

	public static final String DISPLAY_UNKNOWN = "unknown";

	public static final String EXTERNAL_OPEN = "OPEN";
	public static final String EXTERNAL_IN_PROCESS = "IN_PROCESS";
	public static final String EXTERNAL_SHIPPED = "SHIPPED";
	public static final String EXTERNAL_COMPLETED = "COMPLETED";
	public static final String EXTERNAL_CANCELLED = "CANCELLED";

	/**
	 * <code>OrderStatus.PROCESSING</code> is not a declared enum value in this installation, so it is
	 * resolved dynamically. {@link OrderStatus} is a dynamic hybris enum, this creates a pure Java
	 * instance and touches neither the type system nor the database.
	 */
	protected static final OrderStatus STATUS_PROCESSING = OrderStatus.valueOf("PROCESSING");

	private static final Map<String, OrderStatus> STATUS_MAPPING;
	private static final Map<String, String> DISPLAY_MAPPING;

	static
	{
		final Map<String, OrderStatus> statuses = new HashMap<>();
		statuses.put(EXTERNAL_OPEN, OrderStatus.CREATED);
		statuses.put(EXTERNAL_IN_PROCESS, STATUS_PROCESSING);
		statuses.put(EXTERNAL_SHIPPED, OrderStatus.COMPLETED);
		statuses.put(EXTERNAL_COMPLETED, OrderStatus.COMPLETED);
		statuses.put(EXTERNAL_CANCELLED, OrderStatus.CANCELLED);
		STATUS_MAPPING = Collections.unmodifiableMap(statuses);

		final Map<String, String> displays = new HashMap<>();
		displays.put(EXTERNAL_OPEN, "created");
		displays.put(EXTERNAL_IN_PROCESS, "processing");
		displays.put(EXTERNAL_SHIPPED, "shipped");
		displays.put(EXTERNAL_COMPLETED, "completed");
		displays.put(EXTERNAL_CANCELLED, "cancelled");
		DISPLAY_MAPPING = Collections.unmodifiableMap(displays);
	}

	/**
	 * @return the mapped hybris order status, or <code>null</code> for an unknown or missing status
	 */
	public OrderStatus mapStatus(final String externalStatus)
	{
		final String key = normalize(externalStatus);
		final OrderStatus mapped = key == null ? null : STATUS_MAPPING.get(key);
		if (mapped == null)
		{
			logUnknown(externalStatus);
		}
		return mapped;
	}

	/**
	 * @return the <code>statusDisplay</code> suffix, {@link #DISPLAY_UNKNOWN} for an unknown or
	 *         missing status
	 */
	public String mapStatusDisplay(final String externalStatus)
	{
		final String key = normalize(externalStatus);
		final String mapped = key == null ? null : DISPLAY_MAPPING.get(key);
		if (mapped == null)
		{
			logUnknown(externalStatus);
			return DISPLAY_UNKNOWN;
		}
		return mapped;
	}

	protected String normalize(final String externalStatus)
	{
		return StringUtils.isBlank(externalStatus) ? null : StringUtils.upperCase(StringUtils.trim(externalStatus));
	}

	protected void logUnknown(final String externalStatus)
	{
		LOG.warn("Unknown or missing external order status [{}] - the order is shown with status [null] and statusDisplay [{}]",
				externalStatus, DISPLAY_UNKNOWN);
	}
}
