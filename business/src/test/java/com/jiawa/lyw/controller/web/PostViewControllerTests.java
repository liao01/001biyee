package com.jiawa.lyw.controller.web;

import com.jiawa.lyw.context.LoginMemberContext;
import com.jiawa.lyw.controller.ControllerExceptionHandler;
import com.jiawa.lyw.domain.PostView;
import com.jiawa.lyw.domain.PostViewExample;
import com.jiawa.lyw.mapper.PostViewMapper;
import com.jiawa.lyw.mapper.PostViewMapperCust;
import com.jiawa.lyw.resp.MemberLoginResp;
import com.jiawa.lyw.service.PostViewService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PostViewControllerTests {

    private MockMvc mockMvc;

    @Mock
    private PostViewMapper postViewMapper;

    @Mock
    private PostViewMapperCust postViewMapperCust;

    @BeforeEach
    void setUp() {
        PostViewService service = new PostViewService();
        ReflectionTestUtils.setField(service, "postViewMapper", postViewMapper);
        ReflectionTestUtils.setField(service, "postViewMapperCust", postViewMapperCust);

        PostViewController controller = new PostViewController();
        ReflectionTestUtils.setField(controller, "postViewService", service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ControllerExceptionHandler())
                .build();

        MemberLoginResp member = new MemberLoginResp();
        member.setId(100L);
        LoginMemberContext.setMember(member);
    }

    @AfterEach
    void tearDown() {
        LoginMemberContext.removeMember();
    }

    @Test
    void reopeningTheSamePostShouldUpdateTheRecentViewInsteadOfCreatingADuplicate() throws Exception {
        AtomicReference<PostView> stored = new AtomicReference<>();
        when(postViewMapper.selectByExample(any(PostViewExample.class)))
                .thenAnswer(invocation -> stored.get() == null ? List.of() : List.of(stored.get()));
        doAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return 1;
        }).when(postViewMapper).insert(any(PostView.class));
        doAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return 1;
        }).when(postViewMapper).updateByPrimaryKeySelective(any(PostView.class));

        String forgedActorRequest = """
                {"postId": 42, "userId": 999}
                """;
        mockMvc.perform(post("/web/postview/save")
                        .contentType("application/json")
                        .content(forgedActorRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(post("/web/postview/save")
                        .contentType("application/json")
                        .content(forgedActorRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(postViewMapper, times(1)).insert(any(PostView.class));
        verify(postViewMapper, times(1)).updateByPrimaryKeySelective(any(PostView.class));
        org.junit.jupiter.api.Assertions.assertEquals(100L, stored.get().getUserId());
        org.junit.jupiter.api.Assertions.assertEquals(42L, stored.get().getPostId());
        org.junit.jupiter.api.Assertions.assertNotNull(stored.get().getViewTime());
    }
}
