package com.harvey.performance.enums;

/**
 * 阿里云实例regionId和launchTemplateId的枚举类
 *
 * @author harvey
 */
public enum RegionEnum{

    /**
     * 上海集群的regionId和launchTemplateId
     */
    SHANGHAI("上海", "cn-shanghai", "YOUR_LAUNCH_TEMPLATE_ID"),

    /**
     * 新加坡集群的regionId和launchTemplateId
     */
    SINGAPORE("新加坡", "ap-southeast-1", "YOUR_LAUNCH_TEMPLATE_ID"),

    ;

    private String name;
    private String regionId;
    private String launchTemplateId;

    RegionEnum(String name, String regionId, String launchTemplateId) {
        this.name = name;
        this.regionId = regionId;
        this.launchTemplateId = launchTemplateId;
    }

    public static String getRegionId(String name) {
        for (RegionEnum regionEnum : RegionEnum.values()) {
            if (regionEnum.name.equals(name)) {
                return regionEnum.regionId;
            }
        }
        return null;
    }

    public static String getLaunchTemplateId(String name) {
        for (RegionEnum regionEnum : RegionEnum.values()) {
            if (regionEnum.name.equals(name)) {
                return regionEnum.launchTemplateId;
            }
        }
        return null;
    }
}
