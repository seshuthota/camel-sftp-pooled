package com.mycompany.sftp.fix;

import org.apache.camel.Exchange;
import org.apache.camel.impl.engine.DefaultUnitOfWork;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.spi.UnitOfWorkFactory;

/**
 * Factory to configure Camel to use SafeMDCUnitOfWork.
 */
public class SafeUnitOfWorkFactory implements UnitOfWorkFactory {

    @Override
    public UnitOfWork createUnitOfWork(Exchange exchange) {
        if (exchange.getContext().isUseMDCLogging()) {
            return new SafeMDCUnitOfWork(exchange, 
                    exchange.getContext().getInflightRepository(),
                    exchange.getContext().getMDCLoggingKeysPattern(),
                    exchange.getContext().isAllowUseOriginalMessage(),
                    exchange.getContext().isUseBreadcrumb());
        } else {
            return new DefaultUnitOfWork(exchange);
        }
    }
}
