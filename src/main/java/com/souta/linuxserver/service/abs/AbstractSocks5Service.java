package com.souta.linuxserver.service.abs;

import com.souta.linuxserver.config.LineConfig.Socks5Config;
import com.souta.linuxserver.entity.Socks5;
import com.souta.linuxserver.entity.prototype.SocksPrototype;
import com.souta.linuxserver.entity.prototype.SocksPrototypeManager;
import com.souta.linuxserver.service.CommandService;
import com.souta.linuxserver.service.NamespaceService;
import com.souta.linuxserver.service.PPPOEService;
import com.souta.linuxserver.service.Socks5Service;

public abstract class AbstractSocks5Service extends AbstractSocksService<Socks5> implements Socks5Service {

    protected Socks5Config socks5Config;

    public AbstractSocks5Service(NamespaceService namespaceService, PPPOEService pppoeService, CommandService commandService, Integer listenPort, Socks5Config socks5Config) {
        super(namespaceService, pppoeService, commandService, listenPort);
        this.socks5Config = socks5Config;
        initPrototype();
    }

    private void initPrototype() {
        SocksPrototype socks = new Socks5(socks5Config.getUsername(), socks5Config.getUsername());
        socks.setPort(socks5Config.getPort().toString());
        SocksPrototypeManager.registerType(socks);
    }

    @Override
    public Socks5 getSocksInstance() {
        return (Socks5) SocksPrototypeManager.getProtoType(Socks5.class);
    }
}
