package org.joinmastodon.android.fragments;

import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;

import org.joinmastodon.android.MastodonApp;
import org.joinmastodon.android.R;
import org.joinmastodon.android.api.MastodonErrorResponse;
import org.joinmastodon.android.api.requests.accounts.CheckInviteLink;
import org.joinmastodon.android.api.session.AccountSessionManager;
import org.joinmastodon.android.fork.ForkConfig;
import org.joinmastodon.android.fragments.onboarding.InstanceRulesFragment;
import org.joinmastodon.android.model.Instance;
import org.joinmastodon.android.ui.InterpolatingMotionEffect;
import org.joinmastodon.android.ui.M3AlertDialogBuilder;
import org.joinmastodon.android.ui.text.HtmlParser;
import org.joinmastodon.android.ui.views.ProgressBarButton;
import org.joinmastodon.android.ui.views.SizeListenerFrameLayout;
import org.parceler.Parcels;

import java.util.Objects;

import androidx.annotation.Nullable;
import me.grishka.appkit.Nav;
import me.grishka.appkit.api.Callback;
import me.grishka.appkit.api.ErrorResponse;
import me.grishka.appkit.fragments.AppKitFragment;
import me.grishka.appkit.utils.V;

public class SplashFragment extends AppKitFragment{

	// masto.nyc fork: this app is locked to a single server; there is no server picker.
	private static final String DEFAULT_SERVER=ForkConfig.INSTANCE_DOMAIN;

	private SizeListenerFrameLayout contentView;
	private View artContainer;
	private InterpolatingMotionEffect motionEffect;
	// masto.nyc fork: four full-bleed layers instead of upstream's five positioned pieces.
	private View artSky, artSkyline, artRiver, artForeground;
	private ProgressBarButton defaultServerButton;
	private final String chosenDefaultServer=DEFAULT_SERVER;
	private boolean checkedInviteLink;
	private Uri currentInviteLink;
	private ProgressDialog instanceLoadingProgress;
	private String inviteCode;

	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		setRetainInstance(true);
		motionEffect=new InterpolatingMotionEffect(MastodonApp.context);
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState){
		contentView=(SizeListenerFrameLayout) inflater.inflate(R.layout.fragment_splash, container, false);
		contentView.findViewById(R.id.btn_log_in).setOnClickListener(this::onLogInClick);
		defaultServerButton=contentView.findViewById(R.id.btn_join_default_server);
		defaultServerButton.setText(getString(R.string.join_default_server, chosenDefaultServer));
		defaultServerButton.setOnClickListener(this::onJoinDefaultServerClick);

		artSky=contentView.findViewById(R.id.art_sky);
		artSkyline=contentView.findViewById(R.id.art_skyline);
		artRiver=contentView.findViewById(R.id.art_river);
		artForeground=contentView.findViewById(R.id.art_foreground);

		artContainer=contentView.findViewById(R.id.art_container);
		// Travel grows with nearness, which is what reads as depth. The 24dp overscan in the
		// layout is the budget: no layer may move further than that, or its edge comes into view.
		motionEffect.addViewEffect(new InterpolatingMotionEffect.ViewEffect(artSky, V.dp(-4), V.dp(4), V.dp(-4), V.dp(4)));
		motionEffect.addViewEffect(new InterpolatingMotionEffect.ViewEffect(artSkyline, V.dp(-8), V.dp(8), V.dp(-6), V.dp(6)));
		motionEffect.addViewEffect(new InterpolatingMotionEffect.ViewEffect(artRiver, V.dp(-14), V.dp(14), V.dp(-10), V.dp(10)));
		motionEffect.addViewEffect(new InterpolatingMotionEffect.ViewEffect(artForeground, V.dp(-22), V.dp(22), V.dp(-16), V.dp(16)));
		artContainer.setOnTouchListener(motionEffect);
		if(currentInviteLink!=null)
			defaultServerButton.setText(getString(R.string.join_server_x_with_invite, currentInviteLink.getHost()));
		else if(!checkedInviteLink)
			maybePickUpInviteLink();

		return contentView;
	}

	// masto.nyc fork: logging in skips the server chooser and goes straight to OAuth on our server.
	private void onLogInClick(View v){
		if(instanceLoadingProgress!=null)
			return;
		instanceLoadingProgress=new ProgressDialog(getActivity());
		instanceLoadingProgress.setCancelable(false);
		instanceLoadingProgress.setMessage(getString(R.string.loading_instance));
		instanceLoadingProgress.show();
		AccountSessionManager.loadInstanceInfo(ForkConfig.INSTANCE_DOMAIN, new Callback<>(){
			@Override
			public void onSuccess(Instance result){
				if(getActivity()==null)
					return;
				if(instanceLoadingProgress!=null)
					instanceLoadingProgress.dismiss();
				instanceLoadingProgress=null;
				AccountSessionManager.getInstance().authenticate(getActivity(), result);
			}

			@Override
			public void onError(ErrorResponse error){
				if(getActivity()==null)
					return;
				if(instanceLoadingProgress!=null)
					instanceLoadingProgress.dismiss();
				instanceLoadingProgress=null;
				error.showToast(getActivity());
			}
		});
	}

	private void onJoinDefaultServerClick(View v){
		instanceLoadingProgress=new ProgressDialog(getActivity());
		instanceLoadingProgress.setCancelable(false);
		instanceLoadingProgress.setMessage(getString(R.string.loading_instance));
		instanceLoadingProgress.show();
		if(currentInviteLink!=null){
			new CheckInviteLink(currentInviteLink.getPath())
					.setCallback(new Callback<>(){
						@Override
						public void onSuccess(CheckInviteLink.Response result){
							inviteCode=result.inviteCode;
							proceedWithServerDomain(currentInviteLink.getHost());
						}

						@Override
						public void onError(ErrorResponse error){
							if(getActivity()==null)
								return;
							if(instanceLoadingProgress!=null)
								instanceLoadingProgress.dismiss();
							instanceLoadingProgress=null;
							if(error instanceof MastodonErrorResponse mer){
								switch(mer.httpStatus){
									case 401 -> new M3AlertDialogBuilder(getActivity())
											.setTitle(R.string.expired_invite_link)
											.setMessage(getString(R.string.expired_clipboard_invite_link_alert, currentInviteLink.getHost(), chosenDefaultServer))
											.setPositiveButton(R.string.ok, null)
											.show();
									case 404 -> new M3AlertDialogBuilder(getActivity())
											.setTitle(R.string.invalid_invite_link)
											.setMessage(getString(R.string.invalid_clipboard_invite_link_alert, currentInviteLink.getHost(), chosenDefaultServer))
											.setPositiveButton(R.string.ok, null)
											.show();
									default -> error.showToast(getActivity());
								}
							}
						}
					})
					.execNoAuth(currentInviteLink.getHost());
			return;
		}
		proceedWithServerDomain(chosenDefaultServer);
	}

	private void proceedWithServerDomain(String domain){
		AccountSessionManager.loadInstanceInfo(domain, new Callback<>(){
					@Override
					public void onSuccess(Instance result){
						if(getActivity()==null)
							return;
						if(instanceLoadingProgress!=null)
							instanceLoadingProgress.dismiss();
						instanceLoadingProgress=null;
						if(!result.areRegistrationsOpen() && TextUtils.isEmpty(inviteCode)){
							new M3AlertDialogBuilder(getActivity())
									.setTitle(R.string.error)
									.setMessage(R.string.instance_signup_closed)
									.setPositiveButton(R.string.ok, null)
									.show();
							return;
						}
						Bundle args=new Bundle();
						args.putParcelable("instance", Parcels.wrap(result));
						if(inviteCode!=null)
							args.putString("inviteCode", inviteCode);
						Nav.go(getActivity(), InstanceRulesFragment.class, args);
					}

					@Override
					public void onError(ErrorResponse error){
						if(getActivity()==null)
							return;
						if(instanceLoadingProgress!=null)
							instanceLoadingProgress.dismiss();
						instanceLoadingProgress=null;
						error.showToast(getActivity());
					}
				});
	}

	@Override
	public void onApplyWindowInsets(WindowInsets insets){
		super.onApplyWindowInsets(insets);
		int bottomInset=insets.getSystemWindowInsetBottom();
		if(bottomInset>0 && bottomInset<V.dp(36)){
			contentView.setPadding(contentView.getPaddingLeft(), contentView.getPaddingTop(), contentView.getPaddingRight(), V.dp(36));
		}
		// The root insets its children for the system bars, which would leave the art floating
		// inside a border of the background colour. Cancelling the padding on the art container
		// alone keeps the layers edge to edge while the buttons stay clear of the bars.
		ViewGroup.MarginLayoutParams artLp=(ViewGroup.MarginLayoutParams)artContainer.getLayoutParams();
		artLp.topMargin=-contentView.getPaddingTop();
		artLp.bottomMargin=-contentView.getPaddingBottom();
		artContainer.requestLayout();
	}

	@Override
	public boolean wantsLightStatusBar(){
		return true;
	}

	@Override
	public boolean wantsLightNavigationBar(){
		return false;
	}

	@Override
	protected void onShown(){
		super.onShown();
		motionEffect.activate();
	}

	@Override
	protected void onHidden(){
		super.onHidden();
		motionEffect.deactivate();
	}

	// masto.nyc fork: replaces upstream's loadAndChooseDefaultServer(), which asked
	// api.joinmastodon.org to pick a random server. Our server is fixed, so all that's left is
	// honoring an invite link on the clipboard — and only if it's an invite to our own server.
	private void maybePickUpInviteLink(){
		checkedInviteLink=true;
		ClipData clipData=getActivity().getSystemService(ClipboardManager.class).getPrimaryClip();
		if(clipData==null || clipData.getItemCount()==0)
			return;
		String clipText=clipData.getItemAt(0).coerceToText(getActivity()).toString();
		if(!HtmlParser.isValidInviteUrl(clipText))
			return;
		Uri inviteLink=Uri.parse(clipText);
		String host=HtmlParser.normalizeDomain(Objects.requireNonNull(inviteLink.getHost()));
		if(!ForkConfig.isOurInstance(host))
			return;
		currentInviteLink=inviteLink;
		defaultServerButton.setText(getString(R.string.join_server_x_with_invite, host));
	}
}
