package com.mycompany.sftp.component;

import org.apache.camel.component.file.remote.RemoteFileOperations;
import org.apache.camel.component.file.remote.SftpComponent;
import org.apache.camel.component.file.remote.SftpConfiguration;
import org.apache.camel.component.file.remote.SftpEndpoint;
import org.apache.camel.component.file.remote.SftpRemoteFile;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import com.mycompany.sftp.pool.SftpConnection;
import com.mycompany.sftp.pool.SftpConnectionFactory;
import com.mycompany.sftp.pool.SftpConnectionPool;

public class PooledSftpEndpoint extends SftpEndpoint {

    private final PooledSftpConfiguration configuration;

    public PooledSftpEndpoint(String uri, SftpComponent component, SftpConfiguration configuration) {
        super(uri, component, configuration);
        this.configuration = (PooledSftpConfiguration) configuration;
    }

    @Override
    public RemoteFileOperations<SftpRemoteFile> createRemoteFileOperations() {
        if (configuration.isUseConnectionPool()) {
            SftpConnectionFactory factory = new SftpConnectionFactory(this);
            GenericObjectPoolConfig<SftpConnection> config = configuration.getPoolConfig();
            if (config == null) {
                config = new GenericObjectPoolConfig<>();
                // Set default pool settings if needed
            }
            SftpConnectionPool pool = new SftpConnectionPool(factory, config);
            return new PooledSftpOperations(pool, this);
        }
        return super.createRemoteFileOperations();
    }
}
