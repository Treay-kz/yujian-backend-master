package com.treay.yujian.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.treay.yujian.common.ErrorCode;
import com.treay.yujian.exception.BusinessException;
import com.treay.yujian.mapper.ChatMapper;
import com.treay.yujian.model.domain.Chat;
import com.treay.yujian.model.domain.Team;
import com.treay.yujian.model.domain.User;
import com.treay.yujian.service.ChatService;
import com.treay.yujian.service.TeamService;
import com.treay.yujian.service.UserService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;


/**
* @author 16799
* @description 针对表【chat(消息表)】的数据库操作Service实现
* @createDate 2024-05-15 18:25:33
*/
@Service
public class ChatServiceImpl extends ServiceImpl<ChatMapper, Chat>
    implements ChatService {

    @Resource
    @Lazy
    private UserService userService;

    @Resource
    @Lazy
    private TeamService teamService;
    /**
     * 创建私聊
     * @param senderId
     * @param recipientId
     * @return
     */
    @Override
    public boolean addUserChat(Long senderId, Long recipientId) {

//        1. 校验参数
        // 判断两人是否为好友
        if(!checkFriend(senderId, recipientId)){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"不是好友，但在创建频道");
        }
        // 创建memberid列表
        JSONArray jsonArray = new JSONArray();
        jsonArray.add(senderId);
        jsonArray.add(recipientId);
        Chat chat = new Chat();
        chat.setMemberId(JSONUtil.toJsonStr(jsonArray));
        chat.setChatType(0);
        return this.save(chat);

    }

    @Override
    public boolean addTeamChat(Team team, Long userId) {
        JSONArray jsonArray = new JSONArray();
        jsonArray.add(userId);
        Chat chat = new Chat();
        chat.setMemberId(JSONUtil.toJsonStr(jsonArray));
        chat.setChatType(1);
        boolean result = this.save(chat);
        Long chatId = chat.getId();
        if (!result || chatId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"创建频道失败");
        }
        team.setChatId(chatId);
        return teamService.updateById(team);
    }

    /**
     * 判断是否为好友
     * @param senderId
     * @param recipientId
     * @return
     */
    @Override
    public boolean checkFriend(Long senderId, Long recipientId) {
        User sender = userService.getById(senderId);
        User recipient = userService.getById(recipientId);
        String senderFriendId = sender.getFriendId();
        String recipientFriendId = recipient.getFriendId();
        return senderFriendId.contains(recipientId.toString()) && recipientFriendId.contains(senderId.toString());
    }
}




