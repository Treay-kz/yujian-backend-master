package com.treay.yujian.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.treay.yujian.model.domain.Chat;
import com.treay.yujian.model.domain.Team;


/**
* @author 16799
* @description 针对表【chat(消息表)】的数据库操作Service
* @createDate 2024-05-15 18:25:33
*/
public interface ChatService extends IService<Chat> {
    /**
     * 创建私聊

     * @param senderId
     * @param recipientId
     * @return
     */
    boolean addUserChat(Long senderId, Long recipientId);

    /**
     * 创建队伍聊天
     * @param team
     * @param userId
     * @return
     */
    boolean addTeamChat(Team team, Long userId);
    /**
     * 判断是否为好友
     * @param senderId
     * @param recipientId
     * @return
     */
    boolean checkFriend(Long senderId, Long recipientId);
}
