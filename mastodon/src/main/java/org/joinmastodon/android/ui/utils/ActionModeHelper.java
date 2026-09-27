package org.joinmastodon.android.ui.utils;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
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
	private static void startColorAnim(FragmentRootLinearLayout rootLayout, int from, int to, Runnable onEnd){
		ObjectAnimator anim=ObjectAnimator.ofInt(rootLayout, "statusBarBackgroundColor", from, to);
		anim.setEvaluator(new IntEvaluator(){
			@Override
			public Integer evaluate(float fraction, Integer startValue, Integer endValue){
				return UiUtils.alphaBlendColors(startValue, endValue, fraction);
			}
		});
		if(onEnd!=null){
			anim.addListener(new AnimatorListenerAdapter(){
				@Override
				public void onAnimationEnd(Animator animation){
					onEnd.run();
				}
			});
		}
		anim.start();
	}

	public static ActionMode startActionMode(AppKitFragment fragment, ElevationOnScrollListener elevationOnScrollListener, ActionMode.Callback callback){
		FragmentStackActivity activity=(FragmentStackActivity) fragment.getActivity();
		// Tint the fragment's own status bar background rather than the window's status bar, which
		// is transparent and, from API 35, can't be colored at all. Scrolling animates the same
		// property, so it is suppressed for as long as action mode owns the color. Fragments that
		// pass no root layout (see InstanceCatalogSignupFragment) just don't get the tint.
		FragmentRootLinearLayout rootLayout=elevationOnScrollListener.getFragmentRootLayout();
		return activity.startActionMode(new ActionMode.Callback(){
			@Override
			public boolean onCreateActionMode(ActionMode mode, Menu menu){
				if(!callback.onCreateActionMode(mode, menu))
					return false;
				if(rootLayout!=null){
					elevationOnScrollListener.setStatusBarColorSuppressed(true);
					startColorAnim(rootLayout, rootLayout.getStatusBarBackgroundColor(), UiUtils.getThemeColor(activity, R.attr.colorM3Primary), null);
				}
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
				if(rootLayout!=null){
					// Back to whatever the current scroll position calls for, which may have changed
					// while action mode was up, then hand the color back to the scroll listener.
					startColorAnim(rootLayout, UiUtils.getThemeColor(activity, R.attr.colorM3Primary),
							elevationOnScrollListener.getCurrentStatusBarColor(),
							()->elevationOnScrollListener.setStatusBarColorSuppressed(false));
				}
				activity.invalidateSystemBarColors(fragment);
				callback.onDestroyActionMode(mode);
			}
		});
	}
}
