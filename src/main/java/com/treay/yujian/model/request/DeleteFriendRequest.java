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
public class DeleteFriendRequest extends CurrentUserRequest implements Serializable {

    private static final long serialVersionUID = -3001850738731102945L;

    private Long id;

    private Long deleteId;


}
