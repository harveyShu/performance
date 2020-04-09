package com.harvey.performance.application.factory;

import com.harvey.performance.enums.CloudTypeEnum;
import com.harvey.performance.exception.PerformanceException;
import com.harvey.performance.application.interfaces.BaseCloudService;
import com.harvey.performance.application.service.AliyunCloudServiceImpl;

/**
 * @author shu
 */
public class CloudFactory {

    public static BaseCloudService createCloudService(CloudTypeEnum cloudTypeEnum, String regionName){
        if (null == cloudTypeEnum) {
            throw new PerformanceException("cloudTypeEnum不能为空");
        }
        switch (cloudTypeEnum) {
            case aliyun:
                return new AliyunCloudServiceImpl(regionName);
            default:
                throw new PerformanceException("cloudType不存在");
        }
    }

}
