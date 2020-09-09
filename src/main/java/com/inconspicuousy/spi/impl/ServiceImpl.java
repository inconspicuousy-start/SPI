package com.inconspicuousy.spi.impl;

import com.inconspicuousy.spi.Service;

/**
 * Service的实现类
 * @author peng.yi
 */
public class ServiceImpl implements Service {
    public void say() {
        System.out.println("我实现了Service的say方法.");
    }
}
