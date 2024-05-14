//package com.treay.yujian.consumer;
//
//import com.rabbitmq.client.Channel;
//
//import com.treay.yujian.config.MessageSendConfig;
//import com.treay.yujian.model.domain.Notice;
//import com.treay.yujian.model.enums.AddFriendStatusEnum;
//import com.treay.yujian.model.request.AddFriendRequest;
//import com.treay.yujian.service.NoticeService;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.amqp.rabbit.annotation.RabbitListener;
//import org.springframework.amqp.support.AmqpHeaders;
//import org.springframework.data.redis.core.RedisTemplate;
//import org.springframework.messaging.Message;
//import org.springframework.stereotype.Component;
//
//import javax.annotation.Resource;
//import java.io.IOException;
//import java.util.concurrent.TimeUnit;
//
//import static com.treay.yujian.constant.RedisConstant.ADD_FRIEND_KEY;
//
//
///**
// *
// * @author: Treay
// *
// **/
//@Component
//@Slf4j
//public class MessageConsumer {
//
//    @Resource
//    private NoticeService noticeService;
//
//    @Resource
//    private RedisTemplate redisTemplate;
//
//
//    @RabbitListener(queues = MessageSendConfig.ADD_FRIEND_SEND_QUEUE_NAME)
//    public void handleMessage(Message message, Channel channel) {
//        try {
//            AddFriendRequest addFriendRequest = (AddFriendRequest) message.getPayload();
//            Long senderId = addFriendRequest.getSenderId();
//            Long recipientId = addFriendRequest.getRecipientId();
//            log.info("addFriendRequest==========>{}", addFriendRequest);
//            Long deliverTag = (Long) message.getHeaders().get(AmqpHeaders.DELIVERY_TAG);
//            String keySuffix = String.valueOf(senderId + recipientId);
//            //处理幂等性
//            if (redisTemplate.opsForHash().hasKey(ADD_FRIEND_KEY + keySuffix, keySuffix)) {
//                //存在说明这个消息已经被处理了，手动ack
//                //直接丢掉消息
//                channel.basicNack(deliverTag, false, false);
//            } else {
//                //往消息表添加消息
//                log.info("往消息表添加消息：{}", addFriendRequest);
//                Notice notice = new Notice();
//                notice.setSenderId(senderId);
//                notice.setRecipientId(recipientId);
//                notice.setAddFriendStatus(AddFriendStatusEnum.ADDING.getValue());
//                noticeService.save(notice);
//                //将数据存入redis
//                redisTemplate.opsForHash().put(ADD_FRIEND_KEY + keySuffix, keySuffix, keySuffix);
//                redisTemplate.expire(ADD_FRIEND_KEY + keySuffix, 5, TimeUnit.MINUTES);
//                channel.basicAck(deliverTag, false);
//            }
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//
//    }
//
//
//}
