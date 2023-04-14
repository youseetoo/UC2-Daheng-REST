package gxviewer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Rect;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;


public class SeekBarValue extends RelativeLayout {

    private SeekBar m_seekBar;                ///< seek bar obj
    private TextView m_textView;              ///< text view obj
    private double m_max = 100;               ///< max value of range
    private double m_min = 0;                 ///< max value of range
    private int m_precision = 10;             ///< precision of progress
    private final int m_padding = 40;         ///< left and right padding of seek bar
    private String m_strPrecisionFormat = ""; ///< format number precision display

    private int m_defaultValue = 0;           ///< default value of seek bar
    private double m_currentValue = 0;        ///< record current value

    /**
     * brief  seek bar with value in text
     * param context[in]
     * param attrs[in]
     */
    @SuppressLint("ResourceAsColor")
    public SeekBarValue(Context context, AttributeSet attrs) {
        super(context, attrs);

        m_seekBar = new SeekBar(context.getApplicationContext());
        m_textView = new TextView(context.getApplicationContext());

        // setup layout param
        this.setBackgroundColor(Color.WHITE);

        // setup seek bar layout param
        RelativeLayout.LayoutParams seekBarLayoutParams = new RelativeLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
        );
        seekBarLayoutParams.addRule(RelativeLayout.CENTER_VERTICAL);
        m_seekBar.setLayoutParams(seekBarLayoutParams);

        // setup text view layout param
        RelativeLayout.LayoutParams textViewLayoutParams = new RelativeLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
        );
        textViewLayoutParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        textViewLayoutParams.addRule(RelativeLayout.CENTER_VERTICAL);
        textViewLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        m_textView.setLayoutParams(textViewLayoutParams);

        // add view to seek bar
        this.addView(m_seekBar);
        this.addView(m_textView);

        // calculate text rect, set it 6 char width
        String text = String.format("%06d", m_defaultValue);
        Rect bounds = new Rect();
        TextPaint paint = m_textView.getPaint();
        paint.getTextBounds(text, 0, text.length(), bounds);
        // adjust seek bar size
        m_seekBar.setPadding(m_padding, 0, bounds.width() + m_textView.getPaddingRight() + m_padding,0);

        // init seek bar
        TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.SeekBarValue);
        if(attributes != null) {
            // init max and min value
            m_max = attributes.getFloat(R.styleable.SeekBarValue_max_value, (float) m_max);
            m_min = attributes.getFloat(R.styleable.SeekBarValue_min_value, (float) m_min);
            setRange(m_min, m_max);

            // init precision
            m_precision = attributes.getInt(R.styleable.SeekBarValue_precision, m_precision);
            setPrecision(m_precision);

            // init default value
            setDefaultValue(attributes.getFloat(R.styleable.SeekBarValue_value, m_defaultValue));
            m_currentValue = m_defaultValue;

            // init enable status
            setEnabled(attributes.getBoolean(R.styleable.SeekBarValue_enabled, true));
        }

        // register seek bar value changed event
        m_seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar m_seekBar, int progress, boolean fromUser) {
                m_currentValue = (progress + (m_min * m_precision)) / m_precision;
                m_textView.setText(String.format(m_strPrecisionFormat, m_currentValue));
                if(onValueChangedListener != null) {
                    onValueChangedListener.onSeekBarValueChanged(m_currentValue);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar m_seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar m_seekBar) {
            }
        });
    }

    public double getValue() {
        return m_currentValue;
    }
    // setup value changed event
    public interface OnValueChangedListener {
        public void onSeekBarValueChanged(double value);
    }

    OnValueChangedListener onValueChangedListener = null;

    public OnValueChangedListener getOnValueChangedListener() {
        return onValueChangedListener;
    }

    public void setOnValueChangedListener(OnValueChangedListener onValueChangedListener) {
        this.onValueChangedListener = onValueChangedListener;
    }

    /**
     * brief  set range of seek bar
     * param min[in]
     * param max[in]
     */
    public void setRange(double min, double max) {

        m_max = max;
        m_min = min;

        m_seekBar.setMax((int)(m_max * (double) m_precision - m_min * (double) m_precision));
    }

    /**
     * brief  set precision, e.g. pre = 10 for one decimal digit
     * param pre[in]  precision of value
     */

    public void setPrecision(int pre) {
        m_precision = pre;
        if(Integer.bitCount(pre) == 1) {
            m_strPrecisionFormat = "%.0f";
        } else {
            m_strPrecisionFormat = String.format("%%.%df", Integer.bitCount(pre) - 1);
        }
        m_seekBar.setMax((int)((m_max - m_min) * m_precision));
        setRange(m_min, m_max);
    }

    /**
     * brief  whether enable seek bar
     * param enable[in]  true to enable, false to disable
     */
    public void setEnabled(boolean enable) {
        m_seekBar.setEnabled(enable);
    }

    /**
     * brief  set default value
     * param v[in]  default value
     */
    public void setDefaultValue(double v) {

        m_currentValue = v;

        v = (v >= m_max) ? m_max : v;
        v = (v <= m_min) ? m_min : v;
        int nDefault = (int)((v - m_min) * m_precision);

        m_seekBar.setProgress(nDefault);
        m_textView.setText(String.format(m_strPrecisionFormat, v));
    }
}
