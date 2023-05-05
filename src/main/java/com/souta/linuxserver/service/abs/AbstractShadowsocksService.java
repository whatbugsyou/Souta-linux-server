package com.souta.linuxserver.service.abs;

import com.souta.linuxserver.proxy.ProxyConfig.ShadowsocksConfig;
import com.souta.linuxserver.entity.Shadowsocks;
import com.souta.linuxserver.entity.prototype.SocksPrototype;
import com.souta.linuxserver.entity.prototype.SocksPrototypeManager;
import com.souta.linuxserver.service.CommandService;
import com.souta.linuxserver.service.NamespaceService;
import com.souta.linuxserver.service.PPPOEService;
import com.souta.linuxserver.service.ShadowsocksService;

public abstract class AbstractShadowsocksService extends AbstractSocksService<Shadowsocks> implements ShadowsocksService {

    protected ShadowsocksConfig config;

    public AbstractShadowsocksService(NamespaceService namespaceService, PPPOEService pppoeService, CommandService commandService, Integer listenPort, ShadowsocksConfig config) {
        super(namespaceService, pppoeService, commandService, listenPort);
        this.config = config;
        initPrototype();
    }

    private void initPrototype() {
        SocksPrototype socks = new Shadowsocks(config.getPassword(), config.getMethod());
        socks.setPort(config.getPort().toString());
        SocksPrototypeManager.registerType(socks);
    }

    @Override
    public Shadowsocks getSocksInstance() {
        return (Shadowsocks) SocksPrototypeManager.getProtoType(Shadowsocks.class);
    }
}
