package com.jiawa.lyw.service;

import com.jiawa.lyw.domain.PostCategory;
import com.jiawa.lyw.exception.BusinessException;
import com.jiawa.lyw.exception.BusinessExceptionEnum;
import com.jiawa.lyw.mapper.PostCategoryMapper;
import com.jiawa.lyw.resp.PostCategoryResp;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PostCategoryService {
    @Resource
    private PostCategoryMapper postCategoryMapper;

    public List<PostCategoryResp> listEnabled() {
        return postCategoryMapper.selectEnabled().stream()
                .map(category -> new PostCategoryResp(category.getCode(), category.getName()))
                .toList();
    }

    public PostCategory requireEnabled(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException(BusinessExceptionEnum.POST_CATEGORY_INVALID);
        }
        PostCategory category = postCategoryMapper.selectByCode(code);
        if (category == null || !Boolean.TRUE.equals(category.getEnabled())) {
            throw new BusinessException(BusinessExceptionEnum.POST_CATEGORY_INVALID);
        }
        return category;
    }
}
