package com.jiawa.lyw.itinerarymap.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static com.jiawa.lyw.itinerarymap.domain.ItineraryMapModels.PlaceSource.AMAP;
import static com.jiawa.lyw.itinerarymap.domain.ItineraryMapModels.StopStatus.RESOLVED;
import static com.jiawa.lyw.itinerarymap.domain.ItineraryMapModels.StopStatus.UNRESOLVED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ItineraryMapModelsTests {
    private static final Instant NOW = Instant.parse("2026-09-05T00:00:00Z");

    @Test
    void normalizesSearchQueryAndCopiesResponseCollections() {
        var query = new ItineraryMapModels.PlaceSearchQuery("\u3000故宫\u3000", "\u3000北京\u3000", 0);
        var coordinates = new java.util.ArrayList<ItineraryMapModels.Coordinate>();
        coordinates.add(coordinate("116.397026", "39.918058"));

        var route = new ItineraryMapModels.RouteValue(120L, 90L, coordinates, NOW);
        coordinates.clear();

        assertEquals("故宫", query.keyword());
        assertEquals("北京", query.city());
        assertEquals(10, query.limit());
        assertEquals(1, route.polyline().size());
        assertThrows(UnsupportedOperationException.class,
                () -> route.polyline().add(coordinate("116.4", "39.9")));
    }

    @Test
    void rejectsInvalidQueriesCoordinatesAndStopReferenceCombinations() {
        assertError(ItineraryMapError.INVALID_PLACE_QUERY,
                () -> new ItineraryMapModels.PlaceSearchQuery(" ", null, 10));
        assertError(ItineraryMapError.INVALID_PLACE_QUERY,
                () -> new ItineraryMapModels.PlaceSearchQuery("故宫", null, 21));
        assertError(ItineraryMapError.INVALID_MAP_REQUEST,
                () -> coordinate("180.0000001", "39.9"));

        var reference = reference();
        assertError(ItineraryMapError.INVALID_MAP_REQUEST,
                () -> new ItineraryMapModels.Stop(1, 1L, "故宫", null, null,
                        "故宫", null, RESOLVED));
        assertError(ItineraryMapError.INVALID_MAP_REQUEST,
                () -> new ItineraryMapModels.Stop(1, 1L, "故宫", null, null,
                        "故宫", reference, UNRESOLVED));
        var route = new ItineraryMapModels.RouteValue(1L, 1L, List.of(), NOW);
        assertError(ItineraryMapError.INVALID_MAP_REQUEST,
                () -> new ItineraryMapModels.CachedRoute(route, NOW.plus(Duration.ofHours(25))));
    }

    @Test
    void rejectsMismatchedPlaceSourceAndProviderIdentity() {
        assertError(ItineraryMapError.INVALID_MAP_REQUEST,
                () -> new ItineraryMapModels.PlaceCandidate(
                        AMAP, null, "故宫", null, "110101", coordinate("116.397026", "39.918058"),
                        "GCJ02", "北京市东城区"));
    }

    private ItineraryMapModels.Coordinate coordinate(String longitude, String latitude) {
        return new ItineraryMapModels.Coordinate(new BigDecimal(longitude), new BigDecimal(latitude));
    }

    private ItineraryMapModels.PlaceReference reference() {
        return new ItineraryMapModels.PlaceReference(
                AMAP, "B000A", "故宫", null, "110101", coordinate("116.397026", "39.918058"),
                "GCJ02", NOW, NOW
        );
    }

    private void assertError(ItineraryMapError expected, org.junit.jupiter.api.function.Executable action) {
        assertEquals(expected, assertThrows(ItineraryMapException.class, action).error());
    }
}
