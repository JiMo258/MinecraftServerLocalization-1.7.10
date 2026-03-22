# Server Localization 开发文档

## 项目信息

**项目名称**：Server Localization  
**目标版本**：Minecraft 1.7.10 (GTNH 2.8.4)  
**原版本**：Minecraft 1.12.2  
**原仓库**：https://github.com/glitchless/MinecraftServerLocalization

## 开发环境

### 必需工具

- **JDK 8**：编译和运行模组
  - 推荐：Eclipse Adoptium JDK 8
  - 下载：https://adoptium.net/temurin/releases/?version=8
  
- **Gradle 4.4.1**：项目构建工具
  - 通过 Gradle Wrapper 自动管理
  
- **Minecraft Forge 1.7.10**：模组加载器
  - 版本：10.13.4.1614-1.7.10

### 环境配置

1. 安装 JDK 8 并设置环境变量
```bash
# Windows
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-8.0.482.8-hotspot

# PowerShell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-8.0.482.8-hotspot'
```

2. 验证安装
```bash
java -version
# 应显示：java version "1.8.0_xxx"
```

## 构建系统

### Gradle 配置

**build.gradle 关键配置**

```gradle
version = "1.0"
group = "ru.glitchless.serverlocalization"
archivesBaseName = "serverlocalization"

sourceCompatibility = targetCompatibility = '1.8'

repositories {
    mavenCentral()
    maven {
        name = "forge"
        url = "https://maven.minecraftforge.net/"
    }
    maven {
        url = "https://libraries.minecraft.net/"
    }
}

dependencies {
    compile files('libs/forge-1.7.10-10.13.4.1614-1.7.10-universal.jar')
    compile 'com.google.code.gson:gson:2.8.9'
    compile 'org.apache.logging.log4j:log4j-api:2.0-beta9'
    compile 'org.apache.logging.log4j:log4j-core:2.0-beta9'
}
```

### 构建命令

```bash
# 清理并构建
gradlew.bat clean build

# 仅构建
gradlew.bat build

# 清理
gradlew.bat clean
```

构建产物：`build/libs/serverlocalization-1.0.jar`

## 项目结构

```
src/main/java/ru/glitchless/serverlocalization/
├── ServerLocalization.java           # 主模组类
├── config/
│   └── ServerLocalizationConfig.java # 配置管理
├── lang/
│   ├── ILangChanger.java             # 语言加载器接口
│   ├── VanillaChanger.java           # 原版翻译加载器
│   ├── OtherModChanger.java          # 模组翻译加载器
│   ├── TxLoaderChanger.java          # 外部翻译加载器
│   ├── GTNHLocalization.java         # GregTech 配置文件加载器（新）
│   ├── IC2LangChanger.java           # IC2 特殊处理
│   └── LangNotFoundException.java    # 异常类
├── downloader/
│   ├── Asset.java                    # 资源描述类
│   └── AssetsHelper.java             # 资源下载工具
├── utils/
│   └── HttpUtils.java                # HTTP 工具类
└── proxy/
    ├── ISideProxy.java               # 代理接口
    ├── ClientSideProxy.java          # 客户端代理
    └── ServerSideProxy.java          # 服务端代理
```

## 核心技术

### 1. 反射访问 StringTranslate

Minecraft 1.7.10 使用混淆的内部类名，需要通过反射动态访问。

**关键类**：`net.minecraft.util.StringTranslate`

**混淆字段**：
- `field_74817_a`：StringTranslate 实例
- `field_74816_c`：语言映射 Map<String, String>

**代码示例**：
```java
Class<?> stringTranslateClass = Class.forName("net.minecraft.util.StringTranslate");

// 获取实例
Field instanceField = stringTranslateClass.getDeclaredField("field_74817_a");
instanceField.setAccessible(true);
Object stringTranslateInstance = instanceField.get(null);

// 获取语言映射
Field languageListField = stringTranslateClass.getDeclaredField("field_74816_c");
languageListField.setAccessible(true);
Map<String, String> languageList = (Map<String, String>) 
    languageListField.get(stringTranslateInstance);
```

### 2. 动态字段发现

为了提高兼容性，实现了动态字段发现机制。

```java
String[] possibleFieldNames = {
    "field_74816_c",    // 主要字段
    "field_150511_e",   // 备用字段
    "translateTable",   // 未混淆字段名
    "languageList",     // 另一个可能的名字
    "nameToLanguageMap" // 可能的字段名
};

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
        // 尝试下一个字段名
    }
}

// 如果所有已知字段都失败，按类型查找
if (languageListField == null) {
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
            // 跳过
        }
    }
}
```

### 3. UTF-8 编码支持

语言文件使用 UTF-8 编码以支持多字节字符。

```java
try (InputStreamReader reader = new InputStreamReader(
        new FileInputStream(langFile), "UTF-8")) {
    Properties properties = new Properties();
    properties.load(reader);
    
    // 注入翻译
    for (String key : properties.stringPropertyNames()) {
        languageList.put(key, properties.getProperty(key));
    }
}
```

### 4. 多路径语言文件匹配

为了兼容不同的命名约定，支持多种路径格式。

```java
String[] possiblePaths = {
    "assets/" + modId + "/lang/" + langLower + ".lang",      // zh_cn
    "assets/" + modId + "/lang/" + langUpper + ".lang",      // ZH_CN
    "assets/" + modId + "/lang/" + langProper + ".lang",     // zh_CN
    "assets/" + modId + "/lang/" + lang + ".lang",           // 原始格式
    "assets/" + modIdLower + "/lang/" + langLower + ".lang", // 小写 modid
    "assets/" + modIdLower + "/lang/" + langUpper + ".lang",
    "assets/" + modIdLower + "/lang/" + langProper + ".lang",
    "assets/" + modIdLower + "/lang/" + lang + ".lang"
};
```

### 5. GregTech 配置文件解析

使用正则表达式匹配 GregTech 配置文件的两种格式。

**支持格式**：
1. 带引号格式：`S:"key"=value`
2. 不带引号格式：`S:key=value`

**正则表达式**：
```java
// 带引号格式
private static final Pattern CONFIG_PATTERN_QUOTED = 
    Pattern.compile("^\\s*S:\"([^\"]+)\"=(.*)$");

// 不带引号格式
private static final Pattern CONFIG_PATTERN_UNQUOTED = 
    Pattern.compile("^\\s*S:([a-zA-Z0-9_.\\-\\[\\]]+)=(.*)$");
```

**解析逻辑**：
```java
// 尝试匹配带引号格式
Matcher matcherQuoted = CONFIG_PATTERN_QUOTED.matcher(line);
if (matcherQuoted.matches()) {
    String key = matcherQuoted.group(1);
    String value = matcherQuoted.group(2).trim();
    
    // 去除大括号（如果有）
    if (value.startsWith("{") && value.endsWith("}")) {
        value = value.substring(1, value.length() - 1).trim();
    }
    
    languageList.put(key, value);
    count++;
    continue;
}

// 尝试匹配不带引号格式
Matcher matcherUnquoted = CONFIG_PATTERN_UNQUOTED.matcher(line);
if (matcherUnquoted.matches()) {
    String key = matcherUnquoted.group(1);
    String value = matcherUnquoted.group(2).trim();
    
    // 去除大括号（如果有）
    if (value.startsWith("{") && value.endsWith("}")) {
        value = value.substring(1, value.length() - 1).trim();
    }
    
    languageList.put(key, value);
    count++;
    continue;
}
```

## 语言加载器详解

### VanillaChanger（原版翻译）

**功能**：从网络下载 Minecraft 官方语言包

**流程**：
1. 检查本地是否已存在语言文件
2. 如不存在，从 Minecraft 官方资源服务器下载
3. 解析并注入到 StringTranslate

**文件路径**：`assets/minecraft/lang/zh_CN.lang`

**关键代码**：
```java
File assetFile = AssetsHelper.getLangFile(logger, lang);
try (InputStreamReader reader = new InputStreamReader(
        new FileInputStream(assetFile), "UTF-8")) {
    injectLanguage(reader, lang, logger);
}
```

### OtherModChanger（模组翻译）

**功能**：从已加载模组的 jar 文件中提取翻译

**流程**：
1. 遍历所有已加载的模组
2. 打开每个模组的 jar 文件
3. 查找语言文件（支持多种路径格式）
4. 解析并注入翻译

**特殊处理**：
- 支持模组 ID 大小写变体（IC2NuclearControl → nuclearcontrol）
- 支持语言代码大小写变体（zh_CN, zh_cn, ZH_CN）

**关键代码**：
```java
for (ModContainer container : Loader.instance().getActiveModList()) {
    File modFile = container.getSource();
    if (modFile != null && modFile.exists() && 
        modFile.getName().endsWith(".jar")) {
        boolean loaded = loadLanguageFromJar(modFile, 
            container.getModId(), lang, logger);
    }
}
```

### TxLoaderChanger（外部翻译）

**功能**：从 txloader 目录加载外部翻译文件

**流程**：
1. 扫描 `config/txloader/load` 目录
2. 扫描 `config/txloader/forceload` 目录
3. 递归查找所有 `.lang` 文件
4. 解析并注入翻译

**目录结构**：
```
config/txloader/load/
├── modname1/
│   └── lang/
│       └── zh_CN.lang
└── modname2/
    └── lang/
        └── zh_CN.lang
```

**关键代码**：
```java
private void findAndLoadLangFiles(File dir, String lang, Logger logger) {
    File[] files = dir.listFiles();
    for (File file : files) {
        if (file.isDirectory()) {
            findAndLoadLangFiles(file, lang, logger);
        } else if (file.getName().equalsIgnoreCase(lang + ".lang")) {
            loadLanguageFile(file, logger);
        }
    }
}
```

### GTNHLocalization（GregTech 配置文件）

**功能**：加载 GregTech 配置文件格式的翻译（新功能）

**特点**：
- 专门用于 GregTech 配置文件
- 支持两种格式：带引号和不带引号
- 文件放置在服务端根目录
- 文件命名：`GregTech_{语言代码}.lang`

**流程**：
1. 读取配置语言（从 ServerLocalizationConfig）
2. 构建文件名：`GregTech_{lang}.lang`
3. 在服务端根目录查找文件
4. 解析并注入翻译

**支持格式**：
1. 带引号格式：
   ```
   S:"Book.How to: Modular Baubles.Name"=模块化饰品手册
   ```

2. 不带引号格式：
   ```
   S:gt.blockmachines.multimachine.supercapacitor.name=兰波顿超级电容库
   ```

**文件位置**：
- 相对路径：`./GregTech_zh_CN.lang`
- 绝对路径：`D:\Desktop\server\GregTech_zh_CN.lang`

**正则表达式**：
```java
// 带引号格式
private static final Pattern CONFIG_PATTERN_QUOTED = 
    Pattern.compile("^\\s*S:\"([^\"]+)\"=(.*)$");

// 不带引号格式（支持字母、数字、下划线、点号、连字符、方括号）
private static final Pattern CONFIG_PATTERN_UNQUOTED = 
    Pattern.compile("^\\s*S:([a-zA-Z0-9_.\\-\\[\\]]+)=(.*)$");
```

**关键代码**：
```java
@Override
public void changeLanguage(Logger logger, String lang) throws LangNotFoundException {
    logger.info("GTNHLocalization: Starting to load language " + 
        lang + " from GregTech config file...");
    
    // 构建文件名
    String fileName = "GregTech_" + lang + ".lang";
    File configFile = new File(fileName);
    
    if (!configFile.exists()) {
        logger.info("GTNHLocalization: GregTech config file not found: " + 
            fileName);
        return;
    }
    
    try {
        logger.info("GTNHLocalization: Found GregTech config file: " + 
            configFile.getAbsolutePath());
        loadConfigFile(configFile, logger);
    } catch (Exception e) {
        logger.error("Failed to load GregTech config file: " + 
            configFile.getAbsolutePath(), e);
    }
}
```

**特殊处理**：
- 支持 `languagefile { ... }` 块包裹
- 自动去除大括号
- UTF-8 编码支持
- 详细的日志输出

### IC2LangChanger（IC2 特殊处理）

**功能**：专门处理 IndustrialCraft 2 的翻译

**特殊处理**：
- 路径：`/assets/ic2/lang_ic2/`
- 为非标准键添加 "ic2." 前缀
- 跳过 achievement, itemGroup, death 相关键

**关键代码**：
```java
for (Map.Entry<Object, Object> entries : properties.entrySet()) {
    String newKey = (String) entries.getKey();
    
    // 为某些键添加 ic2. 前缀
    if (!newKey.startsWith("achievement.") &&
        !newKey.startsWith("itemGroup.") &&
        !newKey.startsWith("death.")) {
        newKey = "ic2." + newKey;
    }
    
    languageList.put(newKey, (String) entries.getValue());
}
```

## 配置系统

### 配置文件格式

使用 Forge 的 Configuration 类（1.7.10 风格）

```java
public class ServerLocalizationConfig {
    public static String lang = "zh_CN";

    public static void init(File configFile) {
        Configuration config = new Configuration(configFile);
        try {
            config.load();
            lang = config.getString("lang", 
                Configuration.CATEGORY_GENERAL, 
                "zh_CN", 
                "server lang");
        } finally {
            if (config.hasChanged()) {
                config.save();
            }
        }
    }
}
```

### 配置文件内容

`config/serverlocalization.cfg`
```ini
# Configuration file

general {
    # 服务器语言 [默认: zh_CN]
    S:lang=zh_CN
}
```

## 调试技巧

### 日志输出

模组使用 Log4j2 进行日志记录

```java
logger.info("信息消息");
logger.warn("警告消息");
logger.error("错误消息", exception);
```

### 测试翻译

模组包含内置测试功能，会在 postInit 阶段自动运行：

```java
logger.info("=== Translation Test ===");
logger.info("Total translations in StringTranslate: " + languageList.size());

String[] testKeys = {
    "death.fell.accident.generic",
    "book.pageIndicator",
    "entity.LavaSlime.name",
    "dreamcraft.welcome.welcome"
};

for (String key : testKeys) {
    String translated = languageList.get(key);
    logger.info("Test '" + key + "': " + 
        (translated != null ? translated : "NOT FOUND"));
}
```

## 常见问题

### 1. 字段查找失败

**症状**：`NoSuchFieldException: field_74816_c`

**解决方案**：
- 使用动态字段发现机制
- 按类型查找 Map 字段
- 添加更多可能的字段名

### 2. 翻译未显示

**可能原因**：
- 语言文件路径不正确
- 编码问题
- 优先级被覆盖
- 正则表达式不匹配

**调试步骤**：
1. 检查日志中的字段发现信息
2. 验证语言文件路径
3. 检查翻译条目数量
4. 检查正则表达式匹配

### 3. 中文乱码

**解决方案**：
- 使用 UTF-8 编码
- 确保语言文件保存为 UTF-8 格式
- 使用 InputStreamReader 指定编码

### 4. GregTech 配置文件未加载

**可能原因**：
- 文件名不正确（应为 `GregTech_{语言代码}.lang`）
- 文件位置不正确（应在服务端根目录）
- 格式不匹配（检查正则表达式）

**调试步骤**：
1. 检查文件名是否正确
2. 检查文件位置是否在服务端根目录
3. 查看日志中的错误信息
4. 验证文件格式

## 性能优化

### 1. 缓存反射字段

将反射获取的 Field 对象缓存起来，避免重复查找

### 2. 批量注入

一次性注入所有翻译，而不是逐个注入

### 3. 延迟加载

仅在需要时加载翻译，而不是在模组初始化时

### 4. 正则表达式优化

预编译正则表达式（已在代码中实现）

## 扩展开发

### 添加新的语言加载器

1. 实现 `ILangChanger` 接口

```java
public class CustomLangChanger implements ILangChanger {
    @Override
    public void changeLanguage(Logger logger, String lang) 
            throws LangNotFoundException {
        // 实现加载逻辑
    }
}
```

2. 在主类中注册

```java
@Mod.EventHandler
public void preInit(FMLPreInitializationEvent event) {
    addLangChanger(new CustomLangChanger());
}
```

### 支持新的语言

1. 确保语言文件使用标准格式
2. 添加到配置文件说明
3. 测试翻译加载

### 添加新的配置文件格式

参考 GTNHLocalization 的实现：

1. 定义正则表达式
2. 实现解析逻辑
3. 处理特殊格式
4. 添加日志输出

## 测试

### 单元测试

建议为以下功能编写测试：
- 字段发现机制
- 路径匹配逻辑
- 翻译注入功能
- 正则表达式匹配

### 集成测试

在真实 Minecraft 环境中测试：
- 原版翻译
- 多个模组翻译
- 外部翻译文件
- GregTech 配置文件
- 不同语言切换

## 发布

### 构建发布版本

```bash
gradlew.bat clean build
```

### 版本号管理

在 `build.gradle` 中修改版本号：

```gradle
version = "1.0.1"
```

### 打包

构建产物位于：`build/libs/serverlocalization-1.0.jar`

## 参考资料

- [Minecraft Forge 1.7.10 文档](https://files.minecraftforge.net/)
- [GTNewHorizons 项目](https://github.com/GTNewHorizons)
- [Java 反射教程](https://docs.oracle.com/javase/tutorial/reflect/)
- [Java 正则表达式教程](https://docs.oracle.com/javase/tutorial/essential/regex/)

## 贡献指南

1. Fork 项目
2. 创建特性分支
3. 提交更改
4. 推送到分支
5. 创建 Pull Request

## 许可证

请参考原项目的许可证文件。