package com.treay.yujian.controller;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.treay.yujian.common.BaseResponse;
import com.treay.yujian.common.ErrorCode;
import com.treay.yujian.common.ResultUtils;
import com.treay.yujian.exception.BusinessException;
import com.treay.yujian.mapper.UserMapper;
import com.treay.yujian.model.dto.UserDTO;
import com.treay.yujian.model.request.*;
import com.treay.yujian.model.domain.User;
import com.treay.yujian.model.vo.UserSendMessage;
import com.treay.yujian.model.vo.WebSocketRespVO;
import com.treay.yujian.server.WebSocketServer;
import com.treay.yujian.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.treay.yujian.constant.RedisConstant.TOKEN_KEY;
import static com.treay.yujian.constant.RedisConstant.USER_SEARCH_KEY;

/**
 * 用户接口
 *
 * @author Treay
 * 
 */
@RestController
@RequestMapping("/user")
@CrossOrigin(origins = {"http://localhost:3000","http://yujian.treay.cn"}, allowCredentials="true")
@Slf4j
public class UserController {

    @Resource
    private UserService userService;
    @Resource
    private UserMapper userMapper;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private WebSocketServer webSocketServer;

    @PostMapping("/friend/add")
    public BaseResponse<Boolean> addFriend(@RequestBody AddFriendRequest addFriendRequest) {
        if (addFriendRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String key = TOKEN_KEY + addFriendRequest.getUuid();
        System.out.println("key = " + key);
        User user = (User) redisTemplate.opsForHash().get(key, addFriendRequest.getUserAccount());
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }
        Boolean result = userService.addFriend(addFriendRequest);
        return ResultUtils.success(result);
    }

    @PostMapping("/friend/delete")
    public BaseResponse<Boolean> deleteFriend(@RequestBody DeleteFriendRequest deleteFriendRequest) {
        if (deleteFriendRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        Boolean result = userService.deleteFriend(deleteFriendRequest);
        return ResultUtils.success(result);
    }

    @PostMapping("/friend/agree")
    public BaseResponse<Boolean> agreeFriend(@RequestBody AddFriendRequest addFriendRequest) {
        WebSocketRespVO webSocketRespVO = new WebSocketRespVO();
        boolean agree = userService.agreeFriend(addFriendRequest);
//        User user = userService.getById(addFriendRequest.getRecipientId());
//        webSocketRespVO.setMessage("添加" + user.getUsername() + "的好友申请已通过");
//        webSocketRespVO.setIsAgree(true);
//        webSocketRespVO.setSenderId(addFriendRequest.getSenderId());
//        webSocketServer.sendToAllClient(JSONUtil.toJsonStr(webSocketRespVO));
        return ResultUtils.success(agree);
    }

    @PostMapping("/friend/reject")
    public BaseResponse<Boolean> rejectFriend(@RequestBody AddFriendRequest addFriendRequest) {
        WebSocketRespVO webSocketRespVO = new WebSocketRespVO();
        boolean agree = userService.rejectFriend(addFriendRequest);
        User user = userService.getById(addFriendRequest.getRecipientId());
        webSocketRespVO.setMessage("添加" + user.getUsername() + "的好友申请被拒绝");
        webSocketRespVO.setIsAgree(false);
        webSocketRespVO.setSenderId(addFriendRequest.getSenderId());
        webSocketServer.sendToAllClient(JSONUtil.toJsonStr(webSocketRespVO));
        return ResultUtils.success(true);
    }

    @GetMapping("/friend/list")
    public BaseResponse<List<User>> listFriend(CurrentUserRequest currentUserRequest) {
        if (currentUserRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(currentUserRequest.getUserAccount(), currentUserRequest.getUuid());
        List<User> friendList = userService.listFriend(loginUser);
        return ResultUtils.success(friendList);
    }

    @PostMapping("/refresh/cache")
    public BaseResponse<Boolean> refreshCache(@RequestBody CurrentUserRequest currentUserRequest) {
        boolean refresh = userService.refreshCache(currentUserRequest);
        return ResultUtils.success(refresh);
    }

    @PostMapping("/sendMessage")

    public BaseResponse<Boolean> sendMessage(@RequestBody UserSendMessage userSendMessage) {
        return userService.sendEmail(userSendMessage);
    }
    @PostMapping("/register")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest userRegisterRequest) {
        if (userRegisterRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String userAccount = userRegisterRequest.getUserAccount();
        String userPassword = userRegisterRequest.getUserPassword();
        String userEmail = userRegisterRequest.getUserEmail();
        String code = userRegisterRequest.getCode();
        String checkPassword = userRegisterRequest.getCheckPassword();
        if (StringUtils.isAnyBlank(userAccount, userPassword, checkPassword,userEmail,code)) {
            return null;
        }
        long result = userService.userRegister(userAccount, userEmail,code,userPassword, checkPassword);
        return ResultUtils.success(result);
    }

    @PutMapping("/forget")
    public BaseResponse<Boolean> forget(@RequestBody UserForgetRequest userForgetRequest) {
        return userService.updatePassword(userForgetRequest);
    }
    @PostMapping("/login")
    public BaseResponse<String> userLogin(@RequestBody UserLoginRequest userLoginRequest) {
        if (userLoginRequest == null) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR);
        }
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();
        String uuid = userLoginRequest.getUuid();
        if (StringUtils.isAnyBlank(userAccount, userPassword)) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR);
        }
        String key = userService.userLogin(userAccount, userPassword, uuid);
        return ResultUtils.success(key);
    }

    @PostMapping("/logout")
    public BaseResponse<Integer> userLogout(CurrentUserRequest userRequest) {
        String key = TOKEN_KEY + userRequest.getUuid();
        System.out.println(key);
        if (key == null){
            throw  new BusinessException(ErrorCode.NULL_ERROR,"UUid为空");
        }
        Integer result = Math.toIntExact(redisTemplate.opsForHash().delete(key, userRequest.getUserAccount()));
        return ResultUtils.success(result);
    }

    /**
     * 获取当前用户
     *
     * @param userRequest
     * @return
     */
    @GetMapping("/current")
    public BaseResponse<User> getCurrentUser(CurrentUserRequest userRequest) {
        String key = TOKEN_KEY + userRequest.getUuid();
        User user = (User) redisTemplate.opsForHash().get(key, userRequest.getUserAccount());
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        } else {
            redisTemplate.expire(TOKEN_KEY + userRequest.getUuid(), 10, TimeUnit.MINUTES);
        }
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(User::getUserAccount, user.getUserAccount());
        User one = userService.getOne(queryWrapper);
        return ResultUtils.success(one);
    }

    @GetMapping("/search")
    public BaseResponse<Page<User>> searchUsers(long pageSize, long current, String currentUserAccount) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.lambda().eq(User::getUserAccount, currentUserAccount);
        User currentUser = userService.getOne(wrapper);
        if (currentUser.getUserRole() == 0) {
            throw new BusinessException(ErrorCode.NO_AUTH);
        }

        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        Page<User> userPage = userService.page(new Page<>(current, pageSize), queryWrapper);

        return ResultUtils.success(userPage);
    }

    @GetMapping("/search/tags")
    public BaseResponse<Page<User>> searchUsersByTags(SearchUserByTagsRequest byTagsRequest) {
        System.out.println("tagNameList = " + byTagsRequest);
        if (com.baomidou.mybatisplus.core.toolkit.CollectionUtils.isEmpty(byTagsRequest.getTagNameList())) {
            throw new BusinessException(ErrorCode.NULL_ERROR);
        }
        Page<User> userList = userService.searchUsersByTags(byTagsRequest);
        return ResultUtils.success(userList);
    }


    @GetMapping("/recommend")
    public BaseResponse<Page<User>> recommendUsers(long pageSize, long pageNum, String userAccount, String uuid) {
        // 参数验证
        if (pageSize <= 0 || pageNum <= 0 || StringUtils.isEmpty(userAccount) || StringUtils.isEmpty(uuid)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        User loginUser = userService.getLoginUser(userAccount, uuid);
        String key = USER_SEARCH_KEY + loginUser.getId();

        // 读取缓存
        List<User> userList = (List<User>) redisTemplate.opsForValue().get(key);
        Page<User> userPage = new Page<>();
        // 如果缓存有数据，直接返回
        if (com.baomidou.mybatisplus.core.toolkit.CollectionUtils.isNotEmpty(userList)) {
            userList = userList.stream()
                    .filter(user -> user.getId() != loginUser.getId())
                    .collect(Collectors.toList());
            userPage.setRecords(userList);
            return ResultUtils.success(userPage);
        }

        // 查询数据库
        userList = userMapper.searchAddCount();

        // 过滤当前登录用户
        userList = userList
                .stream()
                .filter(user -> user.getId() != loginUser.getId())
                .collect(Collectors.toList());

        // 对用户进行处理
        List<User> safetyUsers = userList.stream().map(userService::getSafetyUser).collect(Collectors.toList());
        userPage.setRecords(safetyUsers);

        // 写缓存
        try {
            redisTemplate.opsForValue().set(key, safetyUsers, 12, TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("Error setting Redis key", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }

        return ResultUtils.success(userPage);
    }


    @PostMapping("/update")
    public BaseResponse<Integer> updateUser(@RequestBody UserDTO userDTO) {
        // 校验参数是否为空
        if (userDTO == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(userDTO.getCurrentUserAccount(), userDTO.getUuid());
        int result = userService.updateUser(userDTO, loginUser);
        return ResultUtils.success(result);
    }


    @PostMapping("/update/tags")
    public BaseResponse<Integer> updateTags(@RequestBody UserDTO userDTO) {
        // 校验参数是否为空
        if (userDTO == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(userDTO.getCurrentUserAccount(), userDTO.getUuid());
        int result = userService.updateTags(userDTO, loginUser);
        return ResultUtils.success(result);
    }
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteUser(@RequestBody long id, HttpServletRequest request) {
        if (!userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH);
        }
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean b = userService.removeById(id);
        return ResultUtils.success(b);
    }

    /**
     * 获取最匹配的用户
     *
     * @param currentUserRequest
     * @return
     */
    @GetMapping("/match")
    public BaseResponse<List<User>> matchUsers(CurrentUserRequest currentUserRequest) {
        int num = currentUserRequest.getNum();
        if (num <= 0 || num > 20) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(currentUserRequest.getUserAccount(), currentUserRequest.getUuid());

        List<User> matchUser = userService.matchUsers(num, loginUser);

        return ResultUtils.success(matchUser);
    }

//    /**
//     * 显示登录用户信息
//     * @param request
//     * @return
//     */
//    @GetMapping("/getNewUserInfo")
//    public BaseResponse<User> getNewUserInfo(HttpServletRequest request) {
//        User loginUser = userService.getLoginUser(request);
//        String id = String.valueOf(loginUser.getId());
//        log.info("id:"+id);
//        if (CollectionUtils.isEmpty(Collections.singleton(id))) {
//            throw new BusinessException(ErrorCode.PARAMS_ERROR);
//        }
//        User user = userService.getById(id);
//        return ResultUtils.success(user);
//    }

//    /**
//     * 显示用户已拥有标签
//     * @param request
//     * @return
//     */
//    @GetMapping("/get/tags")
//    public BaseResponse<TagVo> getTags(HttpServletRequest request) {
//        User loginUser = userService.getLoginUser(request);
//        TagVo tagVo = userService.getTags(loginUser.getTags(),request);
//        log.info(tagVo.toString());
//        return ResultUtils.success(tagVo);
//    }

}
