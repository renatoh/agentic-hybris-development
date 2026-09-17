/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.savedforlater.interceptor;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.servicelayer.interceptor.InterceptorContext;
import de.hybris.platform.servicelayer.interceptor.InterceptorException;
import de.hybris.platform.servicelayer.model.ModelService;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.custom.model.SavedForLaterEntryModel;


/**
 * Covers {@link SavedForLaterEntryCustomerRemoveInterceptor#onRemove} (NET-8941 section 5.1a/8
 * item 3) - cascades removal of a customer's saved-for-later entries, since the relation is not
 * {@code partof} and so is not cleaned up by the platform's own cascade mechanism.
 */
@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class SavedForLaterEntryCustomerRemoveInterceptorTest
{
	@Mock
	private ModelService modelService;
	@Mock
	private CustomerModel customer;
	@Mock
	private InterceptorContext ctx;

	private SavedForLaterEntryCustomerRemoveInterceptor interceptor;

	@Before
	public void setUp()
	{
		interceptor = new SavedForLaterEntryCustomerRemoveInterceptor();
		interceptor.setModelService(modelService);
	}

	@Test
	public void shouldRemoveAllSavedEntriesWhenTheCustomerHasSome() throws InterceptorException
	{
		final SavedForLaterEntryModel entry1 = mock(SavedForLaterEntryModel.class);
		final SavedForLaterEntryModel entry2 = mock(SavedForLaterEntryModel.class);
		final List<SavedForLaterEntryModel> entries = List.of(entry1, entry2);
		given(customer.getSavedForLaterEntries()).willReturn(entries);

		interceptor.onRemove(customer, ctx);

		verify(modelService).removeAll(entries);
	}

	@Test
	public void shouldNotCallRemoveAllWhenTheCustomerHasNoSavedEntries() throws InterceptorException
	{
		given(customer.getSavedForLaterEntries()).willReturn(List.of());

		interceptor.onRemove(customer, ctx);

		verify(modelService, never()).removeAll(anyCollection());
	}

	@Test
	public void shouldNotCallRemoveAllWhenTheCustomerHasNullSavedEntries() throws InterceptorException
	{
		given(customer.getSavedForLaterEntries()).willReturn(null);

		interceptor.onRemove(customer, ctx);

		verify(modelService, never()).removeAll(anyCollection());
	}
}
