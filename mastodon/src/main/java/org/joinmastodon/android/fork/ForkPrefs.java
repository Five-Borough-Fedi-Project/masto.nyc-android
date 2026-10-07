package org.joinmastodon.android.fork;

import android.content.Context;
import android.content.SharedPreferences;

import org.joinmastodon.android.MastodonApp;

import java.util.List;

/**
 * Preferences belonging to this fork, kept out of upstream's GlobalUserPreferences so that file
 * stays untouched and merges cleanly. Its own SharedPreferences file for the same reason.
 */
public class ForkPrefs{
	private static final String PREFS_NAME="fork";
	private static final String KEY_OPEN_ON_NEIGHBORS="openOnNeighbors";
	private static final String KEY_FOLLOWED_HASHTAGS="followedHashtags:";

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

	/**
	 * The hashtags you follow, cached per account so the section on the search screen has
	 * something to show straight away. The server call behind it is slow enough to watch.
	 */
	public static List<String> cachedFollowedHashtags(String accountID){
		String raw=prefs().getString(KEY_FOLLOWED_HASHTAGS+accountID, null);
		if(raw==null || raw.isEmpty())
			return List.of();
		return List.of(raw.split("\n"));
	}

	public static void setCachedFollowedHashtags(String accountID, List<String> names){
		prefs().edit().putString(KEY_FOLLOWED_HASHTAGS+accountID, String.join("\n", names)).apply();
	}
}
