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
