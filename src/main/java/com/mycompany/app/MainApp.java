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
                // Read from local 'inbox' and write to SFTP
                // Note: Configure properties via args or sys props in real app
                from("file:target/inbox?noop=true")
                    .log("Processing file: ${header.CamelFileName}")
                    .to("pooled-sftp://localhost:2222/upload?username=user&password=password&useConnectionPool=true&delay=1000")
                    .log("File uploaded");
            }
        });

        main.run(args);
    }
}
