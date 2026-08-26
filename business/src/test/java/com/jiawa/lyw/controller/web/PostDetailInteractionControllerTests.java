package com.jiawa.lyw.controller.web;

import com.jiawa.lyw.context.LoginMemberContext;
import com.jiawa.lyw.controller.ControllerExceptionHandler;
import com.jiawa.lyw.domain.Post;
import com.jiawa.lyw.domain.UserAction;
import com.jiawa.lyw.domain.UserActionExample;
import com.jiawa.lyw.domain.UserFollow;
import com.jiawa.lyw.domain.UserFollowExample;
import com.jiawa.lyw.mapper.PostMapper;
import com.jiawa.lyw.mapper.UserActionMapper;
import com.jiawa.lyw.mapper.UserFollowMapper;
import com.jiawa.lyw.resp.MemberLoginResp;
import com.jiawa.lyw.service.PostDetailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PostDetailInteractionControllerTests {

    private static final long VIEWER_ID = 100L;
    private static final long POST_ID = 42L;
    private static final long AUTHOR_ID = 7L;

    private MockMvc mockMvc;

    @Mock
    private PostMapper postMapper;

    @Mock
    private UserActionMapper userActionMapper;

    @Mock
    private UserFollowMapper userFollowMapper;

    @BeforeEach
    void setUp() {
        PostDetailService postDetailService = new PostDetailService();
        ReflectionTestUtils.setField(postDetailService, "postMapper", postMapper);
        ReflectionTestUtils.setField(postDetailService, "userActionMapper", userActionMapper);
        ReflectionTestUtils.setField(postDetailService, "userFollowMapper", userFollowMapper);

        PostController postController = new PostController();
        ReflectionTestUtils.setField(postController, "postDetailService", postDetailService);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(postController)
                .setControllerAdvice(new ControllerExceptionHandler())
                .setValidator(validator)
                .build();

        loginAs(VIEWER_ID);
        when(postMapper.selectByPrimaryKey(POST_ID)).thenReturn(postOwnedBy(AUTHOR_ID));
    }

    @AfterEach
    void tearDown() {
        LoginMemberContext.removeMember();
    }

    @Test
    void viewerStateShouldReturnLikeFavoriteFollowAndSelfAuthorIndependently() throws Exception {
        when(userActionMapper.countByExample(any(UserActionExample.class)))
                .thenAnswer(invocation -> actionType(invocation.getArgument(0)).equals("like") ? 1L : 0L);
        when(userFollowMapper.countByExample(any(UserFollowExample.class))).thenReturn(1L);

        mockMvc.perform(get("/web/post/detail/viewer-state").param("postId", String.valueOf(POST_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.content.viewerId").value(VIEWER_ID))
                .andExpect(jsonPath("$.content.liked").value(true))
                .andExpect(jsonPath("$.content.favorited").value(false))
                .andExpect(jsonPath("$.content.followed").value(true))
                .andExpect(jsonPath("$.content.selfAuthor").value(false));
    }

    @Test
    void viewerStateShouldIdentifyThePostAuthorAndNeverOfferSelfFollowState() throws Exception {
        loginAs(AUTHOR_ID);
        when(userActionMapper.countByExample(any(UserActionExample.class))).thenReturn(0L);

        mockMvc.perform(get("/web/post/detail/viewer-state").param("postId", String.valueOf(POST_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.followed").value(false))
                .andExpect(jsonPath("$.content.selfAuthor").value(true));
    }

    @Test
    void likeWriteShouldBeIdempotentAndReturnServerConfirmedStateAndCount() throws Exception {
        AtomicBoolean viewerLiked = new AtomicBoolean(false);
        AtomicLong likeCount = new AtomicLong(12L);
        when(userActionMapper.countByExample(any(UserActionExample.class)))
                .thenAnswer(invocation -> hasUserCriterion(invocation.getArgument(0))
                        ? (viewerLiked.get() ? 1L : 0L)
                        : likeCount.get());
        doAnswer(invocation -> {
            viewerLiked.set(true);
            likeCount.incrementAndGet();
            return 1;
        }).when(userActionMapper).insert(any(UserAction.class));

        String request = """
                {"postId": 42, "active": true}
                """;

        mockMvc.perform(post("/web/post/detail/like")
                        .contentType("application/json")
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.active").value(true))
                .andExpect(jsonPath("$.content.count").value(13));

        mockMvc.perform(post("/web/post/detail/like")
                        .contentType("application/json")
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.active").value(true))
                .andExpect(jsonPath("$.content.count").value(13));

        verify(userActionMapper, times(1)).insert(any(UserAction.class));
    }

    @Test
    void favoriteRemovalShouldReturnTheStoredStateAndCanonicalCount() throws Exception {
        AtomicBoolean viewerFavorited = new AtomicBoolean(true);
        AtomicLong favoriteCount = new AtomicLong(4L);
        when(userActionMapper.countByExample(any(UserActionExample.class)))
                .thenAnswer(invocation -> hasUserCriterion(invocation.getArgument(0))
                        ? (viewerFavorited.get() ? 1L : 0L)
                        : favoriteCount.get());
        doAnswer(invocation -> {
            viewerFavorited.set(false);
            favoriteCount.decrementAndGet();
            return 1;
        }).when(userActionMapper).deleteByExample(any(UserActionExample.class));

        mockMvc.perform(post("/web/post/detail/favorite")
                        .contentType("application/json")
                        .content("""
                                {"postId": 42, "active": false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.active").value(false))
                .andExpect(jsonPath("$.content.count").value(3));
    }

    @Test
    void followWriteShouldReturnTheServerConfirmedRelationshipState() throws Exception {
        AtomicBoolean followed = new AtomicBoolean(false);
        UserFollow existing = new UserFollow();
        existing.setId(900L);
        existing.setUserId(VIEWER_ID);
        existing.setFollowId(AUTHOR_ID);
        existing.setStatus((byte) 0);
        when(userFollowMapper.selectByExample(any(UserFollowExample.class))).thenReturn(List.of(existing));
        doAnswer(invocation -> {
            followed.set(true);
            existing.setStatus((byte) 1);
            return 1;
        }).when(userFollowMapper).updateByExampleSelective(any(UserFollow.class), any(UserFollowExample.class));
        when(userFollowMapper.countByExample(any(UserFollowExample.class)))
                .thenAnswer(invocation -> followed.get() ? 1L : 0L);

        mockMvc.perform(post("/web/post/detail/follow")
                        .contentType("application/json")
                        .content("""
                                {"postId": 42, "active": true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.followed").value(true));

        mockMvc.perform(post("/web/post/detail/follow")
                        .contentType("application/json")
                        .content("""
                                {"postId": 42, "active": true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.followed").value(true));

        verify(userFollowMapper, times(1))
                .updateByExampleSelective(any(UserFollow.class), any(UserFollowExample.class));
    }

    private Post postOwnedBy(long authorId) {
        Post post = new Post();
        post.setId(POST_ID);
        post.setUserId(authorId);
        post.setStatus("1");
        return post;
    }

    private void loginAs(long memberId) {
        MemberLoginResp member = new MemberLoginResp();
        member.setId(memberId);
        LoginMemberContext.setMember(member);
    }

    private String actionType(UserActionExample example) {
        return example.getOredCriteria().stream()
                .flatMap(criteria -> criteria.getAllCriteria().stream())
                .filter(criterion -> "action_type =".equals(criterion.getCondition()))
                .map(criterion -> String.valueOf(criterion.getValue()))
                .findFirst()
                .orElseThrow();
    }

    private boolean hasUserCriterion(UserActionExample example) {
        return example.getOredCriteria().stream()
                .flatMap(criteria -> criteria.getAllCriteria().stream())
                .anyMatch(criterion -> "user_id =".equals(criterion.getCondition()));
    }
}
