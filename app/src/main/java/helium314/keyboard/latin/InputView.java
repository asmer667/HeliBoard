/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;

import androidx.core.view.ViewKt;

import helium314.keyboard.accessibility.AccessibilityUtils;
import helium314.keyboard.keyboard.MainKeyboardView;
import helium314.keyboard.latin.common.ColorType;
import helium314.keyboard.latin.settings.Settings;
import helium314.keyboard.latin.suggestions.MoreSuggestionsView;
import helium314.keyboard.latin.suggestions.SuggestionStripView;
import helium314.keyboard.latin.utils.FloatingKeyboardUtils;
import kotlin.Unit;

public final class InputView extends FrameLayout {
    private final Rect mInputViewRect = new Rect();
    private MainKeyboardView mMainKeyboardView;
    private KeyboardTopPaddingForwarder mKeyboardTopPaddingForwarder;
    private MoreSuggestionsViewCanceler mMoreSuggestionsViewCanceler;
    private MotionEventForwarder<?, ?> mActiveForwarder;

    public InputView(final Context context, final AttributeSet attrs) {
        super(context, attrs, 0);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        final SuggestionStripView suggestionStripView =
                findViewById(R.id.suggestion_strip_view);
        mMainKeyboardView = findViewById(R.id.keyboard_view);
        mKeyboardTopPaddingForwarder = new KeyboardTopPaddingForwarder(
                mMainKeyboardView, suggestionStripView);
        mMoreSuggestionsViewCanceler = new MoreSuggestionsViewCanceler(
                mMainKeyboardView, suggestionStripView);
        ViewKt.doOnNextLayout(this, this::onNextLayout);

        // ==========================================================
        // ربط أزرار شريط الأدوات الجديد
        // ==========================================================
        setupToolbarButtons();
    }

    // ✅ الحل النهائي: الحصول على InputConnection مباشرة من النافذة الحالية
    private InputConnection getInputConnection() {
        // استخدام Context الحالي للحصول على InputMethodManager
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            // محاولة الحصول على InputConnection من النافذة الحالية
            View rootView = getRootView();
            if (rootView != null) {
                return imm.getInputConnection(rootView, 0);
            }
        }
        return null;
    }

    // دالة مساعدة لإرسال أوامر التنقل (KeyEvent)
    private void sendKey(int keyCode) {
        InputConnection ic = getInputConnection();
        if (ic != null) {
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
        }
    }

    // دالة ربط الأزرار بالوظائف
    private void setupToolbarButtons() {
        ImageButton btnEnd = findViewById(R.id.btn_end);
        ImageButton btnRight = findViewById(R.id.btn_right);
        ImageButton btnSelect = findViewById(R.id.btn_select);
        ImageButton btnClear = findViewById(R.id.btn_clear);
        ImageButton btnPaste = findViewById(R.id.btn_paste);
        ImageButton btnCopy = findViewById(R.id.btn_copy);
        ImageButton btnLeft = findViewById(R.id.btn_left);
        ImageButton btnHome = findViewById(R.id.btn_home);

        // 1. زر النهاية (End)
        if (btnEnd != null) {
            btnEnd.setOnClickListener(v -> sendKey(KeyEvent.KEYCODE_MOVE_END));
        }

        // 2. زر اليمين (Right)
        if (btnRight != null) {
            btnRight.setOnClickListener(v -> sendKey(KeyEvent.KEYCODE_DPAD_RIGHT));
        }

        // 3. زر التحديد (Select All)
        if (btnSelect != null) {
            btnSelect.setOnClickListener(v -> {
                InputConnection ic = getInputConnection();
                if (ic != null) ic.performContextMenuAction(android.R.id.selectAll);
            });
        }

        // 4. زر المسح (Clear)
        if (btnClear != null) {
            btnClear.setOnClickListener(v -> {
                InputConnection ic = getInputConnection();
                if (ic != null) ic.deleteSurroundingText(1000, 1000);
            });
        }

        // 5. زر اللصق (Paste)
        if (btnPaste != null) {
            btnPaste.setOnClickListener(v -> {
                InputConnection ic = getInputConnection();
                if (ic != null) ic.performContextMenuAction(android.R.id.paste);
            });
        }

        // 6. زر النسخ (Copy)
        if (btnCopy != null) {
            btnCopy.setOnClickListener(v -> {
                InputConnection ic = getInputConnection();
                if (ic != null) ic.performContextMenuAction(android.R.id.copy);
            });
        }

        // 7. زر اليسار (Left)
        if (btnLeft != null) {
            btnLeft.setOnClickListener(v -> sendKey(KeyEvent.KEYCODE_DPAD_LEFT));
        }

        // 8. زر البداية (Home)
        if (btnHome != null) {
            btnHome.setOnClickListener(v -> sendKey(KeyEvent.KEYCODE_MOVE_HOME));
        }
    }

    public void setKeyboardTopPadding(final int keyboardTopPadding) {
        mKeyboardTopPaddingForwarder.setKeyboardTopPadding(keyboardTopPadding);
    }

    @Override
    protected boolean dispatchHoverEvent(final MotionEvent event) {
        if (AccessibilityUtils.Companion.getInstance().isTouchExplorationEnabled()
                && mMainKeyboardView.isShowingPopupKeysPanel()) {
            return true;
        }
        return super.dispatchHoverEvent(event);
    }

    @Override
    public boolean onInterceptTouchEvent(final MotionEvent me) {
        final Rect rect = mInputViewRect;
        getGlobalVisibleRect(rect);
        final int index = me.getActionIndex();
        final int x = (int)me.getX(index) + rect.left;
        final int y = (int)me.getY(index) + rect.top;

        if (mKeyboardTopPaddingForwarder.onInterceptTouchEvent(x, y, me)) {
            mActiveForwarder = mKeyboardTopPaddingForwarder;
            return true;
        }

        if (mMoreSuggestionsViewCanceler.onInterceptTouchEvent(x, y, me)) {
            mActiveForwarder = mMoreSuggestionsViewCanceler;
            return true;
        }

        mActiveForwarder = null;
        return false;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(final MotionEvent me) {
        if (mActiveForwarder == null) {
            return super.onTouchEvent(me);
        }

        final Rect rect = mInputViewRect;
        getGlobalVisibleRect(rect);
        final int index = me.getActionIndex();
        final int x = (int)me.getX(index) + rect.left;
        final int y = (int)me.getY(index) + rect.top;
        return mActiveForwarder.onTouchEvent(x, y, me);
    }

    private Unit onNextLayout(View v) {
        Settings.getValues().mColors.setBackground(findViewById(R.id.main_keyboard_frame), ColorType.MAIN_BACKGROUND);

        requestApplyInsets();

        if (Settings.getValues().mIsFloatingKeyboard)
            FloatingKeyboardUtils.setFloating(this);
        return null;
    }

    private static abstract class
            MotionEventForwarder<SenderView extends View, ReceiverView extends View> {
        protected final SenderView mSenderView;
        protected final ReceiverView mReceiverView;

        protected final Rect mEventSendingRect = new Rect();
        protected final Rect mEventReceivingRect = new Rect();

        public MotionEventForwarder(final SenderView senderView, final ReceiverView receiverView) {
            mSenderView = senderView;
            mReceiverView = receiverView;
        }

        protected abstract boolean needsToForward(final int x, final int y);

        protected int translateX(final int x) {
            return x - mEventReceivingRect.left;
        }

        protected int translateY(final int y) {
            return y - mEventReceivingRect.top;
        }

        protected void onForwardingEvent(final MotionEvent me) {}

        public boolean onInterceptTouchEvent(final int x, final int y, final MotionEvent me) {
            if (mSenderView.getVisibility() != View.VISIBLE ||
                    mReceiverView.getVisibility() != View.VISIBLE) {
                return false;
            }
            mSenderView.getGlobalVisibleRect(mEventSendingRect);
            if (!mEventSendingRect.contains(x, y)) {
                return false;
            }

            if (me.getActionMasked() == MotionEvent.ACTION_DOWN) {
                return needsToForward(x, y);
            }

            return false;
        }

        public boolean onTouchEvent(final int x, final int y, final MotionEvent me) {
            mReceiverView.getGlobalVisibleRect(mEventReceivingRect);
            me.setLocation(translateX(x), translateY(y));
            mReceiverView.dispatchTouchEvent(me);
            onForwardingEvent(me);
            return true;
        }
    }

    private static class KeyboardTopPaddingForwarder
            extends MotionEventForwarder<MainKeyboardView, SuggestionStripView> {
        private int mKeyboardTopPadding;

        public KeyboardTopPaddingForwarder(final MainKeyboardView mainKeyboardView,
                final SuggestionStripView suggestionStripView) {
            super(mainKeyboardView, suggestionStripView);
        }

        public void setKeyboardTopPadding(final int keyboardTopPadding) {
            mKeyboardTopPadding = keyboardTopPadding;
        }

        private boolean isInKeyboardTopPadding(final int y) {
            return y < mEventSendingRect.top + mKeyboardTopPadding;
        }

        @Override
        protected boolean needsToForward(final int x, final int y) {
            final View mainKeyboardFrame = (View)mSenderView.getParent();
            return mainKeyboardFrame.getVisibility() == View.VISIBLE && isInKeyboardTopPadding(y);
        }

        @Override
        protected int translateY(final int y) {
            final int translatedY = super.translateY(y);
            if (isInKeyboardTopPadding(y)) {
                return Math.min(translatedY, mEventReceivingRect.height() - 1);
            }
            return translatedY;
        }
    }

    private static class MoreSuggestionsViewCanceler
            extends MotionEventForwarder<MainKeyboardView, SuggestionStripView> {
        public MoreSuggestionsViewCanceler(final MainKeyboardView mainKeyboardView,
                final SuggestionStripView suggestionStripView) {
            super(mainKeyboardView, suggestionStripView);
        }

        @Override
        protected boolean needsToForward(final int x, final int y) {
            return mReceiverView.isShowingMoreSuggestionPanel() && mEventSendingRect.contains(x, y);
        }

        @Override
        protected void onForwardingEvent(final MotionEvent me) {
            if (me.getActionMasked() == MotionEvent.ACTION_DOWN) {
                mReceiverView.dismissMoreSuggestionsPanel();
            }
        }
    }
}
