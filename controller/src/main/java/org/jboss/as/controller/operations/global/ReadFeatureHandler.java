/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.controller.operations.global;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADDRESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ANNOTATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ATTRIBUTES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CAPABILITY_REFERENCE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CHILDREN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEFAULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXCEPTIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXTENSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FEATURE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NILLABLE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STORAGE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE;
import static org.jboss.as.controller.operations.global.GlobalOperationAttributes.RECURSIVE;
import static org.jboss.as.controller.operations.global.GlobalOperationAttributes.RECURSIVE_DEPTH;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.UnauthorizedException;
import org.jboss.as.controller.access.Action.ActionEffect;
import org.jboss.as.controller.access.AuthorizationResult;
import org.jboss.as.controller.access.AuthorizationResult.Decision;
import org.jboss.as.controller.access.ResourceAuthorization;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.registry.AttributeAccess.Storage;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_FEATURE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.registry.AttributeAccess.Storage.CONFIGURATION;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.StringJoiner;
import org.jboss.as.controller.ObjectListAttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.capability.registry.CapabilityId;
import org.jboss.as.controller.capability.registry.CapabilityRegistration;
import org.jboss.as.controller.capability.registry.CapabilityScope;
import org.jboss.as.controller.capability.registry.ImmutableCapabilityRegistry;
import org.jboss.as.controller.descriptions.DescriptionProvider;
import org.jboss.as.controller.registry.AliasEntry;
import org.jboss.as.controller.registry.AliasStepHandler;
import org.jboss.as.controller.registry.AttributeAccess;

/**
 * {@link org.jboss.as.controller.OperationStepHandler} querying the complete type description of a given model node.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author Brian Stansberry (c) 2012 Red Hat Inc.
 */
public class ReadFeatureHandler extends GlobalOperationHandlers.AbstractMultiTargetHandler {

    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(READ_FEATURE_OPERATION, ControllerResolver.getResolver("global"))
            .setParameters(RECURSIVE, RECURSIVE_DEPTH)
            .setReadOnly()
            .setReplyValueType(ModelType.OBJECT)
            .build();

    final ImmutableCapabilityRegistry capabilityRegistry;

    //Placeholder for NoSuchResourceExceptions coming from proxies to remove the child in ReadResourceDescriptionAssemblyHandler
    private static final ModelNode PROXY_NO_SUCH_RESOURCE;

    static {
        //Create something non-used since we cannot
        ModelNode none = new ModelNode();
        none.get("no-such-resource").set("no$such$resource");
        none.protect();
        PROXY_NO_SUCH_RESOURCE = none;
    }

    public static OperationStepHandler getInstance(ImmutableCapabilityRegistry capabilityRegistry) {
        return new ReadFeatureHandler(capabilityRegistry);
    }

    private ReadFeatureHandler(final ImmutableCapabilityRegistry capabilityRegistry) {
        super(true);
        this.capabilityRegistry = capabilityRegistry;
    }

    ReadFeatureAccessControlContext getAccessControlContext() {
        return null;
    }

    @Override
    void doExecute(OperationContext context, ModelNode operation, FilteredData filteredData, boolean ignoreMissingResource) throws OperationFailedException {
        final PathAddress address = context.getCurrentAddress();
        ReadFeatureAccessControlContext accessControlContext = getAccessControlContext() == null ? new ReadFeatureAccessControlContext(address, null) : getAccessControlContext();
        doExecute(context, operation, accessControlContext);
    }

    void doExecute(OperationContext context, ModelNode operation, ReadFeatureAccessControlContext accessControlContext) throws OperationFailedException {
        if (accessControlContext.parentAddresses == null) {
            doExecuteInternal(context, operation, accessControlContext);
        } else {
            try {
                doExecuteInternal(context, operation, accessControlContext);
            } catch (Resource.NoSuchResourceException | UnauthorizedException nsre) {
                context.getResult().set(new ModelNode());
            }
        }
    }

    private void doExecuteInternal(final OperationContext context, final ModelNode operation, final ReadFeatureAccessControlContext accessControlContext) throws OperationFailedException {

        for (AttributeDefinition def : DEFINITION.getParameters()) {
            def.validateOperation(operation);
        }
        final String opName = operation.require(OP).asString();
        PathAddress opAddr = PathAddress.pathAddress(operation.get(OP_ADDR));
        // WFCORE-76
        final boolean recursive = GlobalOperationHandlers.getRecursive(context, operation);

        final ImmutableManagementResourceRegistration registry = getResourceRegistrationCheckForAlias(context, opAddr, accessControlContext);
        final Locale locale = GlobalOperationHandlers.getLocale(context, operation);
        final PathAddress pa = registry.getPathAddress();
        boolean hasHost = false;
        boolean hasProfile = false;
        if (pa.size() > 1) {
            List<PathElement> elements = new ArrayList<>();
            Iterator<PathElement> iter = pa.subAddress(1).iterator();
            switch (pa.getElement(0).getKey()) {
                case PROFILE:
                    for (String hostName : context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS).getChildrenNames(HOST)) {
                        elements.add(PathElement.pathElement(HOST, hostName));
                        while (iter.hasNext()) {
                            elements.add(iter.next());
                        }
                        hasHost = context.getRootResourceRegistration().getSubModel(PathAddress.pathAddress(elements)) != null;
                        if (hasHost) {
                            break;
                        }
                    }
                    break;
                case HOST:
                    for (String profileName : context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS).getChildrenNames(PROFILE)) {
                        elements.add(PathElement.pathElement(PROFILE, profileName));
                        while (iter.hasNext()) {
                            elements.add(iter.next());
                        }
                        hasProfile = context.getRootResourceRegistration().getSubModel(PathAddress.pathAddress(elements)) != null;
                        if (hasProfile) {
                            break;
                        }
                    }
                    break;
                default:
            }
        }
        if (hasProfile) {
            return;
        }
        final ModelNode feature = describeFeature(locale, registry, CapabilityScope.Factory.create(context.getProcessType(), pa), isProfileScope(context.getProcessType(), pa), hasHost);

        if (pa.getLastElement() != null && SUBSYSTEM.equals(pa.getLastElement().getKey())) {
            ModelNode extensionParam = new ModelNode();
            extensionParam.get(ModelDescriptionConstants.NAME).set(EXTENSION);
            extensionParam.get(DEFAULT).set(getExtension(context, pa.getLastElement().getValue()));
            feature.get(FEATURE).get("params").add(extensionParam);
        }
        final Map<PathElement, ModelNode> childResources = recursive ? new HashMap<>() : Collections.<PathElement, ModelNode>emptyMap();

        // We're going to add a bunch of steps that should immediately follow this one. We are going to add them
        // in reverse order of how they should execute, as that is the way adding a Stage.IMMEDIATE step works
        // Last to execute is the handler that assembles the overall response from the pieces created by all the other steps
        final ReadFeatureAssemblyHandler assemblyHandler = new ReadFeatureAssemblyHandler(feature, childResources, accessControlContext);
        context.addStep(assemblyHandler, OperationContext.Stage.MODEL, true);

        if (recursive) {
            final ModelNode children;
            if(!feature.get(FEATURE).get(CHILDREN).isDefined()) {
                children = feature.get(FEATURE).get(CHILDREN).setEmptyObject();
            } else {
                children = feature.get(FEATURE).get(CHILDREN);
            }
            for (final PathElement element : registry.getChildAddresses(PathAddress.EMPTY_ADDRESS)) {
                PathAddress relativeAddr = PathAddress.pathAddress(element);
                ImmutableManagementResourceRegistration childReg = registry.getSubModel(relativeAddr);

                boolean readChild = true;
                if (childReg.isRemote()) {
                    readChild = false;
                }
                if (childReg.isAlias()) {
                    readChild = false;
                }
                if (childReg.isRuntimeOnly()) {
                    readChild = false;
                }

                if (readChild) {
                    final ModelNode childNode = children.get(element.getKey());
                    childNode.get(FEATURE);
                    final ModelNode rrOp = operation.clone();
                    final PathAddress address;
                    try {
                        address = PathAddress.pathAddress(opAddr, element);
                    } catch (Exception e) {
                        continue;
                    }
                    rrOp.get(OP_ADDR).set(address.toModelNode());
                    // WFCORE-76
                    GlobalOperationHandlers.setNextRecursive(context, operation, rrOp);
                    final ModelNode rrRsp = new ModelNode();
                    childResources.put(element, rrRsp);

                    final OperationStepHandler handler = getRecursiveStepHandler(childReg, opName, accessControlContext, address);
                    context.addStep(rrRsp, rrOp, handler, OperationContext.Stage.MODEL, true);
                }
            }
        }

        context.completeStep(new OperationContext.RollbackHandler() {
            @Override
            public void handleRollback(OperationContext context, ModelNode operation) {

                if (!context.hasFailureDescription()) {
                    for (final ModelNode value : childResources.values()) {
                        if (value.hasDefined(FAILURE_DESCRIPTION)) {
                            context.getFailureDescription().set(value.get(FAILURE_DESCRIPTION));
                            break;
                        }
                    }
                }
            }
        });
    }

    private String getExtension(OperationContext context, String subsystem) {
        for (String extensionName : context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS).getChildrenNames(EXTENSION)) {
            Resource extension = context.readResourceFromRoot(PathAddress.pathAddress(EXTENSION, extensionName));
            if (extension.getChildrenNames(SUBSYSTEM).contains(subsystem)) {
                return extensionName;
            }
        }
        return null;
    }

    private OperationStepHandler getRecursiveStepHandler(ImmutableManagementResourceRegistration childReg, String opName, ReadFeatureAccessControlContext accessControlContext, PathAddress address) {
        OperationStepHandler overrideHandler = childReg.getOperationHandler(PathAddress.EMPTY_ADDRESS, opName);
        if (overrideHandler != null && (overrideHandler.getClass() == ReadFeatureHandler.class || overrideHandler.getClass() == AliasStepHandler.class)) {
            // not an override
            overrideHandler = null;
        }

        if (overrideHandler != null) {
            return new NestedReadFeatureHandler(capabilityRegistry, overrideHandler);
        }
        return new NestedReadFeatureHandler(capabilityRegistry, new ReadFeatureAccessControlContext(address, accessControlContext));
    }

    private ImmutableManagementResourceRegistration getResourceRegistrationCheckForAlias(OperationContext context, PathAddress opAddr, ReadFeatureAccessControlContext accessControlContext) {
        //The direct root registration is only needed if we are doing access-control=true
        final ImmutableManagementResourceRegistration root = context.getRootResourceRegistration();
        final ImmutableManagementResourceRegistration registry = root.getSubModel(opAddr);

        AliasEntry aliasEntry = registry.getAliasEntry();
        if (aliasEntry == null) {
            return registry;
        }
        //Get hold of the real registry if it was an alias
        PathAddress realAddress = aliasEntry.convertToTargetAddress(opAddr, AliasEntry.AliasContext.create(opAddr, context));
        assert !realAddress.equals(opAddr) : "Alias was not translated";

        return root.getSubModel(realAddress);
    }

    private ModelNode describeFeature(final Locale locale, final ImmutableManagementResourceRegistration registration,
            final CapabilityScope capabilityScope, boolean isProfile, boolean hasHost) {
        ModelNode result = new ModelNode();
        if (registration.isFeature()
                && !registration.isRuntimeOnly()
                && !registration.isAlias()) {
            PathAddress pa = registration.getPathAddress();
            ModelNode feature = result.get(FEATURE);
            feature.get(ModelDescriptionConstants.NAME).set(registration.getFeature());
            final DescriptionProvider addDescriptionProvider = registration.getOperationDescription(PathAddress.EMPTY_ADDRESS, ModelDescriptionConstants.ADD);
            final ModelNode requestProperties;
            if (addDescriptionProvider != null) {
                ModelNode annotation = feature.get(ANNOTATION);
                annotation.get(ModelDescriptionConstants.NAME).set(ModelDescriptionConstants.ADD);
                requestProperties = addDescriptionProvider.getModelDescription(locale).get(ModelDescriptionConstants.REQUEST_PROPERTIES);
                addOpParam(annotation, requestProperties);
            } else {
                requestProperties = new ModelNode().setEmptyList();
            }
            for (RuntimeCapability cap : registration.getCapabilities()) {
                String capabilityName = cap.getName();
                if (cap.isDynamicallyNamed()) {
                    capabilityName = capabilityName + ".$" + pa.getLastElement().getKey();
                }
                if (isProfile) {
                    capabilityName = "$profile." + capabilityName;
                }
                feature.get("provides").add(capabilityName);
            }
            addRequiredCapabilities(feature, requestProperties, capabilityScope, isProfile);
            addReferences(feature, registration, hasHost);
            addParams(feature, pa, requestProperties, hasHost);
            complexAttributeChildren(feature, registration);
        }
        return result;
    }

    private void complexAttributeChildren(final ModelNode parentFeature, final ImmutableManagementResourceRegistration registration) {
        for (AttributeAccess attAccess : registration.getAttributes(PathAddress.EMPTY_ADDRESS).values()) {
            if (CONFIGURATION == attAccess.getStorageType() && attAccess.getAccessType() == AttributeAccess.AccessType.READ_WRITE) {
                AttributeDefinition attDef = attAccess.getAttributeDefinition();
                switch (attDef.getType()) {
                    case LIST:
                        if (ObjectListAttributeDefinition.class.isAssignableFrom(attDef.getClass())) {
                            ObjectListAttributeDefinition objAttDef = (ObjectListAttributeDefinition) attDef;
                            //we need a non resource feature
                            ModelNode attFeature = new ModelNode();
                            String name = parentFeature.require(NAME).asString() + "_" + objAttDef.getName();
                            attFeature.get(NAME).set(name);
                            ModelNode annotation = attFeature.get(ANNOTATION);
                            annotation.get(ModelDescriptionConstants.NAME).set("list-add");
                            annotation.get("addr-params").set(parentFeature.require(ANNOTATION).require("addr-params"));
                            if (parentFeature.require(ANNOTATION).hasDefined("addr-params-mapping")) {
                                annotation.get("addr-params").set(parentFeature.require(ANNOTATION).require("addr-params-mapping"));
                            }
                            annotation.get("op-params").set(objAttDef.getValueType().getName());
                            annotation.get("op-params-mapping").set(objAttDef.getName());
                            ModelNode refs = attFeature.get("refs").setEmptyList();
                            ModelNode ref = new ModelNode();
                            ref.get(FEATURE).set(parentFeature.require(NAME));
                            refs.add(ref);
                            if (parentFeature.hasDefined("params")) {
                                ModelNode params = attFeature.get("params").setEmptyList();
                                for (ModelNode param : parentFeature.require("params").asList()) {
                                    if (param.hasDefined("feature-id") && param.get("feature-id").asBoolean()) {
                                        params.add(param);
                                    }
                                }
                            }
                            parentFeature.get(CHILDREN).get(name).set(attFeature);
                        }
                        break;
                    case OBJECT:
                        if (ObjectTypeAttributeDefinition.class.isAssignableFrom(attDef.getClass())) {
                            ObjectTypeAttributeDefinition objAttDef = (ObjectTypeAttributeDefinition) attDef;
                            //we need a non resource feature
                            ModelNode attFeature = new ModelNode();
                            String name = parentFeature.require(NAME).asString() + "_" + objAttDef.getName();
                            attFeature.get(NAME).set(name);
                            ModelNode annotation = attFeature.get(ANNOTATION);
                            annotation.get(ModelDescriptionConstants.NAME).set("write-attribute");
                            annotation.get("addr-params").set(parentFeature.require(ANNOTATION).require("addr-params"));
                            if (parentFeature.require(ANNOTATION).hasDefined("addr-params-mapping")) {
                                annotation.get("addr-params").set(parentFeature.require(ANNOTATION).require("addr-params-mapping"));
                            }
                            annotation.get("op-params").set(objAttDef.getName());
                            annotation.get("op-params-mapping").set(NAME);
                            ModelNode refs = attFeature.get("refs").setEmptyList();
                            ModelNode ref = new ModelNode();
                            ref.get(FEATURE).set(parentFeature.require(NAME));
                            refs.add(ref);
                            if (parentFeature.hasDefined("params")) {
                                ModelNode params = attFeature.get("params").setEmptyList();
                                for (ModelNode param : parentFeature.require("params").asList()) {
                                    if (param.hasDefined("feature-id") && param.get("feature-id").asBoolean()) {
                                        params.add(param);
                                    }
                                }
                            }
                            parentFeature.get(CHILDREN).get(name).set(attFeature);
                        }
                        break;
                    default:
                }
            }
        }
    }

    private void addParams(ModelNode feature, PathAddress address, ModelNode requestProperties, boolean hasHost) {
        ModelNode params = feature.get("params").setEmptyList();
        Set<String> paramNames = new HashSet<>();
        for (Property att : requestProperties.asPropertyList()) {
            ModelNode param = new ModelNode();
            param.get(ModelDescriptionConstants.NAME).set(att.getName());
            paramNames.add(att.getName());
            ModelNode attDescription = att.getValue();
            if (attDescription.hasDefined(NILLABLE) && attDescription.get(NILLABLE).asBoolean()) {
                param.get(NILLABLE).set(true);
            }
            if (attDescription.hasDefined(ModelDescriptionConstants.DEFAULT)) {
                param.get(ModelDescriptionConstants.DEFAULT).set(attDescription.get(ModelDescriptionConstants.DEFAULT));
            }
            params.add(param);
        }
        if (address == null || PathAddress.EMPTY_ADDRESS.equals(address)) {
            return;
        }
        final ModelNode annotationNode = feature.get("annotation");
        StringJoiner addressParams = new StringJoiner(",");
        StringJoiner addressParamsMappings = new StringJoiner(",");
        boolean keepMapping = false;
        if (hasHost) {
            String paramName;
            if (paramNames.contains(HOST)) {
                keepMapping = true;
                paramName = "addr_mapping_" + HOST;
            } else {
                paramName = HOST;
            }
            ModelNode param = new ModelNode();
            param.get(ModelDescriptionConstants.NAME).set(paramName);
            param.get(DEFAULT).set("PM_UNDEFINED");
            param.get("feature-id").set(true);
            params.add(param);
            addressParams.add(paramName);
            addressParamsMappings.add(HOST);
        }
        for (PathElement elt : address) {
            String paramName;
            if (paramNames.contains(elt.getKey())) {
                keepMapping = true;
                paramName = "addr_mapping_" + elt.getKey();
            } else {
                paramName = elt.getKey();
            }
            ModelNode param = new ModelNode();
            param.get(ModelDescriptionConstants.NAME).set(paramName);
            if (hasHost && PROFILE.equals(elt.getKey())) {
                param.get(DEFAULT).set("PM_UNDEFINED");
            }
            if (!elt.isWildcard()) {
                param.get(DEFAULT).set(elt.getValue());
            }
            param.get("feature-id").set(true);
            params.add(param);
            addressParams.add(paramName);
            addressParamsMappings.add(elt.getKey());
        }
        annotationNode.get("addr-params").set(addressParams.toString());
        if (keepMapping) {
            annotationNode.get("addr-params-mapping").set(addressParamsMappings.toString());
        }
    }

    private void addReferences(ModelNode feature, ImmutableManagementResourceRegistration registration, boolean hasHost) {
        PathAddress address = registration.getPathAddress();
        if (address == null || PathAddress.EMPTY_ADDRESS.equals(address)) {
            return;
        }
        ModelNode refs = feature.get("refs").setEmptyList();
        if (hasHost) {
            ModelNode ref = new ModelNode();
            ref.get(FEATURE).set(HOST);
            ref.get(NILLABLE).set(true);
            refs.add(ref);
        }
        if (registration.getParent() != null && registration.getParent().isFeature()) {
            addReference(refs, registration.getParent(), hasHost);
        }
        PathElement element = registration.getPathAddress().getLastElement();
        if (SUBSYSTEM.equals(element.getKey())) {
            ModelNode ref = new ModelNode();
            ref.get(FEATURE).set(EXTENSION);
            ref.get(INCLUDE).set(true);
            refs.add(ref);
        }
        if (refs.asList().isEmpty()) {
            feature.remove("refs");
        }
    }

    private void addReference(ModelNode refs, ImmutableManagementResourceRegistration registration, boolean hasHost) {
        if (registration.isFeature()) {
            ModelNode ref = new ModelNode();
            ref.get(FEATURE).set(registration.getFeature());
            if (hasHost && PROFILE.equals(registration.getFeature())) {
                ref.get(NILLABLE).set(true);
            }
            refs.add(ref);
        }
        if (registration.getParent() != null) {
            addReference(refs, registration.getParent(), hasHost);
        }
    }

    private void addOpParam(ModelNode annotation, ModelNode requestProperties) {
        if (requestProperties.isDefined()) {
            List<Property> request = requestProperties.asPropertyList();
            if (!request.isEmpty()) {
                StringJoiner params = new StringJoiner(",");
                for (Property att : request) {
                    params.add(att.getName());
                }
                annotation.get("op-params").set(params.toString());
            }
        }
    }

    private void addRequiredCapabilities(ModelNode feature, ModelNode requestProperties, CapabilityScope scope, boolean isProfile) {
        if (requestProperties.isDefined()) {
            List<Property> request = requestProperties.asPropertyList();
            if (!request.isEmpty()) {
                ModelNode required = new ModelNode().setEmptyList();
                for (Property att : request) {
                    if (att.getValue().hasDefined(CAPABILITY_REFERENCE)) {
                        ModelNode capability = new ModelNode();
                        String baseName = att.getValue().get(CAPABILITY_REFERENCE).asString();
                        String capabilityName = baseName;
                        CapabilityRegistration capReg = getCapability(new CapabilityId(baseName, scope));
                        if (capReg != null && capReg.getCapability().isDynamicallyNamed()) {
                            capabilityName = baseName + ".$" + att.getName();
                        }
                        if (isProfile) {
                            capabilityName = "$profile." + capabilityName;
                        }
                        capability.get(NAME).set(capabilityName);
                        capability.get("optional").set(att.getValue().hasDefined(NILLABLE) && att.getValue().get(NILLABLE).asBoolean());
                        required.add(capability);
                    }
                }
                if (!required.asList().isEmpty()) {
                    feature.get("requires").set(required);
                }
            }
        }
    }

    private boolean isProfileScope(ProcessType processType, PathAddress address) {
        PathElement pe = processType.isServer() || address.size() == 0 ? null : address.getElement(0);
        if (pe != null) {
            return PROFILE.equals(pe.getKey()) && address.size() > 1;
        }
        return false;
    }

    private CapabilityRegistration getCapability(CapabilityId capabilityId) {
        CapabilityRegistration capReg = this.capabilityRegistry.getCapability(capabilityId);
        if (capReg == null) {
            for (CapabilityRegistration reg : this.capabilityRegistry.getPossibleCapabilities()) {
                if (reg.getCapabilityId().getName().equals(capabilityId.getName())) {
                    capReg = reg;
                    break;
                }
            }
        }
        return capReg;
    }

    /**
     *
     * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
     */
    private static final class CheckResourceAccessHandler implements OperationStepHandler {

        static final OperationDefinition DEFAULT_DEFINITION = new SimpleOperationDefinitionBuilder(GlobalOperationHandlers.CHECK_DEFAULT_RESOURCE_ACCESS, new NonResolvingResourceDescriptionResolver())
                .setPrivateEntry()
                .build();

        static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(GlobalOperationHandlers.CHECK_RESOURCE_ACCESS, new NonResolvingResourceDescriptionResolver())
                .setPrivateEntry()
                .build();

        private final boolean runtimeResource;
        private final boolean defaultSetting;
        private final ModelNode accessControlResult;
        private final ModelNode nodeDescription;

        CheckResourceAccessHandler(boolean runtimeResource, boolean defaultSetting, ModelNode accessControlResult, ModelNode nodeDescription) {
            this.runtimeResource = runtimeResource;
            this.defaultSetting = defaultSetting;
            this.accessControlResult = accessControlResult;
            this.nodeDescription = nodeDescription;
        }

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            ModelNode result = new ModelNode();
            boolean customDefaultCheck = operation.get(OP).asString().equals(GlobalOperationHandlers.CHECK_DEFAULT_RESOURCE_ACCESS);
            ResourceAuthorization authResp = context.authorizeResource(true, customDefaultCheck);
            if (authResp == null || authResp.getResourceResult(ActionEffect.ADDRESS).getDecision() == Decision.DENY) {
                if (!defaultSetting || authResp == null) {
                    //We are not allowed to see the resource, so we don't set the accessControlResult, meaning that the ReadResourceAssemblyHandler will ignore it for this address
                } else {
                    result.get(ActionEffect.ADDRESS.toString()).set(false);
                }
            } else {
//                if (!defaultSetting) {
//                    result.get(ADDRESS).set(operation.get(OP_ADDR));
//                }
                addResourceAuthorizationResults(result, authResp);

                ModelNode attributes = new ModelNode();
                attributes.setEmptyObject();

                if (result.get(READ).asBoolean()) {
                    if (nodeDescription.hasDefined(ATTRIBUTES)) {
                        for (Property attrProp : nodeDescription.require(ATTRIBUTES).asPropertyList()) {
                            ModelNode attributeResult = new ModelNode();
                            Storage storage = Storage.valueOf(attrProp.getValue().get(STORAGE).asString().toUpperCase(Locale.ENGLISH));
                            addAttributeAuthorizationResults(attributeResult, attrProp.getName(), authResp, storage == Storage.RUNTIME);
                            if (attributeResult.isDefined()) {
                                attributes.get(attrProp.getName()).set(attributeResult);
                            }
                        }
                    }
                    result.get(ATTRIBUTES).set(attributes);
                }
            }
            accessControlResult.set(result);
        }

        private void addResourceAuthorizationResults(ModelNode result, ResourceAuthorization authResp) {
            if (runtimeResource) {
                addResourceAuthorizationResult(result, authResp, ActionEffect.READ_RUNTIME);
                addResourceAuthorizationResult(result, authResp, ActionEffect.WRITE_RUNTIME);
            } else {
                addResourceAuthorizationResult(result, authResp, ActionEffect.READ_CONFIG);
                addResourceAuthorizationResult(result, authResp, ActionEffect.WRITE_CONFIG);
            }
        }

        private void addResourceAuthorizationResult(ModelNode result, ResourceAuthorization authResp, ActionEffect actionEffect) {
            AuthorizationResult authResult = authResp.getResourceResult(actionEffect);
            result.get(actionEffect == ActionEffect.READ_CONFIG || actionEffect == ActionEffect.READ_RUNTIME ? READ : WRITE).set(authResult.getDecision() == Decision.PERMIT);
        }

        private void addAttributeAuthorizationResults(ModelNode result, String attributeName, ResourceAuthorization authResp, boolean runtime) {
            if (runtime) {
                addAttributeAuthorizationResult(result, attributeName, authResp, ActionEffect.READ_RUNTIME);
                addAttributeAuthorizationResult(result, attributeName, authResp, ActionEffect.WRITE_RUNTIME);
            } else {
                addAttributeAuthorizationResult(result, attributeName, authResp, ActionEffect.READ_CONFIG);
                addAttributeAuthorizationResult(result, attributeName, authResp, ActionEffect.WRITE_CONFIG);
            }
        }

        private void addAttributeAuthorizationResult(ModelNode result, String attributeName, ResourceAuthorization authResp, ActionEffect actionEffect) {
            AuthorizationResult authorizationResult = authResp.getAttributeResult(attributeName, actionEffect);
            if (authorizationResult != null) {
                result.get(actionEffect == ActionEffect.READ_CONFIG || actionEffect == ActionEffect.READ_RUNTIME ? READ : WRITE).set(authorizationResult.getDecision() == Decision.PERMIT);
            }
        }
    }

    /**
     * Assembles the response to a read-resource request from the components gathered by earlier steps.
     */
    private static class ReadFeatureAssemblyHandler implements OperationStepHandler {

        private final ModelNode featureDescription;
        private final Map<PathElement, ModelNode> childResources;
        private final ReadFeatureAccessControlContext accessControlContext;

        /**
         * Creates a ReadResourceAssemblyHandler that will assemble the response using the contents of the given maps.
         *
         * @param featureDescription basic description of the node, of its attributes and of its child types
         * @param childResources read-resource-description response from child resources, where the key is the
         * PathAddress relative to the address of the operation this handler is handling and the value is the full
         * read-resource response. Will not be {@code null}
         * @param accessControlContext context for tracking access control data
         * @param accessControl type of access control output that is needed
         */
        private ReadFeatureAssemblyHandler(final ModelNode featureDescription, final Map<PathElement, ModelNode> childResources,
                final ReadFeatureAccessControlContext accessControlContext) {
            this.featureDescription = featureDescription;
            this.childResources = childResources;
            this.accessControlContext = accessControlContext;
        }

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            for (Map.Entry<PathElement, ModelNode> entry : childResources.entrySet()) {
                final PathElement element = entry.getKey();
                final ModelNode value = entry.getValue();
                if (!value.has(FAILURE_DESCRIPTION)) {
                    ModelNode actualValue = value.get(RESULT);
                    if (actualValue.equals(PROXY_NO_SUCH_RESOURCE)) {
                        featureDescription.get(FEATURE).get(CHILDREN).remove(element.getKey());
                    } else if (actualValue.isDefined()) {
                        if (featureDescription.get(FEATURE).get(CHILDREN).has(element.getKey())) {
                            featureDescription.get(FEATURE).get(CHILDREN).remove(element.getKey());
                        }
                        if (actualValue.hasDefined(FEATURE)) {
                            String name = value.get(RESULT, FEATURE, NAME).asString();
                            featureDescription.get(FEATURE, CHILDREN, name).set(actualValue.get(FEATURE));
                        }
                    } else {
                        if (featureDescription.get(FEATURE).get(CHILDREN).has(element.getKey())) {
                            featureDescription.get(FEATURE).get(CHILDREN).remove(element.getKey());
                        }
                    }
                } else if (value.hasDefined(FAILURE_DESCRIPTION)) {
                    context.getFailureDescription().set(value.get(FAILURE_DESCRIPTION));
                    break;
                }
            }

            if (accessControlContext.defaultWildcardAccessControl != null && accessControlContext.localResourceAccessControlResults != null) {
                ModelNode accessControl = new ModelNode();
                accessControl.setEmptyObject();

                ModelNode defaultControl;
                if (accessControlContext.defaultWildcardAccessControl != null) {
                    accessControl.get(DEFAULT).set(accessControlContext.defaultWildcardAccessControl);
                    defaultControl = accessControlContext.defaultWildcardAccessControl;
                } else {
                    //TODO this should always be present
                    defaultControl = new ModelNode();
                }

                if (accessControlContext.localResourceAccessControlResults != null) {
                    ModelNode exceptions = accessControl.get(EXCEPTIONS);
                    exceptions.setEmptyObject();
                    for (Map.Entry<PathAddress, ModelNode> entry : accessControlContext.localResourceAccessControlResults.entrySet()) {
                        if (!entry.getValue().isDefined()) {
                            //If access was denied CheckResourceAccessHandler will leave this as undefined
                            continue;
                        }
                        if (!entry.getValue().equals(defaultControl)) {
                            //This has different values to the default due to vault expressions being used for attribute values. We need to include the address
                            //in the exception modelnode for the console to be easier able to parse it
                            ModelNode exceptionAddr = entry.getKey().toModelNode();
                            ModelNode exception = entry.getValue();
                            exception.get(ADDRESS).set(exceptionAddr);
                            exceptions.get(exceptionAddr.asString()).set(entry.getValue());
                        }
                    }
                }
            }
            context.getResult().set(featureDescription);
        }
    }

    private static final class ReadFeatureAccessControlContext {

        private final PathAddress opAddress;
        private final List<PathAddress> parentAddresses;
        private List<PathAddress> localResourceAddresses = null;
        private ModelNode defaultWildcardAccessControl;
        private Map<PathAddress, ModelNode> localResourceAccessControlResults = new HashMap<>();

        ReadFeatureAccessControlContext(PathAddress opAddress, ReadFeatureAccessControlContext parent) {
            this.opAddress = opAddress;
            this.parentAddresses = parent != null ? parent.parentAddresses : null;
        }

        void checkResourceAccess(final OperationContext context, final ImmutableManagementResourceRegistration registration, final ModelNode nodeDescription) {
            final ModelNode defaultAccess = Util.createOperation(
                    opAddress.size() > 0 && !opAddress.getLastElement().isWildcard()
                    ? GlobalOperationHandlers.CHECK_DEFAULT_RESOURCE_ACCESS : GlobalOperationHandlers.CHECK_RESOURCE_ACCESS,
                    opAddress);
            defaultWildcardAccessControl = new ModelNode();
            context.addStep(defaultAccess, new CheckResourceAccessHandler(registration.isRuntimeOnly(), true, defaultWildcardAccessControl, nodeDescription), OperationContext.Stage.MODEL, true);

            for (final PathAddress address : localResourceAddresses) {
                final ModelNode op = Util.createOperation(GlobalOperationHandlers.CHECK_RESOURCE_ACCESS, address);
                final ModelNode resultHolder = new ModelNode();
                localResourceAccessControlResults.put(address, resultHolder);
                context.addStep(op, new CheckResourceAccessHandler(registration.isRuntimeOnly(), false, resultHolder, nodeDescription), OperationContext.Stage.MODEL, true);
            }
        }
    }

    private class NestedReadFeatureHandler extends ReadFeatureHandler {

        final ReadFeatureAccessControlContext accessControlContext;
        final OperationStepHandler overrideStepHandler;

        NestedReadFeatureHandler(final ImmutableCapabilityRegistry capabilityRegistry, ReadFeatureAccessControlContext accessControlContext) {
            super(capabilityRegistry);
            this.accessControlContext = accessControlContext;
            this.overrideStepHandler = null;
        }

        NestedReadFeatureHandler(final ImmutableCapabilityRegistry capabilityRegistry, OperationStepHandler overrideStepHandler) {
            super(capabilityRegistry);
            this.accessControlContext = null;
            this.overrideStepHandler = overrideStepHandler;
        }

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            if (accessControlContext != null) {
                doExecute(context, operation, accessControlContext);
            } else {
                try {
                    overrideStepHandler.execute(context, operation);
                } catch (Resource.NoSuchResourceException e) {
                    //Mark it as not accessible so that the assembly handler can remove it
                    context.getResult().set(PROXY_NO_SUCH_RESOURCE);
                } catch (UnauthorizedException e) {
                    //We were not allowed to read it, the assembly handler should still allow people to see it
                    context.getResult().set(new ModelNode());
                }
            }
        }
    }

    /**
     * For use with the access-control parameter
     */
    private enum AccessControl {
        /**
         * No access control information should be included
         */
        NONE("none"),
        /**
         * Access control information should be included alongside the resource descriptions
         */
        COMBINED_DESCRIPTIONS("combined-descriptions"),
        /**
         * Access control information should be inclueded alongside the minimal resource descriptions
         */
        TRIM_DESCRIPTONS("trim-descriptions");

        private static final Map<String, AccessControl> MAP;

        static {
            final Map<String, AccessControl> map = new HashMap<String, AccessControl>();
            for (AccessControl directoryGrouping : values()) {
                map.put(directoryGrouping.localName, directoryGrouping);
            }
            MAP = map;
        }

        public static AccessControl forName(String localName) {
            final AccessControl value = localName != null ? MAP.get(localName.toLowerCase(Locale.ENGLISH)) : null;
            return value == null ? AccessControl.valueOf(localName.toUpperCase(Locale.ENGLISH)) : value;
        }

        private final String localName;

        AccessControl(final String localName) {
            this.localName = localName;
        }

        @Override
        public String toString() {
            return localName;
        }

        public ModelNode toModelNode() {
            return new ModelNode().set(toString());
        }
    }

}
