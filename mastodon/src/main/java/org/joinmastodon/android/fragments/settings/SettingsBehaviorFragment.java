package org.joinmastodon.android.fragments.settings;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import org.joinmastodon.android.GlobalUserPreferences;
import org.joinmastodon.android.R;
import org.joinmastodon.android.model.viewmodel.CheckableListItem;
import org.joinmastodon.android.fork.ForkPrefs;
import org.joinmastodon.android.model.viewmodel.ListItem;
import org.joinmastodon.android.ui.M3AlertDialogBuilder;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class SettingsBehaviorFragment extends BaseSettingsFragment<Void>{
	private ListItem<Void> customTabsItem;
	// masto.nyc fork: which feed the home tab opens on
	private ListItem<Void> defaultFeedItem;
	private CheckableListItem<Void> altTextItem, playGifsItem, confirmUnfollowItem, confirmBoostItem, confirmDeleteItem;

	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		setTitle(R.string.settings_behavior);

		onDataLoaded(List.of(
				customTabsItem=new ListItem<>(R.string.settings_custom_tabs, GlobalUserPreferences.useCustomTabs ? R.string.in_app_browser : R.string.system_browser, R.drawable.ic_open_in_browser_24px, this::onCustomTabsClick),
				// masto.nyc fork: the home tab has two feeds, so which one it opens on is a choice
				defaultFeedItem=new ListItem<>(R.string.settings_default_feed, ForkPrefs.isDefaultFeedLocal() ? R.string.nyc_feed : R.string.your_follows, R.drawable.ic_home_24px, this::onDefaultFeedClick),
				altTextItem=new CheckableListItem<>(R.string.settings_alt_text_reminders, 0, CheckableListItem.Style.SWITCH, GlobalUserPreferences.altTextReminders, R.drawable.ic_alt_24px, this::toggleCheckableItem),
				playGifsItem=new CheckableListItem<>(R.string.settings_gif, 0, CheckableListItem.Style.SWITCH, GlobalUserPreferences.playGifs, R.drawable.ic_animation_24px, this::toggleCheckableItem),
				confirmUnfollowItem=new CheckableListItem<>(R.string.settings_confirm_unfollow, 0, CheckableListItem.Style.SWITCH, GlobalUserPreferences.confirmUnfollow, R.drawable.ic_person_remove_24px, this::toggleCheckableItem),
				confirmBoostItem=new CheckableListItem<>(R.string.settings_confirm_boost, 0, CheckableListItem.Style.SWITCH, GlobalUserPreferences.confirmBoost, R.drawable.ic_repeat_24px, this::toggleCheckableItem),
				confirmDeleteItem=new CheckableListItem<>(R.string.settings_confirm_delete_post, 0, CheckableListItem.Style.SWITCH, GlobalUserPreferences.confirmDeletePost, R.drawable.ic_delete_24px, this::toggleCheckableItem)
		));
	}

	@Override
	protected void doLoadData(int offset, int count){}

	// masto.nyc fork: see defaultFeedItem.
	private void onDefaultFeedClick(ListItem<?> item){
		new M3AlertDialogBuilder(getActivity())
				.setTitle(R.string.settings_default_feed)
				.setSingleChoiceItems(new String[]{getString(R.string.nyc_feed), getString(R.string.your_follows)},
						ForkPrefs.isDefaultFeedLocal() ? 0 : 1, (dlg, which)->{
							ForkPrefs.setDefaultFeedLocal(which==0);
							defaultFeedItem.subtitleRes=which==0 ? R.string.nyc_feed : R.string.your_follows;
							rebindItem(defaultFeedItem);
							dlg.dismiss();
						})
				.show();
	}

	private void onCustomTabsClick(ListItem<?> item){
		Intent intent=new Intent(Intent.ACTION_VIEW, Uri.parse("http://example.com"));
		ResolveInfo info=getActivity().getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
		final String browserName;
		if(info==null){
			browserName="??";
		}else{
			browserName=info.loadLabel(getActivity().getPackageManager()).toString();
		}
		ArrayAdapter<CharSequence> adapter=new ArrayAdapter<>(getActivity(), R.layout.item_alert_single_choice_2lines_but_different, R.id.text,
				new String[]{getString(R.string.in_app_browser), getString(R.string.system_browser)}){
			@Override
			public boolean hasStableIds(){
				return true;
			}

			@NonNull
			@Override
			public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent){
				View view=super.getView(position, convertView, parent);
				TextView subtitle=view.findViewById(R.id.subtitle);
				if(position==0){
					subtitle.setVisibility(View.GONE);
				}else{
					subtitle.setVisibility(View.VISIBLE);
					subtitle.setText(browserName);
				}
				return view;
			}
		};
		new M3AlertDialogBuilder(getActivity())
				.setTitle(R.string.settings_custom_tabs)
				.setSingleChoiceItems(adapter, GlobalUserPreferences.useCustomTabs ? 0 : 1, (dlg, which)->{
					GlobalUserPreferences.useCustomTabs=which==0;
					customTabsItem.subtitleRes=GlobalUserPreferences.useCustomTabs ? R.string.in_app_browser : R.string.system_browser;
					rebindItem(customTabsItem);
					dlg.dismiss();
				})
				.show();
	}

	@Override
	protected void onHidden(){
		super.onHidden();
		GlobalUserPreferences.playGifs=playGifsItem.checked;
		GlobalUserPreferences.altTextReminders=altTextItem.checked;
		GlobalUserPreferences.confirmUnfollow=confirmUnfollowItem.checked;
		GlobalUserPreferences.confirmBoost=confirmBoostItem.checked;
		GlobalUserPreferences.confirmDeletePost=confirmDeleteItem.checked;
		GlobalUserPreferences.save();
	}
}
