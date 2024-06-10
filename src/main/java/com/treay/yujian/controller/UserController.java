package com.treay.yujian.controller;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
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

import static com.baomidou.mybatisplus.core.enums.SqlKeyword.DESC;
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


    /**
     * 添加好友
     * @param addFriendRequest
     * @return
     */
    //@PostMapping：处理HTTP POST请求，一般用于在服务器上创建资源（用户注册）   与 getmapping 一样，规定前端请求类型为 post
    //@RequestBody： ：将HTTP请求的body内容绑定到上参数。具体体现为接收 JSON、xml 格式的数据将其转化为 Java 对象
    //一般用于规定 controller 层的所有方法以 json 格式的数据返回
    @PostMapping("/friend/add")
    public BaseResponse<Boolean> addFriend(@RequestBody AddFriendRequest addFriendRequest) {
        if (addFriendRequest == null) {
            //  用于处理应用程序中的错误
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 根据AddFriendRequest对象中的uuid获取一个key，并使用该key从Redis中获取与用户账号对应的User对象。如果获取的User对象为null，
        // 说明用户未登录，此时会抛出一个带有错误代码NOT_LOGIN的BusinessException异常。
        User user =  userService.getLoginUser(addFriendRequest.getUserAccount(), addFriendRequest.getUuid());
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }
        // 调用userService的addFriend方法来处理添加好友的业务逻辑，返回一个Boolean类型的结果。
        Boolean result = userService.addFriend(addFriendRequest);
        return ResultUtils.success(result);
    }// 整段代码通过验证请求参数、用户登录状态，并调用相应的服务方法来实现添加好友功能

    /**
     * 删除好友
     * @param deleteFriendRequest
     * @return
     */
    @PostMapping("/friend/delete")
    public BaseResponse<Boolean> deleteFriend(@RequestBody DeleteFriendRequest deleteFriendRequest) {
        if (deleteFriendRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(deleteFriendRequest.getUserAccount(), deleteFriendRequest.getUuid());
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN, "未登录");
        }
        Boolean result = userService.deleteFriend(deleteFriendRequest);
        return ResultUtils.success(result);
    }

    /**
     * 同意好友申请
     * @param addFriendRequest
     * @return
     */
    @PostMapping("/friend/agree")
    public BaseResponse<Boolean> agreeFriend(@RequestBody AddFriendRequest addFriendRequest) {
        // 检查传入的AddFriendRequest参数是否为null
        if (addFriendRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 调用userService的getLoginUser方法来验证用户的登录状态
        // 根据传入的userAccount和uuid参数在后台获取对应的登录用户信息并赋值给loginUser变量
        User loginUser = userService.getLoginUser(addFriendRequest.getUserAccount(), addFriendRequest.getUuid());
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN, "未登录");
        }
        // 调用userService的agreeFriend方法来处理同意好友请求的业务逻辑
        boolean agree = userService.agreeFriend(addFriendRequest);
        if (!agree) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "同意好友申请失败");
        }
        return ResultUtils.success(agree);
    }// 这段代码整个流程中检查了请求参数、用户登录状态以及同意好友请求的结果

    /**
     * 拒绝好友申请
     * @param addFriendRequest
     * @return
     */
    @PostMapping("/friend/reject")
    public BaseResponse<Boolean> rejectFriend(@RequestBody AddFriendRequest addFriendRequest) {
        if (addFriendRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        //调用userService的getLoginUser方法来验证用户的登录状态
        // 根据传入的userAccount和uuid参数在后台获取对应的登录用户信息并赋值给loginUser变量
        User loginUser = userService.getLoginUser(addFriendRequest.getUserAccount(), addFriendRequest.getUuid());
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN, "未登录");
        }
        boolean agree = userService.rejectFriend(addFriendRequest);
        if (!agree) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "拒绝好友申请失败");
        }
        return ResultUtils.success(true);
    }//处理拒绝好友请求的逻辑，整体流程包括参数验证、用户登录状态检查、拒绝好友请求的处理以及返回统一格式的处理结果

    /**
     * 获取好友列表
     * @param currentUserRequest
     * @return
     */
    @GetMapping("/friend/list")
    public BaseResponse<List<User>> listFriend(CurrentUserRequest currentUserRequest) {
        if (currentUserRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(currentUserRequest.getUserAccount(), currentUserRequest.getUuid());
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NO_AUTH, "未登录");
        }
        List<User> friendList = userService.listFriend(loginUser);
        return ResultUtils.success(friendList);
    }


    /**
     * 刷新缓存
     * @param currentUserRequest
     * @return
     */
    @PostMapping("/refresh/cache")
    public BaseResponse<Boolean> refreshCache(@RequestBody CurrentUserRequest currentUserRequest) {
        boolean refresh = userService.refreshCache(currentUserRequest);
        return ResultUtils.success(refresh);
    }

    /**
     * 发送验证码
     * @param userSendMessage
     * @return
     */
    @PostMapping("/sendMessage")
    public BaseResponse<Boolean> sendMessage(@RequestBody UserSendMessage userSendMessage) {
        return userService.sendEmail(userSendMessage);
    }

    /**
     * 用户注册
     * @param userRegisterRequest
     * @return
     */
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
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"参数不能为空");
        }

        long result = userService.userRegister(userAccount, userEmail,code,userPassword, checkPassword);
        return ResultUtils.success(result);
    }

    /**
     * 用户修改密码
     * @param userForgetRequest
     * @return
     */
    @PutMapping("/forget")
    public BaseResponse<Boolean> forget(@RequestBody UserForgetRequest userForgetRequest) {
        return userService.updatePassword(userForgetRequest);
    }

    /**
     * 用户登录
     * @param userLoginRequest
     * @return
     */
    @PostMapping("/login")
    public BaseResponse<String> userLogin(@RequestBody UserLoginRequest userLoginRequest) {
        if (userLoginRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();
        String uuid = userLoginRequest.getUuid();
        if (StringUtils.isAnyBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String result = userService.userLogin(userAccount, userPassword, uuid);
        return ResultUtils.success(result);
    }

    /**
     * 用户注销
     * @param userRequest
     * @return
     */
    @PostMapping("/logout")
    public BaseResponse<Integer> userLogout(CurrentUserRequest userRequest) {
        String key = TOKEN_KEY + userRequest.getUuid();
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

    /**
     * 搜索用户（条件查询）
     * @param pageSize
     * @param current
     * @param currentUserAccount
     * @return
     */
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

    /**
     * 通过标签搜素用户
     * @param searchUserByTagsRequest
     * @return
     */
    @GetMapping("/search/tags")
    public BaseResponse<Page<User>> searchUsersByTags(SearchUserByTagsRequest searchUserByTagsRequest) {

        if (CollectionUtils.isEmpty(searchUserByTagsRequest.getTagNameList())) {
            throw new BusinessException(ErrorCode.NULL_ERROR);
        }
        Page<User> userList = userService.searchUsersByTags(searchUserByTagsRequest);
        return ResultUtils.success(userList);
    }


    /**
     * 推荐用户
     * @param pageSize
     * @param pageNum
     * @param userAccount
     * @param uuid
     * @return
     */
    @GetMapping("/recommend")
    public BaseResponse<Page<User>> recommendUsers(long pageSize, long pageNum, String userAccount, String uuid) {
        // 参数验证
        if (pageSize <= 0 || pageNum <= 0 || StringUtils.isEmpty(userAccount) || StringUtils.isEmpty(uuid)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 判断是否为当前用户，
        User loginUser = userService.getLoginUser(userAccount, uuid);
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NO_AUTH,"未登录");
        }
        String key = USER_SEARCH_KEY + loginUser.getId();
        Page<User> userPage = new Page<>();
        // 读取缓存
        List<User> userList = (List<User>) redisTemplate.opsForValue().get(key);
        // 如果缓存有数据，直接返回
        if (CollectionUtils.isNotEmpty(userList)) {
            userList = userList.stream()
                    .filter(user -> user.getId() != loginUser.getId())
                    .collect(Collectors.toList());
            userPage.setRecords(userList);
            return ResultUtils.success(userPage);
        }

        // 从数据库中获取所有用户列表
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().orderByDesc(User::getAddCount);
        userList = userService.list(queryWrapper);


        // 过滤当前登录用户
        userList = userList
                .stream()
                .filter(user -> user.getId() != loginUser.getId())
                .collect(Collectors.toList());

        // 对用户进行脱敏处理
        List<User> safetyUsers = userList.stream().map(userService::getSafetyUser).collect(Collectors.toList());
        // 设置分页信息
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

    /**
     * 更新用户
     * @param userDTO
     * @return
     */

    @PostMapping("/update")
    public BaseResponse<Integer> updateUser(@RequestBody UserDTO userDTO) {
        // 校验参数是否为空
        if (userDTO == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(userDTO.getCurrentUserAccount(), userDTO.getUuid());
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NO_AUTH,"未登录");
        }
        int result = userService.updateUser(userDTO, loginUser);
        return ResultUtils.success(result);
    }

    /**
     * 更新标签
     * @param userDTO
     * @return
     */

    @PostMapping("/update/tags")
    public BaseResponse<Integer> updateTags(@RequestBody UserDTO userDTO) {
        // 校验参数是否为空
        if (userDTO == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(userDTO.getCurrentUserAccount(), userDTO.getUuid());
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"未登录");
        }
        int result = userService.updateTags(userDTO, loginUser);
        return ResultUtils.success(result);
    }

    /**
     * 删除用户
     * @param id
     * @param request
     * @return
     */
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
     * @param matchUserRequest
     * @return
     */
    @GetMapping("/match")
    public BaseResponse<List<User>> matchUsers(MatchUserRequest matchUserRequest) {
        if (matchUserRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"参数为空");
        }
        int num = matchUserRequest.getNum();
        User loginUser = userService.getLoginUser(matchUserRequest.getUserAccount(), matchUserRequest.getUuid());
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NO_AUTH,"未登录");
        }
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
