# Implementation Details: Pooled SFTP Component

This document provides a deep dive into the architecture and implementation of the custom `pooled-sftp` component.

## Architecture Overview

The component is built by extending the standard Apache Camel `camel-ftp` component. Instead of rewriting the entire FTP logic, we inject a Connection Pooling layer between the Camel `SftpEndpoint` and the underlying JSch `Session`.

### Core Classes

| Class | Role | Description |
|---|---|---|
| `PooledSftpComponent` | **Entry Point** | Factory for endpoints. Handles URI parsing and manual property binding. |
| `PooledSftpEndpoint` | **Configuration** | Creates `PooledSftpOperations` instead of standard operations if pooling is enabled. |
| `PooledSftpOperations` | **Orchestrator** | Implements `RemoteFileOperations`. Manages the borrowing and returning of connections from the pool. |
| `SftpConnectionPool` | **Pool Manager** | A typed `GenericObjectPool` from `commons-pool2`. |
| `SftpConnectionFactory` | **Lifecycle** | Creates, validates, and destroys actual JSch connections. |
| `InjectableSftpOperations` | **Adapter** | Extends `SftpOperations` to allow injecting pre-created Sessions/Channels via reflection. |

---

## 1. Connection Pooling Layer

The pooling logic uses `commons-pool2`.

### SftpConnection
A wrapper class that holds:
-   `com.jcraft.jsch.Session`: The SSH session.
-   `com.jcraft.jsch.ChannelSftp`: The SFTP channel.

It implements `Closeable` to ensuring both the channel and session are disconnected when the object is destroyed by the pool.

### SftpConnectionFactory
Implements `PooledObjectFactory<SftpConnection>`.
-   **makeObject**: Uses `SftpUtils` to create a fresh JSch Session and Channel. This logic was adapted from the standard `SftpOperations` to ensure all standard Camel configuration options (ciphers, keys, proxies) are respected.
-   **validateObject**: Checks `session.isConnected()` and `channel.isConnected()` before a connection is borrowed.
-   **destroyObject**: Safely closes the session and channel.

---

## 2. Operations Layer

This is where the component integrates with Camel's routing engine.

### PooledSftpOperations
This class implements `RemoteFileOperations` but **does not** hold a permanent connection state.
1.  **Statelessness**: Every method call (e.g., `storeFile`, `retrieveFile`) is wrapped in a `doWithConnection` block.
2.  **Borrowing**: It borrows an `SftpConnection` from the `SftpConnectionPool` at the start of the operation.
3.  **Delegation**: It instantiates an `InjectableSftpOperations` (a short-lived helper) and injects the borrowed Session and Channel into it.
4.  **Execution**: It delegates the actual file operation to this helper.
5.  **Return**: Finally, it returns the connection to the pool.

### InjectableSftpOperations (The "Hack")
Standard `SftpOperations` in Camel is designed to manage its own connection lifecycle and keeps `session` and `channel` fields `private`.
To reuse the robust, existing file transfer logic without copying 1000+ lines of code, we enable **Dependency Injection** on this class.

```java
public void setSession(Session session) {
    try {
        Field f = SftpOperations.class.getDeclaredField("session");
        f.setAccessible(true);
        f.set(this, session);
    } // ...
}
```
This allows us to "slide in" our pooled connection into the standard Camel logic, effectively tricking it into performing operations on a session it didn't create.

---

## 3. Configuration & Parameter Binding

### Manual Binding in PooledSftpComponent
Standard Camel components use generated "Configurers" to map URI parameters (e.g., `?username=foo`) to configuration objects. Since this is a custom component without the Camel build tools generating these classes, we must bind them manually.

```java
// In PooledSftpComponent.buildFileEndpoint
PropertyBindingSupport.bindProperties(getCamelContext(), config, parameters);
```
 This ensures that standard options like `username`, `password`, `host`, and our custom `useConnectionPool` are correctly populated in `PooledSftpConfiguration`.

### Pool Configuration
The component supports standard pooling configuration via the registry. You can bind a `GenericObjectPoolConfig` bean to the registry and reference it in the URI:
`pooled-sftp://...?poolConfig=#myConfigBean`

This allows tuning of:
-   `maxTotal`: Max active connections.
-   `maxIdle`: Max idle connections.
-   `minIdle`: Min idle connections.
-   `testOnBorrow`: Validate connection before use.
