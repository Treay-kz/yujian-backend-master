package com.treay.yujian.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.treay.yujian.common.BaseResponse;
import com.treay.yujian.common.ErrorCode;
import com.treay.yujian.common.ResultUtils;
import com.treay.yujian.constant.UserConstant;
import com.treay.yujian.exception.BusinessException;
import com.treay.yujian.mapper.UserMapper;
import com.treay.yujian.model.domain.Notice;
import com.treay.yujian.model.dto.UserDTO;
import com.treay.yujian.model.enums.AddFriendStatusEnum;
import com.treay.yujian.model.request.*;
import com.treay.yujian.model.vo.TagVo;
import com.treay.yujian.model.vo.UserSendMessage;
import com.treay.yujian.service.NoticeService;
import com.treay.yujian.utils.AlgorithmUtils;
import com.treay.yujian.model.domain.User;
import com.treay.yujian.service.UserService;
import com.treay.yujian.utils.EmailUtils;
import com.treay.yujian.utils.ValidateCodeUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.util.Pair;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.DigestUtils;

import javax.annotation.Resource;
import javax.mail.MessagingException;
import javax.servlet.http.HttpServletRequest;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.treay.yujian.constant.RedisConstant.*;
import static com.treay.yujian.constant.UserConstant.USER_LOGIN_STATE;
import static com.treay.yujian.utils.ValidateCodeUtils.generateValidateCode;

/**
 * 用户服务实现类
 *
 * @author Treay
 * 
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements UserService {

    @Resource
    private UserMapper userMapper;

    /**
     * 盐值，混淆密码
     */
    private static final String SALT = "yujian";
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    @Lazy
    private NoticeService noticeService;


    @Override
    public long userRegister(String userAccount,String userEmail, String code, String userPassword, String checkPassword) {

        // 涉及查询数据库的判断要往后放，
        // 1. 校验
        if (StringUtils.isAnyBlank(userAccount, userEmail, code, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号过短");
        }
        if (userPassword.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短");
        }
        // 邮箱格式是否正确
        Pattern emailValidPattern = Pattern.compile("[a-zA-Z0-9]+@[A-Za-z0-9]+\\.[a-z0-9]");
        Matcher emailMatch = emailValidPattern.matcher(userEmail);
        if (!emailMatch.find()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "邮箱格式错误");
        }
        // 账户不能包含特殊字符
        String userAccountValidPattern = "[`~!@#$%^&*()+=|{}':;',\\\\[\\\\].<>/?~！@#￥%……&*（）——+|{}【】‘；：”“’。，、？]";
        Matcher userAccountMatcher = Pattern.compile(userAccountValidPattern).matcher(userAccount);
        if (userAccountMatcher.find()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户账号不能包含特殊字符");
        }
        // 密码和校验密码相同
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"密码和校验密码必须相同");
        }

        // 获取缓存验证码
        String redisKey = String.format(SEND_MESSAGE_KEY +  userEmail);
        ValueOperations<String, Object> valueOperations = redisTemplate.opsForValue();
        UserSendMessage sendMessage = (UserSendMessage) valueOperations.get(redisKey);
        if (!Optional.ofNullable(sendMessage).isPresent()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "获取验证码失败!");
        }
        String sendMessageCode = sendMessage.getCode();
        if (!code.equals(sendMessageCode)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "验证码不匹配!");
        }

        // 账户不能重复
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        // 设置查询条件
        queryWrapper.eq("userAccount", userAccount);
        // 查询数据库
        long count = userMapper.selectCount(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号重复");
        }

        // 2. 向数据库中插入数据的准备工作
        // 密码加密
        String encryptPassword = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
        // 3. 插入数据
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptPassword);
        user.setEmail(userEmail);
        String defaultUrl = "https://img1.baidu.com/it/u=1637179393,2329776654&fm=253&fmt=auto&app=138&f=JPEG?w=500&h=542";
        user.setAvatarUrl(defaultUrl);
        user.setUsername("用户" + generateValidateCode(6).toString());
        String defaultTag = "\"萌新\"";
        user.setTags("["+  defaultTag + "]");
        // this指userService
        long saveResult = userMapper.insert(user);

        if (saveResult <= 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"注册失败");
        }
        String planetCode = String.valueOf(user.getId());
        user.setPlanetCode(planetCode);
        this.updateById(user);

        return user.getId();
    }


    @Override
    public String userLogin(String userAccount, String userPassword, String uuid) {
        if (StringUtils.isAnyBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号过短");
        }
        if (userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短");
        }

        // 账户不能包含特殊字符
        String validPattern = "[`~!@#$%^&*()+=|{}':;',\\\\[\\\\].<>/?~！@#￥%……&*（）——+|{}【】‘；：”“’。，、？]";
        Matcher matcher = Pattern.compile(validPattern).matcher(userAccount);
        if (matcher.find()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号不能包括特殊字符");
        }
        // 2. 加密
        String encryptPassword = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
        String token = userAccount + "-" + uuid;

        // 从缓存中查询用户是否存在
        User cashUser = (User) redisTemplate.opsForHash().get(TOKEN_KEY + uuid, userAccount);
        if (cashUser != null) {
            redisTemplate.expire(TOKEN_KEY + uuid, 10, TimeUnit.MINUTES);
            return token;
        }

        // 查询用户是否存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        queryWrapper.eq("userPassword", encryptPassword);
        User user = userMapper.selectOne(queryWrapper);
        // 用户不存在
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号密码不匹配");
        }

        // 3. 用户脱敏
        User safetyUser = getSafetyUser(user);

        String newUuid = UUID.randomUUID().toString().replace("-", "");
        token = userAccount + "-" + newUuid;
        // 4. 存储用户信息到Redis中,设置key过期时间和token过期时间
        redisTemplate.opsForHash().put(TOKEN_KEY + newUuid, safetyUser.getUserAccount(), safetyUser);
        redisTemplate.expire(TOKEN_KEY + newUuid, 10, TimeUnit.MINUTES);
        return token;
    }

    /**
     * 用户脱敏
     *
     * @param originUser
     * @return
     */
    @Override
    public User getSafetyUser(User originUser) {
        if (originUser == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"登录用户为空");
        }
        User safetyUser = new User();
        safetyUser.setId(originUser.getId());
        safetyUser.setUsername(originUser.getUsername());
        safetyUser.setFriendId(originUser.getFriendId());
        safetyUser.setUserAccount(originUser.getUserAccount());
        safetyUser.setAvatarUrl(originUser.getAvatarUrl());
        safetyUser.setGender(originUser.getGender());
        safetyUser.setPhone(originUser.getPhone());
        safetyUser.setEmail(originUser.getEmail());
        safetyUser.setPlanetCode(originUser.getPlanetCode());
        safetyUser.setUserRole(originUser.getUserRole());
        safetyUser.setUserStatus(originUser.getUserStatus());
        safetyUser.setCreateTime(originUser.getCreateTime());
        safetyUser.setTags(originUser.getTags());
        safetyUser.setProfile(originUser.getProfile());
        return safetyUser;
    }

    /**
     * 用户注销
     *
     * @param request
     */
    @Override
    public int userLogout(HttpServletRequest request) {
        // 移除登录态
        request.getSession().removeAttribute(USER_LOGIN_STATE);
        return 1;
    }

    /**
     * 根据标签搜索用户（内存过滤）
     *
     * @param byTagsRequest 用户要拥有的标签
     * @return
     */
    @Override
    public Page<User> searchUsersByTags(SearchUserByTagsRequest byTagsRequest) {
        if (com.baomidou.mybatisplus.core.toolkit.CollectionUtils.isEmpty(byTagsRequest.getTagNameList())) {
            throw new BusinessException(ErrorCode.NULL_ERROR);
        }
        Gson gson = new Gson();
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        for (String tagName : byTagsRequest.getTagNameList()) {
            queryWrapper = queryWrapper.like("tags", tagName);
        }
        Page<User> userPage = this.page(new Page<>(byTagsRequest.getPageNum(), byTagsRequest.getPageSize()), queryWrapper);
        List<User> newUserList = userPage.getRecords().stream()
                .filter(
                        user -> {
                            String tagsStr = user.getTags();
                            Set<String> tagList =
                                    gson.fromJson(tagsStr, new TypeToken<Set<String>>() {
                                    }.getType());
                            tagList = Optional.ofNullable(tagList).orElse(new HashSet<>());
                            for (String tagName : byTagsRequest.getTagNameList()) {
                                if (!tagList.contains(tagName)) {
                                    return false;
                                }
                            }
                            return true;
                        })
                .map(this::getSafetyUser)
                .collect(Collectors.toList());
        userPage.setRecords(newUserList);
        return userPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateUser(UserDTO userDTO, User loginUser) {
        long userId = userDTO.getId();
        //如果是管理员，可以更新所有用户
        //如果不是管理员，只允许更新当前用户
        if (!isAdmin(loginUser) && userId != loginUser.getId()) {
            throw new BusinessException(ErrorCode.NO_AUTH);
        }
        User oldUser = this.getById(userId);
        if (oldUser == null) {
            throw new BusinessException(ErrorCode.NULL_ERROR);
        }
        if (StringUtils.isNotBlank(userDTO.getUsername())) {
            oldUser.setUsername(userDTO.getUsername());
        }
        if (StringUtils.isNotBlank(userDTO.getEmail())) {
            oldUser.setEmail(userDTO.getEmail());
        }
        if (userDTO.getGender() != null) {
            oldUser.setGender(userDTO.getGender());
        }
        if (StringUtils.isNotBlank(userDTO.getPhone())) {
            oldUser.setPhone(userDTO.getPhone());
        }
        if (StringUtils.isNotBlank(userDTO.getAvatarUrl())) {
            oldUser.setAvatarUrl(userDTO.getAvatarUrl());
        }
        if (StringUtils.isNotBlank(userDTO.getAvatarUrl())) {
            oldUser.setAvatarUrl(userDTO.getAvatarUrl());
        }
        if (StringUtils.isNotBlank(userDTO.getProfile())) {
            oldUser.setProfile(userDTO.getProfile());
        }

        int result = userMapper.updateById(oldUser);
        // 删除缓存
        String uuid = userDTO.getUuid();
        String currentUserAccount = userDTO.getCurrentUserAccount();
        String matchKey = USER_MATCH_KEY + loginUser.getId();
        String recommendKey = USER_SEARCH_KEY + loginUser.getId();
        CurrentUserRequest currentUserRequest = new CurrentUserRequest();
        currentUserRequest.setUserAccount(currentUserAccount);
        currentUserRequest.setUuid(uuid);
        try {
            refreshCache(currentUserRequest);
            if (redisTemplate.hasKey(matchKey)) {
                redisTemplate.delete(matchKey);
            }
            if (redisTemplate.hasKey(recommendKey)) {
                redisTemplate.delete(recommendKey);
            }

        } catch (Exception e) {
            log.error("redis error");
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
        return result;
    }



//    @Override
//    public User getLoginUser(HttpServletRequest request) {
//        if (request == null) {
//            return null;
//        }
//        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
//        if (userObj == null) {
//            throw new BusinessException(ErrorCode.NO_AUTH);
//        }
//        return (User) userObj;
//    }

    @Override
    public User getLoginUser(String userAccount, String uuid) {
        // 从Redis中查询用户是否存在
        User cashUser = (User) redisTemplate.opsForHash().get(TOKEN_KEY + uuid, userAccount);
        if (cashUser == null) {
            throw new BusinessException(ErrorCode.NO_AUTH);
        }
        return cashUser;
    }

    /**
     * 是否为管理员
     *
     * @param request
     * @return
     */
    @Override
    public boolean isAdmin(HttpServletRequest request) {
        // 仅管理员可查询
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User user = (User) userObj;
        return user != null && user.getUserRole() == UserConstant.ADMIN_ROLE;
    }

    /**
     * 是否为管理员
     *
     * @param loginUser
     * @return
     */
    @Override
    public boolean isAdmin(User loginUser) {
        return loginUser != null && loginUser.getUserRole() == UserConstant.ADMIN_ROLE;
    }

    @Override
    public List<User> matchUsers(long num, User loginUser) {

        // 有缓存读缓存
        String key = USER_MATCH_KEY + loginUser.getId();
        List<User> cacheUserList = (List<User>) redisTemplate.opsForValue().get(key);
        if (com.baomidou.mybatisplus.core.toolkit.CollectionUtils.isNotEmpty(cacheUserList)) {
            return cacheUserList;
        }


        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("id", "tags");
        queryWrapper.isNotNull("tags");
        // 其他用户标签
        List<User> userList = this.list(queryWrapper);

        String tags = loginUser.getTags();
        Gson gson = new Gson();
        // 登录用户标签
        List<String> tagList = gson.fromJson(tags, new TypeToken<List<String>>() {
        }.getType());

        Queue<Pair<User, Long>> priorityQueue = new PriorityQueue<>(
                Comparator.comparing(Pair::getValue)
        );

        for (User user : userList) {
            String userTags = user.getTags();
            // 无标签或者为当前用户自己，直接跳过
            if (StringUtils.isBlank(userTags) || user.getId() == loginUser.getId()) {
                continue;
            }
            List<String> userTagList = gson.fromJson(userTags, new TypeToken<List<String>>() {}.getType());
            long distance = AlgorithmUtils.minDistance(tagList, userTagList);
            // 如果队列未满或者当前用户的相似度更优，则入队并可能移除队尾元素以保持队列大小
            if (priorityQueue.size() < num || distance < priorityQueue.peek().getValue()) {
                if (priorityQueue.size() == num) {
                    priorityQueue.poll(); // 移除当前队列中相似度最大的用户
                }
                priorityQueue.offer(new Pair<>(user, distance));
            }
        }

        // 从优先队列中提取结果，直接获得topN的用户ID列表，无需再进行排序
        List<Long> userIdList = new ArrayList<>(priorityQueue.size());
        for (Pair<User, Long> pair : priorityQueue) {
            userIdList.add(pair.getKey().getId());
        }


        // 使用userIdList查询并转换为最终的User列表
        QueryWrapper<User> userQueryWrapper = new QueryWrapper<>();
        userQueryWrapper.in("id", userIdList);
        Map<Long, List<User>> userIdUserListMap = this.list(userQueryWrapper)
                .stream()
                .map(user -> getSafetyUser(user))
                .collect(Collectors.groupingBy(User::getId));
        List<User> finalUserList = new ArrayList<>();
        for (Long userId : userIdList) {
            finalUserList.add(userIdUserListMap.get(userId).get(0));
        }
        //写缓存
        try {
            redisTemplate.opsForValue().set(key, finalUserList, 5, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("redis set key error");
        }
        return finalUserList;
    }

    @Override
    public BaseResponse<Boolean> sendEmail(UserSendMessage userSendMessage) {
        String email = userSendMessage.getUserEmail();
        String key = SEND_MESSAGE_KEY + email;
        if (StringUtils.isEmpty(email)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "email为空");
        }
        String code = generateValidateCode(6).toString();
        RLock lock = redissonClient.getLock(MESSAGE_KEY + email);
        try {
            if (lock.tryLock(0, -1, TimeUnit.MILLISECONDS)) {
                if (redisTemplate.hasKey(key)) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "验证码已发送，请勿重新再试!");
                }
                try {
                    EmailUtils.sendEmail(email, code);
                } catch (MessagingException e) {
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR, "邮件发送失败");
                }
                userSendMessage.setCode(code);
                ValueOperations<String, Object> valueOperations = redisTemplate.opsForValue();
                try {
                    valueOperations.set(key, userSendMessage, 300000, TimeUnit.MILLISECONDS);
                    UserSendMessage sendMessage = (UserSendMessage) valueOperations.get(key);
                    log.info(sendMessage.toString());
                    return ResultUtils.success(true);
                } catch (Exception e) {
                    log.error("redis set key error", e);
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR, "缓存失败!");
                }
            }
        }  catch (InterruptedException e) {
            log.error("redis set key error");
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                System.out.println("unLock"+Thread.currentThread().getId());
                lock.unlock();
            }
        }
        return ResultUtils.success(false);
    }

    @Override
    public BaseResponse<Boolean> updatePassword(UserForgetRequest userForgetRequest) {
        String email = userForgetRequest.getUserEmail();
        String userPassword = userForgetRequest.getUserPassword();
        String code = userForgetRequest.getCode();
        String userAccount = userForgetRequest.getUserAccount();
        // 1. 校验
        if ((!Optional.ofNullable(email).isPresent()) || (!Optional.ofNullable(userPassword).isPresent())
                || (!Optional.ofNullable(code).isPresent()) || (!Optional.ofNullable(userAccount).isPresent())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }

        if (userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短，应不少于8位!");
        }
        String redisKey = String.format(SEND_MESSAGE_KEY + email);
        ValueOperations<String, Object> valueOperations = redisTemplate.opsForValue();
        log.info(redisKey);
        UserSendMessage sendMessage = (UserSendMessage) valueOperations.get(redisKey);
        if (!Optional.ofNullable(sendMessage).isPresent()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "获取验证码失败!");
        }
        String sendMessageCode = sendMessage.getCode();
        log.info(sendMessageCode);
        if (!code.equals(sendMessageCode)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "验证码不匹配!");
        }
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("email", email);
        queryWrapper.eq("userAccount", userAccount);
        User user = userMapper.selectOne(queryWrapper);
        if (!Optional.ofNullable(user).isPresent()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "该用户不存在!");
        }
        // 2. 加密
        String encryptPassword = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
        user.setUserPassword(encryptPassword);
        int role = userMapper.updateById(user);
        if (role > 0) {
            return ResultUtils.success(true);
        } else {
            return ResultUtils.success(false);
        }

    }

    @Override
    public TagVo getTags(String oldTags, HttpServletRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 已有标签
        Gson gson = new Gson();
        List<String> oldTagList = gson.fromJson(oldTags, new TypeToken<List<String>>() {
        }.getType());
        // 生成热门标签
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.isNotNull("tags");
        List<User> users = this.list(queryWrapper);
        Map<String, Integer> map = new HashMap<>();
        for (User user : users) {
            String Tags = user.getTags();
            List<String> list = Arrays.asList(Tags);
            if (list == null) {
                continue;
            }
            List<String> tagList = gson.fromJson(Tags, new TypeToken<List<String>>() {
            }.getType());
            if (tagList != null) {
                for (String tag : tagList) {
                    if (map.get(tag) == null) {
                        map.put(tag, 1);
                    } else {
                        map.put(tag, map.get(tag) + 1);
                    }
                }
            }
        }
        Map<Integer, List<String>> Map = new TreeMap<>(new Comparator<Integer>() {
            @Override
            public int compare(Integer key1, Integer key2) {
                //降序排序
                return key2.compareTo(key1);
            }
        });
        map.forEach((value, key) -> {
            if (Map.size() == 0) {
                Map.put(key, Arrays.asList(value));
            } else if (Map.get(key) == null) {
                Map.put(key, Arrays.asList(value));
            } else {
                List<String> list = new ArrayList(Map.get(key));
                list.add(value);
                Map.put(key, list);
            }
        });
        Set<String> set = new HashSet<>();
        for (Map.Entry<Integer, List<String>> entry : Map.entrySet()) {

            log.info("set.size():"+set.size());
            List<String> value = entry.getValue();
            if (oldTags == null) {
                for (String tag : value) {
                    set.add(tag);
                    if (set.size() >= 20) {
                        break;
                    }
                }
            }
            for (String tag : value) {
                if (!oldTags.contains(tag)) {
                    set.add(tag);
                    if (set.size() >= 20) {
                        break;
                    }
                }
            }
        }

        List<String> RecommendTags = new ArrayList<String>(set);
        TagVo tagVo = new TagVo();
        tagVo.setOldTags(oldTagList);
        tagVo.setRecommendTags(RecommendTags);
        return tagVo;
    }

    /**
     * 添加好友
     * @param addFriendRequest
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean addFriend(AddFriendRequest addFriendRequest) {
        if (addFriendRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        RLock lock = redissonClient.getLock(ADD_FRIEND_KEY);
        try {
            //只有一个线程会获取锁
            //判断发送人id和接收人id是否存在
            User sender = this.getById(addFriendRequest.getSenderId());
            User recipient = this.getById(addFriendRequest.getRecipientId());
            if (sender == recipient){
                throw  new BusinessException(ErrorCode.PARAMS_ERROR);
            }
            if (sender == null || recipient == null) {
                throw  new BusinessException(ErrorCode.PARAMS_ERROR,"发件人或收件人不存在");
            }
            Long recipientId = addFriendRequest.getRecipientId();
            Long senderId = addFriendRequest.getSenderId();
            //校验是否已经是好友
            Boolean alreadyFriends = checkIfAlreadyFriends(sender, recipientId);
            Boolean alreadyFriends1 = checkIfAlreadyFriends(recipient, senderId);
            if (alreadyFriends && alreadyFriends1) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "对方已经是您的好友，请勿重新发送");
            }

            //判断是否已经发送好友申请，如果状态为添加失败则可以继续添加
            QueryWrapper<Notice> queryWrapper = new QueryWrapper<>();
            queryWrapper.lambda().eq(Notice::getSenderId, senderId).eq(Notice::getRecipientId,
                    recipientId);
            List<Notice> list = noticeService.list(queryWrapper);
            for (Notice notice : list) {
                //正在发送好友申请
                if (notice.getAddFriendStatus().equals(AddFriendStatusEnum.ADDING.getValue())) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "正在发送好友申请，请勿重新发送!");
                }

                //添加成功，对方是你的好友
                if (notice.getAddFriendStatus().equals(AddFriendStatusEnum.ADD_SUCCESS.getValue())) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "对方已经是您的好友，请勿重新添加!");
                }
            }

            // 保存到消息通知表中
            Notice notice = new Notice();
            notice.setSenderId(senderId);
            notice.setRecipientId(recipientId);
            notice.setAddFriendStatus(AddFriendStatusEnum.ADDING.getValue());
            boolean save = noticeService.save(notice);
            if (!save) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "保存消息通知表失败!");
            }

            // 修改被添加人的被添加次数
            User user = this.getById(recipientId);
            if (user.getAddCount() != null){
                user.setAddCount(user.getAddCount() + 1);
            }else {
                user.setAddCount(1);
            }
            this.updateById(user);
            return true;
        } finally {

            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteFriend(DeleteFriendRequest deleteFriendRequest) {
        Long senderId = deleteFriendRequest.getId();
        Long recipientId = deleteFriendRequest.getDeleteId();
        User sender = this.getById(senderId);
        User recipient = this.getById(recipientId);
        if (sender == null || recipient == null) {
            throw  new BusinessException(ErrorCode.PARAMS_ERROR,"发件人或收件人不存在");
        }

        //删除好友，修改消息通知表的好友状态为添加失败
        QueryWrapper<Notice> queryWrapper = new QueryWrapper<>();
        //  eq1: 表中的接受为senderId//当前用户   并且 表中发送者为 recipientId
        // 第一次查询
        queryWrapper.lambda().eq(Notice::getRecipientId, senderId).eq(Notice::getSenderId, recipientId).eq(Notice::getAddFriendStatus, AddFriendStatusEnum.ADD_SUCCESS.getValue());
        Notice notice = noticeService.getOne(queryWrapper);

        // 第二次
        if (notice==null){
            QueryWrapper<Notice> queryWrapper2 = new QueryWrapper<>();
            queryWrapper2.lambda().eq(Notice::getSenderId, senderId).eq(Notice::getRecipientId, recipientId).eq(Notice::getAddFriendStatus, AddFriendStatusEnum.ADD_SUCCESS.getValue());
            notice = noticeService.getOne(queryWrapper2);
        }

        if (notice==null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"消息通知表中不存在该条记录");
        }
        // 设置状态为添加失败
        notice.setAddFriendStatus(AddFriendStatusEnum.ADD_ERROR.getValue());
        noticeService.updateById(notice);

        //双方好友列表id都删除对方
        removeFriendFromList(sender, recipientId);
        removeFriendFromList(recipient, senderId);

        return true;

    }

    /**
     * 同意添加好友
     * @param addFriendRequest
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean agreeFriend(AddFriendRequest addFriendRequest) {
        Long senderId = addFriendRequest.getSenderId();
        Long recipientId = addFriendRequest.getRecipientId();
        // 校验发件人和收件人是否存在
        validateUsersExist(senderId, recipientId);
        User sender = this.getById(senderId);
        User recipient = this.getById(recipientId);

        // 校验是否已经是好友
        Boolean alreadyFriends = checkIfAlreadyFriends(sender, recipientId);
        Boolean alreadyFriends1 = checkIfAlreadyFriends(recipient, senderId);
        if (alreadyFriends && alreadyFriends1) {
            return true;
        }
        // 修改消息通知表
        processFriendRequest(addFriendRequest, AddFriendStatusEnum.ADD_SUCCESS.getValue());
        // 在好友列表互相添加id
        addFriendToLists(addFriendRequest.getSenderId(), addFriendRequest.getRecipientId());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean rejectFriend(AddFriendRequest addFriendRequest) {
        // 校验发件人和收件人是否存在
        validateUsersExist(addFriendRequest.getSenderId(), addFriendRequest.getRecipientId());
        // 修改消息通知表
        processFriendRequest(addFriendRequest, AddFriendStatusEnum.ADD_ERROR.getValue());
        return true;

    }

    private void validateUsersExist(Long senderId, Long recipientId) {
        User sender = this.getById(senderId);
        User recipient = this.getById(recipientId);
        if (sender == null || recipient == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "发件人或收件人不存在");
        }
    }

    // 提取公共方法处理通知
    private void processFriendRequest(AddFriendRequest request, int newStatus) {
        updateNoticeStatus(request.getSenderId(), request.getRecipientId(), newStatus);
        updateNoticeStatus(request.getRecipientId(), request.getSenderId(), newStatus);
    }

    // 更新通知表状态
    private void updateNoticeStatus(Long senderId, Long recipientId, int newStatus) {
        QueryWrapper<Notice> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda()
                .eq(Notice::getSenderId, senderId)
                .eq(Notice::getRecipientId, recipientId)
                .eq(Notice::getAddFriendStatus, AddFriendStatusEnum.ADDING.getValue());
        Notice notice = noticeService.getOne(queryWrapper);
        if (notice != null) {
            notice.setAddFriendStatus(newStatus);
            if (!noticeService.updateById(notice)) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "更新失败");
            }
        }
    }

    // 添加好友到双方的好友列表
    private void addFriendToLists(Long senderId, Long recipientId) {
        addFriendList(this.getById(senderId), recipientId);
        addFriendList(this.getById(recipientId), senderId);
    }
    @Override
    public List<User> listFriend(User loginUser) {
        // 最新数据
        User user = this.getById(loginUser.getId());
        String friendId = user.getFriendId();

        ArrayList<User> userArrayList = new ArrayList<>();
        if (StrUtil.isBlank(friendId)) {
            return userArrayList;
        }

        JSONArray jsonArray = JSONUtil.parseArray(friendId);
        for (Object id : jsonArray) {
            userArrayList.add(this.getById((Serializable) id));
        }

        return userArrayList;

    }

    /**
     * 刷新缓存
     * @param currentUserRequest
     * @return
     */
    @Override
    public boolean refreshCache(CurrentUserRequest currentUserRequest) {
        String userAccount = currentUserRequest.getUserAccount();
        String uuid = currentUserRequest.getUuid();
        User cashUser = (User) redisTemplate.opsForHash().get(TOKEN_KEY + uuid, userAccount);
        // 先删除缓存
        try {
            redisTemplate.opsForHash().delete(TOKEN_KEY + uuid, userAccount);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "删除缓存失败");
        }
        User user = this.getById(cashUser.getId());
        User safetyUser = this.getSafetyUser(user);

        redisTemplate.opsForHash().put(TOKEN_KEY + uuid, userAccount, safetyUser);
        redisTemplate.expire(TOKEN_KEY + uuid, 10, TimeUnit.MINUTES);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateTags(UserDTO userDTO, User loginUser) {
        long userId = userDTO.getId();
        User oldUser = this.getById(userId);
        if (StringUtils.isNotBlank(userDTO.getTags())) {
            oldUser.setTags(userDTO.getTags());
        }
        int result = userMapper.updateById(oldUser);
        // 删除缓存
        String matchKey = USER_MATCH_KEY + loginUser.getId();
        String recommendKey = USER_SEARCH_KEY + loginUser.getId();
        CurrentUserRequest currentUserRequest = new CurrentUserRequest();
        currentUserRequest.setUserAccount(userDTO.getCurrentUserAccount());
        currentUserRequest.setUuid(userDTO.getUuid());
        try {
            refreshCache(currentUserRequest);
            if (redisTemplate.hasKey(matchKey)) {
                redisTemplate.delete(matchKey);
            }
            if (redisTemplate.hasKey(recommendKey)) {
                redisTemplate.delete(recommendKey);
            }

        } catch (Exception e) {
            log.error("redis delete key error:" + e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
        return result;
    }

    /**
     * 根据标签搜索用户（SQL 查询版）
     *
     * @param tagNameList 用户要拥有的标签
     * @return
     */
    @Deprecated
    private List<User> searchUsersByTagsBySQL(List<String> tagNameList) {
        if (CollectionUtils.isEmpty(tagNameList)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        // 拼接 and 查询
        // like '%Java%' and like '%Python%'
        for (String tagName : tagNameList) {
            queryWrapper = queryWrapper.like("tags", tagName);
        }
        List<User> userList = userMapper.selectList(queryWrapper);
        return userList.stream().map(this::getSafetyUser).collect(Collectors.toList());
    }


    /**
     * 校验是否是好友
     *
     * @param user
     * @param friendId
     */
    private Boolean checkIfAlreadyFriends(User user, Long friendId) {
        if (user.getId() == friendId){
            throw  new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String userFriendId = user.getFriendId();
        if (StrUtil.isNotBlank(userFriendId)) {
            JSONArray jsonArray = JSONUtil.parseArray(userFriendId);
            // 将friendId转为Integer类型进行比较
            return jsonArray.contains(Math.toIntExact(friendId));
        }
        return false;
    }


    /**
     * 删除好友
     *
     * @param user
     * @param friendId
     */
    private void removeFriendFromList(User user, Long friendId) {
        if (user.getId() == friendId){
            throw  new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        if (StrUtil.isNotBlank(user.getFriendId())) {
            JSONArray friendIdJsonArray = JSONUtil.parseArray(user.getFriendId());
            JSONArray newFriendIdJsonArray = new JSONArray();
            for (Object id : friendIdJsonArray) {
                if (!id.equals(Math.toIntExact(friendId))) {
                    newFriendIdJsonArray.add(id);
                }
            }
            user.setFriendId(JSONUtil.toJsonStr(newFriendIdJsonArray));
            boolean updateResult = this.updateById(user);
            if (!updateResult) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "删除好友ID失败");
            }
        }
    }


    /**
     * 添加好友到好友列表
     *
     * @param user
     * @param friendId
     */
    private void addFriendList(User user, Long friendId) {
        JSONArray friendIdJsonArray = StrUtil.isBlank(user.getFriendId()) ? new JSONArray() : JSONUtil.parseArray(user.getFriendId());
        if (user.getId() == friendId){
            throw  new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        friendIdJsonArray.add(friendId);
        user.setFriendId(JSONUtil.toJsonStr(friendIdJsonArray));
        boolean updateResult = this.updateById(user);
        if (!updateResult) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "更新好友列表失败");
        }
    }

}




