package ru.glitchless.serverlocalization.lang;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class OtherModChanger implements ILangChanger {
    private String currentLang;

    @Override
    public void changeLanguage(Logger logger, String lang) throws LangNotFoundException {
        this.currentLang = lang;
        logger.info("OtherModChanger: Starting to load language '" + lang + "' for all mods...");
        int modCount = 0;
        int skippedCount = 0;
        int successCount = 0;
        
        for (ModContainer container : Loader.instance().getActiveModList()) {
            modCount++;
            try {
                File modFile = container.getSource();
                if (modFile == null) {
                    skippedCount++;
                    continue;
                }
                if (!modFile.exists()) {
                    skippedCount++;
                    continue;
                }
                if (!modFile.getName().endsWith(".jar")) {
                    skippedCount++;
                    continue;
                }

                // Debug: print info for dreamcraft mod
                if ("dreamcraft".equals(container.getModId())) {
                    logger.info("DEBUG: dreamcraft mod - source: " + modFile.getAbsolutePath() + ", exists: " + modFile.exists());
                    logger.info("DEBUG: dreamcraft mod - will try to load language: '" + lang + "'");
                }

                // Try to load language file from mod's jar
                boolean loaded = loadLanguageFromJar(modFile, container.getModId(), lang, logger);
                if (loaded) {
                    successCount++;
                }
            } catch (Exception e) {
                logger.error("Failed to load language for mod " + container.getModId(), e);
            }
        }
        
        logger.info("OtherModChanger: Checked " + modCount + " mods, skipped " + skippedCount + ", successfully loaded " + successCount + " language files.");
    }

    private boolean loadLanguageFromJar(File jarFile, String modId, String lang, Logger logger) throws Exception {
        try (ZipFile zipFile = new ZipFile(jarFile)) {
            // Try different case variants for the language file
            String langLower = lang.toLowerCase();
            String langUpper = lang.toUpperCase();
            String langProper = langLower.substring(0, 2) + "_" + langUpper.substring(2); // zh_CN
            String modIdLower = modId.toLowerCase(); // Some mods use lowercase modid in assets path
            
            String[] possiblePaths = {
                "assets/" + modId + "/lang/" + langLower + ".lang",      // zh_cn
                "assets/" + modId + "/lang/" + langUpper + ".lang",      // ZH_CN
                "assets/" + modId + "/lang/" + langProper + ".lang",      // zh_CN
                "assets/" + modId + "/lang/" + lang + ".lang",            // original
                "assets/" + modIdLower + "/lang/" + langLower + ".lang",  // lowercase modid
                "assets/" + modIdLower + "/lang/" + langUpper + ".lang",  // lowercase modid
                "assets/" + modIdLower + "/lang/" + langProper + ".lang",  // lowercase modid
                "assets/" + modIdLower + "/lang/" + lang + ".lang"         // lowercase modid
            };

            ZipEntry entry = null;
            String foundPath = null;
            for (String path : possiblePaths) {
                entry = zipFile.getEntry(path);
                if (entry != null) {
                    foundPath = path;
                    break;
                }
            }

            if (entry == null) {
                return false; // No language file found for this mod
            }

            logger.info("Found language file for mod " + modId + ": " + foundPath);
            try (InputStream is = zipFile.getInputStream(entry)) {
                injectLanguage(new InputStreamReader(is, "UTF-8"), logger, modId);
            }
            return true;
        }
    }

    private Map<String, String> getLanguageMap(Logger logger) {
        try {
            Class<?> stringTranslateClass = Class.forName("net.minecraft.util.StringTranslate");
            
            // Get instance from field_74817_a
            Object stringTranslateInstance = null;
            try {
                Field instanceField = stringTranslateClass.getDeclaredField("field_74817_a");
                instanceField.setAccessible(true);
                stringTranslateInstance = instanceField.get(null);
                logger.info("Got StringTranslate instance from field_74817_a");
            } catch (Exception e) {
                logger.error("Failed to get StringTranslate instance from field_74817_a", e);
            }
            
            if (stringTranslateInstance == null) {
                throw new RuntimeException("Could not get StringTranslate instance");
            }
            
            // Try to find the language map field
            Field languageListField = null;
            String[] possibleFieldNames = {"field_74816_c", "field_150511_e", "translateTable", "languageList", "nameToLanguageMap"};
            
            for (String fieldName : possibleFieldNames) {
                try {
                    Field f = stringTranslateClass.getDeclaredField(fieldName);
                    f.setAccessible(true);
                    Object value = f.get(stringTranslateInstance);
                    if (value instanceof Map) {
                        languageListField = f;
                        logger.info("Found language map field: " + fieldName);
                        break;
                    }
                } catch (Exception e) {
                    logger.info("Field " + fieldName + " failed: " + e.getMessage());
                }
            }
            
            if (languageListField == null) {
                // Try to find any Map field by checking all fields
                Field[] fields = stringTranslateClass.getDeclaredFields();
                for (Field f : fields) {
                    f.setAccessible(true);
                    try {
                        Object value = f.get(stringTranslateInstance);
                        if (value instanceof Map) {
                            languageListField = f;
                            logger.info("Found language map field by type: " + f.getName());
                            break;
                        }
                    } catch (Exception e) {
                        // Skip this field
                    }
                }
            }
            
            if (languageListField == null) {
                throw new RuntimeException("Could not find language map field in StringTranslate");
            }
            
            @SuppressWarnings("unchecked")
            Map<String, String> languageList = (Map<String, String>) languageListField.get(stringTranslateInstance);
            return languageList;
        } catch (Exception e) {
            logger.error("Failed to get language map", e);
            throw new RuntimeException(e);
        }
    }

    private void injectLanguage(InputStreamReader reader, Logger logger, String modId) {
        try {
            Properties properties = new Properties();
            properties.load(reader);
            
            Map<String, String> languageList = getLanguageMap(logger);
            
            // Inject all translations
            int count = 0;
            for (String key : properties.stringPropertyNames()) {
                languageList.put(key, properties.getProperty(key));
                count++;
            }
            
            logger.info("Change lang to " + currentLang + " for " + modId + " done! Injected " + count + " translations.");
        } catch (Exception e) {
            logger.error("Failed to inject language for " + modId, e);
        } finally {
            try {
                reader.close();
            } catch (Exception e) {
                // Ignore
            }
        }
    }
}
