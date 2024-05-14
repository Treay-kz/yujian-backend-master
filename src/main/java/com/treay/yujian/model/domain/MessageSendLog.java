package com.treay.yujian.model.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;

/**
 * 
 * @TableName message_send_log
 */
@TableName(value ="message_send_log")
@Data
public class MessageSendLog implements Serializable {
    /**
     * 消息id（uuid）
     */
    @TableId
    private String msgId;

    /**
     * 发送人id
     */
    private Long senderId;

    /**
     * 接收人id
     */
    private Long recipientId;

    /**
     * 添加好友状态
     */
    private Integer addFriendStatus;

    /**
     * 邀请人id
     */
    private Long inviterId;

    /**
     * 队列名字
     */
    private String routeKey;

    /**
     * 0-发送中 1-发送成功 2-发送失败
     */
    private Integer status;

    /**
     * 交换机名字
     */
    private String exchange;

    /**
     * 重试次数
     */
    private Integer tryCount;

    /**
     * 第一次重试时间
     */
    private Date tryTime;

    /**
     * 创建时间
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /**
     * 修改时间
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    /**
     * 是否删除
     */
    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}