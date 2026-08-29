package com.jiawa.lyw.service;

import com.jiawa.lyw.context.LoginMemberContext;
import com.jiawa.lyw.mapper.PostMapperCust;
import com.jiawa.lyw.req.PageReq;
import com.jiawa.lyw.resp.MemberLoginResp;
import com.jiawa.lyw.resp.PageResp;
import com.jiawa.lyw.resp.PostUserResp;
import org.apache.ibatis.annotations.Param;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostHistoryQueryTests {

    @Mock
    private PostMapperCust postMapperCust;

    private PostService service;

    @BeforeEach
    void setUp() {
        service = new PostService();
        ReflectionTestUtils.setField(service, "postMapperCust", postMapperCust);
        MemberLoginResp member = new MemberLoginResp();
        member.setId(100L);
        LoginMemberContext.setMember(member);
    }

    @AfterEach
    void tearDown() {
        LoginMemberContext.removeMember();
    }

    @Test
    void historyMapperShouldExposeTheUserIdNameConsumedByMyBatisXml() throws Exception {
        Method method = PostMapperCust.class.getMethod("selectPostDetailsByUserId", Long.class);
        Param param = method.getParameters()[0].getAnnotation(Param.class);

        assertEquals("userId", param.value());
    }

    @Test
    void historyShouldQueryTheLoggedInUserAndReturnCategoryFields() {
        PostUserResp post = new PostUserResp();
        post.setPostId("42");
        post.setCategoryCode("FOOD");
        post.setCategoryName("美食");
        when(postMapperCust.selectPostDetailsByUserId(100L)).thenReturn(List.of(post));

        PageReq req = new PageReq();
        req.setPage(1);
        req.setSize(5);
        PageResp<PostUserResp> result = service.selectPostDetailsByUserId(req);

        verify(postMapperCust).selectPostDetailsByUserId(100L);
        assertEquals("FOOD", result.getPage().get(0).getCategoryCode());
        assertEquals("美食", result.getPage().get(0).getCategoryName());
    }
}
