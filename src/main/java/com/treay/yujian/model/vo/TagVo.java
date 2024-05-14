package com.treay.yujian.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @author:Treay
 *
 */
@Data
public class TagVo implements Serializable {

    /**
     * 用户标签
     */
    private List<String> oldTags;

    /**
     * 智能推荐标签
     */
    private List<String> RecommendTags;

}
