package com.treay.yujian.model.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String uuid;

    /**
     * 当前账号
     */
    private String currentUserAccount;

    /**
     * 用户id
     */
    private long id;

    /**
     * 用户昵称
     */
    private String username;

    /**
     * 用户头像
     */
    private String avatarUrl;

    /**
     * 性别
     */
    private Integer gender;

    /**
     * 电话
     */
    private String phone;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 标签
     */
    private String tags;

    /**
     * 自我介绍
     */
    private String profile;
}
