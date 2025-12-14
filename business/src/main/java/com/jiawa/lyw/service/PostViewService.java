package com.jiawa.lyw.service;

import cn.hutool.core.util.IdUtil;
import com.jiawa.lyw.context.LoginMemberContext;
import com.jiawa.lyw.domain.PostView;
import com.jiawa.lyw.domain.PostViewExample;
import com.jiawa.lyw.mapper.PostViewMapper;
import com.jiawa.lyw.mapper.PostViewMapperCust;
import com.jiawa.lyw.req.PostViewReq;
import com.jiawa.lyw.resp.PostViewResp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
@Slf4j
public class PostViewService {
    @Autowired
    private PostViewMapper postViewMapper;
    @Autowired
    private PostViewMapperCust postViewMapperCust;

    public void save(PostViewReq postViewReq) {
        PostViewExample postViewExample = new PostViewExample();
        PostViewExample.Criteria criteria = postViewExample.createCriteria();
        criteria.andPostIdEqualTo(postViewReq.getPostId());
        criteria.andUserIdEqualTo(LoginMemberContext.getId());

        List<PostView> postViews = postViewMapper.selectByExample(postViewExample);

        if (postViews.isEmpty()) {
            log.info("不存在记录:{},{}", postViewReq.getPostId(), LoginMemberContext.getId());

            PostView postView = new PostView();
            postView.setId(IdUtil.getSnowflakeNextId());
            postView.setPostId(postViewReq.getPostId());
            postView.setUserId(LoginMemberContext.getId());
            postView.setViewTime(new Date());

            postViewMapper.insert(postView);
        } else {
            log.info("已存在记录:{},{}", postViewReq.getPostId(), LoginMemberContext.getId());

            PostView postView = postViews.get(0);
            postView.setViewTime(new Date());

            postViewMapper.updateByPrimaryKeySelective(postView);
        }
    }

    public List<PostViewResp> findAll(){
        List<PostViewResp> postViewResps = postViewMapperCust.selectRecentViewedPosts(LoginMemberContext.getId());
        return postViewResps;
    }
}
