package com.jiawa.lyw.service;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.jiawa.lyw.context.LoginMemberContext;
import com.jiawa.lyw.domain.*;
import com.jiawa.lyw.enums.PostStatusEnum;
import com.jiawa.lyw.exception.BusinessException;
import com.jiawa.lyw.exception.BusinessExceptionEnum;
import com.jiawa.lyw.mapper.*;
import com.jiawa.lyw.req.*;
import com.jiawa.lyw.resp.*;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service

@Slf4j
public class PostService {
    @Autowired
    private PostMapper postMapper;

    @Autowired
    private PostImageMapper postImageMapper;

    @Autowired
    private tagsMapper tagsMapper;

    @Autowired
    private postTagMapper postTagMapper;

    @Autowired
    private PostMapperCust postMapperCust;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserFollowService userFollowService;

    @Transactional
    public void savePost(PostReq req) throws IOException {
        Date now = new Date();
        long postID = IdUtil.getSnowflakeNextId();

        log.info("开始保存帖子开始:{}", postID);

        Post post = new Post();
        post.setId(postID);
        post.setUserId(LoginMemberContext.getId());
        post.setTitle(req.getTitle());
        post.setContent(req.getContent());
        post.setStatus(PostStatusEnum.OPEN.getCode());
        post.setCreateTime(now);
        post.setUpdateTime(now);
        postMapper.insert(post);

        List<PostReq.PostImage> images = req.getImages();
        if (images == null || images.isEmpty()) {
            throw new BusinessException(BusinessExceptionEnum.IMAGE_NO_ERROR);
        }

        log.info("保存帖子图片开始:{}", postID);
        String uploadDir = "D:/idea/lyw/uploads/"; // 本地存储路径
//        String uploadDir = "/home/lyw/uploads/";

        for (PostReq.PostImage img : images) {
            String imageUrl = img.getImageUrl();
            if (imageUrl == null || imageUrl.isEmpty()) continue;

            // 用 UUID 生成唯一文件名
            String newFileName = java.util.UUID.randomUUID().toString().replace("-", "");

            try {
                if (imageUrl.startsWith("data:image")) {
                    // Base64 图片
                    String[] parts = imageUrl.split(",");
                    String base64Data = parts[1];
                    byte[] data = java.util.Base64.getDecoder().decode(base64Data);

                    // 自动判断后缀
                    String suffix = ".png";
                    if (parts[0].contains("jpeg")) suffix = ".jpg";
                    else if (parts[0].contains("gif")) suffix = ".gif";

                    newFileName += suffix;
                    FileUtil.writeBytes(data, uploadDir + newFileName);
                } else {
                    // URL 下载
                    String suffix = "";
                    if (imageUrl.contains(".")) {
                        suffix = imageUrl.substring(imageUrl.lastIndexOf("."));
                    }
                    newFileName += suffix;
                    FileUtil.writeFromStream(new URL(imageUrl).openStream(), uploadDir + newFileName);
                }

            } catch (IOException e) {
                log.error("图片处理失败: {}", imageUrl, e);
                throw new BusinessException(BusinessExceptionEnum.IMAGE_NOT_ERROR);
            }

            // 保存到数据库
            PostImage postImage = new PostImage();
            postImage.setId(IdUtil.getSnowflakeNextId());
            postImage.setPostId(postID);
            postImage.setImageUrl("/uploads/" + newFileName); // 数据库存储相对路径
            postImage.setSeq(img.getSeq());
            postImage.setDescription(img.getDescription() != null ? img.getDescription() : "");
            postImageMapper.insert(postImage);
        }


        // 保存标签逻辑保持不变
        log.info("保存tag表开始:{}", postID);
        for (PostReq.TagDTO tagDTO : req.getTags()) {
            String tagName = tagDTO.getName();
            tagsExample tagsExample = new tagsExample();
            tagsExample.createCriteria().andNameEqualTo(tagName);

            long count = tagsMapper.countByExample(tagsExample);
            Long tagId;
            if (count == 0) {
                tags newTag = new tags();
                newTag.setId(IdUtil.getSnowflakeNextId());
                newTag.setName(tagName);
                tagsMapper.insert(newTag);
                tagId = newTag.getId();
            } else {
                List<tags> existingTags = tagsMapper.selectByExample(tagsExample);
                tagId = existingTags.get(0).getId();
            }

            postTag postTag = new postTag();
            postTag.setId(IdUtil.getSnowflakeNextId());
            postTag.setPostId(postID);
            postTag.setTagId(tagId);
            postTagMapper.insert(postTag);
        }

        // Redis 今日新增帖子数 +1
        String key = "post:count:" + LocalDate.now();
        stringRedisTemplate.opsForValue().increment(key);
        stringRedisTemplate.expire(key, 7, TimeUnit.DAYS);

        log.info("保存帖子结束:{}", postID);
    }

    public List<Post> findPostIds(Long PostId) {
        PostExample postExample = new PostExample();
        PostExample.Criteria criteria = postExample.createCriteria();
        criteria.andIdEqualTo(PostId);
        return postMapper.selectByExample(postExample);
    }

    public List<PostResp> findAll() {
        log.info("查找全部数据开始");
        return postMapperCust.findAll();
    }

    public List<PostResp> searchPostsByKeyword(PostSearchReq req) {
        log.info("检索内容开始:{}", req);

        List<PostResp> list = postMapperCust.searchPostsByKeyword(req.getKeyword());
        log.info("检索内容结束:{}", list);
        return list;
    }

    public PageResp<PostUserResp> selectPostDetailsByUserId(PageReq pageReq) {
        log.info("用户检索内容开始:{}", LoginMemberContext.getId());

        // 开启分页
        PageHelper.startPage(pageReq.getPage(), pageReq.getSize());

        List<PostUserResp> list = postMapperCust.selectPostDetailsByUserId(LoginMemberContext.getId());

        // 构造分页信息
        PageInfo<PostUserResp> pageInfo = new PageInfo<>(list);

        PageResp<PostUserResp> pageResp = new PageResp<>();
        pageResp.setTotal(pageInfo.getTotal());
        pageResp.setPage(list);

        log.info("用户检索内容结束:{}", LoginMemberContext.getId());
        return pageResp;
    }

    /**
     * 根据userid查询所有该用户信息 */
    public List<PostResp> selectPostDetailsByPostId(UserPostQueryReq req) {
        log.info("查询用户信息开始:{},当前用户:{}", req.getUserid(),LoginMemberContext.getId());
        List<PostResp> list = postMapperCust.UserPostQuery(Long.valueOf(req.getUserid()));
        log.info("查询用户信息结束:{},当前用户:{}", req.getUserid(),LoginMemberContext.getId());
        return list;

    }

    /**
     * 根据userid查询所有该用户信息 */
    public List<PostResp> listFavoritePostsByUserId(UserPostQueryReq req) {
        log.info("查询用户收藏信息开始:{},当前用户:{}", req.getUserid(),LoginMemberContext.getId());
        List<PostResp> list = postMapperCust.listFavoritePostsByUserId(Long.valueOf(req.getUserid()));
        log.info("查询用户收藏信息结束:{},当前用户:{}", req.getUserid(),LoginMemberContext.getId());
        return list;

    }

    public void del(DelPostReq req) {
        Long postId = req.getPostId();
        log.info("开始软删除帖子: {}", postId);

        // 更新帖子状态为 2（已删除）
        int updated = postMapperCust.updateStatus(postId, PostStatusEnum.DELETE.getCode());
        if (updated > 0) {
            log.info("帖子软删除成功: {}", postId);
        } else {
            log.warn("帖子软删除失败，帖子不存在: {}", postId);
        }
    }

    /**
     * 查询帖子总数 */
    public StatisticResp getPostCount(){
        StatisticResp statisticResp = new StatisticResp();

        PostExample postExample = new PostExample();
        PostExample.Criteria criteria = postExample.createCriteria();
        criteria.andStatusEqualTo(PostStatusEnum.OPEN.getCode());

        long totalPostCount = postMapper.countByExample(postExample);
        statisticResp.setPostCount(totalPostCount);

        return statisticResp;
    }

    public StatisticResp selectDailyPostCountLast30Days(){
        StatisticResp statisticResp = new StatisticResp();

        List<StatisticDateResp> statisticResps = postMapperCust.selectDailyPostCountLast30Days();
        statisticResp.setSelectDailyPostCountLast30Days(userFollowService.fill30(statisticResps));

        return statisticResp;
    }
}