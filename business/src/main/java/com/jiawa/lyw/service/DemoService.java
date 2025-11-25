package com.jiawa.lyw.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;

import com.jiawa.lyw.domain.Demo;
import com.jiawa.lyw.domain.DemoExample;
import com.jiawa.lyw.exception.BusinessException;
import com.jiawa.lyw.exception.BusinessExceptionEnum;
import com.jiawa.lyw.mapper.DemoMapper;
import com.jiawa.lyw.mapper.DemoMapperCust;
import com.jiawa.lyw.req.DemoQueryReq;
import com.jiawa.lyw.resp.DemoQueryResp;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DemoService {
    @Autowired
    private DemoMapperCust demoMapperCust;

    @Autowired
    private DemoMapper demoMapper;

    public int count(){
//        return demoMapperCust.count();
    return Math.toIntExact(demoMapper.countByExample(null));
    }


    public List<DemoQueryResp> query(DemoQueryReq req) {
        String mobile = req.getMobile();
        DemoExample demoExample = new DemoExample();
        demoExample.setOrderByClause("id asc");
        DemoExample.Criteria criteria = demoExample.createCriteria();
        // if (mobile != null) {
        //     criteria.andMobileEqualTo(mobile);
        // }
        if (StrUtil.isBlank(mobile)) {
            throw new BusinessException(BusinessExceptionEnum.DEMO_MOBILE_NOT_NULL);
        }
        criteria.andMobileEqualTo(mobile);
        List<Demo> list = demoMapper.selectByExample(demoExample);
        return BeanUtil.copyToList(list, DemoQueryResp.class);
    }

}
