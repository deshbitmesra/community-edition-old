/*
 * Copyright (C) 2005-2008 Alfresco Software Limited.
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
 * FLOSS exception.  You should have recieved a copy of the text describing
 * the FLOSS exception, and it is also available here:
 * http://www.alfresco.com/legal/licensing"
 */
package org.alfresco.repo.cmis.ws;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.ws.Holder;

import org.alfresco.cmis.CMISChangeEvent;
import org.alfresco.cmis.CMISChangeLog;
import org.alfresco.cmis.CMISChangeType;
import org.alfresco.cmis.CMISDataTypeEnum;
import org.alfresco.cmis.CMISDictionaryModel;
import org.alfresco.cmis.CMISQueryOptions;
import org.alfresco.cmis.CMISResultSet;
import org.alfresco.cmis.CMISResultSetColumn;
import org.alfresco.cmis.CMISResultSetRow;
import org.alfresco.repo.cmis.PropertyFilter;
import org.alfresco.repo.cmis.ws.utils.AlfrescoObjectType;

/**
 * Port for Discovery service.
 * 
 * @author Dmitry Lazurkin
 * @author Dmitry Velichkevich
 */
@javax.jws.WebService(name = "DiscoveryServicePort", serviceName = "DiscoveryService", portName = "DiscoveryServicePort", targetNamespace = "http://docs.oasis-open.org/ns/cmis/ws/200908/", endpointInterface = "org.alfresco.repo.cmis.ws.DiscoveryServicePort")
public class DMDiscoveryServicePort extends DMAbstractServicePort implements DiscoveryServicePort
{
    private static Map<CMISChangeType, EnumTypeOfChanges> changesTypeMapping = new HashMap<CMISChangeType, EnumTypeOfChanges>();
    static
    {
        changesTypeMapping.put(CMISChangeType.CREATED, EnumTypeOfChanges.CREATED);
        changesTypeMapping.put(CMISChangeType.UPDATED, EnumTypeOfChanges.UPDATED);
        changesTypeMapping.put(CMISChangeType.SECURITY, EnumTypeOfChanges.SECURITY);
        changesTypeMapping.put(CMISChangeType.DELETED, EnumTypeOfChanges.DELETED);
    }

    /**
     * Queries the repository for queryable object based on properties or an optional full-text string. Relationship objects are not queryable. Content-streams are not returned as
     * part of query
     * 
     * @param parameters query parameters
     * @throws CmisException (with following {@link EnumServiceException} : INVALID_ARGUMENT, OBJECT_NOT_FOUND, NOT_SUPPORTED, PERMISSION_DENIED, RUNTIME)
     */
    public QueryResponse query(Query parameters) throws CmisException
    {
        checkRepositoryId(parameters.getRepositoryId());

        // TODO: includeRelationships, includeRenditions
        CMISQueryOptions options = new CMISQueryOptions(parameters.getStatement(), cmisService.getDefaultRootStoreRef());

        if (parameters.getSkipCount() != null && parameters.getSkipCount().getValue() != null)
        {
            options.setSkipCount(parameters.getSkipCount().getValue().intValue());
        }

        if (parameters.getMaxItems() != null && parameters.getMaxItems().getValue() != null)
        {
            options.setMaxItems(parameters.getMaxItems().getValue().intValue());
        }
        boolean includeAllowableActions = (null != parameters.getIncludeAllowableActions()) ? (parameters.getIncludeAllowableActions().getValue()) : (false);
        String renditionFilter = (null != parameters.getRenditionFilter()) ? (parameters.getRenditionFilter().getValue()) : null;

        // execute query
        // TODO: If the select clause includes properties from more than a single type reference, then the repository SHOULD throw an exception if includeRelationships or
        // includeAllowableActions is specified as true.
        CMISResultSet resultSet = cmisQueryService.query(options);
        CMISResultSetColumn[] columns = resultSet.getMetaData().getColumns();

        // build query response
        QueryResponse response = new QueryResponse();
        response.setObjects(new CmisObjectListType());

        // for each row...
        for (CMISResultSetRow row : resultSet)
        {
            CmisPropertiesType properties = new CmisPropertiesType();
            Map<String, Serializable> values = row.getValues();

            // for each column...
            for (CMISResultSetColumn column : columns)
            {
                CmisProperty property = propertiesUtil.createProperty(column.getName(), column.getCMISDataType(), values.get(column.getName()));
                if (property != null)
                {
                    properties.getProperty().add(property);
                }
            }

            CmisObjectType object = new CmisObjectType();
            object.setProperties(properties);
            Object identifier = cmisObjectsUtils.getIdentifierInstance((String) values.get(CMISDictionaryModel.PROP_OBJECT_ID), AlfrescoObjectType.DOCUMENT_OR_FOLDER_OBJECT);
            if (includeAllowableActions)
            {
                object.setAllowableActions(determineObjectAllowableActions(identifier));
            }
            if (renditionFilter != null)
            {
                List<CmisRenditionType> renditions = getRenditions(identifier, renditionFilter);
                if (renditions != null && !renditions.isEmpty())
                {
                    object.getRendition().addAll(renditions);
                }
            }
            response.getObjects().getObjects().add(object);
        }
        // TODO: response.getObjects().setNumItems(value);
        response.getObjects().setHasMoreItems(resultSet.hasMore());
        return response;
    }

    /**
     * Gets a list of content changes. Targeted for search crawlers or other applications that need to efficiently understand what has changed in the repository. Note: The content
     * stream is NOT returned for any change event.
     * 
     * @param repositoryId {@link String} value that determines Id of the necessary Repository
     * @param changeLogToken generic {@link Holder} class instance with {@link String} type parameter that determines last Change Log Token
     * @param includeProperties {@link Boolean} instance value that specifies whether all allowed by filter properties should be returned for Change Type equal to 'UPDATED' or
     *        Object Id property only
     * @param filter {@link String} value for filtering properties for Change Entry with Change Type equal to 'UPDATED'
     * @param includePolicyIds {@link Boolean} instance value that determines whether Policy Ids must be returned
     * @param includeACL {@link Boolean} instance value that determines whether ACLs must be returned
     * @param maxItems {@link BigInteger} instance value that determines required amount of Change Log Entries
     * @param extension {@link CmisException} instance of unknown assignment
     * @param objects generic {@link Holder} instance with {@link CmisObjectListType} type parameter for storing results of service execution
     * @throws CmisException with next allowable {@link EnumServiceException} enum attribute of exception type values: CONSTRAINT, FILTER_NOT_VALID, INVALID_ARGUMENT,
     *         NOT_SUPPORTED, OBJECT_NOT_FOUND, PERMISSION_DENIED, RUNTIME
     */
    public void getContentChanges(String repositoryId, Holder<String> changeLogToken, Boolean includeProperties, String filter, Boolean includePolicyIds, Boolean includeACL,
            BigInteger maxItems, CmisExtensionType extension, Holder<CmisObjectListType> objects) throws CmisException
    {
        // TODO: includePolicyIds
        checkRepositoryId(repositoryId);
        String changeToken = (null != changeLogToken) ? (changeLogToken.value) : (null);
        Integer maxAmount = (null != maxItems) ? (maxItems.intValue()) : (null);
        boolean propertiesRequsted = (null != includeProperties) ? (includeProperties.booleanValue()) : (false);
        if (propertiesRequsted)
        {
            if ((null != filter) && !"".equals(filter) && !PropertyFilter.MATCH_ALL_FILTER.equals(filter) && !filter.contains(CMISDictionaryModel.PROP_OBJECT_ID))
            {
                filter = CMISDictionaryModel.PROP_OBJECT_ID + PropertyFilter.PROPERTY_NAME_TOKENS_DELIMETER + filter;
            }
        }
        else
        {
            filter = CMISDictionaryModel.PROP_OBJECT_ID;
        }

        if (changeToken != null)
        {
            try
            {
                Long.parseLong(changeToken);
            }
            catch (Exception e)
            {
                throw cmisObjectsUtils.createCmisException("Invalid changeLogToken was specified", EnumServiceException.INVALID_ARGUMENT);
            }
        }

        CMISChangeLog changeLog = null;
        try
        {
            changeLog = cmisChangeLogService.getChangeLogEvents(changeToken, maxAmount);
        }
        catch (Exception e)
        {
            throw cmisObjectsUtils.createCmisException(e.getMessage(), EnumServiceException.STORAGE);
        }
        if (null == objects.value)
        {
            objects.value = new CmisObjectListType();
        }
        if ((null == changeLog) || (null == changeLog.getChangeEvents()) || changeLog.getChangeEvents().isEmpty())
        {
            objects.value.setHasMoreItems(false);
            objects.value.setNumItems(BigInteger.valueOf(0));
        }
        else
        {
            formatObjectsResponse(filter, propertiesRequsted, includeACL, changeLog, objects.value.getObjects());
            objects.value.setHasMoreItems(changeLog.hasMoreItems());
            objects.value.setNumItems(BigInteger.valueOf(changeLog.getChangeEvents().size()));
            changeLogToken.value = changeLog.getNextChangeToken();
        }
    }

    /**
     * This method formats response for Get Content Changes service
     * 
     * @param filter {@link String} value that determines user specified properties filter
     * @param propertiesRequsted {@link Boolean} value that determines whether properties another than Object Id should be returned (according to specified properties filter)
     * @param changeLog {@link CMISChangeLog} instance that represents descriptor for some Change Log Token
     * @param result {@link List}&lt;{@link CmisObjectType}&gt; collection instance for storing Change Event entries from Change Log descriptor
     * @throws CmisException
     */
    private void formatObjectsResponse(String filter, boolean propertiesRequsted, boolean includeAce, CMISChangeLog changeLog, List<CmisObjectType> result) throws CmisException
    {
        for (CMISChangeEvent event : changeLog.getChangeEvents())
        {
            CmisObjectType object = new CmisObjectType();
            CmisPropertiesType propertiesType = new CmisPropertiesType();
            object.setProperties(propertiesType);
            propertiesType.getProperty().add(propertiesUtil.createProperty(CMISDictionaryModel.PROP_OBJECT_ID, CMISDataTypeEnum.ID, event.getNode()));
            if (nodeService.exists(event.getNode()) && includeAce)
            {
                appendWithAce(event.getNode(), object);
            }            
            CmisChangeEventType changeInfo = new CmisChangeEventType();
            XMLGregorianCalendar modificationDate = propertiesUtil.convert(event.getChangeTime());
            changeInfo.setChangeType(changesTypeMapping.get(event.getChangeType()));
            changeInfo.setChangeTime(modificationDate);
            object.setChangeEventInfo(changeInfo);
            result.add(object);
        }
    }

}
