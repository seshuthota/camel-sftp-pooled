package com.mycompany.sftp.pool;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Base64;
import java.util.Hashtable;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Proxy;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SocketFactory;
import com.jcraft.jsch.UIKeyboardInteractive;
import com.jcraft.jsch.UserInfo;
import org.apache.camel.LoggingLevel;
import org.apache.camel.component.file.remote.SftpConfiguration;
import org.apache.camel.component.file.remote.SftpEndpoint;
import org.apache.camel.spi.CamelLogger;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.util.ObjectHelper.isNotEmpty;

public final class SftpUtils {

    private static final Logger LOG = LoggerFactory.getLogger(SftpUtils.class);

    private SftpUtils() {
    }

    public static Session createSession(SftpEndpoint endpoint, SftpConfiguration configuration, Proxy proxy)
            throws JSchException {
        final JSch jsch = new JSch();
        JSch.setLogger(new JSchLogger(endpoint.getConfiguration().getJschLoggingLevel()));

        if (isNotEmpty(configuration.getCiphers())) {
            LOG.debug("Using ciphers: {}", configuration.getCiphers());
            Hashtable<String, String> ciphers = new Hashtable<>();
            ciphers.put("cipher.s2c", configuration.getCiphers());
            ciphers.put("cipher.c2s", configuration.getCiphers());
            JSch.setConfig(ciphers);
        }

        if (isNotEmpty(configuration.getKeyExchangeProtocols())) {
            LOG.debug("Using KEX: {}", configuration.getKeyExchangeProtocols());
            JSch.setConfig("kex", configuration.getKeyExchangeProtocols());
        }

        if (isNotEmpty(configuration.getPrivateKeyFile())) {
            LOG.debug("Using private keyfile: {}", configuration.getPrivateKeyFile());
            if (isNotEmpty(configuration.getPrivateKeyPassphrase())) {
                jsch.addIdentity(configuration.getPrivateKeyFile(), configuration.getPrivateKeyPassphrase());
            } else {
                jsch.addIdentity(configuration.getPrivateKeyFile());
            }
        }

        if (configuration.getPrivateKey() != null) {
            LOG.debug("Using private key information from byte array");
            byte[] passphrase = null;
            if (isNotEmpty(configuration.getPrivateKeyPassphrase())) {
                passphrase = configuration.getPrivateKeyPassphrase().getBytes(StandardCharsets.UTF_8);
            }
            jsch.addIdentity("ID", configuration.getPrivateKey(), null, passphrase);
        }

        if (configuration.getPrivateKeyUri() != null) {
            LOG.debug("Using private key uri : {}", configuration.getPrivateKeyUri());
            byte[] passphrase = null;
            if (isNotEmpty(configuration.getPrivateKeyPassphrase())) {
                passphrase = configuration.getPrivateKeyPassphrase().getBytes(StandardCharsets.UTF_8);
            }
            try {
                InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(endpoint.getCamelContext(),
                        configuration.getPrivateKeyUri());
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                IOHelper.copyAndCloseInput(is, bos);
                jsch.addIdentity("ID", bos.toByteArray(), null, passphrase);
            } catch (IOException e) {
                throw new JSchException("Cannot read resource: " + configuration.getPrivateKeyUri(), e);
            }
        }

        if (configuration.getKeyPair() != null) {
            LOG.debug("Using private key information from key pair");
            KeyPair keyPair = configuration.getKeyPair();
            if (keyPair.getPrivate() != null) {
                // Encode the private key in PEM format for JSCH
                StringBuilder sb = new StringBuilder(256);
                sb.append("-----BEGIN PRIVATE KEY-----").append("\n");
                sb.append(Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded())).append("\n");
                sb.append("-----END PRIVATE KEY-----").append("\n");

                jsch.addIdentity("ID", sb.toString().getBytes(StandardCharsets.UTF_8), null, null);
            } else {
                LOG.warn("PrivateKey in the KeyPair must be filled");
            }
        }

        if (isNotEmpty(configuration.getKnownHostsFile())) {
            LOG.debug("Using knownhosts file: {}", configuration.getKnownHostsFile());
            jsch.setKnownHosts(configuration.getKnownHostsFile());
        }

        if (isNotEmpty(configuration.getKnownHostsUri())) {
            LOG.debug("Using known hosts uri: {}", configuration.getKnownHostsUri());
            try {
                InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(endpoint.getCamelContext(),
                        configuration.getKnownHostsUri());
                jsch.setKnownHosts(is);
            } catch (IOException e) {
                throw new JSchException("Cannot read resource: " + configuration.getKnownHostsUri(), e);
            }
        }

        if (configuration.getKnownHosts() != null) {
            LOG.debug("Using known hosts information from byte array");
            jsch.setKnownHosts(new ByteArrayInputStream(configuration.getKnownHosts()));
        }

        String knownHostsFile = configuration.getKnownHostsFile();
        if (knownHostsFile == null && configuration.isUseUserKnownHostsFile()) {
            knownHostsFile = System.getProperty("user.home") + "/.ssh/known_hosts";
            LOG.info("Known host file not configured, using user known host file: {}", knownHostsFile);
        }
        if (ObjectHelper.isNotEmpty(knownHostsFile)) {
            LOG.debug("Using known hosts information from file: {}", knownHostsFile);
            jsch.setKnownHosts(knownHostsFile);
        }

        final Session session = jsch.getSession(configuration.getUsername(), configuration.getHost(), configuration.getPort());

        if (isNotEmpty(configuration.getStrictHostKeyChecking())) {
            LOG.debug("Using StrictHostKeyChecking: {}", configuration.getStrictHostKeyChecking());
            session.setConfig("StrictHostKeyChecking", configuration.getStrictHostKeyChecking());
        }

        session.setServerAliveInterval(configuration.getServerAliveInterval());
        session.setServerAliveCountMax(configuration.getServerAliveCountMax());

        // compression
        if (configuration.getCompression() > 0) {
            LOG.debug("Using compression: {}", configuration.getCompression());
            session.setConfig("compression.s2c", "zlib@openssh.com,zlib,none");
            session.setConfig("compression.c2s", "zlib@openssh.com,zlib,none");
            session.setConfig("compression_level", Integer.toString(configuration.getCompression()));
        }

        // set the PreferredAuthentications
        if (configuration.getPreferredAuthentications() != null) {
            LOG.debug("Using PreferredAuthentications: {}", configuration.getPreferredAuthentications());
            session.setConfig("PreferredAuthentications", configuration.getPreferredAuthentications());
        }

        // set the ServerHostKeys
        if (configuration.getServerHostKeys() != null) {
            LOG.debug("Using ServerHostKeys: {}", configuration.getServerHostKeys());
            session.setConfig("server_host_key", configuration.getServerHostKeys());
        }

        // set the PublicKeyAcceptedAlgorithms
        if (configuration.getPublicKeyAcceptedAlgorithms() != null) {
            LOG.debug("Using PublicKeyAcceptedAlgorithms: {}", configuration.getPublicKeyAcceptedAlgorithms());
            session.setConfig("PubkeyAcceptedAlgorithms", configuration.getPublicKeyAcceptedAlgorithms());
        }

        // set user information
        session.setUserInfo(new ExtendedUserInfo() {

            private final CamelLogger messageLogger
                    = new CamelLogger(LOG, configuration.getServerMessageLoggingLevel());

            public String getPassphrase() {
                return null;
            }

            public String getPassword() {
                return configuration.getPassword();
            }

            public boolean promptPassword(String s) {
                return true;
            }

            public boolean promptPassphrase(String s) {
                return true;
            }

            public boolean promptYesNo(String s) {
                // are we prompted because the known host files does not exist, and asked whether to auto-create the file
                boolean knownHostFile = s != null && s.endsWith("Are you sure you want to create it?");
                if (knownHostFile && configuration.isAutoCreateKnownHostsFile()) {
                    LOG.warn("Server asks for confirmation (yes|no): {}. Camel will answer yes.", s);
                    return true;
                } else {
                    LOG.warn("Server asks for confirmation (yes|no): {}. Camel will answer no.", s);
                    // Return 'false' indicating modification of the hosts file is
                    // disabled.
                    return false;
                }
            }

            public void showMessage(String s) {
                messageLogger.log("FTP Server: " + s);
            }

            public String[] promptKeyboardInteractive(
                    String destination, String name, String instruction, String[] prompt, boolean[] echo) {
                // must return an empty array if password is null
                if (configuration.getPassword() == null) {
                    return new String[0];
                } else {
                    return new String[] { configuration.getPassword() };
                }
            }

        });

        // set the SO_TIMEOUT for the time after the connect phase
        if (configuration.getServerAliveInterval() == 0) {
            if (configuration.getSoTimeout() > 0) {
                session.setTimeout(configuration.getSoTimeout());
            }
        } else {
            LOG.debug(
                    "The Server Alive Internal is already set, the socket timeout won't be considered to avoid overidding the provided Server alive interval value");
        }

        // set proxy if configured
        if (proxy != null) {
            session.setProxy(proxy);
        }

        if (isNotEmpty(configuration.getBindAddress())) {
            session.setSocketFactory(new SocketFactory() {

                @Override
                public OutputStream getOutputStream(Socket socket) throws IOException {
                    return socket.getOutputStream();
                }

                @Override
                public InputStream getInputStream(Socket socket) throws IOException {
                    return socket.getInputStream();
                }

                @Override
                public Socket createSocket(String host, int port) throws IOException {
                    return createSocketUtil(host, port, configuration.getBindAddress(), session.getTimeout());
                }
            });
        }

        return session;
    }

    private static Socket createSocketUtil(String host, int port, String bindAddress, int timeout) throws IOException {
        // use reflection to create socket with bind address as we cannot use the public API of SocketFactory
        try {
            Socket socket = new Socket();
            socket.bind(new java.net.InetSocketAddress(bindAddress, 0));
            socket.connect(new java.net.InetSocketAddress(host, port), timeout);
            return socket;
        } catch (Exception e) {
            throw new IOException("Cannot create socket", e);
        }
    }

    private static final class JSchLogger implements com.jcraft.jsch.Logger {

        private final LoggingLevel loggingLevel;

        private JSchLogger(LoggingLevel loggingLevel) {
            this.loggingLevel = loggingLevel;
        }

        @Override
        public boolean isEnabled(int level) {
            switch (level) {
                case FATAL:
                    // use ERROR as FATAL
                    return loggingLevel.isEnabled(LoggingLevel.ERROR) && LOG.isErrorEnabled();
                case ERROR:
                    return loggingLevel.isEnabled(LoggingLevel.ERROR) && LOG.isErrorEnabled();
                case WARN:
                    return loggingLevel.isEnabled(LoggingLevel.WARN) && LOG.isWarnEnabled();
                case INFO:
                    return loggingLevel.isEnabled(LoggingLevel.INFO) && LOG.isInfoEnabled();
                default:
                    return loggingLevel.isEnabled(LoggingLevel.DEBUG) && LOG.isDebugEnabled();
            }
        }

        @Override
        public void log(int level, String message) {
            switch (level) {
                case FATAL:
                    // use ERROR as FATAL
                    LOG.error("JSCH -> {}", message);
                    break;
                case ERROR:
                    LOG.error("JSCH -> {}", message);
                    break;
                case WARN:
                    LOG.warn("JSCH -> {}", message);
                    break;
                case INFO:
                    LOG.info("JSCH -> {}", message);
                    break;
                default:
                    LOG.debug("JSCH -> {}", message);
                    break;
            }
        }
    }

    /**
     * Extended user info which supports interactive keyboard mode, by entering the password.
     */
    public interface ExtendedUserInfo extends UserInfo, UIKeyboardInteractive {
    }

}
