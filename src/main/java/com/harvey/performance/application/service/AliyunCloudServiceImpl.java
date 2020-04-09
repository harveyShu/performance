package com.harvey.performance.application.service;

import com.alibaba.fastjson.JSONArray;
import com.harvey.performance.application.interfaces.BaseCloudService;
import com.harvey.performance.cloudUtils.AliEcsUtils;
import com.harvey.performance.enums.RegionEnum;
import com.harvey.performance.exception.PerformanceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * @author harvey
 */
public class AliyunCloudServiceImpl implements BaseCloudService {

    /**
     *
     */
    private static final Logger LOG = LoggerFactory.getLogger(AliyunCloudServiceImpl.class);

    /**
     *
     */
    private AliEcsUtils aliEcsUtils = null;

    /**
     *
     */
    private String regionName;

    public AliyunCloudServiceImpl(String regionName) {
        if (null == RegionEnum.getRegionId(regionName)) {
            throw new PerformanceException("不支持的regionName");
        }
        this.regionName = regionName;
        initAliyunCloudService();
    }

    private void initAliyunCloudService() {
        this.aliEcsUtils = new AliEcsUtils(RegionEnum.getRegionId(regionName), RegionEnum.getLaunchTemplateId(regionName));
    }

    private void checkService() {
        if (null == regionName) {
            throw new PerformanceException("regionName不能为空");
        }
        if (null == aliEcsUtils) {
            throw new PerformanceException("aliEcsUtils不能为空");
        }
    }

    @Override
    public String runInstances(int count, int expireHour) {
        checkService();
        String idList;
        idList = aliEcsUtils.runInstances(count, expireHour);
        if (null == idList) {
            throw new PerformanceException("AliEcsUtils.runInstances()失败");
        }
        LOG.info("实例id：{}", idList);
        return idList;
    }

    @Override
    public List<String> ipAddressQuery(String idList) {
        checkService();
        LOG.info("等待实例就绪，将等待30s");
        try {
            Thread.sleep(1000 * 30);
        } catch (InterruptedException e) {
            LOG.error("调用Thread.sleep()失败", e);
        }
        int retryTime = 3 + idList.split(",").length;
        JSONArray jsonArray = JSONArray.parseArray(idList);
        List<String> ipAddress = new ArrayList<>();
        for (int i = 0; i < retryTime; i++) {
            if (aliEcsUtils.getInstancesStatusById(idList, ipAddress, jsonArray.size())) {
                return ipAddress;
            }
            LOG.info("部分实例没有处于running状态，需继续等待，10s后开始查询实例运行状态");
            try {
                Thread.sleep(1000 * 10);
            } catch (InterruptedException e) {
                LOG.error("调用Thread.sleep()失败", e);
            }
        }
        throw new PerformanceException("查询实例信息失败，已尝试查询{" + retryTime + "}次");
    }

}
