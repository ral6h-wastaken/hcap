# HCAP - Http Client Annotation Processor

**hcap** is a Java annotation processor that generates compile-time HTTP client implementations from annotated interfaces. It eliminates boilerplate by generating a fully working `HttpClient`-based implementation class directly during compilation — no runtime proxies, no reflection.

---

## Table of Contents

- [How It Works](#how-it-works)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Annotations Reference](#annotations-reference)
  - [@Client](#client)
  - [@Request](#request)
  - [@PathParam](#pathparam)
  - [@QueryParam](#queryparam)
  - [@Header](#header)
  - [@Body](#body)
- [ClientResponse](#clientresponse)
- [Generated Code](#generated-code)
- [Validation Rules](#validation-rules)
- [Known Limitations & TODOs](#known-limitations--todos)
- [Building](#building)

---

## How It Works

At compile time, `HCAP` scans all interfaces annotated with `@Client`. For each one, it generates a concrete `*Impl` class in the same package that:

1. Implements the annotated interface and `AutoCloseable`.
2. Manages an internal `HttpClient` with a configurable connection timeout and a work-stealing executor.
3. Translates every `@Request`-annotated method into a blocking HTTP call using Java's `java.net.http` API.
4. Resolves path parameters, query parameters, headers, and a request body from the method arguments at runtime.
5. Returns a `ClientResponse` record containing the status code, response headers, and an optional response body string.

Because all code generation happens at compile time via `javac`'s annotation processing pipeline (JSR-269), there is zero runtime overhead from reflection or proxy generation.

---

## Requirements

| Requirement | Version |
|---|---|
| Java | 21+ |
| Maven | 3.x |
| `com.google.auto.service` | 1.1.1 |

---

## Installation

Add the processor as a dependency in your `pom.xml`. Because it is a source-only processor (`RetentionPolicy.SOURCE`), it only needs to be on the annotation processor path at compile time.

```xml
<dependency>
  <groupId>com.ral6h</groupId>
  <artifactId>hcap</artifactId>
  <version>1.0-SNAPSHOT</version>
</dependency>
```

The processor registers itself automatically via `META-INF/services/javax.annotation.processing.Processor` (managed by `@AutoService`), so no additional compiler flags are required.

---

## Quick Start

Define an interface, annotate it, and use the generated `*Impl` class:

```java
package com.example;

import com.ral6h.hcap.annotation.*;
import com.ral6h.hcap.model.ClientResponse;

@Client(
    scheme = Client.HttpScheme.HTTPS,
    host = "api.example.com",
    port = 443,
    basePath = "/v1",
    connectTimeout = 10
)
public interface UserClient {

    @Request(method = Request.RequestMethod.GET, endpoint = "/users/{userId}", readTimeout = 5)
    ClientResponse getUser(
        @PathParam(name = "userId") String userId,
        @Header(name = "Authorization", required = true) String authHeader
    );

    @Request(method = Request.RequestMethod.POST, endpoint = "/users", readTimeout = 10)
    ClientResponse createUser(
        @Header(name = "Content-Type", required = true) String contentType,
        @Body(contentType = "application/json") String body
    );

    @Request(method = Request.RequestMethod.DELETE, endpoint = "/users/{userId}")
    ClientResponse deleteUser(
        @PathParam(name = "userId") String userId
    );
}
```

At compile time, a class `UserClientImpl` is generated in the same package. Use it like this:

```java
try (var client = new UserClientImpl()) {
    ClientResponse response = client.getUser("42", "Bearer my-token");

    System.out.println(response.status());           // e.g. 200
    System.out.println(response.body().orElse(""));  // response body string
    System.out.println(response.headers());          // Map<String, List<String>>
}
```

Because `UserClientImpl` implements `AutoCloseable`, it is safe to use in a try-with-resources block, which will shut down its internal executor and `HttpClient` cleanly.

---

## Annotations Reference

### `@Client`

**Target:** Interface type declaration  
**Retention:** `SOURCE`

Marks an interface as an HTTP client. The processor generates a `<InterfaceName>Impl` class in the same package.

| Attribute | Type | Default | Description |
|---|---|---|---|
| `host` | `String` | *(required)* | The target server hostname or IP address. |
| `scheme` | `HttpScheme` | `HTTP` | The HTTP scheme to use. Either `HttpScheme.HTTP` or `HttpScheme.HTTPS`. |
| `port` | `int` | `-1` | The server port. Defaults to `80` for HTTP and `443` for HTTPS when set to `-1`. |
| `basePath` | `String` | `""` | A path prefix prepended to every request endpoint. |
| `connectTimeout` | `long` | `30` | Connection timeout in seconds. |
| `async` | `boolean` | `false` | ⚠️ Not yet supported. Setting this to `true` will cause compilation to fail. |

**Example:**
```java
@Client(
    scheme = Client.HttpScheme.HTTPS,
    host = "api.example.com",
    basePath = "/api/v2",
    connectTimeout = 15
)
public interface ProductClient { ... }
```

---

### `@Request`

**Target:** Method  
**Retention:** `SOURCE`

Marks an interface method as an HTTP request. Every annotated method must be `public abstract` and must return `ClientResponse`.

| Attribute | Type | Default | Description |
|---|---|---|---|
| `method` | `RequestMethod` | `GET` | The HTTP method. One of `GET`, `POST`, `PUT`, `DELETE`, `HEAD`. |
| `endpoint` | `String` | `""` | The path relative to `@Client.basePath`. Supports `{paramName}` placeholders for path parameters. |
| `readTimeout` | `long` | `30` | Per-request read timeout in seconds. |

**Example:**
```java
@Request(method = Request.RequestMethod.PUT, endpoint = "/products/{id}", readTimeout = 20)
ClientResponse updateProduct(
    @PathParam(name = "id") String id,
    @Body String body
);
```

---

### `@PathParam`

**Target:** Method parameter  
**Retention:** `SOURCE`

Binds a method parameter to a `{placeholder}` in the `@Request` endpoint path. The `name` must match a placeholder name exactly (case-sensitive).

| Attribute | Type | Default | Description |
|---|---|---|---|
| `name` | `String` | *(required)* | The name of the path placeholder (without curly braces). |

**Example:**
```java
// endpoint = "/orders/{orderId}/items/{itemId}"
ClientResponse getOrderItem(
    @PathParam(name = "orderId") String orderId,
    @PathParam(name = "itemId") String itemId
);
```

---

### `@QueryParam`

**Target:** Method parameter  
**Retention:** `SOURCE`

Declares a method parameter as an HTTP query parameter to be appended to the request URL.

| Attribute | Type | Default | Description |
|---|---|---|---|
| `name` | `String` | *(required)* | The query parameter key name. |
| `required` | `boolean` | `true` | Whether the parameter is mandatory. |

---

### `@Header`

**Target:** Method parameter  
**Retention:** `SOURCE`

Binds a method parameter to an HTTP request header.

| Attribute | Type | Default | Description |
|---|---|---|---|
| `name` | `String` | *(required)* | The HTTP header name (e.g. `"Authorization"`, `"Content-Type"`). |
| `required` | `boolean` | `true` | If `true`, the header value is validated as non-null at call time; an `IllegalArgumentException` is thrown if it is null.

**Example:**
```java
ClientResponse search(
    @Header(name = "Authorization", required = true) String token,
    @Header(name = "X-Trace-Id", required = false) String traceId  // not yet emitted
);
```

---

### `@Body`

**Target:** Method parameter  
**Retention:** `SOURCE`

Marks a method parameter as the HTTP request body. Only `String` parameters are supported. At most **one** parameter per method may be annotated with `@Body`.

| Attribute | Type | Default | Description |
|---|---|---|---|
| `contentType` | `String` | `"text/plain"` | The MIME type of the body. Informational only; the value is not currently used to set a `Content-Type` header automatically. |
| `required` | `boolean` | `true` | Whether the body is required. Informational only in the current implementation. |

**Example:**
```java
@Request(method = Request.RequestMethod.POST, endpoint = "/messages")
ClientResponse sendMessage(
    @Body(contentType = "application/json") String jsonPayload
);
```

For methods that do not have a `@Body` parameter, the processor automatically uses `BodyPublishers.noBody()`.

---

## ClientResponse

All `@Request` methods must return `com.ral6h.hcap.model.ClientResponse`, a Java record that wraps the HTTP response.

```java
public record ClientResponse(
    int status,
    Map<String, List<String>> headers,
    Optional<String> body
) { ... }
```

| Field | Type | Description |
|---|---|---|
| `status` | `int` | The HTTP response status code (100–599). |
| `headers` | `Map<String, List<String>>` | The response headers as returned by `HttpResponse.headers().map()`. |
| `body` | `Optional<String>` | The response body as a string, if present. |

**Convenience method:**

```java
boolean isError()  // Returns true if status is in the 400–599 range
```

**On transport errors**, the generated client returns a synthetic `ClientResponse(500, Map.of(), Optional.empty())` rather than throwing. On `InterruptedException`, the thread's interrupt flag is restored and `null` is returned.

---

## Generated Code

Given the interface:

```java
@Client(host = "localhost", port = 8080, basePath = "/api")
public interface GreetingClient {
    @Request(endpoint = "/hello/{name}", readTimeout = 5)
    ClientResponse greet(@PathParam(name = "name") String name);
}
```

The processor generates a class like the following (simplified):

```java
@Generated(value = "com.ral6h.hcap.hcapProcessor", date = "...")
public final class GreetingClientImpl implements GreetingClient, AutoCloseable {

    private final HttpClient client;
    private final ExecutorService executor = Executors.newWorkStealingPool();

    private final String scheme = "http";
    private final String host   = "localhost";
    private final int    port   = 8080;
    private final String basePath = "/api";

    public GreetingClientImpl() {
        this.client = HttpClient.newBuilder()
            .version(Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(30))
            .executor(this.executor)
            .build();
    }

    @Override
    public void close() {
        this.executor.close();
        this.client.close();
    }

    @Override
    public ClientResponse greet(String name) {
        String path  = "/api/hello/%s".formatted(name);
        String query = "";
        Map<String, Object> optionalQueryParams = new HashMap<>();

        //optional qp map population

        for (final var entry : optionalQueryParams.entrySet()) {
          if (entry.getValue() != null) {
            query += "&%s=%s".formatted(entry.getKey(), entry.getValue());
          }
        }

        List<String> headersList = new ArrayList<>();
        Map<String, Object> requiredHeadersMap = new HashMap<>();
        Map<String, Object> optionalHeadersMap = new HashMap<>();

        //required headers map population
        
        //optional headers map population
        

        for (final var requiredHeader : requiredHeadersMap.entrySet()) {
          headersList.add(requiredHeader.getKey());
          headersList.add(
            Optional.ofNullable(requiredHeader.getValue())
              .map(Object::toString)
              .orElseThrow(IllegalArgumentException::new)
          );
        }

        for (final var optionalHeader : optionalHeadersMap.entrySet()) {
          if (optionalHeader.getValue() != null) {
            headersList.add(optionalHeader.getKey());
            headersList.add(optionalHeader.getValue().toString());
          }
        }

        String[] headers = headersList.toArray(new String[] {});
        int readTimeout = 5;

        URI uri;
        try {
            uri = new URI(this.scheme, null, this.host, this.port, path, query, null);
        } catch (URISyntaxException e) {
            return new ClientResponse(500, Map.of(), Optional.empty());
        }

        final var requestBuilder = HttpRequest.newBuilder(uri)
            .GET()
            .timeout(Duration.ofSeconds(readTimeout));

        if (headers.length > 0) requestBuilder.headers(headers);

        final var request = requestBuilder.build();

        try {
            final var httpResponse = client.send(request, BodyHandlers.ofString());
            return new ClientResponse(
                httpResponse.statusCode(),
                httpResponse.headers().map(),
                Optional.ofNullable(httpResponse.body())
            );
        } catch (IOException e1) {
            return new ClientResponse(500, Map.of(), Optional.empty());
        } catch (InterruptedException e1) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
```

The generated file will appear under `target/generated-sources/annotations/` after compilation.

---

## Validation Rules

The processor enforces the following rules at **compile time** and emits a compilation error if they are violated:

| Rule | Error Message |
|---|---|
| `@Client` can only be applied to an interface | `@Client can only be used on an interface` |
| Non-`@Request` methods in a `@Client` interface must be `default` | `Only default methods can not be annotated with @Request in a @Client` |
| `@Request` methods must be `public abstract` | `Only abstract methods can be annotated with @Request in a @Client` |
| `@Request` methods must return `ClientResponse` | `Methods annotated with @Request must have return type com.ral6h.hcap.model.ClientResponse` |
| Only one `@Body` parameter is allowed per method | `At most 1 method parameter can be annotated with @Body` |
| `@Body` parameters must be of type `String` | `Only String parameters can be annotated with @Body` |

---

## Known Limitations & TODOs

The following features are documented in the source as incomplete or not yet implemented:

- **`@Body.contentType` is not used to set a header.** The content type declared on `@Body` is informational and does not automatically generate a `Content-Type` header.
- **`@Client.async` is unsupported.** Setting `async = true` will cause a compilation failure. Async support is planned for a future version.
- **Duplicate query params names.** Setting two query params with the same name will cause compilation to fail (noted as `// TODO: add support for multiple query params with the same name`).
- **Duplicate header names.** Setting two headers with the same name will cause compilation to fail (noted as `// TODO: add support for multiple headers with the same name`).
- **HTTP/1.1 only.** The generated client always uses `Version.HTTP_1_1`.
- **Blocking I/O only.** All requests are sent synchronously via `HttpClient.send(...)`.

---

## Building

```bash
# Clone or unzip the project
cd hcap/processor

# Build and install to local Maven repository
mvn clean install

# Run tests
mvn test
```

The compiled JAR will be located at `target/client-gen-1.0-SNAPSHOT.jar`.
