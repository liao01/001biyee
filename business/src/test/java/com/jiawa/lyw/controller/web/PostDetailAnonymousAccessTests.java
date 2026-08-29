package com.jiawa.lyw.controller.web;

import com.jiawa.lyw.config.SpringMvcConfig;
import com.jiawa.lyw.controller.ControllerExceptionHandler;
import com.jiawa.lyw.interceptor.AdminLoginInterceptor;
import com.jiawa.lyw.interceptor.LogInterceptor;
import com.jiawa.lyw.interceptor.WebLoginInterceptor;
import com.jiawa.lyw.resp.PostDetailResp;
import com.jiawa.lyw.service.PostService;
import com.jiawa.lyw.service.PostDetailService;
import com.jiawa.lyw.service.PostCategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = PostDetailAnonymousAccessTests.TestConfiguration.class)
class PostDetailAnonymousAccessTests {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private PostService postService;

    @MockitoBean
    private PostDetailService postDetailService;

    @MockitoBean
    private PostCategoryService postCategoryService;

    @MockitoBean
    private WebLoginInterceptor webLoginInterceptor;

    @MockitoBean
    private LogInterceptor logInterceptor;

    @MockitoBean
    private AdminLoginInterceptor adminLoginInterceptor;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void publicDetailShouldBypassLoginInterceptor() throws Exception {
        when(logInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        when(postDetailService.findPublicDetail(42L)).thenReturn(new PostDetailResp());

        mockMvc.perform(get("/web/post/detail").param("postId", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(webLoginInterceptor, never()).preHandle(any(), any(), any());
    }

    @Test
    void publicCategoriesShouldBypassLoginInterceptor() throws Exception {
        when(logInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        when(postCategoryService.listEnabled()).thenReturn(java.util.List.of());

        mockMvc.perform(get("/web/post/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(webLoginInterceptor, never()).preHandle(any(), any(), any());
    }

    @Test
    void viewerStateShouldRemainProtectedByTheLoginInterceptor() throws Exception {
        when(logInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        doAnswer(invocation -> {
            invocation.<jakarta.servlet.http.HttpServletResponse>getArgument(1).setStatus(401);
            return false;
        }).when(webLoginInterceptor).preHandle(any(), any(), any());

        mockMvc.perform(get("/web/post/detail/viewer-state").param("postId", "42"))
                .andExpect(status().isUnauthorized());

        verify(webLoginInterceptor).preHandle(any(), any(), any());
        verify(postDetailService, never()).findViewerState(any());
    }

    @Test
    void interactionWritesShouldRemainProtectedByTheLoginInterceptor() throws Exception {
        when(logInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        doAnswer(invocation -> {
            invocation.<jakarta.servlet.http.HttpServletResponse>getArgument(1).setStatus(401);
            return false;
        }).when(webLoginInterceptor).preHandle(any(), any(), any());

        mockMvc.perform(post("/web/post/detail/like")
                        .contentType("application/json")
                        .content("""
                                {"postId": 42, "active": true}
                                """))
                .andExpect(status().isUnauthorized());

        verify(webLoginInterceptor).preHandle(any(), any(), any());
        verify(postDetailService, never()).setLike(any(), anyBoolean());
    }

    @Configuration
    @EnableWebMvc
    @Import({PostController.class, SpringMvcConfig.class, ControllerExceptionHandler.class})
    static class TestConfiguration {
    }
}
