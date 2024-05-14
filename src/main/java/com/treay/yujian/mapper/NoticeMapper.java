package com.treay.yujian.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.treay.yujian.model.domain.Notice;
import org.apache.ibatis.annotations.Param;

/**
* @author 16799
* @description 针对表【notice(通知表)】的数据库操作Mapper
* @createDate 2024-05-02 11:36:38
* @Entity generator.domain.Notice
*/
public interface NoticeMapper extends BaseMapper<Notice> {

    Notice getBySenderIdAndRecipientId(@Param("senderId") Long senderId, @Param("recipientId") Long recipientId);
}




