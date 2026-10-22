package com.example.pet; // 声明包名，与目录结构保持一致

import javafx.animation.*; // 批量导入动画类：Timeline、KeyFrame、KeyValue、Interpolator、PauseTransition、Animation
import javafx.beans.property.DoubleProperty; // 导入浮点属性接口，用于可监听的数值
import javafx.beans.property.SimpleDoubleProperty; // 导入浮点属性实现
import javafx.scene.canvas.Canvas; // 导入画布，用于绘制动态表情
import javafx.scene.canvas.GraphicsContext; // 导入画布绘图上下文
import javafx.scene.image.Image; // 导入图像类，角色原图
import javafx.scene.image.ImageView; // 导入图像视图，用于显示原图
import javafx.scene.image.WritableImage; // 导入可写图像，用于生成皮肤补片
import java.util.HashMap; // 导入哈希表，用于缓存皮肤补片
import java.util.Map; // 导入映射接口
import java.nio.file.Path; // 导入路径类，用于定位 rig.properties
import javafx.scene.layout.StackPane; // 导入层叠布局，作为本控件的基类
import javafx.scene.effect.DropShadow; // 导入外投影效果，模拟立体感
import javafx.scene.effect.InnerShadow; // 导入内阴影效果，模拟边缘高光
import javafx.scene.paint.*; // 批量导入绘制类：Color、CycleMethod、Stop、RadialGradient
import javafx.util.Duration; // 导入时间长度类，用于动画时刻
import java.util.concurrent.ThreadLocalRandom; // 导入随机数工具，用于随机化待机动作间隔

/** 当前粉发角色的轻量面部绑定：原图不变，在眼睛和嘴部叠加动态表情。 */
final class PetCharacter extends StackPane { // 角色控件，继承层叠布局，包内可见、不可继承
    private final ImageView imageView = new ImageView(); // 显示角色原图的视图（兜底显示，未启用绑定时使用）
    private final Canvas face = new Canvas(); // 叠加在头部的表情画布
    private final RigConfig rigConfig = RigConfig.load(AppPaths.pets().resolve("default/rig.properties")); // 加载骨骼/表情绑定配置
    private final PetRig rig = new PetRig(face, rigConfig); // 创建骨骼部件层，并共享同一张表情画布
    private final Map<String, Image> skinCache = new HashMap<>(); // 皮肤补片缓存，避免每帧重复采样原图像素
    private final DoubleProperty openness = new SimpleDoubleProperty(1); // 睁眼程度 1=全睁，0=闭合
    private final DoubleProperty gaze = new SimpleDoubleProperty(0); // 视线水平偏移（归一化）
    private final PauseTransition idleWait = new PauseTransition(); // 待机间隔计时器，到点播放一次随机小动作
    private final DropShadow depthShadow = new DropShadow(9, 2, 4, Color.rgb(25, 15, 40, .34)); // 角色投影，营造离屏立体感
    private Timeline gesture; // 当前正在播放的待机小动作动画
    private Timeline attention; // 鼠标注视时的眼神/头部跟随动画
    private boolean pointerInside; // 鼠标是否停在角色上
    private int lastGesture = -1; // 上次播放的小动作编号，避免连续重复
    private final PauseTransition blinkWait = new PauseTransition(); // 眨眼间隔计时器
    private final PauseTransition moodWait = new PauseTransition(Duration.seconds(1.1)); // 临时表情持续时间，1.1 秒后恢复普通表情
    private final Timeline blink; // 眨眼动画（闭眼再睁开）
    private final Timeline breathe; // 呼吸动画（驱动骨骼的 breath 属性）
    private Timeline squash; // 碰撞挤压动画
    private String mood = "normal"; // 当前表情：normal/happy/surprised
    private boolean active; // 角色是否处于活动状态（可播放动画）
    private double size = 192; // 当前显示尺寸，默认 192

    PetCharacter() { // 构造方法：初始化视图、效果与各类动画
        imageView.setPreserveRatio(true); // 原图保持宽高比，避免变形
        imageView.setSmooth(true); // 开启平滑缩放
        face.setMouseTransparent(true); // 表情画布不接收鼠标事件
        setPickOnBounds(false); // 透明区域不响应鼠标
        getChildren().add(rig); // 把骨骼层加入本控件（未启用绑定时它只显示完整原图）
        openness.addListener((o, a, b) -> drawFace()); // 睁眼程度变化时重绘表情
        gaze.addListener((o, a, b) -> drawFace()); // 视线偏移变化时重绘表情
        InnerShadow rim = new InnerShadow(); // 创建内阴影作为边缘高光
        rim.setColor(Color.rgb(255, 245, 255, .24)); rim.setRadius(2); // 设置高光颜色与模糊半径
        rim.setOffsetX(-1); rim.setOffsetY(-1); // 高光向左上偏移，模拟左上光源
        depthShadow.setInput(rim); setEffect(depthShadow); // 把内阴影作为投影的输入串联起来，并应用到本控件
        rotateProperty().addListener((o, a, b) -> depthShadow.setOffsetX(2 + b.doubleValue() * .30)); // 角色旋转时同步移动投影方向
        translateYProperty().addListener((o, a, b) -> { // 监听垂直位移（跳跃/甩动）
            double lift = Math.max(0, -b.doubleValue()); // 只取向上的位移量（负值越大表示抬得越高）
            depthShadow.setRadius(9 + lift * .7); depthShadow.setOffsetY(4 + lift * .8); // 抬得越高，投影越大越远，强化离地感
        });
        idleWait.setOnFinished(e -> playGesture()); // 待机计时结束后播放一次小动作
        blink = new Timeline(20, // 创建眨眼动画，20fps
                new KeyFrame(Duration.ZERO, new KeyValue(openness, 1)), // 起始：眼睛全睁
                new KeyFrame(Duration.millis(85), new KeyValue(openness, 0.06, Interpolator.EASE_IN)), // 85ms：快速闭眼
                new KeyFrame(Duration.millis(140), new KeyValue(openness, 0.06)), // 140ms：保持闭合
                new KeyFrame(Duration.millis(230), new KeyValue(openness, 1, Interpolator.EASE_OUT))); // 230ms：睁开并缓出
        blink.setOnFinished(e -> scheduleBlink()); // 一次眨眼结束后安排下一次眨眼
        blinkWait.setOnFinished(e -> { if (active && mood.equals("normal")) blink.playFromStart(); else scheduleBlink(); }); // 到点后若处于活动且普通表情则眨眼，否则重新等待
        breathe = new Timeline(12, // 创建呼吸动画，12fps
                new KeyFrame(Duration.ZERO, new KeyValue(rig.breath, 0)), // 起始：呼气状态
                new KeyFrame(Duration.seconds(1.8), new KeyValue(rig.breath, 1, Interpolator.EASE_BOTH))); // 1.8 秒过渡到吸气状态
        breathe.setAutoReverse(true); // 开启自动反向，形成往复呼吸
        breathe.setCycleCount(Animation.INDEFINITE); // 无限循环
        moodWait.setOnFinished(e -> expression("normal")); // 临时表情持续结束后恢复普通表情
        resizeCharacter(); // 按默认尺寸完成一次布局与绘制
    }
    void setImage(Image image) { skinCache.clear(); imageView.setImage(image); rig.setImage(image); drawFace(); } // 换图时清空皮肤缓存、同步到原图视图与骨骼层并立即重绘表情
    Image getImage() { return imageView.getImage(); } // 返回当前使用的原图
    void setPreserveRatio(boolean value) { imageView.setPreserveRatio(value); } // 透传设置：是否保持宽高比
    void setSmooth(boolean value) { imageView.setSmooth(value); } // 透传设置：是否平滑缩放
    double getFitWidth() { return size; } // 返回当前显示宽度
    double getFitHeight() { return size; } // 返回当前显示高度
    void setFitWidth(double value) { size = value; resizeCharacter(); } // 设置宽度并按新尺寸重排
    void setFitHeight(double value) { size = value; resizeCharacter(); } // 设置高度并按新尺寸重排
    private void resizeCharacter() { // 按当前尺寸调整各层大小
        imageView.setFitWidth(size); imageView.setFitHeight(size); // 原图视图缩放到当前尺寸
        face.setWidth(size); face.setHeight(size); rig.resizeRig(size); // 表情画布与骨骼层同步尺寸
        setMinSize(size, size); setPrefSize(size, size); setMaxSize(size, size); // 把本控件固定为正方形
        drawFace(); // 重新绘制表情
    }
    void resumeIdle() { // 启动（或恢复）全部待机动画
        suspend(); active = true; expression("normal"); // 先清理旧动画，再置为活动状态并恢复普通表情
        breathe.playFromStart(); scheduleBlink(); scheduleGesture(); // 依次启动呼吸、安排眨眼、安排待机小动作
    }
    void suspend() { // 暂停并复位所有动画与姿态
        active = false; pointerInside = false; if (attention != null) { attention.stop(); attention = null; } idleWait.stop(); stopGesture(); blinkWait.stop(); blink.stop(); breathe.stop(); moodWait.stop(); // 停止注视、待机、眨眼、呼吸与表情计时
        if (squash != null) { squash.stop(); squash = null; } // 停止并清空挤压动画
        rig.reset(); openness.set(1); setScaleX(1); setScaleY(1); setRotate(0); setTranslateY(0); setTranslateX(0); gaze.set(0); // 复位骨骼与控件变换、睁眼与视线
    }
    private void scheduleBlink() { // 安排下一次眨眼
        if (!active) return; // 非活动状态不安排
        blinkWait.setDuration(Duration.seconds(ThreadLocalRandom.current().nextDouble(2.8, 5.8))); // 随机 2.8~5.8 秒的间隔
        blinkWait.playFromStart(); // 重新开始计时
    }
    void expression(String value) { mood = value; drawFace(); } // 切换表情并立即重绘
    void happy() { // 切换到开心表情（短暂）
        stopGesture(); expression("happy"); moodWait.playFromStart(); if (active) scheduleGesture(); // 停止小动作、设为开心、启动恢复计时，并继续安排后续待机动作
    }
    private void scheduleGesture() { // 安排下一次待机小动作
        if (!active || pointerInside) return; // 非活动或鼠标停留在角色上时不安排
        idleWait.setDuration(Duration.seconds(2.5)); // 待机动作起始间隔固定 2.5 秒
        idleWait.playFromStart(); // 重新开始计时
    }
    private void stopGesture() { // 停止并复位当前待机小动作
        if (gesture != null) { gesture.stop(); gesture = null; } // 停止动画并清空引用
        rig.headAngle.set(0); rig.bodyAngle.set(0); gaze.set(0); setRotate(0); setTranslateX(0); setScaleX(1); openness.set(1); // 复位头身角度、视线与控件变换
        expression("normal"); // 恢复普通表情
    }
    private KeyFrame look(double ms, double angle, double shift, double eye, double stretch, double open) { // 生成一个姿态关键帧，参数依次为时刻、头角、身体位移、视线、横向拉伸、睁眼度
        return new KeyFrame(Duration.millis(ms), // 关键帧所在时刻
                new KeyValue(rig.headAngle, angle, Interpolator.EASE_BOTH), // 头部角度
                new KeyValue(rig.bodyAngle, angle * .23, Interpolator.EASE_BOTH), // 身体角度按头角的 23% 联动
                new KeyValue(translateXProperty(), shift * .15, Interpolator.EASE_BOTH), // 整体水平位移（取传入值的 15%）
                new KeyValue(gaze, eye, Interpolator.EASE_BOTH), // 视线偏移
                new KeyValue(scaleXProperty(), stretch, Interpolator.EASE_BOTH), // 横向拉伸，配合挤压拉伸做弹性感
                new KeyValue(openness, open, Interpolator.EASE_BOTH)); // 睁眼程度
    }
    private void playGesture() { // 随机播放一个待机小动作
        if (!active) return; // 非活动状态直接返回
        blinkWait.stop(); blink.stop(); moodWait.stop(); stopGesture(); // 动作期间暂不眨眼、不恢复表情，并复位上一轮姿态
        int choice; // 选中的动作编号
        do { choice = ThreadLocalRandom.current().nextInt(3); } while (choice == lastGesture); // 随机选 0~2，避免与上次相同
        lastGesture = choice; // 记录本次编号
        double direction = ThreadLocalRandom.current().nextBoolean() ? 1 : -1; // 随机决定动作朝左还是朝右
        if (choice == 0) {
            // 好奇张望：眼睛先看过去，再轻轻倾身，然后看向另一侧。
            gesture = new Timeline(18, look(0,0,0,0,1,1), // 起始姿态
                    look(260,0,0,.003 * direction,1,1), // 260ms：视线先转向一侧
                    look(650,2.8 * direction,1.8 * direction,.003 * direction,1,1), // 650ms：头部与身体跟着倾过去
                    look(1300,2.8 * direction,1.8 * direction,.003 * direction,1,1), // 1300ms：保持张望
                    look(1850,-1.8 * direction,-1.2 * direction,-.003 * direction,1,1), // 1850ms：转向另一侧
                    look(2350,0,0,0,1,1)); // 2350ms：回到正中
        } else if (choice == 1) {
            // 眯眼撒娇：慢慢歪头再回正，避免机械地匀速摆动。
            expression("happy"); // 切换为开心表情（眯眼 + 腮红）
            gesture = new Timeline(18, look(0,0,0,0,1,1), // 起始姿态
                    look(480,3.6 * direction,1.5 * direction,0,1.008,1), // 480ms：歪头并轻微拉伸
                    look(1100,3.6 * direction,1.5 * direction,0,1.008,1), // 1100ms：保持歪头
                    look(1550,-1 * direction,0,0,1,1),look(1900,0,0,0,1,1)); // 先回摆一点再完全回正
        } else {
            // 小憩伸展：半闭眼、短暂闭眼，醒来后恢复原姿态。
            gesture = new Timeline(18, look(0,0,0,0,1,1), // 起始姿态
                    look(500,-1.8 * direction,0,0,1.018,.45), // 500ms：低头并半闭眼，身体略微拉伸
                    look(950,-1.8 * direction,0,0,1.018,.06), // 950ms：几乎完全闭眼
                    look(1500,.8 * direction,0,0,.995,1),look(1950,0,0,0,1,1)); // 醒来时轻微反向回弹再复位
        }
        gesture.setOnFinished(e -> { gesture = null; stopGesture(); if (active) { scheduleBlink(); } }); // 动作结束后复位并重新安排眨眼与下一次动作
        scheduleGesture(); // 从本次动作开始计时，避免动作时长再叠加 2.5 秒
        gesture.play(); // 播放该动作
    }
    void pointerEnter() { // 鼠标移入角色
        if (!active) return; // 非活动状态不处理
        pointerInside = true; idleWait.stop(); stopGesture(); // 标记鼠标在内，暂停待机动作让位给注视跟随
    }
    void pointerMove(double localX) { // 鼠标在角色上移动，localX 为角色本地横坐标
        if (!active || !mood.equals("normal")) return; // 非活动或非普通表情时不跟随
        if (!pointerInside) pointerEnter(); // 若尚未标记进入则先进入注视状态
        double target = Math.max(-.003, Math.min(.003, (localX / size - .5) * .012)); // 按鼠标相对中心的位置算出目标视线偏移，并夹取到 ±0.003
        if (Math.abs(target - gaze.get()) < .0004) return; // 变化太小则忽略，避免频繁重建动画
        if (attention != null) attention.stop(); // 停掉上一次跟随动画
        attention = new Timeline(20, new KeyFrame(Duration.millis(160), new KeyValue(gaze, target, Interpolator.EASE_OUT), new KeyValue(rig.headAngle, target * 650, Interpolator.EASE_OUT), new KeyValue(rig.bodyAngle, target * 100, Interpolator.EASE_OUT))); // 160ms 内平滑地把视线、头部角度和身体角度过渡到目标值
        attention.play(); // 播放跟随动画
    }
    void pointerLeave() { // 鼠标移出角色
        pointerInside = false; // 清除鼠标在内标记
        if (!active) return; // 非活动状态直接返回
        if (attention != null) attention.stop(); // 停掉跟随动画
        attention = new Timeline(20, new KeyFrame(Duration.millis(260), new KeyValue(gaze, 0, Interpolator.EASE_BOTH), new KeyValue(rig.headAngle, 0, Interpolator.EASE_BOTH), new KeyValue(rig.bodyAngle, 0, Interpolator.EASE_BOTH))); // 260ms 内把视线与头身角度平滑归零
        attention.play(); scheduleGesture(); // 播放归位动画并重新安排待机小动作
    }
    void touched(boolean head) { // 被点击时触发表情，head 为 true 表示点到头部
        if (head) happy(); // 摸头：切换到开心表情
        else { stopGesture(); expression("surprised"); moodWait.playFromStart(); if (active) scheduleGesture(); } // 点身体：惊讶表情，1.1 秒后恢复，并继续安排待机动作
    }
    void held(double dx) { // 被拖动时的姿态，dx 为水平位移
        setRotate(Math.max(-4, Math.min(4, dx / 24))); // 整体倾斜，角度按位移计算并夹取到 ±4 度
        rig.headAngle.set(Math.max(-2, Math.min(2, -dx / 90))); rig.bodyAngle.set(Math.max(-1, Math.min(1, dx / 120))); // 头部与身体做小幅反向/同向摆动
        setScaleX(.985); setScaleY(1.015); // 横向压缩、纵向拉伸，表现被拎起来的形变
    }
    void collision() { // 撞到屏幕边界时的反馈
        rig.impulse(getRotate() >= 0 ? -2.5 : 2.5); // 按当前倾斜方向给头发一个反向惯性冲击
        if (squash != null) squash.stop(); // 停掉上一次挤压动画
        squash = new Timeline(20, // 创建挤压动画，20fps
                new KeyFrame(Duration.ZERO, new KeyValue(scaleXProperty(), 1.035), new KeyValue(scaleYProperty(), 0.97)), // 第 0 帧：瞬间压扁变宽
                new KeyFrame(Duration.millis(170), new KeyValue(scaleXProperty(), 1, Interpolator.EASE_OUT), // 170ms：宽度回到 1
                        new KeyValue(scaleYProperty(), 1, Interpolator.EASE_OUT))); // 170ms：高度回到 1
        squash.play(); // 播放挤压动画
    }
    private void drawFace() { // 在画布上绘制当前表情
        GraphicsContext g = face.getGraphicsContext2D(); // 取绘图上下文
        g.clearRect(0, 0, size, size); // 清空整块画布
        Image image = imageView.getImage(); // 取当前原图，用于采样皮肤色
        if (image == null || image.getPixelReader() == null || !rigConfig.faceEnabled()) return; // 无图、无法读像素或未启用表情绘制时直接返回
        boolean changed = !mood.equals("normal") || openness.get() < 0.995 || Math.abs(gaze.get()) > .00001; // 判断是否有需要绘制的变化：非普通表情、正在眨眼或视线偏移
        if (!changed) return; // 完全普通状态时不绘制，保持原图纯净
        // 绑定当前原图的面部位置。只覆盖原眼睛的小区域，渐变颜色取自原图两侧皮肤。
        double[] eyes = {rigConfig.unit("eye.leftX",.371611), rigConfig.unit("eye.rightX",.53748)}; // 左右眼的归一化 X 坐标（可在配置中调整）
        for (double cx : eyes) { // 逐只眼睛绘制
            skin(g, image, cx - rigConfig.unit("eye.coverHalfWidth",.018), rigConfig.unit("eye.coverY",.433), rigConfig.unit("eye.coverWidth",.036), rigConfig.unit("eye.coverHeight",.080)); // 先用皮肤色补片盖住原图的眼睛区域
            double x = (cx + gaze.get()) * size, y = rigConfig.unit("eye.y",.473) * size; // 计算这只眼睛的中心坐标，叠加视线偏移
            g.setStroke(Color.web("#382622")); // 设置描边颜色（深棕）
            g.setFill(Color.web("#382622")); // 设置填充颜色（深棕）
            g.setLineWidth(Math.max(1, size * 0.006)); // 线宽随尺寸缩放，最小 1 像素
            if (mood.equals("happy")) { // 开心时画弯弯的笑眼
                g.beginPath(); g.moveTo(x - size * .012, y + size * .003); // 从左下起点开始路径
                g.quadraticCurveTo(x, y - size * .017, x + size * .012, y + size * .003); g.stroke(); // 用二次曲线画出上弯的弧线并描边
            } else {
                double open = mood.equals("surprised") ? 1.08 : openness.get(); // 惊讶时睁得更大，否则使用当前睁眼程度（眨眼时变小）
                double h = Math.max(size * .004, size * rigConfig.unit("eye.height",.068) * open); // 眼睛高度按睁眼程度缩放，并有最小高度
                g.fillOval(x - size * rigConfig.unit("eye.width",.026) / 2, y - h / 2, size * rigConfig.unit("eye.width",.026), h); // 以中心为基准绘制椭圆眼珠
            }
        }
        if (mood.equals("happy")) { // 开心时添加腮红
            g.setFill(new RadialGradient(0, 0, .5, .5, .5, true, CycleMethod.NO_CYCLE, // 创建径向渐变（中心到边缘）
                    new Stop(0, Color.rgb(244, 131, 155, .30)), new Stop(1, Color.TRANSPARENT))); // 中心为半透明粉色，边缘完全透明
            g.fillOval(size * rigConfig.unit("cheek.leftX",.324), size * rigConfig.unit("cheek.y",.492), size * .070, size * .030); // 左脸颊椭圆
            g.fillOval(size * rigConfig.unit("cheek.rightX",.513), size * rigConfig.unit("cheek.y",.492), size * .070, size * .030); // 右脸颊椭圆
        }
        if (!mood.equals("normal")) { // 非普通表情时绘制嘴部
            skin(g, image, rigConfig.unit("mouth.coverX",.438), rigConfig.unit("mouth.coverY",.527), rigConfig.unit("mouth.coverWidth",.027), rigConfig.unit("mouth.coverHeight",.018)); // 先用皮肤补片盖住原图的嘴
            double x = rigConfig.unit("mouth.x",.451) * size, y = rigConfig.unit("mouth.y",.535) * size; // 计算嘴部中心坐标
            g.setStroke(Color.web("#916e64")); g.setLineWidth(Math.max(.8, size * .004)); // 设置嘴部描边颜色与线宽
            if (mood.equals("happy")) { // 开心时画微笑弧
                g.beginPath(); g.moveTo(x - .007 * size, y); // 从左侧起点开始
                g.quadraticCurveTo(x, y + .010 * size, x + .007 * size, y); g.stroke(); // 画一条下弯的弧线形成微笑
            } else { g.strokeOval(x - .004 * size, y - .004 * size, .008 * size, .010 * size); } // 惊讶时画一个小圆形张嘴
        }
    }
    private void skin(GraphicsContext g, Image image, double x, double y, double w, double h) { // 生成并绘制皮肤色补片，盖住原图五官
        var pixels = image.getPixelReader(); // 取原图像素读取器
        int iw = (int) image.getWidth(), ih = (int) image.getHeight(); // 原图的宽高
        // 缓存原分辨率的皮肤插值补片，连续采样左右边界，避免 Canvas 分条接缝。
        String key = x + ":" + y + ":" + w + ":" + h; // 用区域参数拼成缓存键
        Image patch = skinCache.computeIfAbsent(key, ignored -> { // 缓存中没有时才生成，避免每帧重复计算
            int width = Math.max(1, (int) Math.ceil(w * iw)); // 补片宽度（原图像素），至少 1
            int height = Math.max(1, (int) Math.ceil(h * ih)); // 补片高度（原图像素），至少 1
            WritableImage result = new WritableImage(width, height); // 创建可写图像存放补片
            int leftX = Math.max(0, (int) (x * iw) - 1); // 取区域左侧外一列的采样点，保证不越界
            int rightX = Math.min(iw - 1, (int) ((x + w) * iw) + 1); // 取区域右侧外一列的采样点，保证不越界
            for (int row = 0; row < height; row++) { // 逐行生成
                int py = Math.max(0, Math.min(ih - 1, (int) ((y + h * row / height) * ih))); // 该行对应的原图纵坐标，夹取在有效范围内
                Color left = pixels.getColor(leftX, py), right = pixels.getColor(rightX, py); // 采样左右两侧边缘的真实皮肤色
                for (int col = 0; col < width; col++) // 逐列填充
                    result.getPixelWriter().setColor(col, row, left.interpolate(right, (col + 1.0) / (width + 1))); // 在左右颜色之间线性插值，得到自然的渐变皮肤
            }
            return result; // 返回生成好的补片
        });
        g.drawImage(patch, x * size, y * size, w * size, h * size); // 把补片按当前尺寸缩放后绘制到目标区域
    }
}
