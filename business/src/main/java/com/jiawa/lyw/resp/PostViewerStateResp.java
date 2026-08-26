package com.jiawa.lyw.resp;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PostViewerStateResp {
    private long viewerId;
    private boolean liked;
    private boolean favorited;
    private boolean followed;
    private boolean selfAuthor;
}
