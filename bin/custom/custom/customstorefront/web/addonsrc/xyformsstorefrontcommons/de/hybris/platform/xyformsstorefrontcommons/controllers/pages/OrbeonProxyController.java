/*
 * Copyright (c) 2022 SAP SE or an SAP affiliate company. All rights reserved.
 */
package de.hybris.platform.xyformsstorefrontcommons.controllers.pages;

import de.hybris.platform.xyformsfacades.proxy.ProxyFacade;
import de.hybris.platform.xyformsservices.proxy.ProxyException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Controller
@RequestMapping(value = "/orbeon")
public class OrbeonProxyController {

    @Resource
    private ProxyFacade proxyFacade;

    @RequestMapping(value = "**")
    public void proxyRequest(final HttpServletRequest request, final HttpServletResponse response) throws ProxyException {
        proxyFacade.proxy(request, response);
    }
}
