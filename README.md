# 小桌宠 DesktopPet

Windows 桌宠，使用 Java 17 与 JavaFX 17。当前优化版本：0.1.1。

![角色预览](pets/default/pet.png)

## 功能
- 随机眨眼、张望、歪头和小憩，待机动作起始间隔 2.5 秒。
- 鼠标悬停关注、摸头微笑、身体点击反馈。
- 头部、身体、后发独立裁分与小幅跟随动画。
- 拖动与甩动：重力抛物线、边缘反弹、空气阻力及地面摩擦。
- 固定位置、三档大小、位置与大小记忆。
- 系统托盘显示、隐藏、退出；中文菜单使用 Swing 绘制。

## 开发运行
环境：JDK 17、Maven，或在 IntelliJ IDEA 中导入 pom.xml。

```shell
mvn javafx:run
```

IDEA 中运行 com.example.pet.Launcher，不要直接运行 PetApp。
本机 start.cmd 也是开发启动入口，其 JDK/Maven 路径可按本机环境调整。

## 目录
- src/main/java/com/example/pet：程序源码。
- pets/default/pet.png：角色原图。
- pets/default/rig.properties：分层轮廓、支点和五官比例坐标。
- pets/rig-template.properties：关闭专属绑定的通用模板。
- packaging/licenses：第三方组件的许可说明。
- build-portable.ps1：Windows 免安装包构建脚本。

素材按应用位置加载；个人设置、缓存和日志在 %LOCALAPPDATA%/DesktopPet。
源码仓库不包含安装包、运行时、缓存、依赖、个人设置或日志。

## 构建免安装包
先解析依赖，并准备解压后的 Windows x64 Java 17 运行时（例如 Eclipse Temurin）：

```powershell
mvn compile
.\build-portable.ps1 -JdkHome '你的JDK17目录' -RuntimeHome '解压后的Java17运行时目录'
```

输出到 release 下的新目录。把完整 DesktopPet 文件夹分发，不能只发 EXE。
此仓库保留构建脚本，不上传二进制运行时或打包 ZIP。

## 换图
同构图只改分辨率时，比例坐标通常不变。新角色需要调整 rig.properties 的轮廓、支点和五官。
构图差异较大时，可先关闭 rig.enabled 和 face.enabled 使用静态图片；窗口、物理与托盘功能可复用。
当前使用原图裁分，适合小幅动作；大幅转身需要补画独立图层。

## 操作
左键点击互动，按住拖动，快速拖动后松手甩出。
右键调整固定状态、大小或退出。Alt+F4 是隐藏到托盘，真正结束请选“退出”。