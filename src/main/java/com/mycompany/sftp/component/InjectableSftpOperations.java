package com.mycompany.sftp.component;

import java.lang.reflect.Field;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.Proxy;
import com.jcraft.jsch.Session;
import org.apache.camel.component.file.remote.SftpOperations;

public class InjectableSftpOperations extends SftpOperations {
    
    public InjectableSftpOperations(Proxy proxy) {
        super(proxy);
    }

    public void setSession(Session session) {
        try {
            Field f = SftpOperations.class.getDeclaredField("session");
            f.setAccessible(true);
            f.set(this, session);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject session via reflection", e);
        }
    }

    public void setChannel(ChannelSftp channel) {
        try {
            Field f = SftpOperations.class.getDeclaredField("channel");
            f.setAccessible(true);
            f.set(this, channel);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject channel via reflection", e);
        }
    }
}
