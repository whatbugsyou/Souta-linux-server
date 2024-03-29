package com.souta.linuxserver.util;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

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
}
