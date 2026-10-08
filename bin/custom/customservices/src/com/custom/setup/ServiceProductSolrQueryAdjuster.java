package com.custom.setup;

import java.util.Locale;
import java.util.regex.Pattern;


/**
 * NET-8943 &sect;4.2: service products must never be indexed as standalone products. The electronics sample
 * {@code SolrIndexerQuery}s select every {@code Product}, subtypes included, so they get an exclusion clause for
 * {@code ServiceProduct}. Pure string handling, kept apart from the persistence code so it can be tested.
 * <p>
 * Two shapes exist: the full queries select from {@code Product} and are extended with a trailing condition; the update
 * queries wrap a union in a {@code tbl} alias and end in {@code ORDER BY tbl.code}, so the condition goes in front of it.
 */
public final class ServiceProductSolrQueryAdjuster
{
	static final String FULL_QUERY_CLAUSE = " AND {PK} NOT IN ({{ SELECT {PK} FROM {ServiceProduct} }})";
	static final String UPDATE_QUERY_CLAUSE = " AND tbl.code NOT IN({{ SELECT {code} FROM {ServiceProduct} }}) ";
	private static final Pattern SELECTS_FROM_PRODUCT = Pattern.compile("FROM\\s*\\{\\s*Product\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern UPDATE_ORDER_BY = Pattern.compile("ORDER\\s+BY\\s+tbl\\.code", Pattern.CASE_INSENSITIVE);

	private ServiceProductSolrQueryAdjuster()
	{
		// static helper
	}

	/**
	 * @return the query with the exclusion added, or the query unchanged if it does not select products or already excludes
	 *         services
	 */
	public static String excludeServiceProducts(final String query)
	{
		if (query == null || !SELECTS_FROM_PRODUCT.matcher(query).find()
				|| query.toLowerCase(Locale.ROOT).contains("serviceproduct"))
		{
			return query;
		}
		final java.util.regex.Matcher orderByMatcher = UPDATE_ORDER_BY.matcher(query);
		int orderBy = -1;
		while (orderByMatcher.find())
		{
			orderBy = orderByMatcher.start();
		}
		if (orderBy >= 0)
		{
			return query.substring(0, orderBy).stripTrailing() + UPDATE_QUERY_CLAUSE + query.substring(orderBy);
		}
		return query.stripTrailing() + FULL_QUERY_CLAUSE;
	}
}
