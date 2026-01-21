package com.mycompany.sftp.component;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.apache.sshd.scp.server.ScpCommandFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class PooledSftpRouteTest extends CamelTestSupport {

    private SshServer sshd;
    private static final int PORT = 22222;
    private static final String USERNAME = "test";
    private static final String PASSWORD = "password";
    private static final String SFTP_ROOT = "target/sftp-root";

    @BeforeEach
    public void setUpSftpServer() throws Exception {
        // Setup directories
        File root = new File(SFTP_ROOT);
        root.mkdirs();
        new File(root, "final").mkdirs();

        sshd = SshServer.setUpDefaultServer();
        sshd.setPort(PORT);
        sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(Paths.get("target/hostkey.ser")));
        
        sshd.setPasswordAuthenticator((username, password, session) -> 
            USERNAME.equals(username) && PASSWORD.equals(password));

        sshd.setSubsystemFactories(Collections.singletonList(new SftpSubsystemFactory()));
        sshd.setCommandFactory(new ScpCommandFactory());
        
        // Virtual File System to confine the server to target/sftp-root
        VirtualFileSystemFactory fileSystemFactory = new VirtualFileSystemFactory(Paths.get(SFTP_ROOT));
        sshd.setFileSystemFactory(fileSystemFactory);

        sshd.start();
    }

    @AfterEach
    public void tearDownSftpServer() throws Exception {
        if (sshd != null) {
            sshd.stop();
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Register our component
                context.addComponent("pooled-sftp", new PooledSftpComponent());

                // Read from a test 'time' dir and write to the SFTP server
                from("file:target/time?noop=true&delay=500")
                    .routeId("test-sftp-route")
                    .log("Reading file: ${header.CamelFileName}")
                    .to("pooled-sftp://localhost:" + PORT + "/final?username=" + USERNAME + "&password=" + PASSWORD + "&useConnectionPool=true")
                    .log("Uploaded to SFTP")
                    .to("mock:result");
            }
        };
    }

    @Test
    public void testSftpUpload() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        // Create a file in the source directory
        template.sendBodyAndHeader("file:target/time", "Hello SFTP Pool", "CamelFileName", "test-file.txt");

        // Wait for the route to process
        mock.assertIsSatisfied();

        // Verify file exists in the 'server' directory
        File uploadedFile = new File(SFTP_ROOT + "/final/test-file.txt");
        assertTrue(uploadedFile.exists(), "File should have been uploaded to the SFTP server root/final");
    }
}
