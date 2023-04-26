package com.souta.linuxserver.service;

import com.souta.linuxserver.entity.ADSL;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class ADSLConfigManager {
    private static final List<ADSL> adslAccountList = new ArrayList<>();
    private static final String adslAccountFilePath = "/root/adsl.txt";
    private static final String pattern = "(.*)----(.*)----(.*)";

    static {
        File adslFile = new File(adslAccountFilePath);
        if (adslFile.exists()) {
            FileReader fileReader = null;
            BufferedReader bufferedReader = null;
            try {
                fileReader = new FileReader(adslFile);
                bufferedReader = new BufferedReader(fileReader);
                String line;
                Pattern compile = Pattern.compile(pattern);
                while ((line = bufferedReader.readLine()) != null) {
                    Matcher matcher = compile.matcher(line);
                    if (matcher.matches()) {
                        ADSL adsl = new ADSL(matcher.group(1), matcher.group(2), matcher.group(3));
                        adslAccountList.add(adsl);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (bufferedReader != null) {
                    try {
                        bufferedReader.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (fileReader != null) {
                    try {
                        fileReader.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            log.info("find {} adsl account", adslAccountList.size());
        } else {
            log.error("not found {} file ", adslAccountFilePath);
            System.exit(1);
        }
    }

    public ADSL getADSL(int index){
        return adslAccountList.get(index);
    }
}
