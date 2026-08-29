package com.jiawa.lyw.controller.web;

import com.jiawa.lyw.controller.ControllerExceptionHandler;
import com.jiawa.lyw.resp.PostCategoryResp;
import com.jiawa.lyw.service.PostCategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PostCategoryControllerTests {

    @Mock
    private PostCategoryService postCategoryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        PostController controller = new PostController();
        ReflectionTestUtils.setField(controller, "postCategoryService", postCategoryService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ControllerExceptionHandler())
                .build();
    }

    @Test
    void categoriesShouldReturnTheEnabledDatabaseConfigurationInOrder() throws Exception {
        when(postCategoryService.listEnabled()).thenReturn(List.of(
                new PostCategoryResp("CITY_WALK", "城市漫游"),
                new PostCategoryResp("NATURAL_SCENERY", "自然风光"),
                new PostCategoryResp("FOOD", "美食")
        ));

        mockMvc.perform(get("/web/post/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.content[0].code").value("CITY_WALK"))
                .andExpect(jsonPath("$.content[1].name").value("自然风光"))
                .andExpect(jsonPath("$.content[2].code").value("FOOD"));
    }
}
