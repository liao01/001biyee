package com.jiawa.lyw.resp;

import lombok.Data;

import java.util.List;

@Data
public class StatisticResp {

    /**
     * 实时在线
     */
    private Integer onlineCount;


    /**
     * 日活用户
     */
    private Long dau;

    /**
     * 每日发帖量
     */
    private String postDayCount;

    /**
     * 总发帖量
     */
    private long postCount;


    private Integer countFollowers;
    /**
     * 相比昨日关注人数
     */
    private Integer countYesterdayNew;


    /**
     * 相比昨日取消关注人数
     */
    private Integer countYesterdayUn;

    /**
     * 该用户近30天关注人数数
     */
    private List<StatisticDateResp> getUserFollowTrendLast30Days;

    /**
     * 该用户近30天发送帖子数量
     */
    private List<StatisticDateResp> selectDailyPostCountLast30Days;

    /**
     * 该用户近30天关注人数数
     */
    private List<UserFollowPesp> getFollowingListByUserId;

}
