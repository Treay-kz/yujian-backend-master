package com.treay.yujian.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.treay.yujian.model.domain.MessageSendLog;
import org.apache.ibatis.annotations.Param;


/**
* @author 16799
* @description 针对表【message_send_log(消息发送日志表)】的数据库操作Mapper
* @createDate 2024-05-02 11:36:39
* @Entity generator.domain.MessageSendLog
*/
public interface MessageSendLogMapper extends BaseMapper<MessageSendLog> {
    MessageSendLog getBySenderIdAndRecipientId(@Param("senderId") Long senderId, @Param("recipientId") Long recipientId);

}




