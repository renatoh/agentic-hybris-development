/*
 * Copyright (c) 2024 SAP SE or an SAP affiliate company. All rights reserved.
 */
package de.hybris.platform.assistedservicestorefront.security.impl;

import de.hybris.platform.assistedserviceservices.AssistedServiceService;
import de.hybris.platform.assistedservicestorefront.security.AssistedServiceAgentAuthoritiesManager;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.collections.CollectionUtils;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;


/**
 * Default implementation for {@link AssistedServiceAgentAuthoritiesManager}.
 */
public class DefaultAssistedServiceAgentAuthoritiesManager implements AssistedServiceAgentAuthoritiesManager
{
	private UserDetailsService userDetailsService;

	private AssistedServiceService assistedServiceService;

	@Override
	public void addCustomerAuthoritiesToAgent(final String customerId)
	{
		final Set<GrantedAuthority> agentAuthorities = addAuthoritiesToSession();
		final Collection<? extends GrantedAuthority> customerAuthorities = getUserDetailsService().loadUserByUsername(customerId)
				.getAuthorities();
		if (CollectionUtils.isNotEmpty(customerAuthorities) && agentAuthorities.addAll(customerAuthorities))
		{
			updateAuthentication(agentAuthorities);
		}
	}

	@Override
	public void restoreInitialAuthorities()
	{
		final Collection<? extends GrantedAuthority> authorities = getAssistedServiceService().getAsmSession()
				.getInitialAgentAuthorities();
		updateAuthentication(authorities);
	}

	/**
	 * Add current agent authorities to the ASM session.
	 *
	 * @return The current agent authorities.
	 */
	protected Set<GrantedAuthority> addAuthoritiesToSession()
	{
		final SecurityContext context = SecurityContextHolder.getContext();
		final Collection<? extends GrantedAuthority> authorities = context.getAuthentication().getAuthorities();
		if(getAssistedServiceService().getAsmSession() != null)
		{
			getAssistedServiceService().getAsmSession().setInitialAgentAuthorities(authorities);
		}
		return new HashSet<>(authorities);
	}

	/**
	 * Update the agent authentication token with new authorities.
	 *
	 * @param authorities
	 *           The new list of authorities. Be aware that existent authorities will be removed.
	 */
	protected void updateAuthentication(final Collection<? extends GrantedAuthority> authorities)
	{
		final SecurityContext context = SecurityContextHolder.getContext();

		// BAD-1938 - cause we don't warp the authority after re-setting of customer's email
		// AccountPageController.updateEmail for details
		// that a case only if logged asagent changing the email
		if (context.getAuthentication() instanceof AssistedServiceAuthenticationToken)
		{
			final AssistedServiceAuthenticationToken currentAuth = (AssistedServiceAuthenticationToken) context.getAuthentication();
			final AssistedServiceAgentPrincipal principal = (AssistedServiceAgentPrincipal) currentAuth.getPrincipal();
			final AssistedServiceAuthenticationToken updatedAuth = new AssistedServiceAuthenticationToken(principal, authorities);
			updatedAuth.setDetails(currentAuth.getDetails());
			updatedAuth.setEmulating(currentAuth.isEmulating());
			context.setAuthentication(updatedAuth);
		}
	}

	protected UserDetailsService getUserDetailsService()
	{
		return userDetailsService;
	}

	public void setUserDetailsService(final UserDetailsService userDetailsService)
	{
		this.userDetailsService = userDetailsService;
	}

	protected AssistedServiceService getAssistedServiceService()
	{
		return assistedServiceService;
	}

	public void setAssistedServiceService(final AssistedServiceService assistedServiceService)
	{
		this.assistedServiceService = assistedServiceService;
	}

}
