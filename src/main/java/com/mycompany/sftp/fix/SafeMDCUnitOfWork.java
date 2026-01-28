package com.mycompany.sftp.fix;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.engine.MDCUnitOfWork;
import org.apache.camel.spi.InflightRepository;

/**
 * A safe implementation of MDCUnitOfWork that prevents NullPointerException
 * during async processing when the UnitOfWork is reset before the callback completes.
 */
public class SafeMDCUnitOfWork extends MDCUnitOfWork {

    public SafeMDCUnitOfWork(Exchange exchange, InflightRepository inflightRepository, String pattern, boolean allowUseOriginalMessage, boolean useBreadcrumb) {
        super(exchange, inflightRepository, pattern, allowUseOriginalMessage, useBreadcrumb);
    }

    @Override
    public void afterProcess(Processor processor, Exchange exchange, AsyncCallback callback, boolean doneSync) {
        // Workaround for NPE: In high-concurrency async scenarios (like reactive streams or thread pools),
        // the UnitOfWork might be 'done' and reset (setting exchange to null) before this callback fires.
        if (exchange != null) {
            super.afterProcess(processor, exchange, callback, doneSync);
        } else {
            // Even if exchange is null, we MUST clear the MDC to prevent context leakage
            // to the thread that is picking up the next task.
            clear();
        }
    }
}
