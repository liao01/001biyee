package com.jiawa.lyw.itineraryplanning.domain;

import com.jiawa.lyw.itinerary.domain.ItineraryModels;
import com.jiawa.lyw.itinerary.domain.ItineraryStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Currency;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RevisionProposalValidatorTests {
    private static final LocalDate DAY_ONE = LocalDate.of(2026, 10, 2);
    private static final LocalDate DAY_TWO = LocalDate.of(2026, 10, 3);
    private final RevisionProposalValidator validator = new RevisionProposalValidator(
            PlanningModels.REVISION_CONTRACT_V1, 80
    );

    @Test
    void validatesACompleteProposalAndCapturesSelectionDependencies() {
        PlanningModels.AddItemOperation add = new PlanningModels.AddItemOperation(
                "add-lunch",
                "增加午餐",
                fields(DAY_ONE, "品尝杭帮菜", "餐厅", "11:30", "13:00", "120.00")
        );
        PlanningModels.ReorderDayItemsOperation reorder = new PlanningModels.ReorderDayItemsOperation(
                "reorder-day-one",
                "调整第一天顺序",
                DAY_ONE,
                List.of(
                        PlanningModels.ItemReference.existing(1001L),
                        PlanningModels.ItemReference.addedBy("add-lunch")
                )
        );
        PlanningModels.CandidateProposal proposal = proposal(List.of(add, reorder));

        PlanningModels.ValidatedProposal validated = validator.validate(snapshot(), request(), proposal);

        assertEquals(new BigDecimal("220.00"), validated.projectedCost());
        assertEquals(Set.of("add-lunch"), validated.dependencies().get("reorder-day-one"));
        assertEquals(List.of("kb:hangzhou-guide:42"), validated.proposal().knowledgeReferenceIds());
    }

    @Test
    void rejectsDatesOutsideTheRequestOrItinerary() {
        PlanningModels.CandidateProposal proposal = proposal(List.of(
                new PlanningModels.AddItemOperation(
                        "outside-date", "越界日期",
                        fields(DAY_TWO.plusDays(2), "远途", "远方", "09:00", "10:00", "10.00")
                )
        ));

        assertError(PlanningError.DATE_OUT_OF_RANGE, proposal);
    }

    @Test
    void rejectsTimeConflictsAfterApplyingAllOperations() {
        PlanningModels.CandidateProposal proposal = proposal(List.of(
                new PlanningModels.AddItemOperation(
                        "overlap", "冲突安排",
                        fields(DAY_ONE, "重叠活动", "湖边", "09:30", "10:30", "10.00")
                )
        ));

        assertError(PlanningError.TIME_CONFLICT, proposal);
    }

    @Test
    void rejectsInvalidPlacesAndBudgetOverflowBeforeDisplay() {
        PlanningModels.CandidateProposal noPlace = proposal(List.of(
                new PlanningModels.AddItemOperation(
                        "no-place", "缺少地点",
                        fields(DAY_TWO, "随便走走", " ", "09:00", "10:00", "10.00")
                )
        ));
        assertError(PlanningError.INVALID_PLACE, noPlace);

        PlanningModels.CandidateProposal expensive = proposal(List.of(
                new PlanningModels.AddItemOperation(
                        "too-expensive", "超预算",
                        fields(DAY_TWO, "高价体验", "会场", "09:00", "10:00", "2999.00")
                )
        ));
        assertError(PlanningError.BUDGET_EXCEEDED, expensive);
    }

    @Test
    void rejectsControlCharactersInPlaceNamesBeforeDisplay() {
        PlanningModels.CandidateProposal proposal = proposal(List.of(
                new PlanningModels.AddItemOperation(
                        "unsafe-place", "控制字符地点",
                        fields(DAY_TWO, "散步", "西湖\u0001北岸", "09:00", "10:00", "10.00")
                )
        ));

        assertError(PlanningError.INVALID_PLACE, proposal);
    }

    @Test
    void rejectsUnknownTargetsAndIncompleteReorders() {
        PlanningModels.CandidateProposal unknown = proposal(List.of(
                new PlanningModels.DeleteItemOperation("delete-missing", "删除不存在安排", 9999L)
        ));
        assertError(PlanningError.ITEM_NOT_FOUND, unknown);

        PlanningModels.CandidateProposal incomplete = proposal(List.of(
                new PlanningModels.ReorderDayItemsOperation(
                        "bad-order", "不完整排序", DAY_ONE, List.of()
                )
        ));
        assertError(PlanningError.INVALID_REORDER, incomplete);
    }

    @Test
    void rejectsUnknownContractsDuplicateKeysAndUnsafeKnowledgeReferences() {
        PlanningModels.CandidateProposal unknownContract = new PlanningModels.CandidateProposal(
                "itinerary-revision/v999", "未知契约",
                List.of(new PlanningModels.DeleteItemOperation("delete-one", "删除", 1001L)),
                List.of()
        );
        assertError(PlanningError.UNSUPPORTED_CONTRACT, unknownContract);

        PlanningModels.CandidateProposal duplicates = proposal(List.of(
                new PlanningModels.DeleteItemOperation("same-key", "删除", 1001L),
                new PlanningModels.DeleteItemOperation("same-key", "再次删除", 1001L)
        ));
        assertError(PlanningError.DUPLICATE_OPERATION, duplicates);

        PlanningModels.CandidateProposal unsafeReference = new PlanningModels.CandidateProposal(
                PlanningModels.REVISION_CONTRACT_V1,
                "引用正文",
                List.of(new PlanningModels.DeleteItemOperation("delete-one", "删除", 1001L)),
                List.of("这是一整段不应保存的知识库文档正文")
        );
        assertError(PlanningError.INVALID_KNOWLEDGE_REFERENCE, unsafeReference);
    }

    @Test
    void rejectsTooManyOperations() {
        RevisionProposalValidator oneOperation = new RevisionProposalValidator(
                PlanningModels.REVISION_CONTRACT_V1, 1
        );
        PlanningModels.CandidateProposal proposal = proposal(List.of(
                new PlanningModels.DeleteItemOperation("delete-one", "删除", 1001L),
                new PlanningModels.AddItemOperation(
                        "add-one", "新增",
                        fields(DAY_TWO, "散步", "河边", "09:00", "10:00", "0.00")
                )
        ));

        PlanningException exception = assertThrows(
                PlanningException.class,
                () -> oneOperation.validate(snapshot(), request(), proposal)
        );
        assertEquals(PlanningError.TOO_MANY_OPERATIONS, exception.error());
    }

    @Test
    void updateAndDeleteAreAppliedBeforeFinalConflictAndBudgetChecks() {
        PlanningModels.CandidateProposal proposal = proposal(List.of(
                new PlanningModels.UpdateItemOperation(
                        "move-existing", "移动已有安排", 1001L,
                        fields(DAY_TWO, "西湖晨游", "西湖", "08:00", "09:00", "80.00")
                ),
                new PlanningModels.AddItemOperation(
                        "replace-slot", "补充第一天",
                        fields(DAY_ONE, "博物馆", "浙江省博物馆", "09:00", "10:00", "20.00")
                )
        ));

        PlanningModels.ValidatedProposal validated = validator.validate(snapshot(), request(), proposal);

        assertEquals(new BigDecimal("100.00"), validated.projectedCost());
        assertTrue(validated.dependencies().get("move-existing").isEmpty());
    }

    private void assertError(PlanningError expected, PlanningModels.CandidateProposal proposal) {
        PlanningException exception = assertThrows(
                PlanningException.class,
                () -> validator.validate(snapshot(), request(), proposal)
        );
        assertEquals(expected, exception.error());
    }

    private PlanningModels.CandidateProposal proposal(List<PlanningModels.RevisionOperation> operations) {
        return new PlanningModels.CandidateProposal(
                PlanningModels.REVISION_CONTRACT_V1,
                "经过知识库参考的三日建议",
                operations,
                List.of("kb:hangzhou-guide:42")
        );
    }

    private PlanningModels.ItemFields fields(
            LocalDate date,
            String title,
            String place,
            String start,
            String end,
            String cost
    ) {
        return new PlanningModels.ItemFields(
                date,
                title,
                place,
                LocalTime.parse(start),
                LocalTime.parse(end),
                null,
                new BigDecimal(cost)
        );
    }

    private PlanningModels.RequestDraft request() {
        return new PlanningModels.RequestDraft(
                42L,
                DAY_ONE,
                DAY_TWO,
                new BigDecimal("3000.00"),
                Currency.getInstance("CNY"),
                2,
                new PlanningModels.Preferences(
                        PlanningModels.TravelPace.BALANCED,
                        Set.of(PlanningModels.PreferenceTag.CULTURE, PlanningModels.PreferenceTag.FOOD),
                        null
                ),
                List.of(new PlanningModels.DestinationInput(
                        "杭州", "CN", ZoneId.of("Asia/Shanghai")
                ))
        );
    }

    private ItineraryModels.Snapshot snapshot() {
        ItineraryModels.Item existing = new ItineraryModels.Item(
                1001L,
                501L,
                "西湖晨游",
                "西湖",
                LocalTime.of(9, 0),
                LocalTime.of(10, 0),
                null,
                new BigDecimal("100.00"),
                1024L,
                null
        );
        return new ItineraryModels.Snapshot(
                42L,
                7L,
                "杭州三日",
                DAY_ONE,
                DAY_TWO,
                ZoneId.of("Asia/Shanghai"),
                Currency.getInstance("CNY"),
                ItineraryStatus.DRAFT,
                3L,
                List.of(new ItineraryModels.Destination(
                        301L, "杭州", "CN", ZoneId.of("Asia/Shanghai"), 1024L
                )),
                List.of(
                        new ItineraryModels.Day(501L, DAY_ONE, List.of(existing)),
                        new ItineraryModels.Day(502L, DAY_TWO, List.of())
                )
        );
    }
}
