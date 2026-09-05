package com.jiawa.lyw.itineraryplanning.infrastructure;

import java.util.Map;

final class DifyWorkflowModels {
    private DifyWorkflowModels() {
    }

    record RunRequest(Map<String, String> inputs, String response_mode, String user) {
        RunRequest {
            inputs = Map.copyOf(inputs);
        }
    }

    record RawResponse(int status, byte[] body) {
        RawResponse {
            body = body.clone();
        }
    }
}
