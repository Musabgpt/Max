package com.maxassistant.app.automation;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** A trusted accessibility overlay: never uses the unrelated draw-over-other-apps permission. */
final class TaskOverlay {
    private final AccessibilityService service;
    private final WindowManager windows;
    private LinearLayout panel;
    private TextView status;
    private ScrollView review;
    private TextView reviewText;
    private Button confirm;
    private Runnable confirmation;
    private final Runnable cancellation;

    TaskOverlay(AccessibilityService service, Runnable cancellation) {
        this.service = service; this.cancellation = cancellation;
        windows = (WindowManager) service.getSystemService(AccessibilityService.WINDOW_SERVICE);
    }
    private int dp(int value) { return (int) (value * service.getResources().getDisplayMetrics().density + 0.5f); }
    void show(String message) {
        if (panel == null) create();
        status.setText("MAX · " + message);
    }
    private void create() {
        panel = new LinearLayout(service); panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(10), dp(14), dp(10)); panel.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        panel.setContentDescription("max_task_overlay");
        GradientDrawable background = new GradientDrawable(); background.setColor(Color.rgb(24, 29, 42)); background.setCornerRadius(dp(16));
        panel.setBackground(background); panel.setElevation(dp(16));
        status = new TextView(service); status.setTextColor(Color.WHITE); status.setTextSize(16); status.setContentDescription("max_task_status");
        panel.addView(status, new LinearLayout.LayoutParams(-1, -2));
        review = new ScrollView(service); review.setVisibility(View.GONE); review.setFillViewport(false);
        reviewText = new TextView(service); reviewText.setTextColor(Color.WHITE); reviewText.setTextSize(15); reviewText.setTextIsSelectable(true); reviewText.setPadding(0, dp(10), 0, dp(10));
        review.addView(reviewText); panel.addView(review, new LinearLayout.LayoutParams(-1, dp(175)));
        LinearLayout buttons = new LinearLayout(service); buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button cancel = new Button(service); cancel.setText("إلغاء المهمة"); cancel.setContentDescription("max_task_cancel");
        cancel.setOnClickListener(view -> cancellation.run()); buttons.addView(cancel, new LinearLayout.LayoutParams(0, -2, 1));
        confirm = new Button(service); confirm.setVisibility(View.GONE); confirm.setContentDescription("max_task_confirm");
        confirm.setOnClickListener(view -> {
            Runnable action = confirmation;
            // Remove the callback before execution: neither a second tap nor an old View can reuse it.
            confirmation = null; confirm.setEnabled(false);
            if (action != null) action.run();
        });
        buttons.addView(confirm, new LinearLayout.LayoutParams(0, -2, 1)); panel.addView(buttons);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(-1, -2, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL; params.setTitle("MAX task review");
        windows.addView(panel, params);
    }
    void requestConfirmation(String text, boolean facebook, Runnable action) {
        show("راجع المسودة في التطبيق ثم أكّد الخطوة النهائية");
        reviewText.setText(text); review.setVisibility(View.VISIBLE);
        confirm.setText(facebook ? "تأكيد النشر" : "تأكيد الإرسال"); confirm.setEnabled(true); confirm.setVisibility(View.VISIBLE);
        confirmation = action;
    }
    void close() {
        confirmation = null;
        if (panel != null) {
            try { windows.removeViewImmediate(panel); } catch (RuntimeException ignored) { }
            panel = null;
        }
    }
}
