package com.souta.linuxserver.service;

import com.souta.linuxserver.entity.Namespace;

import java.util.List;

public interface NamespaceService {
    /**
     * @param name -namespace name
     * @return true if the given namespace exists, false otherwise.
     */
    boolean checkExist(String name);

    /**
     * @param namespace -Namespace Object
     * @return true if the given namespace exists, false otherwise.
     */
    boolean checkExist(Namespace namespace);

    /**
     * @return a list of existing Namespace
     */
    List<Namespace> getAllNameSpace();

    Namespace getNameSpace(String name);

    /**
     * @param name --namespace name
     * @return an object of Namespace .if the namespace exists,will not create a new namespace.
     */
    Namespace createNameSpace(String name);

    /**
     * @param name --namespace name
     * @return true . if the namespace does not exist ,return true directly.
     */
    boolean deleteNameSpace(String name);

    /**
     * @param namespace --an Object of namespace
     * @param cmd       --  bash commond
     * @return Process
     */
    Process exeCmdInNamespace(Namespace namespace, String cmd);

    /**
     * @param namespace --  namespace name
     * @param cmd       --  bash command
     * @return Process
     */
    Process exeCmdInNamespace(String namespace, String cmd);


    /**
     * @param cmd --  bash command
     * @return Process
     */
    Process exeCmdInDefaultNamespace(String cmd);

    void exeCmdInDefaultNamespaceAndCloseIOStream(String cmd);

    void exeCmdAndCloseIOStream(Namespace namespace, String cmd);


}
