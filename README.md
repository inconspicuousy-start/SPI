## SPI源码解析

### 1. 什么是SPI

> SPI是`Service Provide Interface`的简称，是JDK内置的一种服务提供发现机制。简单来说， 就是用来发现某个接口或者规范的实现类。

### 2. 快速开始

#### 2.1 项目结构

![](https://raw.githubusercontent.com/inconspicuousy-start/image/master//1599635876(1).jpg)

#### 2.2 类图

![](https://raw.githubusercontent.com/inconspicuousy-start/image/master//Snipaste_2020-09-09_15-21-07.png)

#### 2.3 相关代码

- `Service`接口类

```java
package com.inconspicuousy.spi;

/**
 * 待实现的接口类
 * @author peng.yi
 */
public interface Service {

    void say();

}
```

- `ServiceImpl`接口实现类

```java
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
```

- `ServiceFactory`简单工厂类

```java
package com.inconspicuousy.spi.facroty;

import com.inconspicuousy.spi.Service;

import java.util.Optional;
import java.util.ServiceLoader;
import java.util.stream.StreamSupport;

/**
 *
 * 创建Service的工厂类
 * @author peng.yi
 */
public class ServiceFactory {

    public static Service getService() {
        ServiceLoader<Service> load = ServiceLoader.load(Service.class);
        // ServiceLoader 继承 Iterable, 调用spliterator方法结合StreamSupport.stream返回Stream
        // 最后在 MATE-INF.service下面找到对应的实现类集合
        // 返回任意一个实现类
        Optional<Service> service = StreamSupport.stream(load.spliterator(), false).findAny();
        return service.orElse(null);
    }
}

```

这里SPI技术的核心就是利用`ServiceLoad`类加载接口类时，会主动到对应的目录文件中找到对应的实现类并完成加载。

- `META-INF/services/com.inconspicuousy.spi.Service`

```
com.inconspicuousy.spi.impl.ServiceImpl
```

指定文件中填写接口的实现类的包名+类名，然后类加载器加载指定的实现类。

- `ServiceImplTest`测试类

```java
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
```

#### 2.4 直接测试结果

```shell
com.inconspicuousy.spi.impl.ServiceImpl@606d8acf
我实现了Service的say方法.

Process finished with exit code 0
```

### 3. SPI的核心实现逻辑

- `ServiceLoader继承Iterable接口重写了iterator方法`

```java
 public Iterator<S> iterator() {
        return new Iterator<S>() {

            Iterator<Map.Entry<String,S>> knownProviders
                = providers.entrySet().iterator();

            public boolean hasNext() {
                if (knownProviders.hasNext())
                    return true;
                // 核心逻辑就是调用 lookupIterator.hasNext()
                // lookupIterator是ServiceLoader内部类LazyIterator的对象
                return lookupIterator.hasNext();
            }

            public S next() {
                if (knownProviders.hasNext())
                    return knownProviders.next().getValue();
                return lookupIterator.next();
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }

        };
    }
```

- `lookupIterator.hasNext()`方法里面本质上调用的就是`LazyIterator#hasNextService`
- `lookupIterator.next()`方法里面本质上调用的就是`LazyIterator#nextService`

```java
        public boolean hasNext() {
            // 这里不管 acc是否为空 都是调用的 hasNextService方法
            if (acc == null) {
                return hasNextService();
            } else {
                PrivilegedAction<Boolean> action = new PrivilegedAction<Boolean>() {
                    public Boolean run() { return hasNextService(); }
                };
                return AccessController.doPrivileged(action, acc);
            }
        }

        public S next() {
             // 这里不管 acc是否为空 都是调用的 nextService 方法
            if (acc == null) {
                return nextService();
            } else {
                PrivilegedAction<S> action = new PrivilegedAction<S>() {
                    public S run() { return nextService(); }
                };
                return AccessController.doPrivileged(action, acc);
            }
        }
```

- `hasNextService`方法中解析`META-INF/services/接口全类名`文件, 并将里面的内容加载到String集合中

```java
        private boolean hasNextService() {
            if (nextName != null) {
                return true;
            }
            if (configs == null) {
                try {
                    // private static final String PREFIX = "META-INF/services/";
                    // service => 接口全类名
                    String fullName = PREFIX + service.getName();
                    // 利用类加载器加载该文件
                    if (loader == null)
                        configs = ClassLoader.getSystemResources(fullName);
                    else
                        configs = loader.getResources(fullName);
                } catch (IOException x) {
                    fail(service, "Error locating configuration files", x);
                }
            }
            // 非首次加载的话, 直接判断pending迭代器的是否拥有下一个元素
            while ((pending == null) || !pending.hasNext()) {
                if (!configs.hasMoreElements()) {
                    return false;
                }
                // Iterator<String> pending = null;
                // 这里首次将文件中的内容加载到String类型的集合中
                pending = parse(service, configs.nextElement());
            }
            // 将实现类的全类名赋值给nextName属性
            nextName = pending.next();
            return true;
        }
```

- `nextService`方法会将实现类加载到JVM中

```java
private S nextService() {
            if (!hasNextService())
                throw new NoSuchElementException();
            String cn = nextName;
            nextName = null;
            Class<?> c = null;
            try {
                // 根据实现类的全类名,将实现类加载到JVM中
                c = Class.forName(cn, false, loader);
            } catch (ClassNotFoundException x) {
                fail(service,
                     "Provider " + cn + " not found");
            }
            if (!service.isAssignableFrom(c)) {
                fail(service,
                     "Provider " + cn  + " not a subtype");
            }
            try {
                // 创建实例对象, 并将对象存储到 providers 集合中并将实例返回
                S p = service.cast(c.newInstance());
                providers.put(cn, p);
                return p;
            } catch (Throwable x) {
                fail(service,
                     "Provider " + cn + " could not be instantiated",
                     x);
            }
            throw new Error();          // This cannot happen
        }
```

#### 3.1 实现总结

> 本质上就是读取`META-INF/services`下的文件, 然后实现类的全类名并将其加载到JVM中创建对象并返回。

### 4. SPI的应用

#### 4.1 数据库驱动

JDK中只是定义了Driver的规范，但是没有具体的实现。 在项目中，一般我们根据数据库的不同（Mysql、Oracle）引入不同的数据库驱动（Driver的实现类）就可以获取到数据库连接对数据库进行操作。 

##### 4.1.1 源码分析

###### 4.1.1.1 前置准备

- 引入`mysql-connector-java`依赖

```xml
 <!-- https://mvnrepository.com/artifact/mysql/mysql-connector-java -->
        <!-- 引入Mysql连接包, mysql连接包中就定义了数据库驱动的Driver的实现类的信息 -->
        <dependency>
            <groupId>mysql</groupId>
            <artifactId>mysql-connector-java</artifactId>
            <version>8.0.21</version>
        </dependency>
```

- 编写`DriverTest`测试类

```java
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

        // 通过DriverManager获取数据库连接
        Connection root = DriverManager.getConnection("jdbc:mysql://localhost:3306", "root", "xxx");
        System.out.println(root);
    }
}
```

###### 4.1.1.2 分析

- 查看`mysql-connector-java`的jar包, 不难发现在jar包中包含`META-INF/services/java.sql.Driver`文件, 文件中描述的正是Driver的mysql实现类的全路径`com.mysql.cj.jdbc.Driver`。
- 在DriverManager中随着类加载的还有静态方法块,其中执行了` loadInitialDrivers()`

```java
 /**
     * Load the initial JDBC drivers by checking the System property
     * jdbc.properties and then use the {@code ServiceLoader} mechanism
     */
    static {
        loadInitialDrivers();
        println("JDBC DriverManager initialized");
    }
```

- 在`loadInitialDrivers()`中有一个核心逻辑就是利用SPI技术加载对应的`Driver`实现类

```java
ServiceLoader<Driver> loadedDrivers = ServiceLoader.load(Driver.class);
Iterator<Driver> driversIterator = loadedDrivers.iterator();
try{
    while(driversIterator.hasNext()) {
        // 这里就会自动加载mysql的驱动
        driversIterator.next();
    }
} catch(Throwable t) {
// Do nothing
}
return null;
```

- 在加载`com.mysql.cj.jdbc.Driver`Mysql驱动类时, 随着类加载会执行`DriverManager.registerDriver()`方法, 并且会创建驱动对象作为参数传递。

```java
 static {
        try {
            java.sql.DriverManager.registerDriver(new Driver());
        } catch (SQLException E) {
            throw new RuntimeException("Can't register driver!");
        }
    }
```

- `DriverManager.registerDriver`核心逻辑就是将数据库驱动对象保存到`java.sql.DriverManager#registeredDrivers`集合中

```java
 public static synchronized void registerDriver(java.sql.Driver driver,
            DriverAction da)
        throws SQLException {

        /* Register the driver if it has not already been added to our list */
        if(driver != null) {
            // registeredDrivers = new CopyOnWriteArrayList<>();
            registeredDrivers.addIfAbsent(new DriverInfo(driver, da));
        } else {
            // This is for compatibility with the original DriverManager
            throw new NullPointerException();
        }

        println("registerDriver: " + driver);

    }
```

- 在执行`getConnection`方法时，核心就是遍历`registeredDrivers`集合拿到对应的驱动之后获取数据库连接对象。

```java
 for(DriverInfo aDriver : registeredDrivers) {
            // If the caller does not have permission to load the driver then
            // skip it.
            if(isDriverAllowed(aDriver.driver, callerCL)) {
                try {
                    println("    trying " + aDriver.driver.getClass().getName());
                    // 获取到driver对象再获取连接, 获取成功后直接返回
                    Connection con = aDriver.driver.connect(url, info);
                    if (con != null) {
                        // Success!
                        println("getConnection returning " + aDriver.driver.getClass().getName());
                        return (con);
                    }
                } catch (SQLException ex) {
                    if (reason == null) {
                        reason = ex;
                    }
                }

            } else {
                println("    skipping: " + aDriver.getClass().getName());
            }

        }
```

