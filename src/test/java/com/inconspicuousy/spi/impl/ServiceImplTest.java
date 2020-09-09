package com.inconspicuousy.spi.impl;

import com.inconspicuousy.spi.Service;
import com.inconspicuousy.spi.facroty.ServiceFactory;
import org.junit.Test;

/**
 * @author peng.yi
 */
public class ServiceImplTest {

    @Test
    public void test() {

        Service service = ServiceFactory.getService();
        System.out.println(service);
        if (service != null) {
            service.say();
        }


    }
}