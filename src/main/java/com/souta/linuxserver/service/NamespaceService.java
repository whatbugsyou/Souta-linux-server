package com.souta.linuxserver.service;

import com.souta.linuxserver.entity.Namespace;

import java.io.InputStream;
import java.util.List;

public interface NamespaceService {
    /**
     *
     * @param name -namespace name
     * @return true if the given namespace is exist, false otherwise.
     */
    boolean checkExist(String name);

    /**
     *
     * @param namespace -Namespace Object
     * @return true if the given namespace is exist, false otherwise.
     */
    boolean checkExist(Namespace namespace);

    /**
     *
     * @return a list of existing Namespace
     */
    List<Namespace> getAllNameSpace();

    Namespace getNameSpace(String name);

    /**
     *
     * @param name --namespace name
     * @return an object of Namespace .if the namespace is existing ,will not create a new namespace.
     */
    Namespace createNameSpace(String name);

    /**
     *
     * @param name --namespace name
     * @return true . if the namespace is not existing ,return true directly.
     */
    boolean deleteNameSpace(String name);

    /**
     *
     * @param namespace --an Object of namespace
     * @param cmd --  bash commond
     * @return an Objcet of InputStream , relating to the cmd exeuting output.
     */
    InputStream exeCmdInNamespace(Namespace namespace, String cmd);

    /**
     *
     * @param namespace - namespace name
     * @param cmd --  bash command
     * @return an Objcet of InputStream , relating to the cmd exeuting output.
     */
    InputStream exeCmdInNamespace(String namespace, String cmd) ;

    InputStream exeCmdInDefaultNamespace(String cmd) ;


}
