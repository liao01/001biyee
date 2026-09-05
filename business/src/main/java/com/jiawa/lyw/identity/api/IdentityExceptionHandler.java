package com.jiawa.lyw.identity.api;

import com.jiawa.lyw.identity.domain.IdentityException;
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

@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = IdentityController.class)
public class IdentityExceptionHandler {
    @ExceptionHandler(IdentityException.class)
    public ResponseEntity<CommonResp<Object>> identityFailure(IdentityException exception) {
        int status = switch (exception.reason()) {
            case UNAUTHENTICATED -> 401;
            case EMAIL_NOT_VERIFIED -> 403;
            default -> 400;
        };
        return failure(status, exception.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<CommonResp<Object>> invalidRequest() {
        return failure(400, "请求参数不正确");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonResp<Object>> unexpectedFailure(Exception exception) {
        log.error("身份请求失败 type={}", exception.getClass().getSimpleName());
        return failure(500, "身份服务暂时不可用，请稍后重试");
    }

    private ResponseEntity<CommonResp<Object>> failure(int status, String message) {
        CommonResp<Object> response = new CommonResp<>();
        response.setSuccess(false);
        response.setMessage(message);
        return ResponseEntity.status(status).header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
    }
}
