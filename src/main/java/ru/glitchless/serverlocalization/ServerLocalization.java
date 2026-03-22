package ru.glitchless.serverlocalization;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;
import ru.glitchless.serverlocalization.config.ServerLocalizationConfig;
import ru.glitchless.serverlocalization.lang.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Mod(modid = ServerLocalization.MODID,
        name = ServerLocalization.NAME,
        version = ServerLocalization.VERSION,
        acceptableRemoteVersions = "*")
public class ServerLocalization {
    public static final String MODID = "serverlocalization";
    public static final String NAME = "Server Localization";
    public static final String VERSION = "1.0";

    public static Logger logger;
    private static List<ILangChanger> langChangerList = new ArrayList<>();

    public static void addLangChanger(ILangChanger langChanger) {
        langChangerList.add(langChanger);
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        ServerLocalizationConfig.init(event.getSuggestedConfigurationFile());
        addLangChanger(new VanillaChanger());
        addLangChanger(new OtherModChanger());
        addLangChanger(new TxLoaderChanger());
        addLangChanger(new GTNHLocalization());
        addLangChanger(new IC2LangChanger());
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        final String lang = ServerLocalizationConfig.lang;
        for (ILangChanger langChanger : langChangerList) {
            try {
                logger.info("Applying lang for " + langChanger.getClass().getSimpleName());
                langChanger.changeLanguage(logger, lang);
            } catch (LangNotFoundException ex) {
                logger.error("Failed " + langChanger.getClass().getSimpleName(), ex);
            }
        }
        
        // Test that translations were actually injected
        try {
            Class<?> stringTranslateClass = Class.forName("net.minecraft.util.StringTranslate");
            java.lang.reflect.Field instanceField = stringTranslateClass.getDeclaredField("field_74817_a");
            instanceField.setAccessible(true);
            Object stringTranslateInstance = instanceField.get(null);
            
            java.lang.reflect.Field languageListField = stringTranslateClass.getDeclaredField("field_74816_c");
            languageListField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, String> languageList = (Map<String, String>) languageListField.get(stringTranslateInstance);
            
            logger.info("=== Translation Test ===");
            logger.info("Total translations in StringTranslate: " + languageList.size());
            
            String[] testKeys = {"death.fell.accident.generic", "book.pageIndicator", "entity.LavaSlime.name", "dreamcraft.welcome.welcome"};
            for (String key : testKeys) {
                String translated = languageList.get(key);
                logger.info("Test '" + key + "': " + (translated != null ? translated : "NOT FOUND"));
            }
            logger.info("If you see Chinese above, server localization is working!");
        } catch (Exception e) {
            logger.error("Failed to test translations", e);
        }
    }
}
