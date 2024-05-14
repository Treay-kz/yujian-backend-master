package com.treay.yujian.controller;


import com.treay.yujian.common.BaseResponse;
import com.treay.yujian.common.ErrorCode;
import com.treay.yujian.common.ResultUtils;
import com.treay.yujian.exception.BusinessException;
import com.treay.yujian.model.domain.User;
import com.treay.yujian.service.NoticeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 *  消息通知
 * @author: Treay
 *
 **/
@RestController
@RequestMapping("/notice")
@CrossOrigin(origins = {"http://localhost:3000","http://yujian.treay.cn"})
@Slf4j
public class NoticeController {

    @Resource
    private NoticeService noticeService;

    /**
     * 获取好友申请信息
     * @param id
     * @return
     */
    @GetMapping("/friend/add")
    public BaseResponse<List<User>> getNotice(Long id){
        //校验
        if (id == null || id <0){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        List<User> userList = noticeService.getNoticeData(id);
        return ResultUtils.success(userList);
    }



}
