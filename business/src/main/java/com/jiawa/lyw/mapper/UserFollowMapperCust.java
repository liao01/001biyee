package com.jiawa.lyw.mapper;

import com.jiawa.lyw.resp.StatisticDateResp;
import com.jiawa.lyw.resp.UserFollowPesp;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserFollowMapperCust {
    //粉丝趋势图
    List<StatisticDateResp>  getUserFollowTrendLast30Days(@Param("userId") Long userId);
    //涨粉数量相比昨日
    int countYesterdayNewFollowers(@Param("userId") Long userId);
    //掉粉数量相比昨日
    int countYesterdayUnfollowers(@Param("userId") Long userId);
    //查询关注列表
    List<UserFollowPesp> getFollowingListByUserId(@Param("userId") Long userId);
}
