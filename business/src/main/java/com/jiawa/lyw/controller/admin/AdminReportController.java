package com.jiawa.lyw.controller.admin;


import com.jiawa.lyw.resp.CommonResp;
import com.jiawa.lyw.resp.StatisticResp;
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

    //首页数字统计
    @GetMapping("/query-statistic")
    public CommonResp<StatisticResp> queryStatistic(){
        StatisticResp statisticResp = reportService.queryStatistic();
        return new CommonResp<>(statisticResp);

    }
}
