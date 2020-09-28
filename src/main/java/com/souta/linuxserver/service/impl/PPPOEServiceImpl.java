package com.souta.linuxserver.service.impl;

import com.souta.linuxserver.entity.ADSL;
import com.souta.linuxserver.entity.Namespace;
import com.souta.linuxserver.entity.PPPOE;
import com.souta.linuxserver.entity.Veth;
import com.souta.linuxserver.service.NamespaceService;
import com.souta.linuxserver.service.PPPOEService;
import com.souta.linuxserver.service.VethService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final String adslAccountFilePath = "/tmp/adsl.txt";
    private static final List<ADSL> adslAccountList = new ArrayList<>();
    private static final HashSet<String> isRecordInSecretFile = new HashSet<>();
    private static final int dilaGapLimit = 8;
    private static final ExecutorService pool = Executors.newCachedThreadPool();
    private static final Timer timer = new Timer();
    private static final ReentrantLock reDialLock = new ReentrantLock();
    private static final ConcurrentHashMap<String, Condition> redialLimitedConditionMap = new ConcurrentHashMap<>();
    private static final ArrayList<Condition> conditionList = new ArrayList<>();
    private static final Pattern iproutePattern = Pattern.compile("([\\d\\\\.]+) dev (.*) proto kernel scope link src ([\\d\\\\.]+) ");

    static {
        File adslFile = new File(adslAccountFilePath);
        if (adslFile.exists()) {
            FileReader fileReader = null;
            BufferedReader bufferedReader = null;
            try {
                fileReader = new FileReader(adslFile);
                bufferedReader = new BufferedReader(fileReader);
                String line;
                String reg = "(.*)----(.*)";
                Pattern compile = Pattern.compile(reg);
                while ((line = bufferedReader.readLine()) != null) {
                    Matcher matcher = compile.matcher(line);
                    if (matcher.matches()) {
                        ADSL adsl = new ADSL(matcher.group(1), matcher.group(2));
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

    public PPPOEServiceImpl(NamespaceService namespaceService, VethService vethService) {
        this.namespaceService = namespaceService;
        this.vethService = vethService;
    }

    @Override
    public PPPOE createPPPOE(String pppoeId) {
        return createPPPOE(pppoeId, null);
    }

    @Override
    public PPPOE createPPPOE(String pppoeId, Veth veth) {
        PPPOE pppoe;
        ADSL adsl = adslAccountList.get(Integer.parseInt(pppoeId) - 1);
        if (adsl != null) {
            String adslUser = adsl.getAdslUser();
            String adslPassword = adsl.getAdslPassword();
            createConifgFile(pppoeId, adslUser, adslPassword);
            pppoe = new PPPOE(veth, pppoeId, adslUser, adslPassword);
        } else {
            return null;
        }
        if (veth == null) {
            String vethName = "eth" + pppoeId;
            String namespaceName = "ns" + pppoeId;
            veth = vethService.createVeth(vethName, namespaceName);

        } else {
            boolean exist = vethService.checkExist(veth);
            if (exist) {
                String vethName = "eth" + pppoeId;
                if (!vethName.equals(veth.getInterfaceName())) {
                    return null;
                }
            }
        }
        pppoe.setVeth(veth);
        return pppoe;
    }

    @Override
    public boolean isDialUp(String pppoeId) {
        String cmd = "ip route";
        String namespaceName = "ns" + pppoeId;
        InputStream inputStream = namespaceService.exeCmdInNamespace(namespaceName, cmd);
        if (inputStream == null) {
            return false;
        }
        return hasOutput(inputStream);
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
        InputStream inputStream = namespaceService.exeCmdInDefaultNamespace(pidCheckCmd);
        if (inputStream != null) {
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            String line;
            try {
                while ((line = bufferedReader.readLine()) != null) {
                    pid.append(" ").append(line);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            namespaceService.exeCmdInDefaultNamespace("kill -9" + pid.toString());
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
        Pattern compile = Pattern.compile(".*?(\\d+).*");
        InputStream inputStream = namespaceService.exeCmdInDefaultNamespace(cmd);
        if (inputStream != null) {
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            String line;
            try {
                while ((line = bufferedReader.readLine()) != null) {
                    Matcher matcher = compile.matcher(line);
                    if (matcher.matches()) {
                        result.add(matcher.group(1));
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
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
        InputStream inputStream = namespaceService.exeCmdInNamespace("ns" + pppoeId, cmd);
        if (inputStream == null) {
            return null;
        }
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        String line;
        try {
            while ((line = bufferedReader.readLine()) != null) {
                Matcher matcher2 = iproutePattern.matcher(line);
                if (matcher2.matches() != false) {
                    return matcher2.group(3);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public boolean isDialUp(PPPOE pppoe) {
        return isDialUp(pppoe.getId());
    }

    private boolean hasOutput(InputStream inputStream) {
        return Socks5ServiceImpl.hasOutput(inputStream);
    }

    @Override
    public boolean checkConfigFileExist(String pppoeId) {
        String cmd = "ls /etc/sysconfig/network-scripts | grep ifcfg-ppp" + pppoeId + "$";
        InputStream inputStream = namespaceService.exeCmdInDefaultNamespace(cmd);
        return hasOutput(inputStream);
    }

    public boolean createConifgFile(String pppoeId, String adslUser, String adslPassword) {
        createMainConfigFile(pppoeId, adslUser);
        refreshSecretConfig(adslUser, adslPassword);
        return true;
    }

    private synchronized void refreshSecretConfig(String adslUser, String adslPassword) {
        if (isRecordInSecretFile.contains(adslUser)) {
            return;
        }
        BufferedWriter tmpfileBufferedWriter = null;
        BufferedWriter papBufferedWriter = null;
        BufferedReader chapbufferedReader = null;
        FileReader chapfileReader = null;
        FileWriter tmpfileWriter = null;
        FileWriter papfileWriter = null;
        String line;
        File chap = new File("/etc/ppp/chap-secrets");
        File tmp_file = new File("/etc/ppp/chap-secrets_test");
        File pap = new File("/etc/ppp/pap-secrets");
        try {
            chapfileReader = new FileReader(chap);
            chapbufferedReader = new BufferedReader(chapfileReader);

            tmpfileWriter = new FileWriter(tmp_file);
            tmpfileBufferedWriter = new BufferedWriter(tmpfileWriter);

            papfileWriter = new FileWriter(pap);
            papBufferedWriter = new BufferedWriter(papfileWriter);

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
        } finally {
            if (chapbufferedReader != null) {
                try {
                    chapbufferedReader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (chapfileReader != null) {
                try {
                    chapfileReader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if (tmpfileBufferedWriter != null) {
                try {
                    tmpfileBufferedWriter.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (tmpfileWriter != null) {
                try {
                    tmpfileWriter.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (papBufferedWriter != null) {
                try {
                    papBufferedWriter.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (papfileWriter != null) {
                try {
                    papfileWriter.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void createMainConfigFile(String pppoeId, String adslUser) {
        if (checkConfigFileExist(pppoeId)) {
            return;
        }
        String configFilePath = "/etc/sysconfig/network-scripts/ifcfg-ppp" + pppoeId;
        BufferedWriter cfgfileBufferedWriter = null;
        BufferedReader tmpbufferedReader = null;
        FileWriter fileWriter = null;
        String line;
        try {
            fileWriter = new FileWriter(new File(configFilePath));
            cfgfileBufferedWriter = new BufferedWriter(fileWriter);
            InputStream ifcfg_pppX_template = this.getClass().getResourceAsStream("/static/ifcfg-pppX-template");
            InputStreamReader inputStreamReader = new InputStreamReader(ifcfg_pppX_template);
            tmpbufferedReader = new BufferedReader(inputStreamReader);
            while (((line = tmpbufferedReader.readLine()) != null)) {
                line = line.replace("{X}", pppoeId);
                line = line.replace("{USER}", adslUser);
                cfgfileBufferedWriter.write(line);
                cfgfileBufferedWriter.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (tmpbufferedReader != null) {
                try {
                    tmpbufferedReader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (cfgfileBufferedWriter != null) {
                try {
                    cfgfileBufferedWriter.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (fileWriter != null) {
                try {
                    fileWriter.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public FutureTask<PPPOE> dialUp(PPPOE pppoe) {
        Callable<PPPOE> callable = () -> {
            boolean configFileExist = checkConfigFileExist(pppoe.getId());
            boolean vethCorrect = vethService.checkExist(pppoe.getVeth().getInterfaceName(), "ns" + pppoe.getId());
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
                try {
                    reDialLock.lock();
                    Condition condition = redialLimitedConditionMap.get(pppoe.getId());
                    if (condition != null) {
                        condition.await();
                    }
                } finally {
                    reDialLock.unlock();
                }
                log.info("ppp{} start dialing ...", pppoe.getId());
                namespaceService.exeCmdInNamespace(namespace, ifupCMD);
                int checkCount = 0;
                float costSec = 0;
                float sleepGapSec = 0.5f;
                int dialEndSec = 60;
                String ip = null;
                while (getIP(pppoe.getId())==null) {
                    checkCount++;
                    costSec = checkCount * sleepGapSec;
                    if (costSec % 10 == 0) {
                        log.info("ppp{} dialing {}s ...", pppoe.getId(), costSec);
                    }
                    if (costSec < dialEndSec) {
                        TimeUnit.MILLISECONDS.sleep((long) (sleepGapSec * 1000));
                    } else {
                        log.warn("ppp{} dialing time reach 60s ,shutdown", pppoe.getId());
                        break;
                    }
                }
                limitRedialTime(pppoe.getId());
                log.info("ppp{} has return , cost {}s", pppoe.getId(), costSec);
                if (ip == null) {
                    shutDown(pppoe);
                }else {
                    pppoe.setOutIP(ip);
                }
                return pppoe;
            }
        };
        FutureTask<PPPOE> futureTask = new FutureTask(callable);
        pool.execute(futureTask);
        return futureTask;
    }

    private void limitRedialTime(String id) {
        Condition condition = conditionList.get(Integer.parseInt(id)-1);
        redialLimitedConditionMap.put(id, condition);
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                try {
                    reDialLock.lock();
                    condition.signalAll();
                    redialLimitedConditionMap.remove(id);
                } finally {
                    reDialLock.unlock();
                }
            }
        };
        timer.schedule(timerTask, TimeUnit.SECONDS.toMillis(dilaGapLimit));
    }

    @Override
    public boolean shutDown(PPPOE pppoe) {
        return shutDown(pppoe.getId());
    }

    @Override
    public boolean reDialup(PPPOE pppoe) {
        shutDown(pppoe);
        dialUp(pppoe);
        return true;
    }

    @Override
    public PPPOE getPPPOE(String pppoeId) {
        PPPOE pppoe;
        String vethName = "eth" + pppoeId;
        Veth veth = vethService.getVeth(vethName);
        if (veth == null) {
            return null;
        }
        pppoe = new PPPOE(pppoeId);
        pppoe.setVeth(veth);
        boolean dialUp = isDialUp(pppoeId);
        if (dialUp) {
            String cmd = "ip route";
            InputStream inputStream = namespaceService.exeCmdInNamespace("ns" + pppoeId, cmd);
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            Pattern pattern2 = Pattern.compile("([\\d\\\\.]+) dev (.*) proto kernel scope link src ([\\d\\\\.]+) ");
            try {
                while ((line = bufferedReader.readLine()) != null) {
                    Matcher matcher2 = pattern2.matcher(line);
                    if (matcher2.matches() != false) {
                        String outIP = matcher2.group(3);
                        String gateWay = matcher2.group(1);
                        String runingOnInterfaceName = matcher2.group(2);
                        pppoe.setOutIP(outIP);
                        pppoe.setGateWay(gateWay);
                        pppoe.setRuningOnInterfaceName(runingOnInterfaceName);
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
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        } else {
            boolean exist = checkConfigFileExist(pppoeId);
            if (!exist) {
                return null;
            }
        }
        pppoe.setAdslPassword(adslAccountList.get(Integer.parseInt(pppoeId) - 1).getAdslPassword());
        pppoe.setAdslUser(adslAccountList.get(Integer.parseInt(pppoeId) - 1).getAdslUser());
        return pppoe;
    }
}
