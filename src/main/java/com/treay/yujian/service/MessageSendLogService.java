package com.treay.yujian.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.treay.yujian.model.domain.MessageSendLog;


/**
* @author 16799
* @description 针对表【message_send_log(消息发送日志表)】的数据库操作Service
* @createDate 2024-05-02 11:36:39
*/
public interface MessageSendLogService extends IService<MessageSendLog> {

    MessageSendLog getBySenderIdAndRecipientId(Long senderId, Long recipientId);

}
