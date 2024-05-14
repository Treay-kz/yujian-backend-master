package com.treay.yujian.model.request;


import lombok.Data;

import java.util.List;

@Data
public class SearchUserByTagsRequest {
    private long pageSize;
    private long pageNum;
    private List<String> tagNameList;
}
