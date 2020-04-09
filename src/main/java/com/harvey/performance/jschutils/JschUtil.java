package com.harvey.performance.jschutils;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.collections.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * @author shu
 */
public class JschUtil {

    private static final int TIMEOUT = 1000 * 60 * 10;

    private static final int DEFAULT_PORT = 22;

    private static final int RETRY_TIME = 3;

    private static final Logger LOG = LoggerFactory.getLogger(JschUtil.class);

    public static Session getOneSession(String host, String username, String password) {
        if (null != host && !("".equals(host))) {
            JSch jSch = new JSch();
            Session session;
            for (int retry = RETRY_TIME; retry > 0; ) {
                retry--;
                try {
                    session = jSch.getSession(username, host, DEFAULT_PORT);
                    // 设置密码
                    session.setPassword(password);
                    Properties config = new Properties();
                    config.put("StrictHostKeyChecking", "no");
                    // 为Session对象设置properties
                    session.setConfig(config);
                    // 设置timeout时间
                    session.setTimeout(TIMEOUT);
                    // 通过Session建立链接
                    session.connect();
                    return session;
                } catch (JSchException e) {
                    holdException(retry, host, e);
                }
            }
        }
        return null;
    }

    /**
     * 创建ssh连接，得到host列表和session列表
     *
     * @param hostList 阿里云查询实例状态的返回结果
     * @param username 实例的系统登录名
     * @param password 实例的登录密码
     */
    public static List<Session> getSession(List<String> hostList, String username, String password) {
        if (!CollectionUtils.hasElements(hostList)) {
            return null;
        }
        JSch jSch = new JSch();
        List<Session> sessionList = null;
        Session session;
        for (String host : hostList) {
            for (int retry = RETRY_TIME; retry > 0; ) {
                retry--;
                try {
                    session = jSch.getSession(username, host, DEFAULT_PORT);
                    // 设置密码
                    session.setPassword(password);
                    Properties config = new Properties();
                    config.put("StrictHostKeyChecking", "no");
                    // 为Session对象设置properties
                    session.setConfig(config);
                    // 设置timeout时间
                    session.setTimeout(TIMEOUT);
                    // 通过Session建立链接
                    session.connect();
                    if (!CollectionUtils.hasElements(sessionList)) {
                        sessionList = new ArrayList<>();
                    }
                    sessionList.add(session);
                    break;
                } catch (JSchException e) {
                    holdException(retry, host, e);
                }
            }
        }
        return sessionList;
    }

    private static void holdException(int retry, String host, JSchException e) {
        if (retry > 0) {
            LOG.warn("[{}] 连接失败，进行重试，剩余次数：[{}]", host, retry);
            try {
                Thread.sleep(1000 * 5);
            } catch (InterruptedException e1) {
                LOG.error("调用Thread.sleep()失败", e1);
            }
        } else {
            LOG.error("调用session.connect()失败，已重试3次", e);
        }
    }
}
