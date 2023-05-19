package com.souta.linuxserver.proxy;

import com.souta.linuxserver.adsl.ADSL;
import com.souta.linuxserver.dataTansfer.DataTransferManager;
import com.souta.linuxserver.entity.Veth;
import com.souta.linuxserver.line.LineBuildConfig;
import com.souta.linuxserver.service.NamespaceCommandService;
import com.souta.linuxserver.service.NamespaceService;
import com.souta.linuxserver.service.VethService;
import com.souta.linuxserver.service.exception.NamespaceNotExistException;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class ProxyEnvironmentBuilder {
    private final VethService vethService;
    private final NamespaceCommandService commandService;
    private final NamespaceService namespaceService;
    private final DataTransferManager dataTransferManager;
    private final LineBuildConfig lineBuildConfig;

    public ProxyEnvironmentBuilder(VethService vethService, NamespaceCommandService commandService, NamespaceService namespaceService, DataTransferManager dataTransferManager, LineBuildConfig lineBuildConfig) {
        this.vethService = vethService;
        this.commandService = commandService;
        this.namespaceService = namespaceService;
        this.dataTransferManager = dataTransferManager;
        this.lineBuildConfig = lineBuildConfig;
    }

    @PostConstruct
    public void build() {
        buildServerSpace();
    }

    private void buildServerSpace() {
        String serverNamespaceName = lineBuildConfig.getServerNamespaceName();
        namespaceService.createNameSpace(serverNamespaceName);
        String cmd = "ifconfig lo up";
        commandService.execAndWaitForAndCloseIOSteam(cmd, serverNamespaceName);
    }

    public boolean check(String lineId) {
        boolean flag = true;
        //check namespace
        String serverNamespaceName = lineBuildConfig.getServerNamespaceName();
        flag = flag && namespaceService.checkExist(serverNamespaceName);
        if (flag) {
            //check eth
            ADSL adsl = lineBuildConfig.getADSL(lineId);
            String ethernetName = adsl.getEthernetName();
            String serverEthName = lineBuildConfig.getServerEthName(ethernetName);
            try {
                flag = flag & vethService.checkExist(serverEthName, serverNamespaceName);
            } catch (NamespaceNotExistException e) {
                flag = false;
            }
        }
        //check local ip
        //check dataTrans
        return flag;
    }


    public boolean build(String lineId) throws NamespaceNotExistException {
        String serverNamespaceName = lineBuildConfig.getServerNamespaceName();
        namespaceService.createNameSpace(serverNamespaceName);
        ADSL adsl = lineBuildConfig.getADSL(lineId);
        String ethernetName = adsl.getEthernetName();
        Veth veth = vethService.createVeth(ethernetName, lineBuildConfig.getServerEthName(ethernetName), serverNamespaceName);
        vethService.upVeth(veth);
        String listenIp = lineBuildConfig.getListenIp(lineId);
        commandService.execAndWaitForAndCloseIOSteam(String.format("ip addr add %s/24 dev %s", listenIp, veth.getInterfaceName()), serverNamespaceName);
        dataTransferManager.serverTransToPPP(lineId);
        return true;
    }
}
