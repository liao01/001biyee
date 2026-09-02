package com.jiawa.lyw.itinerary.application;

import com.jiawa.lyw.itinerary.domain.ItineraryError;
import com.jiawa.lyw.itinerary.domain.ItineraryException;
import com.jiawa.lyw.itinerary.domain.ItineraryModels;
import com.jiawa.lyw.itinerary.domain.ItineraryRules;
import com.jiawa.lyw.itinerary.domain.ItineraryStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class DefaultItineraryApplicationService implements ItineraryApplicationService {
    private static final String CREATE_OPERATION = "CREATE";
    private static final String UPDATE_OVERVIEW_OPERATION = "UPDATE_OVERVIEW";
    private static final String REPLACE_DESTINATIONS_OPERATION = "REPLACE_DESTINATIONS";
    private static final String ADD_ITEM_OPERATION = "ADD_ITEM";
    private static final String UPDATE_ITEM_OPERATION = "UPDATE_ITEM";
    private static final String DELETE_ITEM_OPERATION = "DELETE_ITEM";
    private static final String REORDER_ITEMS_OPERATION = "REORDER_ITEMS";
    private static final String APPLY_REVISION_OPERATION = "APPLY_REVISION";
    private static final String TRANSITION_STATUS_OPERATION = "TRANSITION_STATUS";
    private static final long POSITION_GAP = 1024L;
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
    @Transactional(readOnly = true)
    public ItineraryModels.Detail detail(long actorMemberId, long itineraryId) {
        ItineraryModels.Snapshot snapshot = get(actorMemberId, itineraryId);
        List<ItineraryStatus> allowed = ItineraryRules.allowedTransitions(snapshot.status()).stream()
                .sorted()
                .toList();
        LocalDate localToday = LocalDate.now(clock.withZone(snapshot.timeZone()));
        return new ItineraryModels.Detail(
                snapshot,
                allowed,
                ItineraryRules.suggestedStatus(snapshot, localToday).orElse(null)
        );
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
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
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ItineraryCommands.CommandResult updateOverview(
            long actorMemberId,
            long itineraryId,
            ItineraryCommands.CommandEnvelope<ItineraryCommands.UpdateOverview> command
    ) {
        ItineraryCommands.assertExistingEnvelope(command);
        String requestHash = hashForItinerary(UPDATE_OVERVIEW_OPERATION, itineraryId, command);
        ItineraryModels.Snapshot current = lockForEdit(actorMemberId, itineraryId);
        ItineraryCommands.CommandResult replay = replayIfPresent(
                command.commandId(), actorMemberId, UPDATE_OVERVIEW_OPERATION, requestHash
        );
        if (replay != null) {
            return replay;
        }
        assertVersion(current, command.expectedVersion());
        reserveExistingCommand(
                command, actorMemberId, itineraryId, UPDATE_OVERVIEW_OPERATION, requestHash
        );

        ItineraryCommands.UpdateOverview payload = command.payload();
        String title = payload.title() == null ? current.title() : payload.title();
        var startDate = payload.startDate() == null ? current.startDate() : payload.startDate();
        var endDate = payload.endDate() == null ? current.endDate() : payload.endDate();
        String timeZone = payload.timeZone() == null ? current.timeZone().getId() : payload.timeZone();
        String baseCurrency = payload.baseCurrency() == null
                ? current.baseCurrency().getCurrencyCode() : payload.baseCurrency();
        ItineraryRules.assertShrinkIsSafe(startDate, endDate, current.days());

        repository.deleteEmptyDaysOutside(itineraryId, startDate, endDate);
        Set<java.time.LocalDate> existingDates = current.days().stream()
                .map(ItineraryModels.Day::date)
                .collect(java.util.stream.Collectors.toSet());
        Instant now = clock.instant();
        for (var date : ItineraryRules.dates(startDate, endDate)) {
            if (!existingDates.contains(date)) {
                repository.insertDay(new ItineraryRepository.NewDay(ids.nextId(), itineraryId, date, now));
            }
        }
        long nextVersion = current.version() + 1;
        if (repository.updateOverview(
                itineraryId, title, startDate, endDate, timeZone, baseCurrency,
                current.version(), nextVersion, now
        ) != 1) {
            throw versionConflict();
        }
        return complete(command.commandId(), itineraryId, null, nextVersion);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ItineraryCommands.CommandResult replaceDestinations(
            long actorMemberId,
            long itineraryId,
            ItineraryCommands.CommandEnvelope<ItineraryCommands.ReplaceDestinations> command
    ) {
        ItineraryCommands.assertExistingEnvelope(command);
        String requestHash = hashForItinerary(REPLACE_DESTINATIONS_OPERATION, itineraryId, command);
        ItineraryModels.Snapshot current = lockForEdit(actorMemberId, itineraryId);
        ItineraryCommands.CommandResult replay = replayIfPresent(
                command.commandId(), actorMemberId, REPLACE_DESTINATIONS_OPERATION, requestHash
        );
        if (replay != null) {
            return replay;
        }
        assertVersion(current, command.expectedVersion());
        Set<Long> currentIds = current.destinations().stream()
                .map(ItineraryModels.Destination::id)
                .collect(java.util.stream.Collectors.toSet());
        boolean containsForeignId = command.payload().destinations().stream()
                .map(ItineraryCommands.DestinationInput::id)
                .filter(java.util.Objects::nonNull)
                .anyMatch(id -> !currentIds.contains(id));
        if (containsForeignId) {
            throw new ItineraryException(ItineraryError.INVALID_DESTINATION, "目的地信息无效");
        }
        reserveExistingCommand(
                command, actorMemberId, itineraryId, REPLACE_DESTINATIONS_OPERATION, requestHash
        );

        repository.deleteDestinations(itineraryId);
        Instant now = clock.instant();
        for (int index = 0; index < command.payload().destinations().size(); index++) {
            ItineraryCommands.DestinationInput destination = command.payload().destinations().get(index);
            repository.insertDestination(new ItineraryRepository.NewDestination(
                    destination.id() == null ? ids.nextId() : destination.id(),
                    itineraryId,
                    destination.name(),
                    destination.countryCode(),
                    destination.timeZone(),
                    (long) (index + 1) * 1024,
                    now
            ));
        }
        long nextVersion = current.version() + 1;
        if (repository.bumpVersion(itineraryId, current.version(), nextVersion, now) != 1) {
            throw versionConflict();
        }
        return complete(command.commandId(), itineraryId, null, nextVersion);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ItineraryCommands.CommandResult addItem(
            long actorMemberId,
            long itineraryId,
            ItineraryCommands.CommandEnvelope<ItineraryCommands.AddItem> command
    ) {
        ItineraryCommands.assertExistingEnvelope(command);
        String requestHash = hashForItinerary(ADD_ITEM_OPERATION, itineraryId, command);
        ItineraryModels.Snapshot current = lockForEdit(actorMemberId, itineraryId);
        ItineraryCommands.CommandResult replay = replayIfPresent(
                command.commandId(), actorMemberId, ADD_ITEM_OPERATION, requestHash
        );
        if (replay != null) {
            return replay;
        }
        assertVersion(current, command.expectedVersion());
        ItineraryModels.Day day = requireDay(current, command.payload().dayId());
        ItineraryRules.assertNoOverlap(
                null, command.payload().startTime(), command.payload().endTime(), day.items()
        );
        reserveExistingCommand(command, actorMemberId, itineraryId, ADD_ITEM_OPERATION, requestHash);

        Instant now = clock.instant();
        long position = appendPosition(itineraryId, day, now);
        long itemId = ids.nextId();
        ItineraryCommands.AddItem payload = command.payload();
        repository.insertItem(new ItineraryRepository.NewItem(
                itemId, itineraryId, day.id(), payload.title(), payload.placeName(),
                payload.startTime(), payload.endTime(), payload.notes(), payload.estimatedCost(),
                position, now
        ));
        long nextVersion = bumpVersion(current, now);
        return complete(command.commandId(), itineraryId, itemId, nextVersion);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ItineraryCommands.CommandResult updateItem(
            long actorMemberId,
            long itineraryId,
            long itemId,
            ItineraryCommands.CommandEnvelope<ItineraryCommands.UpdateItem> command
    ) {
        ItineraryCommands.assertExistingEnvelope(command);
        String requestHash = hashForItem(UPDATE_ITEM_OPERATION, itineraryId, itemId, command);
        ItineraryModels.Snapshot current = lockForEdit(actorMemberId, itineraryId);
        ItineraryCommands.CommandResult replay = replayIfPresent(
                command.commandId(), actorMemberId, UPDATE_ITEM_OPERATION, requestHash
        );
        if (replay != null) {
            return replay;
        }
        assertVersion(current, command.expectedVersion());
        ItineraryModels.Item existing = requireItem(current, itemId);
        ItineraryModels.Day targetDay = requireDay(current, command.payload().dayId());
        ItineraryRules.assertNoOverlap(
                itemId, command.payload().startTime(), command.payload().endTime(), targetDay.items()
        );
        reserveExistingCommand(command, actorMemberId, itineraryId, UPDATE_ITEM_OPERATION, requestHash);

        Instant now = clock.instant();
        long position = existing.dayId() == targetDay.id()
                ? existing.position() : appendPosition(itineraryId, targetDay, now);
        ItineraryCommands.UpdateItem payload = command.payload();
        if (repository.updateItem(
                itineraryId, itemId, targetDay.id(), payload.title(), payload.placeName(),
                payload.startTime(), payload.endTime(), payload.notes(), payload.estimatedCost(),
                position, now
        ) != 1) {
            throw invalidItem();
        }
        long nextVersion = bumpVersion(current, now);
        return complete(command.commandId(), itineraryId, null, nextVersion);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ItineraryCommands.CommandResult deleteItem(
            long actorMemberId,
            long itineraryId,
            long itemId,
            ItineraryCommands.CommandEnvelope<ItineraryCommands.DeleteItem> command
    ) {
        ItineraryCommands.assertExistingEnvelope(command);
        String requestHash = hashForItem(DELETE_ITEM_OPERATION, itineraryId, itemId, command);
        ItineraryModels.Snapshot current = lockForEdit(actorMemberId, itineraryId);
        ItineraryCommands.CommandResult replay = replayIfPresent(
                command.commandId(), actorMemberId, DELETE_ITEM_OPERATION, requestHash
        );
        if (replay != null) {
            return replay;
        }
        assertVersion(current, command.expectedVersion());
        requireItem(current, itemId);
        reserveExistingCommand(command, actorMemberId, itineraryId, DELETE_ITEM_OPERATION, requestHash);

        Instant now = clock.instant();
        if (repository.softDeleteItem(itineraryId, itemId, now) != 1) {
            throw invalidItem();
        }
        long nextVersion = bumpVersion(current, now);
        return complete(command.commandId(), itineraryId, null, nextVersion);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ItineraryCommands.CommandResult reorderItems(
            long actorMemberId,
            long itineraryId,
            ItineraryCommands.CommandEnvelope<ItineraryCommands.ReorderItems> command
    ) {
        ItineraryCommands.assertExistingEnvelope(command);
        String requestHash = hashForItinerary(REORDER_ITEMS_OPERATION, itineraryId, command);
        ItineraryModels.Snapshot current = lockForEdit(actorMemberId, itineraryId);
        ItineraryCommands.CommandResult replay = replayIfPresent(
                command.commandId(), actorMemberId, REORDER_ITEMS_OPERATION, requestHash
        );
        if (replay != null) {
            return replay;
        }
        assertVersion(current, command.expectedVersion());
        ItineraryModels.Day day = requireDay(current, command.payload().dayId());
        ItineraryRules.assertPermutation(
                day.items().stream().map(ItineraryModels.Item::id).toList(),
                command.payload().itemIds()
        );
        reserveExistingCommand(
                command, actorMemberId, itineraryId, REORDER_ITEMS_OPERATION, requestHash
        );

        Instant now = clock.instant();
        for (int index = 0; index < command.payload().itemIds().size(); index++) {
            long position = Math.multiplyExact((long) index + 1, POSITION_GAP);
            if (repository.updateItemPosition(
                    itineraryId, command.payload().itemIds().get(index), day.id(), position, now
            ) != 1) {
                throw invalidItem();
            }
        }
        long nextVersion = bumpVersion(current, now);
        return complete(command.commandId(), itineraryId, null, nextVersion);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ItineraryCommands.CommandResult applyRevision(
            long actorMemberId,
            long itineraryId,
            ItineraryCommands.CommandEnvelope<ItineraryCommands.ApplyRevision> command
    ) {
        ItineraryCommands.assertExistingEnvelope(command);
        String requestHash = hashForItinerary(APPLY_REVISION_OPERATION, itineraryId, command);
        ItineraryModels.Snapshot current = lockForEdit(actorMemberId, itineraryId);
        ItineraryCommands.CommandResult replay = replayIfPresent(
                command.commandId(), actorMemberId, APPLY_REVISION_OPERATION, requestHash
        );
        if (replay != null) {
            return replay;
        }
        assertVersion(current, command.expectedVersion());
        if (current.status() == ItineraryStatus.CANCELLED
                || current.status() == ItineraryStatus.ARCHIVED) {
            throw new ItineraryException(
                    ItineraryError.INVALID_STATUS_TRANSITION,
                    "当前行程状态不允许应用修订"
            );
        }

        RevisionState revision = simulateRevision(current, command.payload());
        reserveExistingCommand(
                command, actorMemberId, itineraryId, APPLY_REVISION_OPERATION, requestHash
        );
        Instant now = clock.instant();
        persistRevision(current, revision, now);
        long nextVersion = bumpVersion(current, now);
        return complete(command.commandId(), itineraryId, null, nextVersion);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ItineraryCommands.CommandResult transition(
            long actorMemberId,
            long itineraryId,
            ItineraryCommands.CommandEnvelope<ItineraryCommands.TransitionStatus> command
    ) {
        ItineraryCommands.assertExistingEnvelope(command);
        String requestHash = hashForItinerary(TRANSITION_STATUS_OPERATION, itineraryId, command);
        ItineraryModels.Snapshot current = lockForEdit(actorMemberId, itineraryId);
        ItineraryCommands.CommandResult replay = replayIfPresent(
                command.commandId(), actorMemberId, TRANSITION_STATUS_OPERATION, requestHash
        );
        if (replay != null) {
            return replay;
        }
        assertVersion(current, command.expectedVersion());
        ItineraryRules.assertTransition(current, command.payload().toStatus());
        reserveExistingCommand(
                command, actorMemberId, itineraryId, TRANSITION_STATUS_OPERATION, requestHash
        );

        Instant now = clock.instant();
        long nextVersion = current.version() + 1;
        if (repository.updateStatus(
                itineraryId, command.payload().toStatus(), current.version(), nextVersion, now
        ) != 1) {
            throw versionConflict();
        }
        return complete(command.commandId(), itineraryId, null, nextVersion);
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

    private ItineraryModels.Snapshot lockForEdit(long actorMemberId, long itineraryId) {
        if (actorMemberId <= 0 || itineraryId <= 0) {
            throw notFound();
        }
        ItineraryModels.Snapshot snapshot = repository.findSnapshotForUpdate(itineraryId)
                .orElseThrow(DefaultItineraryApplicationService::notFound);
        accessPolicy.assertCanEdit(actorMemberId, snapshot.ownerMemberId());
        return snapshot;
    }

    private ItineraryCommands.CommandResult replayIfPresent(
            java.util.UUID commandId,
            long memberId,
            String operation,
            String requestHash
    ) {
        return repository.findCommand(commandId)
                .map(ignored -> replay(commandId, memberId, operation, requestHash))
                .orElse(null);
    }

    private void reserveExistingCommand(
            ItineraryCommands.CommandEnvelope<?> command,
            long actorMemberId,
            long itineraryId,
            String operation,
            String requestHash
    ) {
        try {
            repository.reserveCommand(
                    ids.nextId(), command.commandId(), actorMemberId, operation, itineraryId,
                    command.expectedVersion(), requestHash, clock.instant()
            );
        } catch (DuplicateKeyException conflict) {
            replay(command.commandId(), actorMemberId, operation, requestHash);
            throw new IllegalStateException("Unexpected duplicate itinerary command");
        }
    }

    private String hashForItinerary(
            String operation,
            long itineraryId,
            ItineraryCommands.CommandEnvelope<?> command
    ) {
        return hasher.hash(operation + "@" + itineraryId, command);
    }

    private String hashForItem(
            String operation,
            long itineraryId,
            long itemId,
            ItineraryCommands.CommandEnvelope<?> command
    ) {
        return hasher.hash(operation + "@" + itineraryId + "/" + itemId, command);
    }

    private long appendPosition(
            long itineraryId,
            ItineraryModels.Day day,
            Instant now
    ) {
        long maximum = day.items().stream()
                .mapToLong(ItineraryModels.Item::position)
                .max()
                .orElse(0L);
        if (maximum <= Long.MAX_VALUE - POSITION_GAP) {
            return maximum + POSITION_GAP;
        }
        for (int index = 0; index < day.items().size(); index++) {
            long position = Math.multiplyExact((long) index + 1, POSITION_GAP);
            if (repository.updateItemPosition(
                    itineraryId, day.items().get(index).id(), day.id(), position, now
            ) != 1) {
                throw invalidItem();
            }
        }
        return Math.multiplyExact((long) day.items().size() + 1, POSITION_GAP);
    }

    private long bumpVersion(ItineraryModels.Snapshot current, Instant now) {
        long nextVersion = current.version() + 1;
        if (repository.bumpVersion(current.id(), current.version(), nextVersion, now) != 1) {
            throw versionConflict();
        }
        return nextVersion;
    }

    private RevisionState simulateRevision(
            ItineraryModels.Snapshot current,
            ItineraryCommands.ApplyRevision revision
    ) {
        Map<Long, ItineraryModels.Day> days = new LinkedHashMap<>();
        current.days().forEach(day -> days.put(day.id(), day));
        Map<Long, RevisionItem> items = new LinkedHashMap<>();
        current.days().stream().flatMap(day -> day.items().stream())
                .filter(item -> !item.deleted())
                .forEach(item -> items.put(item.id(), RevisionItem.from(item)));
        Map<String, Long> addedIds = new LinkedHashMap<>();
        Set<Long> changedExisting = new java.util.HashSet<>();

        for (ItineraryCommands.RevisionOperation operation : revision.operations()) {
            if (operation instanceof ItineraryCommands.RevisionAddItem add) {
                requireRevisionDay(days, add.dayId());
                long itemId = ids.nextId();
                addedIds.put(add.operationKey(), itemId);
                items.put(itemId, new RevisionItem(
                        itemId, add.dayId(), add.title(), add.placeName(), add.startTime(), add.endTime(),
                        add.notes(), add.estimatedCost(), appendRevisionPosition(items, add.dayId()), true
                ));
            } else if (operation instanceof ItineraryCommands.RevisionUpdateItem update) {
                requireRevisionDay(days, update.dayId());
                RevisionItem existing = requireRevisionItem(items, update.targetItemId());
                requireSingleRevisionChange(changedExisting, update.targetItemId());
                long position = existing.dayId() == update.dayId()
                        ? existing.position() : appendRevisionPosition(items, update.dayId());
                items.put(update.targetItemId(), new RevisionItem(
                        existing.id(), update.dayId(), update.title(), update.placeName(),
                        update.startTime(), update.endTime(), update.notes(), update.estimatedCost(),
                        position, false
                ));
            } else if (operation instanceof ItineraryCommands.RevisionDeleteItem delete) {
                requireRevisionItem(items, delete.targetItemId());
                requireSingleRevisionChange(changedExisting, delete.targetItemId());
                items.remove(delete.targetItemId());
            } else if (operation instanceof ItineraryCommands.RevisionReorderItems reorder) {
                requireRevisionDay(days, reorder.dayId());
                List<Long> orderedIds = reorder.itemReferences().stream()
                        .map(reference -> resolveRevisionReference(reference, addedIds))
                        .toList();
                ItineraryRules.assertPermutation(
                        items.values().stream()
                                .filter(item -> item.dayId() == reorder.dayId())
                                .map(RevisionItem::id)
                                .toList(),
                        orderedIds
                );
                for (int index = 0; index < orderedIds.size(); index++) {
                    long itemId = orderedIds.get(index);
                    RevisionItem item = requireRevisionItem(items, itemId);
                    items.put(itemId, item.withPosition(
                            Math.multiplyExact((long) index + 1, POSITION_GAP)
                    ));
                }
            } else {
                throw invalidItem();
            }
        }

        for (ItineraryModels.Day day : current.days()) {
            List<ItineraryModels.Item> simulated = items.values().stream()
                    .filter(item -> item.dayId() == day.id())
                    .map(RevisionItem::asDomainItem)
                    .toList();
            for (ItineraryModels.Item item : simulated) {
                ItineraryRules.assertNoOverlap(
                        item.id(), item.startTime(), item.endTime(), simulated
                );
            }
        }
        return new RevisionState(Map.copyOf(items));
    }

    private void persistRevision(
            ItineraryModels.Snapshot current,
            RevisionState revision,
            Instant now
    ) {
        Map<Long, ItineraryModels.Item> originals = new LinkedHashMap<>();
        current.days().stream().flatMap(day -> day.items().stream())
                .filter(item -> !item.deleted())
                .forEach(item -> originals.put(item.id(), item));

        for (ItineraryModels.Item original : originals.values()) {
            if (!revision.items().containsKey(original.id())
                    && repository.softDeleteItem(current.id(), original.id(), now) != 1) {
                throw invalidItem();
            }
        }
        for (RevisionItem item : revision.items().values()) {
            ItineraryModels.Item original = originals.get(item.id());
            if (original == null) {
                repository.insertItem(new ItineraryRepository.NewItem(
                        item.id(), current.id(), item.dayId(), item.title(), item.placeName(),
                        item.startTime(), item.endTime(), item.notes(), item.estimatedCost(),
                        item.position(), now
                ));
            } else if (!item.matches(original) && repository.updateItem(
                    current.id(), item.id(), item.dayId(), item.title(), item.placeName(),
                    item.startTime(), item.endTime(), item.notes(), item.estimatedCost(),
                    item.position(), now
            ) != 1) {
                throw invalidItem();
            }
        }
    }

    private static long resolveRevisionReference(
            ItineraryCommands.RevisionItemReference reference,
            Map<String, Long> addedIds
    ) {
        if (reference.existingItemId() != null) {
            return reference.existingItemId();
        }
        Long itemId = addedIds.get(reference.addedByOperationKey());
        if (itemId == null) {
            throw invalidItem();
        }
        return itemId;
    }

    private static void requireRevisionDay(Map<Long, ItineraryModels.Day> days, long dayId) {
        if (!days.containsKey(dayId)) {
            throw invalidItem();
        }
    }

    private static RevisionItem requireRevisionItem(Map<Long, RevisionItem> items, long itemId) {
        RevisionItem item = items.get(itemId);
        if (item == null) {
            throw invalidItem();
        }
        return item;
    }

    private static void requireSingleRevisionChange(Set<Long> changed, long itemId) {
        if (!changed.add(itemId)) {
            throw invalidItem();
        }
    }

    private static long appendRevisionPosition(Map<Long, RevisionItem> items, long dayId) {
        long maximum = items.values().stream()
                .filter(item -> item.dayId() == dayId)
                .mapToLong(RevisionItem::position)
                .max()
                .orElse(0L);
        try {
            return Math.addExact(maximum, POSITION_GAP);
        } catch (ArithmeticException overflow) {
            throw invalidItem();
        }
    }

    private record RevisionState(Map<Long, RevisionItem> items) {
    }

    private record RevisionItem(
            long id,
            long dayId,
            String title,
            String placeName,
            java.time.LocalTime startTime,
            java.time.LocalTime endTime,
            String notes,
            java.math.BigDecimal estimatedCost,
            long position,
            boolean added
    ) {
        static RevisionItem from(ItineraryModels.Item item) {
            return new RevisionItem(
                    item.id(), item.dayId(), item.title(), item.placeName(), item.startTime(),
                    item.endTime(), item.notes(), item.estimatedCost(), item.position(), false
            );
        }

        RevisionItem withPosition(long nextPosition) {
            return new RevisionItem(
                    id, dayId, title, placeName, startTime, endTime, notes,
                    estimatedCost, nextPosition, added
            );
        }

        ItineraryModels.Item asDomainItem() {
            return new ItineraryModels.Item(
                    id, dayId, title, placeName, startTime, endTime, notes,
                    estimatedCost, position, null
            );
        }

        boolean matches(ItineraryModels.Item item) {
            return dayId == item.dayId()
                    && position == item.position()
                    && Objects.equals(title, item.title())
                    && Objects.equals(placeName, item.placeName())
                    && Objects.equals(startTime, item.startTime())
                    && Objects.equals(endTime, item.endTime())
                    && Objects.equals(notes, item.notes())
                    && Objects.equals(estimatedCost, item.estimatedCost());
        }
    }

    private static ItineraryModels.Day requireDay(
            ItineraryModels.Snapshot snapshot,
            long dayId
    ) {
        return snapshot.days().stream()
                .filter(day -> day.id() == dayId)
                .findFirst()
                .orElseThrow(DefaultItineraryApplicationService::invalidItem);
    }

    private static ItineraryModels.Item requireItem(
            ItineraryModels.Snapshot snapshot,
            long itemId
    ) {
        return snapshot.days().stream()
                .flatMap(day -> day.items().stream())
                .filter(item -> item.id() == itemId)
                .findFirst()
                .orElseThrow(DefaultItineraryApplicationService::invalidItem);
    }

    private static void assertVersion(ItineraryModels.Snapshot snapshot, long expectedVersion) {
        if (snapshot.version() != expectedVersion) {
            throw versionConflict();
        }
    }

    private ItineraryCommands.CommandResult complete(
            java.util.UUID commandId,
            long itineraryId,
            Long itemId,
            long version
    ) {
        if (repository.completeCommand(commandId, itineraryId, itemId, version) != 1) {
            throw new IllegalStateException("Itinerary command completion failed");
        }
        return new ItineraryCommands.CommandResult(itineraryId, itemId, version, false);
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

    private static ItineraryException versionConflict() {
        return new ItineraryException(ItineraryError.VERSION_CONFLICT, "行程已被更新，请重新加载");
    }

    private static ItineraryException invalidItem() {
        return new ItineraryException(ItineraryError.INVALID_ITEM, "安排信息无效");
    }

    private record Cursor(Instant updatedAt, long id) {
    }
}
