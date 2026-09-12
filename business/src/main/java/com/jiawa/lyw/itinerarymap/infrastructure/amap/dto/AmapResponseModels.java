package com.jiawa.lyw.itinerarymap.infrastructure.amap.dto;

/** 高德适配器内部的稳定错误分类；不含供应商响应正文或请求细节。 */
public final class AmapResponseModels {
    private AmapResponseModels() {
    }

    public enum ProviderFailure {
        PROVIDER_UNAVAILABLE,
        PROVIDER_RATE_LIMITED,
        PROVIDER_TIMEOUT,
        INVALID_PROVIDER_RESPONSE
    }

    public static final class AmapProviderException extends RuntimeException {
        private final ProviderFailure failure;

        public AmapProviderException(ProviderFailure failure) {
            super(failure.name());
            this.failure = failure;
        }

        public ProviderFailure failure() {
            return failure;
        }
    }
}
