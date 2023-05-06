package com.souta.linuxserver.dataTansfer;

import com.souta.linuxserver.service.CommandService;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

@Component
public class DataTransfer {
    private final CommandService commandService;

    public DataTransfer(CommandService commandService) {
        this.commandService = commandService;
    }

    public void nextJumpRouting(String fromIp, String dstIp, String namespaceName, String tableId) {
        boolean isExist_Table = false;
        try (FileReader fileReader = new FileReader("/etc/iproute2/rt_tables");
             BufferedReader bufferedReader = new BufferedReader(fileReader);
        ) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                if (line.startsWith(tableId)) {
                    isExist_Table = true;
                    break;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        String cmd;
        if (!isExist_Table) {
            cmd = String.format("echo \"%s from%s\" >> /etc/iproute2/rt_tables", tableId, tableId);
            commandService.execAndWaitForAndCloseIOSteam(cmd, namespaceName);
        }
        cmd = String.format("ip route add default via %s src %s", dstIp, fromIp);
        commandService.execAndWaitForAndCloseIOSteam(cmd, namespaceName);
        cmd = String.format("ip rule add from %s table from%s", fromIp, tableId);
        commandService.execAndWaitForAndCloseIOSteam(cmd, namespaceName);
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
