package com.jiawa.lyw.service;

import com.jiawa.lyw.config.StorageProperties;
import com.jiawa.lyw.context.LoginMemberContext;
import com.jiawa.lyw.domain.Post;
import com.jiawa.lyw.domain.PostCategory;
import com.jiawa.lyw.mapper.PostImageMapper;
import com.jiawa.lyw.mapper.PostMapper;
import com.jiawa.lyw.mapper.PostMapperCust;
import com.jiawa.lyw.mapper.postTagMapper;
import com.jiawa.lyw.mapper.tagsMapper;
import com.jiawa.lyw.req.PostReq;
import com.jiawa.lyw.resp.MemberLoginResp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceCategoryTests {

    @TempDir Path tempDir;

    @Mock private PostMapper postMapper;
    @Mock private PostImageMapper postImageMapper;
    @Mock private tagsMapper tagsMapper;
    @Mock private postTagMapper postTagMapper;
    @Mock private PostMapperCust postMapperCust;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private PostCategoryService postCategoryService;

    private PostService service;

    @BeforeEach
    void setUp() {
        service = new PostService();
        ReflectionTestUtils.setField(service, "postMapper", postMapper);
        ReflectionTestUtils.setField(service, "postImageMapper", postImageMapper);
        ReflectionTestUtils.setField(service, "tagsMapper", tagsMapper);
        ReflectionTestUtils.setField(service, "postTagMapper", postTagMapper);
        ReflectionTestUtils.setField(service, "postMapperCust", postMapperCust);
        ReflectionTestUtils.setField(service, "stringRedisTemplate", stringRedisTemplate);
        ReflectionTestUtils.setField(service, "postCategoryService", postCategoryService);
        StorageProperties storageProperties = new StorageProperties();
        storageProperties.setUploadDir(tempDir.resolve("uploads"));
        ReflectionTestUtils.setField(service, "storageProperties", storageProperties);
        MemberLoginResp member = new MemberLoginResp();
        member.setId(100L);
        LoginMemberContext.setMember(member);
    }

    @AfterEach
    void tearDown() {
        LoginMemberContext.removeMember();
    }

    @Test
    void savePostShouldUseTheLoggedInActorOpenStatusAndCanonicalCategory() throws Exception {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        PostCategory category = new PostCategory();
        category.setCode("CITY_WALK");
        category.setEnabled(true);
        when(postCategoryService.requireEnabled("CITY_WALK")).thenReturn(category);

        PostReq req = validRequest();
        req.setUserId(999L);
        req.setStatus(2);
        req.setCategoryCode("CITY_WALK");

        service.savePost(req);

        ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
        verify(postMapper).insert(postCaptor.capture());
        assertEquals(100L, postCaptor.getValue().getUserId());
        assertEquals("1", postCaptor.getValue().getStatus());
        assertEquals("CITY_WALK", postCaptor.getValue().getCategoryCode());
    }

    @Test
    void savePostShouldRejectInvalidCategoryBeforeWritingAPost() {
        PostReq req = validRequest();
        req.setCategoryCode("MISSING");
        when(postCategoryService.requireEnabled(anyString())).thenThrow(IllegalArgumentException.class);

        assertThrows(IllegalArgumentException.class, () -> service.savePost(req));
        verify(postMapper, never()).insert(org.mockito.ArgumentMatchers.any(Post.class));
    }

    private PostReq validRequest() {
        PostReq req = new PostReq();
        req.setTitle("城市散步");
        req.setContent("从老街走到江边。");
        PostReq.PostImage image = new PostReq.PostImage();
        image.setImageUrl("");
        image.setSeq(1);
        req.setImages(List.of(image));
        req.setTags(null);
        return req;
    }
}
