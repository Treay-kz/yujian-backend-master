package com.treay.yujian.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.treay.yujian.model.domain.Notice;
import com.treay.yujian.model.domain.User;

import java.util.List;

/**
* @author 16799
* @description 针对表【notice(通知表)】的数据库操作Service
* @createDate 2024-05-02 11:36:39
*/
public interface NoticeService extends IService<Notice> {

    /**
     * 获取好友申请信息
     * @param id
     * @return
     */
    List<User> getNoticeData(Long id);

    Notice getBySenderIdAndRecipientId(Long senderId, Long recipientId);
}
