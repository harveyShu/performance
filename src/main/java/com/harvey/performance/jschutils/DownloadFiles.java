package com.harvey.performance.jschutils;

import com.aliyuncs.utils.StringUtils;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.Session;
import org.apache.tools.ant.util.FileUtils;
import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Collections;

/**
 * @author shu
 */
public class DownloadFiles {

    private static Logger LOG = LoggerFactory.getLogger(DownloadFiles.class);

    /**
     * 通过JSch的sftp工具类下载远端文件
     *
     * @param host       ip地址
     * @param username   ssh用户名
     * @param password   ssh密码
     * @param remotePath 远端路径（必须使用绝对路径）
     * @param localPath  本地路径（必须使用绝对路径）
     * @return true：下载成功；false：下载失败
     */
    public static boolean downloadRemoteFiles(String host, String username, String password, String remotePath, String localPath) {
        String[] array = remotePath.split("/");
        if (FileUtils.isAbsolutePath(localPath)) {
            LOG.error("{}路径不存在", localPath);
            return false;
        }
        String filename = array[array.length - 1];
        StringBuilder packagePath = new StringBuilder();
        Session session = JschUtil.getOneSession(host, username, password);
        Channel channel = JschExecUtil.getOneExecChannel(session);
        int execResult = -1;
        if (channel != null) {
            execResult = JschExecUtil.execCmdOld((ChannelExec) channel, generateCommand(remotePath, filename));
            for (String file : array) {
                if (StringUtils.isNotEmpty(file)) {
                    packagePath.append("/").append(file);
                }
            }
            packagePath.append(".tar");
        }
        if (execResult == 0) {
            channel = JschSftpUtil.getOneSftpChannel(session);
            if (channel != null) {
                if (JschSftpUtil.downloadFile((ChannelSftp) channel, Collections.singletonList(packagePath.toString()), Collections.singletonList(localPath), host)) {
                    LOG.info("已成功下载文件至\n[{}]\n请前往查看", localPath);
                    unTarFile(localPath + "/" + filename + ".tar", localPath);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * tar解压
     *
     * @param tarPath     压缩包文件路径
     * @param destination 目标文件夹
     */
    private static void unTarFile(String tarPath, String destination) {
        File targetFile = new File(destination);
        // 如果目录不存在，则创建
        targetFile.mkdirs();
        try (TarInputStream tarInputStream = new TarInputStream(new FileInputStream(new File(tarPath)))) {
            TarEntry entry;
            while ((entry = tarInputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = destination + "/" + entry.getName();
                // 需要判断文件所在的目录是否存在，处理压缩包里面有文件夹的情况
                File tempFile = new File(name.substring(0, name.lastIndexOf("/")));
                tempFile.mkdirs();
                try (BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(new File(name)))) {
                    int len;
                    byte[] buffer = new byte[1024];
                    while ((len = tarInputStream.read(buffer)) > 0) {
                        outputStream.write(buffer, 0, len);
                    }
                }

            }

        } catch (Exception e) {
            LOG.error("unTarFile exception", e);
        }
    }

    private static String generateCommand(String remotePath, String fileName) {
        return "cd " + remotePath + "\n" +
                "cd ..\n" +
                "tar -cf " + fileName + ".tar " + fileName + "\n";
    }
}
