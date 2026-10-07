package org.joinmastodon.android.fork;

import android.app.Activity;

import org.joinmastodon.android.fragments.discover.TrendingHashtagsFragment;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Constructor;
import java.util.HashSet;
import java.util.Set;

import androidx.recyclerview.widget.RecyclerView;

import static org.junit.Assert.*;

/**
 * The followed hashtags section is merged in front of upstream's trending adapter, and
 * MergeRecyclerAdapter maps each view type to exactly one adapter: "You MUST override
 * getItemViewType() in each of your adapters and make sure the returned values don't intersect
 * across adapters."
 *
 * Sharing a type means a recycled row can be handed to the adapter that didn't create it, which
 * casts it and throws, as a crash while scrolling with the section open. That is what these
 * cover, since nothing else does until someone scrolls a real phone.
 */
@Config(sdk=35)
@RunWith(RobolectricTestRunner.class)
public class FollowedHashtagsAdapterTest{
	private FollowedHashtagsAdapter adapter(){
		Activity activity=Robolectric.buildActivity(Activity.class).create().get();
		return new FollowedHashtagsAdapter(activity, "test-account");
	}

	/** Every type this adapter can emit, across collapsed, empty and populated states. */
	private Set<Integer> viewTypes(FollowedHashtagsAdapter adapter){
		Set<Integer> types=new HashSet<>();
		for(int position=0; position<adapter.getItemCount(); position++)
			types.add(adapter.getItemViewType(position));
		return types;
	}

	@Test
	public void view_types_do_not_collide_with_upstreams_adapter() throws Exception{
		// Upstream's HashtagsAdapter doesn't override getItemViewType, so every row it has is 0.
		int upstreamType=upstreamHashtagsAdapterViewType();
		assertFalse("this adapter emits "+upstreamType+", the same type upstream's trending rows use, "
						+"so a recycled row can reach the wrong adapter and crash while scrolling",
				viewTypes(adapter()).contains(upstreamType));
	}

	@Test
	public void view_types_are_distinct_per_row_kind(){
		FollowedHashtagsAdapter adapter=adapter();
		int header=adapter.getItemViewType(0);
		assertEquals("collapsed, there is only the header", 1, adapter.getItemCount());
		assertNotEquals("the header and the rows under it must be told apart", header, header+1);
	}

	@Test
	public void collapsed_by_default(){
		assertEquals("the section starts closed, so the tab still leads with trending hashtags",
				1, adapter().getItemCount());
	}

	/** Reads the view type off upstream's adapter rather than assuming it stays 0. */
	private int upstreamHashtagsAdapterViewType() throws Exception{
		for(Class<?> inner : TrendingHashtagsFragment.class.getDeclaredClasses()){
			if(!inner.getSimpleName().equals("HashtagsAdapter"))
				continue;
			Constructor<?> c=inner.getDeclaredConstructors()[0];
			c.setAccessible(true);
			Object instance=c.getParameterCount()==0
					? c.newInstance()
					: c.newInstance(new TrendingHashtagsFragment());
			return ((RecyclerView.Adapter<?>) instance).getItemViewType(0);
		}
		throw new AssertionError("TrendingHashtagsFragment.HashtagsAdapter not found; if upstream "
				+"renamed it, point this test at the new name");
	}
}
