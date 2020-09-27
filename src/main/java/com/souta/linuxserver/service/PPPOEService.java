package com.souta.linuxserver.service;

import com.souta.linuxserver.entity.ADSL;
import com.souta.linuxserver.entity.PPPOE;
import com.souta.linuxserver.entity.Veth;

import java.util.HashSet;
import java.util.List;
import java.util.concurrent.FutureTask;


public interface PPPOEService {

    /**
     * create an object of PPPOE . include creating config file ,veth ,namespace .
     *
     * @param pppoeId
     * @return an object of PPPOE, otherwise null if no ADSL account available or create veth error.
     */
    PPPOE createPPPOE(String pppoeId);

    PPPOE createPPPOE(String pppoeId, Veth veth);

    boolean checkConfigFileExist(String pppoeId);

    /**
     * start dialing and checking .Once upon the dial is successful ,return the pppoe with ip. The checking process will last  for 1 minute if dial is not successful ,and then shutdown the dial process.
     *
     * @param pppoe
     * @return FutureTask<PPPOE> ,if dial success , checking ip exits about the dial,will get an object of PPPOE with exact ip ,otherwise null ip.
     */
    FutureTask<PPPOE> dialUp(PPPOE pppoe);

    FutureTask<PPPOE> dialUp(String pppoeId);

    /**
     * shut down the ADSL no matter what the ADSL is in the state of .
     *
     * @param pppoe
     * @return
     */
    boolean shutDown(PPPOE pppoe);

    boolean shutDown(String pppoeId);

    boolean reDialup(PPPOE pppoe);

    /**
     * @param pppoeId
     * @return an object of pppoe no matter whether the ADSL is dial-upped or not.
     */
    PPPOE getPPPOE(String pppoeId);

    boolean isDialUp(PPPOE pppoe);

    boolean isDialUp(String pppoeId);


    boolean reDialup(String pppoeId);

    /**
     * @return dial-upped id(Number) set ,except null
     */
    HashSet<String> getDialuppedIdSet();

    List<ADSL> getADSLList();

    String getIP(String pppoeId);
}
