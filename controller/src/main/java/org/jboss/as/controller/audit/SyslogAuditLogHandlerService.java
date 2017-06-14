/*
 * Copyright 2017 JBoss by Red Hat.
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
package org.jboss.as.controller.audit;

import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.wildfly.common.function.ExceptionSupplier;
import org.wildfly.security.credential.source.CredentialSource;

/**
 *
 * @author Emmanuel Hugonnet (c) 2017 Red Hat, inc.
 */
public class SyslogAuditLogHandlerService implements Service<SyslogAuditLogHandlerService> {

    private final InjectedValue<ExceptionSupplier<CredentialSource, Exception>> tlsClientCertStoreCredentialSourceSupplier = new InjectedValue<>();
    private final InjectedValue<ExceptionSupplier<CredentialSource, Exception>> tlsClientCertStoreKeyCredentialSourceSupplier = new InjectedValue<>();
    private final InjectedValue<ExceptionSupplier<CredentialSource, Exception>> tlsTrustStoreSupplier = new InjectedValue<>();

    public SyslogAuditLogHandlerService() {
    }

    @Override
    public void start(StartContext context) throws StartException {
    }

    @Override
    public void stop(StopContext context) {
    }

    @Override
    public SyslogAuditLogHandlerService getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    public InjectedValue<ExceptionSupplier<CredentialSource, Exception>> getTlsClientCertStoreCredentialSourceSupplierInjector() {
        return tlsClientCertStoreCredentialSourceSupplier;
    }

    public InjectedValue<ExceptionSupplier<CredentialSource, Exception>> getTlsClientCertStoreKeyCredentialSourceSupplierInjector() {
        return tlsClientCertStoreKeyCredentialSourceSupplier;
    }

    public InjectedValue<ExceptionSupplier<CredentialSource, Exception>> getTlsTrustStoreSupplierInjector() {
        return tlsTrustStoreSupplier;
    }

     public ExceptionSupplier<CredentialSource, Exception> getTlsClientCertStoreSupplier() {
        return tlsClientCertStoreCredentialSourceSupplier.getOptionalValue();
    }

    public ExceptionSupplier<CredentialSource, Exception> getTlsClientCertStoreKeySupplier() {
        return tlsClientCertStoreKeyCredentialSourceSupplier.getOptionalValue();
    }

    public ExceptionSupplier<CredentialSource, Exception> getTlsTrustStoreSupplier() {
        return tlsTrustStoreSupplier.getOptionalValue();
    }
}
