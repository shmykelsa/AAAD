package com.legs.appsforaa.utils;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.legs.appsforaa.R;

public class BottomDialog {
    private static final int DEFAULT_SHADOW_HEIGHT = 3;

    private final Builder mBuilder;
    private ImageView vIcon ;

    public void setTitleTextView(TextView vTitle) {
        this.vTitle = vTitle;
    }

    public void setContentTextView(TextView vContent) {
        this.vContent = vContent;
    }

    private TextView vTitle ;
    private TextView vContent;
    private FrameLayout vCustomView;
    private Button vNegative ;
    private Button vPositive;

    public Builder getBuilder() {
        return mBuilder;
    }

    public ImageView getIconImageView() {
        return vIcon;
    }

    public TextView getTitleTextView() {
        return vTitle;
    }

    public TextView getContentTextView() {
        return vContent;
    }

    public Button getNegativeButton() {
        return vNegative;
    }

    public Button getPositiveButton() {
        return vPositive;
    }

    public View getCustomView() { return vCustomView; }

    BottomDialog(Builder builder) {
        mBuilder = builder;
        mBuilder.bottomDialog = initBottomDialog(builder);
    }

    @UiThread
    public void show() {
        if (mBuilder != null && mBuilder.bottomDialog != null)
            mBuilder.bottomDialog.show();
    }

    @UiThread
    public void dismiss() {
        if (mBuilder != null && mBuilder.bottomDialog != null)
            mBuilder.bottomDialog.dismiss();
    }

    public void setOnDismissListener(DialogInterface.OnDismissListener onDismissListener) {
        if (mBuilder != null && mBuilder.bottomDialog != null)
            mBuilder.bottomDialog.setOnDismissListener(onDismissListener);
    }

    @UiThread
    private Dialog initBottomDialog(final Builder builder) {
        final Dialog bottomDialog = new Dialog(builder.context, com.github.javiersantos.bottomdialogs.R.style.BottomDialogs);
        View view = LayoutInflater.from(builder.context).inflate(com.github.javiersantos.bottomdialogs.R.layout.library_bottom_dialog, null);

        View container = view.findViewById(com.github.javiersantos.bottomdialogs.R.id.bottomDialog_container);
        View shadow = view.findViewById(com.github.javiersantos.bottomdialogs.R.id.bottomDialog_shadow);

        vIcon = view.findViewById(com.github.javiersantos.bottomdialogs.R.id.bottomDialog_icon);
        vTitle = view.findViewById(com.github.javiersantos.bottomdialogs.R.id.bottomDialog_title);
        vContent = view.findViewById(com.github.javiersantos.bottomdialogs.R.id.bottomDialog_content);
        vCustomView = view.findViewById(com.github.javiersantos.bottomdialogs.R.id.bottomDialog_custom_view);
        vNegative = view.findViewById(com.github.javiersantos.bottomdialogs.R.id.bottomDialog_cancel);
        vPositive = view.findViewById(com.github.javiersantos.bottomdialogs.R.id.bottomDialog_ok);

        // Apply style changes
        container.setBackgroundColor(builder.backgroundColor);
        if (builder.shadowHeight != DEFAULT_SHADOW_HEIGHT) {
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams)
                    shadow.getLayoutParams();
            params.height = UtilsLibrary.dpToPixels(builder.context,
                    builder.shadowHeight);
            shadow.setLayoutParams(params);
        }

        if (builder.icon != null) {
            vIcon.setVisibility(View.VISIBLE);
            vIcon.setImageDrawable(builder.icon);
        }

        if (builder.title != null) {
            vTitle.setText(builder.title);
            vTitle.setTextColor(builder.context.getResources().getColor(R.color.dialog_text_color, builder.context.getTheme()));
            vTitle.setTextAppearance(R.style.TitleBarTextAppearance);
        } else {
            vTitle.setVisibility(View.GONE);
        }

        if (builder.content != null) {
            vContent.setText(builder.content);
            vContent.setTextColor(builder.context.getResources().getColor(R.color.dialog_text_color, builder.context.getTheme()));
            vContent.setTextAppearance(R.style.NormalTextAppeareance);
        } else {
            vContent.setVisibility(View.GONE);
        }

        if (builder.customView != null) {
            if (builder.customView.getParent() != null)
                ((ViewGroup) builder.customView.getParent()).removeAllViews();
            vCustomView.addView(builder.customView);
            vCustomView.setPadding(builder.customViewPaddingLeft, builder.customViewPaddingTop, builder.customViewPaddingRight, builder.customViewPaddingBottom);
        }

        if (builder.btn_positive != null) {
            vPositive.setVisibility(View.VISIBLE);
            vPositive.setText(builder.btn_positive);
            vPositive.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (builder.btn_positive_callback != null)
                        builder.btn_positive_callback.onClick(BottomDialog.this);
                    if (builder.isAutoDismiss)
                        bottomDialog.dismiss();
                }
            });

            if (builder.btn_colorPositive != 0) {
                vPositive.setTextColor(builder.btn_colorPositive);
            }

            if (builder.btn_colorPositiveBackground == 0) {
                TypedValue v = new TypedValue();
                boolean hasColorPrimary = builder.context.getTheme().resolveAttribute(com.github.javiersantos.bottomdialogs.R.attr.colorPrimary, v, true);
                builder.btn_colorPositiveBackground = !hasColorPrimary ? v.data : ContextCompat.getColor(builder.context, com.github.javiersantos.bottomdialogs.R.color.colorPrimary);
            }

            Drawable buttonBackground = UtilsLibrary.createButtonBackgroundDrawable(builder.context, builder.btn_colorPositiveBackground);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                vPositive.setBackground(buttonBackground);
            } else {
                // noinspection deprecation
                vPositive.setBackgroundDrawable(buttonBackground);
            }
        }

        if (builder.btn_negative != null) {
            vNegative.setVisibility(View.VISIBLE);
            vNegative.setText(builder.btn_negative);
            vNegative.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (builder.btn_negative_callback != null)
                        builder.btn_negative_callback.onClick(BottomDialog.this);
                    if (builder.isAutoDismiss)
                        bottomDialog.dismiss();
                }
            });

            if (builder.btn_colorNegative != 0) {
                vNegative.setTextColor(builder.btn_colorNegative);
            }
        }

        bottomDialog.setContentView(view);
        bottomDialog.setCancelable(builder.isCancelable);

        if (bottomDialog.getWindow() != null) {
            bottomDialog.getWindow().setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            bottomDialog.getWindow().setGravity(Gravity.BOTTOM);
        }

        return bottomDialog;
    }

    public static class Builder {
        Context context;

        // Bottom Dialog
        Dialog bottomDialog;

        // Icon, Title and Content
        Drawable icon;
        CharSequence title, content;

        // Content style
        int backgroundColor = Color.WHITE;
        int shadowHeight = DEFAULT_SHADOW_HEIGHT;

        // Buttons
        CharSequence btn_negative, btn_positive;
        ButtonCallback btn_negative_callback, btn_positive_callback;
        boolean isAutoDismiss;

        // Button text colors
        int btn_colorNegative, btn_colorPositive;

        // Button background colors
        int btn_colorPositiveBackground;

        // Custom View
        View customView;
        int customViewPaddingLeft, customViewPaddingTop, customViewPaddingRight, customViewPaddingBottom;

        // Other options
        boolean isCancelable;

        public Builder(@NonNull Context context) {
            this.context = context;
            this.isCancelable = true;
            this.isAutoDismiss = true;
        }

        public Builder setTitle(@StringRes int titleRes) {
            setTitle(this.context.getString(titleRes));
            return this;
        }

        public Builder setTitle(@NonNull CharSequence title) {
            this.title = title;
            return this;
        }

        public Builder setContent(@StringRes int contentRes) {
            setContent(this.context.getString(contentRes));
            return this;
        }

        public Builder setContent(@NonNull CharSequence content) {
            this.content = content;
            return this;
        }

        public Builder setIcon(@NonNull Drawable icon) {
            this.icon = icon;
            return this;
        }

        public Builder setIcon(@DrawableRes int iconRes) {
            this.icon = ResourcesCompat.getDrawable(context.getResources(), iconRes, null);
            return this;
        }

        public Builder setBackgroundColor(int colorRes) {
            this.backgroundColor = context.getResources().getColor(colorRes);
            return this;
        }

        public Builder setShadowHeight(int heightDp) {
            this.shadowHeight = heightDp;
            return this;
        }

        public Builder setPositiveBackgroundColorResource(@ColorRes int buttonColorRes) {
            this.btn_colorPositiveBackground = ResourcesCompat.getColor(context.getResources(), buttonColorRes, null);
            return this;
        }

        public Builder setPositiveBackgroundColor(int color) {
            this.btn_colorPositiveBackground = color;
            return this;
        }

        public Builder setPositiveTextColorResource(@ColorRes int textColorRes) {
            this.btn_colorPositive = ResourcesCompat.getColor(context.getResources(), textColorRes, null);
            return this;
        }

        public Builder setPositiveTextColor(int color) {
            this.btn_colorPositive = color;
            return this;
        }

        public Builder setPositiveText(@StringRes int buttonTextRes) {
            setPositiveText(this.context.getString(buttonTextRes));
            return this;
        }

        public Builder setPositiveText(@NonNull CharSequence buttonText) {
            this.btn_positive = buttonText;
            return this;
        }

        public Builder onPositive(@NonNull ButtonCallback buttonCallback) {
            this.btn_positive_callback = buttonCallback;
            return this;
        }

        public Builder setNegativeTextColorResource(@ColorRes int textColorRes) {
            this.btn_colorNegative = ResourcesCompat.getColor(context.getResources(), textColorRes, null);
            return this;
        }

        public Builder setNegativeTextColor(int color) {
            this.btn_colorNegative = color;
            return this;
        }

        public Builder setNegativeText(@StringRes int buttonTextRes) {
            setNegativeText(this.context.getString(buttonTextRes));
            return this;
        }

        public Builder setNegativeText(@NonNull CharSequence buttonText) {
            this.btn_negative = buttonText;
            return this;
        }

        public Builder onNegative(@NonNull ButtonCallback buttonCallback) {
            this.btn_negative_callback = buttonCallback;
            return this;
        }

        public Builder setCancelable(boolean cancelable) {
            this.isCancelable = cancelable;
            return this;
        }

        public Builder autoDismiss(boolean autodismiss) {
            this.isAutoDismiss = autodismiss;
            return this;
        }

        public Builder setCustomView(View customView) {
            this.customView = customView;
            this.customViewPaddingLeft = 0;
            this.customViewPaddingRight = 0;
            this.customViewPaddingTop = 0;
            this.customViewPaddingBottom = 0;
            return this;
        }

        public Builder setCustomView(View customView, int left, int top, int right, int bottom) {
            this.customView = customView;
            this.customViewPaddingLeft = UtilsLibrary.dpToPixels(context, left);
            this.customViewPaddingRight = UtilsLibrary.dpToPixels(context, right);
            this.customViewPaddingTop = UtilsLibrary.dpToPixels(context, top);
            this.customViewPaddingBottom = UtilsLibrary.dpToPixels(context, bottom);
            return this;
        }

        @UiThread
        public BottomDialog build() {
            return new BottomDialog(this);
        }

        @UiThread
        public BottomDialog show() {
            BottomDialog bottomDialog = build();
            bottomDialog.show();
            return bottomDialog;
        }

    }

    public interface ButtonCallback {
        void onClick(@NonNull BottomDialog dialog);
    }
}
