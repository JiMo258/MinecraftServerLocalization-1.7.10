package ru.glitchless.serverlocalization.lang;

import org.apache.logging.log4j.Logger;
import ru.glitchless.serverlocalization.config.ServerLocalizationConfig;

import java.io.*;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.regex.*;

public class GTNHLocalization implements ILangChanger {
    // Pattern for quoted format: S:"key"=value
    private static final Pattern CONFIG_PATTERN_QUOTED = Pattern.compile("^\\s*S:\"([^\"]+)\"=(.*)$");
    // Pattern for unquoted format: S:key=value
    private static final Pattern CONFIG_PATTERN_UNQUOTED = Pattern.compile("^\\s*S:([a-zA-Z0-9_.\\-\\[\\]]+)=(.*)$");

    @Override
    public void changeLanguage(Logger logger, String lang) throws LangNotFoundException {
        logger.info("GTNHLocalization: Starting to load language " + lang + " from GregTech config file...");
        
        // Only look for GregTech_{lang}.lang in server root directory
        String fileName = "GregTech_" + lang + ".lang";
        File configFile = new File(fileName);
        
        if (!configFile.exists()) {
            logger.info("GTNHLocalization: GregTech config file not found: " + fileName);
            return;
        }
        
        try {
            logger.info("GTNHLocalization: Found GregTech config file: " + configFile.getAbsolutePath());
            loadConfigFile(configFile, logger);
        } catch (Exception e) {
            logger.error("Failed to load GregTech config file: " + configFile.getAbsolutePath(), e);
        }
    }

    private void loadConfigFile(File configFile, Logger logger) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(configFile), "UTF-8"))) {
            
            Map<String, String> languageList = getLanguageMap(logger);
            
            String line;
            int count = 0;
            int inLanguagefileBlock = 0;
            
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                
                // Skip empty lines and comments
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                // Check for languagefile block start
                if (line.equals("languagefile {")) {
                    inLanguagefileBlock = 1;
                    continue;
                }
                
                // Check for languagefile block end
                if (line.equals("}") && inLanguagefileBlock > 0) {
                    inLanguagefileBlock--;
                    continue;
                }
                
                // Try to match quoted format: S:"key"=value
                Matcher matcherQuoted = CONFIG_PATTERN_QUOTED.matcher(line);
                if (matcherQuoted.matches()) {
                    String key = matcherQuoted.group(1);
                    String value = matcherQuoted.group(2).trim();
                    
                    // Remove braces if present in value
                    if (value.startsWith("{") && value.endsWith("}")) {
                        value = value.substring(1, value.length() - 1).trim();
                    }
                    
                    languageList.put(key, value);
                    count++;
                    continue;
                }
                
                // Try to match unquoted format: S:key=value
                Matcher matcherUnquoted = CONFIG_PATTERN_UNQUOTED.matcher(line);
                if (matcherUnquoted.matches()) {
                    String key = matcherUnquoted.group(1);
                    String value = matcherUnquoted.group(2).trim();
                    
                    // Remove braces if present in value
                    if (value.startsWith("{") && value.endsWith("}")) {
                        value = value.substring(1, value.length() - 1).trim();
                    }
                    
                    languageList.put(key, value);
                    count++;
                    continue;
                }
            }
            
            logger.info("GTNHLocalization: Loaded " + count + " translations from " + configFile.getAbsolutePath());
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