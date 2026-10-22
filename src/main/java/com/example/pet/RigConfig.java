package com.example.pet; // 声明包名，与目录结构保持一致
import java.io.*; // 批量导入 java.io 下的类：Reader、IOException 等
import java.nio.charset.StandardCharsets; // 导入字符集常量，明确按 UTF-8 读写配置
import java.nio.file.*; // 批量导入 java.nio.file 下的类：Path、Files、StandardCopyOption 等
import java.util.Properties; // 导入 Properties，用于读取 key=value 形式的配置

/** 角色绑定数据独立于拖拽、托盘和物理逻辑。所有坐标均为画布宽高的 0～1 比例。 */
final class RigConfig { // 骨骼绑定配置类，包内可见、不可继承
    private final Properties values = new Properties(); // 存放从 properties 文件读入的键值对
    static RigConfig load(Path path) { // 静态工厂方法：从指定路径加载配置，始终返回一个可用实例
        RigConfig rig = new RigConfig(); // 先创建空配置对象作为兜底
        if (Files.isRegularFile(path)) { // 仅当路径确实是一个普通文件时才尝试读取
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { rig.values.load(reader); } // 以 UTF-8 打开并按 properties 格式解析
            catch (IOException | IllegalArgumentException e) { System.err.println("绑定配置读取失败，使用静态显示：" + e.getMessage()); rig.values.clear(); } // 读失败或格式非法时打印提示并清空，退回静态显示
        }
        return rig; // 返回加载结果（可能是空配置）
    }
    boolean enabled() { return Boolean.parseBoolean(values.getProperty("rig.enabled", "false")); } // 读取 rig.enabled，缺省 false，表示是否启用分部件绑定
    boolean faceEnabled() { return Boolean.parseBoolean(values.getProperty("face.enabled", "false")); } // 读取 face.enabled，缺省 false，表示是否绘制动态表情
    double n(String key, double fallback) { // 读取任意数值配置，失败时返回兜底值
        try { double v = Double.parseDouble(values.getProperty(key,"")); return Double.isFinite(v) ? v : fallback; } // 解析字符串为 double，只有在有限数（非 NaN/无穷）时才采用
        catch (NumberFormatException e) { return fallback; } // 解析异常说明缺项或格式错误，返回兜底值
    }
    double unit(String key, double fallback) { return Math.max(0, Math.min(1,n(key,fallback))); } // 读取归一化数值，并强制夹取到 0~1 区间
    double[] polygon(String key, double[] fallback) { // 读取多边形顶点数组，格式为"x,y x,y ..."的归一化坐标
        String raw = values.getProperty(key); // 取出原始字符串配置
        if (raw == null) return fallback.clone(); // 没有配置则返回兜底数组的副本，避免外部修改内部数组
        try {
            String[] parts = raw.trim().split("[,\\s]+"); // 去掉首尾空白后，按逗号或空白切分成单个数字
            if (parts.length < 6 || parts.length % 2 != 0) return fallback.clone(); // 少于三个点或坐标个数不成对，视为非法
            double[] result = new double[parts.length]; // 按解析出的数量创建结果数组
            for (int i=0;i<parts.length;i++) { // 逐个解析坐标分量
                result[i]=Double.parseDouble(parts[i]); // 转换字符串为 double
                if (!Double.isFinite(result[i]) || result[i]<0 || result[i]>1) return fallback.clone(); // 非法数值或超出 0~1 范围则整体作废
            }
            return result; // 全部合法，返回解析结果
        } catch (NumberFormatException e) { return fallback.clone(); } // 解析过程中出现格式错误，返回兜底数组副本
    }
}
