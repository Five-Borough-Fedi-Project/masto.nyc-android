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
	private static final String KEY_OPEN_ON_NEIGHBORS="openOnNeighbors";

	private static SharedPreferences prefs(){
		return MastodonApp.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
	}

	/**
	 * Which tab the app opens on. The local timeline is the point of this fork, so Neighbors is
	 * the default; settings can change it.
	 */
	public static boolean opensOnNeighbors(){
		return prefs().getBoolean(KEY_OPEN_ON_NEIGHBORS, true);
	}

	public static void setOpensOnNeighbors(boolean neighbors){
		prefs().edit().putBoolean(KEY_OPEN_ON_NEIGHBORS, neighbors).apply();
	}
}
