package com.ral6h.hcap.model;

import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;

import com.ral6h.hcap.annotation.Client;
import com.ral6h.hcap.annotation.Client.HttpScheme;

/**
 * Immutable configuration class for HTTP client settings.
 *
 * <p>
 * Encapsulates all parameters required to establish and manage an HTTP client
 * connection,
 * including host, path, protocol version, scheme, timeout, and execution mode.
 *
 * <p>
 * Instances are created via a two-step builder that enforces the presence of a
 * host
 * before any optional parameters can be set:
 *
 * <pre>{@code
 * ClientConfig config = ClientConfig.builder()
 *     .host("api.example.com")
 *     .basePath("/v1")
 *     .scheme(Client.HttpScheme.HTTPS)
 *     .version(HttpClient.Version.HTTP_2)
 *     .port(8080)
 *     .connectTimeout(10L)
 *     .build();
 * }</pre>
 *
 * <p>
 * Default values applied when optional parameters are omitted:
 * <ul>
 * <li>{@code basePath} – {@code ""} (empty string)</li>
 * <li>{@code version} – {@link HttpClient.Version#HTTP_1_1}</li>
 * <li>{@code scheme} – {@link Client.HttpScheme#HTTP}</li>
 * <li>{@code port}–
 * {@code 80 if scheme = Client.HttpScheme#HTTP, 443 if Client.HttpScheme#HTTPS}
 * </li>
 * <li>{@code connectTimeout}– {@code 30} seconds</li>
 * <li>{@code async} – {@code false}</li>
 * </ul>
 *
 * @see ClientConfig.MissingHostBuilder
 * @see ClientConfig.MissingHostBuilder.Builder
 */
public class ClientConfig {
  private final String host;
  private final String basePath;
  private final HttpClient.Version version;
  private final Client.HttpScheme scheme;
  private final int port;
  private final long connectTimeout;
  private final boolean async;

  public int getPort() {
    return port;
  }

  public String getHost() {
    return host;
  }

  public String getBasePath() {
    return basePath;
  }

  public HttpClient.Version getVersion() {
    return version;
  }

  public Client.HttpScheme getScheme() {
    return scheme;
  }

  public long getConnectTimeout() {
    return connectTimeout;
  }

  public boolean isAsync() {
    return async;
  }

  /**
   * Private constructor — use {@link #builder()} to obtain a
   * {@link MissingHostBuilder}.
   */
  private ClientConfig(String host, String basePath, Version version, HttpScheme scheme, int port, long connectTimeout,
      boolean async) {
    this.host = host;
    this.basePath = basePath;
    this.version = version;
    this.scheme = scheme;
    this.port = port;
    this.connectTimeout = connectTimeout;
    this.async = async;
  }

  /**
   * Returns the first stage of the builder, which requires a host to be
   * supplied before any other configuration can be set.
   *
   * <p>
   * This design guarantees that a {@link ClientConfig} can never be built
   * without a host, making the missing-host error a compile-time rather than
   * a runtime concern.
   *
   * @return a new {@link MissingHostBuilder} instance
   */
  public static MissingHostBuilder builder() {
    return new MissingHostBuilder();
  }

  /**
   * First stage of the {@link ClientConfig} builder.
   *
   * <p>
   * The sole responsibility of this class is to capture the mandatory
   * {@code host} parameter and transition to the fully-featured
   * {@link Builder}, where all optional parameters can be configured.
   *
   * <pre>{@code
   * ClientConfig.MissingHostBuilder stage1 = ClientConfig.builder();
   * ClientConfig.MissingHostBuilder.Builder stage2 = stage1.host("api.example.com");
   * ClientConfig config = stage2.scheme(HttpScheme.HTTPS).build();
   * }</pre>
   */
  public static class MissingHostBuilder {

    public Builder host(String host) {
      return this.new Builder(host);
    }

    /**
     * Second stage of the {@link ClientConfig} builder.
     *
     * <p>
     * All parameters except {@code host} are optional; sensible defaults
     * are applied for any value not explicitly set (see {@link ClientConfig}
     * class-level documentation for the full list).
     *
     * <p>
     * This class is a non-static inner class of {@link MissingHostBuilder}
     * to prevent direct instantiation and enforce the staged-builder contract.
     */
    public class Builder {
      private final String _host;
      private String _basePath = "";
      private HttpClient.Version _version = Version.HTTP_1_1;
      private Client.HttpScheme _scheme = HttpScheme.HTTP;
      private int _port = 80;
      private boolean _portSet = false;
      private long _connectTimeout = 30_000l;
      private boolean _async = false;

      private Builder(String host) {
        this._host = host;
      }

      public Builder basePath(String basePath) {
        this._basePath = basePath;
        return this;
      }

      public Builder version(Version version) {
        this._version = version;
        return this;
      }

      public Builder port(int port) {
        this._port = port;
        this._portSet = true;
        return this;
      }

      public Builder scheme(HttpScheme scheme) {
        this._scheme = scheme;
        if (!this._portSet && HttpScheme.HTTPS.equals(scheme))
          this._port = 443;

        return this;
      }

      public Builder connectTimeout(long connectTimeout) {
        this._connectTimeout = connectTimeout;
        return this;
      }

      public ClientConfig build() {
        return new ClientConfig(_host, _basePath, _version, _scheme, _port, _connectTimeout, _async);
      }
    }
  }
}
