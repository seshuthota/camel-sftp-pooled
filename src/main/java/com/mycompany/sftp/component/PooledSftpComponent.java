package com.mycompany.sftp.component;

import java.net.URI;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.remote.FtpUtils;
import org.apache.camel.component.file.remote.SftpComponent;
import org.apache.camel.component.file.remote.SftpConfiguration;
import org.apache.camel.component.file.remote.SftpRemoteFile;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.util.StringHelper;

@Component("pooled-sftp")
public class PooledSftpComponent extends SftpComponent {

    public PooledSftpComponent() {
    }

    public PooledSftpComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected GenericFileEndpoint<SftpRemoteFile> buildFileEndpoint(
            String uri, String remaining, Map<String, Object> parameters)
            throws Exception {
        
        String baseUri = StringHelper.before(uri, "?", uri);

        // Customize configuration
        PooledSftpConfiguration config = new PooledSftpConfiguration(new URI(baseUri));

        // Bind parameters to configuration manually since we are not using generated configurers
        org.apache.camel.support.PropertyBindingSupport.bindProperties(getCamelContext(), config, parameters);

        FtpUtils.ensureRelativeFtpDirectory(this, config);

        return new PooledSftpEndpoint(uri, this, config);
    }
}
