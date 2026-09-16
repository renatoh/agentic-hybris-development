package com.custom.competitorpricing.service;

/**
 * Thrown when competitor pricing cannot be retrieved from the external system.
 * <p>
 * NET-8939 &sect;5.2: a failure must never be swallowed into an empty or partial list.
 */
public class CompetitorPricingException extends RuntimeException
{
	private static final long serialVersionUID = 1L;

	public CompetitorPricingException(final String message)
	{
		super(message);
	}

	public CompetitorPricingException(final String message, final Throwable cause)
	{
		super(message, cause);
	}
}
