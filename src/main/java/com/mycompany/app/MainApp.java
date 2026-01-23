package com.mycompany.app;

import org.apache.camel.main.Main;
import org.apache.camel.builder.RouteBuilder;
import com.mycompany.sftp.component.PooledSftpComponent;
import com.mycompany.sftp.pool.SftpConnection;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import java.time.Duration;

public class MainApp {
    public static void main(String[] args) throws Exception {
        Main main = new Main();
        
        // 1. Register the custom component
        // Manual registration of the component since we are not using auto-discovery via META-INF yet
        // or just to be explicit
        main.bind("pooled-sftp", new PooledSftpComponent());

        // 2. Example: Create a custom Pool Configuration bean
        // This is useful for advanced settings like eviction policies and test-on-borrow
        GenericObjectPoolConfig<SftpConnection> customPool = new GenericObjectPoolConfig<>();
        customPool.setMaxTotal(15);            // Max 15 concurrent connections
        customPool.setMaxIdle(10);             // Keep 10 idle connections
        customPool.setMinIdle(2);              // Always keep 2 ready
        customPool.setTestOnBorrow(true);      // Check if connection is alive before using
        customPool.setMaxWait(Duration.ofSeconds(5)); // Wait 5s for a connection before failing

        // Bind the bean to the registry so it can be referenced in the URI
        main.bind("mySftpPoolConfig", customPool);

        main.configure().addRoutesBuilder(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // Scenario A: Use the registered bean (# notation)
                // Read from local 'time' directory and write to 'final' directory on SFTP
                from("file:target/inbox?noop=true")
                    .log("Uploading via shared pool config: ${header.CamelFileName}")
                    .to("pooled-sftp://localhost:2222/upload?username=user&password=password&useConnectionPool=true&poolConfig=#mySftpPoolConfig")
                    .log("Upload complete.");

                // Scenario B: Quick override directly in the URI (Nested properties)
                from("timer:fire?period=10s")
                    .setBody(constant("Small heartbeat file"))
                    .setHeader("CamelFileName", constant("heartbeat.txt"))
                    .log("Heartbeat via URI overrides...")
                    .to("pooled-sftp://localhost:2222/logs?username=user&password=password&useConnectionPool=true&poolConfig.maxTotal=5&poolConfig.minIdle=1")
                    .log("Heartbeat sent.");
            }
        });

        main.run(args);
    }
}