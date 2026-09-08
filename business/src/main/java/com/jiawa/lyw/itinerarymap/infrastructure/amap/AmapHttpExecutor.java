package com.jiawa.lyw.itinerarymap.infrastructure.amap;

import com.jiawa.lyw.itinerarymap.infrastructure.amap.dto.AmapResponseModels.AmapProviderException;
import com.jiawa.lyw.itinerarymap.infrastructure.amap.dto.AmapResponseModels.ProviderFailure;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.concurrent.TimeUnit;

/** 有界、无日志且至多重试一次的高德 GET 执行器。 */
final class AmapHttpExecutor {
    private static final long RETRY_BASE_DELAY_MILLIS = 50;
    private static final long RETRY_JITTER_MAX_MILLIS = 100;
    private static final long TOTAL_CALL_BUDGET_NANOS = Duration.ofSeconds(7).toNanos();

    private final AmapMapProperties properties;
    private final RestClient restClient;
    private final LongConsumer sleeper;
    private final LongSupplier jitter;

    AmapHttpExecutor(AmapMapProperties properties, LongConsumer sleeper, LongSupplier jitter) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
        this.jitter = Objects.requireNonNull(jitter, "jitter");
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.responseTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(stripTrailingSlash(properties.baseUrl().toString()))
                .requestFactory(requestFactory)
                .build();
    }

    Response get(String path, Map<String, String> parameters) {
        Map<String, String> safeParameters = new LinkedHashMap<>(parameters);
        long deadline = System.nanoTime() + TOTAL_CALL_BUDGET_NANOS;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                Response response = executeOnce(path, safeParameters);
                if (response.status() == 429 || response.status() >= 500) {
                    ProviderFailure failure = response.status() == 429
                            ? ProviderFailure.PROVIDER_RATE_LIMITED : ProviderFailure.PROVIDER_UNAVAILABLE;
                    if (attempt == 0 && pauseOnceWithinBudget(deadline)) {
                        continue;
                    }
                    throw failure(failure);
                }
                if (response.status() < 200 || response.status() >= 300) {
                    throw failure(ProviderFailure.PROVIDER_UNAVAILABLE);
                }
                return response;
            } catch (ResponseReadException exception) {
                throw failure(ProviderFailure.INVALID_PROVIDER_RESPONSE);
            } catch (RestClientException exception) {
                ProviderFailure failure = isTimeout(exception)
                        ? ProviderFailure.PROVIDER_TIMEOUT : ProviderFailure.PROVIDER_UNAVAILABLE;
                if (attempt == 0 && isRetryableTransportFailure(exception) && pauseOnceWithinBudget(deadline)) {
                    continue;
                }
                throw failure(failure);
            }
        }
        throw failure(ProviderFailure.PROVIDER_UNAVAILABLE);
    }

    private Response executeOnce(String path, Map<String, String> parameters) {
        return restClient.get().uri(builder -> {
            builder.path(path);
            parameters.forEach(builder::queryParam);
            builder.queryParam("key", properties.apiKey());
            return builder.build();
        }).exchange((request, response) -> {
            try {
                return new Response(response.getStatusCode().value(), readBounded(response.getBody()));
            } catch (IOException exception) {
                throw new ResponseReadException(exception);
            }
        });
    }

    private byte[] readBounded(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(properties.maxResponseBytes(), 8_192));
        byte[] buffer = new byte[8_192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > properties.maxResponseBytes()) {
                throw new IOException("response exceeds configured limit");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private boolean pauseOnceWithinBudget(long deadline) {
        long candidate = jitter.getAsLong();
        long delay = RETRY_BASE_DELAY_MILLIS + Math.max(0, Math.min(RETRY_JITTER_MAX_MILLIS, candidate));
        long nextAttemptMaximum = properties.connectTimeout().plus(properties.responseTimeout()).toNanos();
        long required = TimeUnit.MILLISECONDS.toNanos(delay) + nextAttemptMaximum;
        if (System.nanoTime() > deadline - required) {
            return false;
        }
        sleeper.accept(delay);
        return true;
    }

    private static boolean isRetryableTransportFailure(RestClientException exception) {
        return exception instanceof ResourceAccessException || isTimeout(exception);
    }

    private static boolean isTimeout(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof SocketTimeoutException || current instanceof InterruptedIOException) {
                return true;
            }
        }
        return false;
    }

    private static AmapProviderException failure(ProviderFailure failure) {
        return new AmapProviderException(failure);
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    record Response(int status, byte[] body) {
        Response {
            body = body == null ? new byte[0] : body.clone();
        }

        @Override
        public byte[] body() {
            return body.clone();
        }

    }

    private static final class ResponseReadException extends RuntimeException {
        private ResponseReadException(IOException cause) {
            super(cause);
        }
    }
}
