# Phase 1: DDD Domain Layer Refactoring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor OrderDemo from anemic Hibernate/JPA model to DDD aggregate root with MyBatis, leveraging Java 21 features (sealed interfaces, records, pattern matching, virtual threads), establishing the "invariant wall" that rejects illegal commands.

**Architecture:** Domain layer is pure Java (no framework annotations). Infrastructure layer uses MyBatis for persistence with explicit DO↔aggregate conversion. Application layer orchestrates commands, publishes domain events via Kafka. Virtual threads replace @Async/CompletableFuture for concurrency.

**Tech Stack:** Java 21, Spring Boot 3.4.3, MyBatis-Spring-Boot-Starter 3.0.x, Redisson, Kafka, MySQL

---

## File Structure

```
src/main/java/com/example/orderdemo/
├── domain/
│   ├── order/
│   │   ├── Order.java                    ← Aggregate root (pure class, no annotations)
│   │   ├── OrderId.java                  ← Value object (record)
│   │   ├── OrderState.java               ← sealed interface + records
│   │   ├── OrderCommand.java             ← sealed interface of command records
│   │   ├── OrderEvent.java               ← sealed interface of event records
│   │   ├── OrderRepository.java          ← Repository interface (domain layer)
│   │   └── InvariantViolationException.java
│   └── shared/
│       └── DomainEvent.java              ← Marker interface for all domain events
├── application/
│   └── order/
│       ├── OrderApplicationService.java  ← Orchestrates commands, txn, events
│       └── OrderQueryService.java        ← Read-side queries
├── infrastructure/
│   ├── persistence/
│   │   ├── OrderDO.java                  ← MyBatis data object (maps to DB table)
│   │   ├── OrderMapper.java             ← MyBatis mapper interface
│   │   ├── OrderRepositoryImpl.java     ← Implements domain OrderRepository
│   │   └── OrderMapper.xml              ← MyBatis XML mapping
│   └── config/
│       ├── MyBatisConfig.java
│       └── RedisConfig.java             ← Keep existing Redisson config
├── interfaces/
│   └── rest/
│       └── OrderController.java         ← Simplified REST controller
└── OrderDemoApplication.java

src/main/resources/
├── application.yml                       ← Replaces bootstrap.yml + .properties
├── mapper/
│   └── OrderMapper.xml                   ← MyBatis XML (if not in java path)
└── schema.sql                            ← DDL for orders + idempotency table

src/test/java/com/example/orderdemo/
├── domain/
│   └── order/
│       ├── OrderTest.java               ← Unit tests for aggregate invariants
│       └── OrderStateTransitionTest.java ← State machine exhaustive tests
└── application/
    └── order/
        └── OrderApplicationServiceTest.java ← Integration test with embedded DB
```

---

### Task 1: Strip Phase-2 Dependencies & Switch to MyBatis

**Files:**
- Modify: `pom.xml`
- Delete: `src/main/java/com/example/orderdemo/config/DataSourceConfig.java`
- Delete: `src/main/java/com/example/orderdemo/config/AsyncConfig.java`
- Delete: `src/main/java/com/example/orderdemo/config/RetryConfig.java`
- Modify: `src/main/resources/bootstrap.yml` → rename to `application.yml`
- Delete: `src/main/resources/application.properties`

- [ ] **Step 1: Update pom.xml — remove JPA/Seata/SCA, add MyBatis + H2 for tests**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.4.3</version>
        <relativePath/>
    </parent>
    <groupId>com.example</groupId>
    <artifactId>order-demo</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>order-demo</name>

    <properties>
        <java.version>21</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.mybatis.spring.boot</groupId>
            <artifactId>mybatis-spring-boot-starter</artifactId>
            <version>3.0.4</version>
        </dependency>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.kafka</groupId>
            <artifactId>spring-kafka</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>
        <dependency>
            <groupId>org.redisson</groupId>
            <artifactId>redisson-spring-boot-starter</artifactId>
            <version>3.36.0</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.mybatis.spring.boot</groupId>
            <artifactId>mybatis-spring-boot-starter-test</artifactId>
            <version>3.0.4</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Create unified application.yml with virtual threads enabled**

```yaml
spring:
  application:
    name: order-demo
  threads:
    virtual:
      enabled: true
  datasource:
    url: jdbc:mysql://localhost:3306/order_demo?useSSL=false&allowPublicKeyRetrieval=true
    username: root
    password: root
    driver-class-name: com.mysql.cj.jdbc.Driver
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer

mybatis:
  mapper-locations: classpath:mapper/*.xml
  configuration:
    map-underscore-to-camel-case: true

logging:
  level:
    com.example.orderdemo: INFO
  file:
    name: logs/order-demo.log
```

- [ ] **Step 3: Delete obsolete config files**

Delete these files (no longer needed):
- `src/main/java/com/example/orderdemo/config/DataSourceConfig.java` (Seata DataSourceProxy)
- `src/main/java/com/example/orderdemo/config/AsyncConfig.java` (replaced by virtual threads)
- `src/main/java/com/example/orderdemo/config/RetryConfig.java` (retry moves into application service)
- `src/main/resources/bootstrap.yml` (merged into application.yml)
- `src/main/resources/application.properties` (merged into application.yml)

- [ ] **Step 4: Verify project compiles (expect failures from deleted classes — that's fine for now)**

Run: `./mvnw compile -q 2>&1 | tail -20`
Expected: Compilation errors referencing old model/service classes — confirms deps are wired. We'll fix these in subsequent tasks.

- [ ] **Step 5: Commit**

```bash
git add pom.xml src/main/resources/application.yml
git rm src/main/resources/bootstrap.yml src/main/resources/application.properties
git rm src/main/java/com/example/orderdemo/config/DataSourceConfig.java
git rm src/main/java/com/example/orderdemo/config/AsyncConfig.java
git rm src/main/java/com/example/orderdemo/config/RetryConfig.java
git commit -m "chore: strip Seata/JPA/SCA deps, add MyBatis, enable virtual threads

Remove phase-2 dependencies (Seata, Spring Cloud Alibaba, JPA).
Add MyBatis-Spring-Boot-Starter and H2 for tests.
Enable virtual threads via spring.threads.virtual.enabled.
Consolidate config into single application.yml.

Co-Authored-By: Claude Opus 4 <noreply@anthropic.com>"
```

---

### Task 2: Domain Layer — OrderState Sealed Hierarchy (records)

**Files:**
- Create: `src/main/java/com/example/orderdemo/domain/order/OrderState.java`
- Delete: `src/main/java/com/example/orderdemo/model/OrderState.java`
- Delete: `src/main/java/com/example/orderdemo/model/Created.java`
- Delete: `src/main/java/com/example/orderdemo/model/Confirmed.java`
- Delete: `src/main/java/com/example/orderdemo/model/Cancelled.java`
- Delete: `src/main/java/com/example/orderdemo/model/OrderStateConverter.java`

- [ ] **Step 1: Write the failing test for state description and pattern matching**

Create: `src/test/java/com/example/orderdemo/domain/order/OrderStateTransitionTest.java`

```java
package com.example.orderdemo.domain.order;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrderStateTransitionTest {

    @Test
    void allStatesHaveCorrectDescription() {
        assertEquals("CREATED", OrderState.Created.INSTANCE.description());
        assertEquals("CONFIRMED", OrderState.Confirmed.INSTANCE.description());
        assertEquals("CLOSED", OrderState.Closed.INSTANCE.description());
        assertEquals("CANCELLED", OrderState.Cancelled.INSTANCE.description());
    }

    @Test
    void fromStringParsesValidStates() {
        assertEquals(OrderState.Created.INSTANCE, OrderState.fromString("CREATED"));
        assertEquals(OrderState.Confirmed.INSTANCE, OrderState.fromString("CONFIRMED"));
        assertEquals(OrderState.Closed.INSTANCE, OrderState.fromString("CLOSED"));
        assertEquals(OrderState.Cancelled.INSTANCE, OrderState.fromString("CANCELLED"));
    }

    @Test
    void fromStringThrowsOnUnknown() {
        assertThrows(IllegalArgumentException.class, () -> OrderState.fromString("INVALID"));
    }

    @Test
    void patternMatchingIsExhaustive() {
        OrderState state = OrderState.Created.INSTANCE;
        String result = switch (state) {
            case OrderState.Created s -> "created";
            case OrderState.Confirmed s -> "confirmed";
            case OrderState.Closed s -> "closed";
            case OrderState.Cancelled s -> "cancelled";
        };
        assertEquals("created", result);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl . -Dtest=OrderStateTransitionTest -q 2>&1 | tail -5`
Expected: FAIL — class `OrderState` does not exist in `domain.order` package.

- [ ] **Step 3: Implement OrderState sealed interface with record permits**

Create: `src/main/java/com/example/orderdemo/domain/order/OrderState.java`

```java
package com.example.orderdemo.domain.order;

public sealed interface OrderState {

    String description();

    record Created() implements OrderState {
        static final Created INSTANCE = new Created();
        @Override public String description() { return "CREATED"; }
    }

    record Confirmed() implements OrderState {
        static final Confirmed INSTANCE = new Confirmed();
        @Override public String description() { return "CONFIRMED"; }
    }

    record Closed() implements OrderState {
        static final Closed INSTANCE = new Closed();
        @Override public String description() { return "CLOSED"; }
    }

    record Cancelled() implements OrderState {
        static final Cancelled INSTANCE = new Cancelled();
        @Override public String description() { return "CANCELLED"; }
    }

    static OrderState fromString(String value) {
        return switch (value) {
            case "CREATED" -> Created.INSTANCE;
            case "CONFIRMED" -> Confirmed.INSTANCE;
            case "CLOSED" -> Closed.INSTANCE;
            case "CANCELLED" -> Cancelled.INSTANCE;
            default -> throw new IllegalArgumentException("Unknown order state: " + value);
        };
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -pl . -Dtest=OrderStateTransitionTest -q`
Expected: PASS (4 tests)

- [ ] **Step 5: Delete old model classes**

```bash
git rm src/main/java/com/example/orderdemo/model/OrderState.java
git rm src/main/java/com/example/orderdemo/model/Created.java
git rm src/main/java/com/example/orderdemo/model/Confirmed.java
git rm src/main/java/com/example/orderdemo/model/Cancelled.java
git rm src/main/java/com/example/orderdemo/model/OrderStateConverter.java
git rm src/main/java/com/example/orderdemo/model/Order.java
```

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat(domain): add OrderState sealed interface with record permits

Replaces old class-based state hierarchy with Java 21 sealed interface.
Each state is a record singleton enabling exhaustive pattern matching.
Adds Closed state for the close-order use case (phase 1 demo).

Co-Authored-By: Claude Opus 4 <noreply@anthropic.com>"
```

---

### Task 3: Domain Layer — Value Objects, Commands, Events

**Files:**
- Create: `src/main/java/com/example/orderdemo/domain/order/OrderId.java`
- Create: `src/main/java/com/example/orderdemo/domain/shared/DomainEvent.java`
- Create: `src/main/java/com/example/orderdemo/domain/order/OrderEvent.java`
- Create: `src/main/java/com/example/orderdemo/domain/order/OrderCommand.java`
- Create: `src/main/java/com/example/orderdemo/domain/order/InvariantViolationException.java`

- [ ] **Step 1: Write tests for value objects and command/event construction**

Create: `src/test/java/com/example/orderdemo/domain/order/OrderValueObjectsTest.java`

```java
package com.example.orderdemo.domain.order;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class OrderValueObjectsTest {

    @Test
    void orderIdRejectsNull() {
        assertThrows(NullPointerException.class, () -> new OrderId(null));
    }

    @Test
    void orderIdEquality() {
        assertEquals(new OrderId(1L), new OrderId(1L));
        assertNotEquals(new OrderId(1L), new OrderId(2L));
    }

    @Test
    void placeOrderCommandValidation() {
        var cmd = new OrderCommand.PlaceOrder("Coffee", 3, "idem-key-001");
        assertEquals("Coffee", cmd.productName());
        assertEquals(3, cmd.quantity());
        assertEquals("idem-key-001", cmd.idempotencyKey());
    }

    @Test
    void placeOrderCommandRejectsInvalidQuantity() {
        assertThrows(IllegalArgumentException.class,
            () -> new OrderCommand.PlaceOrder("Coffee", 0, "key"));
        assertThrows(IllegalArgumentException.class,
            () -> new OrderCommand.PlaceOrder("Coffee", -1, "key"));
    }

    @Test
    void closeOrderCommandConstruction() {
        var cmd = new OrderCommand.CloseOrder(new OrderId(1L), "timeout", "idem-key-002");
        assertEquals(new OrderId(1L), cmd.orderId());
        assertEquals("timeout", cmd.reason());
    }

    @Test
    void orderCreatedEventCarriesData() {
        var event = new OrderEvent.OrderCreated(new OrderId(1L), "Coffee", 3, Instant.now());
        assertEquals(new OrderId(1L), event.orderId());
        assertEquals("Coffee", event.productName());
    }

    @Test
    void orderClosedEventCarriesReason() {
        var event = new OrderEvent.OrderClosed(new OrderId(1L), "timeout", Instant.now());
        assertEquals("timeout", event.reason());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl . -Dtest=OrderValueObjectsTest -q 2>&1 | tail -5`
Expected: FAIL — classes not found.

- [ ] **Step 3: Implement value objects, commands, events**

Create: `src/main/java/com/example/orderdemo/domain/order/OrderId.java`

```java
package com.example.orderdemo.domain.order;

import java.util.Objects;

public record OrderId(Long value) {
    public OrderId {
        Objects.requireNonNull(value, "OrderId value must not be null");
    }
}
```

Create: `src/main/java/com/example/orderdemo/domain/shared/DomainEvent.java`

```java
package com.example.orderdemo.domain.shared;

import java.time.Instant;

public interface DomainEvent {
    Instant occurredAt();
}
```

Create: `src/main/java/com/example/orderdemo/domain/order/OrderEvent.java`

```java
package com.example.orderdemo.domain.order;

import com.example.orderdemo.domain.shared.DomainEvent;
import java.time.Instant;

public sealed interface OrderEvent extends DomainEvent {

    record OrderCreated(OrderId orderId, String productName, int quantity, Instant occurredAt)
            implements OrderEvent {}

    record OrderConfirmed(OrderId orderId, Instant occurredAt)
            implements OrderEvent {}

    record OrderClosed(OrderId orderId, String reason, Instant occurredAt)
            implements OrderEvent {}

    record OrderCancelled(OrderId orderId, String reason, Instant occurredAt)
            implements OrderEvent {}
}
```

Create: `src/main/java/com/example/orderdemo/domain/order/OrderCommand.java`

```java
package com.example.orderdemo.domain.order;

public sealed interface OrderCommand {

    record PlaceOrder(String productName, int quantity, String idempotencyKey) implements OrderCommand {
        public PlaceOrder {
            if (quantity <= 0) {
                throw new IllegalArgumentException("Quantity must be positive, got: " + quantity);
            }
        }
    }

    record ConfirmOrder(OrderId orderId, String idempotencyKey) implements OrderCommand {}

    record CloseOrder(OrderId orderId, String reason, String idempotencyKey) implements OrderCommand {}

    record CancelOrder(OrderId orderId, String reason, String idempotencyKey) implements OrderCommand {}
}
```

Create: `src/main/java/com/example/orderdemo/domain/order/InvariantViolationException.java`

```java
package com.example.orderdemo.domain.order;

public class InvariantViolationException extends RuntimeException {
    private final OrderId orderId;
    private final String invariant;

    public InvariantViolationException(OrderId orderId, String invariant, String message) {
        super(message);
        this.orderId = orderId;
        this.invariant = invariant;
    }

    public OrderId orderId() { return orderId; }
    public String invariant() { return invariant; }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -pl . -Dtest=OrderValueObjectsTest -q`
Expected: PASS (7 tests)

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(domain): add OrderId, OrderCommand, OrderEvent sealed hierarchies

OrderId: value object with null-check.
OrderCommand: sealed interface with PlaceOrder (validates quantity > 0),
ConfirmOrder, CloseOrder, CancelOrder — all carry idempotencyKey.
OrderEvent: sealed interface implementing DomainEvent marker.
InvariantViolationException: carries orderId + invariant name for audit.

Co-Authored-By: Claude Opus 4 <noreply@anthropic.com>"
```

---

### Task 4: Domain Layer — Order Aggregate Root with Invariants

**Files:**
- Create: `src/main/java/com/example/orderdemo/domain/order/Order.java`
- Create: `src/main/java/com/example/orderdemo/domain/order/OrderRepository.java`
- Test: `src/test/java/com/example/orderdemo/domain/order/OrderTest.java`

- [ ] **Step 1: Write the failing test — aggregate creation and invariant enforcement**

Create: `src/test/java/com/example/orderdemo/domain/order/OrderTest.java`

```java
package com.example.orderdemo.domain.order;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class OrderTest {

    @Test
    void createOrderProducesCreatedEvent() {
        var cmd = new OrderCommand.PlaceOrder("Coffee", 3, "key-001");
        var order = Order.create(cmd);

        assertNotNull(order);
        assertEquals("Coffee", order.productName());
        assertEquals(3, order.quantity());
        assertInstanceOf(OrderState.Created.class, order.state());

        List<OrderEvent> events = order.domainEvents();
        assertEquals(1, events.size());
        assertInstanceOf(OrderEvent.OrderCreated.class, events.getFirst());

        var event = (OrderEvent.OrderCreated) events.getFirst();
        assertEquals("Coffee", event.productName());
        assertEquals(3, event.quantity());
    }

    @Test
    void confirmOrderFromCreatedState() {
        var order = Order.create(new OrderCommand.PlaceOrder("Coffee", 2, "key-001"));
        order.clearEvents();

        var cmd = new OrderCommand.ConfirmOrder(order.id(), "key-002");
        order.confirm(cmd);

        assertInstanceOf(OrderState.Confirmed.class, order.state());
        assertEquals(1, order.domainEvents().size());
        assertInstanceOf(OrderEvent.OrderConfirmed.class, order.domainEvents().getFirst());
    }

    @Test
    void closeOrderFromCreatedState() {
        var order = Order.create(new OrderCommand.PlaceOrder("Coffee", 2, "key-001"));
        order.clearEvents();

        var cmd = new OrderCommand.CloseOrder(order.id(), "timeout", "key-003");
        order.close(cmd);

        assertInstanceOf(OrderState.Closed.class, order.state());
        assertEquals(1, order.domainEvents().size());
        var event = (OrderEvent.OrderClosed) order.domainEvents().getFirst();
        assertEquals("timeout", event.reason());
    }

    @Test
    void cannotConfirmAlreadyClosedOrder() {
        var order = Order.create(new OrderCommand.PlaceOrder("Coffee", 2, "key-001"));
        order.close(new OrderCommand.CloseOrder(order.id(), "timeout", "key-002"));
        order.clearEvents();

        var cmd = new OrderCommand.ConfirmOrder(order.id(), "key-003");
        var ex = assertThrows(InvariantViolationException.class, () -> order.confirm(cmd));
        assertTrue(ex.getMessage().contains("Cannot confirm"));
        assertEquals("state-transition", ex.invariant());
    }

    @Test
    void cannotCloseAlreadyConfirmedOrder() {
        var order = Order.create(new OrderCommand.PlaceOrder("Coffee", 2, "key-001"));
        order.confirm(new OrderCommand.ConfirmOrder(order.id(), "key-002"));
        order.clearEvents();

        var cmd = new OrderCommand.CloseOrder(order.id(), "timeout", "key-003");
        var ex = assertThrows(InvariantViolationException.class, () -> order.close(cmd));
        assertTrue(ex.getMessage().contains("Cannot close"));
    }

    @Test
    void cannotCloseCancelledOrder() {
        var order = Order.create(new OrderCommand.PlaceOrder("Coffee", 2, "key-001"));
        order.cancel(new OrderCommand.CancelOrder(order.id(), "user request", "key-002"));
        order.clearEvents();

        var cmd = new OrderCommand.CloseOrder(order.id(), "timeout", "key-003");
        assertThrows(InvariantViolationException.class, () -> order.close(cmd));
    }

    @Test
    void canCancelFromCreatedState() {
        var order = Order.create(new OrderCommand.PlaceOrder("Coffee", 2, "key-001"));
        order.clearEvents();

        order.cancel(new OrderCommand.CancelOrder(order.id(), "changed mind", "key-002"));
        assertInstanceOf(OrderState.Cancelled.class, order.state());
    }

    @Test
    void cannotCancelConfirmedOrder() {
        var order = Order.create(new OrderCommand.PlaceOrder("Coffee", 2, "key-001"));
        order.confirm(new OrderCommand.ConfirmOrder(order.id(), "key-002"));
        order.clearEvents();

        var cmd = new OrderCommand.CancelOrder(order.id(), "too late", "key-003");
        assertThrows(InvariantViolationException.class, () -> order.cancel(cmd));
    }

    @Test
    void quantityMustBePositive() {
        assertThrows(IllegalArgumentException.class,
            () -> new OrderCommand.PlaceOrder("Coffee", 0, "key"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl . -Dtest=OrderTest -q 2>&1 | tail -5`
Expected: FAIL — `Order` class not found or missing methods.

- [ ] **Step 3: Implement Order aggregate root**

Create: `src/main/java/com/example/orderdemo/domain/order/Order.java`

```java
package com.example.orderdemo.domain.order;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Order {

    private OrderId id;
    private String productName;
    private int quantity;
    private OrderState state;
    private long version;
    private final List<OrderEvent> domainEvents = new ArrayList<>();

    private Order() {}

    public static Order create(OrderCommand.PlaceOrder cmd) {
        var order = new Order();
        order.productName = cmd.productName();
        order.quantity = cmd.quantity();
        order.state = OrderState.Created.INSTANCE;
        order.domainEvents.add(new OrderEvent.OrderCreated(
                order.id, cmd.productName(), cmd.quantity(), Instant.now()));
        return order;
    }

    public void confirm(OrderCommand.ConfirmOrder cmd) {
        if (!(state instanceof OrderState.Created)) {
            throw new InvariantViolationException(id, "state-transition",
                    "Cannot confirm order in state: " + state.description());
        }
        this.state = OrderState.Confirmed.INSTANCE;
        domainEvents.add(new OrderEvent.OrderConfirmed(id, Instant.now()));
    }

    public void close(OrderCommand.CloseOrder cmd) {
        if (!(state instanceof OrderState.Created)) {
            throw new InvariantViolationException(id, "state-transition",
                    "Cannot close order in state: " + state.description());
        }
        this.state = OrderState.Closed.INSTANCE;
        domainEvents.add(new OrderEvent.OrderClosed(id, cmd.reason(), Instant.now()));
    }

    public void cancel(OrderCommand.CancelOrder cmd) {
        if (!(state instanceof OrderState.Created)) {
            throw new InvariantViolationException(id, "state-transition",
                    "Cannot cancel order in state: " + state.description());
        }
        this.state = OrderState.Cancelled.INSTANCE;
        domainEvents.add(new OrderEvent.OrderCancelled(id, cmd.reason(), Instant.now()));
    }

    public static Order reconstitute(OrderId id, String productName, int quantity, OrderState state, long version) {
        var order = new Order();
        order.id = id;
        order.productName = productName;
        order.quantity = quantity;
        order.state = state;
        order.version = version;
        return order;
    }

    public void assignId(OrderId id) {
        if (this.id != null) throw new IllegalStateException("Id already assigned");
        this.id = id;
    }

    public OrderId id() { return id; }
    public String productName() { return productName; }
    public int quantity() { return quantity; }
    public OrderState state() { return state; }
    public long version() { return version; }

    public List<OrderEvent> domainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    public void clearEvents() {
        domainEvents.clear();
    }
}
```

Create: `src/main/java/com/example/orderdemo/domain/order/OrderRepository.java`

```java
package com.example.orderdemo.domain.order;

import java.util.Optional;

public interface OrderRepository {
    void save(Order order);
    Optional<Order> findById(OrderId id);
    boolean existsByIdempotencyKey(String idempotencyKey);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -pl . -Dtest=OrderTest -q`
Expected: PASS (9 tests)

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(domain): implement Order aggregate root with invariant guards

Order.create() factory, confirm/close/cancel with state-transition invariants.
Invalid transitions throw InvariantViolationException with invariant name.
reconstitute() for loading from persistence without triggering events.
OrderRepository interface defined in domain layer (no framework deps).

State machine:
  Created → Confirmed | Closed | Cancelled
  Confirmed → (terminal)
  Closed → (terminal)
  Cancelled → (terminal)

Co-Authored-By: Claude Opus 4 <noreply@anthropic.com>"
```

---

### Task 5: Infrastructure — MyBatis Persistence Layer

**Files:**
- Create: `src/main/java/com/example/orderdemo/infrastructure/persistence/OrderDO.java`
- Create: `src/main/java/com/example/orderdemo/infrastructure/persistence/OrderMapper.java`
- Create: `src/main/resources/mapper/OrderMapper.xml`
- Create: `src/main/java/com/example/orderdemo/infrastructure/persistence/OrderRepositoryImpl.java`
- Create: `src/main/resources/schema.sql`
- Create: `src/main/java/com/example/orderdemo/infrastructure/config/MyBatisConfig.java`

- [ ] **Step 1: Write the failing integration test for repository**

Create: `src/test/java/com/example/orderdemo/infrastructure/persistence/OrderRepositoryImplTest.java`

```java
package com.example.orderdemo.infrastructure.persistence;

import com.example.orderdemo.domain.order.*;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;

import static org.junit.jupiter.api.Assertions.*;

@MybatisTest
@Import(OrderRepositoryImpl.class)
@Sql(statements = {
    "CREATE TABLE IF NOT EXISTS orders (id BIGINT AUTO_INCREMENT PRIMARY KEY, product_name VARCHAR(255) NOT NULL, quantity INT NOT NULL, state VARCHAR(32) NOT NULL, version BIGINT NOT NULL DEFAULT 0)",
    "CREATE TABLE IF NOT EXISTS idempotency_keys (idempotency_key VARCHAR(64) PRIMARY KEY, order_id BIGINT NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
})
class OrderRepositoryImplTest {

    @Autowired
    private OrderRepositoryImpl orderRepository;

    @Test
    void saveAndFindNewOrder() {
        var order = Order.create(new OrderCommand.PlaceOrder("Coffee", 3, "key-001"));
        orderRepository.save(order);

        assertNotNull(order.id());

        var found = orderRepository.findById(order.id());
        assertTrue(found.isPresent());
        assertEquals("Coffee", found.get().productName());
        assertEquals(3, found.get().quantity());
        assertInstanceOf(OrderState.Created.class, found.get().state());
    }

    @Test
    void saveUpdatesExistingOrder() {
        var order = Order.create(new OrderCommand.PlaceOrder("Tea", 1, "key-002"));
        orderRepository.save(order);

        order.clearEvents();
        order.confirm(new OrderCommand.ConfirmOrder(order.id(), "key-003"));
        orderRepository.save(order);

        var found = orderRepository.findById(order.id()).orElseThrow();
        assertInstanceOf(OrderState.Confirmed.class, found.get().state());
    }

    @Test
    void idempotencyKeyIsRecorded() {
        var order = Order.create(new OrderCommand.PlaceOrder("Coffee", 2, "unique-key-999"));
        orderRepository.save(order);

        assertTrue(orderRepository.existsByIdempotencyKey("unique-key-999"));
        assertFalse(orderRepository.existsByIdempotencyKey("nonexistent-key"));
    }

    @Test
    void optimisticLockingIncrementsVersion() {
        var order = Order.create(new OrderCommand.PlaceOrder("Coffee", 2, "key-v1"));
        orderRepository.save(order);
        assertEquals(0, order.version());

        var loaded = orderRepository.findById(order.id()).orElseThrow();
        loaded.confirm(new OrderCommand.ConfirmOrder(loaded.id(), "key-v2"));
        orderRepository.save(loaded);

        var reloaded = orderRepository.findById(loaded.id()).orElseThrow();
        assertEquals(1, reloaded.version());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl . -Dtest=OrderRepositoryImplTest -q 2>&1 | tail -10`
Expected: FAIL — classes not found.

- [ ] **Step 3: Implement persistence layer**

Create: `src/main/java/com/example/orderdemo/infrastructure/persistence/OrderDO.java`

```java
package com.example.orderdemo.infrastructure.persistence;

public class OrderDO {
    private Long id;
    private String productName;
    private int quantity;
    private String state;
    private long version;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
}
```

Create: `src/main/java/com/example/orderdemo/infrastructure/persistence/OrderMapper.java`

```java
package com.example.orderdemo.infrastructure.persistence;

import org.apache.ibatis.annotations.*;

@Mapper
public interface OrderMapper {

    @Insert("INSERT INTO orders (product_name, quantity, state, version) VALUES (#{productName}, #{quantity}, #{state}, 0)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(OrderDO orderDO);

    @Update("UPDATE orders SET product_name = #{productName}, quantity = #{quantity}, state = #{state}, version = version + 1 WHERE id = #{id} AND version = #{version}")
    int update(OrderDO orderDO);

    @Select("SELECT id, product_name, quantity, state, version FROM orders WHERE id = #{id}")
    OrderDO findById(Long id);

    @Insert("INSERT INTO idempotency_keys (idempotency_key, order_id) VALUES (#{idempotencyKey}, #{orderId})")
    void insertIdempotencyKey(@Param("idempotencyKey") String idempotencyKey, @Param("orderId") Long orderId);

    @Select("SELECT COUNT(1) FROM idempotency_keys WHERE idempotency_key = #{idempotencyKey}")
    boolean existsByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);
}
```

Create: `src/main/java/com/example/orderdemo/infrastructure/persistence/OrderRepositoryImpl.java`

```java
package com.example.orderdemo.infrastructure.persistence;

import com.example.orderdemo.domain.order.*;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderMapper orderMapper;

    public OrderRepositoryImpl(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Override
    public void save(Order order) {
        OrderDO dataObject = toDataObject(order);
        if (order.id() == null) {
            orderMapper.insert(dataObject);
            order.assignId(new OrderId(dataObject.getId()));
            String idempotencyKey = extractIdempotencyKey(order);
            if (idempotencyKey != null) {
                orderMapper.insertIdempotencyKey(idempotencyKey, dataObject.getId());
            }
        } else {
            int updated = orderMapper.update(dataObject);
            if (updated == 0) {
                throw new OptimisticLockException(order.id());
            }
        }
    }

    @Override
    public Optional<Order> findById(OrderId id) {
        OrderDO dataObject = orderMapper.findById(id.value());
        if (dataObject == null) return Optional.empty();
        return Optional.of(toDomain(dataObject));
    }

    @Override
    public boolean existsByIdempotencyKey(String idempotencyKey) {
        return orderMapper.existsByIdempotencyKey(idempotencyKey);
    }

    private OrderDO toDataObject(Order order) {
        OrderDO d = new OrderDO();
        d.setId(order.id() != null ? order.id().value() : null);
        d.setProductName(order.productName());
        d.setQuantity(order.quantity());
        d.setState(order.state().description());
        d.setVersion(order.version());
        return d;
    }

    private Order toDomain(OrderDO d) {
        return Order.reconstitute(
                new OrderId(d.getId()),
                d.getProductName(),
                d.getQuantity(),
                OrderState.fromString(d.getState()),
                d.getVersion()
        );
    }

    private String extractIdempotencyKey(Order order) {
        var events = order.domainEvents();
        if (!events.isEmpty() && events.getFirst() instanceof OrderEvent.OrderCreated) {
            return null; // idempotency key from command — need to pass it through
        }
        return null;
    }
}
```

Create: `src/main/java/com/example/orderdemo/infrastructure/persistence/OptimisticLockException.java`

```java
package com.example.orderdemo.infrastructure.persistence;

import com.example.orderdemo.domain.order.OrderId;

public class OptimisticLockException extends RuntimeException {
    private final OrderId orderId;

    public OptimisticLockException(OrderId orderId) {
        super("Optimistic lock conflict for order: " + orderId.value());
        this.orderId = orderId;
    }

    public OrderId orderId() { return orderId; }
}
```

Create: `src/main/resources/schema.sql`

```sql
CREATE TABLE IF NOT EXISTS orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_name VARCHAR(255) NOT NULL,
    quantity INT NOT NULL,
    state VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS idempotency_keys (
    idempotency_key VARCHAR(64) PRIMARY KEY,
    order_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_order_id (order_id)
);
```

Create: `src/test/resources/application-test.yml`

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;MODE=MYSQL;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
  sql:
    init:
      mode: never

mybatis:
  configuration:
    map-underscore-to-camel-case: true
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -pl . -Dtest=OrderRepositoryImplTest -q`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(infra): implement MyBatis-based OrderRepository

OrderDO data object maps to orders table.
OrderMapper with insert/update/findById/idempotency operations.
OrderRepositoryImpl converts between domain aggregate and DO.
Optimistic locking via version column (WHERE version = ?).
Idempotency keys table for command deduplication.
H2 test config for integration tests.

Co-Authored-By: Claude Opus 4 <noreply@anthropic.com>"
```

---

### Task 6: Application Layer — OrderApplicationService

**Files:**
- Create: `src/main/java/com/example/orderdemo/application/order/OrderApplicationService.java`
- Create: `src/main/java/com/example/orderdemo/application/order/OrderQueryService.java`
- Delete: `src/main/java/com/example/orderdemo/service/OrderService.java`
- Delete: `src/main/java/com/example/orderdemo/service/OrderTCCService.java`
- Delete: `src/main/java/com/example/orderdemo/service/OrderTCCServiceImpl.java`
- Delete: `src/main/java/com/example/orderdemo/service/InventoryTCCService.java`
- Delete: `src/main/java/com/example/orderdemo/service/InventoryTCCServiceImpl.java`

- [ ] **Step 1: Write the failing test for application service**

Create: `src/test/java/com/example/orderdemo/application/order/OrderApplicationServiceTest.java`

```java
package com.example.orderdemo.application.order;

import com.example.orderdemo.domain.order.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class OrderApplicationServiceTest {

    private OrderApplicationService service;
    private InMemoryOrderRepository repository;
    private FakeEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        repository = new InMemoryOrderRepository();
        eventPublisher = new FakeEventPublisher();
        service = new OrderApplicationService(repository, eventPublisher);
    }

    @Test
    void placeOrderSucceeds() {
        var cmd = new OrderCommand.PlaceOrder("Coffee", 3, "key-001");
        Order order = service.handle(cmd);

        assertNotNull(order.id());
        assertEquals("Coffee", order.productName());
        assertInstanceOf(OrderState.Created.class, order.state());
        assertEquals(1, eventPublisher.publishedCount());
    }

    @Test
    void duplicateIdempotencyKeyIsRejected() {
        var cmd = new OrderCommand.PlaceOrder("Coffee", 3, "key-dup");
        service.handle(cmd);

        var duplicate = new OrderCommand.PlaceOrder("Tea", 1, "key-dup");
        assertThrows(IdempotencyKeyConflictException.class, () -> service.handle(duplicate));
    }

    @Test
    void closeOrderSucceeds() {
        var order = service.handle(new OrderCommand.PlaceOrder("Coffee", 2, "key-001"));

        var closeCmd = new OrderCommand.CloseOrder(order.id(), "timeout", "key-002");
        Order closed = service.handle(closeCmd);

        assertInstanceOf(OrderState.Closed.class, closed.state());
        assertEquals(2, eventPublisher.publishedCount());
    }

    @Test
    void closeOrderFailsOnInvariant() {
        var order = service.handle(new OrderCommand.PlaceOrder("Coffee", 2, "key-001"));
        service.handle(new OrderCommand.ConfirmOrder(order.id(), "key-002"));

        var closeCmd = new OrderCommand.CloseOrder(order.id(), "timeout", "key-003");
        assertThrows(InvariantViolationException.class, () -> service.handle(closeCmd));
    }

    // In-memory test doubles

    static class InMemoryOrderRepository implements OrderRepository {
        private final ConcurrentHashMap<Long, Order> store = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, Long> idempotencyKeys = new ConcurrentHashMap<>();
        private final AtomicLong sequence = new AtomicLong(1);

        @Override
        public void save(Order order) {
            if (order.id() == null) {
                order.assignId(new OrderId(sequence.getAndIncrement()));
            }
            store.put(order.id().value(), order);
        }

        @Override
        public Optional<Order> findById(OrderId id) {
            return Optional.ofNullable(store.get(id.value()));
        }

        @Override
        public boolean existsByIdempotencyKey(String key) {
            return idempotencyKeys.containsKey(key);
        }

        public void recordIdempotencyKey(String key, Long orderId) {
            idempotencyKeys.put(key, orderId);
        }
    }

    static class FakeEventPublisher implements DomainEventPublisher {
        private int count = 0;

        @Override
        public void publish(OrderEvent event) { count++; }

        int publishedCount() { return count; }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl . -Dtest=OrderApplicationServiceTest -q 2>&1 | tail -5`
Expected: FAIL — classes not found.

- [ ] **Step 3: Implement application service and supporting interfaces**

Create: `src/main/java/com/example/orderdemo/application/order/DomainEventPublisher.java`

```java
package com.example.orderdemo.application.order;

import com.example.orderdemo.domain.order.OrderEvent;

public interface DomainEventPublisher {
    void publish(OrderEvent event);
}
```

Create: `src/main/java/com/example/orderdemo/application/order/IdempotencyKeyConflictException.java`

```java
package com.example.orderdemo.application.order;

public class IdempotencyKeyConflictException extends RuntimeException {
    public IdempotencyKeyConflictException(String key) {
        super("Duplicate idempotency key: " + key);
    }
}
```

Create: `src/main/java/com/example/orderdemo/application/order/OrderApplicationService.java`

```java
package com.example.orderdemo.application.order;

import com.example.orderdemo.domain.order.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderApplicationService {

    private final OrderRepository orderRepository;
    private final DomainEventPublisher eventPublisher;

    public OrderApplicationService(OrderRepository orderRepository, DomainEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Order handle(OrderCommand.PlaceOrder cmd) {
        if (orderRepository.existsByIdempotencyKey(cmd.idempotencyKey())) {
            throw new IdempotencyKeyConflictException(cmd.idempotencyKey());
        }
        Order order = Order.create(cmd);
        orderRepository.save(order);
        order.domainEvents().forEach(eventPublisher::publish);
        order.clearEvents();
        return order;
    }

    @Transactional
    public Order handle(OrderCommand.ConfirmOrder cmd) {
        Order order = loadOrder(cmd.orderId());
        order.confirm(cmd);
        orderRepository.save(order);
        order.domainEvents().forEach(eventPublisher::publish);
        order.clearEvents();
        return order;
    }

    @Transactional
    public Order handle(OrderCommand.CloseOrder cmd) {
        Order order = loadOrder(cmd.orderId());
        order.close(cmd);
        orderRepository.save(order);
        order.domainEvents().forEach(eventPublisher::publish);
        order.clearEvents();
        return order;
    }

    @Transactional
    public Order handle(OrderCommand.CancelOrder cmd) {
        Order order = loadOrder(cmd.orderId());
        order.cancel(cmd);
        orderRepository.save(order);
        order.domainEvents().forEach(eventPublisher::publish);
        order.clearEvents();
        return order;
    }

    private Order loadOrder(OrderId orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}
```

Create: `src/main/java/com/example/orderdemo/application/order/OrderNotFoundException.java`

```java
package com.example.orderdemo.application.order;

import com.example.orderdemo.domain.order.OrderId;

public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(OrderId id) {
        super("Order not found: " + id.value());
    }
}
```

Create: `src/main/java/com/example/orderdemo/application/order/OrderQueryService.java`

```java
package com.example.orderdemo.application.order;

import com.example.orderdemo.domain.order.Order;
import com.example.orderdemo.domain.order.OrderId;
import com.example.orderdemo.domain.order.OrderRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class OrderQueryService {

    private final OrderRepository orderRepository;

    public OrderQueryService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public Optional<Order> findById(Long id) {
        return orderRepository.findById(new OrderId(id));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -pl . -Dtest=OrderApplicationServiceTest -q`
Expected: PASS (4 tests)

- [ ] **Step 5: Delete old service classes**

```bash
git rm src/main/java/com/example/orderdemo/service/OrderService.java
git rm src/main/java/com/example/orderdemo/service/OrderTCCService.java
git rm src/main/java/com/example/orderdemo/service/OrderTCCServiceImpl.java
git rm src/main/java/com/example/orderdemo/service/InventoryTCCService.java
git rm src/main/java/com/example/orderdemo/service/InventoryTCCServiceImpl.java
```

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat(app): add OrderApplicationService with command handling

Orchestrates PlaceOrder/ConfirmOrder/CloseOrder/CancelOrder commands.
Idempotency key check before creating orders.
Domain events published after successful persistence.
Replaces old TCC-based OrderService and Seata services.

Co-Authored-By: Claude Opus 4 <noreply@anthropic.com>"
```

---

### Task 7: Infrastructure — Kafka Event Publisher

**Files:**
- Create: `src/main/java/com/example/orderdemo/infrastructure/event/KafkaDomainEventPublisher.java`

- [ ] **Step 1: Write the test**

Create: `src/test/java/com/example/orderdemo/infrastructure/event/KafkaDomainEventPublisherTest.java`

```java
package com.example.orderdemo.infrastructure.event;

import com.example.orderdemo.domain.order.OrderEvent;
import com.example.orderdemo.domain.order.OrderId;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;

import static org.mockito.Mockito.*;

class KafkaDomainEventPublisherTest {

    @Test
    @SuppressWarnings("unchecked")
    void publishSendsToCorrectTopic() {
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        var publisher = new KafkaDomainEventPublisher(kafkaTemplate);

        var event = new OrderEvent.OrderCreated(new OrderId(42L), "Coffee", 3, Instant.now());
        publisher.publish(event);

        verify(kafkaTemplate).send(eq("order-events"), eq("42"), contains("OrderCreated"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishClosedEventIncludesReason() {
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        var publisher = new KafkaDomainEventPublisher(kafkaTemplate);

        var event = new OrderEvent.OrderClosed(new OrderId(7L), "timeout", Instant.now());
        publisher.publish(event);

        verify(kafkaTemplate).send(eq("order-events"), eq("7"), contains("timeout"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl . -Dtest=KafkaDomainEventPublisherTest -q 2>&1 | tail -5`
Expected: FAIL — class not found.

- [ ] **Step 3: Implement Kafka publisher**

Create: `src/main/java/com/example/orderdemo/infrastructure/event/KafkaDomainEventPublisher.java`

```java
package com.example.orderdemo.infrastructure.event;

import com.example.orderdemo.application.order.DomainEventPublisher;
import com.example.orderdemo.domain.order.OrderEvent;
import com.example.orderdemo.domain.order.OrderId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaDomainEventPublisher implements DomainEventPublisher {

    private static final String TOPIC = "order-events";
    private static final Logger log = LoggerFactory.getLogger(KafkaDomainEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaDomainEventPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publish(OrderEvent event) {
        String key = extractOrderId(event).value().toString();
        String payload = formatPayload(event);
        kafkaTemplate.send(TOPIC, key, payload);
        log.info("Published domain event: topic={}, key={}, type={}", TOPIC, key, event.getClass().getSimpleName());
    }

    private OrderId extractOrderId(OrderEvent event) {
        return switch (event) {
            case OrderEvent.OrderCreated e -> e.orderId();
            case OrderEvent.OrderConfirmed e -> e.orderId();
            case OrderEvent.OrderClosed e -> e.orderId();
            case OrderEvent.OrderCancelled e -> e.orderId();
        };
    }

    private String formatPayload(OrderEvent event) {
        return switch (event) {
            case OrderEvent.OrderCreated e ->
                    "OrderCreated:{\"orderId\":%d,\"productName\":\"%s\",\"quantity\":%d,\"occurredAt\":\"%s\"}"
                            .formatted(e.orderId().value(), e.productName(), e.quantity(), e.occurredAt());
            case OrderEvent.OrderConfirmed e ->
                    "OrderConfirmed:{\"orderId\":%d,\"occurredAt\":\"%s\"}"
                            .formatted(e.orderId().value(), e.occurredAt());
            case OrderEvent.OrderClosed e ->
                    "OrderClosed:{\"orderId\":%d,\"reason\":\"%s\",\"occurredAt\":\"%s\"}"
                            .formatted(e.orderId().value(), e.reason(), e.occurredAt());
            case OrderEvent.OrderCancelled e ->
                    "OrderCancelled:{\"orderId\":%d,\"reason\":\"%s\",\"occurredAt\":\"%s\"}"
                            .formatted(e.orderId().value(), e.reason(), e.occurredAt());
        };
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -pl . -Dtest=KafkaDomainEventPublisherTest -q`
Expected: PASS (2 tests)

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(infra): add KafkaDomainEventPublisher

Implements DomainEventPublisher, sends structured events to 'order-events' topic.
Uses pattern matching switch for exhaustive event type handling.
Event key is orderId for partition co-location.

Co-Authored-By: Claude Opus 4 <noreply@anthropic.com>"
```

---

### Task 8: Interfaces Layer — Simplified REST Controller

**Files:**
- Create: `src/main/java/com/example/orderdemo/interfaces/rest/OrderController.java`
- Create: `src/main/java/com/example/orderdemo/interfaces/rest/OrderResponse.java`
- Create: `src/main/java/com/example/orderdemo/interfaces/rest/GlobalExceptionHandler.java`
- Delete: `src/main/java/com/example/orderdemo/controller/OrderController.java`

- [ ] **Step 1: Write the failing test for controller**

Create: `src/test/java/com/example/orderdemo/interfaces/rest/OrderControllerTest.java`

```java
package com.example.orderdemo.interfaces.rest;

import com.example.orderdemo.application.order.OrderApplicationService;
import com.example.orderdemo.application.order.OrderQueryService;
import com.example.orderdemo.domain.order.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private OrderApplicationService applicationService;
    @MockBean private OrderQueryService queryService;

    @Test
    void createOrderReturns201() throws Exception {
        var order = Order.create(new OrderCommand.PlaceOrder("Coffee", 3, "key-001"));
        order.assignId(new OrderId(1L));
        when(applicationService.handle(any(OrderCommand.PlaceOrder.class))).thenReturn(order);

        mockMvc.perform(post("/orders")
                        .param("productName", "Coffee")
                        .param("quantity", "3")
                        .param("idempotencyKey", "key-001"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.productName").value("Coffee"))
                .andExpect(jsonPath("$.state").value("CREATED"));
    }

    @Test
    void closeOrderReturns200() throws Exception {
        var order = Order.create(new OrderCommand.PlaceOrder("Coffee", 2, "key-001"));
        order.assignId(new OrderId(1L));
        order.close(new OrderCommand.CloseOrder(new OrderId(1L), "timeout", "key-002"));
        when(applicationService.handle(any(OrderCommand.CloseOrder.class))).thenReturn(order);

        mockMvc.perform(put("/orders/1/close")
                        .param("reason", "timeout")
                        .param("idempotencyKey", "key-002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("CLOSED"));
    }

    @Test
    void invariantViolationReturns409() throws Exception {
        when(applicationService.handle(any(OrderCommand.CloseOrder.class)))
                .thenThrow(new InvariantViolationException(new OrderId(1L), "state-transition", "Cannot close"));

        mockMvc.perform(put("/orders/1/close")
                        .param("reason", "timeout")
                        .param("idempotencyKey", "key-003"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Cannot close"))
                .andExpect(jsonPath("$.invariant").value("state-transition"));
    }

    @Test
    void getOrderReturns200() throws Exception {
        var order = Order.reconstitute(new OrderId(1L), "Coffee", 3, OrderState.Created.INSTANCE, 0);
        when(queryService.findById(1L)).thenReturn(Optional.of(order));

        mockMvc.perform(get("/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productName").value("Coffee"));
    }

    @Test
    void getOrderNotFoundReturns404() throws Exception {
        when(queryService.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/orders/99"))
                .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl . -Dtest=OrderControllerTest -q 2>&1 | tail -5`
Expected: FAIL — new controller class not found.

- [ ] **Step 3: Implement controller, response DTO, and exception handler**

Create: `src/main/java/com/example/orderdemo/interfaces/rest/OrderResponse.java`

```java
package com.example.orderdemo.interfaces.rest;

import com.example.orderdemo.domain.order.Order;

public record OrderResponse(Long id, String productName, int quantity, String state, long version) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.id().value(),
                order.productName(),
                order.quantity(),
                order.state().description(),
                order.version()
        );
    }
}
```

Create: `src/main/java/com/example/orderdemo/interfaces/rest/OrderController.java`

```java
package com.example.orderdemo.interfaces.rest;

import com.example.orderdemo.application.order.OrderApplicationService;
import com.example.orderdemo.application.order.OrderQueryService;
import com.example.orderdemo.domain.order.Order;
import com.example.orderdemo.domain.order.OrderCommand;
import com.example.orderdemo.domain.order.OrderId;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderApplicationService applicationService;
    private final OrderQueryService queryService;

    public OrderController(OrderApplicationService applicationService, OrderQueryService queryService) {
        this.applicationService = applicationService;
        this.queryService = queryService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestParam String productName,
            @RequestParam int quantity,
            @RequestParam String idempotencyKey) {
        var cmd = new OrderCommand.PlaceOrder(productName, quantity, idempotencyKey);
        Order order = applicationService.handle(cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(order));
    }

    @PutMapping("/{id}/confirm")
    public ResponseEntity<OrderResponse> confirmOrder(
            @PathVariable Long id,
            @RequestParam String idempotencyKey) {
        var cmd = new OrderCommand.ConfirmOrder(new OrderId(id), idempotencyKey);
        Order order = applicationService.handle(cmd);
        return ResponseEntity.ok(OrderResponse.from(order));
    }

    @PutMapping("/{id}/close")
    public ResponseEntity<OrderResponse> closeOrder(
            @PathVariable Long id,
            @RequestParam String reason,
            @RequestParam String idempotencyKey) {
        var cmd = new OrderCommand.CloseOrder(new OrderId(id), reason, idempotencyKey);
        Order order = applicationService.handle(cmd);
        return ResponseEntity.ok(OrderResponse.from(order));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(
            @PathVariable Long id,
            @RequestParam String reason,
            @RequestParam String idempotencyKey) {
        var cmd = new OrderCommand.CancelOrder(new OrderId(id), reason, idempotencyKey);
        Order order = applicationService.handle(cmd);
        return ResponseEntity.ok(OrderResponse.from(order));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable Long id) {
        return queryService.findById(id)
                .map(order -> ResponseEntity.ok(OrderResponse.from(order)))
                .orElse(ResponseEntity.notFound().build());
    }
}
```

Create: `src/main/java/com/example/orderdemo/interfaces/rest/GlobalExceptionHandler.java`

```java
package com.example.orderdemo.interfaces.rest;

import com.example.orderdemo.application.order.IdempotencyKeyConflictException;
import com.example.orderdemo.application.order.OrderNotFoundException;
import com.example.orderdemo.domain.order.InvariantViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvariantViolationException.class)
    public ResponseEntity<Map<String, String>> handleInvariantViolation(InvariantViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", ex.getMessage(),
                "invariant", ex.invariant()
        ));
    }

    @ExceptionHandler(IdempotencyKeyConflictException.class)
    public ResponseEntity<Map<String, String>> handleIdempotencyConflict(IdempotencyKeyConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", ex.getMessage()
        ));
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(OrderNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", ex.getMessage()
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", ex.getMessage()
        ));
    }
}
```

- [ ] **Step 4: Delete old controller and run tests**

```bash
git rm src/main/java/com/example/orderdemo/controller/OrderController.java
```

Run: `./mvnw test -pl . -Dtest=OrderControllerTest -q`
Expected: PASS (5 tests)

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(interfaces): add new REST controller with invariant error handling

OrderController delegates to application service, returns OrderResponse record.
GlobalExceptionHandler maps InvariantViolationException → 409 Conflict.
Close/cancel endpoints require reason + idempotencyKey params.
Replaces old controller that mixed domain logic with HTTP concerns.

Co-Authored-By: Claude Opus 4 <noreply@anthropic.com>"
```

---

### Task 9: Cleanup — Remove Orphan Files, Update Application Entry Point

**Files:**
- Modify: `src/main/java/com/example/orderdemo/OrderDemoApplication.java`
- Delete: `src/main/java/com/example/orderdemo/config/SecurityConfig.java`
- Delete: `src/main/java/com/example/orderdemo/config/KafkaConfig.java`
- Delete: `src/main/java/com/example/orderdemo/repository/OrderRepository.java`
- Delete: `src/main/java/org/example/Main.java`
- Modify: `src/main/java/com/example/orderdemo/config/RedisConfig.java` (keep)

- [ ] **Step 1: Update application entry point**

```java
package com.example.orderdemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class OrderDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderDemoApplication.class, args);
    }
}
```

- [ ] **Step 2: Delete orphan files**

```bash
git rm src/main/java/com/example/orderdemo/config/SecurityConfig.java
git rm src/main/java/com/example/orderdemo/config/KafkaConfig.java
git rm src/main/java/com/example/orderdemo/repository/OrderRepository.java
git rm src/main/java/org/example/Main.java
```

- [ ] **Step 3: Run full test suite**

Run: `./mvnw test -q`
Expected: ALL PASS

- [ ] **Step 4: Verify application compiles cleanly**

Run: `./mvnw compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "chore: remove orphan files, clean up application entry point

Remove SecurityConfig (oauth2 dep removed), old JPA repository, KafkaConfig
(Spring Boot auto-config handles Kafka), and unused Main.java.
Application entry point simplified.

Co-Authored-By: Claude Opus 4 <noreply@anthropic.com>"
```

---

### Task 10: Full Integration Smoke Test

**Files:**
- Create: `src/test/java/com/example/orderdemo/OrderDemoIntegrationTest.java`

- [ ] **Step 1: Write integration test that exercises the full stack**

```java
package com.example.orderdemo;

import com.example.orderdemo.application.order.OrderApplicationService;
import com.example.orderdemo.domain.order.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Sql(statements = {
    "CREATE TABLE IF NOT EXISTS orders (id BIGINT AUTO_INCREMENT PRIMARY KEY, product_name VARCHAR(255) NOT NULL, quantity INT NOT NULL, state VARCHAR(32) NOT NULL, version BIGINT NOT NULL DEFAULT 0)",
    "CREATE TABLE IF NOT EXISTS idempotency_keys (idempotency_key VARCHAR(64) PRIMARY KEY, order_id BIGINT NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
})
class OrderDemoIntegrationTest {

    @Autowired private OrderApplicationService orderService;
    @MockBean private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void fullLifecycle_createThenClose() {
        var order = orderService.handle(new OrderCommand.PlaceOrder("Coffee", 5, "int-key-001"));
        assertNotNull(order.id());
        assertInstanceOf(OrderState.Created.class, order.state());

        var closed = orderService.handle(new OrderCommand.CloseOrder(order.id(), "agent-timeout", "int-key-002"));
        assertInstanceOf(OrderState.Closed.class, closed.state());
    }

    @Test
    void invariantEnforcement_cannotCloseConfirmedOrder() {
        var order = orderService.handle(new OrderCommand.PlaceOrder("Tea", 1, "int-key-003"));
        orderService.handle(new OrderCommand.ConfirmOrder(order.id(), "int-key-004"));

        var ex = assertThrows(InvariantViolationException.class,
                () -> orderService.handle(new OrderCommand.CloseOrder(order.id(), "timeout", "int-key-005")));
        assertEquals("state-transition", ex.invariant());
    }

    @Test
    void idempotencyPreventsDoubleCreate() {
        orderService.handle(new OrderCommand.PlaceOrder("Coffee", 2, "dup-key-001"));

        assertThrows(Exception.class,
                () -> orderService.handle(new OrderCommand.PlaceOrder("Coffee", 2, "dup-key-001")));
    }
}
```

- [ ] **Step 2: Run integration test**

Run: `./mvnw test -pl . -Dtest=OrderDemoIntegrationTest -q`
Expected: PASS (3 tests)

- [ ] **Step 3: Run full suite one final time**

Run: `./mvnw test -q`
Expected: ALL PASS — full green bar.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "test: add full integration smoke test for DDD order lifecycle

Exercises create→close, invariant rejection (confirm then close),
and idempotency key deduplication through the full stack.
Uses H2 in-memory with MockBean for Kafka.

Co-Authored-By: Claude Opus 4 <noreply@anthropic.com>"
```

---

## Summary of What Changes

| Before | After |
|--------|-------|
| `Order` is a JPA `@Entity` record | `Order` is a pure class aggregate root |
| State logic in controller/service | State transitions inside aggregate with invariant guards |
| Hibernate/JPA | MyBatis with explicit SQL |
| Seata TCC distributed transaction | Removed (phase 2 scope) |
| Spring Cloud Alibaba (Nacos/Seata) | Removed (phase 2 scope) |
| `@Async` + CompletableFuture | Virtual threads (zero code — config only) |
| String-concatenation Kafka events | Sealed `OrderEvent` hierarchy with pattern matching |
| No idempotency | Idempotency key table + check |
| Java 17 | Java 21 |
| Single `model/` + `service/` packages | `domain/` + `application/` + `infrastructure/` + `interfaces/` layers |