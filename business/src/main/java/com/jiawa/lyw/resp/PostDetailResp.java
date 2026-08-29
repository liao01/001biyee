package com.jiawa.lyw.resp;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PostDetailResp {
    private PostContent post;
    private Author author;
    private List<String> images;
    private List<CommentResp> comments;
    private InteractionCounts interactionCounts;

    @Data
    public static class PostContent {
        private String id;
        private String title;
        private String description;
        private LocalDateTime postTime;
        private String categoryCode;
        private String categoryName;
    }

    @Data
    public static class Author {
        private String id;
        private String name;
        private String avatar;
    }

    @Data
    public static class InteractionCounts {
        private long like;
        private long favorite;
    }
}
