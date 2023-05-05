package com.souta.linuxserver.proxy;

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
public class ProxyEnvironmentBuilder {
    private final VethService vethService;
    private final CommandService commandService;
    private final NamespaceService namespaceService;
    private final DataTransferManager dataTransferManager;
    private final LineBuildConfig lineBuildConfig;
    private final ProxyService proxyService;

    public ProxyEnvironmentBuilder(VethService vethService, CommandService commandService, NamespaceService namespaceService, DataTransferManager dataTransferManager, LineBuildConfig lineBuildConfig, ProxyService proxyService) {
        this.vethService = vethService;
        this.commandService = commandService;
        this.namespaceService = namespaceService;
        this.dataTransferManager = dataTransferManager;
        this.lineBuildConfig = lineBuildConfig;
        this.proxyService = proxyService;
    }


    @PostConstruct
    public void build() {
        buildServerSpace();
        startProxy();
        dataTransfer();
    }

    private void dataTransfer() {
        Iterator<ADSL> iterator = lineBuildConfig.getADSLIterator();
        int i = 1;
        while (iterator.hasNext()) {
            ADSL adsl = iterator.next();
            String lineId = String.valueOf(i);
            dataTransferManager.serverTransToPPP(lineId);
            i++;
        }
    }

    private void startProxy() {
        Iterator<ADSL> iterator = lineBuildConfig.getADSLIterator();
        int i = 1;
        while (iterator.hasNext()) {
            String lineId = String.valueOf(i++);
            proxyService.startProxy(lineId);
        }
    }

    private void buildServerSpace() {
        String serverNamespaceName = lineBuildConfig.getServerNamespaceName();
        namespaceService.createNameSpace(serverNamespaceName);
        Iterator<ADSL> iterator = lineBuildConfig.getADSLIterator();
        int i = 1;
        while (iterator.hasNext()) {
            ADSL adsl = iterator.next();
            String ethernetName = adsl.getEthernetName();
            try {
                String lineId = String.valueOf(i);
                Veth veth = vethService.createVeth(ethernetName, lineBuildConfig.getServerEthName(ethernetName), serverNamespaceName);
                vethService.upVeth(veth);
                String listenIp = lineBuildConfig.getListenIp(lineId);
                commandService.execAndWaitForAndCloseIOSteam(String.format("ip addr add %s dev %s", listenIp, veth.getInterfaceName()), serverNamespaceName);
                i++;
            } catch (NamespaceNotExistException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public boolean check(String lineId) {
        //check namespace
        String serverNamespaceName = lineBuildConfig.getServerNamespaceName();
        namespaceService.checkExist(serverNamespaceName);
        //check eth
        ADSL adsl = lineBuildConfig.getADSL(lineId);
        String ethernetName = adsl.getEthernetName();
        String serverEthName = lineBuildConfig.getServerEthName(ethernetName);
        try {
            vethService.checkExist(serverEthName, serverNamespaceName);
        } catch (NamespaceNotExistException e) {
            return false;
        }
        //check local ip
        //check dataTrans
        //check proxy
        return isProxyStart(lineId);
    }

    private boolean isProxyStart(String lineId) {
        return proxyService.isProxyStart(lineId);
    }

    public boolean build(String lineId) throws NamespaceNotExistException {
        String serverNamespaceName = lineBuildConfig.getServerNamespaceName();
        namespaceService.createNameSpace(serverNamespaceName);
        ADSL adsl = lineBuildConfig.getADSL(lineId);
        String ethernetName = adsl.getEthernetName();
        Veth veth = vethService.createVeth(ethernetName, lineBuildConfig.getServerEthName(ethernetName), serverNamespaceName);
        vethService.upVeth(veth);
        String listenIp = lineBuildConfig.getListenIp(lineId);
        commandService.execAndWaitForAndCloseIOSteam(String.format("ip addr add %s dev %s", listenIp, veth.getInterfaceName()), serverNamespaceName);
        dataTransferManager.serverTransToPPP(lineId);
        startProxy(lineId);
        return true;
    }

    private void startProxy(String lineId) {
        proxyService.startProxy(lineId);
    }
}
