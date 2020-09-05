package com.souta.linuxserver.service;

import com.souta.linuxserver.entity.ADSL;
import com.souta.linuxserver.entity.PPPOE;
import com.souta.linuxserver.entity.Veth;

import java.util.HashSet;
import java.util.List;
import java.util.concurrent.FutureTask;


public interface PPPOEService {
    PPPOE createPPPOE(String pppoeId,Veth veth);
    boolean checkConfigFileExist(String pppoeId);
    boolean createConifgFile(String pppoeId,String adslUser,String adslPassword);
    FutureTask<PPPOE> dialUp(PPPOE pppoe);
    boolean dialUp(String pppoeId);
    boolean shutDown(PPPOE pppoe);
    boolean reDialup(PPPOE pppoe);
    /**
     *
     * @param pppoeId
     * @return an object of pppoe ,if the pppoe is dialuped.null otherwise.
     */
    PPPOE getPPPOE(String pppoeId);
    boolean isDialUp(PPPOE pppoe);
    boolean isDialUp(String pppoeId);
    boolean shutDown(String pppoeId);
    boolean reDialup(String pppoeId);
    HashSet<String > getDialuppedIdSet();
    List<ADSL> getADSLList();
    String getIP(String pppoeId);
}
