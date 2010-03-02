/*
 * Copyright (C) 2005-2010 Alfresco Software Limited.
 *
 * This file is part of Alfresco
 *
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 */
package org.alfresco.web.portlet;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import org.springframework.extensions.surf.RequestContext;
import org.springframework.extensions.surf.WebFrameworkServiceRegistry;
import org.springframework.extensions.surf.exception.RequestContextException;
import org.springframework.extensions.surf.util.URLDecoder;

/**
 * Performs lazy creation of dashboard pages when they are requested without requiring redirects, thus making them
 * addressable from a portlet.
 * 
 * @author dward
 */
public class LazyDashboardFilter implements Filter
{
    private static final Pattern PATTERN_DASHBOARD_PATH = Pattern.compile("/user/([^/]*)/dashboard");
    
    private ServletContext context;
    
    
    /*
     * (non-Javadoc)
     * @see javax.servlet.Filter#doFilter(javax.servlet.ServletRequest, javax.servlet.ServletResponse,
     * javax.servlet.FilterChain)
     */
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException,
            ServletException
    {
        HttpServletRequest httpServletRequest = (HttpServletRequest) request;
        
        // If this is a request for the current user's dashboard page, create it if it doesn't exist
        String pathInfo = httpServletRequest.getPathInfo();
        Matcher matcher;
        if (pathInfo != null && (matcher = PATTERN_DASHBOARD_PATH.matcher(pathInfo)).matches())
        {
            WebFrameworkServiceRegistry serviceRegistry = WebFrameworkServiceRegistry.getInstance(this.context);
            // Get hold of the context
            RequestContext context;
            try
            {
                context = serviceRegistry.getRequestContextFactory().newInstance(request);
            }
            catch (RequestContextException e)
            {
                throw new ServletException(e);
            }
            
            String userid = context.getUserId();
            
            // test user dashboard page exists?
            if (userid != null && userid.equals(URLDecoder.decode(matcher.group(1))))
            {
                if (serviceRegistry.getModelObjectService().getPage("user/" + userid + "/dashboard") == null)
                {
                    // no site found! create initial dashboard for this user...
                    Map<String, String> tokens = new HashMap<String, String>();
                    tokens.put("userid", userid);
                    serviceRegistry.getPresetsManager().constructPreset("user-dashboard", tokens);
                }
            }
        }
        
        chain.doFilter(request, response);
    }

    /*
     * (non-Javadoc)
     * @see javax.servlet.Filter#init(javax.servlet.FilterConfig)
     */
    public void init(FilterConfig config) throws ServletException
    {
        // get reference to our ServletContext
        this.context = config.getServletContext();
    }

    /*
     * (non-Javadoc)
     * @see javax.servlet.Filter#destroy()
     */
    public void destroy()
    {
    }
}