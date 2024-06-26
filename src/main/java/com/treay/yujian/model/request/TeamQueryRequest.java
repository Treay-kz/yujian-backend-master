package com.treay.yujian.model.request;


import com.treay.yujian.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * @description: 队伍查询参数
 * @author: Treay
 * @create: 2023-12-29 14:52
 **/
@Data
@EqualsAndHashCode(callSuper = true)
public class TeamQueryRequest extends PageRequest {

    private Long id;

    /**
     * 我加入的队伍id列表
     */
    private List<Long> idList;

    /**
     * 队伍名称
     */
    private String name;

    /**
     * 搜索关键字
     */
    private String searchText;

    /**
     * 描述
     */
    private String description;

    /**
     * 最大人数
     */
    private Integer maxNum;

    /**
     * 用户id
     */
    private Long userId;

    /**
     * 0 - 公开，1 - 私有，2 - 加密
     */
    private Integer status;

    /**
     * 当前用户
     */
    private String userAccount;

    /**
     * uuid
     */
    private String uuid;

}
