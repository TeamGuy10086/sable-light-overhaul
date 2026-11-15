package dev.sablelightoverhaul.client.ui;

/** Manual drags must never preempt a popup or a clipped region of the scroller. */
public final class ConfigPointerCapture {
    private ConfigPointerCapture() {}
    public static boolean manual(int button,boolean popupOpen) { return button==0&&!popupOpen; }
    public static boolean hit(float x,float y,float left,float top,float width,float height,
            float viewLeft,float viewTop,float viewWidth,float viewHeight) {
        return width>0&&height>0&&viewWidth>0&&viewHeight>0
            &&x>=left&&x<=left+width&&y>=top&&y<=top+height
            &&x>=viewLeft&&x<=viewLeft+viewWidth&&y>=viewTop&&y<=viewTop+viewHeight;
    }
}
