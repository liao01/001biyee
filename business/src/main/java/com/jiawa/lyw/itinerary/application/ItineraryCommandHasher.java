package com.jiawa.lyw.itinerary.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class ItineraryCommandHasher {
    private final JsonMapper mapper;

    public ItineraryCommandHasher() {
        this.mapper = JsonMapper.builder()
                .findAndAddModules()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .build();
    }

    public String hash(String operation, ItineraryCommands.CommandEnvelope<?> command) {
        if (operation == null || operation.isBlank() || command == null) {
            throw new IllegalArgumentException("operation and command are required");
        }
        try {
            byte[] canonical = mapper.writeValueAsBytes(new CanonicalCommand(operation, command));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to hash itinerary command", exception);
        }
    }

    private record CanonicalCommand(
            String operation,
            ItineraryCommands.CommandEnvelope<?> command
    ) {
    }
}
