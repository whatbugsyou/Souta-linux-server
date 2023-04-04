package com.souta.linuxserver.service.impl;

import com.souta.linuxserver.entity.Namespace;
import com.souta.linuxserver.entity.Veth;
import com.souta.linuxserver.service.CommandService;
import com.souta.linuxserver.service.NamespaceService;
import com.souta.linuxserver.service.VethService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.Random;

@Service
public class VethServiceImpl implements VethService {
    private static final Logger log = LoggerFactory.getLogger(VethServiceImpl.class);
    @Autowired
    private CommandService commandService;
    @Autowired
    private NamespaceService namespaceService;

    @Override
    public Veth createVeth(String physicalEthName, String vethName, String namespaceName) {
        Namespace namespace = namespaceService.getNameSpace(namespaceName);
        Veth veth;
        if (namespace == null) {
            namespace = namespaceService.createNameSpace(namespaceName);
        }
        boolean vethExistInNamespace = checkExist(vethName, namespaceName);
        String macAddr;
        if (!vethExistInNamespace) {
            if (checkExist(vethName, Namespace.DEFAULT_NAMESPACE.getName())) {
                macAddr = getMacAddr(vethName, Namespace.DEFAULT_NAMESPACE.getName());
            } else {
                macAddr = createMacAddr();
                String cmd = "ip link add link %s address %s %s type macvlan";
                cmd = String.format(cmd, physicalEthName, macAddr, vethName);
                commandService.exeCmdInDefaultNamespaceAndCloseIOStream(cmd);
            }
            veth = new Veth(physicalEthName, vethName, macAddr, Namespace.DEFAULT_NAMESPACE);
            moveVethToNamespace(veth, namespace);
        } else {
            macAddr = getMacAddr(vethName, namespaceName);
            veth = new Veth(physicalEthName, vethName, macAddr, new Namespace(namespaceName));
        }
        return veth;
    }


    public String getMacAddr(String vethName, String namespaceName) {
        String macaddr = null;
        if (checkExist(vethName, namespaceName)) {
            Namespace namespace = new Namespace(namespaceName);
            String cmd = "cat /sys/class/net/%s/address";// 00:e5:5d:d3:7d:01
            cmd = String.format(cmd, vethName);
            Process process = commandService.exeCmdInNamespace(namespace.getName(), cmd);
            try (InputStream inputStream = process.getInputStream();
                 OutputStream outputStream = process.getOutputStream();
                 InputStream errorStream = process.getErrorStream();
                 InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                 BufferedReader bufferedReader = new BufferedReader(inputStreamReader)
            ) {
                macaddr = bufferedReader.readLine();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return macaddr;
    }

    @Override
    public Veth getVeth(String vethName) {
        String namespaceName = Namespace.DEFAULT_PREFIX + vethName.substring(Veth.DEFAULT_PREFIX.length());
        boolean exist = checkExist(vethName, namespaceName);
        if (!exist) {
            return null;
        } else {
            String macAddr = getMacAddr(vethName, namespaceName);
            return new Veth(null, vethName, macAddr, new Namespace(namespaceName));
        }
    }

    @Override
    public boolean checkIsUp(Veth veth) {
        return checkIsUp(veth.getInterfaceName(), veth.getNamespace().getName());
    }

    @Override
    public boolean checkIsUp(String vethName, String namespaceName) {
        String cmd = "ifconfig " + vethName + " |grep inet6";
        Process process = commandService.exeCmdWithNewSh(namespaceName, cmd);
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
    public boolean checkExist(String vethName, String namespaceName) {
        boolean exist = namespaceService.checkExist(namespaceName);
        if (!exist) {
            return false;
        } else {
            Namespace namespace = new Namespace(namespaceName);
            String cmd = "ls /sys/class/net/|grep " + vethName + "$";
            Process process = commandService.exeCmdWithNewSh(namespace.getName(), cmd);
            try (InputStream inputStream = process.getInputStream();
                 OutputStream outputStream = process.getOutputStream();
                 InputStream errorStream = process.getErrorStream()
            ) {
                return inputStream.read() != -1;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public boolean checkExist(Veth veth) {
        return checkExist(veth.getInterfaceName(), veth.getNamespace().getName());
    }

    @Override
    public boolean deleteVeth(String vethName, String namespaceName) {
        boolean exist = checkExist(vethName, namespaceName);
        if (!exist) {
            return true;
        } else {
            String cmd = "ip link delete " + vethName + " type macvlan";
            commandService.execCmdAndWaitForAndCloseIOSteam(cmd, false, namespaceName);
            return true;
        }
    }

    @Override
    public boolean upVeth(Veth veth) {
        boolean exist = checkExist(veth);
        if (exist) {
            Namespace namespace = veth.getNamespace();
            String cmd = "ifconfig " + veth.getInterfaceName() + " up";
            commandService.execCmdAndWaitForAndCloseIOSteam(cmd, false, namespace.getName());
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean downVeth(Veth veth) {
        boolean exist = checkExist(veth);
        if (exist) {
            Namespace namespace = veth.getNamespace();
            String cmd = "ifconfig " + veth.getInterfaceName() + " down";
            commandService.execCmdAndWaitForAndCloseIOSteam(cmd, false, namespace.getName());
            return true;
        }
        return false;
    }

    @Override
    public boolean moveVethToNamespace(Veth veth, Namespace namespace) {
        if (checkExist(veth) && namespaceService.checkExist(namespace)) {
            String cmd = "ip link set %s netns %s ";
            cmd = String.format(cmd, veth.getInterfaceName(), namespace.getName());
            commandService.execCmdAndWaitForAndCloseIOSteam(cmd, false, Namespace.DEFAULT_NAMESPACE.getName());
            veth.setNamespace(namespace);
            return true;
        } else {
            return false;
        }
    }

    /**
     * create a mac address by random
     *
     * @return random mac address
     */
    private String createMacAddr() {
        StringBuilder mac = new StringBuilder();
        mac.append("00:");
        char[] ori = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
        Random random = new Random();
        for (int i = 3; i <= 12; i++) {
            int a = random.nextInt(16);
            if (i == 3) {
                a = a % 3;
            }
            mac.append(ori[a]);
            if (i % 2 == 0) {
                mac.append(':');
            }
        }
        return mac.substring(0, mac.length() - 1);
    }
}
