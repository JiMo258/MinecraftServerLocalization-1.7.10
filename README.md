# Server Localization

Minecraft mod for changing language on server-side (Minecraft 1.7.10)

## 概述

Server Localization 是一个 Minecraft 1.7.10 服务器端语言本地化模组，允许服务器管理员设置服务器端显示的语言，支持原版和模组的翻译。

## 关于本项目 / About This Project

本人代码小白，利用 AI 对 [原仓库](https://github.com/glitchless/MinecraftServerLocalization) 进行 1.7.10 版本移植，本人没法进行 BUG 修复，仅做抛砖引玉用，希望有大佬可以参考并进行再开发。模组基础功能已经实现，仅在 GTNH 2.8.4 服务端中进行测试。


I am a complete beginner in programming. I used AI to port the [original repository](https://github.com/glitchless/MinecraftServerLocalization) to Minecraft 1.7.10. I am unable to fix bugs. This project is meant to serve as a starting point, and I hope experienced developers can reference it and continue development. The basic functionality has been implemented, and it has only been tested on a GTNH 2.8.4 server.

## 功能特性

- **原版翻译支持**：自动下载并加载 Minecraft 官方语言包
- **模组翻译支持**：自动从已加载的模组 jar 文件中提取翻译
- **外部翻译支持**：支持通过 txloader 加载外部翻译文件
- **GTNH 配置文件支持**：支持 GregTech 配置文件格式的翻译加载
- **IC2 特殊处理**：专门优化 IndustrialCraft 2 的翻译加载(直接对原模组的移植，功能实现情况未知)
- **UTF-8 编码**：完整支持中文等多字节语言
- **灵活配置**：通过配置文件轻松切换语言

## 安装方法

1. 下载最新版本的 `serverlocalization-1.0.jar`
2. 在服务端覆盖对应版本的翻译文件 
3. 将 jar 文件放入服务器的 `mods` 文件夹
4. 重启服务器，模组会自动生成配置文件

## 配置说明

配置文件位置：`config/serverlocalization.cfg`

```ini
general {
    # 服务器语言 [默认: zh_CN]
    S:lang=zh_CN
}
```

## 已知问题

模组会将服务端部分原版输出改为对应语言输出，可能会导致部分依赖输出语句判断服务器运行情况的插件失效。需要手动修改插件为对应的语句匹配。

## 翻译加载顺序

模组按以下顺序加载翻译，后加载的会覆盖先加载的：

1. **VanillaChanger** - Minecraft 原版翻译
2. **OtherModChanger** - 各模组 jar 文件中的翻译
3. **TxLoaderChanger** - txloader 目录中的外部翻译文件
4. **GTNHLocalization** - GregTech 配置文件格式的翻译（服务端根目录）
5. **IC2LangChanger** - IndustrialCraft 2 的特殊翻译

## 外部翻译文件

### TxLoader 支持

模组会自动扫描以下目录中的翻译文件：

- `config/txloader/load/`
- `config/txloader/forceload/`


### 语言文件格式

语言文件使用标准的 Minecraft Properties 格式：

```
# 注释行
translation.key=翻译文本
another.key=另一个翻译
```

### GregTech 配置文件支持

模组支持 GregTech 配置文件格式的翻译文件，放置在服务端根目录：

**文件命名**：`GregTech_{语言代码}.lang`

**示例**：
- `GregTech_zh_CN.lang` - 中文翻译
- `GregTech_en_US.lang` - 英语翻译

**文件位置**：服务端根目录（与 server.properties 同级）

**支持格式**：
1. 带引号格式：
   ```
   S:"Book.How to: Modular Baubles.Name"=模块化饰品手册
   ```

2. 不带引号格式：
   ```
   S:gt.blockmachines.multimachine.supercapacitor.name=兰波顿超级电容库
   ```



## 版本信息

- **版本号**：1.0
- **原仓库**：https://github.com/glitchless/MinecraftServerLocalization
- **移植版本**：Minecraft 1.7.10 (仅在GTNH 2.8.4服务端下进行测试)

## 技术实现

模组使用反射机制访问 Minecraft 内部的 `StringTranslate` 类，通过动态字段发现找到正确的语言映射字段，然后将翻译注入到运行时的语言系统中。

核心技术：
- Java 反射
- Properties 文件解析
- GregTech 配置文件解析（正则表达式匹配）
- UTF-8 编码支持
- 动态字段发现

## 开发者

原模组开发：glitchless  
1.7.10 移植：使用AI进行编程

## 许可证

请参考原项目的许可证文件。

## 支持

如有问题或建议，请自行利用AI进行解决，代码小白，没办法处理BUG，望理解。