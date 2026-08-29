package com.jiawa.lyw.controller.web;

import com.jiawa.lyw.controller.ControllerExceptionHandler;
import com.jiawa.lyw.domain.PostImage;
import com.jiawa.lyw.domain.UserActionExample;
import com.jiawa.lyw.mapper.CommentMapperCust;
import com.jiawa.lyw.mapper.PostImageMapper;
import com.jiawa.lyw.mapper.PostMapperCust;
import com.jiawa.lyw.mapper.UserActionMapper;
import com.jiawa.lyw.resp.CommentResp;
import com.jiawa.lyw.resp.PostResp;
import com.jiawa.lyw.service.PostService;
import com.jiawa.lyw.service.PostDetailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PostDetailControllerTests {

    private MockMvc mockMvc;

    @Mock
    private PostMapperCust postMapperCust;

    @Mock
    private PostImageMapper postImageMapper;

    @Mock
    private CommentMapperCust commentMapperCust;

    @Mock
    private UserActionMapper userActionMapper;

    @BeforeEach
    void setUp() {
        PostDetailService postDetailService = new PostDetailService();
        ReflectionTestUtils.setField(postDetailService, "postMapperCust", postMapperCust);
        ReflectionTestUtils.setField(postDetailService, "postImageMapper", postImageMapper);
        ReflectionTestUtils.setField(postDetailService, "commentMapperCust", commentMapperCust);
        ReflectionTestUtils.setField(postDetailService, "userActionMapper", userActionMapper);

        PostController postController = new PostController();
        ReflectionTestUtils.setField(postController, "postDetailService", postDetailService);

        mockMvc = MockMvcBuilders.standaloneSetup(postController)
                .setControllerAdvice(new ControllerExceptionHandler())
                .build();
    }

    @Test
    void publicDetailShouldReturnCanonicalPostWithoutLoginContext() throws Exception {
        PostResp post = new PostResp();
        post.setPostId("42");
        post.setPostTitle("桂林山水之旅");
        post.setPostContent("沿着漓江慢慢看山水。");
        post.setPostTime(LocalDateTime.of(2026, 8, 20, 10, 0));
        post.setUserId("7");
        post.setMembername("旅行者小林");
        post.setAvatar("/uploads/avatar/xiaolin.png");
        post.setCategoryCode("NATURAL_SCENERY");
        post.setCategoryName("自然风光");
        when(postMapperCust.findPublicDetailById(42L)).thenReturn(post);

        PostImage firstImage = new PostImage();
        firstImage.setImageUrl("/uploads/guilin-1.jpg");
        firstImage.setSeq(1);
        PostImage secondImage = new PostImage();
        secondImage.setImageUrl("/uploads/guilin-2.jpg");
        secondImage.setSeq(2);
        when(postImageMapper.selectByExample(any())).thenReturn(List.of(firstImage, secondImage));

        CommentResp comment = new CommentResp();
        comment.setId("501");
        comment.setUserId("8");
        comment.setMembername("山水读者");
        comment.setAvatar("/uploads/avatar/reader.png");
        comment.setCommentContent("景色真漂亮");
        comment.setCommentTime(new Date(1_777_000_000_000L));
        when(commentMapperCust.findCommentByPostId(42L)).thenReturn(List.of(comment));
        when(userActionMapper.countByExample(any(UserActionExample.class)))
                .thenAnswer(invocation -> countFor(invocation.getArgument(0)));

        mockMvc.perform(get("/web/post/detail").param("postId", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.content.post.id").value("42"))
                .andExpect(jsonPath("$.content.post.title").value("桂林山水之旅"))
                .andExpect(jsonPath("$.content.post.description").value("沿着漓江慢慢看山水。"))
                .andExpect(jsonPath("$.content.post.categoryCode").value("NATURAL_SCENERY"))
                .andExpect(jsonPath("$.content.post.categoryName").value("自然风光"))
                .andExpect(jsonPath("$.content.author.id").value("7"))
                .andExpect(jsonPath("$.content.author.name").value("旅行者小林"))
                .andExpect(jsonPath("$.content.author.avatar").value("/uploads/avatar/xiaolin.png"))
                .andExpect(jsonPath("$.content.images[0]").value("/uploads/guilin-1.jpg"))
                .andExpect(jsonPath("$.content.images[1]").value("/uploads/guilin-2.jpg"))
                .andExpect(jsonPath("$.content.comments[0].id").value("501"))
                .andExpect(jsonPath("$.content.comments[0].commentContent").value("景色真漂亮"))
                .andExpect(jsonPath("$.content.interactionCounts.like").value(12))
                .andExpect(jsonPath("$.content.interactionCounts.favorite").value(4));
    }

    private long countFor(UserActionExample example) {
        return example.getOredCriteria().stream()
                .flatMap(criteria -> criteria.getAllCriteria().stream())
                .filter(criterion -> "action_type =".equals(criterion.getCondition()))
                .map(criterion -> "like".equals(criterion.getValue()) ? 12L : 4L)
                .findFirst()
                .orElseThrow();
    }
}
