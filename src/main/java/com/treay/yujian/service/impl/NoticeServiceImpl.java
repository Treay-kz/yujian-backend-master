package com.treay.yujian.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.treay.yujian.mapper.NoticeMapper;
import com.treay.yujian.model.domain.Notice;
import com.treay.yujian.model.domain.User;
import com.treay.yujian.model.enums.AddFriendStatusEnum;
import com.treay.yujian.service.NoticeService;
import com.treay.yujian.service.UserService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
* @author 16799
* @description 针对表【notice(通知表)】的数据库操作Service实现
* @createDate 2024-05-02 11:36:39
*/
@Service
public class NoticeServiceImpl extends ServiceImpl<NoticeMapper, Notice>
    implements NoticeService {
    @Resource
    private UserService userService;

    @Resource
    private NoticeMapper noticeMapper;

    @Override
    public List<User> getNoticeData(Long id) {
        QueryWrapper<Notice> queryWrapper = new QueryWrapper<>();

        queryWrapper.lambda().eq(Notice::getRecipientId,id).eq(Notice::getAddFriendStatus,
                AddFriendStatusEnum.ADDING.getValue());

        List<Notice> noticeList = this.list(queryWrapper);
        ArrayList<Long> userIds = new ArrayList<>();

        noticeList.stream().map(notice -> userIds.add(notice.getSenderId())).collect(Collectors.toList());

        if (CollectionUtils.isEmpty(userIds)){
            return new ArrayList<>();
        }

        QueryWrapper<User> userQueryWrapper = new QueryWrapper<>();
        userQueryWrapper.lambda().in(User::getId,userIds);
        // select * from user in (ids)
        List<User> userList = userService.list(userQueryWrapper);

        List<User> users = userList.stream().map(user -> userService.getSafetyUser(user)).collect(Collectors.toList());
        return users;
    }

    @Override
    public Notice getBySenderIdAndRecipientId(Long senderId, Long recipientId) {

        return noticeMapper.getBySenderIdAndRecipientId(senderId,recipientId);
    }
}




