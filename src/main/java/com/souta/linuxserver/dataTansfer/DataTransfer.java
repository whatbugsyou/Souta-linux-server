package com.souta.linuxserver.dataTansfer;

import com.souta.linuxserver.service.CommandService;
import org.springframework.stereotype.Component;

@Component
public class DataTransfer {
    private final CommandService commandService;

    public DataTransfer(CommandService commandService) {
        this.commandService = commandService;
    }

    public void nextJump(String fromIp, String dstIp, String namespaceName) {
        //TODO test
        String cmd = String.format("ip route add default via %s src %s", dstIp, fromIp);
        commandService.exec(cmd, namespaceName);
    }

    public void dnatPPP0(String dstIp, String namespaceName) {
        String cmd = String.format("iptables -t nat -A PREROUTING -i ppp0 -j DNAT --to %s", dstIp);
        commandService.execAndWaitForAndCloseIOSteam(cmd, namespaceName);
    }

    public void masqueradePPP0(String srcIp, String namespaceName) {
        commandService.execAndWaitForAndCloseIOSteam("sysctl -w net.ipv4.ip_forward=1", namespaceName);
        String cmd = String.format("iptables -t nat -A POSTROUTING -s %s -o ppp0 -j MASQUERADE", srcIp);
        commandService.execAndWaitForAndCloseIOSteam(cmd, namespaceName);
    }

}
