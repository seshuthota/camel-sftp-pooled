package com.mycompany.sftp.pool;

import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

/**
 * A typed connection pool for SFTP connections.
 */
public class SftpConnectionPool extends GenericObjectPool<SftpConnection> {

    public SftpConnectionPool(SftpConnectionFactory factory, GenericObjectPoolConfig<SftpConnection> config) {
        super(factory, config);
    }
}
