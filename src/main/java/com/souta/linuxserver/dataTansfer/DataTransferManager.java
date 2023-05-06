package com.souta.linuxserver.dataTansfer;

import com.souta.linuxserver.line.LineBuildConfig;
import org.springframework.stereotype.Component;

@Component
public class DataTransferManager {
    private final DataTransfer dataTransfer;
    private final LineBuildConfig lineBuildConfig;

    public DataTransferManager(DataTransfer dataTransfer, LineBuildConfig lineBuildConfig) {
        this.dataTransfer = dataTransfer;
        this.lineBuildConfig = lineBuildConfig;
    }


    public void PPPTransToServer(String lineId) {
        String listenIp = lineBuildConfig.getListenIp(lineId);
        String pppNamespace = lineBuildConfig.getNamespaceName(lineId);
        dataTransfer.dnatPPP0(listenIp, pppNamespace);  // ppp -> (lan) -> server
        dataTransfer.masqueradePPP0(listenIp, pppNamespace);// lan -> ppp
    }

    public void serverTransToPPP(String lineId) {
        String lanIp = lineBuildConfig.getLanIp(lineId);
        String listenIp = lineBuildConfig.getListenIp(lineId);
        String serverNamespaceName = lineBuildConfig.getServerNamespaceName();
        String tableId = String.valueOf(Integer.valueOf(lineId) + 100);
        dataTransfer.nextJumpRouting(listenIp, lanIp, serverNamespaceName, tableId); // listen -> lan
    }

    public void trans(String lineId) {
        PPPTransToServer(lineId);
        serverTransToPPP(lineId);
    }
}
