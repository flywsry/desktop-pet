package com.example.pet; // 声明包名，与目录 src/main/java/com/example/pet 对应

import java.nio.file.Path; // 导入路径类，用于拼接并解析缓存目录路径

/** Run this plain Java entry point in IDEA instead of PetApp. */
public final class Launcher { // 普通 Java 启动入口类；声明为 final 表示不允许被继承
    private Launcher() { } // 私有构造方法，禁止外部实例化，说明它只是静态入口

    public static void main(String[] args) { // JVM 程序入口方法，args 为命令行参数
        try {
            java.nio.file.Files.createDirectories(AppPaths.data());
            java.io.PrintStream log = new java.io.PrintStream(new java.io.FileOutputStream(
                    AppPaths.data().resolve("desktop-pet.log").toFile(), false), true, java.nio.charset.StandardCharsets.UTF_8);
            System.setOut(log); System.setErr(log);
            System.out.println("DesktopPet 0.1.1; assets=" + AppPaths.home() + "; java=" + System.getProperty("java.home"));
        } catch (java.io.IOException e) { System.err.println("日志目录不可写：" + e.getMessage()); }
        System.setProperty("javafx.cachedir", // 设置 JavaFX 使用的缓存目录系统属性
                AppPaths.data().resolve("javafx-cache").toString()); // 指向项目下的 .javafx-cache 绝对路径，避免写用户目录失败
        javafx.application.Application.launch(PetApp.class, args); // 以 PetApp 为 Application 子类启动 JavaFX 生命周期，并透传命令行参数
    }
}
