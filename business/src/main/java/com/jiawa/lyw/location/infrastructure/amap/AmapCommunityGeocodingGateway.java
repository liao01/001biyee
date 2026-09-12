package com.jiawa.lyw.location.infrastructure.amap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiawa.lyw.config.AmapWebServiceProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/** 旧社区地点的高德地理编码适配器；供应商 JSON 不离开地点基础设施包。 */
@Component
public final class AmapCommunityGeocodingGateway {
    private final ObjectProvider<AmapWebServiceProperties> propertiesProvider;
    private final ObjectMapper objectMapper;

    public AmapCommunityGeocodingGateway(
            ObjectProvider<AmapWebServiceProperties> propertiesProvider,
            ObjectMapper objectMapper
    ) {
        this.propertiesProvider = Objects.requireNonNull(propertiesProvider, "propertiesProvider");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper").copy();
    }

    public GeocodedAddress geocode(String address) {
        String normalized = address == null ? null : address.strip();
        if (normalized == null || normalized.isEmpty()) {
            return null;
        }
        AmapWebServiceProperties properties = propertiesProvider.getIfAvailable();
        if (properties == null) {
            return null;
        }
        try {
            byte[] body = restClient(properties).get().uri(builder -> builder.path("/v3/geocode/geo")
                    .queryParam("address", normalized)
                    .queryParam("key", properties.apiKey())
                    .build()).exchange((request, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            return null;
                        }
                        try {
                            return readBounded(response.getBody(), properties.maxResponseBytes());
                        } catch (IOException exception) {
                            return null;
                        }
                    });
            if (body == null) {
                return null;
            }
            JsonNode root = objectMapper.readTree(body);
            if (root == null || !"1".equals(text(root, "status"))) {
                return null;
            }
            JsonNode geocodes = root.get("geocodes");
            if (geocodes == null || !geocodes.isArray() || geocodes.isEmpty()) {
                return null;
            }
            JsonNode geocode = geocodes.get(0);
            String location = text(geocode, "location");
            if (location == null) {
                return null;
            }
            String[] point = location.split(",", -1);
            if (point.length != 2) {
                return null;
            }
            return new GeocodedAddress(
                    Double.parseDouble(point[0]), Double.parseDouble(point[1]),
                    text(geocode, "formatted_address"), text(geocode, "province"),
                    text(geocode, "city"), text(geocode, "district")
            );
        } catch (RuntimeException | IOException exception) {
            return null;
        }
    }

    public record GeocodedAddress(
            double longitude, double latitude, String formattedAddress,
            String province, String city, String district
    ) {
    }

    private static byte[] readBounded(InputStream input, int maximum) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maximum, 8_192));
        byte[] buffer = new byte[8_192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maximum) {
                throw new IOException("response exceeds configured limit");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isTextual() ? value.textValue() : null;
    }

    private static RestClient restClient(AmapWebServiceProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.responseTimeout());
        return RestClient.builder()
                .baseUrl(stripTrailingSlash(properties.baseUrl().toString()))
                .requestFactory(requestFactory)
                .build();
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
