package ru.glitchless.serverlocalization.lang;

import cpw.mods.fml.common.Loader;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import java.lang.reflect.Field;

public class IC2LangChanger implements ILangChanger {
    @Override
    public void changeLanguage(Logger logger, String lang) throws LangNotFoundException {
        if (Loader.isModLoaded("IC2")) {
            InputStream is = null;
            try {
                // Try both lowercase and uppercase variants
                is = IC2LangChanger.class.getResourceAsStream("/assets/ic2/lang_ic2/" + lang.toLowerCase() + ".properties");
                if (is == null) {
                    is = IC2LangChanger.class.getResourceAsStream("/assets/ic2/lang_ic2/" + lang.toUpperCase() + ".properties");
                }
                if (is == null) {
                    is = IC2LangChanger.class.getResourceAsStream("/assets/ic2/lang_ic2/" + lang + ".properties");
                }
                if (is == null) {
                    throw new LangNotFoundException(lang);
                }
                loadIC2Localization(is, lang, logger);
                logger.info("Change lang to " + lang + " for ic2 done!");
            } catch (IOException ex) {
                logger.error(ex);
                throw new LangNotFoundException(lang);
            } finally {
                if (is != null) {
                    try {
                        is.close();
                    } catch (IOException ex) {
                        logger.error(ex);
                    }
                }
            }
        }
    }

    private Map<String, String> getLanguageMap(Logger logger) {
        try {
            Class<?> stringTranslateClass = Class.forName("net.minecraft.util.StringTranslate");
            
            // Try to find the correct field name dynamically
            Field languageListField = null;
            String[] possibleFieldNames = {"translateTable", "languageList", "field_74816_e", "nameToLanguageMap"};
            
            for (String fieldName : possibleFieldNames) {
                try {
                    Field f = stringTranslateClass.getDeclaredField(fieldName);
                    f.setAccessible(true);
                    Object value = f.get(null);
                    if (value instanceof Map) {
                        languageListField = f;
                        logger.info("Found language map field: " + fieldName);
                        break;
                    }
                } catch (NoSuchFieldException e) {
                    // Try next field name
                }
            }
            
            if (languageListField == null) {
                // If none of the known field names work, try to find any Map field
                Field[] fields = stringTranslateClass.getDeclaredFields();
                for (Field f : fields) {
                    f.setAccessible(true);
                    Object value = f.get(null);
                    if (value instanceof Map) {
                        languageListField = f;
                        logger.info("Found language map field by type: " + f.getName());
                        break;
                    }
                }
            }
            
            if (languageListField == null) {
                throw new RuntimeException("Could not find language map field in StringTranslate");
            }
            
            @SuppressWarnings("unchecked")
            Map<String, String> languageList = (Map<String, String>) languageListField.get(null);
            return languageList;
        } catch (Exception e) {
            logger.error("Failed to get language map", e);
            throw new RuntimeException(e);
        }
    }

    private static void loadIC2Localization(InputStream inputStream, String lang, Logger logger) throws IOException {
        Properties properties = new Properties();
        properties.load(new InputStreamReader(inputStream, StandardCharsets.UTF_8));

        try {
            Map<String, String> languageList = getLanguageMapStatic(logger);

            int count = 0;
            for (Map.Entry<Object, Object> entries : properties.entrySet()) {
                Object key = entries.getKey();
                Object value = entries.getValue();

                if (((key instanceof String)) && ((value instanceof String))) {
                    String newKey = (String) key;

                    if ((!newKey.startsWith("achievement.")) &&
                            (!newKey.startsWith("itemGroup.")) &&
                            (!newKey.startsWith("death."))) {

                        newKey = "ic2." + newKey;
                    }
                    languageList.put(newKey, (String) value);
                    count++;
                }
            }
            logger.info("Injected " + count + " IC2 translations");
        } catch (Exception e) {
            logger.error("Failed to inject IC2 language using reflection", e);
            throw new IOException(e);
        }
    }
    
    private static Map<String, String> getLanguageMapStatic(Logger logger) {
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
