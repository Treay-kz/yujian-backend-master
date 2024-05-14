package com.treay.yujian.model.request;

import lombok.Data;
import lombok.ToString;

import java.io.Serializable;

/**
 * 
 * @author: Treay
 *
 **/
@Data
@ToString
public class AddFriendRequest implements Serializable {

    private static final long serialVersionUID = -3875902490135062967L;

    private Long senderId;

    private Long recipientId;

    private String userAccount;

    private String uuid;

    private Boolean isAgree;

}
