package com.jiawa.lyw.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Aspect
@Component
public class LogAspect {
    /**
     * 只记录调用元数据。请求/响应、Cookie、参数和异常内容可能携带凭据，
     * 不进行通用序列化；关联 ID 统一由 LogInterceptor 提供。
     */
    @Around("execution(public * com.jiawa..*Controller.*(..))")
    public Object doAround(ProceedingJoinPoint invocation) throws Throwable {
        long started = System.nanoTime();
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        String method = attributes == null ? "INTERNAL" : attributes.getRequest().getMethod();
        String handler = invocation.getSignature().toShortString();
        try {
            return invocation.proceed();
        } finally {
            log.info("接口耗时 method={} handler={} elapsedMs={}",
                    method, handler, (System.nanoTime() - started) / 1_000_000);
        }
    }
}
