package com.souta.linuxserver.util;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class FileUtil {
    public static String ReadFile(String path) {
        StringBuffer laststr = new StringBuffer();
        java.io.File file = new java.io.File(path);// 打开文件
        if (!file.exists()) {
            return null;
        }
        BufferedReader reader = null;
        try {
            FileInputStream in = new FileInputStream(file);
            reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));// 读取文件
            String tempString = null;
            while ((tempString = reader.readLine()) != null) {
                laststr = laststr.append(tempString);
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException el) {
                }
            }
        }
        return laststr.toString();
    }
    public static String getFileMD5(String path){
        File file = new File(path);
        if (!file.exists() || !file.isFile()) return null;
        MessageDigest digest = null;
        FileInputStream in = null;
        byte buffer[] = new byte[1024];
        int len;
        try {
            digest = MessageDigest.getInstance("MD5");
            in = new FileInputStream(file);
            while ((len = in.read(buffer, 0, 1024)) != -1) {
                digest.update(buffer, 0, len);
            }
            in.close();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        BigInteger bigInt = new BigInteger(1, digest.digest());
        return bigInt.toString(16);
    }
}
