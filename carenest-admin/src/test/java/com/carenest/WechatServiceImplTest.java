package com.carenest;

import com.carenest.nursing.service.WechatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class WechatServiceImplTest {

    @Autowired
    private WechatService wechatService;

    @Test
    public void testGetOpenid() {
        String openid = wechatService.getOpenid("0b31QdGa1Ab0RL0CiUIa18R4fe11QdG2");
        System.out.println(openid);//o3CsK6_C6f4WP9b0AxXNJOkc6q9Q
    }

    @Test
    public void testGetPhone() {
        String phone = wechatService.getPhone("ee656f26b952f67c2fd2f863d3b2e9fae3f402f3427c60b78a0b03700f27b7f5");
        System.out.println(phone);
    }
}