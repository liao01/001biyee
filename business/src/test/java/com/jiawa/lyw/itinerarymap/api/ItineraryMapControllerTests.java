package com.jiawa.lyw.itinerarymap.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiawa.lyw.identity.application.CurrentMemberProvider;
import com.jiawa.lyw.identity.domain.IdentityException;
import com.jiawa.lyw.itinerary.application.ItineraryCommands;
import com.jiawa.lyw.itinerary.domain.ItineraryError;
import com.jiawa.lyw.itinerary.domain.ItineraryException;
import com.jiawa.lyw.itinerarymap.application.ItineraryMapApplicationService;
import com.jiawa.lyw.itinerarymap.domain.ItineraryMapError;
import com.jiawa.lyw.itinerarymap.domain.ItineraryMapException;
import com.jiawa.lyw.itinerarymap.domain.ItineraryMapModels;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ItineraryMapControllerTests {
    private final ItineraryMapApplicationService maps = mock(ItineraryMapApplicationService.class);
    private final CurrentMemberProvider currentMember = mock(CurrentMemberProvider.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(currentMember);
        when(currentMember.memberId()).thenReturn(7L);
        mvc = MockMvcBuilders.standaloneSetup(new ItineraryMapController(maps, currentMember))
                .setControllerAdvice(new ItineraryMapExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        new ObjectMapper().findAndRegisterModules()))
                .build();
    }

    @Test
    void routesSearchConfirmationRemovalAndDailyMapThroughTheApplicationBoundary() throws Exception {
        UUID putCommand = UUID.fromString("00000000-0000-0000-0000-000000000501");
        UUID removeCommand = UUID.fromString("00000000-0000-0000-0000-000000000502");
        when(maps.search(eq(7L), any())).thenReturn(List.of(candidate()));
        when(maps.confirmPlace(eq(7L), eq(11L), eq(21L), any())).thenReturn(
                new ItineraryCommands.CommandResult(11, 21L, 4, false));
        when(maps.removePlace(eq(7L), eq(11L), eq(21L), any())).thenReturn(
                new ItineraryCommands.CommandResult(11, 21L, 5, false));
        when(maps.dailyMap(7L, 11L, 31L, ItineraryMapModels.RouteMode.WALKING)).thenReturn(dailyMap());

        mvc.perform(get("/web/places/search")
                        .param("keyword", "故宫")
                        .param("city", "110101")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.content.items[0].source").value("AMAP"))
                .andExpect(jsonPath("$.content.items[0].sourcePlaceId").value("B000A"))
                .andExpect(jsonPath("$.content.items[0].longitude").value(116.397026));
        mvc.perform(put("/web/itineraries/11/items/21/place")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(putBody(putCommand)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.itineraryId").value("11"))
                .andExpect(jsonPath("$.content.itemId").value("21"))
                .andExpect(jsonPath("$.content.version").value(4));
        mvc.perform(delete("/web/itineraries/11/items/21/place")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commandId\":\"" + removeCommand + "\",\"expectedVersion\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.version").value(5));
        mvc.perform(get("/web/itineraries/11/days/31/map").param("mode", "WALKING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.itineraryId").value("11"))
                .andExpect(jsonPath("$.content.stops[0].itemId").value("21"))
                .andExpect(jsonPath("$.content.legs[0].status").value("CACHED"));

        ArgumentCaptor<ItineraryMapModels.PlaceSearchQuery> query = ArgumentCaptor.forClass(
                ItineraryMapModels.PlaceSearchQuery.class);
        verify(maps).search(eq(7L), query.capture());
        assertEquals(new ItineraryMapModels.PlaceSearchQuery("故宫", "110101", 10), query.getValue());
        verify(maps).confirmPlace(eq(7L), eq(11L), eq(21L), any());
        verify(maps).removePlace(eq(7L), eq(11L), eq(21L), any());
    }

    @Test
    void mapsMapAndItineraryFailuresToStableSafeHttpResponses() throws Exception {
        when(maps.search(eq(7L), any())).thenThrow(new ItineraryMapException(
                ItineraryMapError.MAP_PROVIDER_UNAVAILABLE, "private provider detail"));
        mvc.perform(get("/web/places/search").param("keyword", "故宫"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.content.errorCode").value("MAP_PROVIDER_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("地点服务暂时不可用，请稍后重试"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("private"))));

        when(maps.confirmPlace(eq(7L), eq(11L), eq(21L), any())).thenThrow(new ItineraryException(
                ItineraryError.VERSION_CONFLICT, "private version detail"));
        mvc.perform(put("/web/itineraries/11/items/21/place")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(putBody(UUID.fromString("00000000-0000-0000-0000-000000000503"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.content.errorCode").value("VERSION_CONFLICT"))
                .andExpect(jsonPath("$.message").value("行程已被更新，请重新加载"));

        when(maps.dailyMap(7L, 99L, 31L, ItineraryMapModels.RouteMode.WALKING)).thenThrow(new ItineraryException(
                ItineraryError.ITINERARY_NOT_FOUND, "private itinerary detail"));
        mvc.perform(get("/web/itineraries/99/days/31/map").param("mode", "WALKING"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.content.errorCode").value("ITINERARY_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("行程不存在"));
    }

    @Test
    void rejectsUnauthenticatedAndMalformedRequestsWithoutExposingState() throws Exception {
        when(currentMember.memberId()).thenThrow(new IdentityException(
                IdentityException.Reason.UNAUTHENTICATED, "private token detail"));
        mvc.perform(get("/web/places/search").param("keyword", "故宫"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("请先登录"));

        reset(currentMember);
        when(currentMember.memberId()).thenReturn(7L);
        mvc.perform(put("/web/itineraries/11/items/21/place")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.content.errorCode").value("INVALID_MAP_REQUEST"))
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
        mvc.perform(get("/web/itineraries/11/days/31/map").param("mode", "FLYING"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.content.errorCode").value("INVALID_ROUTE_MODE"));
    }

    private static ItineraryMapModels.PlaceCandidate candidate() {
        return new ItineraryMapModels.PlaceCandidate(
                ItineraryMapModels.PlaceSource.AMAP, "B000A", "故宫博物院", "景山前街4号", "110101",
                new ItineraryMapModels.Coordinate(new BigDecimal("116.397026"), new BigDecimal("39.918058")),
                "GCJ02", "北京市东城区"
        );
    }

    private static ItineraryMapModels.DailyMapView dailyMap() {
        Instant now = Instant.parse("2026-09-05T08:00:00Z");
        ItineraryMapModels.PlaceReference place = new ItineraryMapModels.PlaceReference(
                ItineraryMapModels.PlaceSource.AMAP, "B000A", "故宫博物院", "景山前街4号", "110101",
                new ItineraryMapModels.Coordinate(new BigDecimal("116.397026"), new BigDecimal("39.918058")),
                "GCJ02", now, now
        );
        return new ItineraryMapModels.DailyMapView(
                11, 31, LocalDate.of(2026, 9, 5), ItineraryMapModels.RouteMode.WALKING,
                List.of(
                        new ItineraryMapModels.Stop(1, 21, "故宫游览", null, null, "故宫", place,
                                ItineraryMapModels.StopStatus.RESOLVED),
                        new ItineraryMapModels.Stop(2, 22, "景山游览", null, null, "景山", place,
                                ItineraryMapModels.StopStatus.RESOLVED)
                ),
                List.of(new ItineraryMapModels.Leg(21, 22, ItineraryMapModels.RouteMode.WALKING,
                        ItineraryMapModels.RouteStatus.CACHED, 800L, 600L, List.of(), now, false, null)),
                false, now
        );
    }

    private static String putBody(UUID commandId) {
        return """
                {"commandId":"%s","expectedVersion":3,"place":{
                  "source":"AMAP","sourcePlaceId":"B000A","name":"故宫博物院","address":"景山前街4号",
                  "administrativeAreaCode":"110101","longitude":116.397026,"latitude":39.918058,
                  "coordinateSystem":"GCJ02"}}
                """.formatted(commandId);
    }
}
