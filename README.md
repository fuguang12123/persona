
# Persona - AI智能体社交应用

![Platform](https://img.shields.io/badge/Platform-Android-green)
![Language](https://img.shields.io/badge/Language-Kotlin-blue)
![Architecture](https://img.shields.io/badge/Architecture-MVVM%20%2B%20Clean%20Architecture-orange)
![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-purple)

## 项目简介

Persona 是一个基于 Android 平台的 AI 智能体社交应用，用户可以创建、定制和与各种 AI 智能体进行交互。应用支持本地和云端两种运行模式，提供丰富的社交功能，包括聊天、动态发布、智能体广场等。

### 主要功能

- **智能体创建与定制**：用户可以创建个性化的 AI 智能体，设置头像、名称、描述和性格标签
  - **多模态聊天**：支持文本、图片和语音输入，与智能体进行自然对话
  - **智能体广场**：浏览和发现其他用户创建的公开智能体，获取个性化推荐
  - **社交动态**：发布和浏览动态，与其他用户互动
  - **本地AI模式**：支持端侧 AI 运行，保护用户隐私
  - **记忆系统**：AI 能够学习和记住用户的偏好和对话历史

## 技术架构

### 完整项目结构

```
Persona/
├── .gitignore                    # Git 忽略文件
├── .gradle/                      # Gradle 缓存目录
├── .kotlin/                      # Kotlin 编译缓存
├── app/                          # 应用模块
│   ├── .gitignore                # 应用模块 Git 忽略文件
│   ├── build/                    # 构建输出目录
│   │   ├── generated/            # 自动生成的文件
│   │   │   ├── ap_generated_sources/  # AP 生成的源码
│   │   │   ├── hilt/             # Hilt 生成的代码
│   │   │   ├── ksp/              # KSP 生成的代码
│   │   │   └── res/              # 生成的资源文件
│   │   ├── intermediates/        # 中间构建文件
│   │   ├── kotlin/               # Kotlin 编译输出
│   │   ├── outputs/              # 最终构建输出
│   │   └── tmp/                  # 临时文件
│   ├── build.gradle.kts          # 应用模块构建脚本
│   ├── proguard-rules.pro        # ProGuard 混淆规则
│   └── src/                      # 源代码目录
│       ├── main/                 # 主要源代码
│       │   ├── AndroidManifest.xml  # 应用清单文件
│       │   ├── java/             # Java/Kotlin 源代码
│       │   │   └── com/
│       │   │       └── example/
│       │   │           └── persona/
│       │   │               ├── MainActivity.kt          # 主活动
│       │   │               ├── MainApplication.kt       # 应用程序类
│       │   │               ├── data/                    # 数据层
│       │   │               │   ├── local/              # 本地数据源
│       │   │               │   │   ├── AppDatabase.kt  # Room 数据库
│       │   │               │   │   ├── UserPreferencesRepository.kt  # 用户偏好设置
│       │   │               │   │   ├── converter/       # 类型转换器
│       │   │               │   │   │   └── PostTypeConverters.kt
│       │   │               │   │   ├── dao/            # 数据访问对象
│       │   │               │   │   │   ├── ChatDao.kt
│       │   │               │   │   │   ├── PersonaDao.kt
│       │   │               │   │   │   ├── PostDao.kt
│       │   │               │   │   │   └── UserMemoryDao.kt
│       │   │               │   │   └── entity/         # 数据库实体
│       │   │               │   │       ├── ChatMessageEntity.kt
│       │   │               │   │       ├── PersonaEntity.kt
│       │   │               │   │       ├── PostEntity.kt
│       │   │               │   │       └── UserMemoryEntity.kt
│       │   │               │   ├── manager/            # 管理器类
│       │   │               │   │   └── SessionManager.kt
│       │   │               │   ├── model/              # 数据模型
│       │   │               │   │   ├── AuthModels.kt
│       │   │               │   │   ├── BaseResponse.kt
│       │   │               │   │   ├── ChatModels.kt
│       │   │               │   │   ├── PersonaModels.kt
│       │   │               │   │   └── PostInteractEvent.kt
│       │   │               │   ├── remote/             # 远程数据源
│       │   │               │   │   ├── AuthInterceptor.kt
│       │   │               │   │   ├── AuthService.kt
│       │   │               │   │   ├── ChatService.kt
│       │   │               │   │   ├── PersonaService.kt
│       │   │               │   │   ├── PostDetailDto.kt
│       │   │               │   │   ├── PostDtos.kt
│       │   │               │   │   └── PostService.kt
│       │   │               │   ├── repository/         # 数据仓库
│       │   │               │   │   ├── ChatRepository.kt
│       │   │               │   │   ├── PersonaRepository.kt
│       │   │               │   │   └── PostRepository.kt
│       │   │               │   └── service/            # 服务类
│       │   │               │       └── LocalLLMService.kt  # 本地LLM服务
│       │   │               ├── di/                     # 依赖注入
│       │   │               │   ├── DatabaseModule.kt   # 数据库模块
│       │   │               │   ├── MediaModule.kt      # 媒体模块
│       │   │               │   └── NetWorkModule.kt    # 网络模块
│       │   │               ├── ui/                     # UI层
│       │   │               │   ├── MainViewModel.kt     # 主视图模型
│       │   │               │   ├── chat/               # 聊天相关界面
│       │   │               │   │   ├── ChatBubbleParts.kt
│       │   │               │   │   ├── ChatScreen.kt
│       │   │               │   │   ├── ChatViewModel.kt
│       │   │               │   │   ├── VoiceInputButton.kt
│       │   │               │   │   └── list/
│       │   │               │   │       ├── ChatListScreen.kt
│       │   │               │   │       └── ChatListViewModel.kt
│       │   │               │   ├── create/             # 创建相关界面
│       │   │               │   │   ├── CreatePersonaScreen.kt
│       │   │               │   │   ├── CreatePersonaViewModel.kt
│       │   │               │   │   ├── CreatePostScreen.kt
│       │   │               │   │   └── CreatePostViewModel.kt
│       │   │               │   ├── detail/             # 详情相关界面
│       │   │               │   │   ├── PersonaDetailScreen.kt
│       │   │               │   │   ├── PersonaDetailViewModel.kt
│       │   │               │   │   ├── PostDetailScreen.kt
│       │   │               │   │   └── PostDetailViewModel.kt
│       │   │               │   ├── feed/               # 动态/广场界面
│       │   │               │   │   ├── PostFeedScreen.kt
│       │   │               │   │   ├── PostFeedViewModel.kt
│       │   │               │   │   ├── SocialFeedScreen.kt
│       │   │               │   │   └── SocialFeedViewModel.kt
│       │   │               │   ├── login/              # 登录/注册界面
│       │   │               │   │   ├── LoginScreen.kt
│       │   │               │   │   ├── LoginViewModel.kt
│       │   │               │   │   ├── RegisterScreen.kt
│       │   │               │   │   └── RegisterViewModel.kt
│       │   │               │   ├── notification/        # 通知界面
│       │   │               │   │   ├── NotificationScreen.kt
│       │   │               │   │   └── NotificationViewModel.kt
│       │   │               │   ├── profile/            # 个人资料界面
│       │   │               │   │   ├── EditProfileScreen.kt
│       │   │               │   │   ├── EditProfileViewModel.kt
│       │   │               │   │   ├── ProfileScreen.kt
│       │   │               │   │   ├── ProfileUiState.kt
│       │   │               │   │   ├── SettingsScreen.kt
│       │   │               │   │   └── SettingsViewModel.kt
│       │   │               │   ├── screens/            # 其他屏幕
│       │   │               │   │   └── SocialFeedScreen.kt
│       │   │               │   └── theme/              # 主题和样式
│       │   │               │       ├── Color.kt
│       │   │               │       ├── Theme.kt
│       │   │               │       └── Type.kt
│       │   │               └── utils/                  # 工具类
│       │   │                   ├── AudioPlayerManager.kt
│       │   │                   ├── AudioRecorderManager.kt
│       │   │                   ├── CrashHandler.kt
│       │   │                   ├── DateUtils.kt
│       │   │                   └── UriUtils.kt
│       │   └── res/              # 资源文件
│       │       ├── drawable/      # 可绘制资源
│       │       │   ├── ic_launcher_background.xml
│       │       │   └── ic_launcher_foreground.xml
│       │       ├── mipmap-*/      # 应用图标资源
│       │       │   ├── ic_launcher.webp
│       │       │   └── ic_launcher_round.webp
│       │       ├── values/        # 值资源
│       │       │   ├── colors.xml
│       │       │   ├── strings.xml
│       │       │   └── themes.xml
│       │       └── xml/           # XML资源
│       │           ├── backup_rules.xml
│       │           └── data_extraction_rules.xml
│       ├── androidTest/           # Android测试
│       │   └── java/
│       └── test/                  # 单元测试
│           └── java/
├── build/                        # 项目构建输出
│   └── reports/                  # 构建报告
│       └── problems/
│           └── problems-report.html
├── build.gradle.kts              # 项目构建脚本
├── gradle/                       # Gradle配置
│   ├── libs.versions.toml        # 版本目录
│   └── wrapper/                  # Gradle包装器
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── gradle.properties             # Gradle属性
├── gradlew                       # Gradle包装器脚本(Unix)
├── gradlew.bat                   # Gradle包装器脚本(Windows)
├── local.properties              # 本地属性
└── settings.gradle.kts           # Gradle设置
```

### 架构概述

项目采用 MVVM (Model-View-ViewModel) 架构模式，结合 Clean Architecture 原则，实现了清晰的代码分层和职责分离。

#### 数据层 (Data Layer)
- **本地数据源**: 使用 Room 数据库存储持久化数据，DataStore 存储用户偏好设置
  - **远程数据源**: 使用 Retrofit 进行网络请求，与后端 API 交互
  - **数据仓库**: 实现 Repository 模式，统一数据访问接口，为上层提供单一数据源
  - **数据模型**: 定义应用中使用的各种数据结构和实体

#### 依赖注入 (Dependency Injection)
- 使用 Hilt 框架进行依赖注入，实现松耦合设计
  - DatabaseModule: 提供数据库和 DAO 实例
  - NetworkModule: 提供网络相关实例
  - MediaModule: 提供媒体处理相关实例

#### UI层 (UI Layer)
- **聊天相关**: 聊天界面、聊天列表、语音输入等
  - **创建相关**: 智能体创建、动态发布等
  - **详情相关**: 智能体详情、动态详情等
  - **动态/广场**: 社交动态、智能体广场等
  - **登录/注册**: 用户认证相关界面
  - **个人资料**: 用户资料、设置等
  - **主题和样式**: 应用 UI 主题和样式定义

#### 工具类 (Utils)
- 音频播放和录制管理
  - 异常处理
  - 日期工具
  - URI 工具

### 核心技术栈

- **UI框架**: Jetpack Compose
  - **架构组件**: ViewModel, LiveData, Room, DataStore
  - **依赖注入**: Hilt
  - **网络请求**: Retrofit + OkHttp
  - **本地数据库**: Room
  - **异步处理**: Kotlin Coroutines + Flow
  - **AI模型**: MediaPipe (本地LLM)
  - **图片加载**: Coil
  - **导航**: Navigation Compose

## 项目结构详解

### 数据层 (Data Layer)

#### 本地数据源
- **AppDatabase.kt**: Room 数据库主类，包含所有实体和DAO
  - **实体类**:
    - `PersonaEntity`: 智能体实体
    - `ChatMessageEntity`: 聊天消息实体
    - `PostEntity`: 动态实体
    - `UserMemoryEntity`: 用户记忆实体
  - **DAO接口**:
    - `PersonaDao`: 智能体数据访问对象
    - `ChatDao`: 聊天数据访问对象
    - `PostDao`: 动态数据访问对象
    - `UserMemoryDao`: 用户记忆数据访问对象

#### 远程数据源
- **服务接口**:
  - `AuthService`: 认证服务
  - `PersonaService`: 智能体服务
  - `ChatService`: 聊天服务
  - `PostService`: 动态服务
  - **拦截器**: `AuthInterceptor` 用于自动添加认证头

#### 数据仓库
- `PersonaRepository`: 智能体数据仓库
  - `ChatRepository`: 聊天数据仓库
  - `PostRepository`: 动态数据仓库

### UI层 (UI Layer)

#### 主要界面
- **SocialFeedScreen**: 智能体广场界面，展示推荐和全部智能体
  - **ChatScreen**: 聊天界面，支持多模态输入和本地/云端模式切换
  - **CreatePersonaScreen**: 创建/编辑智能体界面
  - **CreatePostScreen**: 创建动态界面
  - **ProfileScreen**: 个人资料界面
  - **LoginScreen/RegisterScreen**: 登录/注册界面

#### ViewModel
- 每个主要界面都有对应的 ViewModel，负责处理业务逻辑和状态管理
  - 使用 Hilt 进行依赖注入，实现 ViewModel 的创建和管理

### 依赖注入 (Dependency Injection)

- **DatabaseModule**: 提供数据库和DAO实例
  - **NetworkModule**: 提供网络相关实例(Retrofit, OkHttpClient等)
  - **MediaModule**: 提供媒体处理相关实例

## 核心功能实现

### 本地AI服务 (LocalLLMService)

应用支持本地AI模式，使用 MediaPipe 的 LLM 推理功能：

```kotlin
class LocalLLMService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val memoryDao: UserMemoryDao,
    private val personaDao: PersonaDao
) {
    // 初始化本地LLM模型
    suspend fun initModel(): Boolean
    
    // 生成回复
    fun generateResponse(userId: Long, personaId: Long, userContent: String): Flow<String>
    
    // 总结和保存记忆
    suspend fun summarizeAndSaveMemory(userId: Long, personaId: Long, chatContent: String)
}
```

### 多模态聊天支持

聊天界面支持多种输入方式：
- 文本输入
  - 图片上传和生成
  - 语音录制和播放

### 记忆系统

应用实现了智能记忆系统，AI能够：
- 定期总结对话内容
  - 提取用户偏好和重要信息
  - 在后续对话中利用这些记忆提供更个性化的回复

### 核心功能实现

#### 1. 智能体系统
- **智能体创建**: 用户可以创建具有不同性格和能力的AI智能体，包括设置头像、名称、标签等
  - **智能体广场**: 展示社区热门智能体，支持浏览、搜索和交互
  - **智能体详情**: 查看智能体详细信息，包括背景故事、能力特点等
  - **推荐系统**: 基于用户兴趣和行为推荐相关智能体

#### 2. 聊天系统
- **多模态交互**: 支持文本、语音、图片等多种输入方式
  - **云端/端侧模式**: 支持云端大模型和本地小模型两种模式
  - **记忆系统**: 智能体具有记忆能力，可以记住之前的对话内容
  - **语音输入/输出**: 支持语音转文字和文字转语音功能

#### 3. 社交动态
- **发布动态**: 用户可以发布包含文字、图片的社交动态
  - **动态流**: 展示关注智能体和用户的最新动态
  - **互动功能**: 支持点赞、评论、分享等社交互动
  - **推荐内容**: 基于用户兴趣推荐相关动态内容

#### 4. 本地LLM集成
- **Gemma模型**: 集成Google Gemma轻量级语言模型
  - **CPU运行**: 支持在移动设备CPU上运行本地模型
  - **记忆管理**: 实现了智能的记忆总结和管理机制
  - **性能优化**: 针对移动设备进行了性能优化

## 环境配置

### 开发环境要求

- Android Studio Hedgehog | 2023.1.1 或更高版本
  - JDK 17
  - Android SDK API 26+
  - Kotlin 1.9.23

### 项目配置

项目使用以下关键配置：

1. **Gradle配置**:
   - Kotlin编译选项: jvmTarget = "17"
   - Compose编译器版本: "1.5.11"
   - KSP用于注解处理

   2. **依赖版本** (详见 `gradle/libs.versions.toml`):
      - Compose BOM: 2024.05.00
      - Hilt: 2.51.1
      - Room: 2.6.1
      - Retrofit: 2.9.0

   3. **网络配置**:
      - 开发环境自动检测模拟器/真机并配置相应的基础URL
      - 支持HTTP明文流量 (仅开发环境)

## 构建与运行

### 克隆项目

```bash
git clone [项目地址]
cd Persona
```

### 配置本地LLM模型

1. 下载 `gemma-1.1-2b-it-gpu-int4.bin` 模型文件
   2. 将模型文件放置在设备/模拟器的 `files` 目录下
   3. 应用启动时会自动检测并加载模型

### 构建和运行

1. 使用 Android Studio 打开项目
   2. 等待 Gradle 同步完成
   3. 连接 Android 设备或启动模拟器
  如果是真机请执行：
  adb reverse  tcp:8080  tcp:8080       
   5. 点击运行按钮构建并安装应用

## 项目特色与未来展望

### 项目特色

1. **多模态AI交互**: 结合文本、语音和图像，提供丰富的交互体验
   2. **本地AI能力**: 集成轻量级本地模型，支持离线使用，保护用户隐私
   3. **个性化智能体**: 每个智能体具有独特的个性和记忆能力
   4. **社交化体验**: 融合社交元素，打造AI驱动的社区平台
   5. **现代Android架构**: 采用最新的Android开发技术和架构模式

### 技术亮点

- **Jetpack Compose**: 使用声明式UI框架，提供流畅的用户体验
  - **MVVM架构**: 清晰的代码结构，易于维护和扩展
  - **依赖注入**: 使用Hilt实现松耦合设计
  - **响应式编程**: 采用Kotlin Coroutines和Flow处理异步操作
  - **本地存储**: 使用Room数据库和DataStore管理数据

### 未来展望

1. **更多AI模型**: 支持更多本地和云端AI模型
   2. **智能体进化**: 实现智能体的自主学习和进化能力
   3. **社区功能**: 增强社区互动和用户生成内容
   4. **跨平台支持**: 扩展到iOS和Web平台
   5. **企业应用**: 开发面向企业场景的定制化解决方案

---

## 贡献指南

欢迎贡献代码！请遵循以下步骤：

1. Fork 本仓库
   2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
   3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
   4. 推送到分支 (`git push origin feature/AmazingFeature`)
   5. 开启 Pull Request

## 许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情

## 联系方式

如有问题或建议，请通过以下方式联系：

- 项目主页: [项目GitHub链接]
  - 问题反馈: [Issues链接]
  - 邮箱: [联系邮箱]

---

**感谢您对Persona项目的关注！**

## 注意事项

1. **权限要求**:
   - 网络访问权限 (INTERNET)
   - 录音权限 (RECORD_AUDIO)

   2. **本地LLM要求**:
      - 本地LLM功能需要较新的Android设备
      - 建议至少4GB RAM以确保流畅运行
      - 首次运行需要下载模型文件

   3. **网络依赖**:
      - 部分功能需要网络连接
      - 云端模式需要稳定的网络环境

   4. **隐私说明**:
      - 本地模式下所有数据均在设备本地处理
      - 云端模式下对话数据会被发送到服务器进行处理
      - 用户数据按照隐私政策进行处理和保护

## 贡献指南

欢迎贡献代码！请遵循以下步骤：

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

## 许可证

本项目采用 MIT 许可证 - 详见 [LICENSE](LICENSE) 文件

## 联系方式

如有问题或建议，请通过以下方式联系：

- 提交 Issue
- 发送邮件至 [3326498228@qq.com]

---

感谢您对 Persona 项目的关注！
