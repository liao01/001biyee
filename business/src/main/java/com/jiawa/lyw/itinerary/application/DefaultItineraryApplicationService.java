package com.jiawa.lyw.itinerary.application;

import com.jiawa.lyw.itinerary.domain.ItineraryError;
import com.jiawa.lyw.itinerary.domain.ItineraryException;
import com.jiawa.lyw.itinerary.domain.ItineraryModels;
import com.jiawa.lyw.itinerary.domain.ItineraryRules;
import com.jiawa.lyw.itinerary.domain.ItineraryStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class DefaultItineraryApplicationService implements ItineraryApplicationService {
    private static final String CREATE_OPERATION = "CREATE";
    private static final int MAX_PAGE_SIZE = 100;

    private final ItineraryRepository repository;
    private final ItineraryAccessPolicy accessPolicy;
    private final ItineraryIdGenerator ids;
    private final ItineraryCommandHasher hasher;
    private final Clock clock;

    public DefaultItineraryApplicationService(
            ItineraryRepository repository,
            ItineraryAccessPolicy accessPolicy,
            ItineraryIdGenerator ids,
            ItineraryCommandHasher hasher,
            Clock clock
    ) {
        this.repository = repository;
        this.accessPolicy = accessPolicy;
        this.ids = ids;
        this.hasher = hasher;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public ItineraryModels.PageSlice<ItineraryModels.Summary> list(
            long actorMemberId,
            Set<ItineraryStatus> statuses,
            String cursor,
            int limit
    ) {
        if (actorMemberId <= 0 || limit < 1 || limit > MAX_PAGE_SIZE) {
            throw invalidItinerary();
        }
        List<ItineraryStatus> selectedStatuses = normalizeStatuses(statuses);
        Cursor decoded = decodeCursor(cursor);
        List<ItineraryModels.Summary> rows = repository.findSummaries(
                actorMemberId,
                selectedStatuses,
                decoded == null ? null : decoded.updatedAt(),
                decoded == null ? null : decoded.id(),
                limit + 1
        );
        boolean hasMore = rows.size() > limit;
        List<ItineraryModels.Summary> page = hasMore
                ? List.copyOf(rows.subList(0, limit))
                : List.copyOf(rows);
        String next = hasMore ? encodeCursor(page.get(page.size() - 1)) : null;
        return new ItineraryModels.PageSlice<>(page, next);
    }

    @Override
    @Transactional(readOnly = true)
    public ItineraryModels.Snapshot get(long actorMemberId, long itineraryId) {
        if (actorMemberId <= 0 || itineraryId <= 0) {
            throw notFound();
        }
        ItineraryModels.Snapshot snapshot = repository.findSnapshot(itineraryId).orElseThrow(
                DefaultItineraryApplicationService::notFound
        );
        accessPolicy.assertCanRead(actorMemberId, snapshot.ownerMemberId());
        return snapshot;
    }

    @Override
    @Transactional
    public ItineraryCommands.CommandResult create(
            long actorMemberId,
            ItineraryCommands.CommandEnvelope<ItineraryCommands.CreateItinerary> command
    ) {
        if (actorMemberId <= 0) {
            throw invalidItinerary();
        }
        ItineraryCommands.assertCreateEnvelope(command);
        String requestHash = hasher.hash(CREATE_OPERATION, command);
        Instant now = clock.instant();
        try {
            repository.reserveCommand(
                    ids.nextId(), command.commandId(), actorMemberId, CREATE_OPERATION,
                    null, command.expectedVersion(), requestHash, now
            );
        } catch (DuplicateKeyException conflict) {
            return replay(command.commandId(), actorMemberId, CREATE_OPERATION, requestHash);
        }

        ItineraryCommands.CreateItinerary payload = command.payload();
        long itineraryId = ids.nextId();
        repository.insertItinerary(new ItineraryRepository.NewItinerary(
                itineraryId,
                actorMemberId,
                payload.title(),
                payload.startDate(),
                payload.endDate(),
                payload.timeZone(),
                payload.baseCurrency(),
                ItineraryStatus.DRAFT,
                1,
                now
        ));
        for (int index = 0; index < payload.destinations().size(); index++) {
            ItineraryCommands.DestinationInput destination = payload.destinations().get(index);
            repository.insertDestination(new ItineraryRepository.NewDestination(
                    ids.nextId(), itineraryId, destination.name(), destination.countryCode(),
                    destination.timeZone(), (long) (index + 1) * 1024, now
            ));
        }
        for (var date : ItineraryRules.dates(payload.startDate(), payload.endDate())) {
            repository.insertDay(new ItineraryRepository.NewDay(ids.nextId(), itineraryId, date, now));
        }
        if (repository.completeCommand(command.commandId(), itineraryId, null, 1) != 1) {
            throw new IllegalStateException("Itinerary command completion failed");
        }
        return new ItineraryCommands.CommandResult(itineraryId, null, 1, false);
    }

    @Override
    public ItineraryCommands.CommandResult updateOverview(
            long actorMemberId,
            long itineraryId,
            ItineraryCommands.CommandEnvelope<ItineraryCommands.UpdateOverview> command
    ) {
        throw new UnsupportedOperationException("Task 5");
    }

    @Override
    public ItineraryCommands.CommandResult replaceDestinations(
            long actorMemberId,
            long itineraryId,
            ItineraryCommands.CommandEnvelope<ItineraryCommands.ReplaceDestinations> command
    ) {
        throw new UnsupportedOperationException("Task 5");
    }

    @Override
    public ItineraryCommands.CommandResult addItem(
            long actorMemberId,
            long itineraryId,
            ItineraryCommands.CommandEnvelope<ItineraryCommands.AddItem> command
    ) {
        throw new UnsupportedOperationException("Task 6");
    }

    @Override
    public ItineraryCommands.CommandResult updateItem(
            long actorMemberId,
            long itineraryId,
            long itemId,
            ItineraryCommands.CommandEnvelope<ItineraryCommands.UpdateItem> command
    ) {
        throw new UnsupportedOperationException("Task 6");
    }

    @Override
    public ItineraryCommands.CommandResult deleteItem(
            long actorMemberId,
            long itineraryId,
            long itemId,
            ItineraryCommands.CommandEnvelope<ItineraryCommands.DeleteItem> command
    ) {
        throw new UnsupportedOperationException("Task 6");
    }

    @Override
    public ItineraryCommands.CommandResult reorderItems(
            long actorMemberId,
            long itineraryId,
            ItineraryCommands.CommandEnvelope<ItineraryCommands.ReorderItems> command
    ) {
        throw new UnsupportedOperationException("Task 6");
    }

    @Override
    public ItineraryCommands.CommandResult transition(
            long actorMemberId,
            long itineraryId,
            ItineraryCommands.CommandEnvelope<ItineraryCommands.TransitionStatus> command
    ) {
        throw new UnsupportedOperationException("Task 7");
    }

    private ItineraryCommands.CommandResult replay(
            java.util.UUID commandId,
            long memberId,
            String operation,
            String requestHash
    ) {
        ItineraryRepository.StoredCommand stored = repository.findCommand(commandId)
                .orElseThrow(() -> new IllegalStateException("Duplicate itinerary command was not found"));
        if (stored.memberId() != memberId
                || !operation.equals(stored.operation())
                || !requestHash.equals(stored.requestHash())) {
            throw new ItineraryException(ItineraryError.IDEMPOTENCY_CONFLICT, "命令已被其他请求使用");
        }
        if (stored.resultItineraryId() == null || stored.resultVersion() == null) {
            throw new IllegalStateException("Itinerary command result is incomplete");
        }
        return new ItineraryCommands.CommandResult(
                stored.resultItineraryId(), stored.resultItemId(), stored.resultVersion(), true
        );
    }

    private static List<ItineraryStatus> normalizeStatuses(Set<ItineraryStatus> statuses) {
        Set<ItineraryStatus> selected = statuses == null || statuses.isEmpty()
                ? EnumSet.complementOf(EnumSet.of(ItineraryStatus.ARCHIVED))
                : EnumSet.copyOf(statuses);
        List<ItineraryStatus> ordered = new ArrayList<>(selected);
        ordered.sort(Comparator.comparing(Enum::name));
        return List.copyOf(ordered);
    }

    private static String encodeCursor(ItineraryModels.Summary summary) {
        String raw = summary.updatedAt().toEpochMilli() + ":" + summary.id();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.US_ASCII));
    }

    private static Cursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(
                    Base64.getUrlDecoder().decode(cursor), StandardCharsets.US_ASCII
            );
            String[] parts = decoded.split(":", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException();
            }
            return new Cursor(Instant.ofEpochMilli(Long.parseLong(parts[0])), Long.parseLong(parts[1]));
        } catch (RuntimeException exception) {
            throw invalidItinerary();
        }
    }

    private static ItineraryException invalidItinerary() {
        return new ItineraryException(ItineraryError.INVALID_ITINERARY, "行程信息无效");
    }

    private static ItineraryException notFound() {
        return new ItineraryException(ItineraryError.ITINERARY_NOT_FOUND, "行程不存在");
    }

    private record Cursor(Instant updatedAt, long id) {
    }
}
