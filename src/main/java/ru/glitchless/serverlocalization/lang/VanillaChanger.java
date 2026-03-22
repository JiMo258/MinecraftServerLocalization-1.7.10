package ru.glitchless.serverlocalization.lang;

import org.apache.logging.log4j.Logger;
import ru.glitchless.serverlocalization.downloader.AssetsHelper;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Properties;

public class VanillaChanger implements ILangChanger {
    @Override
    public void changeLanguage(Logger logger, String lang) throws LangNotFoundException {
        final File assetFile = AssetsHelper.getLangFile(logger, lang);
        if (assetFile == null) {
            throw new LangNotFoundException(lang);
        }
        final InputStream is;
        try {
            is = new FileInputStream(assetFile);
        } catch (FileNotFoundException e) {
            throw new LangNotFoundException(lang);
        }

        try {
            // Use UTF-8 encoding for Minecraft language files
            injectLanguage(new InputStreamReader(is, "UTF-8"), lang, logger);
        } catch (Exception e) {
            logger.error("Failed to read language file with UTF-8 encoding", e);
            throw new LangNotFoundException(lang);
        } finally {
            try {
                is.close();
            } catch (IOException ex) {
                logger.error(ex);
            }
        }
        logger.info("Change lang to " + lang + " for vanilla done!");
    }

    private void injectLanguage(Reader reader, String lang, Logger logger) {
        try {
            Properties properties = new Properties();
            
            // Load properties using the UTF-8 reader
            properties.load(reader);
            logger.info("Properties loaded, size: " + properties.size());
            
            // Log first few properties for debugging
            int count = 0;
            for (String key : properties.stringPropertyNames()) {
                if (count++ < 5) {
                    logger.info("Example translation: " + key + " = " + properties.getProperty(key));
                }
            }
            
            // Use reflection to inject into StringTranslate (Minecraft 1.7.10)
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
            
            // Inject all translations
            for (String key : properties.stringPropertyNames()) {
                languageList.put(key, properties.getProperty(key));
            }
            
            logger.info("Injected " + properties.size() + " translations into StringTranslate");
        } catch (Exception e) {
            logger.error("Failed to inject language using reflection", e);
            throw new RuntimeException(e);
        }
    }
}
