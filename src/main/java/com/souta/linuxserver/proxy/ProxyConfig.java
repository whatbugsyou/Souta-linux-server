package com.souta.linuxserver.proxy;

import cn.hutool.core.util.RandomUtil;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import javax.annotation.PostConstruct;
import java.io.*;
import java.util.ArrayList;
import java.util.List;


@ConfigurationProperties(prefix = "line")
@Data
public class ProxyConfig {

    public static String RANDOM_AUTH_FILE_PATH = "/root/v2rayConfig/auth.txt";
    private static List<String[]> auth = new ArrayList<>();
    @NestedConfigurationProperty
    private Socks5Config socks5Config;
    @NestedConfigurationProperty
    private ShadowsocksConfig shadowsocksConfig;

    static private void generateRandomAuthFile() {
        File file = new File(RANDOM_AUTH_FILE_PATH);
        if (file.exists()) {
            return;
        }
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        try (FileWriter fileWriter = new FileWriter(file); BufferedWriter bufferedWriter = new BufferedWriter(fileWriter)) {
            for (int i = 0; i < 300; i++) {
                String socks5_username = RandomUtil.randomString(10);
                String socks5_password = RandomUtil.randomString(10);
                String shadowsocks_password = RandomUtil.randomString(10);
                String line = socks5_username + "----" + socks5_password + "----" + shadowsocks_password;
                auth.add(new String[]{socks5_username, socks5_password, shadowsocks_password});
                bufferedWriter.write(line);
                bufferedWriter.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @PostConstruct
    public void init() {
//        boot_time=$(sysctl -n kern.boottime | awk '{print $3}')
//            # 随机密码文件路径
//                file_path="/root/v2rayConfig/auth.txt"
//        if [ -f "$file_path" ]; then
//                file_mtime=$(stat -c %Y "$file_path")
//        if [ "$file_mtime" -lt "$boot_time" ]; then
//        echo "文件 $file_path 的修改时间早于系统启动时间，将被删除。"
//        rm "$file_path"
//        fi
        File file = new File(RANDOM_AUTH_FILE_PATH);
        if (file.exists()) {
            loadRandomAuthFile();
        } else {
            generateRandomAuthFile();
        }
    }

    public String getInboundConfigFilePath(String lineId) {
        return "/root/v2rayConfig/inbound" + lineId + ".json";
    }

    public String getOutboundConfigFilePath(String lineId) {
        return "/root/v2rayConfig/outbound" + lineId + ".json";
    }

    public String getShadowsocksTag(String lineId) {
        return "ss" + lineId;
    }

    public String getSocks5Tag(String lineId) {
        return "socks5" + lineId;
    }

    public String getOutBoundTag(String lineId) {
        return "out" + lineId;
    }

    private void loadRandomAuthFile() {
        File file = new File(RANDOM_AUTH_FILE_PATH);
        if (!file.exists()) {
            return;
        }
        try (FileReader fileReader = new FileReader(file); BufferedReader bufferedReader = new BufferedReader(fileReader);) {
            String line = null;
            while ((line = bufferedReader.readLine()) != null) {
                String[] split = line.split("----");
                auth.add(split);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Socks5Config getSocks5Config(String lineId) {
        String[] strings = auth.get(Integer.parseInt(lineId) - 1);
        if (strings != null) {
            Socks5Config socks5Config1 = new Socks5Config();
            socks5Config1.setPort(socks5Config.getPort());
            socks5Config1.setUsername(strings[0]);
            socks5Config1.setPassword(strings[1]);
            return socks5Config1;
        } else {
            return socks5Config;
        }
    }

    public ShadowsocksConfig getShadowsocksConfig(String lineId) {
        String[] strings = auth.get(Integer.parseInt(lineId) - 1);
        if (strings != null) {
            ShadowsocksConfig shadowsocksConfig1 = new ShadowsocksConfig();
            shadowsocksConfig1.setPort(shadowsocksConfig.getPort());
            shadowsocksConfig1.setPassword(strings[2]);
            shadowsocksConfig1.setMethod(shadowsocksConfig.getMethod());
            return shadowsocksConfig1;
        } else {
            return shadowsocksConfig;
        }
    }

    @Data
    public static class ShadowsocksConfig {

        private Integer port;

        private String password;

        private String method;

    }

    @Data
    public static class Socks5Config {

        private Integer port;

        private String username;

        private String password;

    }
}
