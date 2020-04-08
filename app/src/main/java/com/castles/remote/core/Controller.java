package com.castles.remote.core;

import com.castles.remote.core.wrappers.InputManager;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.io.IOException;

public class Controller {

    private final Device device;
    private final DesktopConnection connection;
    private final DeviceMessageSender sender;

    private final KeyCharacterMap charMap = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD);

    private long lastMouseDown;
    private final MotionEvent.PointerProperties[] pointerProperties = {new MotionEvent.PointerProperties()};
    private final MotionEvent.PointerCoords[] pointerCoords = {new MotionEvent.PointerCoords()};

    private boolean stop = false;

    public Controller(Device device, DesktopConnection connection) {
        this.device = device;
        this.connection = connection;
        initPointer();
        sender = new DeviceMessageSender(connection);
    }

    private void initPointer() {
        MotionEvent.PointerProperties props = pointerProperties[0];
        props.id = 0;
        props.toolType = MotionEvent.TOOL_TYPE_FINGER;

        MotionEvent.PointerCoords coords = pointerCoords[0];
        coords.orientation = 0;
        coords.pressure = 1;
        coords.size = 1;
    }

    private void setPointerCoords(Point point) {
        MotionEvent.PointerCoords coords = pointerCoords[0];
        coords.x = point.getX();
        coords.y = point.getY();
    }

    private void setScroll(int hScroll, int vScroll) {
        MotionEvent.PointerCoords coords = pointerCoords[0];
        coords.setAxisValue(MotionEvent.AXIS_HSCROLL, hScroll);
        coords.setAxisValue(MotionEvent.AXIS_VSCROLL, vScroll);
    }

    /**
     * 8.接收展示端控制信息并且进行响应
     * @throws IOException
     */
    @SuppressWarnings("checkstyle:MagicNumber")
    public void control() throws IOException {
        // on start, power on the device
        stop = false;
        // 连接上马上模拟按下power,以唤醒设备（因为可能会是熄屏状态）
        if (!device.isScreenOn()) {
            injectKeycode(KeyEvent.KEYCODE_POWER);

            // dirty hack
            // After POWER is injected, the device is powered on asynchronously.
            // To turn the device screen off while mirroring, the client will send a message that
            // would be handled before the device is actually powered on, so its effect would
            // be "canceled" once the device is turned back on.
            // Adding this delay prevents to handle the message before the device is actually
            // powered on.
            SystemClock.sleep(500);
        }

        while (!stop) {
            // 这里会进入死循环，不断的获取展示端发送来的事件
            handleEvent();
        }
        Ln.d("control thread exit!!!!!!!");
    }

    public void stop() {
        stop = true;
    }

    public DeviceMessageSender getSender() {
        return sender;
    }

    private void handleEvent() throws IOException {
        ControlMessage msg = connection.receiveControlMessage();
        switch (msg.getType()) {
            case ControlMessage.TYPE_INJECT_KEYCODE:
                injectKeycode(msg.getAction(), msg.getKeycode(), msg.getMetaState());
                break;
            case ControlMessage.TYPE_INJECT_TEXT:
                injectText(msg.getText());
                break;
            case ControlMessage.TYPE_INJECT_MOUSE_EVENT:
                injectMouse(msg.getAction(), msg.getButtons(), msg.getPosition());
                break;
            case ControlMessage.TYPE_INJECT_SCROLL_EVENT:
                injectScroll(msg.getPosition(), msg.getHScroll(), msg.getVScroll());
                break;
            case ControlMessage.TYPE_BACK_OR_SCREEN_ON:
                pressBackOrTurnScreenOn();
                break;
            case ControlMessage.TYPE_EXPAND_NOTIFICATION_PANEL:
                device.expandNotificationPanel();
                break;
            case ControlMessage.TYPE_COLLAPSE_NOTIFICATION_PANEL:
                device.collapsePanels();
                break;
            case ControlMessage.TYPE_GET_CLIPBOARD:
                String clipboardText = device.getClipboardText();
                sender.pushClipboardText(clipboardText);
                break;
            case ControlMessage.TYPE_SET_CLIPBOARD:
                device.setClipboardText(msg.getText());
                break;
            case ControlMessage.TYPE_SET_SCREEN_POWER_MODE:
                device.setScreenPowerMode(msg.getAction());
                break;
            default:
                // do nothing
        }
    }

    private boolean injectKeycode(int action, int keycode, int metaState) {
        return injectKeyEvent(action, keycode, 0, metaState);
    }

    private boolean injectChar(char c) {
        String decomposed = KeyComposition.decompose(c);
        char[] chars = decomposed != null ? decomposed.toCharArray() : new char[]{c};
        KeyEvent[] events = charMap.getEvents(chars);
        if (events == null) {
            return false;
        }
        for (KeyEvent event : events) {
            if (!injectEvent(event)) {
                return false;
            }
        }
        return true;
    }

    private int injectText(String text) {
        int successCount = 0;
        for (char c : text.toCharArray()) {
            if (!injectChar(c)) {
                Ln.w("Could not inject char u+" + String.format("%04x", (int) c));
                continue;
            }
            successCount++;
        }
        return successCount;
    }

    private boolean injectMouse(int action, int buttons, Position position) {
        long now = SystemClock.uptimeMillis();
        if (action == MotionEvent.ACTION_DOWN) {
            lastMouseDown = now;
        }
        Point point = device.getPhysicalPoint(position);
        if (point == null) {
            // ignore event
            return false;
        }
        setPointerCoords(point);
        MotionEvent event = MotionEvent.obtain(lastMouseDown, now, action, 1, pointerProperties, pointerCoords, 0, buttons, 1f, 1f, 0, 0,
                InputDevice.SOURCE_TOUCHSCREEN, 0);
        return injectEvent(event);
    }

    private boolean injectScroll(Position position, int hScroll, int vScroll) {
        long now = SystemClock.uptimeMillis();
        Point point = device.getPhysicalPoint(position);
        if (point == null) {
            // ignore event
            return false;
        }
        setPointerCoords(point);
        setScroll(hScroll, vScroll);
        MotionEvent event = MotionEvent.obtain(lastMouseDown, now, MotionEvent.ACTION_SCROLL, 1, pointerProperties, pointerCoords, 0, 0, 1f, 1f, 0,
                0, InputDevice.SOURCE_MOUSE, 0);
        return injectEvent(event);
    }

    private boolean injectKeyEvent(int action, int keyCode, int repeat, int metaState) {
        long now = SystemClock.uptimeMillis();
        KeyEvent event = new KeyEvent(now, now, action, keyCode, repeat, metaState, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0,
                InputDevice.SOURCE_KEYBOARD);
        return injectEvent(event);
    }

    private boolean injectKeycode(int keyCode) {
        return injectKeyEvent(KeyEvent.ACTION_DOWN, keyCode, 0, 0)
                && injectKeyEvent(KeyEvent.ACTION_UP, keyCode, 0, 0);
    }

    private boolean injectEvent(InputEvent event) {
        return device.injectInputEvent(event, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
    }

    private boolean pressBackOrTurnScreenOn() {
        int keycode = device.isScreenOn() ? KeyEvent.KEYCODE_BACK : KeyEvent.KEYCODE_POWER;
        return injectKeycode(keycode);
    }
}
