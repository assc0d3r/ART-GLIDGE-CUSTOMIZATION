package dev.custom.gboardpatches.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.inputmethodservice.InputMethodService;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.widget.PopupMenu;
import java.util.ArrayList;
import java.util.List;
import java.text.BreakIterator;
import java.util.Locale;
import java.util.WeakHashMap;

public final class KeyboardGestureRuntime {
    public static final String PREF_ENABLED = "pref_key_xgesture_keyboard_enabled";
    public static final String PREF_GESTURE_DRAWING = "pref_key_xgesture_gesture_drawing";
    private static final String TAG = "XGesture";
    private static final float EDGE_WIDTH_DP = 24f;
    private static final float SWIPE_DISTANCE_DP = 72f;
    private static final long CORNER_HOLD_MS = 500L;
    private static final long SPACE_HOLD_MS = 500L;
    private static final long MULTI_TAP_MS = 350L;
    private static final float CORNER_SIZE_DP = 44f;
    private static final float TRACKPAD_STEP_DP = 12f;
    private static final WeakHashMap<View, TouchState> TOUCH_STATES = new WeakHashMap<>();
    private static final WeakHashMap<View, String> KEY_SLOTS = new WeakHashMap<>();
    private static final WeakHashMap<View, TapHistory> SPACE_TAPS = new WeakHashMap<>();
    private static final WeakHashMap<View, TapHistory> DELETE_TAPS = new WeakHashMap<>();

    private KeyboardGestureRuntime() {}

    public static void registerRenderedKey(View keyView, Object keyDefinition) {
        if (keyView == null || keyDefinition == null) return;
        try {
            String slot = KeyboardIconPackRuntime.resolveSlot(keyView, keyDefinition);
            if (slot != null) KEY_SLOTS.put(keyView, slot);
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to identify a rendered keyboard key.", exception);
        }
    }

    public static boolean onInterceptTouchEvent(View keyboard, MotionEvent event) {
        if (keyboard == null || event == null) return false;
        if (!isEnabled(keyboard.getContext())) {
            clearState(keyboard);
            return false;
        }

        int action = event.getActionMasked();
        TouchState state = TOUCH_STATES.get(keyboard);
        if (action == MotionEvent.ACTION_DOWN) {
            float x = event.getX();
            float y = event.getY();
            String keySlot = findKeySlotAt(keyboard, x, y);
            if ("key.space".equals(keySlot)) {
                state = new TouchState(event.getPointerId(0), x, y, event.getEventTime(),
                    TouchState.SPACE, keySlot);
                int tapCount = tapCountWithin(SPACE_TAPS.get(keyboard), event, keyboard);
                if (tapCount == 1) {
                    state.zone = TouchState.SPACE_UNDO;
                    state.consumed = true;
                    TOUCH_STATES.put(keyboard, state);
                    return true;
                }
                if (tapCount >= 2) {
                    state.zone = TouchState.SPACE_REDO;
                    state.consumed = true;
                    TOUCH_STATES.put(keyboard, state);
                    return true;
                }
            } else if ("key.backspace".equals(keySlot)) {
                state = new TouchState(event.getPointerId(0), x, y, event.getEventTime(),
                    TouchState.DELETE, keySlot);
                if (tapCountWithin(DELETE_TAPS.get(keyboard), event, keyboard) == 1) {
                    state.zone = TouchState.DELETE_UNDO;
                    state.consumed = true;
                    TOUCH_STATES.put(keyboard, state);
                    return true;
                }
            } else {
                float density = keyboard.getResources().getDisplayMetrics().density;
                boolean inCorner = y >= keyboard.getHeight() - CORNER_SIZE_DP * density;
                boolean left = x <= EDGE_WIDTH_DP * density;
                boolean right = x >= keyboard.getWidth() - EDGE_WIDTH_DP * density;
                if (inCorner && (left || right)) {
                    state = new TouchState(event.getPointerId(0), x, y, event.getEventTime(),
                        left ? TouchState.LEFT_CORNER : TouchState.RIGHT_CORNER, keySlot);
                } else if (left) {
                    state = new TouchState(event.getPointerId(0), x, y, event.getEventTime(),
                        TouchState.LEFT_EDGE, keySlot);
                } else if (right) {
                    state = new TouchState(event.getPointerId(0), x, y, event.getEventTime(),
                        TouchState.RIGHT_EDGE, keySlot);
                } else {
                    state = new TouchState(event.getPointerId(0), x, y, event.getEventTime(),
                        TouchState.OTHER, keySlot);
                }
            }
            TOUCH_STATES.put(keyboard, state);
            return false;
        }
        if (state == null) return false;

        if (action == MotionEvent.ACTION_MOVE) {
            if (state.consumed) {
                if (state.drawing) {
                    int pointerIndex = event.findPointerIndex(state.pointerId);
                    if (pointerIndex >= 0) {
                        addStrokePoint(keyboard, state, event.getX(pointerIndex), event.getY(pointerIndex));
                    }
                } else {
                    handleConsumedMovement(keyboard, event, state);
                }
                return true;
            }
            int pointerIndex = event.findPointerIndex(state.pointerId);
            if (pointerIndex < 0) {
                stopDeleteRepeat(keyboard, state);
                TOUCH_STATES.remove(keyboard);
                return false;
            }
            float x = event.getX(pointerIndex);
            float y = event.getY(pointerIndex);
            float dx = x - state.startX;
            float dy = y - state.startY;
            float threshold = SWIPE_DISTANCE_DP * keyboard.getResources().getDisplayMetrics().density;
            if (state.zone == TouchState.DELETE) {
                if (Math.abs(dx) >= threshold && dx < 0 && Math.abs(dx) > Math.abs(dy)) {
                    state.consumed = deleteWord(keyboard.getContext());
                    if (state.consumed) DELETE_TAPS.remove(keyboard);
                } else if (Math.abs(dy) >= threshold && dy < 0 && Math.abs(dy) > Math.abs(dx)) {
                    state.consumed = deletePreviousSentence(keyboard.getContext());
                    if (state.consumed) DELETE_TAPS.remove(keyboard);
                } else if (event.getEventTime() - state.startTime >= SPACE_HOLD_MS) {
                    state.consumed = true;
                    startDeleteRepeat(keyboard, state);
                }
                if (state.consumed) return true;
            } else if (state.zone == TouchState.SPACE) {
                if (event.getPointerCount() >= 2) {
                    beginSelection(keyboard, state);
                    state.consumed = true;
                    handleConsumedMovement(keyboard, event, state);
                    return true;
                }
                if (event.getEventTime() - state.startTime >= SPACE_HOLD_MS) {
                    state.consumed = true;
                    if (isGestureDrawingEnabled(keyboard.getContext())) {
                        beginDrawing(keyboard, state, x, y);
                    } else {
                        state.trackpad = true;
                        handleConsumedMovement(keyboard, event, state);
                    }
                    return true;
                }
            } else if (state.drawing) {
                addStrokePoint(keyboard, state, x, y);
                return true;
            } else if (
                (state.zone == TouchState.LEFT_EDGE && dx > 0) ||
                (state.zone == TouchState.RIGHT_EDGE && dx < 0)
            ) {
                if (Math.abs(dx) >= threshold && Math.abs(dy) <= Math.abs(dx) * 0.8f) {
                    boolean performed = state.zone == TouchState.LEFT_EDGE
                        ? deleteWord(keyboard.getContext())
                        : pasteClipboard(keyboard.getContext());
                    if (performed) {
                        state.consumed = true;
                        return true;
                    }
                }
            }
            return false;
        }

        if (action == MotionEvent.ACTION_POINTER_DOWN && state.zone == TouchState.SPACE) {
            beginSelection(keyboard, state);
            state.consumed = true;
            return true;
        }
        if (action == MotionEvent.ACTION_POINTER_UP && state.consumed) {
            return true;
        }
        if (action == MotionEvent.ACTION_UP) {
            stopDeleteRepeat(keyboard, state);
            if (state.drawing) {
                addStrokePoint(keyboard, state, event.getX(), event.getY());
                if (!performDrawnGesture(keyboard, state)) {
                    replayTrackpadMovement(keyboard, state);
                }
                return true;
            }
            if (state.zone == TouchState.SPACE_UNDO) {
                performUndo(keyboard.getContext());
                updateTapHistory(SPACE_TAPS, keyboard, event, 2);
                TOUCH_STATES.remove(keyboard);
                return true;
            }
            if (state.zone == TouchState.SPACE_REDO) {
                performRedo(keyboard.getContext());
                SPACE_TAPS.remove(keyboard);
                TOUCH_STATES.remove(keyboard);
                return true;
            }
            if (state.zone == TouchState.DELETE_UNDO) {
                performUndo(keyboard.getContext());
                DELETE_TAPS.remove(keyboard);
                TOUCH_STATES.remove(keyboard);
                return true;
            }
            if (state.consumed) {
                TOUCH_STATES.remove(keyboard);
                return true;
            }
            if (state.zone == TouchState.SPACE) {
                if (event.getEventTime() - state.startTime >= SPACE_HOLD_MS) {
                    state.consumed = true;
                    TOUCH_STATES.remove(keyboard);
                    return true;
                }
                updateTapHistory(SPACE_TAPS, keyboard, event, 1);
            } else if (state.zone == TouchState.DELETE) {
                updateTapHistory(DELETE_TAPS, keyboard, event, 1);
            } else if (
                state.zone == TouchState.LEFT_CORNER || state.zone == TouchState.RIGHT_CORNER
            ) {
                float slop = ViewConfiguration.get(keyboard.getContext()).getScaledTouchSlop();
                float dx = event.getX() - state.startX;
                float dy = event.getY() - state.startY;
                if (
                    event.getEventTime() - state.startTime >= CORNER_HOLD_MS &&
                    Math.abs(dx) <= slop && Math.abs(dy) <= slop
                ) {
                    state.consumed = true;
                    showCornerMenu(keyboard);
                    TOUCH_STATES.remove(keyboard);
                    return true;
                }
            } else if (
                state.zone == TouchState.OTHER &&
                state.keySlot != null &&
                (state.keySlot.startsWith("key.letter_") || state.keySlot.startsWith("key.number_"))
            ) {
                scheduleSnippetExpansion(keyboard);
            }
            TOUCH_STATES.remove(keyboard);
            return state.consumed;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            stopDeleteRepeat(keyboard, state);
            TOUCH_STATES.remove(keyboard);
        }
        return state.consumed;
    }

    public static boolean consumeInterceptedTouch(View keyboard, MotionEvent event) {
        TouchState state = keyboard == null ? null : TOUCH_STATES.get(keyboard);
        if (state == null || !state.consumed) return false;
        int action = event == null ? MotionEvent.ACTION_CANCEL : event.getActionMasked();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            stopDeleteRepeat(keyboard, state);
            TOUCH_STATES.remove(keyboard);
        }
        return true;
    }

    private static void handleConsumedMovement(View keyboard, MotionEvent event, TouchState state) {
        InputConnection connection = currentInputConnection(keyboard.getContext());
        if (connection == null) return;
        if (state.selection) {
            int pointerIndex = event.findPointerIndex(state.pointerId);
            if (pointerIndex < 0 || state.selectionAnchor < 0) return;
            float density = keyboard.getResources().getDisplayMetrics().density;
            int steps = Math.round((event.getX(pointerIndex) - state.startX) / (TRACKPAD_STEP_DP * density));
            int position = Math.max(0, state.selectionCursor + steps);
            connection.setSelection(state.selectionAnchor, position);
            return;
        }
        if (!state.trackpad) return;
        float density = keyboard.getResources().getDisplayMetrics().density;
        float step = TRACKPAD_STEP_DP * density;
        int pointerIndex = event.findPointerIndex(state.pointerId);
        if (pointerIndex < 0) return;
        float dx = event.getX(pointerIndex) - state.lastX;
        float dy = event.getY(pointerIndex) - state.lastY;
        state.lastX = event.getX(pointerIndex);
        state.lastY = event.getY(pointerIndex);
        if (Math.abs(dx) >= step && Math.abs(dx) >= Math.abs(dy)) {
            moveCursor(keyboard.getContext(), dx < 0 ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT,
                Math.max(1, (int) (Math.abs(dx) / step)));
        } else if (Math.abs(dy) >= step) {
            moveCursor(keyboard.getContext(), dy < 0 ? KeyEvent.KEYCODE_DPAD_UP : KeyEvent.KEYCODE_DPAD_DOWN,
                Math.max(1, (int) (Math.abs(dy) / step)));
        }
    }

    private static void beginDrawing(View keyboard, TouchState state, float x, float y) {
        state.drawing = true;
        state.strokePoints.add(new StrokePoint(state.startX, state.startY));
        state.lastX = state.startX;
        state.lastY = state.startY;
        addStrokePoint(keyboard, state, x, y);
    }

    private static void addStrokePoint(View keyboard, TouchState state, float x, float y) {
        float density = keyboard.getResources().getDisplayMetrics().density;
        if (state.strokePoints.isEmpty()) {
            state.strokePoints.add(new StrokePoint(x, y));
            state.lastX = x;
            state.lastY = y;
            return;
        }
        float dx = x - state.lastX;
        float dy = y - state.lastY;
        if (dx * dx + dy * dy < 9f * density * density) return;
        state.strokePoints.add(new StrokePoint(x, y));
        state.lastX = x;
        state.lastY = y;
    }

    private static boolean performDrawnGesture(View keyboard, TouchState state) {
        float density = keyboard.getResources().getDisplayMetrics().density;
        char gesture = recognizeGesture(state.strokePoints, 24f * density, 12f * density);
        InputConnection connection = currentInputConnection(keyboard.getContext());
        if (connection == null) return false;
        switch (gesture) {
            case 'C':
                return connection.performContextMenuAction(android.R.id.copy);
            case 'P':
                return pasteClipboard(connection, keyboard.getContext());
            case 'U':
                sendModifiedKey(connection, KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON);
                return true;
            case 'R':
                sendModifiedKey(
                    connection,
                    KeyEvent.KEYCODE_Z,
                    KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON
                );
                return true;
            default:
                return false;
        }
    }

    private static void replayTrackpadMovement(View keyboard, TouchState state) {
        float density = keyboard.getResources().getDisplayMetrics().density;
        float step = TRACKPAD_STEP_DP * density;
        float dx = state.lastX - state.startX;
        float dy = state.lastY - state.startY;
        if (Math.abs(dx) >= step && Math.abs(dx) >= Math.abs(dy)) {
            moveCursor(
                keyboard.getContext(),
                dx < 0 ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT,
                Math.max(1, (int) (Math.abs(dx) / step))
            );
        } else if (Math.abs(dy) >= step) {
            moveCursor(
                keyboard.getContext(),
                dy < 0 ? KeyEvent.KEYCODE_DPAD_UP : KeyEvent.KEYCODE_DPAD_DOWN,
                Math.max(1, (int) (Math.abs(dy) / step))
            );
        }
    }

    private static char recognizeGesture(List<StrokePoint> points, float minimumWidth, float minimumHeight) {
        if (points.size() < 5) return 0;
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (StrokePoint point : points) {
            minX = Math.min(minX, point.x);
            maxX = Math.max(maxX, point.x);
            minY = Math.min(minY, point.y);
            maxY = Math.max(maxY, point.y);
        }
        if (maxX - minX < minimumWidth || maxY - minY < minimumHeight) return 0;

        float[] input = resample(points, minX, maxX, minY, maxY);
        char[] candidates = { 'C', 'P', 'U', 'R' };
        char best = 0;
        float bestScore = Float.MAX_VALUE;
        float secondBestScore = Float.MAX_VALUE;
        for (char candidate : candidates) {
            float score = shapeDistance(input, gestureTemplate(candidate));
            if (score < bestScore) {
                secondBestScore = bestScore;
                bestScore = score;
                best = candidate;
            } else if (score < secondBestScore) {
                secondBestScore = score;
            }
        }
        return bestScore <= 0.29f && secondBestScore - bestScore >= 0.025f ? best : 0;
    }

    private static float[] resample(
        List<StrokePoint> points,
        float minX,
        float maxX,
        float minY,
        float maxY
    ) {
        final int samples = 24;
        float total = 0f;
        float[] lengths = new float[points.size()];
        for (int index = 1; index < points.size(); index++) {
            StrokePoint previous = points.get(index - 1);
            StrokePoint current = points.get(index);
            total += (float) Math.hypot(current.x - previous.x, current.y - previous.y);
            lengths[index] = total;
        }

        float[] result = new float[samples * 2];
        int segment = 1;
        for (int sample = 0; sample < samples; sample++) {
            float distance = total * sample / (samples - 1);
            while (segment < lengths.length - 1 && lengths[segment] < distance) segment++;
            float segmentLength = lengths[segment] - lengths[segment - 1];
            float fraction = segmentLength == 0 ? 0f :
                (distance - lengths[segment - 1]) / segmentLength;
            StrokePoint start = points.get(segment - 1);
            StrokePoint end = points.get(segment);
            float x = start.x + (end.x - start.x) * fraction;
            float y = start.y + (end.y - start.y) * fraction;
            result[sample * 2] = (x - minX) / (maxX - minX);
            result[sample * 2 + 1] = (y - minY) / (maxY - minY);
        }
        return result;
    }

    private static float shapeDistance(float[] first, float[] second) {
        float forward = 0f;
        float reverse = 0f;
        int samples = first.length / 2;
        for (int index = 0; index < samples; index++) {
            int reverseIndex = samples - index - 1;
            float dx = first[index * 2] - second[index * 2];
            float dy = first[index * 2 + 1] - second[index * 2 + 1];
            forward += dx * dx + dy * dy;
            dx = first[index * 2] - second[reverseIndex * 2];
            dy = first[index * 2 + 1] - second[reverseIndex * 2 + 1];
            reverse += dx * dx + dy * dy;
        }
        return (float) Math.sqrt(Math.min(forward, reverse) / samples);
    }

    private static float[] gestureTemplate(char gesture) {
        float[][] path;
        switch (gesture) {
            case 'C':
                path = new float[][] {
                    { .88f, .16f }, { .62f, .05f }, { .34f, .08f }, { .14f, .28f },
                    { .08f, .50f }, { .14f, .74f }, { .36f, .92f }, { .66f, .94f }, { .90f, .82f }
                };
                break;
            case 'P':
                path = new float[][] {
                    { .18f, .94f }, { .18f, .08f }, { .58f, .08f }, { .84f, .22f },
                    { .86f, .42f }, { .68f, .54f }, { .18f, .54f }
                };
                break;
            case 'U':
                path = new float[][] {
                    { .14f, .08f }, { .14f, .64f }, { .22f, .86f }, { .42f, .94f },
                    { .64f, .92f }, { .82f, .76f }, { .86f, .08f }
                };
                break;
            default:
                path = new float[][] {
                    { .18f, .94f }, { .18f, .08f }, { .58f, .08f }, { .84f, .22f },
                    { .86f, .42f }, { .68f, .54f }, { .18f, .54f }, { .48f, .62f }, { .86f, .94f }
                };
        }
        List<StrokePoint> templatePoints = new ArrayList<>();
        for (float[] point : path) templatePoints.add(new StrokePoint(point[0], point[1]));
        return resample(templatePoints, 0f, 1f, 0f, 1f);
    }

    private static void beginSelection(View keyboard, TouchState state) {
        InputConnection connection = currentInputConnection(keyboard.getContext());
        if (connection == null) return;
        ExtractedText extracted = connection.getExtractedText(new ExtractedTextRequest(), 0);
        if (extracted == null) return;
        state.selection = true;
        state.selectionAnchor = extracted.startOffset + extracted.selectionEnd;
        state.selectionCursor = state.selectionAnchor;
        state.startX = state.lastX;
        state.startY = state.lastY;
    }

    private static void startDeleteRepeat(View keyboard, TouchState state) {
        if (state.repeatRunnable != null) return;
        state.repeatStartedAt = System.currentTimeMillis();
        state.repeatRunnable = new Runnable() {
            @Override
            public void run() {
                if (TOUCH_STATES.get(keyboard) != state || !state.consumed) return;
                deletePreviousCharacter(keyboard.getContext());
                long elapsed = System.currentTimeMillis() - state.repeatStartedAt;
                keyboard.postDelayed(this, elapsed > 1800 ? 30L : elapsed > 900 ? 55L : 100L);
            }
        };
        keyboard.postDelayed(state.repeatRunnable, 120L);
    }

    private static void stopDeleteRepeat(View keyboard, TouchState state) {
        if (state.repeatRunnable != null) {
            keyboard.removeCallbacks(state.repeatRunnable);
            state.repeatRunnable = null;
        }
    }

    private static void scheduleSnippetExpansion(View keyboard) {
        keyboard.postDelayed(() -> {
            InputConnection connection = currentInputConnection(keyboard.getContext());
            try {
                KeyboardMacroManager.expandIfMatched(keyboard.getContext(), connection);
            } catch (RuntimeException exception) {
                Log.e(TAG, "Unable to expand a snippet or run a macro.", exception);
            }
        }, 80L);
    }

    private static boolean isEnabled(Context context) {
        try {
            return GlideTrailPreferences.getPrefs(context).getBoolean(PREF_ENABLED, false);
        } catch (RuntimeException exception) {
            Log.e(TAG, "Unable to read the keyboard gesture setting.", exception);
            return false;
        }
    }

    private static boolean isGestureDrawingEnabled(Context context) {
        try {
            return GlideTrailPreferences.getPrefs(context).getBoolean(PREF_GESTURE_DRAWING, true);
        } catch (RuntimeException exception) {
            Log.e(TAG, "Unable to read the gesture-drawing setting.", exception);
            return false;
        }
    }

    private static void clearState(View keyboard) {
        TouchState state = TOUCH_STATES.remove(keyboard);
        if (state != null) stopDeleteRepeat(keyboard, state);
    }

    private static String findKeySlotAt(View keyboard, float x, float y) {
        int[] location = new int[2];
        keyboard.getLocationOnScreen(location);
        return findRegisteredKeySlotAt(keyboard, location[0] + x, location[1] + y);
    }

    private static String findRegisteredKeySlotAt(View view, float screenX, float screenY) {
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = group.getChildCount() - 1; index >= 0; index--) {
                String slot = findRegisteredKeySlotAt(group.getChildAt(index), screenX, screenY);
                if (slot != null) return slot;
            }
        }
        String slot = KEY_SLOTS.get(view);
        if (slot == null) return null;
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        return screenX >= location[0] && screenX <= location[0] + view.getWidth() &&
            screenY >= location[1] && screenY <= location[1] + view.getHeight() ? slot : null;
    }

    private static int tapCountWithin(TapHistory history, MotionEvent event, View view) {
        if (history == null || event.getEventTime() - history.time > MULTI_TAP_MS) return 0;
        float slop = ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
        float dx = event.getX() - history.x;
        float dy = event.getY() - history.y;
        return Math.abs(dx) <= slop && Math.abs(dy) <= slop ? history.count : 0;
    }

    private static void updateTapHistory(
        WeakHashMap<View, TapHistory> histories,
        View keyboard,
        MotionEvent event,
        int count
    ) {
        histories.put(keyboard, new TapHistory(event.getEventTime(), event.getX(), event.getY(), count));
    }

    private static InputConnection currentInputConnection(Context context) {
        Context current = context;
        while (current != null) {
            if (current instanceof InputMethodService) {
                return ((InputMethodService) current).getCurrentInputConnection();
            }
            if (!(current instanceof ContextWrapper)) break;
            Context next = ((ContextWrapper) current).getBaseContext();
            if (next == current) break;
            current = next;
        }
        Log.w(TAG, "The Gboard input connection is unavailable from the keyboard view context.");
        return null;
    }

    public static boolean deleteWord(Context context) {
        return deleteWord(currentInputConnection(context));
    }

    public static boolean deleteWord(InputConnection connection) {
        if (connection == null) return false;
        CharSequence before = connection.getTextBeforeCursor(1024, 0);
        if (TextUtils.isEmpty(before)) return false;

        String text = before.toString();
        int end = trimTrailingWhitespace(text, text.length());
        if (end == 0) return false;

        BreakIterator words = BreakIterator.getWordInstance(Locale.getDefault());
        words.setText(text);
        int cursor = end;
        int start = end;
        while (cursor > 0) {
            int boundary = words.preceding(cursor);
            if (boundary == BreakIterator.DONE) boundary = 0;
            if (containsWordCharacter(text, boundary, cursor)) {
                start = boundary;
                break;
            }
            cursor = boundary;
            start = boundary;
        }
        while (start > 0) {
            int codePoint = text.codePointBefore(start);
            if (!Character.isWhitespace(codePoint)) break;
            start -= Character.charCount(codePoint);
        }
        int deleteLength = text.length() - start;
        return deleteLength > 0 && connection.deleteSurroundingText(deleteLength, 0);
    }

    private static boolean deletePreviousSentence(Context context) {
        InputConnection connection = currentInputConnection(context);
        if (connection == null) return false;
        CharSequence before = connection.getTextBeforeCursor(4096, 0);
        if (TextUtils.isEmpty(before)) return false;
        String text = before.toString();
        int end = trimTrailingWhitespace(text, text.length());
        BreakIterator sentences = BreakIterator.getSentenceInstance(Locale.getDefault());
        sentences.setText(text);
        int start = sentences.preceding(end);
        if (start == BreakIterator.DONE) start = 0;
        while (start < end && Character.isWhitespace(text.charAt(start))) start++;
        return end > start && connection.deleteSurroundingText(end - start, 0);
    }

    private static int trimTrailingWhitespace(String text, int end) {
        while (end > 0) {
            int codePoint = text.codePointBefore(end);
            if (!Character.isWhitespace(codePoint)) break;
            end -= Character.charCount(codePoint);
        }
        return end;
    }

    private static boolean containsWordCharacter(String text, int start, int end) {
        for (int index = start; index < end;) {
            int codePoint = text.codePointAt(index);
            int type = Character.getType(codePoint);
            if (
                Character.isLetterOrDigit(codePoint) ||
                type == Character.NON_SPACING_MARK ||
                type == Character.COMBINING_SPACING_MARK ||
                type == Character.ENCLOSING_MARK
            ) return true;
            index += Character.charCount(codePoint);
        }
        return false;
    }

    private static boolean deletePreviousCharacter(Context context) {
        InputConnection connection = currentInputConnection(context);
        if (connection == null) return false;
        CharSequence before = connection.getTextBeforeCursor(2, 0);
        if (TextUtils.isEmpty(before)) return false;
        int codePoint = Character.codePointBefore(before, before.length());
        return connection.deleteSurroundingText(Character.charCount(codePoint), 0);
    }

    private static boolean pasteClipboard(Context context) {
        return pasteClipboard(currentInputConnection(context), context);
    }

    static boolean pasteClipboardFromActiveContext(InputConnection connection, Context context) {
        return pasteClipboard(connection, context);
    }

    private static boolean pasteClipboard(InputConnection connection, Context context) {
        if (connection == null) return false;
        Object service = context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (!(service instanceof ClipboardManager)) {
            Log.w(TAG, "The system clipboard service is unavailable.");
            return false;
        }
        ClipboardManager clipboard = (ClipboardManager) service;
        if (!clipboard.hasPrimaryClip()) return false;
        ClipData clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) return false;
        CharSequence text = clip.getItemAt(0).getText();
        if (TextUtils.isEmpty(text)) {
            Log.w(TAG, "The current clipboard item does not contain plain text.");
            return false;
        }
        return connection.commitText(text, 1);
    }

    private static boolean moveCursor(Context context, int keyCode, int count) {
        InputConnection connection = currentInputConnection(context);
        if (connection == null) return false;
        for (int index = 0; index < count; index++) {
            connection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
            connection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
        }
        return true;
    }

    private static void performUndo(Context context) {
        sendModifiedKey(context, KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON);
    }

    private static void performRedo(Context context) {
        sendModifiedKey(context, KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON);
    }

    private static void sendModifiedKey(Context context, int keyCode, int modifiers) {
        InputConnection connection = currentInputConnection(context);
        if (connection == null) return;
        sendModifiedKey(connection, keyCode, modifiers);
    }

    private static void sendModifiedKey(InputConnection connection, int keyCode, int modifiers) {
        connection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode, 0, 0, 0, modifiers));
        connection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode, 0, 0, 0, modifiers));
    }

    private static void showCornerMenu(View keyboard) {
        PopupMenu popup = new PopupMenu(keyboard.getContext(), keyboard);
        popup.getMenu().add("Delete previous word").setOnMenuItemClickListener(item ->
            deleteWord(keyboard.getContext())
        );
        popup.getMenu().add("Paste clipboard").setOnMenuItemClickListener(item ->
            pasteClipboard(keyboard.getContext())
        );
        popup.getMenu().add("Move cursor left").setOnMenuItemClickListener(item ->
            moveCursor(keyboard.getContext(), KeyEvent.KEYCODE_DPAD_LEFT, 1)
        );
        popup.getMenu().add("Move cursor right").setOnMenuItemClickListener(item ->
            moveCursor(keyboard.getContext(), KeyEvent.KEYCODE_DPAD_RIGHT, 1)
        );
        popup.getMenu().add("Snippets & macros").setOnMenuItemClickListener(item -> {
            KeyboardMacroManager.show(keyboard.getContext());
            return true;
        });
        try {
            popup.show();
        } catch (RuntimeException exception) {
            Log.e(TAG, "Unable to show the keyboard corner action menu.", exception);
        }
    }

    private static final class TouchState {
        static final int LEFT_EDGE = 1;
        static final int RIGHT_EDGE = 2;
        static final int LEFT_CORNER = 3;
        static final int RIGHT_CORNER = 4;
        static final int SPACE = 5;
        static final int DELETE = 6;
        static final int OTHER = 7;
        static final int SPACE_UNDO = 8;
        static final int SPACE_REDO = 9;
        static final int DELETE_UNDO = 10;

        final int pointerId;
        float startX;
        float startY;
        float lastX;
        float lastY;
        final long startTime;
        int zone;
        final String keySlot;
        boolean consumed;
        boolean trackpad;
        boolean drawing;
        boolean selection;
        int selectionAnchor = -1;
        int selectionCursor = -1;
        long repeatStartedAt;
        Runnable repeatRunnable;
        final List<StrokePoint> strokePoints = new ArrayList<>();

        TouchState(int pointerId, float x, float y, long startTime, int zone, String keySlot) {
            this.pointerId = pointerId;
            this.startX = x;
            this.startY = y;
            this.lastX = x;
            this.lastY = y;
            this.startTime = startTime;
            this.zone = zone;
            this.keySlot = keySlot;
        }
    }

    private static final class StrokePoint {
        final float x;
        final float y;

        StrokePoint(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    private static final class TapHistory {
        final long time;
        final float x;
        final float y;
        final int count;

        TapHistory(long time, float x, float y, int count) {
            this.time = time;
            this.x = x;
            this.y = y;
            this.count = count;
        }
    }
}
