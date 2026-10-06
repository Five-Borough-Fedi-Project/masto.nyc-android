package org.joinmastodon.android.fork;

import android.content.Context;

import org.joinmastodon.android.R;
import org.joinmastodon.android.ui.tabs.TabLayout;
import org.joinmastodon.android.ui.utils.UiUtils;

import java.util.function.Consumer;

import me.grishka.appkit.utils.V;

/**
 * The two feed tabs in the home toolbar, in place of upstream's dropdown. People switch between
 * the two timelines constantly, and a dropdown makes that a menu trip; these are one tap and
 * always show which feed you are on.
 *
 * Built on the same TabLayout the search screen uses, with the same indicator drawable, so they
 * read as tabs and match the rest of the app. The brand purple marks the selected one.
 *
 * Lists and followed hashtags were in that dropdown too, and live on the search screen now.
 */
public class FeedTabsView extends TabLayout{
	private Consumer<Boolean> onTabSelected;
	private boolean settingSelection;

	public FeedTabsView(Context context){
		super(context);
		int brand=UiUtils.getThemeColor(context, R.attr.colorBrand);
		setTabMode(MODE_FIXED);
		setTabGravity(GRAVITY_FILL);
		setTabIndicatorFullWidth(false);
		setSelectedTabIndicator(R.drawable.tab_indicator_m3);
		setSelectedTabIndicatorColor(brand);
		setSelectedTabIndicatorHeight(V.dp(3));
		setTabIndicatorAnimationMode(INDICATOR_ANIMATION_MODE_ELASTIC);
		setTabTextColors(UiUtils.getThemeColor(context, R.attr.colorM3OnSurfaceVariant), brand);
		setTabRippleColor(null);
		setTabTextSize(V.dp(15));

		addTab(newTab().setText(R.string.nyc_feed));
		addTab(newTab().setText(R.string.your_follows));

		addOnTabSelectedListener(new OnTabSelectedListener(){
			@Override
			public void onTabSelected(Tab tab){
				// Also fires when the fragment sets the selection itself, which would loop back into
				// a reload.
				if(settingSelection || FeedTabsView.this.onTabSelected==null)
					return;
				FeedTabsView.this.onTabSelected.accept(tab.getPosition()==0);
			}

			@Override
			public void onTabUnselected(Tab tab){}

			@Override
			public void onTabReselected(Tab tab){}
		});
	}

	public void setOnTabSelected(Consumer<Boolean> listener){
		onTabSelected=listener;
	}

	/** True selects the NYC feed, false selects your follows. */
	public void setLocalSelected(boolean local){
		Tab tab=getTabAt(local ? 0 : 1);
		if(tab==null || tab.isSelected())
			return;
		settingSelection=true;
		tab.select();
		settingSelection=false;
	}
}
