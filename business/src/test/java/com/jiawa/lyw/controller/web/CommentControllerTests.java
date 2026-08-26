package com.jiawa.lyw.controller.web;

import com.jiawa.lyw.context.LoginMemberContext;
import com.jiawa.lyw.controller.ControllerExceptionHandler;
import com.jiawa.lyw.domain.Comment;
import com.jiawa.lyw.domain.CommentExample;
import com.jiawa.lyw.mapper.CommentMapper;
import com.jiawa.lyw.mapper.CommentMapperCust;
import com.jiawa.lyw.resp.CommentResp;
import com.jiawa.lyw.resp.MemberLoginResp;
import com.jiawa.lyw.service.CommentService;
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

import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CommentControllerTests {

    private static final long COMMENT_ID = 900L;
    private static final long COMMENT_AUTHOR_ID = 200L;

    private MockMvc mockMvc;

    @Mock
    private CommentMapper commentMapper;

    @Mock
    private CommentMapperCust commentMapperCust;

    @BeforeEach
    void setUp() {
        CommentService commentService = new CommentService();
        ReflectionTestUtils.setField(commentService, "commentMapper", commentMapper);
        ReflectionTestUtils.setField(commentService, "commentMapperCust", commentMapperCust);

        CommentController commentController = new CommentController();
        ReflectionTestUtils.setField(commentController, "commentService", commentService);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(commentController)
                .setControllerAdvice(new ControllerExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void tearDown() {
        LoginMemberContext.removeMember();
    }

    @Test
    void updateShouldRejectAUserWhoDoesNotOwnTheComment() throws Exception {
        loginAs(100L, "other-user");
        when(commentMapper.updateByExampleSelective(any(Comment.class), any(CommentExample.class)))
                .thenAnswer(invocation -> matchesStoredComment(invocation.getArgument(1)) ? 1 : 0);

        mockMvc.perform(post("/web/comment/update-comment")
                        .contentType("application/json")
                        .content("""
                                {
                                  "id": 900,
                                  "userId": 200,
                                  "content": "forged update"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("评论不存在或无权操作"));
    }

    @Test
    void deleteShouldRejectAUserWhoDoesNotOwnTheComment() throws Exception {
        loginAs(100L, "post-author");
        when(commentMapper.deleteByExample(any(CommentExample.class)))
                .thenAnswer(invocation -> matchesStoredComment(invocation.getArgument(0)) ? 1 : 0);

        mockMvc.perform(post("/web/comment/del-comment")
                        .contentType("application/json")
                        .content("""
                                {
                                  "id": 900,
                                  "userId": 200
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("评论不存在或无权操作"));
    }

    @Test
    void saveShouldIgnoreClientUserIdAndReturnTheCanonicalComment() throws Exception {
        loginAs(COMMENT_AUTHOR_ID, "comment-author");
        AtomicReference<Comment> storedComment = new AtomicReference<>();
        when(commentMapper.countByExample(any())).thenReturn(0L);
        doAnswer(invocation -> {
            storedComment.set(invocation.getArgument(0));
            return 1;
        }).when(commentMapper).insert(any(Comment.class));
        when(commentMapperCust.findCommentById(any()))
                .thenAnswer(invocation -> canonicalComment(storedComment.get()));

        mockMvc.perform(post("/web/comment/save-comment")
                        .contentType("application/json")
                        .content("""
                                {
                                  "postId": 300,
                                  "userId": 999,
                                  "content": "canonical content"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.content.id").isNotEmpty())
                .andExpect(jsonPath("$.content.userId").value("200"))
                .andExpect(jsonPath("$.content.membername").value("comment-author"))
                .andExpect(jsonPath("$.content.avatar").value("/avatar/author.png"))
                .andExpect(jsonPath("$.content.commentContent").value("canonical content"))
                .andExpect(jsonPath("$.content.commentTime").isNotEmpty());
    }

    @Test
    void updateShouldReturnTheCanonicalCommentForItsAuthor() throws Exception {
        loginAs(COMMENT_AUTHOR_ID, "comment-author");
        Comment storedComment = storedComment("old content");
        when(commentMapper.updateByExampleSelective(any(Comment.class), any(CommentExample.class)))
                .thenAnswer(invocation -> {
                    if (!matchesStoredComment(invocation.getArgument(1))) {
                        return 0;
                    }
                    Comment changes = invocation.getArgument(0);
                    storedComment.setContent(changes.getContent());
                    storedComment.setCreateTime(changes.getCreateTime());
                    return 1;
                });
        when(commentMapperCust.findCommentById(COMMENT_ID))
                .thenAnswer(invocation -> canonicalComment(storedComment));

        mockMvc.perform(post("/web/comment/update-comment")
                        .contentType("application/json")
                        .content("""
                                {
                                  "id": 900,
                                  "userId": 999,
                                  "content": "updated content"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.content.id").value("900"))
                .andExpect(jsonPath("$.content.userId").value("200"))
                .andExpect(jsonPath("$.content.membername").value("comment-author"))
                .andExpect(jsonPath("$.content.avatar").value("/avatar/author.png"))
                .andExpect(jsonPath("$.content.commentContent").value("updated content"))
                .andExpect(jsonPath("$.content.commentTime").isNotEmpty());
    }

    @Test
    void deleteShouldReturnTheDeletedCommentIdForItsAuthor() throws Exception {
        loginAs(COMMENT_AUTHOR_ID, "comment-author");
        when(commentMapper.deleteByExample(any(CommentExample.class)))
                .thenAnswer(invocation -> matchesStoredComment(invocation.getArgument(0)) ? 1 : 0);

        mockMvc.perform(post("/web/comment/del-comment")
                        .contentType("application/json")
                        .content("""
                                {
                                  "id": 900,
                                  "userId": 999
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.content").value("900"));
    }

    private boolean matchesStoredComment(CommentExample example) {
        return example.getOredCriteria().stream().anyMatch(criteria -> {
            boolean idMatches = criteria.getAllCriteria().stream()
                    .filter(criterion -> "id =".equals(criterion.getCondition()))
                    .anyMatch(criterion -> Long.valueOf(COMMENT_ID).equals(criterion.getValue()));
            boolean hasUserCriterion = criteria.getAllCriteria().stream()
                    .anyMatch(criterion -> "user_id =".equals(criterion.getCondition()));
            boolean userMatches = criteria.getAllCriteria().stream()
                    .filter(criterion -> "user_id =".equals(criterion.getCondition()))
                    .anyMatch(criterion -> Long.valueOf(COMMENT_AUTHOR_ID).equals(criterion.getValue()));
            return idMatches && (!hasUserCriterion || userMatches);
        });
    }

    private CommentResp canonicalComment(Comment comment) {
        CommentResp response = new CommentResp();
        response.setId(String.valueOf(comment.getId()));
        response.setUserId(String.valueOf(comment.getUserId()));
        response.setMembername("comment-author");
        response.setAvatar("/avatar/author.png");
        response.setCommentContent(comment.getContent());
        response.setCommentTime(new Date(1_700_000_000_000L));
        return response;
    }

    private Comment storedComment(String content) {
        Comment comment = new Comment();
        comment.setId(COMMENT_ID);
        comment.setPostId(300L);
        comment.setUserId(COMMENT_AUTHOR_ID);
        comment.setContent(content);
        comment.setCreateTime(new Date(1_700_000_000_000L));
        return comment;
    }

    private void loginAs(Long id, String name) {
        MemberLoginResp member = new MemberLoginResp();
        member.setId(id);
        member.setName(name);
        LoginMemberContext.setMember(member);
    }
}
