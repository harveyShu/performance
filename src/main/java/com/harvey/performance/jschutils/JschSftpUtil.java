package com.harvey.performance.jschutils;

import com.jcraft.jsch.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.text.DecimalFormat;
import java.util.*;

/**
 * @author shu
 */
public class JschSftpUtil {

    private static final Logger LOG = LoggerFactory.getLogger(JschSftpUtil.class);

    public JschSftpUtil() {

    }

    /**
     * 获取单个连接的sftp管道
     *
     * @param session ssh连接的session
     * @return sftp的管道
     */
    public static Channel getOneSftpChannel(Session session) {
        if (null == session) {
            LOG.error("session为空");
            return null;
        }
        try {
            // 打开sftp通道
            Channel channel = session.openChannel("sftp");
            channel.connect();
            return channel;
        } catch (JSchException e) {
            LOG.error("调用channel.connect()失败", e);
        }
        return null;
    }

    /**
     * 获取多个连接的sftp管道
     *
     * @param sessionList session列表
     * @return key：sftp管道，value：session
     */
    public static Map<Channel, Session> getSftpChannel(List<Session> sessionList) {
        if (null == sessionList) {
            LOG.error("sessionList为空");
            return null;
        }
        HashMap<Channel, Session> sftpMap = new HashMap<>();
        for (Session session : sessionList) {
            try {
                // 打开sftp通道
                Channel channel = session.openChannel("sftp");
                channel.connect();
                sftpMap.put(channel, session);
            } catch (JSchException e) {
                LOG.error("调用channel.connect()失败", e);
            }
        }
        return sftpMap;
    }

    /**
     * 下载文件
     *
     * @param channelSftp sftp管道
     * @param src         源文件路径
     * @param dst         目标路径
     * @param host        host地址，用于标识下载进度
     * @return 判断是否有文件下载失败
     */
    public static boolean downloadFile(ChannelSftp channelSftp, List<String> src, List<String> dst, String host) {
        if (null == src || null == dst) {
            LOG.warn("src或dst为空");
            return false;
        }
        if (src.size() != dst.size()) {
            LOG.warn("源文件数组与目标文件数组不匹配");
            return false;
        }
        boolean result = true;
        for (int i = 0, retry = 3; i < src.size(); i++) {
            try {
                LOG.info("下载[{}]", src.get(i));
                channelSftp.get(src.get(i), dst.get(i), new sftpMonitor(0, host), ChannelSftp.RESUME);
                LOG.info("[{}]下载结束", src.get(i));
                retry = 3;
            } catch (SftpException e) {
                LOG.warn("调用channelSftp.get()失败", e);
                if (retry > 0) {
                    LOG.warn("[{}]下载失败，进行重试，剩余次数：[{}]", src.get(i), retry);
                    try {
                        Thread.sleep(1000 * 3);
                    } catch (InterruptedException e1) {
                        LOG.error("调用Thread.sleep()失败", e1);
                    }
                    i--;
                    retry--;
                } else {
                    LOG.warn("[{}]下载失败，已重试3次", src.get(i));
                    result = false;
                }
            }
        }
        channelSftp.disconnect();
        return result;
    }

    /**
     * 上传文件
     *
     * @param channelSftp sftp管道
     * @param src         源文件路径
     * @param dst         目标路径
     * @param host        host地址，用于标识上传进度
     * @return 判断是否有文件上传失败
     */
    public static boolean uploadFile(ChannelSftp channelSftp, List<String> src, List<String> dst, String host) {
        if (null == src || null == dst) {
            LOG.warn("src或dst为空");
            return false;
        }
        if (src.size() != dst.size()) {
            LOG.warn("源文件数组与目标文件数组不匹配");
            return false;
        }
        boolean result = true;
        for (int i = 0, retry = 3; i < src.size(); i++) {
            try {
                LOG.info("[{}] 上传[{}]", host, src.get(i));
                File file = new File(src.get(i));
//                channelSftp.put(src.get(i), dst.get(i), new sftpMonitor(file.length(), host), ChannelSftp.RESUME);
//                channelSftp.mkdir(dst.get(i));
                channelSftp.put(src.get(i), dst.get(i));
                LOG.info("[{}] [{}]上传结束", host, src.get(i));
                retry = 3;
            } catch (SftpException e) {
                LOG.warn("调用channelSftp.put()失败", e);
                if (retry > 0) {
                    LOG.info("[{}]上传失败，进行重试，剩余次数：[{}]", src.get(i), retry);
                    try {
                        Thread.sleep(1000 * 3);
                    } catch (InterruptedException e1) {
                        LOG.error("调用Thread.sleep()失败", e1);
                    }
                    i--;
                    retry--;
                } else {
                    LOG.warn("[{}]上传失败，已重试3次", src.get(i));
                    result = false;
                }
            }
        }
        channelSftp.disconnect();
        return result;
    }

    /**
     * 下载目录
     *
     * @param channelSftp sftp管道
     * @param srcDir      源目录路径
     * @param dstDir      目标路径
     */
    public static void downloadDir(ChannelSftp channelSftp, String srcDir, String dstDir) {
        Map<String, String> fileList = new HashMap<>();
        ArrayList<String> dirList = new ArrayList<>();
        String[] temp = srcDir.split("/");
        dirList.add(dstDir + "/" + temp[temp.length - 1]);
        listFilesFromDir(channelSftp, srcDir, dirList.get(0), fileList, dirList);
        if (fileList.size() == 0) {
            LOG.warn("源文件夹为空");
            return;
        }
        for (String dir : dirList) {
            File file = new File(dir);
            if (!file.exists()) {
                if (!file.mkdir()) {
                    LOG.warn("[{}]创建失败！", dir);
                }
            }
        }
        int retry = 3;
        for (String file : fileList.keySet()) {
            try {
                channelSftp.get(file, fileList.get(file));
                LOG.info("[{}] 下载成功", file);
                retry = 3;
            } catch (SftpException downloadError) {
                LOG.warn("调用channelSftp.get()失败", downloadError);
                if (retry > 0) {
                    try {
                        Thread.sleep(1000 * 3);
                    } catch (InterruptedException error) {
                        LOG.error("调用Thread.sleep()失败", error);
                    }
                    retry--;
                } else {
                    LOG.warn("[{}]下载失败，已重试3次", file);
                }
            }
//                try {
//                    File file = new File(fileList.get(i));
//                    channelSftp.put(fileList.get(i),dstDir,new sftpMonitor(file.length(),host), ChannelSftp.RESUME);
//                    retry = 3;
//                } catch (SftpException uploadError){
//                    if (retry > 0) {
//                        try {
//                            Thread.sleep(1000 * 3);
//                        } catch (InterruptedException error) {
//                            LOG.warn(error.getMessage());
//                        }
//                        i--;
//                        retry--;
//                    } else {
//                        LOG.warn("上传失败");
//                    }
//                }
//
        }
        channelSftp.disconnect();
    }

    /**
     * 列出指定目录下的文件/目录结构，生成目录结构map
     */
    private static void listFilesFromDir(ChannelSftp channelSftp, String dir, String dstDir, Map<String, String> fileList, List<String> dirList) {
        try {
            assert channelSftp != null;
            Vector vector = channelSftp.ls(dir);
            if (vector.size() > 0) {
                for (Object aVector : vector) {
                    ChannelSftp.LsEntry lsEntry = (ChannelSftp.LsEntry) aVector;
                    String filename = lsEntry.getFilename();
                    if ("..".equals(filename) || ".".equals(filename)) {
                        continue;
                    }
                    SftpATTRS attrs = lsEntry.getAttrs();
                    if (attrs.isDir()) {
                        dirList.add(dstDir + "/" + filename);
                        listFilesFromDir(channelSftp, dir + "/" + filename, dstDir + "/" + filename, fileList, dirList);
                    } else {
                        fileList.put(dir + "/" + filename, dstDir);
                    }
                }
            }
        } catch (SftpException e) {
            LOG.error("调用channelSftp.ls()失败", e);
        }
    }
}

/**
 * 用于下载和上传时的进度监控
 * @author shu
 */
class sftpMonitor extends TimerTask implements SftpProgressMonitor {

    private static final Logger LOG = LoggerFactory.getLogger(sftpMonitor.class);

    /**
     * 默认间隔时间为5秒
     */
    private long progressInterval = 5 * 1000;

    /**
     * 记录传输是否结束
     */
    private boolean isEnd = false;

    /**
     * 记录已传输的数据总大小
     */
    private long transfered;

    private String host;

    /**
     * 记录文件总大小
     */
    private long fileSize;

    /**
     * 定时器对象
     */
    private Timer timer;

    /**
     * 记录是否已启动timer记时器
     */
    private boolean isScheduled = false;

    sftpMonitor(long fileSize, String host) {
        this.fileSize = fileSize;
        this.host = host;
    }

    @Override
    public void run() {
        if (!isEnd()) {
            // 判断传输是否已结束
            long transfered = getTransfered();
            if (transfered != fileSize) {
                // 判断当前已传输数据大小是否等于文件总大小
                sendProgressMessage(transfered);
            } else {
                // 如果当前已传输数据大小等于文件总大小，说明已完成，设置end
                setEnd(true);
            }
        } else {
            // 如果传输结束，停止timer记时器
            stop();
        }
    }

    private void stop() {
        if (timer != null) {
            timer.cancel();
            timer.purge();
            timer = null;
            isScheduled = false;
        }
    }

    private void start() {
        if (timer == null) {
            timer = new Timer();
        }
        timer.schedule(this, 1000, progressInterval);
        isScheduled = true;
    }

    /**
     * 打印progress信息
     *
     * @param transfered 已传输的字节数
     */
    private void sendProgressMessage(long transfered) {
        if (fileSize != 0) {
            double d = ((double) transfered * 100) / (double) fileSize;
            DecimalFormat df = new DecimalFormat("#.##");
            long countKB = transfered >> 10;
            if (countKB > 0) {
                long countMB = countKB >> 10;
                if (countMB > 0) {
                    LOG.info("[{}] 已传输：[{}] MB\t当前进度: [{}]%", host, countMB, df.format(d));
                } else {
                    LOG.info("[{}] 已传输：[{}] KB\t当前进度: [{}]%", host, countKB, df.format(d));
                }
            } else {
                LOG.info("[{}] 已传输：[{}] bytes\t当前进度: [{}]%", host, transfered, df.format(d));
            }
        } else {
            long countKB = transfered >> 10;
            if (countKB > 0) {
                long countMB = countKB >> 10;
                if (countMB > 0) {
                    LOG.info("[{}] 已传输：[{}] MB", host, countMB);
                } else {
                    LOG.info("[{}] 已传输：[{}] KB", host, countKB);
                }
            } else {
                LOG.info("[{}] 已传输：[{}] bytes", host, transfered);
            }
        }
    }

    /**
     * 实现了SftpProgressMonitor接口的count方法
     */
    @Override
    public boolean count(long count) {
        if (isEnd()) {
            return false;
        }
        if (!isScheduled) {
            start();
        }
        add(count);
        return true;
    }

    /**
     * 实现了SftpProgressMonitor接口的end方法
     */
    @Override
    public void end() {
        setEnd(true);
    }

    private synchronized void add(long count) {
        transfered = transfered + count;
    }

    private synchronized long getTransfered() {
        return transfered;
    }

    public synchronized void setTransfered(long transfered) {
        this.transfered = transfered;
    }

    private synchronized void setEnd(boolean isEnd) {
        this.isEnd = isEnd;
    }

    private synchronized boolean isEnd() {
        return isEnd;
    }

    /**
     * 实现了SftpProgressMonitor接口的init方法
     */
    @Override
    public void init(int op, String src, String dest, long max) {

    }
}
