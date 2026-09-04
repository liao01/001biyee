package com.jiawa.lyw.itineraryplanning.application;

import com.jiawa.lyw.itineraryplanning.domain.PlanningModels;

import java.util.List;
import java.util.UUID;

public final class PlanningCommands {
    private PlanningCommands() {
    }

    public record SaveDraft(Long requestId, long expectedVersion, PlanningModels.RequestDraft draft) {
        public SaveDraft {
            if ((requestId == null && expectedVersion != 0)
                    || (requestId != null && (requestId <= 0 || expectedVersion < 1))
                    || draft == null) {
                throw new IllegalArgumentException("规划草稿命令无效");
            }
        }
    }

    public record Confirm(
            UUID decisionId,
            UUID itineraryCommandId,
            long expectedItineraryVersion,
            List<String> selectedOperationKeys
    ) {
        public Confirm {
            if (decisionId == null || itineraryCommandId == null || expectedItineraryVersion < 1
                    || selectedOperationKeys == null || selectedOperationKeys.isEmpty()
                    || selectedOperationKeys.stream().anyMatch(key -> key == null || key.isBlank())
                    || selectedOperationKeys.stream().distinct().count() != selectedOperationKeys.size()) {
                throw new IllegalArgumentException("建议确认命令无效");
            }
            selectedOperationKeys = List.copyOf(selectedOperationKeys);
        }
    }

    public record Reject(UUID decisionId) {
        public Reject {
            if (decisionId == null) {
                throw new IllegalArgumentException("建议拒绝命令无效");
            }
        }
    }
}
