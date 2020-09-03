package com.souta.linuxserver.service.impl;

import com.souta.linuxserver.controller.PPPOEController;
import com.souta.linuxserver.entity.Namespace;
import com.souta.linuxserver.service.NamespaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class NamespaceServiceImpl implements NamespaceService {
    private static final Logger log = LoggerFactory.getLogger(NamespaceServiceImpl.class);
    private Runtime runtime = Runtime.getRuntime();

    @Override
    public boolean checkExist(String name) {
        if (name.equals("")) return true;
        String cmd = "ls /var/run/netns/ |grep " + name + "$";
        InputStream inputStream = exeCmdInNamespace("", cmd);
        int read = 0;
        try {
            read = inputStream.read();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        if (read != -1) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean checkExist(Namespace namespace) {
        if (namespace == null) {
            return false;
        } else {
            return checkExist(namespace.getName());
        }

    }

    @Override
    public List<Namespace> getAllNameSpace() {
        String cmd = "ip netns list";
        InputStream inputStream = exeCmdInNamespace(new Namespace(""), cmd);
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        String line = null;
        String pattern = "(.*?) .*";
        Pattern compile = Pattern.compile(pattern);
        ArrayList<Namespace> namespaces = new ArrayList<>();
        try {
            while ((line = bufferedReader.readLine()) != null) {
                Matcher matcher = compile.matcher(line);
                if (matcher.matches()) {
                    Namespace namespace = new Namespace(matcher.group(1));
                    namespaces.add(namespace);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return namespaces;
    }

    @Override
    public Namespace getNameSpace(String name) {
        boolean exist = checkExist(name);
        if (exist) {
            return new Namespace(name);
        }
        return null;
    }

    @Override
    public Namespace createNameSpace(String name) {
        boolean exist = checkExist(name);
        if (!exist) {
            String cmd = "ip netns add " + name;
            exeCmdInNamespace(new Namespace(""), cmd);
        }
        Namespace namespace = new Namespace(name);
        return namespace;
    }

    @Override
    public boolean deleteNameSpace(String name) {
        boolean exist = checkExist(name);
        if (exist) {
            String cmd = "ip netns del " + name;
            exeCmdInNamespace(new Namespace(""), cmd);
        }
        return true;
    }

    @Override
    public InputStream exeCmdInNamespace(Namespace namespace, String cmd) {
        return exeCmdInNamespace(namespace.getName(), cmd);
    }

    public InputStream exeCmdInNamespace(String namespace, String cmd) {
        if (!namespace.equals("")) {
            if (!checkExist(namespace)) {
                return null;
            } else {
                cmd = "ip netns exec " + namespace + " " + cmd;
            }
        }
        String[] cmdprep = new String[]{"/bin/sh", "-c", cmd};
        Process exec = null;
        try {
            exec = runtime.exec(cmdprep);
        } catch (IOException e) {
            e.printStackTrace();
        }
        assert exec != null;
        return exec.getInputStream();
    }

}
