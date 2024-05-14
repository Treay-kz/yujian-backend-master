package com.treay.yujian.job;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.treay.yujian.mapper.UserMapper;
import com.treay.yujian.model.domain.User;
import com.treay.yujian.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.treay.yujian.constant.RedisConstant.USER_SEARCH_KEY;

/**
 * 缓存预热任务
 *
 * @author Treay
 * 
 */
@Component
@Slf4j
public class PreCacheJob {

    @Resource
    private UserService userService;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private UserMapper userMapper;


    // 重点用户
    private List<Long> mainUserList = Arrays.asList(1L);

    // 每天执行，预热推荐用户
    @Scheduled(cron = "0 0 0 * * *")
    public void doCacheRecommendUser() {
        RLock lock = redissonClient.getLock("yujian:precachejob:docache:lock");
        try {
            //只有一个线程会获取锁
            if (lock.tryLock(0, -1, TimeUnit.MILLISECONDS)) {
                List<Long> mainUserList = new ArrayList<>();
                //为所有用户进行用户推荐
                List<User> list = userService.list();
                for (User user : list) {
                    mainUserList.add(user.getId());
                }
                for (Long userId : mainUserList) {
                    //没有直接查询数据库
                    List<User> userList = userMapper.searchAddCount();
                    // 执行任务逻辑
                    String key = USER_SEARCH_KEY + userId;
                    //写缓存
                    try {
                        redisTemplate.opsForValue().set(key, userList, 24, TimeUnit.HOURS);
                    } catch (Exception e) {
                        log.error("redis set key error");
                    }
                }
            }

        } catch (InterruptedException e) {
            log.error("redis set key error");
        } finally {
            //只能释放自己的锁
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }




//        try {
//            // 只有一个线程能获取到锁
//            if (lock.tryLock(0, -1, TimeUnit.MILLISECONDS)) {
//                for (Long userId : mainUserList) {
//                    QueryWrapper<User> queryWrapper = new QueryWrapper<>();
//                    Page<User> userPage = userService.page(new Page<>(1, 20), queryWrapper);
//                    String redisKey = String.format("yujian:user:recommend:%s", userId);
//                    ValueOperations<String, Object> valueOperations = redisTemplate.opsForValue();
//                    // 写缓存
//                    try {
//                        valueOperations.set(redisKey, userPage, 30000, TimeUnit.MILLISECONDS);
//                    } catch (Exception e) {
//                        log.error("redis set key error", e);
//                    }
//                }
//            }
//        } catch (InterruptedException e) {
//            log.error("doCacheRecommendUser error", e);
//        } finally {
//            // 只能释放自己的锁
//            if (lock.isHeldByCurrentThread()) {
//                System.out.println("unLock: " + Thread.currentThread().getId());
//                lock.unlock();
//            }
//        }

    }

}
