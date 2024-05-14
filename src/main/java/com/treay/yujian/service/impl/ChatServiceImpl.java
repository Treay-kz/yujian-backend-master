package com.treay.yujian.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.treay.yujian.mapper.ChatMapper;
import com.treay.yujian.model.domain.Chat;
import com.treay.yujian.service.ChatService;
import org.springframework.stereotype.Service;

/**
* @author 16799
* @description 针对表【chat(消息表)】的数据库操作Service实现
* @createDate 2024-05-14 17:23:21
*/
@Service
public class ChatServiceImpl extends ServiceImpl<ChatMapper, Chat>
    implements ChatService {

}




