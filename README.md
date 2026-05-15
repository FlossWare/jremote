# FlossWare jremote

A production-ready Java 21 Universal Remote Invocation framework with multi-service support and connection pooling. Built on Virtual Threads for high-scalability distributed computing.

## Features

### Core Capabilities
- ✅ **Multi-Service Support**: Host multiple service objects on a single server
- ✅ **Connection Pooling**: Efficient connection reuse with configurable pool sizes
- ✅ **Project Loom**: Virtual Threads handle thousands of concurrent calls
- ✅ **Generic Proxying**: Dynamically proxy any Java interface at runtime
- ✅ **Type-Safe**: Full compile-time type safety with generics
- ✅ **Keep-Alive**: Persistent connections for reduced latency

### Security & Reliability
- ✅ **Interface Validation**: Only methods declared in service interfaces can be invoked
- ✅ **JSON Serialization**: Secure Jackson-based serialization (no Java serialization vulnerabilities)
- ✅ **Error Handling**: Comprehensive exception propagation with stack traces
- ✅ **Logging**: SLF4J + Logback for production observability
- ✅ **Thread-Safe**: Concurrent access supported via ConcurrentHashMap and BlockingQueue

### Development Experience
- ✅ **Builder Pattern**: Fluent API for service registration
- ✅ **Auto-Closeable**: try-with-resources support
- ✅ **Backward Compatible**: Deprecated APIs still functional
- ✅ **Comprehensive Tests**: 56 tests with 100% pass rate
- ✅ **CI/CD Ready**: Automated versioning and deployment

## Quick Start

### Maven Dependency

```xml
<repository>
    <id>packagecloud-flossware</id>
    <url>https://packagecloud.io/flossware/java/maven2/</url>
</repository>

<dependency>
    <groupId>org.flossware</groupId>
    <artifactId>jremote</artifactId>
    <version>1.0</version>
</dependency>
```

### Basic Usage

#### 1. Define Your Service Interface

```java
public interface UserService {
    String createUser(String name);
    User getUser(int id);
    void deleteUser(int id);
}
```

#### 2. Implement the Service

```java
public class UserServiceImpl implements UserService {
    @Override
    public String createUser(String name) {
        // Implementation
        return "User created: " + name;
    }
    
    @Override
    public User getUser(int id) {
        // Implementation
        return new User(id, "Alice");
    }
    
    @Override
    public void deleteUser(int id) {
        // Implementation
    }
}
```

#### 3. Start the Server (Multi-Service)

```java
import org.flossware.jremote.JRemoteServer;

public class Server {
    public static void main(String[] args) {
        JRemoteServer server = JRemoteServer.builder()
            .register("users", UserService.class, new UserServiceImpl())
            .register("orders", OrderService.class, new OrderServiceImpl())
            .register("payments", PaymentService.class, new PaymentServiceImpl())
            .build();
            
        server.start(8080);
        System.out.println("Server running on port 8080");
    }
}
```

#### 4. Create a Client (With Connection Pooling)

```java
import org.flossware.jremote.JRemoteClient;

public class Client {
    public static void main(String[] args) {
        // Create client with connection pool (min=2, max=10)
        try (JRemoteClient client = new JRemoteClient("localhost", 8080, 2, 10)) {
            
            // Get proxies for different services
            UserService users = client.getProxy("users", UserService.class);
            OrderService orders = client.getProxy("orders", OrderService.class);
            
            // Make remote calls (connections are pooled and reused)
            String result = users.createUser("Alice");
            System.out.println(result);
            
            User user = users.getUser(1);
            System.out.println("Retrieved: " + user.getName());
            
            orders.createOrder(123);
            
            // Connection pool stats
            System.out.println("Pool size: " + client.getPoolSize());
            System.out.println("Available: " + client.getAvailableConnections());
        }
    }
}
```

## Advanced Usage

### Connection Pool Configuration

```java
// Default pool (min=1, max=10)
JRemoteClient client = new JRemoteClient("localhost", 8080);

// Custom pool size
JRemoteClient client = new JRemoteClient("localhost", 8080, 5, 20);

// Pool automatically manages connection lifecycle
UserService service = client.getProxy("users", UserService.class);

// Connections are reused across multiple calls
for (int i = 0; i < 1000; i++) {
    service.createUser("User" + i);  // Same connections reused
}
```

### Single Service Mode (Backward Compatible)

```java
// Server
JRemoteServer server = new JRemoteServer(UserService.class, new UserServiceImpl());
server.start(8080);

// Client (deprecated, but still works)
UserService service = JRemoteClient.create(UserService.class, "localhost", 8080);
service.createUser("Alice");
```

### Auto-Generated Service IDs

```java
JRemoteServer.Builder builder = JRemoteServer.builder();

// Register with custom ID
builder.register("my-service", UserService.class, new UserServiceImpl());

// Register with auto-generated UUID
String serviceId = builder.registerWithGeneratedId(OrderService.class, new OrderServiceImpl());
System.out.println("Service registered with ID: " + serviceId);

JRemoteServer server = builder.build();
```

### Error Handling

```java
try (JRemoteClient client = new JRemoteClient("localhost", 8080)) {
    UserService users = client.getProxy("users", UserService.class);
    
    try {
        users.deleteUser(999);  // Might throw exception
    } catch (RemoteException e) {
        System.err.println("Remote call failed: " + e.getOriginalExceptionType());
        System.err.println("Message: " + e.getOriginalMessage());
        e.printStackTrace();  // Stack trace from remote server preserved
    }
}
```

## Architecture

### How It Works

```
Client Side                              Server Side
───────────                              ───────────

JRemoteClient                            JRemoteServer
    ↓                                         ↓
ConnectionPool ←→ Socket ←→ Network ←→ Socket ←→ ServiceRegistry
    ↓                                         ↓
Dynamic Proxy                            Service Lookup
    ↓                                         ↓
RemoteInvocation (JSON)                  Method Validation
    ↓                                         ↓
Send over wire                           Reflection Invocation
    ↓                                         ↓
Wait for response                        Return result
    ↓                                         ↓
RemoteResponse (JSON)                    RemoteResponse (JSON)
    ↓                                         ↓
Return to caller                         Send over wire
```

### Components

- **JRemoteClient**: Client-side proxy factory with connection pooling
- **JRemoteServer**: Server-side request handler with multi-service support
- **ConnectionPool**: Thread-safe socket connection pool
- **ServiceRegistry**: Registry for managing multiple service instances
- **RemoteInvocation**: JSON-serializable method invocation metadata
- **RemoteResponse**: JSON-serializable response wrapper (success or error)
- **RemoteException**: Custom exception preserving remote stack traces

## Configuration

### Logging

Uses SLF4J with Logback. Configure in `logback.xml`:

```xml
<configuration>
    <logger name="org.flossware.jremote" level="DEBUG"/>
    <root level="INFO">
        <appender-ref ref="STDOUT"/>
    </root>
</configuration>
```

### Security

- Only methods declared in the registered interface can be invoked
- Attempts to call non-interface methods are rejected with `SecurityException`
- JSON serialization prevents deserialization attacks

## Version Management

This project uses strict **X.Y** versioning (no X.Y.Z, no SNAPSHOTs):
- **Major.Minor** format enforced by Maven Enforcer plugin
- Automated version bumping via GitHub Actions
- Each release tagged in git

## Building

```bash
# Run tests
mvn test

# Build JAR
mvn clean install

# Validate version format
mvn validate
```

## Testing

Comprehensive test suite with 56 tests:

```bash
mvn test
```

**Test coverage includes:**
- ✅ Multi-service routing and isolation
- ✅ Connection pool lifecycle (acquire/release/validation)
- ✅ Security validation (interface method whitelist)
- ✅ Exception propagation with stack traces
- ✅ JSON serialization/deserialization
- ✅ Concurrent client access
- ✅ Backward compatibility
- ✅ Keep-alive connection reuse

## Requirements

- **Java 21+** (Virtual Threads, Records, Pattern Matching)
- **Maven 3.8+**

## Dependencies

- Jackson 2.17.0 (JSON serialization)
- SLF4J 2.0.12 (Logging API)
- Logback 1.5.3 (Logging implementation)
- JUnit 5.10.2 (Testing)

## Performance Characteristics

### Connection Pooling Benefits

- **Latency**: Eliminates TCP handshake overhead (typically 1-3ms per call)
- **Throughput**: Supports high-concurrency workloads with virtual threads
- **Resource Efficiency**: Bounded connection pool prevents resource exhaustion

### Scalability

- Virtual threads allow thousands of concurrent remote calls
- Connection pool size configurable based on workload
- Keep-alive connections reduce per-call overhead

## Limitations & Future Enhancements

**Current limitations:**
- No built-in load balancing (use external load balancer)
- No SSL/TLS support (use reverse proxy or VPN)
- No service discovery (clients must know service IDs)
- No heartbeat/ping protocol for connection validation

**Planned enhancements:**
- Dynamic service registration/deregistration
- Built-in service discovery mechanism
- Connection pool metrics and monitoring
- Configurable serialization (Protobuf, MessagePack)
- SSL/TLS native support

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make changes with tests
4. Ensure `mvn clean install` passes
5. Submit pull request

## License

[Add license information]

## Credits

Built with ❤️ by FlossWare using Java 21 Virtual Threads (Project Loom).

## Support

- **GitHub**: https://github.com/FlossWare/jremote
- **Issues**: https://github.com/FlossWare/jremote/issues
- **Packagecloud**: https://packagecloud.io/flossware/java
