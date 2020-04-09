package com.harvey.performance.enums;

/**
 * @author harvey
 */
public enum CloudTypeEnum{

    /**
     * 阿里云
     */
    aliyun(1, "阿里云"),

    /**
     * 华为云
     */
    huawei(2, "华为云"),

    /**
     * 腾讯云
     */
    tencent(3, "腾讯云"),

    ;

    private Integer value;

    private String content;

    CloudTypeEnum(Integer value, String content) {
        this.value = value;
        this.content = content;
    }

    public void setValue(Integer value) {
        this.value = value;
    }

    public Integer getValue() {
        return this.value;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getContent() {
        return this.content;
    }

}
