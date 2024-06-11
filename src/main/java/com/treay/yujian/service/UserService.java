package com.treay.yujian.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.treay.yujian.common.BaseResponse;
import com.treay.yujian.model.domain.User;
import com.baomidou.mybatisplus.extension.service.IService;
import com.treay.yujian.model.dto.UserDTO;
import com.treay.yujian.model.request.*;
import com.treay.yujian.model.vo.TagVo;
import com.treay.yujian.model.vo.UserSendMessage;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 用户服务
 *
 * @author Treay
 * 
 */
public interface UserService extends IService<User> {

    /**
     * 用户注册
     *
     * @param userAccount   用户账户
     * @param userPassword  用户密码
     * @param checkPassword 校验密码
     * @param  userEmail    用户邮箱
     * @param  code         验证码
     * @return 新用户 id
     */
    long userRegister(String userAccount, String userEmail,String code,String userPassword, String checkPassword);

    /**
     * 用户登录
     *
     * @param userAccount  用户账户
     * @param userPassword 用户密码
     * @param uuid
     * @return 脱敏后的用户信息
     */
    String userLogin(String userAccount, String userPassword, String uuid);

    /**
     * 用户脱敏
     *
     * @param originUser
     * @return
     */
    User getSafetyUser(User originUser);


    /**
     * 根据标签搜索用户
     *
     * @param byTagsRequest
     * @return
     */
    Page<User> searchUsersByTags(SearchUserByTagsRequest byTagsRequest);

    /**
     * 更新用户信息
     * @param userDTO
     * @param loginUser
     * @return
     */
    int updateUser(UserDTO userDTO, User loginUser);



    /**
     * 获取当前登录用户信息
     *
     * @param userAccount
     * @param uuid
     * @return
     */
    User getLoginUser(String userAccount, String uuid);
    /**
     * 是否为管理员
     *
     * @param request
     * @return
     */
    boolean isAdmin(HttpServletRequest request);

    /**
     * 是否为管理员
     *
     * @param loginUser
     * @return
     */
    boolean isAdmin(User loginUser);

    /**
     * 匹配用户
     * @param num
     * @param loginUser
     * @return
     */
    List<User> matchUsers(long num, User loginUser);

    /**
     * 发邮件
     * @param userSendMessage
     * @return
     */
    BaseResponse<Boolean> sendEmail(UserSendMessage userSendMessage);

    /**
     * 修改密码
     * @param userForgetRequest
     * @return
     */
    BaseResponse<Boolean> updatePassword(UserForgetRequest userForgetRequest);


    /**
     * 添加好友
     * @param addFriendRequest
     * @return
     */
    Boolean addFriend(AddFriendRequest addFriendRequest);

    /**
     * 删除好友
     * @param deleteFriendRequest
     * @return
     */
    Boolean deleteFriend(DeleteFriendRequest deleteFriendRequest);

    /**
     * 同意添加好友
     * @param addFriendRequest
     * @return
     */
    boolean agreeFriend(AddFriendRequest addFriendRequest);

    /**
     * 拒绝添加好友
     * @param addFriendRequest
     * @return
     */
    boolean rejectFriend(AddFriendRequest addFriendRequest);

    /**
     * 查看好友列表
     * @param loginUser
     * @return
     */
    List<User> listFriend(User loginUser);

    /**
     * 同意好友后刷新缓存
     * @param currentUserRequest
     * @return
     */
    boolean refreshCache(CurrentUserRequest currentUserRequest);

    /**
     * 更新用户标签
     * @param userDTO
     * @param loginUser
     * @return
     */
    int updateTags(UserDTO userDTO, User loginUser);

    /**
     * 推荐用户
     * @param userId
     * @param pageSize
     * @param pageNum
     * @return
     */
    Page<User> recommend(long userId, long pageSize, long pageNum);
}
