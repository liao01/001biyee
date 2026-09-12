package com.jiawa.lyw.itineraryplanning.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiawa.lyw.itineraryplanning.domain.PlanningError;
import com.jiawa.lyw.itineraryplanning.domain.PlanningException;
import com.jiawa.lyw.itineraryplanning.domain.PlanningModels;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RevisionContractParserTests {
    private final RevisionContractParser parser = new RevisionContractParser(
            new ObjectMapper().findAndRegisterModules(),
            PlanningModels.REVISION_CONTRACT_V1
    );

    @Test
    void parsesTheVersionedDifyOutputIntoProviderIndependentOperations() {
        PlanningModels.CandidateProposal proposal = parser.parse("""
                {
                  "contract_version":"itinerary-revision/v1",
                  "summary":"杭州两日优化建议",
                  "operations":[
                    {"operation_key":"add-lunch","type":"ADD_ITEM","summary":"增加午餐",
                     "item":{"date":"2026-10-02","title":"杭帮菜","place_name":"餐厅",
                             "start_time":"11:30:00","end_time":"13:00:00","notes":null,
                             "estimated_cost":120.00}},
                    {"operation_key":"order-day","type":"REORDER_DAY_ITEMS","summary":"调整顺序",
                     "target_date":"2026-10-02","item_references":[
                       {"existing_item_id":1001}, {"added_by_operation_key":"add-lunch"}
                     ]}
                  ],
                  "knowledge_reference_ids":["kb:hangzhou-guide:42"]
                }
                """);

        assertEquals(PlanningModels.REVISION_CONTRACT_V1, proposal.contractVersion());
        assertEquals(2, proposal.operations().size());
        assertInstanceOf(PlanningModels.AddItemOperation.class, proposal.operations().get(0));
        assertInstanceOf(PlanningModels.ReorderDayItemsOperation.class, proposal.operations().get(1));
        assertEquals("kb:hangzhou-guide:42", proposal.knowledgeReferenceIds().get(0));
    }

    @Test
    void rejectsMarkdownUnknownFieldsPrivacyFieldsAndUnknownContracts() {
        assertContractError("```json\n{}\n```");
        assertContractError("""
                {"contract_version":"itinerary-revision/v1","summary":"x","operations":[],
                 "knowledge_reference_ids":[],"raw_prompt":"secret"}
                """);
        assertContractError("""
                {"contract_version":"itinerary-revision/v1","summary":"x","operations":[],
                 "knowledge_reference_ids":[],"member_email":"person@example.com"}
                """);
        assertContractError("""
                {"contract_version":"itinerary-revision/v999","summary":"x","operations":[],
                 "knowledge_reference_ids":[]}
                """);
    }

    @Test
    void rejectsOperationShapesThatDoNotMatchTheirDeclaredType() {
        assertContractError("""
                {"contract_version":"itinerary-revision/v1","summary":"x",
                 "operations":[{"operation_key":"bad","type":"DELETE_ITEM","summary":"x",
                                "target_item_id":1,"item":{"date":"2026-10-02"}}],
                 "knowledge_reference_ids":[]}
                """);
    }

    private void assertContractError(String json) {
        PlanningException exception = assertThrows(PlanningException.class, () -> parser.parse(json));
        assertEquals(PlanningError.INVALID_CONTRACT, exception.error());
    }
}
