package com.example.pet; // 声明包名，与目录结构保持一致

import javafx.animation.AnimationTimer; // 导入帧动画计时器，用于逐帧驱动甩动物理
import javafx.geometry.Rectangle2D; // 导入矩形类，用于获取屏幕可视区域
import javafx.stage.Screen; // 导入屏幕信息类，支持多屏与可视区域计算
import javafx.stage.Stage; // 导入窗口类，甩动时直接修改窗口坐标
import java.util.ArrayDeque; // 导入双端队列，用于保存最近一段时间的拖动采样

/** 只在甩动期间启用约 30fps 更新；拖动速度取最近 120ms，停顿后松手不会飞。 */
final class PetMotion { // 拖动采样与甩动物理的驱动类，包内可见、不可继承
    private record Sample(double x, double y, long time) { } // 私有记录：一次拖动采样的窗口坐标与时间戳（纳秒）
    private final ArrayDeque<Sample> samples = new ArrayDeque<>(); // 采样队列，按时间顺序保存最近的采样点
    private final Stage stage; // 主窗口引用
    private final PetCharacter character; // 角色引用，用于在甩动时切换表情与姿态
    private final Runnable settled; // 甩动结束（完全静止）后的回调，通常用于保存设置
    private AnimationTimer timer; // 甩动期间运行的帧计时器，非甩动状态为 null
    PetMotion(Stage stage, PetCharacter character, Runnable settled) { // 构造方法：保存窗口、角色与结束回调
        this.stage = stage; this.character = character; this.settled = settled; // 分别赋值到字段
    }
    void begin() { stop(); samples.clear(); sample(); } // 开始一次可能的拖动：先停止上一次甩动，清空采样并记录起点
    void sample() { // 记录一次当前位置与时间的采样
        long now = System.nanoTime(); // 取当前纳秒时间戳
        samples.addLast(new Sample(stage.getX(), stage.getY(), now)); // 把当前窗口坐标与时间戳追加到队尾
        while (samples.size() > 1 && now - samples.getFirst().time() > 120_000_000L) samples.removeFirst(); // 丢弃 120ms 之前的旧采样，只保留最近一段时间的轨迹
    }
    boolean release() { // 松手时调用：计算速度，决定是否进入甩动，返回是否真的甩出去了
        sample(); // 补一次松手瞬间的采样
        Sample first = samples.getFirst(), last = samples.getLast(); // 取队列中最早与最新的采样点
        double seconds = (last.time() - first.time()) / 1e9; // 计算两点之间的时间差（秒）
        double vx = seconds > 0.012 ? (last.x() - first.x()) / seconds : 0; // 采样间隔足够时算水平速度，太短则视为 0
        double vy = seconds > 0.012 ? (last.y() - first.y()) / seconds : 0; // 同理计算垂直速度
        samples.clear(); // 清空采样队列，为下一次拖动做准备
        if (Math.hypot(vx, vy) < 160) { character.resumeIdle(); return false; } // 速度低于 160 像素/秒不甩动，直接恢复待机并返回 false
        Rectangle2D b = Screen.getScreensForRectangle(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight()) // 查找窗口所在屏幕
                .stream().findFirst().orElse(Screen.getPrimary()).getVisualBounds(); // 取第一个匹配屏幕或回退主屏幕，再取可视区域
        TossModel model = new TossModel(stage.getX(), stage.getY(), vx, vy, // 用当前位置与初速度创建物理模型
                b.getMinX(), b.getMinY(), b.getMaxX() - stage.getWidth(), b.getMaxY() - stage.getHeight()); // 传入可运动边界（右/下边界需减去窗口尺寸）
        character.expression("surprised"); // 甩动开始时切换成惊讶表情
        timer = new AnimationTimer() { // 创建逐帧驱动的物理动画
            private long previous; // 上一帧的时间戳，用于计算帧间隔
            @Override public void handle(long now) { // 每帧回调，now 为当前纳秒时间
                if (previous == 0) { previous = now; return; } // 第一帧只记录时间，不做计算
                double dt = (now - previous) / 1e9; // 换算本帧经过的秒数
                if (dt < 1.0 / 30) return; // 限制到约 30fps，未到间隔直接跳过本帧
                previous = now; // 更新上一帧时间戳
                boolean hit = model.step(Math.min(0.10, dt)); // 推进物理模型，单帧最多推进 0.1 秒，返回是否撞到边界
                stage.setX(model.x); stage.setY(model.y); // 把模型计算出的新位置应用到窗口
                character.setRotate(Math.max(-10, Math.min(10, model.vx / 100))); // 按水平速度让角色倾斜，角度夹取在 ±10 度
                if (hit) character.collision(); // 撞到边界时播放挤压与头发甩动反馈
                if (model.stopped()) { PetMotion.this.stop(); character.resumeIdle(); settled.run(); } // 速度衰减到停止后结束动画、恢复待机并执行结束回调
            }
        };
        timer.start(); // 启动帧计时器，开始甩动
        return true; // 返回 true 表示本次进入甩动状态
    }
    boolean isMoving() { return timer != null; } // 通过计时器是否存在判断是否正在甩动
    void stop() { // 立即停止甩动并复位角色姿态
        if (timer != null) { timer.stop(); timer = null; } // 停止并清空计时器
        character.setRotate(0); // 复位倾斜角度
        character.setScaleX(1); character.setScaleY(1); // 复位缩放，消除可能的挤压变形
    }
}
