package com.jiawa.lyw.itinerary.api;

import com.jiawa.lyw.identity.application.CurrentMemberProvider;
import com.jiawa.lyw.itinerary.application.ItineraryApplicationService;
import com.jiawa.lyw.itinerary.application.ItineraryCommands;
import com.jiawa.lyw.itinerary.domain.ItineraryStatus;
import com.jiawa.lyw.resp.CommonResp;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/web/itineraries")
public class ItineraryController {
    private final ItineraryApplicationService itineraries;
    private final CurrentMemberProvider currentMember;

    public ItineraryController(
            ItineraryApplicationService itineraries,
            CurrentMemberProvider currentMember
    ) {
        this.itineraries = itineraries;
        this.currentMember = currentMember;
    }

    @GetMapping
    public ResponseEntity<CommonResp<ItineraryHttpModels.PageResponse>> list(
            @RequestParam(name = "status", required = false) Set<ItineraryStatus> statuses,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ok(ItineraryHttpModels.PageResponse.from(
                itineraries.list(currentMember.memberId(), statuses, cursor, limit)
        ));
    }

    @PostMapping
    public ResponseEntity<CommonResp<ItineraryHttpModels.CommandResponse>> create(
            @RequestBody ItineraryHttpModels.CommandRequest<ItineraryHttpModels.CreatePayload> request
    ) {
        return command(itineraries.create(
                currentMember.memberId(), request.envelope(request.payload().command())
        ));
    }

    @GetMapping("/{itineraryId}")
    public ResponseEntity<CommonResp<ItineraryHttpModels.SnapshotResponse>> get(
            @PathVariable long itineraryId
    ) {
        return ok(ItineraryHttpModels.SnapshotResponse.from(
                itineraries.detail(currentMember.memberId(), itineraryId)
        ));
    }

    @PatchMapping("/{itineraryId}")
    public ResponseEntity<CommonResp<ItineraryHttpModels.CommandResponse>> updateOverview(
            @PathVariable long itineraryId,
            @RequestBody ItineraryHttpModels.CommandRequest<ItineraryHttpModels.UpdateOverviewPayload> request
    ) {
        return command(itineraries.updateOverview(
                currentMember.memberId(), itineraryId, request.envelope(request.payload().command())
        ));
    }

    @PutMapping("/{itineraryId}/destinations")
    public ResponseEntity<CommonResp<ItineraryHttpModels.CommandResponse>> replaceDestinations(
            @PathVariable long itineraryId,
            @RequestBody ItineraryHttpModels.CommandRequest<ItineraryHttpModels.ReplaceDestinationsPayload> request
    ) {
        return command(itineraries.replaceDestinations(
                currentMember.memberId(), itineraryId, request.envelope(request.payload().command())
        ));
    }

    @PostMapping("/{itineraryId}/items")
    public ResponseEntity<CommonResp<ItineraryHttpModels.CommandResponse>> addItem(
            @PathVariable long itineraryId,
            @RequestBody ItineraryHttpModels.CommandRequest<ItineraryHttpModels.ItemPayload> request
    ) {
        return command(itineraries.addItem(
                currentMember.memberId(), itineraryId, request.envelope(request.payload().addCommand())
        ));
    }

    @PatchMapping("/{itineraryId}/items/{itemId}")
    public ResponseEntity<CommonResp<ItineraryHttpModels.CommandResponse>> updateItem(
            @PathVariable long itineraryId,
            @PathVariable long itemId,
            @RequestBody ItineraryHttpModels.CommandRequest<ItineraryHttpModels.ItemPayload> request
    ) {
        return command(itineraries.updateItem(
                currentMember.memberId(), itineraryId, itemId,
                request.envelope(request.payload().updateCommand())
        ));
    }

    @DeleteMapping("/{itineraryId}/items/{itemId}")
    public ResponseEntity<CommonResp<ItineraryHttpModels.CommandResponse>> deleteItem(
            @PathVariable long itineraryId,
            @PathVariable long itemId,
            @RequestBody ItineraryHttpModels.CommandRequest<ItineraryHttpModels.DeletePayload> request
    ) {
        return command(itineraries.deleteItem(
                currentMember.memberId(), itineraryId, itemId,
                request.envelope(new ItineraryCommands.DeleteItem())
        ));
    }

    @PutMapping("/{itineraryId}/days/{dayId}/item-order")
    public ResponseEntity<CommonResp<ItineraryHttpModels.CommandResponse>> reorderItems(
            @PathVariable long itineraryId,
            @PathVariable long dayId,
            @RequestBody ItineraryHttpModels.CommandRequest<ItineraryHttpModels.ReorderPayload> request
    ) {
        return command(itineraries.reorderItems(
                currentMember.memberId(), itineraryId,
                request.envelope(new ItineraryCommands.ReorderItems(dayId, request.payload().itemIds()))
        ));
    }

    @PostMapping("/{itineraryId}/status-transitions")
    public ResponseEntity<CommonResp<ItineraryHttpModels.CommandResponse>> transition(
            @PathVariable long itineraryId,
            @RequestBody ItineraryHttpModels.CommandRequest<ItineraryHttpModels.TransitionPayload> request
    ) {
        return command(itineraries.transition(
                currentMember.memberId(), itineraryId, request.envelope(request.payload().command())
        ));
    }

    private ResponseEntity<CommonResp<ItineraryHttpModels.CommandResponse>> command(
            ItineraryCommands.CommandResult result
    ) {
        return ok(ItineraryHttpModels.CommandResponse.from(result));
    }

    private <T> ResponseEntity<CommonResp<T>> ok(T content) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new CommonResp<>(content));
    }
}
