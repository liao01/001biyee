package com.jiawa.lyw.config;

import com.jiawa.lyw.interceptor.AdminLoginInterceptor;
import com.jiawa.lyw.interceptor.LogInterceptor;
import com.jiawa.lyw.interceptor.WebLoginInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SpringMvcConfig implements WebMvcConfigurer {
    @Autowired
    private WebLoginInterceptor webLoginInterceptor;
    @Autowired
    private LogInterceptor logInterceptor;
    @Autowired
    private AdminLoginInterceptor adminLoginInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(logInterceptor);

        registry.addInterceptor(webLoginInterceptor)
                .addPathPatterns("/web/**")
                .excludePathPatterns(
                        "/web/kaptcha/image-code/*",
                        "/web/member/login",
                        "/web/member/register",
                        "/web/member/reset",
                        "/web/sms-code/send-for-register",
                        "/web/sms-code/send-for-reset",
                        "/web/post/post-findAll",
                        "/web/comment/findall-comment"
                );

        registry.addInterceptor(adminLoginInterceptor)
                .addPathPatterns("/admin/**")
                .excludePathPatterns(
                        "/admin/kaptcha/image-code/*",
                        "/admin/member/**",
                        "/admin/report/**"
                );


    }
}
