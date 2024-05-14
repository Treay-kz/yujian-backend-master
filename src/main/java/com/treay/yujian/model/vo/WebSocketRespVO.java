package com.treay.yujian.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 
 * @author: Treay
 *
 **/
@Data
public class WebSocketRespVO implements Serializable {


    private static final long serialVersionUID = 563709915200844227L;

    private String message;

    private Long senderId;

    private Boolean isAgree;
}
