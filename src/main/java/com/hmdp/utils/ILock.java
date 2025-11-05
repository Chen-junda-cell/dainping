package com.hmdp.utils;

public interface ILock {
    /**
     *
     * @param timeoutSec 设置过期时间
     * @return
     */
    boolean tryLock(long timeoutSec);

    /**
     * 释放锁
     */
    void unlock();
}
