# jremote

A production-ready Java 21 Remote Procedure Call framework with factory-based instance creation and connection pooling. Built on Virtual Threads for high-scalability distributed computing.

## Features

### Core Capabilities
- ✅ **Factory-Based Instances**: Create remote instances like `new Foo()` - no string IDs
- ✅ **Connection Pooling**: Efficient connection reuse with configurable pool sizes
- ✅ **Project Loom**: Virtual Threads handle thousands of concurrent calls
- ✅ **Generic Proxying**: Dynamically proxy any Java interface at runtime
- ✅ **Type-Safe**: Full compile-time type safety with generics
- ✅ **Keep-Alive**: Persistent connections for reduced latency
- ✅ **Constructor Arguments**: Support for parameterized constructors

### Security & Reliability
- ✅ **Interface Validation**: Only methods declared in service interfaces can be invoked
- ✅ **JSON Serialization**: Secure Jackson-based serialization (no Java serialization vulnerabilities)
- ✅ **Error Handling**: Comprehensive exception propagation with stack traces
- ✅ **Logging**: SLF4J + Logback for production observability
- ✅ **Thread-Safe**: Concurrent access supported via ConcurrentHashMap and BlockingQueue
- ✅ **Instance Ownership**: Clients can only destroy their own instances

### Development Experience
- ✅ **Intuitive API**: Works like `new Foo()` instead of string-based lookup
- ✅ **Auto-Closeable**: try-with-resources support with automatic cleanup
- ✅ **Comprehensive Tests**: 60 tests with 100% pass rate
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
        return "User created: " + name;
    }
    
    @Override
    public User getUser(int id) {
        return new User(id, "Alice");
    }
    
    @Override
    public void deleteUser(int id) {
        // Implementation
    }
}
```

#### 3. Start the Server

```java
import org.flossware.jremote.JRemoteServer;

public class Server {
    public static void main(String[] args) {
        JRemoteServer server = JRemoteServer.builder()
            .registerFactory(UserService.class, UserServiceImpl.class)
            .registerFactory(OrderService.class, OrderServiceImpl.class)
            .build();
            
        server.start(8080);
        System.out.println("Server running on port 8080");
    }
}
```

#### 4. Create Remote Instances

```java
import org.flossware.jremote.JRemoteClient;

public class Client {
    public static void main(String[] args) {
        try (JRemoteClient client = new JRemoteClient("localhost", 8080)) {
            
            // Create remote instances like "new Foo()"
            UserService user1 = client.create(UserService.class);
            UserService user2 = client.create(UserService.class);  // Different instance
            
            // Use them
            String result = user1.createUser("Alice");
            System.out.println(result);
            
            User user = user2.getUser(1);
            System.out.println("Retrieved: " + user.getName());
            
            // Explicit cleanup (optional)
            client.destroy(user1);
            
        }  // user2 auto-destroyed on close
    }
}
```

## Advanced Usage

### Constructor Arguments

```java
// Server: register factory
JRemoteServer server = JRemoteServer.builder()
    .registerFactory(DatabaseService.class, DatabaseServiceImpl.class)
    .build();

// Client: create with constructor arguments
try (JRemoteClient client = new JRemoteClient("localhost", 8080)) {
    DatabaseService db = client.create(
        DatabaseService.class, 
        "jdbc:postgresql://localhost:5432/mydb",  // connection string
        "username",                               // username
        "password"                                // password
    );
    
    db.query("SELECT * FROM users");
}
```

### Custom Factories

For complex initialization logic, use a Supplier:

```java
JRemoteServer server = JRemoteServer.builder()
    .registerFactory(OrderService.class, () -> {
        OrderServiceImpl service = new OrderServiceImpl();
        service.setDatabase(dbConnection);
        service.setLogger(logger);
        return service;
    })
    .build();
```

### Connection Pool Configuration

```java
// Default pool (min=1, max=10)
JRemoteClient client = new JRemoteClient("localhost", 8080);

// Custom pool size
JRemoteClient client = new JRemoteClient("localhost", 8080, 5, 20);
```

### Multiple Instances

Each `create()` call creates a new remote instance:

```java
try (JRemoteClient client = new JRemoteClient("localhost", 8080)) {
    UserService user1 = client.create(UserService.class);
    UserService user2 = client.create(UserService.class);
    UserService user3 = client.create(UserService.class);
    
    // All three are independent instances
    user1.createUser("Alice");
    user2.createUser("Bob");
    user3.createUser("Carol");
}  // All three auto-destroyed
```

### Lifecycle Management

```java
try (JRemoteClient client = new JRemoteClient("localhost", 8080)) {
    UserService user = client.create(UserService.class);
    
    // Use the service
    user.createUser("Alice");
    
    // Explicit cleanup (optional)
    client.destroy(user);
    
    // Create another instance
    UserService user2 = client.create(UserService.class);
    
}  // user2 auto-destroyed on close
```

### Error Handling

```java
try (JRemoteClient client = new JRemoteClient("localhost", 8080)) {
    UserService users = client.create(UserService.class);
    
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
create(Class)                            Factory Registry
    ↓                                         ↓
CREATE_INSTANCE request ────────────→    Create Instance
    ↓                                         ↓
Receive objectId ←──────────────────────  Return objectId
    ↓                                         ↓
Create Dynamic Proxy                     Track Instance
    ↓                                         ↓
proxy.method() ──────────────────────→   Lookup Instance
    ↓                                         ↓
METHOD_CALL request                      Invoke Method
    ↓                                         ↓
Receive result ←─────────────────────────  Return Result
    ↓                                         ↓
destroy(proxy) ───────────────────────→   Destroy Instance
```

### Request Types

**CREATE_INSTANCE**: Create new remote instance
- Client sends interface name + constructor arguments
- Server calls factory, generates UUID, returns objectId
- Client creates proxy with embedded objectId

**METHOD_CALL**: Invoke method on remote instance
- Client sends objectId + method name + arguments
- Server routes to instance, validates method, invokes
- Returns result or error

**DESTROY_INSTANCE**: Destroy remote instance
- Client sends objectId
- Server validates ownership, removes instance

### Components

- **JRemoteClient**: Client-side proxy factory with connection pooling
- **JRemoteServer**: Server-side request handler with factory registry
- **ConnectionPool**: Thread-safe socket connection pool
- **ServiceRegistry**: Registry for managing factories and instances
- **InstanceFactory**: Factory abstraction (reflection or custom Supplier)
- **RemoteInvocation**: JSON-serializable request wrapper
- **RemoteResponse**: JSON-serializable response wrapper
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
- Instance ownership prevents cross-client destruction

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

Comprehensive test suite with 60 tests:

```bash
mvn test
```

**Test coverage includes:**
- ✅ Factory-based instance creation
- ✅ Constructor arguments support
- ✅ Multiple instances of same interface
- ✅ Instance lifecycle (create/destroy/auto-cleanup)
- ✅ Connection pool validation
- ✅ Security validation (interface method whitelist)
- ✅ Ownership validation (cross-client protection)
- ✅ Exception propagation with stack traces
- ✅ JSON serialization/deserialization
- ✅ Concurrent client access
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
- Each client manages its own instances independently

## Limitations & Future Enhancements

**Current limitations:**
- No built-in load balancing (use external load balancer)
- No SSL/TLS support (use reverse proxy or VPN)
- No service discovery (clients must know server address)
- No heartbeat/ping protocol for connection validation
- Constructor argument types inferred from values (primitives may need boxing)

**Planned enhancements:**
- Dynamic factory registration/deregistration
- Service discovery mechanism
- Connection pool metrics and monitoring
- Configurable serialization (Protobuf, MessagePack)
- SSL/TLS native support
- Primitive constructor parameter handling

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
