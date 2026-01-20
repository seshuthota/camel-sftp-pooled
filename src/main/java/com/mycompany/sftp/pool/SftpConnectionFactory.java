package com.mycompany.sftp.pool;

import java.nio.charset.Charset;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.apache.camel.component.file.remote.SftpConfiguration;
import org.apache.camel.component.file.remote.SftpEndpoint;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for creating {@link SftpConnection} objects.
 */
public class SftpConnectionFactory implements PooledObjectFactory<SftpConnection> {

    private static final Logger LOG = LoggerFactory.getLogger(SftpConnectionFactory.class);
    private final SftpEndpoint endpoint;
    private final SftpConfiguration configuration;

    public SftpConnectionFactory(SftpEndpoint endpoint) {
        this.endpoint = endpoint;
        this.configuration = endpoint.getConfiguration();
    }

    @Override
    public PooledObject<SftpConnection> makeObject() throws Exception {
        LOG.trace("Creating new SFTP connection to {}:{}", configuration.getHost(), configuration.getPort());
        
        Session session = SftpUtils.createSession(endpoint, configuration, endpoint.getProxy());
        
        if (configuration.getConnectTimeout() > 0) {
            LOG.trace("Connecting use connectTimeout: {} ...", configuration.getConnectTimeout());
            session.connect(configuration.getConnectTimeout());
        } else {
            LOG.trace("Connecting ...");
            session.connect();
        }

        LOG.trace("Session connected, opening SFTP channel");
        ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");

        if (configuration.getFilenameEncoding() != null) {
            Charset ch = Charset.forName(configuration.getFilenameEncoding());
            LOG.trace("Using filename encoding: {}", ch);
            channel.setFilenameEncoding(ch);
        }

        if (configuration.getConnectTimeout() > 0) {
            LOG.trace("Connecting channel use connectTimeout: {} ...", configuration.getConnectTimeout());
            channel.connect(configuration.getConnectTimeout());
        } else {
            LOG.trace("Connecting channel ...");
            channel.connect();
        }
        
        Integer bulkRequests = configuration.getBulkRequests();
        if (bulkRequests != null) {
            LOG.trace("configuring channel to use up to {} bulk request(s)", bulkRequests);
            channel.setBulkRequests(bulkRequests);
        }

        LOG.debug("Connected to {}:{}", configuration.getHost(), configuration.getPort());
        return new DefaultPooledObject<>(new SftpConnection(session, channel));
    }

    @Override
    public void destroyObject(PooledObject<SftpConnection> p) throws Exception {
        SftpConnection connection = p.getObject();
        LOG.trace("Destroying SFTP connection: {}", connection);
        connection.close();
    }

    @Override
    public boolean validateObject(PooledObject<SftpConnection> p) {
        SftpConnection connection = p.getObject();
        boolean connected = connection.isConnected();
        LOG.trace("Validating SFTP connection: {} -> {}", connection, connected);
        return connected;
    }

    @Override
    public void activateObject(PooledObject<SftpConnection> p) throws Exception {
        SftpConnection connection = p.getObject();
        LOG.trace("Activating SFTP connection: {}", connection);
        
        if (!connection.isConnected()) {
            throw new JSchException("Connection is not connected");
        }
    }

    @Override
    public void passivateObject(PooledObject<SftpConnection> p) throws Exception {
        // No-op
    }
}
