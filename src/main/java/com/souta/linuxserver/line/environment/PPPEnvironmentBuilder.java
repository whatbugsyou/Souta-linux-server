package com.souta.linuxserver.line.environment;

import com.souta.linuxserver.adsl.ADSL;
import com.souta.linuxserver.entity.Veth;
import com.souta.linuxserver.line.LineBuildConfig;
import com.souta.linuxserver.service.NamespaceCommandService;
import com.souta.linuxserver.service.NamespaceService;
import com.souta.linuxserver.service.VethService;
import com.souta.linuxserver.service.exception.NamespaceNotExistException;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Iterator;

@Component
public class PPPEnvironmentBuilder {
    private final VethService vethService;
    private final NamespaceCommandService commandService;
    private final NamespaceService namespaceService;
    private final LineDataTransferManager lineDataTransferManager;
    private final LineBuildConfig lineBuildConfig;

    public PPPEnvironmentBuilder(VethService vethService, NamespaceCommandService commandService, NamespaceService namespaceService, LineDataTransferManager lineDataTransferManager, LineBuildConfig lineBuildConfig) {
        this.vethService = vethService;
        this.commandService = commandService;
        this.namespaceService = namespaceService;
        this.lineDataTransferManager = lineDataTransferManager;
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
            lineDataTransferManager.PPPTransToServer(lineId);
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
                namespaceService.createNameSpace(namespaceName);
                Veth veth = vethService.createVeth(ethernetName, vethName, namespaceName);
                vethService.upVeth(veth);
                String listenIp = lineBuildConfig.getVethLan(lineId);
                commandService.execAndWaitForAndCloseIOSteam(String.format("ip addr add %s/24 dev %s", listenIp, vethName), namespaceName);
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
        //TODO check local ip
        //TODO check dataTrans
        return true;
    }


    public boolean build(String lineId) throws NamespaceNotExistException {
        ADSL adsl = lineBuildConfig.getADSL(lineId);
        String ethernetName = adsl.getEthernetName();
        String namespaceName = lineBuildConfig.getNamespaceName(lineId);
        Veth veth = vethService.createVeth(ethernetName, lineBuildConfig.getVethName(lineId), namespaceName);
        vethService.upVeth(veth);
        String vethLan = lineBuildConfig.getVethLan(lineId);
        commandService.execAndWaitForAndCloseIOSteam(String.format("ip addr add %s/24 dev %s", vethLan, veth.getInterfaceName()), namespaceName);
        lineDataTransferManager.PPPTransToServer(lineId);
        return true;
    }

}
