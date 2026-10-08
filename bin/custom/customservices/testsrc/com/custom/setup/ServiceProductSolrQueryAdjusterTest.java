/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.setup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import de.hybris.bootstrap.annotations.UnitTest;

import org.junit.Test;


/**
 * NET-8943 &sect;4.2, AC6: the electronics {@code SolrIndexerQuery}s get a {@code ServiceProduct} exclusion so services
 * are never indexed. Fixtures are the two real query shapes of the electronics index.
 */
@UnitTest
public class ServiceProductSolrQueryAdjusterTest
{
	private static final String FULL_QUERY = "SELECT {PK} FROM {Product} WHERE ({varianttype} IS NULL OR {varianttype} NOT IN ( "
			+ "{{ SELECT {PK} FROM {varianttype} WHERE {code} = 'ElectronicsColorVariantProduct'}}) ) AND {code} NOT IN( "
			+ "{{ SELECT {code} FROM {GenericVariantProduct} }})";

	private static final String UPDATE_QUERY_BEFORE_ORDER_BY = "SELECT DISTINCT tbl.pk, tbl.code FROM ( {{ SELECT DISTINCT "
			+ "{p:PK} AS pk, {p:code} AS code FROM {Product AS p} }} ) tbl WHERE tbl.code NOT IN({{ SELECT {code} FROM "
			+ "{GenericVariantProduct} }})";
	private static final String ORDER_BY = "ORDER BY tbl.code";
	private static final String UPDATE_QUERY = UPDATE_QUERY_BEFORE_ORDER_BY + " " + ORDER_BY;

	@Test
	public void shouldReturnNullForNull()
	{
		assertNull(ServiceProductSolrQueryAdjuster.excludeServiceProducts(null));
	}

	@Test
	public void shouldAppendTheExclusionToTheFullQuery()
	{
		assertEquals(FULL_QUERY + ServiceProductSolrQueryAdjuster.FULL_QUERY_CLAUSE,
				ServiceProductSolrQueryAdjuster.excludeServiceProducts(FULL_QUERY));
	}

	@Test
	public void shouldAppendTheExclusionAfterTrailingWhitespace()
	{
		assertEquals(FULL_QUERY + ServiceProductSolrQueryAdjuster.FULL_QUERY_CLAUSE,
				ServiceProductSolrQueryAdjuster.excludeServiceProducts(FULL_QUERY + "  \n"));
	}

	@Test
	public void shouldInsertTheExclusionBeforeTheOrderByOfTheUpdateQuery()
	{
		final String adjusted = ServiceProductSolrQueryAdjuster.excludeServiceProducts(UPDATE_QUERY);

		assertEquals(UPDATE_QUERY_BEFORE_ORDER_BY + ServiceProductSolrQueryAdjuster.UPDATE_QUERY_CLAUSE + ORDER_BY, adjusted);
		assertTrue("ORDER BY stays last", adjusted.endsWith(ORDER_BY));
		assertTrue("everything before the clause is untouched", adjusted.startsWith(UPDATE_QUERY_BEFORE_ORDER_BY));
	}

	@Test
	public void shouldInsertTheExclusionBeforeALowercaseOrderBy()
	{
		final String lowercase = UPDATE_QUERY_BEFORE_ORDER_BY.toLowerCase() + " order by tbl.code";

		final String adjusted = ServiceProductSolrQueryAdjuster.excludeServiceProducts(lowercase);

		assertEquals(UPDATE_QUERY_BEFORE_ORDER_BY.toLowerCase() + ServiceProductSolrQueryAdjuster.UPDATE_QUERY_CLAUSE
				+ "order by tbl.code", adjusted);
		assertTrue("ORDER BY stays last", adjusted.endsWith("order by tbl.code"));
	}

	@Test
	public void shouldInsertTheExclusionBeforeAnOrderByWithExtraWhitespace()
	{
		final String spaced = UPDATE_QUERY_BEFORE_ORDER_BY + "\n  Order  By\ttbl.code";

		assertEquals(UPDATE_QUERY_BEFORE_ORDER_BY + ServiceProductSolrQueryAdjuster.UPDATE_QUERY_CLAUSE + "Order  By\ttbl.code",
				ServiceProductSolrQueryAdjuster.excludeServiceProducts(spaced));
	}

	@Test
	public void shouldBeIdempotentForALowercaseUpdateQuery()
	{
		final String once = ServiceProductSolrQueryAdjuster.excludeServiceProducts(UPDATE_QUERY.toLowerCase());

		assertEquals(once, ServiceProductSolrQueryAdjuster.excludeServiceProducts(once));
	}

	@Test
	public void shouldDetectTheAliasedProductForm()
	{
		final String query = "SELECT {p:PK} FROM {Product AS p} WHERE {p:code} LIKE 'A%'";

		assertEquals(query + ServiceProductSolrQueryAdjuster.FULL_QUERY_CLAUSE,
				ServiceProductSolrQueryAdjuster.excludeServiceProducts(query));
	}

	@Test
	public void shouldDetectProductRegardlessOfCaseAndSpacing()
	{
		final String query = "select {pk} from { product }";

		assertEquals(query + ServiceProductSolrQueryAdjuster.FULL_QUERY_CLAUSE,
				ServiceProductSolrQueryAdjuster.excludeServiceProducts(query));
	}

	@Test
	public void shouldLeaveAQueryOnAnotherTypeUnchanged()
	{
		final String orders = "SELECT {PK} FROM {Order} WHERE {code} = 'x'";
		final String references = "SELECT {PK} FROM {ProductReference}";
		final String variants = "SELECT {PK} FROM {GenericVariantProduct}";

		assertSame(orders, ServiceProductSolrQueryAdjuster.excludeServiceProducts(orders));
		assertSame(references, ServiceProductSolrQueryAdjuster.excludeServiceProducts(references));
		assertSame(variants, ServiceProductSolrQueryAdjuster.excludeServiceProducts(variants));
	}

	@Test
	public void shouldLeaveAQueryThatAlreadyMentionsServiceProductUnchangedInAnyCase()
	{
		final String excluded = "SELECT {PK} FROM {Product} WHERE {PK} NOT IN ({{ SELECT {PK} FROM {serviceproduct} }})";

		assertSame(excluded, ServiceProductSolrQueryAdjuster.excludeServiceProducts(excluded));
	}

	@Test
	public void shouldBeIdempotentForTheFullQuery()
	{
		final String once = ServiceProductSolrQueryAdjuster.excludeServiceProducts(FULL_QUERY);

		assertEquals(once, ServiceProductSolrQueryAdjuster.excludeServiceProducts(once));
	}

	@Test
	public void shouldBeIdempotentForTheUpdateQuery()
	{
		final String once = ServiceProductSolrQueryAdjuster.excludeServiceProducts(UPDATE_QUERY);

		assertEquals(once, ServiceProductSolrQueryAdjuster.excludeServiceProducts(once));
	}
}
