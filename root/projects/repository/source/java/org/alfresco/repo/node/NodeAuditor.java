/*
 * Copyright (C) 2005-2010 Alfresco Software Limited.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.

 * As a special exception to the terms and conditions of version 2.0 of 
 * the GPL, you may redistribute this Program in connection with Free/Libre 
 * and Open Source Software ("FLOSS") applications as described in Alfresco's 
 * FLOSS exception.  You should have received a copy of the text describing 
 * the FLOSS exception, and it is also available here: 
 * http://www.alfresco.com/legal/licensing"
 */
package org.alfresco.repo.node;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.alfresco.repo.audit.AuditComponent;
import org.alfresco.repo.audit.AuditMethodInterceptor;
import org.alfresco.repo.node.NodeServicePolicies.BeforeDeleteNodePolicy;
import org.alfresco.repo.policy.JavaBehaviour;
import org.alfresco.repo.policy.PolicyComponent;
import org.alfresco.service.cmr.repository.NodeRef;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.extensions.surf.util.PropertyCheck;

/**
 * A listener that ensures that an event is audited for every deleted node in a tree of nodes, not just the top one
 * captured by {@link AuditMethodInterceptor}!
 * 
 * @author dward
 */
public class NodeAuditor implements InitializingBean, NodeServicePolicies.BeforeDeleteNodePolicy
{
    /** Logger */
    private static Log logger = LogFactory.getLog(NodeAuditor.class);

    private static final String ROOT_PATH = "/alfresco-node";
    private static final String BEFORE_DELETE_NODE_PATH = ROOT_PATH + "/beforeDeleteNode";
    private static final String NODE_PATH_COMPONENT = "node";

    private PolicyComponent policyComponent;
    private AuditComponent auditComponent;

    public NodeAuditor()
    {
    }

    /**
     * Set the component used to bind to behaviour callbacks
     */
    public void setPolicyComponent(PolicyComponent policyComponent)
    {
        this.policyComponent = policyComponent;
    }

    /**
     * The component to create audit events
     */
    public void setAuditComponent(AuditComponent auditComponent)
    {
        this.auditComponent = auditComponent;
    }

    /**
     * Checks that all necessary properties have been set and binds with the policy component.
     */
    public void afterPropertiesSet()
    {
        PropertyCheck.mandatory(this, "policyComponent", policyComponent);
        PropertyCheck.mandatory(this, "auditComponent", auditComponent);
        policyComponent.bindClassBehaviour(BeforeDeleteNodePolicy.QNAME, this, new JavaBehaviour(this,
                "beforeDeleteNode"));
    }

    public void beforeDeleteNode(NodeRef nodeRef)
    {
        // Only continue if there is something listening for our events (note this may change depending on audit
        // subsystem configuration)
        if (!auditComponent.isSourcePathMapped(BEFORE_DELETE_NODE_PATH))
        {
            return;
        }
        // Deleted nodes will not be available at the end of the transaction. The data needs to
        // be extracted now and the audit entry needs to be created now.
        Map<String, Serializable> auditMap = new HashMap<String, Serializable>(13);
        auditMap.put(NODE_PATH_COMPONENT, nodeRef);
        auditMap = auditComponent.recordAuditValues(BEFORE_DELETE_NODE_PATH, auditMap);
        if (logger.isDebugEnabled())
        {
            logger.debug("NodeAuditor: Audited node deletion: \n" + auditMap);
        }
    }
}