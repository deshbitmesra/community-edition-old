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
package org.alfresco.opencmis.dictionary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.opencmis.CMISUtils;
import org.alfresco.opencmis.mapping.CMISMapping;
import org.alfresco.service.cmr.dictionary.AssociationDefinition;
import org.alfresco.service.cmr.dictionary.ClassDefinition;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.util.ISO9075;
import org.apache.chemistry.opencmis.commons.definitions.PropertyDefinition;
import org.apache.chemistry.opencmis.commons.enums.BaseTypeId;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.RelationshipTypeDefinitionImpl;

public class RelationshipTypeDefintionWrapper extends AbstractTypeDefinitionWrapper
{
    private static final long serialVersionUID = 1L;

    private RelationshipTypeDefinitionImpl typeDef;
    private RelationshipTypeDefinitionImpl typeDefInclProperties;

    public RelationshipTypeDefintionWrapper(CMISMapping cmisMapping, PropertyAccessorMapping accessorMapping,
            PropertyLuceneBuilderMapping luceneBuilderMapping, String typeId, DictionaryService dictionaryService, ClassDefinition cmisClassDef)
    {
        alfrescoName = cmisClassDef.getName();
        alfrescoClass = cmisMapping.getAlfrescoClass(alfrescoName);

        typeDef = new RelationshipTypeDefinitionImpl();

        typeDef.setBaseTypeId(BaseTypeId.CMIS_RELATIONSHIP);
        typeDef.setId(typeId);
        typeDef.setLocalName(alfrescoName.getLocalName());
        typeDef.setLocalNamespace(alfrescoName.getNamespaceURI());

        if (BaseTypeId.CMIS_RELATIONSHIP.value().equals(typeId))
        {
            typeDef.setQueryName(ISO9075.encodeSQL(typeId));
            typeDef.setParentTypeId(null);
            typeDef.setIsCreatable(false);
        } else
        {
            typeDef.setQueryName(ISO9075.encodeSQL(cmisMapping.buildPrefixEncodedString(alfrescoName)));
            typeDef.setParentTypeId(BaseTypeId.CMIS_RELATIONSHIP.value());
            typeDef.setIsCreatable(true);
        }
        
        typeDef.setDisplayName(typeId);
        typeDef.setDescription(typeDef.getDisplayName());

        typeDef.setIsQueryable(false);
        typeDef.setIsFulltextIndexed(false);
        typeDef.setIsControllablePolicy(false);
        typeDef.setIsControllableAcl(false);
        typeDef.setIsIncludedInSupertypeQuery(true);
        typeDef.setIsFileable(false);

        typeDefInclProperties = CMISUtils.copy(typeDef);
        setTypeDefinition(typeDef, typeDefInclProperties);

        createOwningPropertyDefinitions(cmisMapping, accessorMapping, luceneBuilderMapping, dictionaryService, cmisClassDef);
        createActionEvaluators(accessorMapping, BaseTypeId.CMIS_RELATIONSHIP);
    }

    public RelationshipTypeDefintionWrapper(CMISMapping cmisMapping, PropertyAccessorMapping accessorMapping,
            PropertyLuceneBuilderMapping luceneBuilderMapping, String typeId, DictionaryService dictionaryService, AssociationDefinition cmisAssocDef)
    {
        alfrescoName = cmisAssocDef.getName();
        alfrescoClass = cmisMapping.getAlfrescoClass(alfrescoName);

        typeDef = new RelationshipTypeDefinitionImpl();

        typeDef.setBaseTypeId(BaseTypeId.CMIS_RELATIONSHIP);
        typeDef.setId(typeId);
        typeDef.setLocalName(alfrescoName.getLocalName());
        typeDef.setLocalNamespace(alfrescoName.getNamespaceURI());

        typeDef.setQueryName(cmisMapping.buildPrefixEncodedString(alfrescoName));
        typeDef.setParentTypeId(BaseTypeId.CMIS_RELATIONSHIP.value());

        typeDef.setDisplayName(typeId);
        typeDef.setDescription(typeDef.getDisplayName());

        typeDef.setIsCreatable(true);
        typeDef.setIsQueryable(false);
        typeDef.setIsFulltextIndexed(false);
        typeDef.setIsControllablePolicy(false);
        typeDef.setIsControllableAcl(false);
        typeDef.setIsIncludedInSupertypeQuery(true);
        typeDef.setIsFileable(false);

        ArrayList<String> both = new ArrayList<String>(2);
        both.add(BaseTypeId.CMIS_DOCUMENT.value());
        both.add(BaseTypeId.CMIS_FOLDER.value());
        
        String sourceTypeId = cmisMapping.getCmisTypeId(cmisMapping
                .getCmisType(cmisAssocDef.getSourceClass().getName()));
        if (sourceTypeId != null)
        {
            typeDef.setAllowedSourceTypes(Collections.singletonList(sourceTypeId));
        }
        else
        {
            typeDef.setAllowedSourceTypes(both);
        }

        String targetTypeId = cmisMapping.getCmisTypeId(cmisMapping
                .getCmisType(cmisAssocDef.getTargetClass().getName()));
        if (targetTypeId != null)
        {
            typeDef.setAllowedTargetTypes(Collections.singletonList(targetTypeId));
        }
        else
        {
            typeDef.setAllowedTargetTypes(both);
        }

        typeDefInclProperties = CMISUtils.copy(typeDef);
        setTypeDefinition(typeDef, typeDefInclProperties);
        createActionEvaluators(accessorMapping, BaseTypeId.CMIS_RELATIONSHIP);
    }

    @Override
    public List<TypeDefinitionWrapper> connectParentAndSubTypes(CMISMapping cmisMapping, CMISDictionaryRegistry registry,
            DictionaryService dictionaryService)
    {
    	String parentTypeId = typeDef.getParentTypeId();

        // find parent
        if (parentTypeId != null)
        {
            parent = registry.getTypeDefByTypeId(parentTypeId);
            if(registry.getTenant() != null && parent != null && registry.getTypeDefByTypeId(parentTypeId, false) == null)
            {
            	// this is a tenant registry and the parent is not defined locally so add this type as a child of it
            	registry.addChild(parent.getTypeId(), this);
            }
        }
        else
        {
            if (!isBaseType())
            {
                throw new AlfrescoRuntimeException("Type " + typeDef.getId() + " has no parent!");
            }

            parent = null;
        }

        // find children
        List<TypeDefinitionWrapper> children = new LinkedList<TypeDefinitionWrapper>();
        if (isBaseType())
        {
            for (TypeDefinitionWrapper child : registry.getAssocDefs())
            {
                children.add(child);
            }
        }

        return children;
//        registry.setChildren(typeDef.getId(), children);
    }

    public void resolveInheritance(CMISMapping cmisMapping, CMISDictionaryRegistry registry,
            DictionaryService dictionaryService)
    {
        PropertyDefinition<?> propertyDefintion;

        if (parent != null)
        {
            for (PropertyDefinitionWrapper propDef : parent.getProperties())
            {
                org.alfresco.service.cmr.dictionary.PropertyDefinition alfrescoPropDef = dictionaryService.getProperty(
                        propDef.getOwningType().getAlfrescoName(), propDef.getAlfrescoName());

                propertyDefintion = createPropertyDefinition(cmisMapping, propDef.getPropertyId(),
                        alfrescoPropDef.getName(), dictionaryService, alfrescoPropDef, true);

                if (propertyDefintion != null)
                {
                    registerProperty(new BasePropertyDefintionWrapper(propertyDefintion, alfrescoPropDef.getName(),
                            propDef.getOwningType(), propDef.getPropertyAccessor(), propDef.getPropertyLuceneBuilder()));
                }
            }
        }

        List<TypeDefinitionWrapper> children = registry.getChildren(typeDef.getId());
        for (TypeDefinitionWrapper child : children)
        {
            if (child instanceof AbstractTypeDefinitionWrapper)
            {
                ((AbstractTypeDefinitionWrapper) child).resolveInheritance(cmisMapping, registry, dictionaryService);
            }
        }
    }
}
