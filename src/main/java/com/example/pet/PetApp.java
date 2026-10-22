package com.example.pet; // 声明包名，与目录结构保持一致

import javafx.application.Application; // 导入 JavaFX 应用基类，继承它才能启动图形界面
import javafx.application.Platform; // 导入 Platform，用于退出应用与设置隐式退出行为
import javafx.geometry.*; // 批量导入几何类：Rectangle2D、Pos 等
import javafx.scene.Scene; // 导入场景类，窗口内容都装载在 Scene 中
import javafx.scene.Cursor; // 导入光标枚举，用于切换抓手/手型光标
import javafx.scene.control.*; // 批量导入控件类：ContextMenu、Menu、MenuItem、CheckMenuItem、SeparatorMenuItem
import javafx.scene.effect.DropShadow; // 导入投影效果（本类中保留导入备用）
import javafx.scene.image.Image; // 导入图像类，用于加载角色原图
import javafx.scene.input.MouseButton; // 导入鼠标按键枚举，用于区分左键与其他键
import javafx.scene.layout.StackPane; // 导入层叠布局，子节点居中堆叠
import javafx.scene.paint.Color; // 导入颜色类，用于设置场景透明背景
import javafx.stage.*; // 批量导入窗口类：Stage、Screen、StageStyle 等
import java.nio.file.Path; // 导入路径类，用于解析角色图片与托盘图标路径

/** 当前阶段：表情、待机、固定和惯性甩动。 */
public class PetApp extends Application { // 桌宠主应用类，继承 Application 作为 JavaFX 入口
    private final PetSettings settings = new PetSettings(); // 设置读写对象，创建时即载入已保存配置
    private final PetTray tray = new PetTray(); // 系统托盘封装对象
    private final PetCharacter character = new PetCharacter(); // 角色显示与表情动画对象
    private Stage stage; // 主窗口引用，在 start 中赋值
    private PetMotion motion; // 拖动甩动的物理驱动对象
    private PetInteraction interaction; // 点击气泡交互对象
    private boolean trayAvailable, fixed, dragging, pressed; // 托盘是否可用 / 是否固定位置 / 是否正在拖动 / 鼠标是否按下
    private double pressX, pressY, windowX, windowY; // 按下时的鼠标屏幕坐标与当时窗口左上角坐标

    @Override public void start(Stage window) { // JavaFX 启动入口，window 为系统创建的主窗口
        stage = window; // 保存主窗口引用
        fixed = settings.fixed(); // 从设置中读取"固定位置"开关
        Path imagePath = AppPaths.pets().resolve("default/pet.png"); // 角色图片路径：pets/default/pet.png 的绝对路径
        Image image = new Image(imagePath.toUri().toString()); // 把路径转成 URI 字符串后加载图片
        if (image.isError()) throw new IllegalStateException("无法读取角色图片：" + imagePath, image.getException()); // 加载失败直接抛异常，附上路径与原因
        character.setImage(image); // 把图片交给角色对象显示
        int size = settings.size(); // 读取上次的角色尺寸（只可能是 128/192/256）
        character.setFitWidth(size); character.setFitHeight(size); // 设置角色显示尺寸
        // 动态轮廓阴影由 PetCharacter 统一管理。
        StackPane root = new StackPane(character); // 创建层叠布局作为根节点，角色居中放置
        root.setPickOnBounds(false); // 关闭空白区域响应鼠标，让透明区域不拦截事件
        root.setStyle("-fx-background-color: transparent;"); // 背景透明，只显示角色本身
        stage.initStyle(StageStyle.TRANSPARENT); // 窗口设为透明无边框样式
        stage.setTitle("小桌宠 · 表情与甩动预览"); // 设置窗口标题
        stage.setAlwaysOnTop(true); stage.setResizable(false); // 始终置顶且不允许改变大小
        stage.setScene(new Scene(root, size + 64, size + 64, Color.TRANSPARENT)); // 场景比角色大 64 像素，留出阴影和旋转余量，背景透明
        interaction = new PetInteraction(stage, character); // 创建点击交互对象
        motion = new PetMotion(stage, character, this::saveSettings); // 创建甩动物理对象，静止后回调保存设置

        ContextMenu menu = new ContextMenu(); // 创建右键菜单
        CheckMenuItem pin = new CheckMenuItem("固定位置（禁止拖动和甩动）"); // 创建可勾选的"固定位置"菜单项
        pin.setSelected(fixed); // 按当前设置初始化勾选状态
        pin.setOnAction(e -> { // 勾选状态变化时的处理
            motion.stop(); pressed = false; dragging = false; // 停止甩动并清除拖动相关状态
            fixed = pin.isSelected(); settings.fixed(fixed); character.setCursor(fixed ? Cursor.HAND : Cursor.OPEN_HAND); // 更新固定标志、写入设置并切换光标样式
            character.resumeIdle(); saveSettings(); // 恢复待机动画并保存设置
        });
        Menu sizes = new Menu("角色大小"); // 创建"角色大小"子菜单
        for (int candidate : new int[]{128, 192, 256}) { // 遍历三档可选尺寸
            MenuItem item = new MenuItem(candidate + " px"); // 为每档尺寸创建菜单项，文字形如"128 px"
            item.setOnAction(e -> { // 点击该尺寸时的处理
                stopActions(); character.suspend(); // 停止正在进行的动画与交互，角色进入暂停态
                character.setFitWidth(candidate); character.setFitHeight(candidate); // 调整角色显示尺寸
                stage.setWidth(candidate + 64); stage.setHeight(candidate + 64); // 同步调整窗口尺寸，保持 64 像素余量
                keepVisible(); character.resumeIdle(); saveSettings(); // 校正位置、恢复待机并保存设置
            });
            sizes.getItems().add(item); // 把尺寸菜单项加入子菜单
        }
        MenuItem hide = new MenuItem("隐藏到托盘"); hide.setDisable(true); // 创建"隐藏到托盘"菜单项，默认禁用，等托盘安装成功后再启用
        hide.setOnAction(e -> { menu.hide(); hidePet(); }); // 点击时先收起菜单再隐藏窗口
        MenuItem exit = new MenuItem("退出"); exit.setOnAction(e -> exitPet()); // 创建"退出"菜单项，点击时执行退出流程
        menu.getItems().addAll(pin, sizes, hide, new SeparatorMenuItem(), exit); // 依次加入固定、尺寸、隐藏、分隔线、退出
        root.setOnContextMenuRequested(e -> { // 监听右键菜单请求
            stopActions(); character.resumeIdle(); saveSettings(); // 弹出前先停止动作、恢复待机并保存位置
            menu.show(root, e.getScreenX(), e.getScreenY()); e.consume(); // 在鼠标位置显示菜单并消费事件
        });
        character.setCursor(fixed ? Cursor.HAND : Cursor.OPEN_HAND); // 根据是否固定设置初始光标：固定为手型，否则为张开的手
        character.setOnMouseEntered(e -> { if (!pressed && !motion.isMoving()) character.pointerEnter(); }); // 鼠标移入且未在拖动/甩动时进入注视状态
        character.setOnMouseMoved(e -> { // 监听鼠标在角色上移动
            if (!pressed && !motion.isMoving()) character.pointerMove(character.sceneToLocal(e.getSceneX(), e.getSceneY()).getX()); // 把场景坐标转成角色本地 X 坐标，驱动眼神跟随
        });
        character.setOnMouseExited(e -> character.pointerLeave()); // 鼠标移出时恢复默认视线
        root.setOnMousePressed(e -> { // 监听鼠标按下
            if (e.getButton() != MouseButton.PRIMARY) return; // 只响应鼠标左键
            menu.hide(); stopActions(); character.suspend(); character.expression("normal"); // 收起菜单、停止动作、暂停待机并把表情复位
            dragging = false; pressed = true; character.setCursor(fixed ? Cursor.HAND : Cursor.CLOSED_HAND); // 初始化拖动状态并切换为握拳光标
            pressX = e.getScreenX(); pressY = e.getScreenY(); // 记录按下时的鼠标屏幕坐标
            windowX = stage.getX(); windowY = stage.getY(); // 记录按下时的窗口左上角坐标
            motion.begin(); // 通知物理模块开始采样，为可能的甩动做准备
        });
        root.setOnMouseDragged(e -> { // 监听鼠标拖动
            if (!pressed || fixed || !e.isPrimaryButtonDown()) return; // 未按下、已固定或非左键拖动时忽略
            double dx = e.getScreenX() - pressX, dy = e.getScreenY() - pressY; // 计算相对按下点的位移
            if (!dragging && Math.hypot(dx, dy) < 6) return; // 位移小于 6 像素视为抖动，不进入拖动
            dragging = true; // 正式进入拖动状态
            character.expression("surprised"); character.held(dx); // 切换惊讶表情并按水平位移倾斜身体
            stage.setX(windowX + dx); stage.setY(windowY + dy); // 按下时的窗口位置加上位移，得到新的窗口位置
            keepVisible(); motion.sample(); // 校正到屏幕内并追加一次速度采样
        });
        root.setOnMouseReleased(e -> { // 监听鼠标松开
            if (!pressed || e.getButton() != MouseButton.PRIMARY) return; // 只处理左键松开，且必须之前是按下状态
            pressed = false; character.setCursor(fixed ? Cursor.HAND : Cursor.OPEN_HAND); // 清除按下状态并恢复光标
            if (dragging && !fixed) { // 之前确实在拖动且未固定
                if (!motion.release()) { snapToEdge(); saveSettings(); } // 松手速度不足则贴边并保存；速度足够时由物理模块接管
            } else {
                character.resumeIdle(); // 只是单击，恢复待机动画
                if (Math.hypot(e.getScreenX() - pressX, e.getScreenY() - pressY) < 6) interaction.react(character.sceneToLocal(e.getSceneX(), e.getSceneY()).getY() < character.getFitHeight() * .57); // 位移小于 6 像素判定为点击：按点击位置的 Y 决定是摸头还是叫它（高于 57% 高度算头部）
            }
            dragging = false; // 清除拖动标志
        });
        stage.show(); // 显示窗口（必须先 show 才能拿到准确宽高）
        Rectangle2D b = Screen.getPrimary().getVisualBounds(); // 获取主屏幕可视区域（不含任务栏）
        stage.setX(settings.number("x", b.getMaxX() - stage.getWidth() - 24)); // 恢复上次的 X，首次运行则放到右下角留 24 像素边距
        stage.setY(settings.number("y", b.getMaxY() - stage.getHeight() - 24)); // 恢复上次的 Y，首次运行则放到右下角留 24 像素边距
        keepVisible(); // 校正位置，确保窗口完整位于屏幕内
        trayAvailable = tray.install(imagePath, this::showPet, this::hidePet, this::exitPet); // 安装系统托盘，并注册显示/隐藏/退出回调
        hide.setDisable(!trayAvailable); Platform.setImplicitExit(!trayAvailable); // 托盘不可用时禁用隐藏项；托盘可用时关闭窗口不退出程序
        stage.setOnCloseRequest(e -> { e.consume(); if (trayAvailable) hidePet(); else exitPet(); }); // 拦截关闭请求：有托盘则隐藏，否则真正退出
        stage.iconifiedProperty().addListener((o, oldValue, minimized) -> { // 监听窗口最小化状态变化
            if (minimized) { stopActions(); character.suspend(); saveSettings(); } // 最小化时停止动画并保存位置
            else if (stage.isShowing()) character.resumeIdle(); // 从最小化恢复且窗口可见时重启待机动画
        });
        character.resumeIdle(); // 启动待机动画
        if (getParameters().getRaw().contains("--verify-package")) {
            javafx.animation.PauseTransition check = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(1.5));
            check.setOnFinished(event -> {
                try {
                    java.nio.file.Files.writeString(AppPaths.data().resolve("package-check.txt"),
                            "PASS\njava.home=" + System.getProperty("java.home") + "\nassets=" + AppPaths.home()
                            + "\nimage=" + character.getImage().getWidth() + "\ntray=" + trayAvailable,
                            java.nio.charset.StandardCharsets.UTF_8);
                } catch (java.io.IOException error) { error.printStackTrace(); }
                exitPet();
            });
            check.play();
        }
    }
    private Rectangle2D bounds() { // 获取窗口所在屏幕的可视区域
        return Screen.getScreensForRectangle(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight()) // 按窗口矩形查找所在屏幕
                .stream().findFirst().orElse(Screen.getPrimary()).getVisualBounds(); // 取第一个匹配屏幕，找不到则回退主屏幕
    }
    private void keepVisible() { // 把窗口夹取到屏幕可视范围内
        Rectangle2D b = bounds(); // 取当前屏幕可视区域
        stage.setX(Math.max(b.getMinX(), Math.min(stage.getX(), b.getMaxX() - stage.getWidth()))); // X 限制在 [左边界, 右边界-窗口宽]
        stage.setY(Math.max(b.getMinY(), Math.min(stage.getY(), b.getMaxY() - stage.getHeight()))); // Y 限制在 [上边界, 下边界-窗口高]
    }
    private void snapToEdge() { // 松手后若离某条边很近，则吸附到该边
        Rectangle2D b = bounds(); // 取当前屏幕可视区域
        double left = stage.getX() - b.getMinX(), right = b.getMaxX() - stage.getX() - stage.getWidth(); // 计算到左、右边界的距离
        double top = stage.getY() - b.getMinY(), bottom = b.getMaxY() - stage.getY() - stage.getHeight(); // 计算到上、下边界的距离
        if (Math.min(left, right) <= 24) stage.setX(left <= right ? b.getMinX() : b.getMaxX() - stage.getWidth()); // 横向最近边在 24 像素内则吸附到较近的一侧
        if (Math.min(top, bottom) <= 24) stage.setY(top <= bottom ? b.getMinY() : b.getMaxY() - stage.getHeight()); // 纵向最近边在 24 像素内则吸附到较近的一侧
    }
    private void stopActions() { // 停止所有进行中的动作
        pressed = false; // 清除按下状态
        if (motion != null) motion.stop(); // 停止甩动物理
        if (interaction != null) interaction.cancel(); // 取消并隐藏气泡
    }
    private void saveSettings() { // 把当前状态写入设置文件
        if (stage != null) { settings.fixed(fixed); settings.save(stage.getX(), stage.getY(), (int) character.getFitWidth()); } // 窗口存在时保存固定开关、窗口坐标与角色尺寸
    }
    private void showPet() { // 显示桌宠（托盘菜单或双击托盘图标时调用）
        stage.setIconified(false); stage.show(); keepVisible(); stage.toFront(); character.resumeIdle(); // 取消最小化、显示窗口、校正位置、置顶并恢复待机
    }
    private void hidePet() { // 隐藏桌宠到托盘
        if (!trayAvailable) return; // 没有托盘时不执行隐藏，避免窗口消失后无法找回
        stopActions(); character.suspend(); saveSettings(); stage.hide(); // 停止动作、暂停动画、保存位置后隐藏窗口
    }
    private void exitPet() { // 真正退出程序
        stopActions(); character.suspend(); saveSettings(); tray.remove(); Platform.exit(); // 停止动作、保存设置、移除托盘图标并退出 JavaFX
    }
    @Override public void stop() { stopActions(); character.suspend(); saveSettings(); tray.remove(); } // 应用生命周期结束时的清理回调
    public static void main(String[] args) { launch(args); } // main 入口，调用 launch 启动 JavaFX 并透传命令行参数
}
