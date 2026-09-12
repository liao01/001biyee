package com.jiawa.lyw.itineraryplanning.api;

import com.jiawa.lyw.identity.domain.IdentityException;
import com.jiawa.lyw.itinerary.domain.ItineraryException;
import com.jiawa.lyw.itineraryplanning.domain.PlanningError;
import com.jiawa.lyw.itineraryplanning.domain.PlanningException;
import com.jiawa.lyw.resp.CommonResp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = ItineraryPlanningController.class)
public class ItineraryPlanningExceptionHandler {
    @ExceptionHandler(PlanningException.class)
    public ResponseEntity<CommonResp<ItineraryPlanningHttpModels.ErrorContent>> planningFailure(
            PlanningException exception
    ) {
        int status = switch (exception.error()) {
            case PLANNING_NOT_FOUND, PROPOSAL_NOT_FOUND -> 404;
            case PROVIDER_RATE_LIMITED -> 429;
            case PROVIDER_UNAVAILABLE, PROVIDER_TIMEOUT -> 503;
            case INVALID_CONTRACT -> 422;
            case VERSION_CONFLICT, GENERATION_IN_PROGRESS, PROPOSAL_NOT_READY,
                    PROPOSAL_EXPIRED, IDEMPOTENCY_CONFLICT -> 409;
            default -> 400;
        };
        return failure(status, exception.error(), safeMessage(exception.error()));
    }

    @ExceptionHandler(IdentityException.class)
    public ResponseEntity<CommonResp<ItineraryPlanningHttpModels.ErrorContent>> unauthenticated() {
        return failure(401, null, "请先登录");
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<CommonResp<ItineraryPlanningHttpModels.ErrorContent>> concurrentResolution() {
        return failure(409, PlanningError.IDEMPOTENCY_CONFLICT, "该建议已经由另一个决定处理");
    }

    @ExceptionHandler(ItineraryException.class)
    public ResponseEntity<CommonResp<ItineraryPlanningHttpModels.ErrorContent>> itineraryConflict(
            ItineraryException exception
    ) {
        return switch (exception.error()) {
            case VERSION_CONFLICT -> failure(
                    409, PlanningError.PROPOSAL_EXPIRED, "行程已变化，请重新生成建议"
            );
            case IDEMPOTENCY_CONFLICT -> failure(
                    409, PlanningError.IDEMPOTENCY_CONFLICT, "决定编号已被其他请求使用"
            );
            case TIME_CONFLICT -> failure(
                    400, PlanningError.TIME_CONFLICT, "建议存在时间冲突"
            );
            default -> failure(400, PlanningError.INVALID_REQUEST, "规划请求无效");
        };
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class,
            NullPointerException.class
    })
    public ResponseEntity<CommonResp<ItineraryPlanningHttpModels.ErrorContent>> invalidRequest() {
        return failure(400, PlanningError.INVALID_REQUEST, "请求参数不正确");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonResp<ItineraryPlanningHttpModels.ErrorContent>> unexpected(
            Exception exception
    ) {
        log.error("行程规划请求失败 type={}", exception.getClass().getSimpleName());
        return failure(500, null, "行程规划服务暂时不可用，请稍后重试");
    }

    private static ResponseEntity<CommonResp<ItineraryPlanningHttpModels.ErrorContent>> failure(
            int status,
            PlanningError error,
            String message
    ) {
        CommonResp<ItineraryPlanningHttpModels.ErrorContent> body = new CommonResp<>(
                error == null ? null : new ItineraryPlanningHttpModels.ErrorContent(error.name())
        );
        body.setSuccess(false);
        body.setMessage(message);
        return ResponseEntity.status(status).header(HttpHeaders.CACHE_CONTROL, "no-store").body(body);
    }

    private static String safeMessage(PlanningError error) {
        return switch (error) {
            case PLANNING_NOT_FOUND -> "规划请求不存在";
            case PROPOSAL_NOT_FOUND -> "修订建议不存在";
            case VERSION_CONFLICT -> "规划请求已更新，请重新加载";
            case GENERATION_IN_PROGRESS -> "规划正在生成";
            case PROPOSAL_NOT_READY -> "建议当前不可确认";
            case PROPOSAL_EXPIRED -> "行程已变化，请重新生成建议";
            case INVALID_SELECTION -> "所选操作缺少依赖或已无效";
            case IDEMPOTENCY_CONFLICT -> "决定编号已被其他请求使用";
            case PROVIDER_RATE_LIMITED -> "AI 规划请求过多，请稍后重试";
            case PROVIDER_UNAVAILABLE, PROVIDER_TIMEOUT -> "AI 规划服务暂时不可用";
            case INVALID_CONTRACT -> "AI 返回的建议格式无效";
            case TIME_CONFLICT -> "建议存在时间冲突";
            case BUDGET_EXCEEDED -> "建议超过规划预算";
            default -> "规划请求无效";
        };
    }
}
