package org.joinmastodon.android.fork;

import android.content.Context;
import android.content.SharedPreferences;

import org.joinmastodon.android.MastodonApp;

/**
 * Preferences belonging to this fork, kept out of upstream's GlobalUserPreferences so that file
 * stays untouched and merges cleanly. Its own SharedPreferences file for the same reason.
 */
public class ForkPrefs{
	private static final String PREFS_NAME="fork";
	private static final String KEY_DEFAULT_FEED_IS_LOCAL="defaultFeedIsLocal";

	private static SharedPreferences prefs(){
		return MastodonApp.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
	}

	/**
	 * Which feed the home tab opens on. The NYC feed is the point of this fork, so it is the
	 * default; settings can change it.
	 */
	public static boolean isDefaultFeedLocal(){
		return prefs().getBoolean(KEY_DEFAULT_FEED_IS_LOCAL, true);
	}

	public static void setDefaultFeedLocal(boolean local){
		prefs().edit().putBoolean(KEY_DEFAULT_FEED_IS_LOCAL, local).apply();
	}
}
