package com.treay.yujian.model.request;

import lombok.Data;

@Data
public class CurrentUserRequest {
    private int num;
    private String userAccount;
    private String uuid;
}
