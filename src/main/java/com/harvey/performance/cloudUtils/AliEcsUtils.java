package com.harvey.performance.cloudUtils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.aliyuncs.CommonRequest;
import com.aliyuncs.CommonResponse;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.profile.DefaultProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author shu
 */
public class AliEcsUtils {

    /**
     * 日志logger
     */
    private static final Logger LOG = LoggerFactory.getLogger(AliEcsUtils.class);

    /**
     * 通过sdk调用云服务API的用户标识
     */
    private String accessKeyId = "YOUR_ACCESS_KEY_ID";

    /**
     * 通过sdk调用云服务API的用户密钥
     */
    private String accessKeySecret = "YOUR_ACCESS_KEY_SECRET";

    /**
     * ECS实例地域ID
     */
    private String regionId;

    /**
     * ECS实例启动模板ID
     */
    private String launchTemplateId;

    /**
     * 单次创建实例数上限
     */
    private static final int INSTANCES_COUNT_LIMIT = 50;

    /**
     * 通过sdk调用云服务API的域名
     */
    private static final String DOMAIN = "ecs.aliyuncs.com";

    /**
     * 版本格式
     */
    private static final DateFormat VERSION_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    /**
     * 日期格式
     */
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

    /**
     * 当前版本（以日期作为版本标识）
     */
    private static final String VERSION = "2014-05-26";

    /**
     * 通过sdk调用云服务API的client
     */
    private IAcsClient client = null;

    /**
     * @param regionId         地域ID
     * @param launchTemplateId 实例启动模板ID
     */
    public AliEcsUtils(String regionId, String launchTemplateId) {
        this.regionId = regionId;
        this.launchTemplateId = launchTemplateId;
    }

    /**
     * @param accessKeyId      用户标识
     * @param accessKeySecret  用户秘钥
     * @param regionId         地域ID
     * @param launchTemplateId 实例启动模板ID
     */
    public AliEcsUtils(String accessKeyId, String accessKeySecret, String regionId, String launchTemplateId) {
        this.accessKeyId = accessKeyId;
        this.accessKeySecret = accessKeySecret;
        this.regionId = regionId;
        this.launchTemplateId = launchTemplateId;
    }

    /**
     * 初始化调用云服务API的client
     */
    private void initClient() {
        DefaultProfile profile = DefaultProfile.getProfile(regionId, accessKeyId, accessKeySecret);
        this.client = new DefaultAcsClient(profile);
    }

    /**
     * 创建一台或多台按量付费定时释放的ECS实例。
     *
     * @param count      实例数量
     * @param expireHour 过期时间
     * @return 由一个或多个实例ID组成一个JSON数组
     */
    public String runInstances(int count, int expireHour) {
        if (count > INSTANCES_COUNT_LIMIT) {
            LOG.warn("实例数量超过允许上限：{}", count);
            return null;
        }
        if (client == null) {
            initClient();
        }
        CommonRequest request = new CommonRequest();
        request.setMethod(MethodType.POST);
        request.setDomain(DOMAIN);
        request.setVersion(VERSION);
        request.setAction("RunInstances");
        request.putQueryParameter("Tag.1.Key", "type");
        request.putQueryParameter("Tag.1.Value", "jmeter");
        request.putQueryParameter("RegionId", regionId);
        request.putQueryParameter("LaunchTemplateId", launchTemplateId);
        request.putQueryParameter("HostName", "quautotest");
        request.putQueryParameter("UniqueSuffix", "true");
        request.putQueryParameter("InstanceName", "quautotest_");
        request.putQueryParameter("PasswordInherit", "true");
        request.putQueryParameter("Amount", count + "");
        request.putQueryParameter("DeletionProtection", "false");
        request.putQueryParameter("Description", "测试压测机器");
        request.putQueryParameter("AutoReleaseTime", getAutoReleaseTime(expireHour));
        CommonResponse response;
        try {
            response = client.getCommonResponse(request);
        } catch (ClientException e) {
            LOG.error("创建实例失败，异常如下：", e);
            return null;
        }
        JSONObject jsonObject = JSON.parseObject(response.getData());
        return jsonObject.getJSONObject("InstanceIdSets").getJSONArray("InstanceIdSet").toJSONString();
    }

    /**
     * 查询一台或多台ECS实例的详细信息
     *
     * @param idList 实例id list
     * @return false：有实例没有处于running状态；true：所有实例已处于running状态
     */
    public boolean getInstancesStatusById(String idList, List<String> ipAddress, int size) {
        if (client == null) {
            initClient();
        }
        ipAddress.clear();
        CommonRequest request = new CommonRequest();
        request.setMethod(MethodType.POST);
        request.setDomain(DOMAIN);
        request.setVersion(VERSION);
        request.setAction("DescribeInstances");
        request.putQueryParameter("RegionId", regionId);
        request.putQueryParameter("PageNumber", 1 + "");
        request.putQueryParameter("PageSize", size + "");
        request.putQueryParameter("InstanceIds", idList);
        CommonResponse response;
        try {
            response = client.getCommonResponse(request);
        } catch (ClientException e) {
            LOG.error("查询实例失败，异常如下：", e);
            return false;
        }
        JSONObject jsonObject = JSON.parseObject(response.getData());
        JSONArray jsonArray = jsonObject.getJSONObject("Instances").getJSONArray("Instance");
        for (int i = 0; i < jsonArray.size(); i++) {
            jsonObject = jsonArray.getJSONObject(i);
            String primaryIpAddress = jsonObject.getJSONObject("NetworkInterfaces").getJSONArray("NetworkInterface").getJSONObject(0).getString("PrimaryIpAddress");
            String status = jsonObject.getString("Status");
            LOG.info("ip:" + primaryIpAddress + " 当前状态为：" + jsonObject.getString("Status"));
            if ("Running".equals(status)) {
                ipAddress.add(primaryIpAddress);
            } else {
                return false;
            }
        }
        return true;
    }

    /**
     * @param idList 实例id列表
     */
    public void deleteInstances(String idList) {
        if (client == null) {
            initClient();
        }
        JSONArray jsonArray = JSONArray.parseArray(idList);
        CommonRequest request = new CommonRequest();
        request.setMethod(MethodType.POST);
        request.setDomain(DOMAIN);
        request.setVersion(VERSION);
        request.setAction("DeleteInstance");
        request.putQueryParameter("RegionId", regionId);
        request.putQueryParameter("Force", "true");
        for (Object aJsonArray : jsonArray) {
            request.putQueryParameter("InstanceId", String.valueOf(aJsonArray));
            try {
                client.getCommonResponse(request);
            } catch (ClientException e) {
                LOG.error("释放实例失败，异常如下：", e);
            }
        }
    }

    /**
     * @return 时间版本信息
     */
    private static String getNewVersion() {
        // 1、取得本地时间： 　　
        Calendar cal = Calendar.getInstance();
        // 2、取得时间偏移量： 　　
        int zoneOffset = cal.get(Calendar.ZONE_OFFSET);
        // 3、取得夏令时差： 　
        int dstOffset = cal.get(Calendar.DST_OFFSET);
        // 4、从本地时间里扣除这些差量，即可以取得UTC时间： 　　
        cal.add(Calendar.MILLISECOND, -(zoneOffset + dstOffset));
        return VERSION_FORMAT.format(cal.getTime());
    }

    /**
     * 根据当前时间，返回delayhour小时后的UTC时间
     *
     * @param expireHour 过期时间
     * @return 时间字符串
     */
    private static String getAutoReleaseTime(int expireHour) {
        // 1、取得本地时间： 　　
        Calendar cal = Calendar.getInstance();
        // 2、取得时间偏移量： 　　
        int zoneOffset = cal.get(Calendar.ZONE_OFFSET);
        // 3、取得夏令时差： 　
        int dstOffset = cal.get(Calendar.DST_OFFSET);
        // 4、从本地时间里扣除这些差量，即可以取得UTC时间： 　　
        cal.add(Calendar.MILLISECOND, -(zoneOffset + dstOffset));
        cal.add(Calendar.HOUR, expireHour);
        return DATE_FORMAT.format(cal.getTime());
    }

    @Test
    void test1() {
        LOG.info(runInstances(2, 1));
    }

}

