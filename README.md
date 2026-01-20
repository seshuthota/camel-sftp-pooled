# Camel SFTP Pooled Component

This project provides a custom Apache Camel component (`pooled-sftp`) that extends the standard `sftp` component to support **connection pooling** using `commons-pool2`. This is designed to improve performance in high-throughput SFTP scenarios by reusing JSch sessions and channels.

## Features

-   **Connection Pooling**: Leverages `commons-pool2` to manage a pool of SFTP connections (Sessions/Channels).
-   **Seamless Integration**: Extends the standard `camel-ftp` component, making it easy to swap in.
-   **Configurable**: Supports standard `GenericObjectPoolConfig` options (maxTotal, maxIdle, etc.) via the URI or registry.
-   **Reflection-based Injection**: Uses reflection to inject pooled connections into the base `SftpOperations`, allowing re-use of standard Camel logic without forking the entire library.

## Prerequisites

-   Java 17+
-   Apache Maven 3.x
-   Apache Camel 4.x

## Build

To build the project:

```bash
mvn clean install
```

## Usage

### 1. Register the Component

In your Camel application initialization (e.g., `Main` class or configuration bean):

```java
import com.mycompany.sftp.component.PooledSftpComponent;

// ...
main.bind("pooled-sftp", new PooledSftpComponent());
```

### 2. Configure the Route

Use the `pooled-sftp` scheme in your URI. You can enable pooling and configure it:

```java
from("file:local/dir")
    .to("pooled-sftp://hostname:22/upload?username=user&password=pass&useConnectionPool=true");
```

### Configuration Options

| Option | Type | Default | Description |
|---|---|---|---|
| `useConnectionPool` | boolean | `false` | Enable connection pooling. |
| `poolConfig` | `GenericObjectPoolConfig` | `null` | Reference to a `GenericObjectPoolConfig` bean in the registry for fine-grained pool tuning. |

## Project Structure

-   `com.mycompany.sftp.component`: Contains the Camel component, endpoint, and operations implementation.
-   `com.mycompany.sftp.pool`: Contains the connection pooling logic (factory, pool, connection wrapper).
-   `com.mycompany.app`: Contains a sample `MainApp` for demonstration.

## License
Apache License 2.0
