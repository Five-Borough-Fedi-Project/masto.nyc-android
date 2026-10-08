package org.joinmastodon.android.fork;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.widget.TextView;

import org.joinmastodon.android.R;

import androidx.annotation.Nullable;

/**
 * A TextView with a stroke around the glyphs, for text that sits over artwork.
 *
 * The welcome screen's name and byline are drawn over a painting that runs from pale sky to dark
 * towers, so no single text colour reads everywhere on it. A blurred shadow underneath was not
 * enough: it softens the background rather than separating the letters from it. A hard outline
 * does separate them, at any size and whatever is behind.
 *
 * The stroke is drawn by configuring the TextView's own paint and asking the layout to draw
 * itself, rather than by swapping the text colour around a call to super. TextView resets the
 * paint's colour from the text colour on every draw, so the colour has to be set on the paint
 * after that point; and setTextColor during a draw invalidates the view, which means drawing
 * again, forever.
 */
public class OutlinedTextView extends TextView{
	private float outlineWidth;
	private int outlineColor=0xFFFFFFFF;

	public OutlinedTextView(Context context){
		this(context, null);
	}

	public OutlinedTextView(Context context, @Nullable AttributeSet attrs){
		this(context, attrs, 0);
	}

	public OutlinedTextView(Context context, @Nullable AttributeSet attrs, int defStyleAttr){
		super(context, attrs, defStyleAttr);
		if(attrs!=null){
			TypedArray ta=context.obtainStyledAttributes(attrs, R.styleable.OutlinedTextView);
			outlineWidth=ta.getDimension(R.styleable.OutlinedTextView_outlineWidth, 0f);
			outlineColor=ta.getColor(R.styleable.OutlinedTextView_outlineColor, outlineColor);
			ta.recycle();
		}
	}

	@Override
	protected void onDraw(Canvas canvas){
		Layout layout=getLayout();
		if(outlineWidth>0 && layout!=null){
			TextPaint paint=getPaint();
			Paint.Style style=paint.getStyle();
			float width=paint.getStrokeWidth();
			Paint.Join join=paint.getStrokeJoin();
			int color=paint.getColor();

			// Doubled, because a stroke straddles the outline: half of it falls inside the glyph
			// and is painted over by the fill pass below.
			paint.setStyle(Paint.Style.STROKE);
			paint.setStrokeWidth(outlineWidth*2f);
			paint.setStrokeJoin(Paint.Join.ROUND);
			paint.setColor(outlineColor);
			canvas.save();
			canvas.translate(getTotalPaddingLeft(), getTotalPaddingTop());
			layout.draw(canvas);
			canvas.restore();

			paint.setStyle(style);
			paint.setStrokeWidth(width);
			paint.setStrokeJoin(join);
			paint.setColor(color);
		}
		super.onDraw(canvas);
	}
}
