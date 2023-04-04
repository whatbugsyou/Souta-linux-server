package com.souta.linuxserver.service.impl;

import com.souta.linuxserver.dto.BatchedChangeADSLDTO;
import com.souta.linuxserver.dto.ChangeOneADSLDTO;
import com.souta.linuxserver.entity.ADSL;
import com.souta.linuxserver.entity.Namespace;
import com.souta.linuxserver.entity.PPPOE;
import com.souta.linuxserver.entity.Veth;
import com.souta.linuxserver.service.NamespaceService;
import com.souta.linuxserver.service.PPPOEService;
import com.souta.linuxserver.service.VethService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PPPOEServiceImpl implements PPPOEService {
    private static final Logger log = LoggerFactory.getLogger(PPPOEService.class);
    private static final String adslAccountFilePath = "/root/adsl.txt";
    private static final String pppConfigFilePath = "/etc/sysconfig/network-scripts/";
    private static final List<ADSL> adslAccountList = new ArrayList<>();
    private static final HashSet<String> isRecordInSecretFile = new HashSet<>();
    private static final HashSet<String> isCreatedpppFile = new HashSet<>();
    private static final int dialGapLimit = 10;
    private static final Timer timer = new Timer();
    private static final ReentrantLock reDialLock = new ReentrantLock();
    private static final ConcurrentHashMap<String, Condition> redialLimitedConditionMap = new ConcurrentHashMap<>();
    private static final ArrayList<Condition> conditionList = new ArrayList<>();
    private static final Pattern iproutePattern = Pattern.compile("([\\d\\\\.]+)\\s+dev\\s+(.*).*src\\s+([\\d\\\\.]+).*");

    static {
        File adslFile = new File(adslAccountFilePath);
        if (adslFile.exists()) {
            FileReader fileReader = null;
            BufferedReader bufferedReader = null;
            try {
                fileReader = new FileReader(adslFile);
                bufferedReader = new BufferedReader(fileReader);
                String line;
                String reg = "(.*)----(.*)----(.*)";
                Pattern compile = Pattern.compile(reg);
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
            log.info("not found {} file ", adslAccountFilePath);
            System.exit(1);
        }
        for (int i = 0; i < adslAccountList.size(); i++) {
            conditionList.add(reDialLock.newCondition());
        }
    }

    private final NamespaceService namespaceService;
    private final VethService vethService;
    @Autowired
    @Qualifier("dialingPool")
    private ExecutorService dialingPool;

    public PPPOEServiceImpl(NamespaceService namespaceService, VethService vethService) {
        this.namespaceService = namespaceService;
        this.vethService = vethService;
    }


    @Override
    public PPPOE createPPPOE(String pppoeId) {
        ADSL adsl = adslAccountList.get(Integer.parseInt(pppoeId) - 1);
        if (adsl == null) {
            return null;
        }
        String vethName = Veth.DEFAULT_PREFIX + pppoeId;
        String namespaceName = Namespace.DEFAULT_PREFIX + pppoeId;
        Veth veth = vethService.createVeth(adsl.getEthernetName(), vethName, namespaceName);
        String adslUser = adsl.getAdslUser();
        String adslPassword = adsl.getAdslPassword();
        createConfigFile(pppoeId, adslUser, adslPassword);

        return new PPPOE(veth, pppoeId, adslUser, adslPassword);
    }

    @Override
    public boolean changeADSLAccount(ChangeOneADSLDTO adsl) {
        ADSL update = new ADSL(adsl.getAdslUsername(), adsl.getAdslPassword(), adsl.getEthernetName());
        Long pppoeId = adsl.getLineId();
        ADSL origin = adslAccountList.get((int) (pppoeId - 1));
        if (update.getEthernetName() == null) {
            update.setEthernetName(origin.getEthernetName());
        }
        adslAccountList.set((int) (pppoeId - 1), update);
        for (int i = 0; i < adslAccountList.size(); i++) {
            origin = adslAccountList.get(i);
            if (origin.getAdslUser().equals(adsl.getAdslUsername())) {
                if (update.getEthernetName() == null) {
                    update.setEthernetName(origin.getEthernetName());
                }
                adslAccountList.set(i, update);
                int lineId = i + 1;
                isCreatedpppFile.remove(Integer.toString(lineId));
            }
        }
        isRecordInSecretFile.remove(adsl.getAdslUsername());
        File adslFile = new File(adslAccountFilePath);
        try (FileWriter fileWriter = new FileWriter(adslFile)) {
            for (ADSL data : adslAccountList) {
                fileWriter.write(data.toString() + "\n");
            }
            return true;
        } catch (IOException e) {
            log.error("refresh adsl file error:{}", e.getMessage());
        }
        return false;
    }

    @Override
    public boolean batchedChangeADSLAccount(BatchedChangeADSLDTO adsl) {
        ADSL update = new ADSL(adsl.getAdslUsername(), adsl.getAdslPassword(), adsl.getEthernetName());
        for (int i = 0; i < adslAccountList.size(); i++) {
            ADSL origin = adslAccountList.get(i);
            if (origin.getAdslUser().equals(adsl.getOldAdslUsername()) || origin.getAdslUser().equals(adsl.getAdslUsername())) {
                if (update.getEthernetName() == null) {
                    update.setEthernetName(origin.getEthernetName());
                }
                adslAccountList.set(i, update);
                int lineId = i + 1;
                isCreatedpppFile.remove(Integer.toString(lineId));
            }
        }
        isRecordInSecretFile.remove(adsl.getAdslUsername());
        File adslFile = new File(adslAccountFilePath);
        try (FileWriter fileWriter = new FileWriter(adslFile)) {
            for (ADSL data : adslAccountList) {
                fileWriter.write(data.toString() + "\n");
            }
            return true;
        } catch (IOException e) {
            log.error("refresh adsl file error:{}", e.getMessage());
        }
        return false;

    }

    @Override
    public boolean isDialUp(String pppoeId) {
        String cmd = "ip route";
        String namespaceName = Namespace.DEFAULT_PREFIX + pppoeId;
        Process process = namespaceService.exeCmdInNamespace(namespaceName, cmd);
        if (process == null) {
            return false;
        }
        try (InputStream inputStream = process.getInputStream();
             OutputStream outputStream = process.getOutputStream();
             InputStream errorStream = process.getErrorStream()
        ) {
            return inputStream.read() != -1;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public FutureTask<PPPOE> dialUp(String pppoeId) {
        PPPOE pppoe = getPPPOE(pppoeId);
        return dialUp(pppoe);
    }

    @Override
    public boolean shutDown(String pppoeId) {
        StringBuilder pid = new StringBuilder();
        String pidCheckCmd = String.format("ps ax|awk '/(ppp%s$)|(ppp%s )/{print $1}'", pppoeId, pppoeId);
        Process process = namespaceService.exeCmdWithNewSh(pidCheckCmd);
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
        process = namespaceService.exeCmdWithNewSh("kill -9" + pid);
        try (InputStream inputStream = process.getInputStream();
             OutputStream outputStream = process.getOutputStream();
             InputStream errorStream = process.getErrorStream()
        ) {
            process.waitFor();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return true;
    }

    @Override
    public boolean reDialup(String pppoeId) {
        shutDown(pppoeId);
        dialUp(pppoeId);
        return true;
    }

    @Override
    public HashSet<String> getDialuppedIdSet() {
        HashSet<String> result = new HashSet<>();
        String cmd = "ps ax|awk '/ppp\\d/ {print $9}'";
//        String cmd = "pgrep -a pppoe|awk '/run/ {print $4}'";
        Pattern compile = Pattern.compile(".*?(\\d+).*");
        Process process = namespaceService.exeCmdWithNewSh(cmd);
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
    public List<ADSL> getADSLList() {
        return adslAccountList;
    }

    @Override
    public String getIP(String pppoeId) {
        String cmd = "ip route";
        Process process = namespaceService.exeCmdInNamespace(Namespace.DEFAULT_PREFIX + pppoeId, cmd);
        if (process == null) {
            return null;
        }
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
    public boolean isDialUp(PPPOE pppoe) {
        return isDialUp(pppoe.getId());
    }


    @Override
    public boolean checkConfigFileExist(String pppoeId) {
        File file = new File(pppConfigFilePath + "ifcfg-ppp" + pppoeId);
        return file.exists();
    }

    public boolean createConfigFile(String pppoeId, String adslUser, String adslPassword) {
        createMainConfigFile(pppoeId, adslUser);
        refreshSecretConfig(adslUser, adslPassword);
        return true;
    }

    private synchronized void refreshSecretConfig(String adslUser, String adslPassword) {
        if (isRecordInSecretFile.contains(adslUser)) {
            return;
        }
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
            isRecordInSecretFile.add(adslUser);
        } catch (IOException | SecurityException e) {
            e.printStackTrace();
        }
    }

    private void createMainConfigFile(String pppoeId, String adslUser) {
        if (isCreatedpppFile.contains(pppoeId)) {
            return;
        }
        String configFilePath = pppConfigFilePath + "ifcfg-ppp" + pppoeId;


        String line;
        try (FileWriter fileWriter = new FileWriter(configFilePath);
             BufferedWriter cfgfileBufferedWriter = new BufferedWriter(fileWriter);
             InputStream ifcfg_pppX_template = this.getClass().getResourceAsStream("/static/ifcfg-pppX-template");
             InputStreamReader inputStreamReader = new InputStreamReader(ifcfg_pppX_template);
             BufferedReader tmpbufferedReader = new BufferedReader(inputStreamReader)) {
            while (((line = tmpbufferedReader.readLine()) != null)) {
                line = line.replace("{X}", pppoeId);
                line = line.replace("{USER}", adslUser);
                cfgfileBufferedWriter.write(line);
                cfgfileBufferedWriter.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (checkConfigFileExist(pppoeId)) {
            isCreatedpppFile.add(pppoeId);
        }
    }

    @Override
    public FutureTask<PPPOE> dialUp(PPPOE pppoe) {
        Callable<PPPOE> callable = () -> {
            boolean configFileExist = isCreatedpppFile.contains(pppoe.getId());
            boolean vethCorrect = vethService.checkExist(pppoe.getVeth().getInterfaceName(), Namespace.DEFAULT_PREFIX + pppoe.getId());
            if (!configFileExist || !vethCorrect) {
                return null;
            }
            if (isDialUp(pppoe)) {
                return getPPPOE(pppoe.getId());
            } else {
                if (!vethService.checkIsUp(pppoe.getVeth())) {
                    vethService.upVeth(pppoe.getVeth());
                }
                Namespace namespace = pppoe.getVeth().getNamespace();
                String ifupCMD = "ifup " + "ppp" + pppoe.getId();
                reDialLock.lock();
                try {
                    Condition condition = redialLimitedConditionMap.get(pppoe.getId());
                    if (condition != null) {
                        condition.await();
                    }
                } finally {
                    reDialLock.unlock();
                }
                log.info("ppp{} start dialing ...", pppoe.getId());
                Process process = namespaceService.exeCmdInNamespace(namespace, ifupCMD);
                try (InputStream inputStream = process.getInputStream();
                     OutputStream outputStream = process.getOutputStream();
                     InputStream errorStream = process.getErrorStream()
                ) {
                    long beginTimeMillis = System.currentTimeMillis();
                    String ip = null;
                    while (true) {
                        process.waitFor(1, TimeUnit.SECONDS);
                        ip = getIP(pppoe.getId());
                        long costTimeMillis = System.currentTimeMillis() - beginTimeMillis;
                        if (ip != null || costTimeMillis > 60 * 1000) {
                            break;
                        }
                    }
                    limitRedialTime(pppoe.getId());
                    if (ip == null) {
                        log.warn("ppp{} dialing time reach 60s ,shutdown", pppoe.getId());
                        process.destroy();
                        shutDown(pppoe);
                    } else {
                        pppoe.setOutIP(ip);
                    }
                    long costTimeMillis = System.currentTimeMillis() - beginTimeMillis;
                    log.info("ppp{} has return , cost {}ms ,ip =[{}]", pppoe.getId(), costTimeMillis, ip == null ? "" : ip);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return pppoe;
            }
        };
        FutureTask<PPPOE> futureTask = new FutureTask(callable);
        dialingPool.execute(futureTask);
        return futureTask;
    }

    private void limitRedialTime(String id) {
        Condition condition = conditionList.get(Integer.parseInt(id) - 1);
        redialLimitedConditionMap.put(id, condition);
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                reDialLock.lock();
                try {
                    condition.signalAll();
                    redialLimitedConditionMap.remove(id);
                } finally {
                    reDialLock.unlock();
                }
            }
        };
        timer.schedule(timerTask, TimeUnit.SECONDS.toMillis(dialGapLimit));
    }

    @Override
    public boolean shutDown(PPPOE pppoe) {
        return shutDown(pppoe.getId());
    }

    @Override
    public boolean reDialup(PPPOE pppoe) {
        shutDown(pppoe);
        //sleep 3s ?
        dialUp(pppoe);
        return true;
    }

    @Override
    public PPPOE getPPPOE(String pppoeId) {
        PPPOE pppoe;
        String vethName = Veth.DEFAULT_PREFIX + pppoeId;
        Veth veth = vethService.getVeth(vethName);
        if (veth == null) {
            return null;
        }
        pppoe = new PPPOE(pppoeId);
        pppoe.setVeth(veth);
        if (isDialUp(pppoeId)) {
            String cmd = "ip route";
            Process process = namespaceService.exeCmdInNamespace(Namespace.DEFAULT_PREFIX + pppoeId, cmd);
            try (InputStream inputStream = process.getInputStream();
                 OutputStream outputStream = process.getOutputStream();
                 InputStream errorStream = process.getErrorStream();
                 BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream))
            ) {
                String line;
                Pattern pattern2 = Pattern.compile("([\\d\\\\.]+) dev (.*) proto kernel scope link src ([\\d\\\\.]+) ");
                while ((line = bufferedReader.readLine()) != null) {
                    Matcher matcher2 = pattern2.matcher(line);
                    if (matcher2.matches()) {
                        String outIP = matcher2.group(3);
                        String gateWay = matcher2.group(1);
                        String runingOnInterfaceName = matcher2.group(2);
                        pppoe.setOutIP(outIP);
                        pppoe.setGateWay(gateWay);
                        pppoe.setRuningOnInterfaceName(runingOnInterfaceName);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            boolean exist = isCreatedpppFile.contains(pppoeId);
            if (!exist) {
                return null;
            }
        }
        pppoe.setAdslPassword(adslAccountList.get(Integer.parseInt(pppoeId) - 1).getAdslPassword());
        pppoe.setAdslUser(adslAccountList.get(Integer.parseInt(pppoeId) - 1).getAdslUser());
        return pppoe;
    }
}
