package com.treay.yujian.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.treay.yujian.mapper.MessageMapper;
import com.treay.yujian.model.domain.Message;
import com.treay.yujian.service.MessageService;
import org.springframework.stereotype.Service;

/**
* @author 16799
* @description 针对表【message(消息表)】的数据库操作Service实现
* @createDate 2024-05-14 17:23:21
*/
@Service
public class MessageServiceImpl extends ServiceImpl<MessageMapper, Message>
    implements MessageService {

}




