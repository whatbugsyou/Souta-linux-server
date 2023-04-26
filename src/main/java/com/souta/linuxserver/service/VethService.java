package com.souta.linuxserver.service;

import com.souta.linuxserver.entity.Veth;
import com.souta.linuxserver.service.exception.NamespaceNotExistException;

public interface VethService {

    Veth createVeth(String physicalEthName, String vethName, String namespaceName) throws NamespaceNotExistException;

    boolean checkExist(String vethName, String namespaceName) throws NamespaceNotExistException;

    boolean checkExist(Veth veth) throws NamespaceNotExistException;

    boolean deleteVeth(String vethName, String namespaceName);

    boolean upVeth(Veth veth);

    boolean downVeth(Veth veth);

    String getMacAddr(String vethName, String namespaceName) throws NamespaceNotExistException;

    boolean checkIsUp(Veth veth) throws NamespaceNotExistException;

    boolean checkIsUp(String vethName, String namespaceName) throws NamespaceNotExistException;

}
