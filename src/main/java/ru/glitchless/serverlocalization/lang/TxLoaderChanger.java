package ru.glitchless.serverlocalization.lang;

import org.apache.logging.log4j.Logger;

import java.io.*;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Properties;

public class TxLoaderChanger implements ILangChanger {
    private String currentLang;
    private static final String TXLOADER_LOAD_PATH = "config/txloader/load";
    private static final String TXLOADER_FORCELOAD_PATH = "config/txloader/forceload";

    @Override
    public void changeLanguage(Logger logger, String lang) throws LangNotFoundException {
        this.currentLang = lang;
        logger.info("TxLoaderChanger: Starting to load language " + lang + " from txloader directories...");
        
        // Load from both directories
        loadFromDirectory(TXLOADER_LOAD_PATH, lang, logger);
        loadFromDirectory(TXLOADER_FORCELOAD_PATH, lang, logger);
    }

    private void loadFromDirectory(String path, String lang, Logger logger) {
        File txLoaderDir = new File(path);
        if (!txLoaderDir.exists()) {
            logger.info("TxLoaderChanger: " + path + " directory not found, skipping.");
            return;
        }
        
        // Recursively find all .lang files
        findAndLoadLangFiles(txLoaderDir, lang, logger);
    }

    private void findAndLoadLangFiles(File dir, String lang, Logger logger) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                findAndLoadLangFiles(file, lang, logger);
            } else if (file.getName().equals(lang + ".lang") || 
                       file.getName().equalsIgnoreCase(lang + ".lang")) {
                try {
                    loadLanguageFile(file, logger);
                } catch (Exception e) {
                    logger.error("Failed to load language file: " + file.getAbsolutePath(), e);
                }
            }
        }
    }

    private void loadLanguageFile(File langFile, Logger logger) throws Exception {
        try (FileInputStream fis = new FileInputStream(langFile);
             InputStreamReader reader = new InputStreamReader(fis, "UTF-8")) {
            
            Properties properties = new Properties();
            properties.load(reader);
            
            Map<String, String> languageList = getLanguageMap(logger);
            
            // Inject all translations
            int count = 0;
            for (String key : properties.stringPropertyNames()) {
                languageList.put(key, properties.getProperty(key));
                count++;
            }
            
            logger.info("TxLoaderChanger: Loaded " + count + " translations from " + langFile.getAbsolutePath());
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
}