package com.mycompany.sftp.component;

import java.util.function.Function;

import org.apache.camel.Exchange;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.file.remote.RemoteFileConfiguration;
import org.apache.camel.component.file.remote.RemoteFileOperations;
import org.apache.camel.component.file.remote.SftpEndpoint;
import org.apache.camel.component.file.remote.SftpOperations;
import org.apache.camel.component.file.remote.SftpRemoteFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mycompany.sftp.pool.SftpConnection;
import com.mycompany.sftp.pool.SftpConnectionPool;

/**
 * SFTP remote file operations that uses a connection pool.
 */
public class PooledSftpOperations implements RemoteFileOperations<SftpRemoteFile> {

    private static final Logger LOG = LoggerFactory.getLogger(PooledSftpOperations.class);
    private final SftpConnectionPool pool;
    private final SftpEndpoint endpoint;
    private final SftpOperations delegate;

    public PooledSftpOperations(SftpConnectionPool pool, SftpEndpoint endpoint) {
        this.pool = pool;
        this.endpoint = endpoint;
        // Delegate for non-connection specific methods (if any) or initial setup
        this.delegate = new InjectableSftpOperations(endpoint.getProxy());
        this.delegate.setEndpoint(endpoint);
    }

    @Override
    public void setEndpoint(GenericFileEndpoint<SftpRemoteFile> endpoint) {
        // noop
    }

    @Override
    public GenericFile<SftpRemoteFile> newGenericFile() {
        return delegate.newGenericFile();
    }

    @Override
    public boolean connect(RemoteFileConfiguration configuration, Exchange exchange) throws GenericFileOperationFailedException {
        // connection management is handled by the pool
        return true;
    }

    @Override
    public boolean isConnected() throws GenericFileOperationFailedException {
        return !pool.isClosed();
    }

    @Override
    public void disconnect() throws GenericFileOperationFailedException {
        // we do not disconnect as we pooling
    }

    @Override
    public void forceDisconnect() throws GenericFileOperationFailedException {
        // we do not disconnect as we pooling
    }

    @Override
    public boolean deleteFile(String name) throws GenericFileOperationFailedException {
        return doWithConnection(ops -> ops.deleteFile(name));
    }

    @Override
    public boolean renameFile(String from, String to) throws GenericFileOperationFailedException {
        return doWithConnection(ops -> ops.renameFile(from, to));
    }

    @Override
    public boolean buildDirectory(String directory, boolean absolute) throws GenericFileOperationFailedException {
        return doWithConnection(ops -> ops.buildDirectory(directory, absolute));
    }

    @Override
    public String getCurrentDirectory() throws GenericFileOperationFailedException {
        return doWithConnection(ops -> ops.getCurrentDirectory());
    }

    @Override
    public void changeCurrentDirectory(String path) throws GenericFileOperationFailedException {
        throw new UnsupportedOperationException("changeCurrentDirectory is not supported in pooled mode directly");
    }

    @Override
    public void changeToParentDirectory() throws GenericFileOperationFailedException {
        throw new UnsupportedOperationException("changeToParentDirectory is not supported in pooled mode");
    }

    @Override
    public SftpRemoteFile[] listFiles() throws GenericFileOperationFailedException {
        return doWithConnection(ops -> ops.listFiles());
    }

    @Override
    public SftpRemoteFile[] listFiles(String path) throws GenericFileOperationFailedException {
        return doWithConnection(ops -> ops.listFiles(path));
    }

    @Override
    public boolean retrieveFile(String name, Exchange exchange, long size) throws GenericFileOperationFailedException {
        return doWithConnection(ops -> ops.retrieveFile(name, exchange, size));
    }

    @Override
    public void releaseRetrievedFileResources(Exchange exchange) throws GenericFileOperationFailedException {
        delegate.releaseRetrievedFileResources(exchange);
    }

    @Override
    public boolean storeFile(String name, Exchange exchange, long size) throws GenericFileOperationFailedException {
        return doWithConnection(ops -> ops.storeFile(name, exchange, size));
    }

    @Override
    public boolean existsFile(String name) throws GenericFileOperationFailedException {
        return doWithConnection(ops -> ops.existsFile(name));
    }

    @Override
    public boolean sendNoop() throws GenericFileOperationFailedException {
        return doWithConnection(ops -> ops.sendNoop());
    }

    @Override
    public boolean sendSiteCommand(String command) throws GenericFileOperationFailedException {
        return doWithConnection(ops -> ops.sendSiteCommand(command));
    }

    private <T> T doWithConnection(Function<SftpOperations, T> operation) {
        SftpConnection connection = null;
        try {
            connection = pool.borrowObject();
            
            InjectableSftpOperations ops = new InjectableSftpOperations(endpoint.getProxy());
            ops.setEndpoint(endpoint);
            ops.setSession(connection.getSession());
            ops.setChannel(connection.getChannel());
            
            return operation.apply(ops);
            
        } catch (Exception e) {
            try {
                if (connection != null) {
                    pool.invalidateObject(connection);
                    connection = null;
                }
            } catch (Exception ie) {
                // ignore
            }
            throw new GenericFileOperationFailedException("Error during pooled SFTP operation", e);
        } finally {
            if (connection != null) {
                pool.returnObject(connection);
            }
        }
    }
}
