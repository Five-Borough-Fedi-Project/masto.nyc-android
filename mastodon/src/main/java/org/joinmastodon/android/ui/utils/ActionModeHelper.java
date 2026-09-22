package org.joinmastodon.android.ui.utils;

import android.animation.Animator;
import android.animation.IntEvaluator;
import android.animation.ObjectAnimator;
import android.graphics.drawable.Drawable;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import org.joinmastodon.android.R;
import org.joinmastodon.android.utils.ElevationOnScrollListener;

import me.grishka.appkit.FragmentStackActivity;
import me.grishka.appkit.fragments.AppKitFragment;
import me.grishka.appkit.views.FragmentRootLinearLayout;

public class ActionModeHelper{
	public static ActionMode startActionMode(AppKitFragment fragment, ElevationOnScrollListener elevationOnScrollListener, ActionMode.Callback callback){
		FragmentStackActivity activity=(FragmentStackActivity) fragment.getActivity();
		// Tint the fragment's own status bar background rather than the window's status bar, which
		// is transparent and, from API 35, can't be colored at all.
		FragmentRootLinearLayout rootLayout=elevationOnScrollListener.getFragmentRootLayout();
		int statusBarColorBeforeActionMode=rootLayout.getStatusBarBackgroundColor();
		return activity.startActionMode(new ActionMode.Callback(){
			@Override
			public boolean onCreateActionMode(ActionMode mode, Menu menu){
				if(!callback.onCreateActionMode(mode, menu))
					return false;
				ObjectAnimator anim=ObjectAnimator.ofInt(rootLayout, "statusBarBackgroundColor", statusBarColorBeforeActionMode, UiUtils.getThemeColor(activity, R.attr.colorM3Primary));
				anim.setEvaluator(new IntEvaluator(){
					@Override
					public Integer evaluate(float fraction, Integer startValue, Integer endValue){
						return UiUtils.alphaBlendColors(startValue, endValue, fraction);
					}
				});
				anim.start();
				activity.invalidateSystemBarColors(fragment);
				View fakeView=new View(activity);
//				mode.setCustomView(fakeView);
//				int buttonID=activity.getResources().getIdentifier("action_mode_close_button", "id", "android");
//				View btn=activity.getWindow().getDecorView().findViewById(buttonID);
//				if(btn!=null){
//					((ViewGroup.MarginLayoutParams)btn.getLayoutParams()).setMarginEnd(0);
//				}
				return true;
			}

			@Override
			public boolean onPrepareActionMode(ActionMode mode, Menu menu){
				if(!callback.onPrepareActionMode(mode, menu))
					return false;
				for(int i=0;i<menu.size();i++){
					Drawable icon=menu.getItem(i).getIcon();
					if(icon!=null){
						icon=icon.mutate();
						icon.setTint(UiUtils.getThemeColor(activity, R.attr.colorM3OnPrimary));
						menu.getItem(i).setIcon(icon);
					}
				}
				return true;
			}

			@Override
			public boolean onActionItemClicked(ActionMode mode, MenuItem item){
				return callback.onActionItemClicked(mode, item);
			}

			@Override
			public void onDestroyActionMode(ActionMode mode){
				ObjectAnimator anim=ObjectAnimator.ofInt(rootLayout, "statusBarBackgroundColor", UiUtils.getThemeColor(activity, R.attr.colorM3Primary), statusBarColorBeforeActionMode);
				anim.setEvaluator(new IntEvaluator(){
					@Override
					public Integer evaluate(float fraction, Integer startValue, Integer endValue){
						return UiUtils.alphaBlendColors(startValue, endValue, fraction);
					}
				});
				anim.start();
				activity.invalidateSystemBarColors(fragment);
				callback.onDestroyActionMode(mode);
			}
		});
	}
}
