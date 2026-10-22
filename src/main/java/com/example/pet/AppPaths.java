package com.example.pet;
import java.nio.file.*;

/** 打包后从 EXE 所在目录读取素材；配置和缓存写入每个用户自己的目录。 */
final class AppPaths {
    private AppPaths() { }
    static Path home() {
        String override = System.getProperty("desktop.pet.home");
        if (override != null && !override.isBlank()) return Path.of(override).toAbsolutePath().normalize();
        String executable = System.getProperty("jpackage.app-path");
        if (executable != null && !executable.isBlank()) return Path.of(executable).toAbsolutePath().getParent();
        try {
            Path code = Path.of(AppPaths.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath();
            Path candidate = Files.isDirectory(code) ? code : code.getParent();
            for (int i=0; candidate!=null && i<6; i++,candidate=candidate.getParent()) {
                if (Files.isRegularFile(candidate.resolve("pets/default/pet.png"))) return candidate;
            }
        } catch (Exception ignored) { }
        return Path.of("").toAbsolutePath().normalize();
    }
    static Path pets() { return home().resolve("pets"); }
    static Path data() {
        String override = System.getenv("DESKTOP_PET_DATA_DIR");
        if (override != null && !override.isBlank()) return Path.of(override).toAbsolutePath().normalize();
        String local = System.getenv("LOCALAPPDATA");
        if (local != null && !local.isBlank()) return Path.of(local).resolve("DesktopPet");
        return Path.of(System.getProperty("user.home"), "AppData", "Local", "DesktopPet");
    }
}