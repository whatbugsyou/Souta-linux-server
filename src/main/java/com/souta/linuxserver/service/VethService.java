package com.souta.linuxserver.service;

import com.souta.linuxserver.entity.Namespace;
import com.souta.linuxserver.entity.Veth;

public interface VethService {
    Veth createVeth(String physicalEthName,String vthName,String namespaceName);
    boolean checkExist(String vethName,String namespaceName);
    boolean checkExist(Veth veth);
    boolean deleteVeth(String vethName,String namespaceName);
    boolean upVeth(Veth veth);
    boolean downVeth(Veth veth);
    boolean moveVethToNamespace(Veth veth, Namespace namespace);
    String getMacAddr(String vethName, String namespaceName);
    Veth getVeth(String vethName);
    boolean checkIsUp(Veth veth);
    boolean checkIsUp(String vethName,String namespaceName);

}
