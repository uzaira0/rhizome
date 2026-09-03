package com.geekbeast.rhizome.configuration.jetty;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Optional;

/**
 * Security hardening configuration for Jetty server to prevent DoS attacks,
 * request smuggling, and resource exhaustion.
 *
 * @author uzaira0
 */
public class HardeningConfiguration {
    // Property names for JSON serialization
    protected static final String IDLE_TIMEOUT_PROPERTY = "idle-timeout";
    protected static final String REQUEST_HEADER_SIZE_PROPERTY = "request-header-size";
    protected static final String RESPONSE_HEADER_SIZE_PROPERTY = "response-header-size";
    protected static final String MAX_FORM_CONTENT_SIZE_PROPERTY = "max-form-content-size";
    protected static final String MAX_FORM_KEYS_PROPERTY = "max-form-keys";
    protected static final String MIN_THREADS_PROPERTY = "min-threads";
    protected static final String MAX_THREADS_PROPERTY = "max-threads";
    protected static final String SEND_SERVER_VERSION_PROPERTY = "send-server-version";
    protected static final String SEND_DATE_HEADER_PROPERTY = "send-date-header";
    protected static final String OUTPUT_BUFFER_SIZE_PROPERTY = "output-buffer-size";

    // Secure defaults to prevent DoS attacks
    // Idle timeout: 30 seconds (prevents Slow Loris attacks)
    protected static final long IDLE_TIMEOUT_DEFAULT = 30_000L;
    // Request header size: 8KB (prevents header bomb attacks)
    protected static final int REQUEST_HEADER_SIZE_DEFAULT = 8 * 1024;
    // Response header size: 8KB
    protected static final int RESPONSE_HEADER_SIZE_DEFAULT = 8 * 1024;
    // Max form content size: 10MB (prevents large payload DoS)
    protected static final int MAX_FORM_CONTENT_SIZE_DEFAULT = 10 * 1024 * 1024;
    // Max form keys: 1000 (prevents hash collision attacks)
    protected static final int MAX_FORM_KEYS_DEFAULT = 1000;
    // Thread pool bounds to prevent resource exhaustion
    protected static final int MIN_THREADS_DEFAULT = 8;
    protected static final int MAX_THREADS_DEFAULT = 200;
    // Don't send server version to prevent information disclosure
    protected static final boolean SEND_SERVER_VERSION_DEFAULT = false;
    // Send date header (standard HTTP behavior)
    protected static final boolean SEND_DATE_HEADER_DEFAULT = true;
    // Output buffer size: 32KB
    protected static final int OUTPUT_BUFFER_SIZE_DEFAULT = 32 * 1024;

    protected final long idleTimeout;
    protected final int requestHeaderSize;
    protected final int responseHeaderSize;
    protected final int maxFormContentSize;
    protected final int maxFormKeys;
    protected final int minThreads;
    protected final int maxThreads;
    protected final boolean sendServerVersion;
    protected final boolean sendDateHeader;
    protected final int outputBufferSize;

    @JsonCreator
    public HardeningConfiguration(
            @JsonProperty(IDLE_TIMEOUT_PROPERTY) Optional<Long> idleTimeout,
            @JsonProperty(REQUEST_HEADER_SIZE_PROPERTY) Optional<Integer> requestHeaderSize,
            @JsonProperty(RESPONSE_HEADER_SIZE_PROPERTY) Optional<Integer> responseHeaderSize,
            @JsonProperty(MAX_FORM_CONTENT_SIZE_PROPERTY) Optional<Integer> maxFormContentSize,
            @JsonProperty(MAX_FORM_KEYS_PROPERTY) Optional<Integer> maxFormKeys,
            @JsonProperty(MIN_THREADS_PROPERTY) Optional<Integer> minThreads,
            @JsonProperty(MAX_THREADS_PROPERTY) Optional<Integer> maxThreads,
            @JsonProperty(SEND_SERVER_VERSION_PROPERTY) Optional<Boolean> sendServerVersion,
            @JsonProperty(SEND_DATE_HEADER_PROPERTY) Optional<Boolean> sendDateHeader,
            @JsonProperty(OUTPUT_BUFFER_SIZE_PROPERTY) Optional<Integer> outputBufferSize) {
        this.idleTimeout = idleTimeout.orElse(IDLE_TIMEOUT_DEFAULT);
        this.requestHeaderSize = requestHeaderSize.orElse(REQUEST_HEADER_SIZE_DEFAULT);
        this.responseHeaderSize = responseHeaderSize.orElse(RESPONSE_HEADER_SIZE_DEFAULT);
        this.maxFormContentSize = maxFormContentSize.orElse(MAX_FORM_CONTENT_SIZE_DEFAULT);
        this.maxFormKeys = maxFormKeys.orElse(MAX_FORM_KEYS_DEFAULT);
        this.minThreads = minThreads.orElse(MIN_THREADS_DEFAULT);
        this.maxThreads = maxThreads.orElse(MAX_THREADS_DEFAULT);
        this.sendServerVersion = sendServerVersion.orElse(SEND_SERVER_VERSION_DEFAULT);
        this.sendDateHeader = sendDateHeader.orElse(SEND_DATE_HEADER_DEFAULT);
        this.outputBufferSize = outputBufferSize.orElse(OUTPUT_BUFFER_SIZE_DEFAULT);
    }

    /**
     * Default constructor with secure defaults.
     */
    public HardeningConfiguration() {
        this(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
    }

    @JsonProperty(IDLE_TIMEOUT_PROPERTY)
    public long getIdleTimeout() {
        return idleTimeout;
    }

    @JsonProperty(REQUEST_HEADER_SIZE_PROPERTY)
    public int getRequestHeaderSize() {
        return requestHeaderSize;
    }

    @JsonProperty(RESPONSE_HEADER_SIZE_PROPERTY)
    public int getResponseHeaderSize() {
        return responseHeaderSize;
    }

    @JsonProperty(MAX_FORM_CONTENT_SIZE_PROPERTY)
    public int getMaxFormContentSize() {
        return maxFormContentSize;
    }

    @JsonProperty(MAX_FORM_KEYS_PROPERTY)
    public int getMaxFormKeys() {
        return maxFormKeys;
    }

    @JsonProperty(MIN_THREADS_PROPERTY)
    public int getMinThreads() {
        return minThreads;
    }

    @JsonProperty(MAX_THREADS_PROPERTY)
    public int getMaxThreads() {
        return maxThreads;
    }

    @JsonProperty(SEND_SERVER_VERSION_PROPERTY)
    public boolean isSendServerVersion() {
        return sendServerVersion;
    }

    @JsonProperty(SEND_DATE_HEADER_PROPERTY)
    public boolean isSendDateHeader() {
        return sendDateHeader;
    }

    @JsonProperty(OUTPUT_BUFFER_SIZE_PROPERTY)
    public int getOutputBufferSize() {
        return outputBufferSize;
    }
}
