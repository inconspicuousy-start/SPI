package com.inconspicuousy.spi.impl;

import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * @author peng.yi
 */
public class DriverTest {

    @Test
    public void test() throws SQLException {

        Connection root = DriverManager.getConnection("jdbc:mysql://172.16.13.93:3306", "root", "173UvBGjMOVMPlqe");
        System.out.println(root);


    }
}
