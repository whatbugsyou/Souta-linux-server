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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PPPOEServiceImpl implements PPPOEService {
    private static final Logger log = LoggerFactory.getLogger(PPPOEService.class);
    private static final String adslAccountFilePath = "/tmp/adsl.txt";
    @Autowired
    private NamespaceService namespaceService;
    @Autowired
    private VethService vethService;
    private static List<ADSL> adslAccount;
    private static HashMap<String, Integer> lastDialSecondGap;
    private static ScheduledExecutorService scheduler;
    private static HashSet<String> isRecordInSecretFile;

    static {
        adslAccount = new ArrayList<>();
        File adslFile = new File(adslAccountFilePath);
        if (adslFile.exists()) {
            FileReader fileReader = null;
            BufferedReader bufferedReader = null;
            try {
                fileReader = new FileReader(adslFile);
                bufferedReader = new BufferedReader(fileReader);
                String line = null;
                String reg = "(.*)----(.*)";
                Pattern compile = Pattern.compile(reg);
                while ((line = bufferedReader.readLine()) != null) {
                    Matcher matcher = compile.matcher(line);
                    if (matcher.matches()) {
                        ADSL adsl = new ADSL(matcher.group(1), matcher.group(2));
                        adslAccount.add(adsl);
                    }
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
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
            scheduler = Executors.newScheduledThreadPool(adslAccount.size());
            log.info("find {} adsl account", adslAccount.size());
        } else {
            log.info("not found {} file ", adslAccountFilePath);
            System.exit(1);
        }

        lastDialSecondGap = new HashMap<>();
        Runnable refreshSecondGap = new Runnable() {
            @Override
            public void run() {
                synchronized (lastDialSecondGap) {
                    for (Map.Entry<String, Integer> entry : lastDialSecondGap.entrySet()) {
                        Integer value = entry.getValue() + 1;
                        lastDialSecondGap.put(entry.getKey(), value);
                        lastDialSecondGap.notifyAll();
                    }
                }
            }
        };
        scheduler.scheduleAtFixedRate(refreshSecondGap, 0, 1, TimeUnit.SECONDS);
        isRecordInSecretFile = new HashSet<>();
    }

    @Override
    public PPPOE createPPPOE(String pppoeId, Veth veth) {
        PPPOE pppoe = null;
        ADSL adsl = adslAccount.get(Integer.parseInt(pppoeId) - 1);
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
        String cmd = "ifconfig |grep ppp";
        String namespaceName = "ns" + pppoeId;
        InputStream inputStream = namespaceService.exeCmdInNamespace(namespaceName, cmd);
        if (inputStream == null) {
            return false;
        }
        return hasOutput(inputStream);
    }

    @Override
    public boolean dialUp(String pppoeId) {
        PPPOE pppoe = getPPPOE(pppoeId);
        dialUp(pppoe);
        return true;
    }

    @Override
    public boolean shutDown(String pppoeId) {
        StringBuilder pid = new StringBuilder();
        String pidCheckCmd = String.format("ps ax|awk '/(ppp%s$)|(ppp%s )/{print $1}'", pppoeId, pppoeId);
        InputStream inputStream = namespaceService.exeCmdInNamespace("", pidCheckCmd);
        if (inputStream != null) {
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            String line = null;
            try {
                while ((line = bufferedReader.readLine()) != null) {
                    pid.append(" " + line);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            namespaceService.exeCmdInNamespace("", "kill -9" + pid.toString());
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
        InputStream inputStream = namespaceService.exeCmdInNamespace("", cmd);
        if (inputStream != null) {
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            String line = null;
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
        return adslAccount;
    }

    @Override
    public boolean isDialUp(PPPOE pppoe) {
        return isDialUp(pppoe.getId());
    }

    private boolean hasOutput(InputStream inputStream) {
        int read = 0;
        try {
            read = inputStream.read();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return read != -1;
    }

    @Override
    public boolean checkConfigFileExist(String pppoeId) {
        String cmd = "ls /etc/sysconfig/network-scripts | grep ifcfg-ppp" + pppoeId + "$";
        InputStream inputStream = namespaceService.exeCmdInNamespace("", cmd);
        int read = 0;
        try {
            read = inputStream.read();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        if (read != -1) {
            return true;
        } else {
            return false;
        }
    }

    @Override
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
        String line = null;
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
            boolean delete = chap.delete();
            boolean b = tmp_file.renameTo(chap);
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

    private boolean createMainConfigFile(String pppoeId, String adslUser) {
        if (checkConfigFileExist(pppoeId)) {
            return true;
        }
        String configFilePath = "/etc/sysconfig/network-scripts/ifcfg-ppp" + pppoeId;
        BufferedWriter cfgfileBufferedWriter = null;
        BufferedReader tmpbufferedReader = null;
        FileWriter fileWriter = null;
        String line = null;
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
        return false;
    }

    @Override
    public FutureTask<PPPOE> dialUp(PPPOE pppoe) {
        Callable<PPPOE> callable = new Callable<PPPOE>() {
            @Override
            public PPPOE call() throws Exception {
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
                    Integer integer;
                    synchronized (lastDialSecondGap) {
                        while ((integer = lastDialSecondGap.get(pppoe.getId())) != null && integer < 10) {
                            lastDialSecondGap.wait();
                        }
                    }
                    log.info("ppp{} start dialing ...", pppoe.getId());
                    namespaceService.exeCmdInNamespace(namespace, ifupCMD);
                    int checkCount = 0;
                    float costSec = 0;
                    float sleepGapSec = 0.5f;
                    int dialEndSec = 60;
                    while (!isDialUp(pppoe)) {
                        checkCount++;
                        costSec = checkCount * sleepGapSec;
                        if (costSec % 10 == 0) {
                            log.info("ppp{} dialing {}s ...", pppoe.getId(), costSec);
                        }
                        if (costSec < dialEndSec) {
                            TimeUnit.MILLISECONDS.sleep((long) (sleepGapSec * 1000));
                        } else {
                            log.info("ppp{} dialing time reach 60s ,shutdown", pppoe.getId());
                            shutDown(pppoe.getId());
                            break;
                        }
                    }
                    lastDialSecondGap.put(pppoe.getId(), 0);
                    log.info("ppp{} has return , cost {}s", pppoe.getId(), costSec);
                    return getPPPOE(pppoe.getId());
                }
            }
        };
        FutureTask<PPPOE> futureTask = new FutureTask(callable);
        new Thread(futureTask).start();
        return futureTask;
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
        PPPOE pppoe = null;
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
            String line = null;
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
        pppoe.setAdslPassword(adslAccount.get(Integer.parseInt(pppoeId) - 1).getAdslPassword());
        pppoe.setAdslUser(adslAccount.get(Integer.parseInt(pppoeId) - 1).getAdslUser());
        return pppoe;
    }

    //deprecated
    private void changePidFileForNamespaceEnviroment(String pppoeId) {
        String toPidPath = "/var/run/ppp0.pid";
        String fromPidPath = "/var/run/pppoe-adsl%s.pid.pppd";
        fromPidPath = String.format(fromPidPath, pppoeId);
        File toPidfile = new File(toPidPath);
        File fromPidFile = new File(fromPidPath);
        FileReader fileReader = null;
        FileWriter fileWriter = null;
        try {
            fileReader = new FileReader(fromPidFile);
            fileWriter = new FileWriter(toPidfile);
            char buf[] = new char[10];
            int len;
            while ((len = fileReader.read(buf)) != -1) {
                fileWriter.write(buf, 0, len);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (fileReader != null) {
                try {
                    fileReader.close();
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


}
