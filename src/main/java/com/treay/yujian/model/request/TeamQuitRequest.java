package com.treay.yujian.model.request;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户退出队伍请求体
 *
 * @author Treay
 * 
 */
@Data
public class TeamQuitRequest  extends CurrentUserRequest  implements Serializable {


    private static final long serialVersionUID = 3191241716373120793L;

    /**
     * id
     */
    private Long teamId;

}
