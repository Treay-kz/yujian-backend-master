package com.treay.yujian.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * @author:Treay
 *
 */
@Data
public class UserSendMessage implements Serializable {

    private static final long serialVersionUID = 46412442243484364L;

    private String userEmail;
    private String code;

}