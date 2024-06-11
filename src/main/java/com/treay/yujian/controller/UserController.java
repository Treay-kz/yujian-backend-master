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
        /*@Param：在MyBatis等ORM框架中，用于给SQL映射文件中的参数命名，以便于将方法参数绑定到SQL语句的参数上。
             一般用在在 Mapper 层中写 sql 语句时将参数传入 sql 语句中*/
        // 使用 userAccount和uuid(用户登录凭证)校验用户是否登录
        User user =  userService.getLoginUser(addFriendRequest.getUserAccount(), addFriendRequest.getUuid());
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }/*调用userService的addFriend方法来处理添加好友的业务逻辑，返回一个Boolean类型的结果。*/
        // 已登录，调用userService的addFriend方法来处理添加好友的业务逻辑，返回一个Boolean类型的结果。
        Boolean result = userService.addFriend(addFriendRequest);
        return ResultUtils.success(result);
    }// 整段代码通过验证请求参数、用户登录状态，并调用相应的服务方法来实现添加好友功能

    /**
     * 删除好友
     * @param deleteFriendRequest
     * @return
     */
    @PostMapping("/friend/delete")//是Spring MVC的注释，映射HTTP POST 发送的请求，使客户端发送的到该地址的时候能够调用该地址
    public BaseResponse<Boolean> deleteFriend(@RequestBody DeleteFriendRequest deleteFriendRequest) {
        //用于接收客户端发送的JSON格式的数据，@RequestBody注解告诉Spring框架将请求体中的数据绑定到DeleteFriendRequest对象中。
        // DeleteFriendRequest是一个自定义的封装类，
        //定义了一个通用的响应类BaseResponse<T>，它被设计用于封装从服务器端返回给客户端的数据和状态信息
        //判空，用户请求响应是否为空，为空则抛出异常
        if (deleteFriendRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        //不为空，判断当前用户是否登录
        User loginUser = userService.getLoginUser(deleteFriendRequest.getUserAccount(), deleteFriendRequest.getUuid());
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN, "未登录");
        }
        //已登录，调用userService中的deleteFriend方法，进行删除好友操作
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

        // 使用 userAccount和uuid(用户登录凭证)校验用户是否登录
        User loginUser = userService.getLoginUser(addFriendRequest.getUserAccount(), addFriendRequest.getUuid());
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN, "未登录");
        }

        // 如果登录用户 不等于 接收者id则返回
        if (loginUser.getId() != addFriendRequest.getRecipientId()){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        boolean agree = userService.agreeFriend(addFriendRequest);/*调用userService的agreeFriend方法来处理同意好友请求的业务逻辑*/
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
        // 使用 userAccount和uuid(用户登录凭证)校验用户是否登录
        User loginUser = userService.getLoginUser(addFriendRequest.getUserAccount(), addFriendRequest.getUuid());
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN, "未登录");
        }
        // 如果登录用户 不等于 接收者id则返回异常
        if (loginUser.getId() != addFriendRequest.getRecipientId()){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean agree = userService.rejectFriend(addFriendRequest);/*调用userService的agreeFriend方法来处理同意好友请求的业务逻辑*/
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
        //判空，用户请求是否为空
        if (currentUserRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 调用userService的getLoginUser方法，通过userAccount和uuid获取当前登录的用户信息。
        User loginUser = userService.getLoginUser(currentUserRequest.getUserAccount(), currentUserRequest.getUuid());
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NO_AUTH, "未登录");
        }
        //调用userService的listFriend方法
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

    /*Controller层：
    用户通过前端发送POST请求到/register端点，请求体包含UserRegisterRequest对象，该对象有userAccount(用户名)、userEmail(邮箱)、code(验证码)、userPassword(密码)、checkPassword(确认密码)。
    控制器检查userRegisterRequest是否为空，若为空则抛出PARAMS_ERROR异常。
    控制器调用userService.userRegister方法，传递注册所需的所有参数。
    UserService实现类：
    参数校验：首先进行参数有效性校验，确保所有必填项非空，用户名长度、密码长度合规，邮箱格式正确，密码与确认密码匹配，且验证码正确。
    数据库查询：检查用户名是否已存在，如果存在则抛出PARAMS_ERROR异常。
    密码加密：使用MD5加密用户密码。
    用户实体构建：创建User对象，设置账户、加密后的密码、邮箱等信息，包括默认的头像URL、用户名和标签。
    保存用户：通过userMapper.insert方法将用户信息保存至数据库，如果插入失败则抛出SYSTEM_ERROR异常。
    生成星球编码：设置用户的星球编码并更新用户信息。
    返回用户ID：注册成功后，返回新用户的ID给Controller层。
     */
    @PostMapping("/register")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest userRegisterRequest) {
        // 判断传入的注册请求是否为空
        if (userRegisterRequest == null) {
            // 如果为空，抛出参数错误异常
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 从请求中获取用户账号、密码、邮箱、验证码和确认密码
        String userAccount = userRegisterRequest.getUserAccount();
        String userPassword = userRegisterRequest.getUserPassword();
        String userEmail = userRegisterRequest.getUserEmail();
        String code = userRegisterRequest.getCode();
        String checkPassword = userRegisterRequest.getCheckPassword();
        // 判断所有必需参数是否为空
        if (StringUtils.isAnyBlank(userAccount, userPassword, checkPassword,userEmail,code)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"参数不能为空");
        }
        // 调用用户服务层的注册方法，并传入注册信息
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

    /*
        Controller层：
        用户通过前端发送POST请求到/login端点，请求体包含UserLoginRequest对象，该对象有userAccount(用户名)、userPassword(密码)、uuid(用户唯一标识)。
        控制器检查userLoginRequest是否为空，以及用户名和密码是否为空，若为空则抛出PARAMS_ERROR异常。
        控制器调用userService.userLogin方法，传递登录所需参数。
        UserService实现类：
        参数校验：检查用户名、密码长度，以及用户名是否含有非法字符。
        密码加密：加密用户提供的密码。
        登录状态检查：检查Redis中是否有该用户的登录会话，如果有且未过期，则直接返回现有的Token。
        查询数据库：如果Redis中没有有效的会话，使用加密后的密码查询数据库中是否存在匹配的用户。
        安全性处理：获取安全的用户对象（可能去除敏感信息）。
        生成Token：生成新的UUID作为Token的一部分，同时存储用户安全信息到Redis中，设置过期时间。
        返回Token：登录成功后，返回新生成的Token给Controller层。
     */
    @PostMapping("/login")
    public BaseResponse<String> userLogin(@RequestBody UserLoginRequest userLoginRequest) {
        // 判断传入的登录请求是否为空
        if (userLoginRequest == null) {
            // 如果为空，返回参数错误响应
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 从请求中获取用户账号和密码
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();
        String uuid = userLoginRequest.getUuid();
        // 第一次登录不需要uuid
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
   // 这段代码是一个GET请求的接口方法，用于推荐用户列表。它接受参数pageSize（每页记录数）、pageNum（页码）、userAccount（用户账号）
    // 和uuid（用户UUID），用于筛选和分页结果。首先，方法对输入参数进行验证，如果任何参数无效，
    // 则抛出一个带有错误代码的BusinessException异常。接下来，它通过调用userService组件的getLoginUser方法来检查用户是否已登录。
    // 然后，它创建一个新的Page对象来存储分页结果。如果推荐用户列表未缓存，则从数据库中查询按照addCount属性降序排序的所有用户列表。
    // 然后，过滤掉当前用户。接下来，它对用户列表进行数据脱敏处理，
    // 通过调用getSafetyUser方法对每个用户进行处理。经过过滤和脱敏处理后的用户列表设置为userPage对象的记录。
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
        Page<User> userPage = userService.recommend(loginUser.getId(), pageSize, pageNum);

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
