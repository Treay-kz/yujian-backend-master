package com.treay.yujian.utils;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.mail.MessagingException;



/**
 * @version 1.0
 * @auther Treay_kz
 */

class EmailUtilsTest {
    @Test
     void Test(){
        try {
            //使用其他QQ邮箱或者163邮箱接收信息
            EmailUtils.sendEmail("1652991065@qq.com","167992");
        } catch (MessagingException e) {
            e.printStackTrace();
        }

    }

}