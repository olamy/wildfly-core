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
package org.jboss.as.controller.persistence;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.xml.namespace.QName;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLElementWriter;

/**
 *
 * @author Emmanuel Hugonnet (c) 2017 Red Hat, inc.
 */
public class DifferedBackupXmlConfigurationPersister extends BackupXmlConfigurationPersister {

    private final AtomicBoolean persisting = new AtomicBoolean();
    private final ConfigurationFile file;

    public DifferedBackupXmlConfigurationPersister(ConfigurationFile file, QName rootElement, XMLElementReader<List<ModelNode>> rootParser, XMLElementWriter<ModelMarshallingContext> rootDeparser, boolean suppressLoad) {
        super(file, rootElement, rootParser, rootDeparser, suppressLoad);
        this.file = file;
    }

    public DifferedBackupXmlConfigurationPersister(ConfigurationFile file, QName rootElement, XMLElementReader<List<ModelNode>> rootParser, XMLElementWriter<ModelMarshallingContext> rootDeparser, boolean reload, boolean allowEmpty) {
        super(file, rootElement, rootParser, rootDeparser, reload, allowEmpty);
        this.file = file;
    }

    @Override
    public boolean isPersisting() {
        return super.isPersisting() && persisting.get();
    }

    public boolean enablePersistence() {
       if(persisting.compareAndSet(false, true)) {
           file.enablePersistence();
           return true;
       }
       return false;
    }
}
