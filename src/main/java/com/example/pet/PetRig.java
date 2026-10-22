package com.example.pet; // 声明包名，与目录结构保持一致
import javafx.animation.*; // 批量导入动画类：Timeline、KeyFrame、KeyValue、Interpolator、Animation 等
import javafx.beans.property.*; // 批量导入属性类：DoubleProperty、SimpleDoubleProperty
import javafx.scene.Group; // 导入分组容器，用于把图片和面具/表情组合成一个部件
import javafx.scene.canvas.Canvas; // 导入画布，此处用于绘制颈部补色
import javafx.scene.image.*; // 批量导入图像类：Image、ImageView、WritableImage
import javafx.scene.layout.Pane; // 导入 Pane，作为可自由摆放子节点的布局基类
import javafx.scene.paint.Color; // 导入颜色类，用于填充颈部皮肤色
import javafx.scene.shape.*; // 批量导入形状类：Polygon、Rectangle、Shape
import javafx.scene.transform.Rotate; // 导入旋转变换，用于绕枢轴点转动部件
import javafx.util.Duration; // 导入时间长度类，用于定义关键帧时刻

/** 共享同一原图纹理，按轮廓裁成三个独立节点；不写入或破坏原始 PNG。 */
final class PetRig extends Pane { // 角色骨骼部件容器，包内可见、不可继承
    private static final double[] HEAD = {0,0,1,0,1,.535,.655,.535,.61,.558,.54,.568,.38,.568,.345,.60,.345,.667,.235,.667,0,.54}; // 头部轮廓默认顶点（归一化坐标对）
    private static final double[] HAIR = {.665,.518,.90,.518,.90,.80,.60,.80,.615,.67,.655,.60}; // 头发轮廓默认顶点（归一化坐标对）
    final DoubleProperty headAngle = new SimpleDoubleProperty(); // 头部转动角度，对外暴露供动画绑定
    final DoubleProperty bodyAngle = new SimpleDoubleProperty(); // 身体转动角度，对外暴露供动画绑定
    final DoubleProperty breath = new SimpleDoubleProperty(); // 呼吸强度 0~1，对外暴露供动画绑定
    private final DoubleProperty hairLag = new SimpleDoubleProperty(); // 头发滞后角度，模拟惯性甩动
    private final ImageView headImage = new ImageView(), bodyImage = new ImageView(), hairImage = new ImageView(); // 三个部件共享同一张原图的图像视图
    private final Group head, body, hair; // 头部、身体、头发三个分组节点
    private final Rotate headTurn = new Rotate(), bodyTurn = new Rotate(), hairTurn = new Rotate(); // 三个部件各自的旋转变换
    private final Canvas neck = new Canvas(); // 用于填补头身之间缝隙的颈部画布
    private final RigConfig config; // 骨骼绑定配置（轮廓、枢轴点、开关）
    private Timeline follow; // 头发跟随/惯性动画，可能被反复重建
    private double size = 192; // 当前尺寸，默认 192 像素
    private boolean resetting; // 重置标志，用于抑制重置过程中触发的跟随动画
    PetRig(Canvas face, RigConfig config) { // 构造方法：传入表情画布和绑定配置
        this.config = config; // 保存配置引用
        head = new Group(headImage, face); body = new Group(bodyImage); hair = new Group(hairImage); // 头部组包含原图与表情画布，身体和头发组各含一张图
        head.getTransforms().add(headTurn); body.getTransforms().add(bodyTurn); hair.getTransforms().add(hairTurn); // 为三个分组分别挂载旋转变换
        head.setMouseTransparent(true); body.setMouseTransparent(true); hair.setMouseTransparent(true); neck.setMouseTransparent(true); // 各部件不接收鼠标事件，交给外层统一处理
        setMouseTransparent(true); // 整个骨骼层完全不拦截鼠标
        getChildren().addAll(hair, body, neck, head); // 按"头发最下 → 身体 → 颈部 → 头部最上"的顺序叠放
        for (ImageView image : new ImageView[]{headImage,bodyImage,hairImage}) { image.setPreserveRatio(true); image.setSmooth(true); } // 三个视图都保持宽高比并开启平滑缩放
        headAngle.addListener((o,a,b) -> { // 监听头部角度变化
            updateTransforms(); // 立即刷新所有部件的变换与位移
            if (!resetting && config.enabled()) { // 非重置状态且启用绑定时才播放头发跟随
                if (follow != null) follow.stop(); // 先停掉上一轮未结束的跟随动画
                follow = new Timeline(20,new KeyFrame(Duration.millis(260),new KeyValue(hairLag,-b.doubleValue()*.42,Interpolator.EASE_OUT))); // 260ms 内让头发反向甩到当前头角的 42%，缓出收尾
                follow.play(); // 播放跟随动画
            }
        });
        bodyAngle.addListener((o,a,b) -> updateTransforms()); // 身体角度变化时刷新变换
        breath.addListener((o,a,b) -> updateTransforms()); // 呼吸值变化时刷新变换（产生上下起伏）
        hairLag.addListener((o,a,b) -> updateTransforms()); // 头发滞后值变化时刷新变换
    }
    void setImage(Image image) { headImage.setImage(image); bodyImage.setImage(image); hairImage.setImage(image); resizeRig(size); } // 三个部件共用同一张图片，并立即按当前尺寸重建
    void resizeRig(double value) { // 按新尺寸重建裁剪轮廓、枢轴点与颈部补丁
        size = value; setMinSize(size,size); setPrefSize(size,size); setMaxSize(size,size); // 记录尺寸并把本容器固定为正方形
        for (ImageView image : new ImageView[]{headImage,bodyImage,hairImage}) { image.setFitWidth(size); image.setFitHeight(size); } // 同步三张视图的显示尺寸
        if (config.enabled()) { // 启用骨骼绑定时才做轮廓裁剪
            Polygon headMask = polygon(config.polygon("head.polygon",HEAD)); // 取头部轮廓（缺省用内置常量）并缩放到当前尺寸
            Polygon hairMask = polygon(config.polygon("hair.polygon",HAIR)); // 取头发轮廓并缩放
            head.setClip(headMask); hair.setClip(hairMask); // 用轮廓作为裁剪面具，只显示对应区域
            Shape bodyMask = Shape.subtract(new Rectangle(size,size),Shape.union(polygon(config.polygon("head.polygon",HEAD)),polygon(config.polygon("hair.polygon",HAIR)))); // 身体 = 整块方形减去头部与头发区域
            // 颈部小幅重叠，让转头时不露出背景缝隙。
            bodyMask = Shape.union(bodyMask,new Rectangle(size*config.unit("neck.x",.405),size*config.unit("neck.y",.555),size*.10,size*.035)); // 再并入颈部小块，避免头身之间露缝
            // 分割线底衬：使用同一原图在活动层后方补齐小角度转动的空隙。
            Polygon headGuard = polygon(config.polygon("head.polygon",HEAD));
            headGuard.setFill(null); headGuard.setStroke(Color.BLACK);
            headGuard.setStrokeWidth(size*config.unit("seam.overlap",.045));
            Polygon hairGuard = polygon(config.polygon("hair.polygon",HAIR));
            hairGuard.setFill(null); hairGuard.setStroke(Color.BLACK);
            hairGuard.setStrokeWidth(size*config.unit("seam.overlap",.045));
            Shape seamGuard = Shape.union(headGuard,hairGuard);
            bodyMask = Shape.union(bodyMask,seamGuard);
            body.setClip(bodyMask); hair.setVisible(true); body.setVisible(true); // 应用身体裁剪并显示头发与身体部件
        } else { head.setClip(null); hair.setVisible(false); body.setVisible(false); } // 未启用绑定时不做裁剪，仅保留完整图像，隐藏多余部件
        neck.setWidth(size); neck.setHeight(size); neck.getGraphicsContext2D().clearRect(0,0,size,size); // 重置颈部画布尺寸并清空内容
        if (config.enabled() && headImage.getImage()!=null) { // 启用绑定且已有图片时才绘制颈部
            neck.getGraphicsContext2D().setFill(Color.web("#f8e6dc")); // 设置颈部皮肤色
            neck.getGraphicsContext2D().fillOval(size*config.unit("neck.x",.405),size*config.unit("neck.y",.555),size*.10,size*.035); // 在颈部位置画一个椭圆补丁
        }
        headTurn.setPivotX(size*config.unit("head.pivotX",.45)); headTurn.setPivotY(size*config.unit("head.pivotY",.565)); // 设置头部旋转枢轴点（约在脖颈处）
        bodyTurn.setPivotX(size*config.unit("body.pivotX",.46)); bodyTurn.setPivotY(size*config.unit("body.pivotY",.93)); // 设置身体旋转枢轴点（约在脚底）
        hairTurn.setPivotX(size*config.unit("hair.pivotX",.68)); hairTurn.setPivotY(size*config.unit("hair.pivotY",.535)); // 设置头发旋转枢轴点（约在发根）
        updateTransforms(); // 用新尺寸重新计算一次变换
    }
    private Polygon polygon(double[] points) { // 把归一化顶点数组按当前尺寸缩放成实际多边形
        double[] scaled=points.clone(); for(int i=0;i<scaled.length;i++)scaled[i]*=size; return new Polygon(scaled); // 复制数组避免改动原数据，逐项乘尺寸后构造 Polygon
    }
    private void updateTransforms() { // 根据当前各属性值刷新旋转、位移与呼吸起伏
        if (!config.enabled()) return; // 未启用绑定时无需计算
        double maxHead = Math.max(0, Math.min(5,config.n("head.maxDegrees",3.8)));
        double angle=Math.max(-maxHead,Math.min(maxHead,headAngle.get())); // 头部角度夹取到 -5~5 度，防止转得过头
        headTurn.setAngle(angle); bodyTurn.setAngle(Math.max(-2,Math.min(2,bodyAngle.get()))); // 设置头部角度与夹取后的身体角度（-2~2 度）
        double rise=-size*.004*breath.get(); // 呼吸产生的整体上升位移（负值表示向上）
        head.setTranslateY(rise); body.setTranslateY(rise*.35); neck.setTranslateY(rise*.6); // 头、身、颈按不同比例起伏，营造层次感
        double dx=hairTurn.getPivotX()-headTurn.getPivotX(), dy=hairTurn.getPivotY()-headTurn.getPivotY(); // 计算头发枢轴相对头部枢轴的偏移
        double rad=Math.toRadians(angle); // 角度转弧度以便做旋转矩阵计算
        hair.setTranslateX(dx*Math.cos(rad)-dy*Math.sin(rad)-dx); // 按头部旋转把头发枢轴补偿回原位（X 分量）
        hair.setTranslateY(dx*Math.sin(rad)+dy*Math.cos(rad)-dy+rise); // 同上计算 Y 分量，并叠加呼吸起伏
        hairTurn.setAngle(angle+hairLag.get()); // 头发角度 = 头部角度 + 滞后量，形成甩动延迟
    }
    void impulse(double direction) { // 施加一次惯性冲击（如撞墙时头发甩动）
        if (!config.enabled()) return; // 未启用绑定时忽略
        if (follow!=null) follow.stop(); // 停掉正在进行的跟随动画
        follow=new Timeline(20, // 以 20fps 播放三段式甩动
                new KeyFrame(Duration.ZERO,new KeyValue(hairLag,Math.max(-3,Math.min(3,direction)))), // 第 0 帧：头发瞬间偏到冲击方向（夹取 ±3 度）
                new KeyFrame(Duration.millis(240),new KeyValue(hairLag,-direction*.25,Interpolator.EASE_OUT)), // 240ms：回弹到反方向 25%，缓出
                new KeyFrame(Duration.millis(650),new KeyValue(hairLag,0,Interpolator.EASE_BOTH))); // 650ms：回到 0，双向缓动收尾
        follow.play(); // 播放冲击动画
    }
    void reset() { // 立即复位所有部件到初始姿态
        resetting=true; if(follow!=null){follow.stop();follow=null;} // 置重置标志并终止跟随动画，避免复位过程触发新的动画
        headAngle.set(0);bodyAngle.set(0);breath.set(0);hairLag.set(0);updateTransforms();resetting=false; // 角度、呼吸、滞后全部归零后刷新变换，再解除标志
    }
}
