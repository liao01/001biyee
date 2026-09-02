package com.jiawa.lyw.itinerary.api;

import com.jiawa.lyw.identity.domain.IdentityException;
import com.jiawa.lyw.itinerary.domain.ItineraryError;
import com.jiawa.lyw.itinerary.domain.ItineraryException;
import com.jiawa.lyw.resp.CommonResp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = ItineraryController.class)
public class ItineraryExceptionHandler {
    @ExceptionHandler(ItineraryException.class)
    public ResponseEntity<CommonResp<ItineraryHttpModels.ErrorContent>> itineraryFailure(
            ItineraryException exception
    ) {
        int status = switch (exception.error()) {
            case ITINERARY_NOT_FOUND -> 404;
            case VERSION_CONFLICT, IDEMPOTENCY_CONFLICT, INVALID_STATUS_TRANSITION -> 409;
            default -> 400;
        };
        return failure(status, exception.error(), safeMessage(exception.error()));
    }

    @ExceptionHandler(IdentityException.class)
    public ResponseEntity<CommonResp<ItineraryHttpModels.ErrorContent>> unauthenticated() {
        return failure(401, null, "请先登录");
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            NullPointerException.class
    })
    public ResponseEntity<CommonResp<ItineraryHttpModels.ErrorContent>> invalidRequest() {
        return failure(400, ItineraryError.INVALID_ITINERARY, "请求参数不正确");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonResp<ItineraryHttpModels.ErrorContent>> unexpectedFailure(
            Exception exception
    ) {
        log.error("行程请求失败 type={}", exception.getClass().getSimpleName());
        return failure(500, null, "行程服务暂时不可用，请稍后重试");
    }

    private ResponseEntity<CommonResp<ItineraryHttpModels.ErrorContent>> failure(
            int status,
            ItineraryError error,
            String message
    ) {
        CommonResp<ItineraryHttpModels.ErrorContent> response = new CommonResp<>(
                error == null ? null : new ItineraryHttpModels.ErrorContent(error.name())
        );
        response.setSuccess(false);
        response.setMessage(message);
        return ResponseEntity.status(status)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(response);
    }

    private static String safeMessage(ItineraryError error) {
        return switch (error) {
            case INVALID_ITINERARY -> "行程信息无效";
            case INVALID_DESTINATION -> "目的地信息无效";
            case INVALID_ITEM -> "安排信息无效";
            case TIME_CONFLICT -> "安排时间存在冲突";
            case DATE_RANGE_CONTAINS_ITEMS -> "缩短后的日期范围仍包含安排";
            case ITINERARY_NOT_FOUND -> "行程不存在";
            case VERSION_CONFLICT -> "行程已被更新，请重新加载";
            case IDEMPOTENCY_CONFLICT -> "命令已被其他请求使用";
            case INVALID_STATUS_TRANSITION -> "行程状态转换无效";
        };
    }
}
