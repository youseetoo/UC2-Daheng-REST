package gxviewer;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class WordWrapView extends ViewGroup {

    private int m_paddingHorizontal = 10; ///< child view horizontal padding
    private int m_paddingVertical   = 1;  ///< child view vertical padding
    private int m_marginHorizontal  = 5;  ///< child view horizontal margin
    private int m_marginVertical    = 2;  ///< child view vertical margin


    private int m_num = 0; ///< max number of character of per line

    public WordWrapView(Context context) {
        super(context);
    }

    public WordWrapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initAttrs(context,attrs);
    }

    public WordWrapView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initAttrs(context,attrs);
    }

    /**
     * brief    get attributes
     * param context    view's context
     * param attrs      Attribute Set
     */
    private void initAttrs(Context context, AttributeSet attrs) {

        TypedArray ta      = context.obtainStyledAttributes(attrs, R.styleable.WordWrapView);
        m_paddingHorizontal = (int) ta.getDimension(R.styleable.WordWrapView_padding_horizontal, m_paddingHorizontal);
        m_paddingVertical   = (int) ta.getDimension(R.styleable.WordWrapView_padding_vertical, m_paddingVertical);
        m_marginHorizontal  = (int) ta.getDimension(R.styleable.WordWrapView_margin_horizontal, m_marginHorizontal);
        m_marginVertical    = (int) ta.getDimension(R.styleable.WordWrapView_margin_vertical, m_marginVertical);
        ta.recycle();

    }


    /**
     * brief  assign a size and position to each of its children.
     * param changed    This is a new size or position for this view
     * param left       Left position, relative to parent
     * param top        top position, relative to parent
     * param right      right position, relative to parent
     * param bottom     bottom position, relative to parent
     */
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {

        int childCount = getChildCount(); // get child count
        int actualWith = right - left;    // get real width
        int x = 0;
        int y;
        int rows=1;

        // check total height
        for (int i = 0; i < childCount ; i++) {

            View view = getChildAt(i);

            int width  = view.getMeasuredWidth();
            int height = view.getMeasuredHeight();
            x += width + m_marginHorizontal;

            // when child view width is out of parent view, move it to next line
            if(x > actualWith - m_marginHorizontal){
                if(i != 0){
                    x = width + m_marginHorizontal;
                    rows++;
                }
            }

            // when child view width is still out of parent view, replace text tail with '...'
            if(x > actualWith - m_marginHorizontal){

                // check view height
                if(view instanceof TextView){
                    TextView tv= (TextView) view;
                    if(m_num == 0){
                        int wordNum = tv.getText().toString().length();
                        m_num = wordNum * (actualWith - 2 * m_marginHorizontal - 2 * m_paddingHorizontal) / (width - 2 * m_paddingHorizontal) - 1;
                    }
                    String text=tv.getText().toString();
                    text = text.substring(0, m_num) + "...";
                    tv.setText(text);
                }
                x = actualWith - m_marginHorizontal;
                width = actualWith - 2 * m_marginHorizontal;
            }


            y = rows * (height + m_marginVertical);
            view.layout(x - width, y - height, x, y);
        }
    }
    
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int x = 0;         ///> horizontal ordinate
        int y = 0;         ///> vertical ordinate
        int rows = 1;      ///> total line number

        int actualWidth = MeasureSpec.getSize(widthMeasureSpec);    ///> actual width

        int childCount = getChildCount();

        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            child.setPadding(m_paddingHorizontal, m_paddingVertical, m_paddingHorizontal, m_paddingVertical);
            child.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
            int width = child.getMeasuredWidth();
            int height = child.getMeasuredHeight();
            x += width + m_marginHorizontal;
            if(x > actualWidth - m_marginHorizontal){// change row
                if(i != 0){
                    x = width + m_marginHorizontal;
                    rows++;
                }
            }
            y = rows * (height + m_marginVertical);
        }
        setMeasuredDimension(actualWidth, y + m_marginVertical);
    }
}
