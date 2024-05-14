package com.treay.yujian.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.treay.yujian.mapper.MessageSendLogMapper;
import com.treay.yujian.model.domain.MessageSendLog;
import com.treay.yujian.service.MessageSendLogService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
* @author 16799
* @description 针对表【message_send_log(消息发送日志表)】的数据库操作Service实现
* @createDate 2024-05-02 11:36:39
*/
@Service
public class MessageSendLogServiceImpl extends ServiceImpl<MessageSendLogMapper, MessageSendLog>
    implements MessageSendLogService {
    @Resource
    MessageSendLogMapper massageSendLogMapper;

    @Override
    public MessageSendLog getBySenderIdAndRecipientId(Long senderId, Long recipientId) {

        return massageSendLogMapper.getBySenderIdAndRecipientId(senderId,recipientId);
    }
}




