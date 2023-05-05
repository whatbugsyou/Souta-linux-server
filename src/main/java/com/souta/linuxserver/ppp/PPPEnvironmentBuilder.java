package com.souta.linuxserver.ppp;

import com.souta.linuxserver.adsl.ADSL;
import com.souta.linuxserver.dataTansfer.DataTransferManager;
import com.souta.linuxserver.entity.Veth;
import com.souta.linuxserver.line.LineBuildConfig;
import com.souta.linuxserver.service.CommandService;
import com.souta.linuxserver.service.NamespaceService;
import com.souta.linuxserver.service.VethService;
import com.souta.linuxserver.service.exception.NamespaceNotExistException;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Iterator;

@Component
public class PPPEnvironmentBuilder {
    private final VethService vethService;
    private final CommandService commandService;
    private final NamespaceService namespaceService;
    private final DataTransferManager dataTransferManager;
    private final LineBuildConfig lineBuildConfig;

    public PPPEnvironmentBuilder(VethService vethService, CommandService commandService, NamespaceService namespaceService, DataTransferManager dataTransferManager, LineBuildConfig lineBuildConfig) {
        this.vethService = vethService;
        this.commandService = commandService;
        this.namespaceService = namespaceService;
        this.dataTransferManager = dataTransferManager;
        this.lineBuildConfig = lineBuildConfig;
    }


    @PostConstruct
    public void build() {
        buildPPPSpace();
        dataTransfer();
    }

    private void dataTransfer() {
        Iterator<ADSL> iterator = lineBuildConfig.getADSLIterator();
        int i = 1;
        while (iterator.hasNext()) {
            ADSL adsl = iterator.next();
            String lineId = String.valueOf(i);
            dataTransferManager.PPPTransToServer(lineId);
            i++;
        }
    }

    private void buildPPPSpace() {
        Iterator<ADSL> iterator = lineBuildConfig.getADSLIterator();
        int i = 1;
        while (iterator.hasNext()) {
            ADSL adsl = iterator.next();
            String ethernetName = adsl.getEthernetName();
            try {
                String lineId = String.valueOf(i);
                String namespaceName = lineBuildConfig.getNamespaceName(lineId);
                String vethName = lineBuildConfig.getVethName(lineId);
                Veth veth = vethService.createVeth(ethernetName, vethName, namespaceName);
                vethService.upVeth(veth);
                String listenIp = lineBuildConfig.getLanIp(lineId);
                commandService.execAndWaitForAndCloseIOSteam(String.format("ip addr add %s dev %s", listenIp, vethName), namespaceName);
                i++;
            } catch (NamespaceNotExistException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public boolean check(String lineId) {
        //check namespace
        String namespaceName = lineBuildConfig.getNamespaceName(lineId);
        namespaceService.checkExist(namespaceName);
        //check eth
        ADSL adsl = lineBuildConfig.getADSL(lineId);
        String ethernetName = adsl.getEthernetName();
        String serverEthName = lineBuildConfig.getServerEthName(ethernetName);
        try {
            vethService.checkExist(serverEthName, namespaceName);
        } catch (NamespaceNotExistException e) {
            return false;
        }
        //check local ip
        //check dataTrans
        return true;
    }


    public boolean build(String lineId) throws NamespaceNotExistException {
        ADSL adsl = lineBuildConfig.getADSL(lineId);
        String ethernetName = adsl.getEthernetName();
        String namespaceName = lineBuildConfig.getNamespaceName(lineId);
        Veth veth = vethService.createVeth(ethernetName, lineBuildConfig.getServerEthName(ethernetName), namespaceName);
        vethService.upVeth(veth);
        String listenIp = lineBuildConfig.getListenIp(lineId);
        commandService.execAndWaitForAndCloseIOSteam(String.format("ip addr add %s dev %s", listenIp, veth.getInterfaceName()), namespaceName);
        dataTransferManager.PPPTransToServer(lineId);
        return true;
    }

}
