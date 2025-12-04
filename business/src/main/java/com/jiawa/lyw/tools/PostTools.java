package com.jiawa.lyw.tools;

import com.jiawa.lyw.resp.CommonResp;
import com.jiawa.lyw.resp.PostResp;
import com.jiawa.lyw.service.PostService;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("PostTools")
public class PostTools {

    @Autowired
    private PostService postService;

    @Tool(
            name = "查询帖子列表",
            value = "调用该工具可获取全部帖子数据。工具会执行 findAll 方法查询并返回所有帖子列表，供模型在回答用户时直接使用。"
    )
    public CommonResp<List<PostResp>> findAll(){
        List<PostResp> list = postService.findAll();
        return new CommonResp<>(list);
    }
}
