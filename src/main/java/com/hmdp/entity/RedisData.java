package com.hmdp.entity;


import lombok.AllArgsConstructor;

import java.time.LocalDateTime;


@AllArgsConstructor
public class RedisData {
    private Object data;
    private LocalDateTime expireTime;

}
