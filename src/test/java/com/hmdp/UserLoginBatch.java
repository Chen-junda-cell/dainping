package com.hmdp;

import com.hmdp.entity.User;
import com.hmdp.service.IUserService;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;

import org.apache.http.util.EntityUtils;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.BufferedWriter;
import java.io.FileWriter;

import java.util.List;
@SpringBootTest
public class UserLoginBatch {

    @Autowired
    private IUserService userService;
    
    @Test
    public void function() {
        String loginUrl = "http://localhost:8081/user/login";
        String tokenFilePath = "tokens.txt";

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(tokenFilePath))) {

            HttpClient httpClient = HttpClients.createDefault();

            // 从数据库读取用户
            List<User> users = userService.list();

            for (User user : users) {
                String phoneNumber = user.getPhone();

                HttpPost httpPost = new HttpPost(loginUrl);

                // 必须添加 Content-Type！！！
                httpPost.setHeader("Content-Type", "application/json");

                // 构建 JSON
                JSONObject jsonRequest = new JSONObject();
                jsonRequest.put("phone", phoneNumber);
                jsonRequest.put("code", "123456");   // 必须！！！

                httpPost.setEntity(new StringEntity(
                        jsonRequest.toString(),
                        ContentType.APPLICATION_JSON
                ));

                HttpResponse response = httpClient.execute(httpPost);

                if (response.getStatusLine().getStatusCode() == 200) {
                    String responseString = EntityUtils.toString(response.getEntity());
                    System.out.println(responseString);

                    String token = parseTokenFromJson(responseString);
                    writer.write(token);
                    writer.newLine();

                    System.out.println("手机号 " + phoneNumber + " 登录成功，Token: " + token);
                } else {
                    System.out.println("手机号 " + phoneNumber + " 登录失败");
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    // 解析JSON响应获取token的方法，这里只是示例，具体实现需要根据实际响应格式进行解析
    private static String parseTokenFromJson(String json) {
        try {
            // 将JSON字符串转换为JSONObject
            JSONObject jsonObject = new JSONObject(json);
            // 从JSONObject中获取名为"token"的字段的值
            String token = jsonObject.getString("data");
            return token;
        } catch (Exception e) {
            e.printStackTrace();
            return null; // 解析失败，返回null或者抛出异常，具体根据实际需求处理
        }
    }
}
