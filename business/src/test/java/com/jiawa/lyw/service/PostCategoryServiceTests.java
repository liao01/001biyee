package com.jiawa.lyw.service;

import com.jiawa.lyw.domain.PostCategory;
import com.jiawa.lyw.exception.BusinessException;
import com.jiawa.lyw.mapper.PostCategoryMapper;
import com.jiawa.lyw.resp.PostCategoryResp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostCategoryServiceTests {

    @Mock
    private PostCategoryMapper postCategoryMapper;

    private PostCategoryService service;

    @BeforeEach
    void setUp() {
        service = new PostCategoryService();
        ReflectionTestUtils.setField(service, "postCategoryMapper", postCategoryMapper);
    }

    @Test
    void listEnabledShouldPreserveDatabaseOrder() {
        when(postCategoryMapper.selectEnabled()).thenReturn(List.of(
                category("CITY_WALK", "城市漫游", 10, true),
                category("FOOD", "美食", 30, true)
        ));

        List<PostCategoryResp> categories = service.listEnabled();

        assertEquals(List.of("CITY_WALK", "FOOD"),
                categories.stream().map(PostCategoryResp::getCode).toList());
        assertEquals(List.of("城市漫游", "美食"),
                categories.stream().map(PostCategoryResp::getName).toList());
    }

    @Test
    void requireEnabledShouldRejectMissingOrDisabledCategories() {
        PostCategory disabled = category("DISABLED", "已停用", 90, false);
        when(postCategoryMapper.selectByCode("MISSING")).thenReturn(null);
        when(postCategoryMapper.selectByCode("DISABLED")).thenReturn(disabled);

        assertThrows(BusinessException.class, () -> service.requireEnabled("MISSING"));
        assertThrows(BusinessException.class, () -> service.requireEnabled("DISABLED"));
    }

    @Test
    void requireEnabledShouldReturnTheCanonicalDatabaseCategory() {
        PostCategory food = category("FOOD", "美食", 30, true);
        when(postCategoryMapper.selectByCode("FOOD")).thenReturn(food);

        assertSame(food, service.requireEnabled("FOOD"));
    }

    private PostCategory category(String code, String name, int sortOrder, boolean enabled) {
        PostCategory category = new PostCategory();
        category.setCode(code);
        category.setName(name);
        category.setSortOrder(sortOrder);
        category.setEnabled(enabled);
        return category;
    }
}
