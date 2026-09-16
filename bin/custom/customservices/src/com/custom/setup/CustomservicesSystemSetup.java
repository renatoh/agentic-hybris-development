package com.custom.setup;

import de.hybris.platform.commerceservices.setup.AbstractSystemSetup;
import de.hybris.platform.core.initialization.SystemSetup;
import de.hybris.platform.core.initialization.SystemSetup.Process;
import de.hybris.platform.core.initialization.SystemSetup.Type;
import de.hybris.platform.core.initialization.SystemSetupContext;
import de.hybris.platform.core.initialization.SystemSetupParameter;
import de.hybris.platform.core.initialization.SystemSetupParameterMethod;

import java.util.Collections;
import java.util.List;

import com.custom.constants.CustomservicesConstants;


/**
 * Hooks into the system's initialization and update processes.
 * <p>
 * NET-8939 &sect;5.3: the {@code CompetitorPricingImportCronJob} and its {@code Trigger} are
 * environment-active project data, not reference data, so they are imported here rather than
 * loaded via essential data.
 * <p>
 * NET-8940: the product-compare CMS page/template/slot wiring lives as ImpEx in
 * {@code customstorefront}'s own {@code resources/impex} (it is that extension's presentation
 * content), but {@code customstorefront} is web-module-only and has no core module of its own to
 * host a {@code @SystemSetup} class - a class annotated {@code @SystemSetup} must live in an
 * extension with a coremodule to be picked up by the platform's initialize/update process.
 * {@code customservices} is the nearest coremodule extension in this feature's own dependency
 * chain, so the import is registered here instead, against the same extension-name-prefixed
 * classpath resource path used everywhere else in this project (extension resource folders are
 * on the shared platform classpath regardless of which extension's {@code createProjectData}
 * triggers the import).
 */
@SystemSetup(extension = CustomservicesConstants.EXTENSIONNAME)
public class CustomservicesSystemSetup extends AbstractSystemSetup
{
	protected static final String COMPETITOR_PRICING_CRONJOB_IMPEX = "/customservices/impex/customservices-competitorpricing-cronjob.impex";
	protected static final String PRODUCT_COMPARISON_CMS_IMPEX = "/customstorefront/impex/customstorefront-productcomparison-cms.impex";

	@Override
	@SystemSetupParameterMethod
	public List<SystemSetupParameter> getInitializationOptions()
	{
		return Collections.emptyList();
	}

	@SystemSetup(type = Type.PROJECT, process = Process.ALL)
	public void createProjectData(final SystemSetupContext context)
	{
		importImpexFile(context, COMPETITOR_PRICING_CRONJOB_IMPEX);
		importImpexFile(context, PRODUCT_COMPARISON_CMS_IMPEX);
	}
}
