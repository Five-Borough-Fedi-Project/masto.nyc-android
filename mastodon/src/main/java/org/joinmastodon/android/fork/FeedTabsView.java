package org.joinmastodon.android.fork;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.joinmastodon.android.R;
import org.joinmastodon.android.ui.utils.UiUtils;

import java.util.function.Consumer;

import androidx.annotation.NonNull;
import me.grishka.appkit.utils.V;

/**
 * The two feed tabs in the home toolbar, in place of upstream's dropdown. People switch between
 * the two timelines constantly, and a dropdown makes that two taps and a menu; these are one tap
 * and always show which feed you are on.
 *
 * Lists and followed hashtags were in that dropdown too, and live on the search screen now.
 */
public class FeedTabsView extends LinearLayout{
	private final TextView localTab, followingTab;
	private Consumer<Boolean> onTabSelected;
	private boolean localSelected;

	public FeedTabsView(Context context){
		super(context);
		setOrientation(HORIZONTAL);
		setGravity(Gravity.CENTER_VERTICAL);
		localTab=addTab(R.string.nyc_feed, true);
		followingTab=addTab(R.string.your_follows, false);
		setLocalSelected(true);
	}

	private TextView addTab(int titleRes, boolean local){
		TextView tab=new TextView(getContext());
		tab.setTextAppearance(R.style.m3_label_large);
		tab.setText(titleRes);
		tab.setSingleLine();
		tab.setEllipsize(TextUtils.TruncateAt.END);
		tab.setGravity(Gravity.CENTER);
		tab.setPaddingRelative(V.dp(16), 0, V.dp(16), 0);
		tab.setOnClickListener(v->{
			if(localSelected==local)
				return;
			setLocalSelected(local);
			if(onTabSelected!=null)
				onTabSelected.accept(local);
		});
		tab.setAccessibilityDelegate(new AccessibilityDelegate(){
			@Override
			public void onInitializeAccessibilityNodeInfo(@NonNull View host, @NonNull AccessibilityNodeInfo info){
				super.onInitializeAccessibilityNodeInfo(host, info);
				// Announced as a tab rather than a button, and selection is read out.
				info.setClassName("android.widget.Button");
				info.setSelected(localSelected==local);
			}
		});
		LayoutParams lp=new LayoutParams(LayoutParams.WRAP_CONTENT, V.dp(36));
		lp.setMarginEnd(V.dp(8));
		addView(tab, lp);
		return tab;
	}

	public void setOnTabSelected(Consumer<Boolean> listener){
		onTabSelected=listener;
	}

	/** True selects the NYC feed, false selects your follows. */
	public void setLocalSelected(boolean local){
		localSelected=local;
		style(localTab, local);
		style(followingTab, !local);
	}

	private void style(TextView tab, boolean selected){
		int brand=UiUtils.getThemeColor(getContext(), R.attr.colorBrand);
		GradientDrawable pill=new GradientDrawable();
		pill.setCornerRadius(V.dp(18));
		pill.setColor(selected ? brand : 0);
		if(!selected){
			pill.setStroke(V.dp(1), UiUtils.alphaBlendColors(
					UiUtils.getThemeColor(getContext(), R.attr.colorM3Surface),
					UiUtils.getThemeColor(getContext(), R.attr.colorM3OnSurfaceVariant), 0.35f));
		}
		tab.setBackground(new RippleDrawable(ColorStateList.valueOf(
				UiUtils.getThemeColor(getContext(), R.attr.colorM3OnSurfaceVariant) & 0x40ffffff), pill, null));
		tab.setTextColor(selected ? 0xFFFFFFFF : UiUtils.getThemeColor(getContext(), R.attr.colorM3OnSurfaceVariant));
		tab.setSelected(selected);
	}
}
