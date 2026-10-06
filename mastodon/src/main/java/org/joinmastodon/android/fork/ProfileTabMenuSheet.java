package org.joinmastodon.android.fork;

import android.app.Activity;
import android.os.Bundle;

import org.joinmastodon.android.MainActivity;
import org.joinmastodon.android.R;
import org.joinmastodon.android.api.session.AccountSession;
import org.joinmastodon.android.api.session.AccountSessionManager;
import org.joinmastodon.android.fragments.settings.SettingsMainFragment;
import org.joinmastodon.android.model.viewmodel.ListItem;
import org.joinmastodon.android.ui.M3AlertDialogBuilder;
import org.joinmastodon.android.ui.sheets.ListItemsSheet;

import me.grishka.appkit.Nav;

/**
 * The menu behind a long press on the profile tab. Upstream opens the account switcher there;
 * this fork has one account per install, so the space is used for the things that have nowhere
 * else to live: settings, which is no longer in the home toolbar, and logging out.
 */
public class ProfileTabMenuSheet{
	public static void show(Activity activity, String accountID){
		ListItemsSheet sheet=new ListItemsSheet(activity);
		sheet.add(new ListItem<>(R.string.settings, 0, R.drawable.ic_settings_24px, item->{
			sheet.dismiss();
			Bundle args=new Bundle();
			args.putString("account", accountID);
			Nav.go(activity, SettingsMainFragment.class, args);
		}));
		sheet.add(new ListItem<>(R.string.log_out, 0, R.drawable.ic_logout_24px, item->{
			sheet.dismiss();
			confirmLogOut(activity, accountID);
		}));
		sheet.show();
	}

	private static void confirmLogOut(Activity activity, String accountID){
		AccountSession session=AccountSessionManager.getInstance().getAccount(accountID);
		new M3AlertDialogBuilder(activity)
				.setMessage(activity.getString(R.string.confirm_log_out, session.getFullUsername()))
				.setPositiveButton(R.string.log_out, (dialog, which)->logOut(activity, accountID))
				.setNegativeButton(R.string.cancel, null)
				.show();
	}

	private static void logOut(Activity activity, String accountID){
		AccountSessionManager.get(accountID).logOut(activity, ()->{
			// Same as the account switcher did: the session is gone, so the home fragment has to be
			// rebuilt, which lands on the splash screen when it was the only account.
			((MainActivity) activity).restartHomeFragment();
		});
	}
}
