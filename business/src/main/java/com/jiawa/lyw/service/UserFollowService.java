package com.jiawa.lyw.service;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.jiawa.lyw.context.LoginMemberContext;
import com.jiawa.lyw.domain.UserFollow;
import com.jiawa.lyw.domain.UserFollowExample;
import com.jiawa.lyw.enums.UserFollowStatusEnum;
import com.jiawa.lyw.exception.BusinessException;
import com.jiawa.lyw.exception.BusinessExceptionEnum;
import com.jiawa.lyw.mapper.UserFollowMapper;
import com.jiawa.lyw.mapper.UserFollowMapperCust;
import com.jiawa.lyw.req.PageReq;
import com.jiawa.lyw.req.UserFollowReq;
import com.jiawa.lyw.resp.PageResp;
import com.jiawa.lyw.resp.StatisticDateResp;
import com.jiawa.lyw.resp.StatisticResp;
import com.jiawa.lyw.resp.UserFollowPesp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class UserFollowService {
    @Autowired
    private MemberService memberService;

    @Autowired
    private UserFollowMapper userFollowMapper;

    @Autowired
    private UserFollowMapperCust userFollowMapperCust;

    /**
     * 统一的关注/取关操作
     * @param req   请求参数
     * @param follow true 表示关注，false 表示取消关注
     */
    public void handleUserFollow(UserFollowReq req, boolean follow) {
        log.info("开始处理用户关注操作：userId={}, followId={}, follow={}", LoginMemberContext.getId(), req.getFollowId(), follow);

        // 参数与用户合法性检查
        validateFollowRequest(req);

        // 查询现有的关注记录
        UserFollow existing = findFollowRecord(LoginMemberContext.getId(), req.getFollowId());

        if (follow) {
            // ---- 执行关注操作 ----
            if (existing != null && existing.getStatus() == UserFollowStatusEnum.FOLLOW.getCode()) {
                throw new BusinessException(BusinessExceptionEnum.ALREADY_FOLLOWED);
            }

            if (existing == null) {
                // 没有记录 -> 新增
                UserFollow newFollow = new UserFollow();
                newFollow.setId(IdUtil.getSnowflakeNextId());
                newFollow.setUserId(LoginMemberContext.getId());
                newFollow.setFollowId(req.getFollowId());
                newFollow.setCreateTime(new Date());
                newFollow.setStatus(UserFollowStatusEnum.FOLLOW.getCode());
                userFollowMapper.insert(newFollow);
                log.info("关注成功 userId={} followId={}", LoginMemberContext.getId(), req.getFollowId());
            } else {
                // 已存在记录 -> 修改状态为关注
                existing.setStatus(UserFollowStatusEnum.FOLLOW.getCode());
                userFollowMapper.updateByPrimaryKeySelective(existing);
                log.info("重新关注成功 userId={} followId={}", LoginMemberContext.getId(), req.getFollowId());
            }

        } else {
            // ---- 执行取消关注操作 ----
            if (existing == null) {
                throw new BusinessException(BusinessExceptionEnum.DATA_NOT_FOUND);
            }

            existing.setStatus(UserFollowStatusEnum.UNFOLLOW.getCode());
            userFollowMapper.updateByPrimaryKeySelective(existing);
            log.info("取消关注成功 userId={} followId={}", LoginMemberContext.getId(), req.getFollowId());
        }

        log.info("用户关注操作结束 userId={} followId={}", LoginMemberContext.getId(), req.getFollowId());
    }

    /**
     * 判断是否已关注
     */
    public boolean isFollowed(UserFollowReq req) {
        UserFollowExample example = new UserFollowExample();
        example.createCriteria()
                .andUserIdEqualTo(LoginMemberContext.getId())
                .andFollowIdEqualTo(req.getFollowId())
                .andStatusEqualTo(UserFollowStatusEnum.FOLLOW.getCode());
        return userFollowMapper.countByExample(example) > 0;
    }


    /**
     * 校验请求参数与用户存在性
     */
    private void validateFollowRequest(UserFollowReq req) {
        if (LoginMemberContext.getId() == null || req.getFollowId() == null) {
            throw new BusinessException(BusinessExceptionEnum.PARAM_ERROR);
        }

        if (LoginMemberContext.getId().equals(req.getFollowId())) {
            throw new BusinessException(BusinessExceptionEnum.USER_CANNOT_FOLLOW_SELF);
        }

        // 校验用户是否存在
        if (memberService.findByMemberId(LoginMemberContext.getId()).isEmpty()) {
            throw new BusinessException(BusinessExceptionEnum.MEMBER_MOBILE_NOT_REGISTER);
        }
        if (memberService.findByMemberId(req.getFollowId()).isEmpty()) {
            throw new BusinessException(BusinessExceptionEnum.MEMBER_MOBILE_NOT_REGISTER);
        }
    }
    /**
     * 近30天粉丝数量,及粉丝详情等
     */
    public StatisticResp getUserFollowTrendLast30Days(){
        StatisticResp statisticResp = new StatisticResp();
        
        //粉丝关注列表
        List<UserFollowPesp> list = userFollowMapperCust.getFollowingListByUserId(LoginMemberContext.getId());
        int count = list.size();

        //粉丝总数
        statisticResp.setCountFollowers( count);

        //昨日涨粉数量
        statisticResp.setCountYesterdayNew(userFollowMapperCust.countYesterdayNewFollowers(LoginMemberContext.getId()));

        //昨日取消关注数量
        statisticResp.setCountYesterdayUn(userFollowMapperCust.countYesterdayUnfollowers(LoginMemberContext.getId()));

        //趋势图
        List<StatisticDateResp> userFollowTrendLast30Days = userFollowMapperCust.getUserFollowTrendLast30Days(LoginMemberContext.getId());
        statisticResp.setGetUserFollowTrendLast30Days(fill30(userFollowTrendLast30Days));
        return statisticResp;
    }

    public PageResp<UserFollowPesp> getFollowingListByUserIdList(PageReq pageReq) {
        // 开启分页
        PageHelper.startPage(pageReq.getPage(), pageReq.getSize());

        // 查询关注列表
        List<UserFollowPesp> list = userFollowMapperCust.getFollowingListByUserId(LoginMemberContext.getId());

        // 构造分页信息
        PageInfo<UserFollowPesp> pageInfo = new PageInfo<>(list);

        PageResp<UserFollowPesp> pageResp = new PageResp<>();
        pageResp.setTotal(pageInfo.getTotal());
        pageResp.setPage(list);

        return pageResp;
    }


    /**
     * 查找关注记录（若不存在返回 null）
     */
    private UserFollow findFollowRecord(Long userId, Long followId) {
        UserFollowExample example = new UserFollowExample();
        example.createCriteria()
                .andUserIdEqualTo(userId)
                .andFollowIdEqualTo(followId);
        List<UserFollow> list = userFollowMapper.selectByExample(example);
        return list.isEmpty() ? null : list.get(0);
    }


    /**
     * 补齐30天数据，数据库里可能只有20天有数据，查出来的列表就是只有20个数据，需要补齐成30个数据
     * @param list
     */
    public List<StatisticDateResp> fill30(List<StatisticDateResp> list) {
        List<StatisticDateResp> list30 = new ArrayList<>();
        Date now = new Date();
        String dateFormat = "MM-dd";
        // 将两个列表30天的数据合成一个列表
        for (int i = 29; i >= 0; i--) {
            String date = DateUtil.format(DateUtil.offsetDay(now, -i), dateFormat);
            Optional<StatisticDateResp> registerCountOptional = list.stream().filter(o -> date.equals(o.getDate())).findFirst();
            if (registerCountOptional.isPresent()) {
                list30.add(registerCountOptional.get());
            } else {
                // 如果某天没有数据，则补0
                // log.info("日期【{}】没数据，补0", date);
                StatisticDateResp statisticDateResp = new StatisticDateResp(date, 0);
                list30.add(statisticDateResp);
            }
        }
        return list30;
    }
}