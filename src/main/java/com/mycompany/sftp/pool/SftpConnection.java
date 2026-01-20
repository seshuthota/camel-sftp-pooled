package com.mycompany.sftp.pool;

import java.io.Closeable;
import java.io.IOException;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.Session;

/**
 * Represents a pooled SFTP connection which holds the reference to the JSCH session and channel.
 */
public class SftpConnection implements Closeable {

    private final Session session;
    private final ChannelSftp channel;

    public SftpConnection(Session session, ChannelSftp channel) {
        this.session = session;
        this.channel = channel;
    }

    public Session getSession() {
        return session;
    }

    public ChannelSftp getChannel() {
        return channel;
    }

    public boolean isConnected() {
        return session != null && session.isConnected() && channel != null && channel.isConnected();
    }

    @Override
    public void close() throws IOException {
        if (channel != null && channel.isConnected()) {
            channel.disconnect();
        }
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
    }
}
