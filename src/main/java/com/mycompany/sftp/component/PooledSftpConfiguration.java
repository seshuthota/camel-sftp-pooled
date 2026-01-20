package com.mycompany.sftp.component;

import java.net.URI;
import org.apache.camel.component.file.remote.SftpConfiguration;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import com.mycompany.sftp.pool.SftpConnection;

@UriParams
public class PooledSftpConfiguration extends SftpConfiguration {

    @UriParam(label = "pooling", defaultValue = "false")
    private boolean useConnectionPool;
    @UriParam(label = "pooling")
    private GenericObjectPoolConfig<SftpConnection> poolConfig;

    public PooledSftpConfiguration() {
    }

    public PooledSftpConfiguration(URI uri) {
        super(uri);
    }

    public boolean isUseConnectionPool() {
        return useConnectionPool;
    }

    public void setUseConnectionPool(boolean useConnectionPool) {
        this.useConnectionPool = useConnectionPool;
    }

    public GenericObjectPoolConfig<SftpConnection> getPoolConfig() {
        return poolConfig;
    }

    public void setPoolConfig(GenericObjectPoolConfig<SftpConnection> poolConfig) {
        this.poolConfig = poolConfig;
    }
}
