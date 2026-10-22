package com.example.pet;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javafx.application.Platform;

/** 系统托盘保留 AWT 图标，菜单用 Swing 绘制，避开原生菜单中文字形方块问题。 */
final class PetTray {
    private TrayIcon icon;
    private JWindow menuOwner;
    private JPopupMenu menu;
    private long lastPopup;

    boolean install(Path imagePath, Runnable show, Runnable hide, Runnable exit) {
        if (!SystemTray.isSupported()) return false;
        try {
            EventQueue.invokeAndWait(() -> {
                try {
                    Image source=ImageIO.read(imagePath.toFile());
                    if(source==null)throw new IllegalArgumentException("Invalid tray image");
                    Dimension size=SystemTray.getSystemTray().getTrayIconSize();
                    BufferedImage thumbnail=new BufferedImage(size.width,size.height,BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g=thumbnail.createGraphics();
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    double scale=Math.min((double)size.width/source.getWidth(null),(double)size.height/source.getHeight(null));
                    int w=Math.max(1,(int)(source.getWidth(null)*scale)), h=Math.max(1,(int)(source.getHeight(null)*scale));
                    g.drawImage(source,(size.width-w)/2,(size.height-h)/2,w,h,null);g.dispose();
                    menu=createMenu(() -> Platform.runLater(show),() -> Platform.runLater(hide),() -> Platform.runLater(exit));
                    menuOwner=new JWindow();menuOwner.setSize(1,1);menuOwner.setAlwaysOnTop(true);
                    menuOwner.setFocusableWindowState(true);
                    menu.addPopupMenuListener(new PopupMenuListener(){
                        public void popupMenuWillBecomeVisible(PopupMenuEvent e) { }
                        public void popupMenuWillBecomeInvisible(PopupMenuEvent e){menuOwner.setVisible(false);}
                        public void popupMenuCanceled(PopupMenuEvent e){menuOwner.setVisible(false);}
                    });
                    menuOwner.addWindowFocusListener(new WindowAdapter(){
                        @Override public void windowLostFocus(WindowEvent e){
                            EventQueue.invokeLater(() -> {
                                Window focused=KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
                                Window popupWindow=SwingUtilities.getWindowAncestor(menu);
                                if(focused!=menuOwner && focused!=popupWindow) closeMenu();
                            });
                        }
                    });
                    TrayIcon candidate=new TrayIcon(thumbnail,"小桌宠 · 双击显示");
                    candidate.setImageAutoSize(true);
                    candidate.addActionListener(e -> Platform.runLater(show));
                    candidate.addMouseListener(new MouseAdapter(){
                        @Override public void mousePressed(MouseEvent e){if(e.isPopupTrigger())openMenu(e);}
                        @Override public void mouseReleased(MouseEvent e){if(e.isPopupTrigger() || e.getButton()==MouseEvent.BUTTON3)openMenu(e);}
                    });
                    SystemTray.getSystemTray().add(candidate);icon=candidate;
                } catch(Exception e){
                    if(menuOwner!=null){menuOwner.dispose();menuOwner=null;}
                    System.err.println("托盘初始化失败："+e.getMessage());
                }
            });
        } catch(Exception e){System.err.println("托盘不可用："+e.getMessage());}
        return icon!=null;
    }
    private void openMenu(MouseEvent event){
        long now=System.currentTimeMillis();if(now-lastPopup<250)return;lastPopup=now;
        Point point=event.getPoint();
        GraphicsConfiguration screen=null;
        for(GraphicsDevice device:GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices())
            for(GraphicsConfiguration candidate:device.getConfigurations())if(candidate.getBounds().contains(point))screen=candidate;
        if(screen==null)screen=GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
        Rectangle bounds=new Rectangle(screen.getBounds());Insets insets=Toolkit.getDefaultToolkit().getScreenInsets(screen);
        bounds.x+=insets.left;bounds.y+=insets.top;bounds.width-=insets.left+insets.right;bounds.height-=insets.top+insets.bottom;
        Dimension size=menu.getPreferredSize();
        int x=Math.max(bounds.x,Math.min(point.x,bounds.x+bounds.width-size.width));
        int y=Math.max(bounds.y,Math.min(point.y,bounds.y+bounds.height-size.height));
        menuOwner.setLocation(x,y);menuOwner.setVisible(true);menuOwner.requestFocus();
        menu.show(menuOwner.getContentPane(),0,0);
    }
    private void closeMenu(){if(menu!=null)menu.setVisible(false);if(menuOwner!=null)menuOwner.setVisible(false);}
    static JPopupMenu createMenu(Runnable show,Runnable hide,Runnable exit){
        JPopupMenu result=new JPopupMenu();result.setLightWeightPopupEnabled(false);
        Font font=chineseMenuFont();result.setFont(font);
        add(result,"显示桌宠",font,show);add(result,"隐藏桌宠",font,hide);result.addSeparator();add(result,"退出",font,exit);
        return result;
    }
    private static void add(JPopupMenu menu,String title,Font font,Runnable action){
        JMenuItem item=new JMenuItem(title);item.setFont(font);item.setMargin(new Insets(7,13,7,20));
        item.addActionListener(e -> {menu.setVisible(false);action.run();});menu.add(item);
    }
    static Font chineseMenuFont(){
        String text="显示桌宠隐藏退出";
        for(String name:new String[]{"Microsoft YaHei UI","Microsoft YaHei","SimSun","SimHei","Dialog"}){
            Font font=new Font(name,Font.PLAIN,13);if(font.canDisplayUpTo(text)==-1)return font;
        }
        for(Font font:GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts())
            if(font.canDisplayUpTo(text)==-1)return font.deriveFont(Font.PLAIN,13f);
        return new Font("Dialog",Font.PLAIN,13);
    }
    void remove(){
        TrayIcon previous=icon;icon=null;
        EventQueue.invokeLater(() -> {
            closeMenu();if(menuOwner!=null){menuOwner.dispose();menuOwner=null;}
            if(previous!=null)SystemTray.getSystemTray().remove(previous);
        });
    }
}