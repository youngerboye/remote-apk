package com.castles.remote.core;

/**
 * Union of all supported event types, identified by their {@code type}.
 */
public final class ControlMessage {

    public static final int TYPE_INJECT_KEYCODE = 0;
    public static final int TYPE_INJECT_TEXT = 1;
    public static final int TYPE_INJECT_MOUSE_EVENT = 2;
    public static final int TYPE_INJECT_SCROLL_EVENT = 3;
    public static final int TYPE_BACK_OR_SCREEN_ON = 4;
    public static final int TYPE_EXPAND_NOTIFICATION_PANEL = 5;
    public static final int TYPE_COLLAPSE_NOTIFICATION_PANEL = 6;
    public static final int TYPE_GET_CLIPBOARD = 7;
    public static final int TYPE_SET_CLIPBOARD = 8;
    public static final int TYPE_SET_SCREEN_POWER_MODE = 9;

    private int type;
    private String text;
    private int metaState; // KeyEvent.META_*
    private int action; // KeyEvent.ACTION_* or MotionEvent.ACTION_* or POWER_MODE_*
    private int keycode; // KeyEvent.KEYCODE_*
    private int buttons; // MotionEvent.BUTTON_*
    private Position position;
    private int hScroll;
    private int vScroll;

    private ControlMessage() {
    }

    public static ControlMessage createInjectKeycode(int action, int keycode, int metaState) {
        ControlMessage event = new ControlMessage();
        event.type = TYPE_INJECT_KEYCODE;
        event.action = action;
        event.keycode = keycode;
        event.metaState = metaState;
        return event;
    }

    public static ControlMessage createInjectText(String text) {
        ControlMessage event = new ControlMessage();
        event.type = TYPE_INJECT_TEXT;
        event.text = text;
        return event;
    }

    public static ControlMessage createInjectMouseEvent(int action, int buttons, Position position) {
        ControlMessage event = new ControlMessage();
        event.type = TYPE_INJECT_MOUSE_EVENT;
        event.action = action;
        event.buttons = buttons;
        event.position = position;
        return event;
    }

    public static ControlMessage createInjectScrollEvent(Position position, int hScroll, int vScroll) {
        ControlMessage event = new ControlMessage();
        event.type = TYPE_INJECT_SCROLL_EVENT;
        event.position = position;
        event.hScroll = hScroll;
        event.vScroll = vScroll;
        return event;
    }

    public static ControlMessage createSetClipboard(String text) {
        ControlMessage event = new ControlMessage();
        event.type = TYPE_SET_CLIPBOARD;
        event.text = text;
        return event;
    }

    /**
     * @param mode one of the {@code Device.SCREEN_POWER_MODE_*} constants
     */
    public static ControlMessage createSetScreenPowerMode(int mode) {
        ControlMessage event = new ControlMessage();
        event.type = TYPE_SET_SCREEN_POWER_MODE;
        event.action = mode;
        return event;
    }

    public static ControlMessage createEmpty(int type) {
        ControlMessage event = new ControlMessage();
        event.type = type;
        return event;
    }

    public int getType() {
        return type;
    }

    public String getText() {
        return text;
    }

    public int getMetaState() {
        return metaState;
    }

    public int getAction() {
        return action;
    }

    public int getKeycode() {
        return keycode;
    }

    public int getButtons() {
        return buttons;
    }

    public Position getPosition() {
        return position;
    }

    public int getHScroll() {
        return hScroll;
    }

    public int getVScroll() {
        return vScroll;
    }
}
