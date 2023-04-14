package gxviewer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;


public class SettingTitleBar extends RelativeLayout {

    private Button m_titleBarLeftBtn;
    private Button m_titleBarRightBtn;
    private TextView m_titleBarText;


    /**
     * brief  setting title bar
     * param context    context for view
     * param attrs      attributes set
     */
    @SuppressLint("ResourceAsColor")
    public SettingTitleBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater.from(context).inflate(R.layout.setting_title_bar, this, true);
        m_titleBarLeftBtn = (Button) findViewById(R.id.setting_title_bar_left_btn);
        m_titleBarRightBtn = (Button) findViewById(R.id.setting_title_bar_right_btn);
        m_titleBarText = (TextView) findViewById(R.id.setting_title_bar_text);

        TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.SettingTitleBar);
        if(attributes != null) {
            // process title bar's color
            int titleBarBackGround = attributes.getResourceId(R.styleable.SettingTitleBar_title_background_color, Color.GREEN);
            setBackgroundResource(titleBarBackGround);

            // process left button
            // is left button visible
            boolean isLeftButtonVisible = attributes.getBoolean(R.styleable.SettingTitleBar_left_button_visible, true);
            if(isLeftButtonVisible) {
                m_titleBarLeftBtn.setVisibility(View.VISIBLE);
            } else {
                m_titleBarLeftBtn.setVisibility(View.INVISIBLE);
            }
            // set text of left button
            String leftButtonText = attributes.getString(R.styleable.SettingTitleBar_left_button_text);
            if(!TextUtils.isEmpty(leftButtonText)) {
                m_titleBarLeftBtn.setText(leftButtonText);
                // set color of left button's text
                int leftButtonTextColor = attributes.getResourceId(R.styleable.SettingTitleBar_left_button_text_color, Color.WHITE);
                m_titleBarLeftBtn.setTextColor(leftButtonTextColor);
            }
            // set a icon to left button
            int leftButtonDrawable = attributes.getResourceId(R.styleable.SettingTitleBar_left_button_drawable, R.drawable.ic_chevron_left_black_24dp);
            if(leftButtonDrawable != -1) {
                m_titleBarLeftBtn.setCompoundDrawablesWithIntrinsicBounds(leftButtonDrawable, 0, 0, 0);
                m_titleBarLeftBtn.setEnabled(true);
                m_titleBarLeftBtn.setClickable(true);
            }

            // process title
            // check if show a icon
            int titleTextDrawable = attributes.getResourceId(R.styleable.SettingTitleBar_title_text_drawable, -1);
            if(titleTextDrawable != -1) {
                m_titleBarText.setBackgroundResource(titleTextDrawable);
            } else {
                // if not icon then show text
                String titleText = attributes.getString(R.styleable.SettingTitleBar_title_text);
                if(!TextUtils.isEmpty(titleText)) {
                    m_titleBarText.setText(titleText);
                }
                // get text color
                int titleTextColor = attributes.getColor(R.styleable.SettingTitleBar_title_text_color, Color.WHITE);
                m_titleBarText.setTextColor(titleTextColor);
            }

            // process right button
            // is right button visible
            boolean isRightButtonVisible = attributes.getBoolean(R.styleable.SettingTitleBar_right_button_visible, true);
            if(isRightButtonVisible) {
                m_titleBarRightBtn.setVisibility(View.VISIBLE);
            } else {
                m_titleBarRightBtn.setVisibility(View.INVISIBLE);
            }
            // set text of right button
            String rightButtonText = attributes.getString(R.styleable.SettingTitleBar_right_button_text);
            if(!TextUtils.isEmpty(rightButtonText)) {
                m_titleBarRightBtn.setText(rightButtonText);
                // set color of right button's text
                int rightButtonTextColor = attributes.getResourceId(R.styleable.SettingTitleBar_right_button_text_color, Color.WHITE);
                m_titleBarRightBtn.setTextColor(rightButtonTextColor);
            }
            // set a icon to right button
            int rightButtonDrawable = attributes.getResourceId(R.styleable.SettingTitleBar_right_button_drawable, -1);
            if(rightButtonDrawable != -1) {
                m_titleBarRightBtn.setCompoundDrawablesWithIntrinsicBounds(rightButtonDrawable, 0, 0, 0);
            }

            attributes.recycle();

        }
    }

    public void setTitleClickListener(OnClickListener onClickListener) {
        if(onClickListener != null) {
            m_titleBarLeftBtn.setOnClickListener(onClickListener);
            m_titleBarRightBtn.setOnClickListener(onClickListener);
        }
    }
}
