# Fix for MDCUnitOfWork NullPointerException

## The Issue
You are experiencing a `java.lang.NullPointerException` in `MDCUnitOfWork.afterProcess`.

```text
java.lang.NullPointerException: Cannot invoke "org.apache.camel.Exchange.getProperty(...)" because "exchange" is null
        at org.apache.camel.impl.engine.MDCUnitOfWork.afterProcess(MDCUnitOfWork.java:159)
```

## Root Cause
This is caused by a race condition in Camel's asynchronous routing engine when **MDC Logging** is enabled.

1.  **Async Processing**: When a message is processed asynchronously (e.g., via `.toD()` or a pooled consumer), Camel wraps the execution in a callback.
2.  **Completion & Reset**: If the route processing completes, the `UnitOfWork` is marked as "done" and effectively reset. This `reset()` method sets the internal `exchange` reference to `null` to free memory and allow object pooling.
3.  **The Race**: Sometimes, the code that cleans up the MDC logging context (`afterProcess`) runs *after* the `UnitOfWork` has already been reset.
4.  **The Crash**: `afterProcess` tries to access `exchange.getProperty(...)` to check for a "Step ID", but since `exchange` is now `null`, it throws an NPE.

## The Solution
We have implemented a workaround that "safely" handles the MDC cleanup.

1.  **`SafeMDCUnitOfWork`**: This subclass extends Camel's default `MDCUnitOfWork`. It overrides the `afterProcess` method to check if `exchange` is null.
    *   If `exchange` is present, it proceeds as normal.
    *   If `exchange` is null (the race condition occurred), it skips the property check but **still clears the MDC context**. This prevents the NPE while ensuring your logs don't get mixed up.

2.  **`SafeUnitOfWorkFactory`**: This factory tells Camel to create our `SafeMDCUnitOfWork` instead of the default one whenever a new Exchange is created.

## How to Apply to Your Project

You need to register the `SafeUnitOfWorkFactory` with your Camel Context.

### Option 1: Using `Main` (Java Standalone)
If you are using `org.apache.camel.main.Main`, add this to your configuration:

```java
import com.mycompany.sftp.fix.SafeUnitOfWorkFactory;

public class MainApp {
    public static void main(String[] args) throws Exception {
        Main main = new Main();
        
        // Register the fix
        main.configure().addRoutesBuilder(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // Set the custom UnitOfWorkFactory
                getContext().adapt(ExtendedCamelContext.class)
                    .setUnitOfWorkFactory(new SafeUnitOfWorkFactory());
                
                // ... rest of your routes
            }
        });
        
        main.run(args);
    }
}
```

### Option 2: Spring Boot
If you are using Spring Boot, simply register the factory as a Bean. Camel will auto-detect it.

```java
import com.mycompany.sftp.fix.SafeUnitOfWorkFactory;
import org.apache.camel.spi.UnitOfWorkFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MyCamelConfig {

    @Bean
    public UnitOfWorkFactory unitOfWorkFactory() {
        return new SafeUnitOfWorkFactory();
    }
}
```

### Option 3: XML / Blueprint
You can define the bean in your XML configuration:

```xml
<bean id="unitOfWorkFactory" class="com.mycompany.sftp.fix.SafeUnitOfWorkFactory"/>
```
