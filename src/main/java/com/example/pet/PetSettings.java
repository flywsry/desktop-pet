package com.example.pet; // 声明包名，与目录结构保持一致
import java.io.*; // 批量导入 java.io 的类：InputStream、OutputStream、IOException 等
import java.nio.file.*; // 批量导入 java.nio.file 的类：Path、Files、StandardCopyOption、AtomicMoveNotSupportedException 等
import java.util.Properties; // 导入 Properties，用于读写 key=value 形式的设置文件
/** 设置保存在项目文件夹中，只有操作结束时写入。 */
final class PetSettings { // 桌宠设置持久化类，包内可见、不可继承
    private final Path file = AppPaths.data().resolve("settings.properties"); // 设置文件路径：项目目录下 config/settings.properties
    private final Properties values = new Properties(); // 内存中的设置键值对
    PetSettings() { // 构造方法：创建对象时即尝试载入已保存的设置
        if (Files.isRegularFile(file)) { // 仅当设置文件存在时读取，首次运行直接使用默认值
            try (InputStream in = Files.newInputStream(file)) { values.load(in); } // 以字节流打开并解析 properties 内容
            catch (IOException | IllegalArgumentException e) { System.err.println("读取配置失败：" + e.getMessage()); } // 读取或格式出错时打印提示，继续使用默认值
        }
    }
    double number(String key, double fallback) { // 读取数值型设置，缺省或非法时返回兜底值
        try {
            double value = Double.parseDouble(values.getProperty(key, "")); // 取出字符串并解析为 double
            return Double.isFinite(value) ? value : fallback; // 只有有限数才采用，NaN/无穷大退回兜底值
        } catch (NumberFormatException e) { return fallback; } // 缺项或格式错误时返回兜底值
    }
    boolean fixed() { return Boolean.parseBoolean(values.getProperty("fixed", "false")); } // 读取"固定位置"开关，缺省关闭
    void fixed(boolean value) { values.setProperty("fixed", Boolean.toString(value)); } // 写入"固定位置"开关
    int size() { // 读取窗口/角色尺寸
        int size = (int) number("size", 192); // 取出数值并取整，缺省 192
        return size == 128 || size == 192 || size == 256 ? size : 192; // 只允许三档预设尺寸，其他值一律回退为 192
    }
    void save(double x, double y, int size) { // 把当前位置与尺寸写入设置文件
        values.setProperty("x", Double.toString(x)); // 记录窗口左上角 X 坐标
        values.setProperty("y", Double.toString(y)); // 记录窗口左上角 Y 坐标
        values.setProperty("size", Integer.toString(size)); // 记录当前角色尺寸
        try {
            Files.createDirectories(file.getParent()); // 确保 config 目录存在，不存在则创建
            Path temporary = file.resolveSibling("settings.properties.tmp"); // 在同级目录准备一个临时文件用于原子替换
            try (OutputStream out = Files.newOutputStream(temporary)) { values.store(out, "Desktop pet preferences"); } // 先把全部设置写入临时文件并加注释头
            try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); } // 优先使用原子移动替换旧文件，避免写一半损坏配置
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); } // 文件系统不支持原子移动时退化为普通覆盖移动
        } catch (IOException e) { System.err.println("保存配置失败：" + e.getMessage()); } // 任何 IO 异常都只打印提示，不影响程序继续运行
    }
}
