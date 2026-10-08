package org.joinmastodon.android.fragments.settings;

import android.content.Context;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Build;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Toast;
import org.joinmastodon.android.MainActivity;
import org.joinmastodon.android.api.MastodonAPIController;
import org.joinmastodon.android.ui.M3AlertDialogBuilder;
import org.joinmastodon.android.ui.Snackbar;
import me.grishka.appkit.imageloader.ImageCache;
import me.grishka.appkit.utils.SingleViewRecyclerAdapter;
import me.grishka.appkit.imageloader.disklrucache.DiskLruCache;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.otto.Subscribe;

import org.joinmastodon.android.BuildConfig;
import org.joinmastodon.android.E;
import org.joinmastodon.android.GlobalUserPreferences;
import org.joinmastodon.android.R;
import org.joinmastodon.android.api.session.AccountSession;
import org.joinmastodon.android.api.session.AccountSessionManager;
import org.joinmastodon.android.events.AccountLoggedOutEvent;
import org.joinmastodon.android.events.SelfUpdateStateChangedEvent;
import org.joinmastodon.android.fragments.SplashFragment;
import org.joinmastodon.android.model.viewmodel.ListItem;
import org.joinmastodon.android.model.viewmodel.SectionHeaderListItem;
import org.joinmastodon.android.model.viewmodel.SettingsAccountListItem;
import org.joinmastodon.android.ui.utils.HideableSingleViewRecyclerAdapter;
import org.joinmastodon.android.ui.utils.UiUtils;
import org.joinmastodon.android.updater.GithubSelfUpdater;

import java.util.ArrayList;
import java.util.List;

import androidx.recyclerview.widget.RecyclerView;
import me.grishka.appkit.Nav;
import me.grishka.appkit.imageloader.requests.ImageLoaderRequest;
import me.grishka.appkit.imageloader.requests.UrlImageLoaderRequest;
import me.grishka.appkit.utils.MergeRecyclerAdapter;
import me.grishka.appkit.utils.V;

public class SettingsMainFragment extends BaseSettingsFragment<Object>{

	private HideableSingleViewRecyclerAdapter bannerAdapter;
	private Button updateButton1, updateButton2;
	private TextView updateText;
	private ArrayList<ListItem<?>> items=new ArrayList<>();
	// masto.nyc fork: moved here from SettingsAboutAppFragment along with the rest of that screen.
	private ListItem<Object> mediaCacheItem;
	private Runnable updateDownloadProgressUpdater=new Runnable(){
		@Override
		public void run(){
			GithubSelfUpdater.UpdateState state=GithubSelfUpdater.getInstance().getState();
			if(state==GithubSelfUpdater.UpdateState.DOWNLOADING){
				updateButton1.setText(getString(R.string.downloading_update, Math.round(GithubSelfUpdater.getInstance().getDownloadProgress()*100f)));
				list.postDelayed(this, 250);
			}
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		setTitle(R.string.settings);
		// masto.nyc fork: one account per install, so there is no account list and no screen
		// behind an account row. Everything that lived on SettingsAccountFragment and
		// SettingsAboutAppFragment is here, in one page, under the username.
		AccountSession self=AccountSessionManager.get(accountID);
		items.add(new SectionHeaderListItem(self.getFullUsername()));
		items.addAll(List.of(
				new SectionHeaderListItem(R.string.account_settings),
				new ListItem<>(R.string.settings_privacy, 0, R.drawable.ic_privacy_tip_24px, this::onPrivacyClick),
				new ListItem<>(R.string.settings_filters, 0, R.drawable.ic_filter_alt_24px, this::onFiltersClick),
				new ListItem<>(R.string.settings_notifications, 0, R.drawable.ic_notifications_24px, this::onNotificationsClick),
				new ListItem<>(R.string.settings_posting_defaults, 0, R.drawable.ic_edit_square_24px, this::onPostingDefaultsClick),
				// The rest of the account's settings only exist on the web.
				new ListItem<>(R.string.settings_even_more, 0, R.drawable.ic_open_in_browser_24px,
						i->UiUtils.launchWebBrowser(getActivity(), "https://"+self.domain+"/auth/edit")),

				new SectionHeaderListItem(R.string.settings_app_settings),
				new ListItem<>(R.string.settings_behavior, 0, R.drawable.ic_tune_24px, this::onBehaviorClick),
				new ListItem<>(R.string.settings_display, 0, R.drawable.ic_style_24px, this::onDisplayClick)
		));
		items.add(mediaCacheItem=new ListItem<>(R.string.settings_clear_cache, 0, R.drawable.ic_delete_24px, this::onClearMediaCacheClick));

		items.addAll(List.of(
				new SectionHeaderListItem(R.string.settings_about),
				new ListItem<>(getString(R.string.settings_about_this_server), getString(R.string.settings_server_explanation), R.drawable.ic_dns_24px, this::onServerClick, null),
				new ListItem<>(getString(R.string.settings_donate_5bfp), getString(R.string.settings_donate_5bfp_explanation), R.drawable.ic_volunteer_activism_24px,
						i->UiUtils.launchWebBrowser(getActivity(), getString(R.string.fork_donate_url)), null),
				new ListItem<>(R.string.settings_fork_github, 0, R.drawable.ic_open_in_browser_24px,
						i->UiUtils.launchWebBrowser(getActivity(), getString(R.string.fork_github_org_url)))
		));

		items.addAll(List.of(
				new SectionHeaderListItem(R.string.manage_account),
				// No "switch to this account": there is never another one to switch to.
				new ListItem<Object>(R.string.delete_account, 0, R.drawable.ic_delete_forever_24px,
						i->UiUtils.launchWebBrowser(getActivity(), "https://"+self.domain+"/settings/delete"), R.attr.colorM3Error, false),
				new ListItem<Object>(R.string.log_out, 0, R.drawable.ic_logout_24px, this::onLogOutClick, R.attr.colorM3Error, false)
		));

		// The small print, at the bottom, where it belongs. The version label goes below even
		// this, as a footer on the adapter; see getAdapter().
		items.addAll(List.of(
				new ListItem<>(R.string.settings_tos, 0, i->UiUtils.launchWebBrowser(getActivity(), "https://"+self.domain+"/terms")),
				new ListItem<>(R.string.settings_privacy_policy, 0, i->UiUtils.launchWebBrowser(getActivity(), getString(R.string.privacy_policy_url)))
		));
		if(BuildConfig.DEBUG || GlobalUserPreferences.showDebugSettings){
			items.add(new ListItem<>("Debug settings", null, R.drawable.ic_bug_report_24px, i->Nav.go(getActivity(), SettingsDebugFragment.class, makeFragmentArgs()), null));
		}

		//noinspection unchecked
		onDataLoaded((List<ListItem<Object>>)(Object)items);

		AccountSession session=AccountSessionManager.get(accountID);
		session.reloadPreferences(null);
		session.updateAccountInfo();
		E.register(this);
	}

	@Override
	public void onDestroy(){
		super.onDestroy();
		E.unregister(this);
	}

	@Override
	protected void doLoadData(int offset, int count){}

	@Override
	protected RecyclerView.Adapter<?> getAdapter(){
		View banner=getActivity().getLayoutInflater().inflate(R.layout.item_settings_banner, list, false);
		updateText=banner.findViewById(R.id.text);
		TextView bannerTitle=banner.findViewById(R.id.title);
		ImageView bannerIcon=banner.findViewById(R.id.icon);
		updateButton1=banner.findViewById(R.id.button);
		updateButton2=banner.findViewById(R.id.button2);
		bannerAdapter=new HideableSingleViewRecyclerAdapter(banner);
		bannerAdapter.setVisible(false);
		updateButton1.setOnClickListener(this::onUpdateButtonClick);
		updateButton2.setOnClickListener(this::onUpdateButtonClick);

		bannerTitle.setText(R.string.app_update_ready);
		bannerIcon.setImageResource(R.drawable.ic_apk_install_24px);

		MergeRecyclerAdapter adapter=new MergeRecyclerAdapter();
		adapter.addAdapter(bannerAdapter);
		adapter.addAdapter(super.getAdapter());
		// masto.nyc fork: the version label, from SettingsAboutAppFragment. Last thing on the
		// page, below even the terms and the privacy policy.
		adapter.addAdapter(new SingleViewRecyclerAdapter(makeVersionLabel()));
		return adapter;
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState){
		super.onViewCreated(view, savedInstanceState);
		if(GithubSelfUpdater.needSelfUpdating()){
			updateUpdateBanner();
		}
		// masto.nyc fork: needs the list, so not in onCreate with the rest of the items.
		updateMediaCacheItem();
	}

	/** masto.nyc fork: lifted from SettingsAboutAppFragment, tap to copy and all. */
	private TextView makeVersionLabel(){
		TextView versionInfo=new TextView(getActivity());
		versionInfo.setSingleLine();
		versionInfo.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, V.dp(32)));
		versionInfo.setTextAppearance(R.style.m3_label_medium);
		versionInfo.setTextColor(UiUtils.getThemeColor(getActivity(), R.attr.colorM3Outline));
		versionInfo.setGravity(Gravity.CENTER);
		versionInfo.setText(getString(R.string.settings_app_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE));
		versionInfo.setOnClickListener(v->{
			getActivity().getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("", BuildConfig.VERSION_NAME+" ("+BuildConfig.VERSION_CODE+")"));
			if(Build.VERSION.SDK_INT<=Build.VERSION_CODES.S_V2){
				new Snackbar.Builder(getActivity()).setText(R.string.app_version_copied).show();
			}
		});
		if("beta".equals(BuildConfig.BUILD_TYPE)){
			versionInfo.setOnLongClickListener(v->{
				GlobalUserPreferences.showDebugSettings=true;
				GlobalUserPreferences.save();
				Toast.makeText(getActivity(), "Debug settings unlocked", Toast.LENGTH_SHORT).show();
				return true;
			});
		}
		return versionInfo;
	}

	private Bundle makeFragmentArgs(){
		Bundle args=new Bundle();
		args.putString("account", accountID);
		return args;
	}

	// masto.nyc fork: from SettingsAccountFragment, which no longer has a way in.
	private void onPrivacyClick(ListItem<?> item_){
		Nav.go(getActivity(), SettingsPrivacyFragment.class, makeFragmentArgs());
	}

	private void onFiltersClick(ListItem<?> item_){
		Nav.go(getActivity(), SettingsFiltersFragment.class, makeFragmentArgs());
	}

	private void onNotificationsClick(ListItem<?> item_){
		Nav.go(getActivity(), SettingsNotificationsFragment.class, makeFragmentArgs());
	}

	private void onPostingDefaultsClick(ListItem<?> item_){
		Nav.go(getActivity(), SettingsPostingDefaultsFragment.class, makeFragmentArgs());
	}

	private void onServerClick(ListItem<?> item_){
		Nav.go(getActivity(), SettingsServerFragment.class, makeFragmentArgs());
	}

	private void onLogOutClick(ListItem<?> item_){
		AccountSession session=AccountSessionManager.get(accountID);
		new M3AlertDialogBuilder(getActivity())
				.setMessage(getString(R.string.confirm_log_out, session.getFullUsername()))
				.setPositiveButton(R.string.log_out, (dialog, which)->AccountSessionManager.get(accountID)
						.logOut(getActivity(), ()->((MainActivity)getActivity()).restartHomeFragment()))
				.setNegativeButton(R.string.cancel, null)
				.show();
	}

	// masto.nyc fork: from SettingsAboutAppFragment, likewise.
	private void onClearMediaCacheClick(ListItem<?> item){
		MastodonAPIController.runInBackground(()->{
			Activity activity=getActivity();
			ImageCache.getInstance(activity).clear();
			activity.runOnUiThread(()->{
				Toast.makeText(activity, R.string.media_cache_cleared, Toast.LENGTH_SHORT).show();
				updateMediaCacheItem();
			});
		});
	}

	private void updateMediaCacheItem(){
		DiskLruCache cache=ImageCache.getInstance(getActivity()).getDiskCache();
		long size=cache==null ? 0 : cache.size();
		mediaCacheItem.subtitle=UiUtils.formatFileSize(getActivity(), size, false);
		mediaCacheItem.isEnabled=size>0;
		rebindItem(mediaCacheItem);
	}

	private void onBehaviorClick(ListItem<?> item_){
		Nav.go(getActivity(), SettingsBehaviorFragment.class, makeFragmentArgs());
	}

	private void onDisplayClick(ListItem<?> item_){
		Nav.go(getActivity(), SettingsDisplayFragment.class, makeFragmentArgs());
	}

	private boolean useStagingEnvironmentForDonations(){
		return (BuildConfig.DEBUG || BuildConfig.BUILD_TYPE.equals("appcenterPrivateBeta")) && getActivity().getSharedPreferences("debug", Context.MODE_PRIVATE).getBoolean("donationsStaging", false);
	}

	private void onManageDonationClick(ListItem<?> item){
		UiUtils.launchWebBrowser(getActivity(), useStagingEnvironmentForDonations() ? "https://sponsor.staging.joinmastodon.org/donate/manage" : "https://sponsor.joinmastodon.org/donate/manage");
	}

	@Subscribe
	public void onSelfUpdateStateChanged(SelfUpdateStateChangedEvent ev){
		updateUpdateBanner();
	}

	@Subscribe
	public void onAccountLoggedOut(AccountLoggedOutEvent ev){
		for(int i=0;i<items.size();i++){
			if(items.get(i) instanceof SettingsAccountListItem<?> item && item.parentObject instanceof AccountSession as && as.getID().equals(ev.id)){
				items.remove(i);
				itemsAdapter.notifyItemRemoved(i);
				break;
			}
		}
	}

	private void updateUpdateBanner(){
		GithubSelfUpdater.UpdateState state=GithubSelfUpdater.getInstance().getState();
		if(state==GithubSelfUpdater.UpdateState.NO_UPDATE || state==GithubSelfUpdater.UpdateState.CHECKING){
			bannerAdapter.setVisible(false);
		}else{
			bannerAdapter.setVisible(true);
			updateText.setText(getString(R.string.app_update_version, GithubSelfUpdater.getInstance().getUpdateInfo().version));
			if(state==GithubSelfUpdater.UpdateState.UPDATE_AVAILABLE){
				updateButton2.setVisibility(View.GONE);
				updateButton1.setEnabled(true);
				updateButton1.setText(getString(R.string.download_update, UiUtils.formatFileSize(getActivity(), GithubSelfUpdater.getInstance().getUpdateInfo().size, true)));
			}else if(state==GithubSelfUpdater.UpdateState.DOWNLOADING){
				updateButton2.setVisibility(View.VISIBLE);
				updateButton2.setText(R.string.cancel);
				updateButton1.setEnabled(false);
				list.removeCallbacks(updateDownloadProgressUpdater);
				updateDownloadProgressUpdater.run();
			}else if(state==GithubSelfUpdater.UpdateState.DOWNLOADED){
				updateButton2.setVisibility(View.GONE);
				updateButton1.setEnabled(true);
				updateButton1.setText(R.string.install_update);
			}
		}
	}

	private void onUpdateButtonClick(View v){
		if(v.getId()==R.id.button){
			GithubSelfUpdater.UpdateState state=GithubSelfUpdater.getInstance().getState();
			if(state==GithubSelfUpdater.UpdateState.UPDATE_AVAILABLE){
				GithubSelfUpdater.getInstance().downloadUpdate();
			}else if(state==GithubSelfUpdater.UpdateState.DOWNLOADED){
				GithubSelfUpdater.getInstance().installUpdate(getActivity());
			}
		}else if(v.getId()==R.id.button2){
			GithubSelfUpdater.getInstance().cancelDownload();
		}
	}
}
