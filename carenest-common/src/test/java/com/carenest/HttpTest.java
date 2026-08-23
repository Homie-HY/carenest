package com.carenest;

import cn.hutool.http.HttpUtil;
import com.carenest.common.utils.http.HttpUtils;
import org.junit.Test;

public class HttpTest {

    //发起get请求
    @Test
    public void testGet() throws Exception {
        String url = "https://www.baidu.com";
        String result = HttpUtil.get(url);
        System.out.println(result);
    }

}
