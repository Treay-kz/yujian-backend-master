package com.treay.yujian.server;

import com.treay.yujian.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author: Treay
 *
 **/
@Component
@ServerEndpoint("/ws/{sid}")
@Slf4j
public class WebSocketServer {



    private UserService userService;

    private static final Map<String, Session> sessionMap = new HashMap<>();

    /**
     * 建立Web连接
     * @param session
     * @param sid
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("sid") String sid) {
        log.info("[onOpen]: sid = {}", sid);
        sessionMap.put(sid, session);
    }
    /**
     * 收到客户端消息后调用的方法
     *
     * @param message 客户端发送过来的消息
     */
    @OnMessage
    public void onMessage(String message, @PathParam("sid") String sid) {
        this.sendToAllClient(message);
        log.info("收到消息[onMessage]: sid = {}, message = {}", sid, message);
    }
    /**
     * 连接关闭调用的方法
     *
     * @param sid
     */
    @OnClose
    public void onClose(@PathParam("sid") String sid) {
        log.info("[onClose]: sid = {}", sid);
        sessionMap.remove(sid);
    }
    /**
     * 群发
     *
     * @param message
     */
    public void sendToAllClient(String message) {
        sessionMap.values().forEach(session -> {
            try {
                session.getBasicRemote().sendText(message);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
