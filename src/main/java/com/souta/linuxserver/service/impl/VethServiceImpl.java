package com.souta.linuxserver.service.impl;

import com.souta.linuxserver.entity.Namespace;
import com.souta.linuxserver.entity.Veth;
import com.souta.linuxserver.service.NamespaceService;
import com.souta.linuxserver.service.VethService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Random;

@Service
public class VethServiceImpl implements VethService {
    private static final Logger log = LoggerFactory.getLogger(VethServiceImpl.class);
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
            if (checkExist(vethName, "")) {
                macAddr = getMacAddr(vethName, "");
            } else {
                macAddr = createMacAddr();
                String cmd = "ip link add link %s address %s %s type macvlan";
                cmd = String.format(cmd, physicalEthName, macAddr, vethName);
                InputStream inputStream = namespaceService.exeCmdInDefaultNamespace(cmd);
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            veth = new Veth(physicalEthName, vethName, macAddr, new Namespace(""));
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
            InputStream inputStream = namespaceService.exeCmdInNamespace(namespace, cmd);
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            try {
                macaddr = bufferedReader.readLine();
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
        InputStream inputStream = namespaceService.exeCmdInNamespace(namespaceName, cmd);
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
        if (read == -1) {
            return false;
        } else {
            return true;
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
            InputStream inputStream = namespaceService.exeCmdInNamespace(namespace, cmd);
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
            if (read == -1) {
                return false;
            } else {
                return true;
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
            namespaceService.exeCmdInNamespace(new Namespace(namespaceName), cmd);
            return true;
        }
    }

    @Override
    public boolean upVeth(Veth veth) {
        boolean exist = checkExist(veth);
        if (exist) {
            Namespace namespace = veth.getNamespace();
            String cmd = "ifconfig " + veth.getInterfaceName() + " up";
            namespaceService.exeCmdInNamespace(namespace, cmd);
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
            namespaceService.exeCmdInNamespace(namespace, cmd);
            return true;
        }
        return false;
    }

    @Override
    public boolean moveVethToNamespace(Veth veth, Namespace namespace) {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (checkExist(veth) && namespaceService.checkExist(namespace)) {
            String cmd = "ip link set %s netns %s ";
            cmd = String.format(cmd, veth.getInterfaceName(), namespace.getName());
            InputStream inputStream = namespaceService.exeCmdInNamespace(veth.getNamespace(), cmd);
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
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
