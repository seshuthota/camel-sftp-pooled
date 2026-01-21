package com.mycompany.app;

import org.apache.camel.main.Main;
import org.apache.camel.builder.RouteBuilder;
import com.mycompany.sftp.component.PooledSftpComponent;

public class MainApp {
    public static void main(String[] args) throws Exception {
        Main main = new Main();
        
        // Manual registration of the component since we are not using auto-discovery via META-INF yet
        // or just to be explicit
        main.bind("pooled-sftp", new PooledSftpComponent());

        main.configure().addRoutesBuilder(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // Read from local 'time' directory and write to 'final' directory on SFTP
                from("file:time?noop=true")
                    .log("Processing file: ${header.CamelFileName}")
                    .to("pooled-sftp://localhost:2222/final?username=user&password=password&useConnectionPool=true")
                    .log("File uploaded to SFTP final directory");
            }
        });

        main.run(args);
    }
}
