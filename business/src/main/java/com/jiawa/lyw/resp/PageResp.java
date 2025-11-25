package com.jiawa.lyw.resp;

import lombok.Data;

import java.util.List;

@Data
public class PageResp<T> {
    private long total;

    private List<T> page;
}
