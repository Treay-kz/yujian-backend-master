package com.treay.yujian.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.treay.yujian.mapper.TagMapper;
import com.treay.yujian.model.domain.Tag;
import com.treay.yujian.service.TagService;
import org.springframework.stereotype.Service;

/**
* @author 16799
* @description 针对表【tag(标签表)】的数据库操作Service实现
* @createDate 2024-05-02 11:36:38
*/
@Service
public class TagServiceImpl extends ServiceImpl<TagMapper, Tag>
    implements TagService {

}




