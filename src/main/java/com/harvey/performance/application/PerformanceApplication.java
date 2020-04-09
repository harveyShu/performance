package com.harvey.performance.application;

import com.harvey.performance.application.factory.CloudFactory;
import com.harvey.performance.application.interfaces.BaseCloudService;
import com.harvey.performance.enums.CloudTypeEnum;
import com.harvey.performance.exception.PerformanceException;
import com.harvey.performance.jschutils.JschExecUtil;
import com.harvey.performance.jschutils.JschSftpUtil;
import com.harvey.performance.jschutils.JschUtil;
import com.jcraft.jsch.*;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.collections.CollectionUtils;

import java.io.File;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * @author shu
 */
@Data
public class PerformanceApplication {

    /**
     *
     */
    private static final Logger LOG = LoggerFactory.getLogger(PerformanceApplication.class);

    /**
     *
     */
    private final String RESOURCES_BASE_PATH = "/src/main/resources/";

    /**
     *
     */
    private final String REMOTE_CASE_ROOT = "/usr/local/JmeterTest/TestCase/";

    /**
     *
     */
    private final String REMOTE_JMETER_ROOT = "/usr/local/JmeterTest/";

    /**
     *
     */
    private final String REPORT_PATH = "/src/main/resources/JmeterReport/";

    /**
     *
     */
    private final String START_TIME = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss").format(new Date());

    /**
     *
     */
    private String region = "新加坡";

    /**
     *
     */
    private int instanceCount = 3;

    /**
     *
     */
    private int expireHour = 1;

    /**
     *
     */
    private Map<String, String> params = null;

    /**
     *
     */
    private List<String> hostList = null;

    /**
     *
     */
    private String masterHost = null;

    /**
     *
     */
    private Map<String, String> resourceFilePath = null;

    /**
     *
     */
    private String userName = "root";

    /**
     *
     */
    private String password = "6EV9m2bGrY";

    /**
     *
     */
    private String srcJmx;

    public PerformanceApplication(String srcJmx) {
        this.srcJmx = srcJmx;
    }

    public PerformanceApplication(String srcJmx, int instanceCount, int expireHour) {
        this.srcJmx = srcJmx;
        this.instanceCount = instanceCount;
        this.expireHour = expireHour;
    }

    /**
     *
     */
    public void runTest() {
        String jmxAbsolutePath = localAbsolutePath(srcJmx);
        boolean downloadFlag = false;
        LOG.info("当前指定的jmx文件路径为：[{}]", jmxAbsolutePath);
        if (!CollectionUtils.hasElements(hostList)) {
            downloadFlag = true;
            BaseCloudService cloudService = CloudFactory.createCloudService(CloudTypeEnum.aliyun, region);
            String idList = cloudService.runInstances(instanceCount, expireHour);
            LOG.info("等待实例创建完成");
            hostList = cloudService.ipAddressQuery(idList);
        } else {
            LOG.info("hostList已指定，不在重新申请实例");
        }
        List<Session> sessionList = connectInstances(hostList);
        Session masterSession = null;
        if (null == masterHost) {
            masterSession = sessionList.get(0);
        } else {
            for (Session session : sessionList) {
                if (masterHost.equals(session.getHost())) {
                    masterSession = session;
                    break;
                }
            }
        }
        uploadFiles(masterSession, sessionList, jmxAbsolutePath);
        configureSlave(true, masterSession, sessionList);
        executeCommand(downloadFlag, masterSession);
        LOG.info("master机IP地址：[{}]", masterSession.getHost());
        LOG.info("slave机IP地址如下：");
        for (int i = 1; i < sessionList.size(); i++) {
            System.out.println(sessionList.get(i).getHost());
        }
    }

    /**
     * 在master压测机运行JMeter，调度slave压测
     *
     * @param downloadFlag 是否需要下载安装JMeter
     * @param master master压测机session
     */
    private void executeCommand(boolean downloadFlag, Session master) {
        LOG.info("压测开始");
        // 判断是否需要重新下载Jmeter包
        Channel channel = JschSftpUtil.getOneSftpChannel(master);
        assert channel != null;
        try {
            ((ChannelSftp) channel).ls(REMOTE_JMETER_ROOT + "apache-jmeter");
        } catch (SftpException e) {
            if (2 == e.id) {
                downloadFlag = true;
            } else {
                LOG.warn("调用ChannelSftp.ls()方法失败", e);
            }
        }
        channel = JschExecUtil.getOneExecChannel(master);
        assert channel != null;
        JschExecUtil.execCmdOld((ChannelExec) channel, generateCommand(master.getHost(), true, downloadFlag));
        channel.disconnect();
        String reportPath = System.getProperty("user.dir") + REPORT_PATH;
        LOG.info("压测结束，下载报告文件至[{}]", reportPath);
        channel = JschSftpUtil.getOneSftpChannel(master);
        assert channel != null;
        List<String> srcList = new ArrayList<>();
        srcList.add(REMOTE_CASE_ROOT + START_TIME + ".tar");
        List<String> dstList = new ArrayList<>();
        dstList.add(reportPath);
        (new File(reportPath)).mkdirs();
        if (JschSftpUtil.downloadFile((ChannelSftp) channel, srcList, dstList, master.getHost())) {
            LOG.info("已成功下载报告文件至\n[{}]\n请前往查看", reportPath);
        }
    }

    /**
     * 使用命令行配置slave压测机，启动./jmeter-server
     *
     * @param downloadFlag 是否需要下载安装JMeter true:需要下载安装
     * @param master master压测机session
     * @param sessions slaver压测机session列表
     */
    private void configureSlave(boolean downloadFlag, Session master, List<Session> sessions) {
        LOG.info("开始执行jmeter下载和配置的cmd命令");
        ExecutorService threadPool = Executors.newCachedThreadPool();
        Map<Channel, Session> channelSessionHashMap = JschExecUtil.getExecChannel(sessions);
        if (null == channelSessionHashMap) {
            throw new PerformanceException("channel创建失败");
        }
        for (Channel channelExec : channelSessionHashMap.keySet()) {
            if (!master.equals(channelSessionHashMap.get(channelExec))) {
                threadPool.execute(() -> JschExecUtil.execCmdOld((ChannelExec) channelExec, generateCommand("", false, downloadFlag)));
            }
        }
        threadPool.shutdown();
        try {
            while (true) {
                if (threadPool.awaitTermination(10, TimeUnit.SECONDS)) {
                    break;
                }
            }
        } catch (InterruptedException e) {
            LOG.info(e.getMessage());
        }

        LOG.info("启动slave机的jmeter-server");
        ExecutorService threadPool2 = Executors.newCachedThreadPool();
        channelSessionHashMap = JschExecUtil.getExecChannel(sessions);
        assert channelSessionHashMap != null;
        for (Channel channelExec : channelSessionHashMap.keySet()) {
            if (!master.equals(channelSessionHashMap.get(channelExec))) {
                threadPool2.execute(() -> JschExecUtil.execCmd((ChannelExec) channelExec, "cd apache-jmeter/bin\n" + "./jmeter-server\n"));
            }
        }
        threadPool2.shutdown();
        try {
            while (true) {
                if (threadPool2.awaitTermination(10, TimeUnit.SECONDS)) {
                    break;
                }
            }
        } catch (InterruptedException e) {
            LOG.info(e.getMessage());
        }
    }

    /**
     * 生成JMeter命令行方式运行的command
     *
     * @param masterHost master的IP
     * @param isMaster 是否返回master的命令
     * @param downloadFlag 是否需要下载安装JMeter true:需要下载安装
     * @return JMeter运行命令
     */
    private String generateCommand(String masterHost, boolean isMaster, boolean downloadFlag) {
        String downloadJmeterPackage = "wget -N http://mirror-hk.koddos.net/apache//jmeter/binaries/apache-jmeter-5.2.tgz\n";
        String uncompressJmeterPackage = "tar -xf apache-jmeter-5.2.tgz\n";
        String renameJmeterFile = "mv apache-jmeter-5.2 apache-jmeter\n";
        String enterJmeterBin = "cd apache-jmeter/bin\n";
        String downloadFastJson = "wget -N http://repo1.maven.org/maven2/com/alibaba/fastjson/1.2.58/fastjson-1.2.58.jar -P apache-jmeter/lib/\n";
        String configureJvm = "sed -i 's/: \"${HEAP:=\"-Xms1g -Xmx1g -XX:MaxMetaspaceSize=256m\"}/: \"${HEAP:=\"-Xms8g -Xmx8g -XX:MaxMetaspaceSize=256m\"}/g' jmeter\n";
        String removeDubboPlugin = "cp -f /root/jmeter-plugins-dubbo-2.7.1-jar-with-dependencies.jar /root/apache-jmeter/lib/ext/jmeter-plugins-dubbo-2.7.1-jar-with-dependencies.jar\n";
        String configureJmeterSeverPort = "sed -i 's/#server_port=1099/server_port=1099/g' jmeter.properties\n";
        String configureJmeterLocalPort = "sed -i 's/#server.rmi.localport=4000/server.rmi.localport=4000/g' jmeter.properties\n";
        String configureJmeterTcpCharset = "sed -i 's/#tcp.charset=/tcp.charset=UTF-8/g' jmeter.properties\n";
        String configureJmeterSeverSsl = "sed -i 's/#server.rmi.ssl.disable=false/server.rmi.ssl.disable=true/g' jmeter.properties\n";
        StringBuilder cmd = new StringBuilder();
        if (isMaster) {
            cmd.append("cd ").append(REMOTE_JMETER_ROOT).append("\n");
            if (downloadFlag) {
                cmd.append(downloadJmeterPackage);
                cmd.append(uncompressJmeterPackage);
                cmd.append(renameJmeterFile);
            }
//            cmd.append(downloadFastJson);
            cmd.append(enterJmeterBin);
            cmd.append(configureJvm);
//            cmd.append(removeDubboPlugin);
            cmd.append(configureJmeterTcpCharset);
            cmd.append(configureJmeterSeverSsl);
            cmd.append("sed -i 's/remote_hosts=.*/remote_hosts=");
            for (String host : hostList) {
                if (!masterHost.equals(host)) {
                    cmd.append(host).append(":1099,");
                }
            }
            cmd.append("/g' jmeter.properties\n");
            if (null == params) {
                params = defaultParams();
            }
            cmd.append("./jmeter");
            for (String param : params.keySet()) {
                cmd.append(" ").append(param).append(" ").append(params.get(param));
            }
            cmd.append("\n");
            cmd.append("cd ").append(REMOTE_CASE_ROOT).append("\n");
            cmd.append("tar -cf ").append(START_TIME).append(".tar ").append(START_TIME).append("\n");
            LOG.info("master机的命令为");
            System.out.println(cmd.toString());
            return cmd.toString();
        } else {
            if (downloadFlag) {
                cmd.append(downloadJmeterPackage);
                cmd.append(uncompressJmeterPackage);
                cmd.append(renameJmeterFile);
            }
//            cmd.append(downloadFastJson);
            cmd.append(enterJmeterBin);
            cmd.append(configureJvm);
//            cmd.append(removeDubboPlugin);
            cmd.append(configureJmeterSeverPort);
            cmd.append(configureJmeterLocalPort);
            cmd.append(configureJmeterTcpCharset);
            cmd.append(configureJmeterSeverSsl);
            return cmd.toString();
        }
    }

    /**
     * 返回默认的JMeter运行参数
     *
     * @return JMeter运行参数
     */
    private Map<String, String> defaultParams() {
        String[] strings = srcJmx.split("/");
        String jmxName = strings[strings.length - 1];
        String dstFile = REMOTE_CASE_ROOT + START_TIME + "/";
        Map<String, String> map = new HashMap<>(10);
        map.put("-n", "");
        map.put("-t", dstFile + jmxName);
        map.put("-r", "");
        map.put("-l", dstFile + jmxName + ".jtl");
        map.put("-e", "");
        map.put("-o", dstFile + "report");
        return map;
    }

    /**
     * 上传文件至压测机
     *
     * @param master   master压测机的session
     * @param sessions 所有session的列表
     * @param srcJmx   JMeter脚本本地路径
     */
    private void uploadFiles(Session master, List<Session> sessions, String srcJmx) {
        LOG.info("在master机器创建dstFile文件夹");
        Channel channel = JschExecUtil.getOneExecChannel(master);
        if (null == channel) {
            throw new PerformanceException("channel创建失败");
        }
        String dstFile = REMOTE_CASE_ROOT + START_TIME + "/";
        JschExecUtil.execCmdOld((ChannelExec) channel, "mkdir -p -v " + dstFile);
        LOG.info("开始上传文件到实例");
        List<String> masterSrcList = new ArrayList<>();
        masterSrcList.add(srcJmx);
        List<String> masterDstList = new ArrayList<>();
        masterDstList.add(dstFile);
        List<String> slaveSrcList = null;
        List<String> slaveDstList = null;
        if (null != resourceFilePath) {
            slaveSrcList = new ArrayList<>();
            slaveDstList = new ArrayList<>();
            Set<Map.Entry<String, String>> entrySet = resourceFilePath.entrySet();
            for (Map.Entry<String, String> entry : entrySet) {
                masterSrcList.add(localAbsolutePath(entry.getKey()));
                slaveSrcList.add(localAbsolutePath(entry.getKey()));
                masterDstList.add(entry.getValue());
                slaveDstList.add(entry.getValue());
            }
        }
        // channel与session对应
        Map<Channel, Session> channelSessionHashMap = JschSftpUtil.getSftpChannel(sessions);
        if (null == channelSessionHashMap) {
            throw new PerformanceException("channel创建失败");
        }
        ExecutorService threadPool = Executors.newCachedThreadPool();
        for (Channel channelSftp : channelSessionHashMap.keySet()) {
            String host = channelSessionHashMap.get(channelSftp).getHost();
            if (host.equals(master.getHost())) {
//                threadPool.execute(() -> JschSftpUtil.uploadFile((ChannelSftp) channelSftp, masterSrcList, masterDstList, host));
                JschSftpUtil.uploadFile((ChannelSftp) channelSftp, masterSrcList, masterDstList, host);
            } else if (!CollectionUtils.hasElements(slaveSrcList) && !CollectionUtils.hasElements(slaveDstList)) {
                //                threadPool.execute(() -> JschSftpUtil.uploadFile((ChannelSftp) channelSftp, finalSlaveSrcList, finalSlaveDstList, host));
                JschSftpUtil.uploadFile((ChannelSftp) channelSftp, slaveSrcList, slaveDstList, host);
            }
        }
        threadPool.shutdown();
        try {
            while (true) {
                if (threadPool.awaitTermination(10, TimeUnit.SECONDS)) {
                    break;
                }
            }
        } catch (InterruptedException e) {
            LOG.warn(e.getMessage());
        }
    }

    /**
     * 连接压测机，创建session
     *
     * @param hosts 压测机ip列表
     * @return session列表
     */
    private List<Session> connectInstances(List<String> hosts) {
        if (!CollectionUtils.hasElements(hosts)) {
            throw new PerformanceException("hosts不能为空");
        }
        return JschUtil.getSession(hosts, userName, password);
    }

    /**
     * 将path转换为本地的绝对路径
     *
     * @param path 路径
     * @return 本地绝对路径
     */
    private String localAbsolutePath(String path) {
        if ((new File(path)).exists()) {
            return path;
        } else {
            String absolutePath;
            absolutePath = System.getProperty("user.dir") + RESOURCES_BASE_PATH + path;
            LOG.info("absolutePath为：[{}]", absolutePath);
            if ((new File(path)).exists()) {
                return absolutePath;
            }
            URL url = this.getClass().getClassLoader().getResource(path);
            LOG.info("url为：[{}]", url);
            if (null != url) {
                if ((new File(url.getPath()).exists())) {
                    return url.getPath();
                }
            }
            throw new PerformanceException("jmx文件无法转换为绝对路径");
        }
    }

}
