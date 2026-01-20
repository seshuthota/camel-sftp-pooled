package com.mycompany.sftp.component;

import org.apache.camel.Endpoint;
import org.apache.camel.component.file.remote.RemoteFileOperations;
import org.apache.camel.component.file.remote.SftpRemoteFile;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PooledSftpComponentTest extends CamelTestSupport {

    @Test
    public void testPooledComponentConfiguration() throws Exception {
        PooledSftpComponent component = new PooledSftpComponent(context);
        context.addComponent("pooled-sftp", component);

        Endpoint endpoint = context.getEndpoint("pooled-sftp://localhost:22/upload?username=user&password=pass&useConnectionPool=true");
        assertInstanceOf(PooledSftpEndpoint.class, endpoint);

        PooledSftpEndpoint pooledEndpoint = (PooledSftpEndpoint) endpoint;
        PooledSftpConfiguration config = (PooledSftpConfiguration) pooledEndpoint.getConfiguration();
        assertTrue(config.isUseConnectionPool());
        assertEquals("localhost", config.getHost());

        RemoteFileOperations<SftpRemoteFile> ops = pooledEndpoint.createRemoteFileOperations();
        assertInstanceOf(PooledSftpOperations.class, ops);
    }
    
    @Test
    public void testDefaultComponentConfiguration() throws Exception {
        PooledSftpComponent component = new PooledSftpComponent(context);
        context.addComponent("pooled-sftp", component);

        // Default should not use pool if not enabled (though our code defaults false, let's verify)
        Endpoint endpoint = context.getEndpoint("pooled-sftp://localhost:22/upload?username=user&password=pass");
        assertInstanceOf(PooledSftpEndpoint.class, endpoint);

        PooledSftpEndpoint pooledEndpoint = (PooledSftpEndpoint) endpoint;
        PooledSftpConfiguration config = (PooledSftpConfiguration) pooledEndpoint.getConfiguration();
        // default is false
        assertEquals(false, config.isUseConnectionPool());
        
        // Should return standard operations (implementation detail: our override calls super if false)
        // Check implementation of PooledSftpEndpoint.createRemoteFileOperations
    }
}
