package com.jiawa.lyw.itinerarymap.api;

import com.jiawa.lyw.identity.application.CurrentMemberProvider;
import com.jiawa.lyw.itinerarymap.application.ItineraryMapApplicationService;
import com.jiawa.lyw.itinerarymap.domain.ItineraryMapError;
import com.jiawa.lyw.itinerarymap.domain.ItineraryMapException;
import com.jiawa.lyw.itinerarymap.domain.ItineraryMapModels;
import com.jiawa.lyw.resp.CommonResp;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 行程地图 HTTP 层只负责认证成员、DTO 转换和固定响应外壳。 */
@RestController
@ConditionalOnProperty(prefix = "app.map.amap", name = "base-url")
@RequestMapping("/web")
public class ItineraryMapController {
    private final ItineraryMapApplicationService maps;
    private final CurrentMemberProvider currentMember;

    public ItineraryMapController(ItineraryMapApplicationService maps, CurrentMemberProvider currentMember) {
        this.maps = maps;
        this.currentMember = currentMember;
    }

    @GetMapping("/places/search")
    public ResponseEntity<CommonResp<ItineraryMapHttpModels.PlaceSearchResponse>> search(
            @RequestParam String keyword,
            @RequestParam(required = false) String city,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ok(ItineraryMapHttpModels.PlaceSearchResponse.from(maps.search(
                currentMember.memberId(), new ItineraryMapModels.PlaceSearchQuery(keyword, city, limit)
        )));
    }

    @PutMapping("/itineraries/{itineraryId}/items/{itemId}/place")
    public ResponseEntity<CommonResp<ItineraryMapHttpModels.CommandResponse>> confirmPlace(
            @PathVariable long itineraryId,
            @PathVariable long itemId,
            @RequestBody ItineraryMapHttpModels.PutPlaceRequest request
    ) {
        return command(maps.confirmPlace(currentMember.memberId(), itineraryId, itemId, request.command()));
    }

    @DeleteMapping("/itineraries/{itineraryId}/items/{itemId}/place")
    public ResponseEntity<CommonResp<ItineraryMapHttpModels.CommandResponse>> removePlace(
            @PathVariable long itineraryId,
            @PathVariable long itemId,
            @RequestBody ItineraryMapHttpModels.RemovePlaceRequest request
    ) {
        return command(maps.removePlace(currentMember.memberId(), itineraryId, itemId, request.command()));
    }

    @GetMapping("/itineraries/{itineraryId}/days/{dayId}/map")
    public ResponseEntity<CommonResp<ItineraryMapHttpModels.DailyMapResponse>> dailyMap(
            @PathVariable long itineraryId,
            @PathVariable long dayId,
            @RequestParam(defaultValue = "WALKING") String mode
    ) {
        return ok(ItineraryMapHttpModels.DailyMapResponse.from(maps.dailyMap(
                currentMember.memberId(), itineraryId, dayId, routeMode(mode)
        )));
    }

    private ResponseEntity<CommonResp<ItineraryMapHttpModels.CommandResponse>> command(
            com.jiawa.lyw.itinerary.application.ItineraryCommands.CommandResult result
    ) {
        return ok(ItineraryMapHttpModels.CommandResponse.from(result));
    }

    private static ItineraryMapModels.RouteMode routeMode(String value) {
        try {
            return ItineraryMapModels.RouteMode.valueOf(value == null ? "" : value.strip());
        } catch (IllegalArgumentException exception) {
            throw new ItineraryMapException(ItineraryMapError.INVALID_ROUTE_MODE, "路线方式无效");
        }
    }

    private static <T> ResponseEntity<CommonResp<T>> ok(T content) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new CommonResp<>(content));
    }
}
