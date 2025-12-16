package com.jiawa.lyw.controller.admin;


import com.jiawa.lyw.resp.CommonResp;
import com.jiawa.lyw.resp.StatisticResp;
import com.jiawa.lyw.service.MemberService;
import com.jiawa.lyw.service.PostService;
import com.jiawa.lyw.service.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/report")
public class AdminReportController {
    @Autowired
    private ReportService reportService;

    @Autowired
    private PostService postService;
    @Autowired
    private MemberService memberService;


    //首页数字统计
    @GetMapping("/query-statistic")
    public CommonResp<StatisticResp> queryStatistic(){
        StatisticResp statisticResp = reportService.queryStatistic();
        return new CommonResp<>(statisticResp);

    }

    //统计日活用户
    @GetMapping("/dau")
    public CommonResp<StatisticResp> getDau() {
        StatisticResp statisticResp = reportService.getDau();
        return new CommonResp<>(statisticResp);
    }

    //统计每日发贴量
    @GetMapping("/postDayCount")
    public CommonResp<StatisticResp> getPostDayCount() {
        StatisticResp statisticResp = reportService.getPostDayCount();
        return new CommonResp<>(statisticResp);
    }

    //统计每日发贴量
    @GetMapping("/postCount")
    public CommonResp<StatisticResp> getPostCount() {
        StatisticResp statisticResp = postService.getPostCount();
        return new CommonResp<>(statisticResp);
    }
    //统计用户总数
    @GetMapping("/UserCount")
    public CommonResp<StatisticResp> getUserCount() {
        StatisticResp statisticResp = memberService.getUserCount();
        return new CommonResp<>(statisticResp);
    }
    //统计今日注册人数
    @GetMapping("/RegisterUserCount")
    public CommonResp<StatisticResp> getRegisterUserCount() {
        StatisticResp statisticResp = memberService.getRegisterUserCount();
        return new CommonResp<>(statisticResp);
    }

    //统计每日发贴量
    @GetMapping("/postCount30Days")
    public CommonResp<StatisticResp> selectDailyPostCountLast30Days() {
        StatisticResp statisticResp = postService.selectDailyPostCountLast30Days();
        return new CommonResp<>(statisticResp);
    }
}
