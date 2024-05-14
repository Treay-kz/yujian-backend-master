package com.treay.yujian.mapper;

// [鱼皮的知识星球](https://t.zsxq.com/0emozsIJh) 从 0 到 1 求职指导，斩获 offer！1 对 1 简历优化服务、200+ 真实简历和建议参考、2000+ 求职面试经验分享、25w 字前后端精选面试题

import com.treay.yujian.model.domain.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.util.List;

/**
 * 用户 Mapper
 *
 * @author Treay
 * 
 */
public interface UserMapper extends BaseMapper<User> {

    List<User> searchAddCount();

}




