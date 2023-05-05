package com.souta.linuxserver.service;

import com.souta.linuxserver.entity.PPPOE;

import java.util.HashSet;


public interface PPPOEService {


    boolean checkConfigFileExist(String pppoeId);

    boolean createConfigFile(String pppoeId, String adslUser, String adslPassword, String ethernetName);

    PPPOE getPPPOE(String pppoeId);

    boolean shutDown(String pppoeId);

    boolean isDialUp(String pppoeId);

    /**
     * @return dial-upped id(Number) set ,except null
     */
    HashSet<String> getDialuppedIdSet();

    String getIP(String pppoeId);

    /**
     * start dial process. Once upon the dial is successful, return the ip.
     * The process will last for 1 minute, and then shutdown the dial process.
     *
     * @return ip, otherwise null.
     */

    String dialUp(String pppoeId, String adslUser, String adslPassword, String ethernetName, String namespaceName);
}
