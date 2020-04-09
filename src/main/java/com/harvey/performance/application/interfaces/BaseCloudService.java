package com.harvey.performance.application.interfaces;

import com.aliyuncs.exceptions.ClientException;

import java.util.List;

/**
 * @author shu
 */
public interface BaseCloudService {

    /**
     * 创建一台或多台云平台ECS实例
     *
     * @param count      指定创建ECS实例的数量。（阿里云取值范围：1~100，）
     * @param expireHour 实例过期时间
     * @return 由一个或多个实例ID组成一个JSON数组
     */
    String runInstances(int count, int expireHour);

    /**
     * 查询一台或多台云平台ECS实例的IP地址
     *
     * @param idList 由一个或多个实例ID组成一个JSON数组。可使用runInstances的返回值。
     * @return 由实例的内网ip组成的list
     */
    List<String> ipAddressQuery(String idList);

}
