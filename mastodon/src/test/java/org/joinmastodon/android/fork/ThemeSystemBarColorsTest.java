package org.joinmastodon.android.fork;

import android.content.Context;
import android.content.res.TypedArray;
import android.view.ContextThemeWrapper;

import org.joinmastodon.android.R;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

/**
 * The system bars are transparent because the themes say so, since nothing calls
 * Window.setStatusBarColor any more, and fragments paint their own color behind the bars from the
 * appkit attributes. Both halves have to hold at once: a theme that sets an opaque
 * android:statusBarColor would put a solid bar back, and one that sets no appkit color would paint
 * the wrong thing behind it. See FORK.md, "Patched appkit".
 */
// Pinned to API 35, the version where the window stops honoring the bar colors, and the oldest
// one where all of this has to hold. Robolectric's Android 17 images don't run on JDK 21.
@Config(sdk=35)
@RunWith(RobolectricTestRunner.class)
public class ThemeSystemBarColorsTest{
	private static final int[] THEMES={
			R.style.Theme_Mastodon_Light,
			R.style.Theme_Mastodon_Dark,
			R.style.Theme_Mastodon_Light_MediumContrast,
			R.style.Theme_Mastodon_Dark_MediumContrast,
			R.style.Theme_Mastodon_Light_HighContrast,
			R.style.Theme_Mastodon_Dark_HighContrast,
			R.style.Theme_Mastodon_AutoLightDark,
	};

	@Test
	public void window_bars_are_transparent_in_every_theme(){
		for(int theme : THEMES){
			assertEquals(themeName(theme)+" sets an opaque android:statusBarColor. From API 35 the "+
					"window ignores it, so the fragment's own color would be hidden below that and "+
					"show above it.", 0, resolve(theme, android.R.attr.statusBarColor));
			assertEquals(themeName(theme)+" sets an opaque android:navigationBarColor.",
					0, resolve(theme, android.R.attr.navigationBarColor));
		}
	}

	@Test
	public void fragments_have_a_bar_color_to_paint_in_every_theme(){
		for(int theme : THEMES){
			assertNotEquals(themeName(theme)+" has no appkitStatusBarColor, so fragments would paint "+
					"transparent behind the status bar.", 0, resolve(theme, R.attr.appkitStatusBarColor));
			assertNotEquals(themeName(theme)+" has no appkitNavigationBarColor.",
					0, resolve(theme, R.attr.appkitNavigationBarColor));
		}
	}

	@Test
	public void qr_code_dialog_theme_has_transparent_bars(){
		int theme=R.style.Theme_Mastodon_Dialog_NoFrame_TransparentSystemBars;
		assertEquals("The QR code dialog sets its bar colors from this theme, since the window setters "+
				"are deprecated.", 0, resolve(theme, android.R.attr.statusBarColor));
		assertEquals(0, resolve(theme, android.R.attr.navigationBarColor));
		// Mirrors the platform's private Theme.DeviceDefault.Dialog.NoFrame, which sets no elevation.
		TypedArray ta=themed(theme).obtainStyledAttributes(new int[]{android.R.attr.windowElevation});
		assertEquals("windowElevation must be 0, as in the platform's NoFrame theme; the base dialog "+
				"theme it inherits from uses 16dp.", 0, ta.getDimensionPixelSize(0, -1));
		ta.recycle();
	}

	private int resolve(int theme, int attr){
		TypedArray ta=themed(theme).obtainStyledAttributes(new int[]{attr});
		int color=ta.getColor(0, 0);
		ta.recycle();
		return color;
	}

	private Context themed(int theme){
		return new ContextThemeWrapper(RuntimeEnvironment.getApplication(), theme);
	}

	private String themeName(int theme){
		return RuntimeEnvironment.getApplication().getResources().getResourceEntryName(theme);
	}
}
