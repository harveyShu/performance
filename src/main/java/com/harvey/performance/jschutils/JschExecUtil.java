package com.harvey.performance.jschutils;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;

/**
 * @author harvey
 */
public class JschExecUtil {

    private static final Logger LOG = LoggerFactory.getLogger(JschExecUtil.class);

    public JschExecUtil() {

    }

    /**
     * 获取单个连接的exec管道
     *
     * @param session ssh连接的session
     * @return exec的管道
     */
    public static Channel getOneExecChannel(Session session) {
        if (null == session) {
            LOG.warn("session为空");
            return null;
        }
        try {
            // 打开exec通道
            return session.openChannel("exec");
        } catch (JSchException e) {
            LOG.error("调用channel.openChannel()失败", e);
        }
        return null;
    }

    /**
     * 获取多个连接的exec管道
     *
     * @param sessionList session列表
     * @return key：exec管道，value：session
     */
    public static HashMap<Channel, Session> getExecChannel(List<Session> sessionList) {
        if (null == sessionList) {
            LOG.warn("sessionList为空");
            return null;
        }
        HashMap<Channel, Session> execMap = new HashMap<>();
        for (Session session : sessionList) {
            try {
                // 打开exec通道
                Channel channel = session.openChannel("exec");
                execMap.put(channel, session);
            } catch (JSchException e) {
                LOG.error("调用channel.openChannel()失败", e);
            }
        }
        return execMap;
    }

    /**
     * 执行shell命令
     *
     * @param channelExec exec管道
     * @param cmd         shell命令
     * @return 执行结果（暂未使用起来）
     */
    public static int execCmd(ChannelExec channelExec, String cmd) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        channelExec.setCommand(cmd);
        channelExec.setInputStream(null);
        channelExec.setOutputStream(out);
        channelExec.setErrStream(error);
        int result = -1;
        try {
            channelExec.connect();
            //确保能够执行完成及响应所有数据
            result = channelExec.getExitStatus();
            try {
                Thread.sleep(5 * 1000);
            } catch (InterruptedException e) {
                LOG.error("调用Thread.sleep()失败", e);
            }
            if (error.size() != 0) {
                LOG.warn("[{}]执行结果: [{}]", channelExec.getSession().getHost(), error.toString());
            }
            channelExec.disconnect();
        } catch (JSchException e) {
            LOG.error("调用channelExec.disconnect()失败", e);
        }
        return result;
    }

    /**
     * 执行shell命令，并等待命令结束
     *
     * @param channelExec exec管道
     * @param cmd         shell命令
     * @return 执行结果（暂未使用）
     */
    public static int execCmdOld(ChannelExec channelExec, String cmd) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream extOut = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        channelExec.setCommand(cmd);
        channelExec.setInputStream(null);
        channelExec.setOutputStream(out);
        channelExec.setExtOutputStream(extOut);
        channelExec.setErrStream(error);
        int res = -1;
        try {
            InputStream in = channelExec.getInputStream();
            try {
                channelExec.connect();
                byte[] tmp = new byte[1024];
                while (true) {
                    while (in.available() > 0) {
                        int i = in.read(tmp, 0, 1024);
                        if (i < 0) {
                            break;
                        }
                        System.out.println(new String(tmp, 0, i));
                    }
                    if (channelExec.isClosed()) {
                        res = channelExec.getExitStatus();
                        if (error.size() != 0 && 0 != res) {
                            LOG.warn("[{}]执行命令失败: [{}]", channelExec.getSession().getHost(), error.toString());
                        }
                        break;
                    }
                }
                channelExec.disconnect();
                return res;
            } catch (JSchException e) {
                LOG.error("调用channelExec.connect()失败", e);
            }
        } catch (IOException e) {
            LOG.error("调用channelExec.getInputStream()失败", e);
        }
        channelExec.disconnect();
        return res;
    }
}
