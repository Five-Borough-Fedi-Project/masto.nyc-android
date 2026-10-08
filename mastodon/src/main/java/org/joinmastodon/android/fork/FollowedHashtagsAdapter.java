package org.joinmastodon.android.fork;

import android.app.Activity;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.joinmastodon.android.R;
import org.joinmastodon.android.api.requests.tags.GetFollowedTags;
import org.joinmastodon.android.api.session.AccountSessionManager;
import org.joinmastodon.android.model.HeaderPaginationList;
import org.joinmastodon.android.model.Hashtag;
import org.joinmastodon.android.ui.utils.UiUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import me.grishka.appkit.api.Callback;
import me.grishka.appkit.api.ErrorResponse;
import me.grishka.appkit.utils.V;

/**
 * The hashtags you follow, as a section that opens and closes at the top of the trending hashtags
 * tab. They used to live in the home toolbar's dropdown, which this fork removed.
 *
 * Collapsed by default: the tab is for discovering hashtags, and your own are a drawer you open
 * rather than the first thing in the way.
 */
public class FollowedHashtagsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>{
	// MergeRecyclerAdapter maps a view type to one adapter, and says so in its own docs: "You MUST
	// override getItemViewType() in each of your adapters and make sure the returned values don't
	// intersect across adapters." Upstream's HashtagsAdapter doesn't override it, so every trending
	// row is type 0. Sharing a type means a recycled row can be handed to the wrong adapter, which
	// casts it and throws. Hence an offset nothing else uses.
	private static final int TYPE_OFFSET=0x5B0000;
	private static final int TYPE_HEADER=TYPE_OFFSET, TYPE_TAG=TYPE_OFFSET+1, TYPE_EMPTY=TYPE_OFFSET+2;

	private final Activity activity;
	private final String accountID;
	private RecyclerView list;
	private final List<Hashtag> tags=new ArrayList<>();
	private boolean expanded, loaded, loading;

	public FollowedHashtagsAdapter(Activity activity, String accountID){
		this.activity=activity;
		this.accountID=accountID;
		// Show last time's list immediately; the request to refresh it takes seconds.
		for(String name : ForkPrefs.cachedFollowedHashtags(accountID)){
			Hashtag tag=new Hashtag();
			tag.name=name;
			tags.add(tag);
		}
		loaded=!tags.isEmpty();
	}

	@Override
	public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView){
		super.onAttachedToRecyclerView(recyclerView);
		list=recyclerView;
	}

	@Override
	public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView){
		super.onDetachedFromRecyclerView(recyclerView);
		list=null;
	}

	/**
	 * RecyclerView throws if an adapter changes while it is laying out or settling a scroll, and
	 * the response to opening this section can land at exactly that moment.
	 */
	private void notifyWhenSettled(){
		if(list!=null && (list.isComputingLayout() || list.getScrollState()!=RecyclerView.SCROLL_STATE_IDLE))
			list.post(this::notifyDataSetChanged);
		else
			notifyDataSetChanged();
	}

	@NonNull
	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType){
		if(viewType==TYPE_HEADER)
			return new HeaderHolder(parent);
		if(viewType==TYPE_EMPTY)
			return new EmptyHolder(parent);
		return new TagHolder(parent);
	}

	@Override
	public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position){
		if(holder instanceof HeaderHolder header)
			header.bind();
		else if(holder instanceof TagHolder tag && position-1 < tags.size())
			tag.bind(tags.get(position-1));
		// The tint marks the section as yours; the rule on the last row is where it ends and
		// upstream's trending list begins. Without either, the two are laid out identically and
		// read as one list.
		holder.itemView.setBackgroundResource(position==getItemCount()-1
				? R.drawable.bg_followed_hashtags_row_last
				: R.drawable.bg_followed_hashtags_row);
	}

	@Override
	public int getItemViewType(int position){
		if(position==0)
			return TYPE_HEADER;
		return tags.isEmpty() ? TYPE_EMPTY : TYPE_TAG;
	}

	@Override
	public int getItemCount(){
		if(!expanded)
			return 1;
		// Nothing but the header until the first response, so an empty list isn't claimed early.
		return 1+(tags.isEmpty() ? (loaded ? 1 : 0) : tags.size());
	}

	private void toggle(){
		expanded=!expanded;
		notifyDataSetChanged();
		// Fetch every time it is opened, not just the first time: following a hashtag during the
		// session otherwise leaves this showing a stale list until the app restarts. Whatever was
		// loaded before stays on screen while the request runs.
		if(expanded && !loading)
			load();
	}

	private void load(){
		loading=true;
		new GetFollowedTags(null, 100)
				.setCallback(new Callback<>(){
					@Override
					public void onSuccess(HeaderPaginationList<Hashtag> result){
						loading=false;
						loaded=true;
						tags.clear();
						tags.addAll(result);
						ForkPrefs.setCachedFollowedHashtags(accountID,
								result.stream().map(t->t.name).collect(Collectors.toList()));
						notifyWhenSettled();
					}

					@Override
					public void onError(ErrorResponse error){
						loading=false;
						loaded=true;
						notifyWhenSettled();
						error.showToast(activity);
					}
				})
				.exec(accountID);
	}

	private class HeaderHolder extends RecyclerView.ViewHolder{
		private final TextView title;
		private final ImageView chevron;

		HeaderHolder(ViewGroup parent){
			super(row(parent));
			LinearLayout row=(LinearLayout) itemView;
			title=new TextView(activity);
			title.setTextAppearance(R.style.m3_title_small);
			title.setText(R.string.your_followed_hashtags);
			row.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
			chevron=new ImageView(activity);
			chevron.setImageResource(R.drawable.ic_arrow_drop_down_24px);
			chevron.setImageTintList(title.getTextColors());
			row.addView(chevron, new LinearLayout.LayoutParams(V.dp(24), V.dp(24)));
			row.setOnClickListener(v->toggle());
		}

		void bind(){
			chevron.setRotation(expanded ? 180 : 0);
			itemView.setContentDescription(activity.getString(R.string.your_followed_hashtags));
		}
	}

	private class TagHolder extends RecyclerView.ViewHolder{
		private final TextView name;
		private Hashtag tag;

		TagHolder(ViewGroup parent){
			super(row(parent));
			LinearLayout row=(LinearLayout) itemView;
			name=new TextView(activity);
			name.setTextAppearance(R.style.m3_body_large);
			row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
			row.setOnClickListener(v->{
				if(tag!=null)
					UiUtils.openHashtagTimeline(activity, accountID, tag);
			});
		}

		void bind(Hashtag hashtag){
			tag=hashtag;
			name.setText("#"+hashtag.name);
		}
	}

	private class EmptyHolder extends RecyclerView.ViewHolder{
		EmptyHolder(ViewGroup parent){
			super(row(parent));
			TextView text=new TextView(activity);
			text.setTextAppearance(R.style.m3_body_medium);
			text.setTextColor(UiUtils.getThemeColor(activity, R.attr.colorM3OnSurfaceVariant));
			text.setText(R.string.no_followed_hashtags);
			((LinearLayout) itemView).addView(text);
		}
	}

	private View row(ViewGroup parent){
		LinearLayout row=new LinearLayout(activity);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setPaddingRelative(V.dp(16), V.dp(12), V.dp(16), V.dp(12));
		// Replaced per row in onBindViewHolder, which is the only place that knows whether this is
		// the last row of the section.
		row.setBackgroundResource(R.drawable.bg_followed_hashtags_row);
		row.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		return row;
	}
}
