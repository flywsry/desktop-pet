package com.example.pet; // 声明包名，与目录结构保持一致
import javafx.animation.PauseTransition; // 导入延时过渡，用于让气泡停留一段时间后自动淡出
import javafx.animation.FadeTransition; // 导入淡入淡出过渡，用于气泡的出现与消失
import javafx.scene.effect.DropShadow; // 导入投影效果，让气泡有层次感
import javafx.scene.paint.Color; // 导入颜色类，用于设置阴影颜色
import javafx.geometry.Rectangle2D; // 导入矩形类，用于读取屏幕可视区域边界
import javafx.scene.control.Label; // 导入标签控件，作为气泡中的文本载体
import javafx.stage.Popup; // 导入 Popup，用于显示无边框的气泡窗口
import javafx.stage.Screen; // 导入屏幕信息类，用于多屏与可视区域计算
import javafx.stage.Stage; // 导入窗口类，气泡需要依附于主窗口定位
import javafx.util.Duration; // 导入时间长度类，用于设置动画时长
import java.util.concurrent.ThreadLocalRandom; // 导入随机数工具，用于随机挑选台词

/** 点击以微笑和短句回应，不再让整张图片反复跳跃。 */
final class PetInteraction { // 点击交互与气泡提示封装类，包内可见、不可继承
    private final Stage stage; // 主窗口引用，用于气泡定位
    private final PetCharacter character; // 角色引用，点击时触发表情反馈
    private final Popup bubble = new Popup(); // 气泡弹窗本体
    private final Label text = new Label(); // 气泡中的文本标签
    private final PauseTransition dismiss = new PauseTransition(Duration.seconds(2.5)); // 停留计时器：2.5 秒后开始淡出
    private final String[] messages = {"嘿嘿，摸摸头～", "我在呢！", "今天也要开心呀。", "陪你休息一小会儿。"}; // 点击头部时随机使用的台词库
    private int last = -1; // 上次使用的台词下标，避免连续两次说同一句
    private final FadeTransition appear = new FadeTransition(Duration.millis(160), text); // 160ms 淡入动画
    private final FadeTransition disappear = new FadeTransition(Duration.millis(180), text); // 180ms 淡出动画
    PetInteraction(Stage stage, PetCharacter character) { // 构造方法：绑定主窗口与角色
        this.stage = stage; this.character = character; // 保存窗口与角色引用
        text.setWrapText(true); text.setMaxWidth(190); // 允许自动换行并限制最大宽度，防止气泡过宽
        text.setStyle("-fx-background-color: #fff3f8; -fx-text-fill: #553b4c; -fx-background-radius: 14;" // 设置粉白背景、深紫文字与圆角
                + "-fx-border-radius: 14; -fx-border-color: #dea8c0; -fx-padding: 10 14; -fx-font-size: 13px; -fx-font-family: 'Microsoft YaHei';"); // 设置边框圆角、描边色、内边距和字体
        text.setMouseTransparent(true); bubble.getContent().add(text); // 文本不拦截鼠标，并作为气泡的唯一内容
        bubble.setAutoFix(false); // 关闭自动修正位置，完全由代码控制气泡坐标
        text.setEffect(new DropShadow(5, 0, 2, Color.rgb(40, 20, 40, .16))); // 给气泡加淡紫色投影
        appear.setFromValue(0); appear.setToValue(1); // 淡入：透明度 0 → 1
        disappear.setFromValue(1); disappear.setToValue(0); // 淡出：透明度 1 → 0
        disappear.setOnFinished(e -> bubble.hide()); // 淡出结束后真正隐藏弹窗
        dismiss.setOnFinished(e -> disappear.playFromStart()); // 停留结束后启动淡出
    }
    void react(boolean head) { // 响应点击：head 为 true 表示点到头部
        cancel(); int index; // 先取消上一次的气泡与动画，再准备选台词
        do { index = ThreadLocalRandom.current().nextInt(messages.length); } while (index == last); // 随机抽一句，抽到与上次相同就重抽
        last = index; text.setText(head ? messages[index] : "咦，你在叫我吗？"); character.touched(head); // 记录下标、设置文本（点身体用固定疑问语），并让角色做出对应表情
        bubble.show(stage, stage.getX(), stage.getY()); text.applyCss(); text.autosize(); // 先显示气泡再应用样式并自适应尺寸，才能拿到真实宽高
        Rectangle2D b = Screen.getScreensForRectangle(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight()) // 查找窗口所在屏幕
                .stream().findFirst().orElse(Screen.getPrimary()).getVisualBounds(); // 取第一个匹配屏幕，找不到则回退主屏幕，再取其可视区域
        double x = stage.getX() + (stage.getWidth() - bubble.getWidth()) / 2; // 水平居中于窗口上方
        double y = stage.getY() - bubble.getHeight() + 20; // 垂直放在窗口上方，并下移 20 像素与角色略微重叠
        if (y < b.getMinY()) y = stage.getY() + stage.getHeight() - 20; // 上方空间不够时改放到窗口下方
        bubble.setX(Math.max(b.getMinX(), Math.min(x, b.getMaxX() - bubble.getWidth()))); // 把 X 夹取在屏幕可视范围内
        bubble.setY(Math.max(b.getMinY(), Math.min(y, b.getMaxY() - bubble.getHeight()))); // 把 Y 夹取在屏幕可视范围内
        appear.playFromStart(); dismiss.playFromStart(); // 播放淡入并重新开始停留计时
    }
    void cancel() { dismiss.stop(); appear.stop(); disappear.stop(); text.setOpacity(1); bubble.hide(); } // 立即取消所有气泡动画、恢复不透明度并隐藏气泡
}
