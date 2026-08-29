package com.jiawa.lyw.service;

import com.jiawa.lyw.mapper.PostMapperCust;
import com.jiawa.lyw.resp.PostResp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostDiscoveryQueryTests {

    @Mock private PostMapperCust postMapperCust;
    @Mock private PostCategoryService postCategoryService;

    private PostService service;

    @BeforeEach
    void setUp() {
        service = new PostService();
        ReflectionTestUtils.setField(service, "postMapperCust", postMapperCust);
        ReflectionTestUtils.setField(service, "postCategoryService", postCategoryService);
    }

    @Test
    void categoryViewShouldValidateTheCategoryAndPassCanonicalQueryParameters() {
        PostResp post = new PostResp();
        post.setPostId("42");
        when(postMapperCust.findAll("RECOMMENDED", "FOOD")).thenReturn(List.of(post));

        List<PostResp> result = service.findAll(null, "FOOD");

        verify(postCategoryService).requireEnabled("FOOD");
        verify(postMapperCust).findAll("RECOMMENDED", "FOOD");
        assertEquals("42", result.get(0).getPostId());
    }

    @Test
    void latestViewShouldPassLatestWithoutAClassificationFilter() {
        when(postMapperCust.findAll("LATEST", null)).thenReturn(List.of());

        service.findAll("LATEST", null);

        verify(postMapperCust).findAll("LATEST", null);
    }
}
