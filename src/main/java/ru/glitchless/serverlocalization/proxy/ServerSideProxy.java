package ru.glitchless.serverlocalization.proxy;

import java.io.File;

public class ServerSideProxy implements ISideProxy {
    @Override
    public String getMinecraftVersion() {
        return "1.7.10";
    }

    @Override
    public File getFile(String path) {
        return new File(path);
    }
}
