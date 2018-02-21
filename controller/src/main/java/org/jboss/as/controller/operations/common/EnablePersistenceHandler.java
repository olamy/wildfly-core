/*
 * Copyright 2018 JBoss by Red Hat.
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
package org.jboss.as.controller.operations.common;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.SimpleOperationDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.controller.persistence.ConfigurationPersistenceException;
import org.jboss.as.controller.persistence.DifferedBackupXmlConfigurationPersister;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author Emmanuel Hugonnet (c) 2017 Red Hat, inc.
 */
public class EnablePersistenceHandler implements OperationStepHandler {

    private final DifferedBackupXmlConfigurationPersister persister;
    public static final SimpleOperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder("enable-persistence", ControllerResolver.getResolver("root"))
            .build();
    public EnablePersistenceHandler(DifferedBackupXmlConfigurationPersister persister) {
        this.persister = persister;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        try {
            persister.enablePersistence();
            context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS);
            persister.successfulBoot();
        } catch (ConfigurationPersistenceException ex) {
            throw new OperationFailedException(ex.getMessage(), ex);
        }
    }
}
