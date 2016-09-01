/*
 * Copyright 2016 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.as.server.operations.sync;


import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_MODEL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXTENSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import org.jboss.as.controller.Extension;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.extension.ExtensionRegistryType;
import org.jboss.as.controller.extension.ExtensionResource;
import org.jboss.as.controller.operations.common.OrderedChildTypesAttachment;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;

/**
 *
 * @author Emmanuel Hugonnet (c) 2016 Red Hat, inc.
 */
public class SyncStandaloneModelHandler implements OperationStepHandler {
    protected final StandaloneSyncModelParameters parameters;

    protected SyncStandaloneModelHandler(StandaloneSyncModelParameters parameters) {
        this.parameters = parameters;
    }


    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        // Create the remote model based on the result of the read-master-model operation
        final Set<String> remoteExtensions = new HashSet<>();
        final Resource remote = ReadServerModelUtil.createResourceFromServerModelOperation(operation.require(DOMAIN_MODEL), remoteExtensions);

        // Describe the local model
        final ModelNode localModel = parameters.readLocalModel();
        if (localModel.hasDefined(FAILURE_DESCRIPTION)) {
            context.getFailureDescription().set(localModel.get(FAILURE_DESCRIPTION));
            return;
        }

        // Translate the local domain-model to a resource
        final Set<String> localExtensions = new HashSet<>();
        ReadServerModelUtil.createResourceFromServerModelOperation(localModel, localExtensions);

        // Create the local describe operations
        final ModelNode localOperations = parameters.readLocalOperations();
        if (localOperations.hasDefined(FAILURE_DESCRIPTION)) {
            context.getFailureDescription().set(localOperations.get(FAILURE_DESCRIPTION));
            return;
        }

        // Determine the extensions we are missing locally
        for (final String extension : localExtensions) {
            remoteExtensions.remove(extension);
        }
        final Set<String> missingExtensions = new HashSet<>(remoteExtensions);
        //Adding the missing extensions
        if(!remoteExtensions.isEmpty()) {
            final ManagementResourceRegistration registration = context.getResourceRegistrationForUpdate();
            Iterator<String> missingExtensionIter = remoteExtensions.iterator();
            while(missingExtensionIter.hasNext()) {
                String extension = missingExtensionIter.next();
                context.addResource(PathAddress.pathAddress(EXTENSION, extension),
                        new ExtensionResource(extension, parameters.getExtensionRegistry()));
                initializeExtension(extension, registration);
                missingExtensionIter.remove();
            }
            context.completeStep(new OperationContext.RollbackHandler() {
                @Override
                public void handleRollback(OperationContext context, ModelNode operation) {
                    if (!missingExtensions.isEmpty()) {
                        for (String extension : missingExtensions) {
                            parameters.getExtensionRegistry().removeExtension(new ExtensionResource(extension, parameters.getExtensionRegistry()), extension, registration);
                        }
                    }
                }
            });
        }

        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                final ModelNode result = localOperations;
                OrderedChildTypesAttachment orderedChildTypesAttachment = context.getAttachment(OrderedChildTypesAttachment.KEY);
                if(orderedChildTypesAttachment == null) {
                    orderedChildTypesAttachment = new OrderedChildTypesAttachment();
                }
                final SyncServerModelOperationHandler handler =
                        new SyncServerModelOperationHandler(result.asList(), remote, remoteExtensions,
                                parameters, orderedChildTypesAttachment);
                context.addStep(operation, handler, OperationContext.Stage.MODEL, true);
            }
        }, OperationContext.Stage.MODEL, true);
    }

    private void initializeExtension(String module, ManagementResourceRegistration rootRegistration) throws OperationFailedException {
        try {
            for (final Extension extension : Module.loadServiceFromCallerModuleLoader(ModuleIdentifier.fromString(module), Extension.class)) {
                ClassLoader oldTccl = SecurityActions.setThreadContextClassLoader(extension.getClass());
                try {
                    extension.initializeParsers(parameters.getExtensionRegistry().getExtensionParsingContext(module, null));
                    extension.initialize(parameters.getExtensionRegistry().getExtensionContext(module, rootRegistration, ExtensionRegistryType.SERVER));
                } finally {
                    SecurityActions.setThreadContextClassLoader(oldTccl);
                }
            }
        } catch (ModuleLoadException e) {
            throw new OperationFailedException(ServerLogger.ROOT_LOGGER.failedToLoadModule(ModuleIdentifier.create(module), e).getMessage(), e);
        }
    }
}
