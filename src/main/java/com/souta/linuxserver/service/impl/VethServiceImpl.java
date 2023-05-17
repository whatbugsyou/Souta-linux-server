package com.souta.linuxserver.service.impl;

import com.souta.linuxserver.entity.Namespace;
import com.souta.linuxserver.entity.Veth;
import com.souta.linuxserver.service.NamespaceCommandService;
import com.souta.linuxserver.service.NamespaceService;
import com.souta.linuxserver.service.VethService;
import com.souta.linuxserver.service.exception.EthernetNotExistException;
import com.souta.linuxserver.service.exception.NamespaceNotExistException;
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
    private NamespaceCommandService commandService;
    @Autowired
    private NamespaceService namespaceService;

    @Override
    public Veth createVeth(String physicalEthName, String vethName, String namespaceName) throws NamespaceNotExistException {
        Veth veth;
        String macAddr;
        if (!checkExist(vethName, namespaceName)) {
            if (checkExist(vethName, Namespace.DEFAULT_NAMESPACE.getName())) {
                macAddr = getMacAddr(vethName, Namespace.DEFAULT_NAMESPACE.getName());
            } else {
                macAddr = createMacAddr();
                String cmd = "ip link add link %s address %s %s type macvlan mode bridge";
                cmd = String.format(cmd, physicalEthName, macAddr, vethName);
                commandService.execAndWaitForAndCloseIOSteam(cmd, Namespace.DEFAULT_NAMESPACE.getName());
            }
            veth = new Veth(physicalEthName, vethName, macAddr, Namespace.DEFAULT_NAMESPACE);
            moveVethToNamespace(veth, namespaceName);
        } else {
            macAddr = getMacAddr(vethName, namespaceName);
            veth = new Veth(physicalEthName, vethName, macAddr, namespaceService.getNameSpace(namespaceName));
        }
        return veth;
    }


    public String getMacAddr(String vethName, String namespaceName) throws NamespaceNotExistException {
        String macaddr = null;
        if (checkExist(vethName, namespaceName)) {
            Namespace namespace = namespaceService.getNameSpace(namespaceName);
            String cmd = "cat /sys/class/net/%s/address";// 00:e5:5d:d3:7d:01
            cmd = String.format(cmd, vethName);
            Process process = commandService.exec(cmd, namespace.getName());
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
    public boolean checkIsUp(Veth veth) throws NamespaceNotExistException {
        return checkIsUp(veth.getInterfaceName(), veth.getNamespace().getName());
    }

    @Override
    public boolean checkIsUp(String vethName, String namespaceName) throws NamespaceNotExistException {
        Namespace namespace = namespaceService.getNameSpace(namespaceName);
        String cmd = "ifconfig " + vethName + " |grep inet6";
        Process process = commandService.exec(cmd, namespace.getName());
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
    public boolean checkExist(String vethName, String namespaceName) throws NamespaceNotExistException {
        Namespace namespace = namespaceService.getNameSpace(namespaceName);
        String cmd = "ls /sys/class/net/|grep " + vethName + "$";
        Process process = commandService.exec(cmd, namespace.getName());
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
    public boolean checkExist(Veth veth) throws NamespaceNotExistException {
        return checkExist(veth.getInterfaceName(), veth.getNamespace().getName());
    }

    @Override
    public boolean deleteVeth(String vethName, String namespaceName) {
        boolean exist = false;
        try {
            exist = checkExist(vethName, namespaceName);
        } catch (NamespaceNotExistException e) {

        }
        if (exist) {
            String cmd = "ip link delete " + vethName + " type macvlan  mode bridge";
            commandService.execAndWaitForAndCloseIOSteam(cmd, namespaceName);
        }
        return true;
    }

    @Override
    public boolean upVeth(Veth veth) {
        try {
            assertVethExist(veth);
            Namespace namespace = veth.getNamespace();
            String cmd = "ifconfig " + veth.getInterfaceName() + " up";
            Process process = commandService.execAndWaitForAndCloseIOSteam(cmd, namespace.getName());
            return process.exitValue() == 0;
        } catch (EthernetNotExistException e) {
            log.error(e.getMessage());
        } catch (NamespaceNotExistException e) {
            log.error(e.getMessage());
        }
        return false;
    }

    private void assertVethExist(Veth veth) throws EthernetNotExistException, NamespaceNotExistException {
        if (!checkExist(veth)) {
            throw new EthernetNotExistException(veth.getNamespace() + ":" + veth.getInterfaceName());
        }
    }

    @Override
    public boolean downVeth(Veth veth) {
        try {
            boolean exist = checkExist(veth);
            if (exist) {
                Namespace namespace = veth.getNamespace();
                String cmd = "ifconfig " + veth.getInterfaceName() + " down";
                Process process = commandService.execAndWaitForAndCloseIOSteam(cmd, namespace.getName());
                return process.exitValue() == 0;
            }
        } catch (NamespaceNotExistException e) {
            log.error(e.getMessage());
        }
        return false;
    }


    public boolean moveVethToNamespace(Veth veth, String namespaceName) throws NamespaceNotExistException {
        Namespace nameSpace = namespaceService.getNameSpace(namespaceName);
        if (checkExist(veth)) {
            String cmd = "ip link set %s netns %s ";
            cmd = String.format(cmd, veth.getInterfaceName(), namespaceName);
            commandService.execAndWaitForAndCloseIOSteam(cmd, Namespace.DEFAULT_NAMESPACE.getName());
            veth.setNamespace(nameSpace);
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
