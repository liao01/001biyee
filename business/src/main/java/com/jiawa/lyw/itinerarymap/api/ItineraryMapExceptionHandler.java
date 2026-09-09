package com.jiawa.lyw.itinerarymap.api;

import com.jiawa.lyw.identity.domain.IdentityException;
import com.jiawa.lyw.itinerary.domain.ItineraryError;
import com.jiawa.lyw.itinerary.domain.ItineraryException;
import com.jiawa.lyw.itinerarymap.domain.ItineraryMapError;
import com.jiawa.lyw.itinerarymap.domain.ItineraryMapException;
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

/** 只返回固定中文文本和稳定错误码，绝不把供应商或底层异常透传到地图 API。 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = ItineraryMapController.class)
public class ItineraryMapExceptionHandler {
    @ExceptionHandler(ItineraryMapException.class)
    public ResponseEntity<CommonResp<ItineraryMapHttpModels.ErrorContent>> mapFailure(
            ItineraryMapException exception
    ) {
        int status = exception.error() == ItineraryMapError.MAP_PROVIDER_UNAVAILABLE ? 503 : 400;
        return failure(status, exception.error().name(), safeMessage(exception.error()));
    }

    @ExceptionHandler(ItineraryException.class)
    public ResponseEntity<CommonResp<ItineraryMapHttpModels.ErrorContent>> itineraryFailure(
            ItineraryException exception
    ) {
        int status = switch (exception.error()) {
            case ITINERARY_NOT_FOUND, ITINERARY_ITEM_NOT_FOUND, ITINERARY_DAY_NOT_FOUND -> 404;
            case VERSION_CONFLICT, IDEMPOTENCY_CONFLICT -> 409;
            default -> 400;
        };
        return failure(status, exception.error().name(), safeMessage(exception.error()));
    }

    @ExceptionHandler(IdentityException.class)
    public ResponseEntity<CommonResp<ItineraryMapHttpModels.ErrorContent>> unauthenticated() {
        return failure(401, null, "请先登录");
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<CommonResp<ItineraryMapHttpModels.ErrorContent>> idempotencyConflict() {
        return failure(409, ItineraryError.IDEMPOTENCY_CONFLICT.name(), "命令已被其他请求使用");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<CommonResp<ItineraryMapHttpModels.ErrorContent>> typeMismatch(
            MethodArgumentTypeMismatchException exception
    ) {
        if ("limit".equals(exception.getName())) {
            return failure(400, ItineraryMapError.INVALID_PLACE_QUERY.name(), "地点搜索条件无效");
        }
        return failure(400, ItineraryMapError.INVALID_MAP_REQUEST.name(), "地图请求无效");
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, IllegalArgumentException.class, NullPointerException.class})
    public ResponseEntity<CommonResp<ItineraryMapHttpModels.ErrorContent>> invalidRequest() {
        return failure(400, ItineraryMapError.INVALID_MAP_REQUEST.name(), "地图请求无效");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonResp<ItineraryMapHttpModels.ErrorContent>> unexpectedFailure(Exception exception) {
        log.error("行程地图请求失败 type={}", exception.getClass().getSimpleName());
        return failure(500, null, "地图服务暂时不可用，请稍后重试");
    }

    private static ResponseEntity<CommonResp<ItineraryMapHttpModels.ErrorContent>> failure(
            int status,
            String errorCode,
            String message
    ) {
        CommonResp<ItineraryMapHttpModels.ErrorContent> response = new CommonResp<>(
                errorCode == null ? null : new ItineraryMapHttpModels.ErrorContent(errorCode)
        );
        response.setSuccess(false);
        response.setMessage(message);
        return ResponseEntity.status(status).header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
    }

    private static String safeMessage(ItineraryMapError error) {
        return switch (error) {
            case INVALID_PLACE_QUERY -> "地点搜索条件无效";
            case INVALID_ROUTE_MODE -> "路线方式无效";
            case INVALID_MAP_REQUEST -> "地图请求无效";
            case MAP_PROVIDER_UNAVAILABLE -> "地点服务暂时不可用，请稍后重试";
        };
    }

    private static String safeMessage(ItineraryError error) {
        return switch (error) {
            case INVALID_PLACE -> "地点信息无效";
            case ITINERARY_NOT_FOUND -> "行程不存在";
            case ITINERARY_ITEM_NOT_FOUND -> "安排不存在";
            case ITINERARY_DAY_NOT_FOUND -> "日程日不存在";
            case VERSION_CONFLICT -> "行程已被更新，请重新加载";
            case IDEMPOTENCY_CONFLICT -> "命令已被其他请求使用";
            default -> "地图请求无效";
        };
    }
}
