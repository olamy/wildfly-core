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
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESPONSE_HEADERS;
import static org.jboss.as.controller.operations.common.OrderedChildTypesAttachment.ORDERED_CHILDREN;
import static org.jboss.as.server.Services.JBOSS_AS;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.ModelControllerClientConfiguration;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.operations.common.OrderedChildTypesAttachment;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.as.server.ServerEnvironment;
import org.jboss.as.server.ServerEnvironmentService;
import org.jboss.as.server.Services;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;

/**
 *
 * @author Emmanuel Hugonnet (c) 2016 Red Hat, inc.
 */
public class SynchronizationService implements Service<SynchronizationService> {

    public static final ServiceName SERVICE_NAME = JBOSS_AS.append("synchronization");

    private final ModelControllerClientConfiguration configuration;
    private final InjectedValue<ModelController> injectedController = new InjectedValue<>();
    private final InjectedValue<ExecutorService> injectedExecutor = new InjectedValue<>();
    private final InjectedValue<ServerEnvironment> injectedServerEnvironment = new InjectedValue<>();
    private final InjectedValue<PathManager> injectedPathManager = new InjectedValue<>();
    private ModelControllerClient remoteClient;
    private ModelControllerClient client;
    private final ExtensionRegistry extensionRegistry;
    private final ExpressionResolver expressionResolver;

    private SynchronizationService(ModelControllerClientConfiguration configuration,
            final ExtensionRegistry extensionRegistry, final ExpressionResolver expressionResolver) {
        this.configuration = configuration;
        this.extensionRegistry = extensionRegistry;
        this.expressionResolver = expressionResolver;
    }

    public static void addService(final ServiceTarget serviceTarget, final ModelControllerClientConfiguration configuration,
            final ExtensionRegistry extensionRegistry, final ExpressionResolver expressionResolver) {
        SynchronizationService service = new SynchronizationService(configuration, extensionRegistry, expressionResolver);
        serviceTarget.addService(SERVICE_NAME, service)
                .addDependency(Services.JBOSS_SERVER_CONTROLLER, ModelController.class, service.getInjectedModelController())
                .addDependency(ServerEnvironmentService.SERVICE_NAME, ServerEnvironment.class, service.getInjectedServerEnvironment())
                .addDependency(Services.JBOSS_SERVER_EXECUTOR, ExecutorService.class, service.getInjectedExecutor())
                .addDependency(PathManagerService.SERVICE_NAME, PathManager.class, service.getInjectedPathManager())
                .install();
    }

    @Override
    public void start(StartContext sc) throws StartException {
        remoteClient = ModelControllerClient.Factory.create(configuration);
        client = injectedController.getValue().createClient(injectedExecutor.getValue());
    }

    public void synchronize(final OperationContext context) {
        final StandaloneSyncModelParameters parameters = new StandaloneSyncModelParameters(this, expressionResolver,
                injectedServerEnvironment.getValue(), extensionRegistry, injectedPathManager.getValue(), true);
        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                SyncStandaloneModelHandler handler = new SyncStandaloneModelHandler(parameters);
                // Create the operation to get the required configuration from the master
                ModelNode response = readRemoteModel();
                ModelNode result = Operations.readResult(response);
                if(response.hasDefined(RESPONSE_HEADERS, ORDERED_CHILDREN)) {
                    OrderedChildTypesAttachment orderedChildTypes = new OrderedChildTypesAttachment();
                    orderedChildTypes.fromModel(response.get(RESPONSE_HEADERS).get(ORDERED_CHILDREN));
                    context.attach(OrderedChildTypesAttachment.KEY, orderedChildTypes);
                }
                if (result.hasDefined(FAILURE_DESCRIPTION)) {
                    throw new OperationFailedException(result.get(FAILURE_DESCRIPTION).asString());
                }

                final ModelNode syncOperation = new ModelNode();
                syncOperation.get(OP).set("calculate-diff-and-sync");
                syncOperation.get(OP_ADDR).setEmptyList();
                syncOperation.get(DOMAIN_MODEL).set(result);

                // Execute the handler to synchronize the model
                context.addStep(syncOperation, handler, OperationContext.Stage.MODEL, true);
            }
        }, OperationContext.Stage.MODEL, true);
    }

    @Override
    public void stop(StopContext sc) {
        try {
            remoteClient.close();
        } catch (IOException ex) {
            ServerLogger.AS_ROOT_LOGGER.warn(ex.getMessage(), ex);
        }
        try {
            client.close();
        } catch (IOException ex) {
            ServerLogger.AS_ROOT_LOGGER.warn(ex.getMessage(), ex);
        }
    }

    @Override
    public SynchronizationService getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    public InjectedValue<ModelController> getInjectedModelController() {
        return injectedController;
    }

    public InjectedValue<ExecutorService> getInjectedExecutor() {
        return injectedExecutor;
    }

    public InjectedValue<ServerEnvironment> getInjectedServerEnvironment() {
        return injectedServerEnvironment;
    }

    public InjectedValue<PathManager> getInjectedPathManager() {
        return injectedPathManager;
    }

    public ModelNode readLocalModel() {
        try {
            return Operations.readResult(client.execute(Operations.createOperation(ReadServerModelHandler.OPERATION_NAME)));
        } catch (IOException ioex) {
            ServerLogger.AS_ROOT_LOGGER.warn(ioex.getMessage(), ioex);
            throw new RuntimeException(ioex);
        }
    }

    public ModelNode readLocalOperations() {
        try {
            return Operations.readResult(client.execute(Operations.createOperation(ReadServerOperationsHandler.OPERATION_NAME)));
        } catch (IOException ioex) {
            ServerLogger.AS_ROOT_LOGGER.warn(ioex.getMessage(), ioex);
            throw new RuntimeException(ioex);
        }
    }

    public ModelNode readRemoteModel() {
        try {
            return remoteClient.execute(Operations.createOperation(ReadServerModelHandler.OPERATION_NAME));
        } catch (IOException ioex) {
            ServerLogger.AS_ROOT_LOGGER.warn(ioex.getMessage(), ioex);
            throw new RuntimeException(ioex);
        }
    }

    public ModelNode readRemoteOperations() {
        try {
            return remoteClient.execute(Operations.createOperation(ReadServerOperationsHandler.OPERATION_NAME));
        } catch (IOException ioex) {
            ServerLogger.AS_ROOT_LOGGER.warn(ioex.getMessage(), ioex);
            throw new RuntimeException(ioex);
        }
    }
}
