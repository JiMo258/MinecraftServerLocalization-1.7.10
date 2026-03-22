package ru.glitchless.serverlocalization.proxy;

import java.io.File;

public class ClientSideProxy implements ISideProxy {
    @Override
    public String getMinecraftVersion() {
        try {
            Class<?> minecraftClass = Class.forName("net.minecraft.client.Minecraft");
            Object mcInstance = minecraftClass.getMethod("getMinecraft").invoke(null);
            return (String) minecraftClass.getMethod("getVersion").invoke(mcInstance);
        } catch (Exception e) {
            return "1.7.10";
        }
    }

    @Override
    public File getFile(String path) {
        try {
            Class<?> minecraftClass = Class.forName("net.minecraft.client.Minecraft");
            Object mcInstance = minecraftClass.getMethod("getMinecraft").invoke(null);
            Object mcDataDir = minecraftClass.getMethod("getMcDataDir").invoke(mcInstance);
            return new File((File) mcDataDir, path);
        } catch (Exception e) {
            return new File(path);
        }
    }
}
