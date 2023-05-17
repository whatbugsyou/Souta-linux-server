package com.souta.linuxserver.service.impl;

import com.souta.linuxserver.entity.Namespace;
import com.souta.linuxserver.entity.PPPOE;
import com.souta.linuxserver.service.NamespaceCommandService;
import com.souta.linuxserver.service.PPPOEService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class PPPOEServiceImpl implements PPPOEService {
    private static final String pppConfigFileDir = "/etc/sysconfig/network-scripts";
    private static final Pattern iproutePattern = Pattern.compile("([\\d\\\\.]+)\\s+dev\\s+(.*).*src\\s+([\\d\\\\.]+).*");
    private static final ConcurrentHashMap<String, PPPOE> PPPOEMap = new ConcurrentHashMap<>();
    private final NamespaceCommandService commandService;

    public PPPOEServiceImpl(NamespaceCommandService commandService) {
        this.commandService = commandService;
    }

    @Override
    public String dialUp(String pppoeId, String adslUser, String adslPassword, String ethernetName, String namespaceName) {
        String ip = getIP(pppoeId);
        if (ip != null) {
            return ip;
        }
        createConfigFile(pppoeId, adslUser, adslPassword, ethernetName);
        String ifupCMD = "ifup " + "ppp" + pppoeId;
        log.info("ppp{} start dialing ...", pppoeId);
        Process process = null;
        process = commandService.exec(ifupCMD, namespaceName);
        try (InputStream inputStream = process.getInputStream();
             OutputStream outputStream = process.getOutputStream();
             InputStream errorStream = process.getErrorStream()
        ) {
            long beginTimeMillis = System.currentTimeMillis();
            while (true) {
                boolean result = process.waitFor(1, TimeUnit.SECONDS);
                if (result && process.exitValue() != 0) {
                    break;
                }
                ip = getIP(pppoeId);
                long costTimeMillis = System.currentTimeMillis() - beginTimeMillis;
                if (ip != null) {
                    break;
                }else if (costTimeMillis > 60 * 1000){
                    log.warn("ppp{} dialing time reach 60s ,shutdown", pppoeId);
                    process.destroy();
                    shutDown(pppoeId);
                    break;
                }

            }
            long costTimeMillis = System.currentTimeMillis() - beginTimeMillis;
            log.info("ppp{} has return , cost {}ms ,ip =[{}]", pppoeId, costTimeMillis, ip == null ? "" : ip);

        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return ip;
    }

    @Override
    public boolean isDialUp(String pppoeId) {
        return getIP(pppoeId) != null;
    }

    @Override
    public PPPOE getPPPOE(String pppoeId) {
        return PPPOEMap.get(pppoeId);
    }

    private PPPOE savePPPOE(PPPOE pppoe) {
        return PPPOEMap.put(pppoe.getId(), pppoe);
    }

    @Override
    public boolean shutDown(String pppoeId) {
        StringBuilder pid = new StringBuilder();
        String pidCheckCmd = String.format("ps ax|awk '/(ppp%s$)|(ppp%s )/'|grep -v awk|awk '{print $1}'", pppoeId, pppoeId);
        Process process = commandService.exec(pidCheckCmd);
        try (InputStream inputStream = process.getInputStream();
             OutputStream outputStream = process.getOutputStream();
             InputStream errorStream = process.getErrorStream();
             InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
             BufferedReader bufferedReader = new BufferedReader(inputStreamReader)
        ) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                pid.append(" ").append(line);
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        commandService.execAndWaitForAndCloseIOSteam("kill -9" + pid);
        return true;
    }


    @Override
    public HashSet<String> getDialuppedIdSet() {
        HashSet<String> result = new HashSet<>();
        String cmd = "ps ax|awk '/ppp\\d/ {print $9}'";
//        String cmd = "pgrep -a pppoe|awk '/run/ {print $4}'";
        Pattern compile = Pattern.compile(".*?(\\d+).*");
        Process process = commandService.exec(cmd);
        try (InputStream inputStream = process.getInputStream();
             OutputStream outputStream = process.getOutputStream();
             InputStream errorStream = process.getErrorStream();
             InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
             BufferedReader bufferedReader = new BufferedReader(inputStreamReader)
        ) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                Matcher matcher = compile.matcher(line);
                if (matcher.matches()) {
                    result.add(matcher.group(1));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    @Override
    public String getIP(String pppoeId) {
        String cmd = "ip route";// "pppoe-status /etc/sysconfig/network-scripts/ifcfg-pppX" cmd is not available by pid file is not exist for some unknown reason.
        Process process = commandService.exec(cmd, Namespace.DEFAULT_PREFIX + pppoeId);
        try (InputStream inputStream = process.getInputStream();
             OutputStream outputStream = process.getOutputStream();
             InputStream errorStream = process.getErrorStream();
             InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
             BufferedReader bufferedReader = new BufferedReader(inputStreamReader)
        ) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                Matcher matcher2 = iproutePattern.matcher(line);
                if (matcher2.matches()) {
                    return matcher2.group(3);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    @Override
    public boolean checkConfigFileExist(String pppoeId) {
        File file = new File(pppConfigFileDir, "ifcfg-ppp" + pppoeId);
        return file.exists();
    }

    @Override
    public boolean createConfigFile(String pppoeId, String adslUser, String adslPassword, String ethernetName) {
        PPPOE pppoe = getPPPOE(pppoeId);
        if (pppoe == null || !pppoe.getAdslUser().equals(adslUser) || !pppoe.getAdslPassword().equals(adslPassword) || !pppoe.getEthName().equals(ethernetName)) {
            createMainConfigFile(pppoeId, adslUser, ethernetName);
            refreshSecretConfig(adslUser, adslPassword);
            pppoe = new PPPOE();
            pppoe.setId(pppoeId);
            pppoe.setAdslUser(adslUser);
            pppoe.setAdslPassword(adslPassword);
            pppoe.setEthName(ethernetName);
            savePPPOE(pppoe);
        }
        return true;
    }

    private synchronized void refreshSecretConfig(String adslUser, String adslPassword) {
        String line;
        File chap = new File("/etc/ppp/chap-secrets");
        File tmp_file = new File("/etc/ppp/chap-secrets_test");
        File pap = new File("/etc/ppp/pap-secrets");
        try (FileReader chapfileReader = new FileReader(chap);
             BufferedReader chapbufferedReader = new BufferedReader(chapfileReader);

             FileWriter tmpfileWriter = new FileWriter(tmp_file);
             BufferedWriter tmpfileBufferedWriter = new BufferedWriter(tmpfileWriter);

             FileWriter papfileWriter = new FileWriter(pap);
             BufferedWriter papBufferedWriter = new BufferedWriter(papfileWriter)
        ) {

            Pattern compile = Pattern.compile("\"(.*?)\".*\\*.*\"(.*?)\"");
            boolean userExist = false;
            while (((line = chapbufferedReader.readLine()) != null)) {
                if (!userExist) {
                    Matcher matcher = compile.matcher(line);
                    if (matcher.matches()) {
                        String _user = matcher.group(1);
                        String _password = matcher.group(2);
                        if (_user.equals(adslUser)) {
                            userExist = true;
                            if (!_password.equals(adslPassword)) {
                                _password = adslPassword;
                                line = "\"" + _user + "\"" + "     *       " + "\"" + _password + "\"";
                            }
                        }
                    }
                }
                tmpfileBufferedWriter.write(line);
                tmpfileBufferedWriter.newLine();

                papBufferedWriter.write(line);
                papBufferedWriter.newLine();
            }
            if (!userExist) {
                line = "\"" + adslUser + "\"" + "     *       " + "\"" + adslPassword + "\"";
                tmpfileBufferedWriter.write(line);
                papBufferedWriter.newLine();
            }
            chap.delete();
            tmp_file.renameTo(chap);
        } catch (IOException | SecurityException e) {
            e.printStackTrace();
        }
    }

    private void createMainConfigFile(String pppoeId, String adslUser, String ethernetName) {
        String line;
        try (FileWriter fileWriter = new FileWriter(new File(pppConfigFileDir, "ifcfg-ppp" + pppoeId));
             BufferedWriter cfgfileBufferedWriter = new BufferedWriter(fileWriter);
             InputStream ifcfg_pppX_template = this.getClass().getResourceAsStream("/static/ifcfg-pppX-template");
             InputStreamReader inputStreamReader = new InputStreamReader(ifcfg_pppX_template);
             BufferedReader tmpbufferedReader = new BufferedReader(inputStreamReader)) {
            while (((line = tmpbufferedReader.readLine()) != null)) {
                line = line.replace("{X}", pppoeId);
                line = line.replace("{USER}", adslUser);
                line = line.replace("{ETH}", ethernetName);
                cfgfileBufferedWriter.write(line);
                cfgfileBufferedWriter.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
