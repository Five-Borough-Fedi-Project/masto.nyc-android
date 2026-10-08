package org.joinmastodon.android.fork;

import android.app.Fragment;

import org.joinmastodon.android.fragments.ScrollableToTop;
import org.joinmastodon.android.fragments.discover.DiscoverFragment;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import me.grishka.appkit.fragments.AppKitFragment;

import static org.junit.Assert.*;

/**
 * The search screen's tabs, and the things tapping one of them can do to a fragment that was never
 * built to be a tab there.
 *
 * This fork added a fifth tab, Lists, by reusing ManageListsFragment, which upstream only ever
 * opened as a full screen from settings. Tapping an already-selected tab calls scrollToTop() on
 * whatever fragment is showing, and DiscoverFragment reached that through an unchecked cast to
 * ScrollableToTop, which ManageListsFragment did not implement. Opening Lists and tapping Lists
 * again crashed the app, and nothing here noticed.
 *
 * So these hold the tab count, the page mapping and the interfaces each page has to satisfy
 * against each other. Driven by reflection with fragments in the fields rather than through the
 * lifecycle, which wants an account, a network and a view pager.
 */
@Config(sdk=35)
@RunWith(RobolectricTestRunner.class)
public class DiscoverFragmentWiringTest{
	private final Map<String, Fragment> pages=new LinkedHashMap<>();
	private final DiscoverFragment fragment=new DiscoverFragment();

	public DiscoverFragmentWiringTest() throws Exception{
		for(Field field : DiscoverFragment.class.getDeclaredFields()){
			if(!AppKitFragment.class.isAssignableFrom(field.getType()))
				continue;
			field.setAccessible(true);
			field.set(fragment, field.getType().getDeclaredConstructor().newInstance());
			pages.put(field.getName(), (Fragment) field.get(fragment));
		}
		assertFalse("no fragment fields on DiscoverFragment; if upstream restructured it this test "
				+"is looking at the wrong thing", pages.isEmpty());
	}

	/**
	 * The one that matters. Every tab has to survive being tapped while already selected, and the
	 * only thing standing between that and a crash is implementing this interface.
	 */
	@Test
	public void every_tab_can_be_scrolled_to_top() throws Exception{
		for(int page=0; page<tabCount(); page++){
			Fragment target=fragmentForPage(page);
			assertTrue("the tab at position "+page+" is a "+target.getClass().getSimpleName()
							+", which doesn't implement ScrollableToTop. Tapping that tab while it is "
							+"already selected calls scrollToTop() on it, so it does nothing at best "
							+"and crashes at worst",
					target instanceof ScrollableToTop);
		}
	}

	@Test
	public void every_tab_position_maps_to_a_fragment() throws Exception{
		for(int page=0; page<tabCount(); page++){
			try{
				assertNotNull("the tab at position "+page+" has no fragment", fragmentForPage(page));
			}catch(IllegalStateException e){
				throw new AssertionError("the tab bar has "+tabCount()+" tabs but "
						+"getFragmentForPage() throws at position "+page+"; tapping that tab crashes", e);
			}
		}
	}

	/** One tab per page fragment, so neither a tab nor a fragment is left stranded. */
	@Test
	public void the_tab_count_matches_the_page_fragments() throws Exception{
		// searchFragment is not a tab: it replaces the pager when a search is running.
		int pageFragments=pages.size()-(pages.containsKey("searchFragment") ? 1 : 0);
		assertEquals("the pager reports "+tabCount()+" tabs but DiscoverFragment keeps "
						+pageFragments+" page fragments ("+String.join(", ", pages.keySet())+")",
				tabCount(), pageFragments);
	}

	/** Read off the adapter's own array, so it tracks whatever the fragment actually builds. */
	private int tabCount() throws Exception{
		Field field=DiscoverFragment.class.getDeclaredField("tabViews");
		field.setAccessible(true);
		Object views=field.get(fragment);
		if(views!=null)
			return java.lang.reflect.Array.getLength(views);
		// tabViews is only filled in onCreateView, so before that fall back to counting the
		// positions getFragmentForPage accepts.
		int count=0;
		while(true){
			try{
				fragmentForPage(count);
			}catch(IllegalStateException e){
				return count;
			}
			count++;
			assertTrue("getFragmentForPage accepts an implausible number of pages", count<64);
		}
	}

	private Fragment fragmentForPage(int page) throws Exception{
		Method method=DiscoverFragment.class.getDeclaredMethod("getFragmentForPage", int.class);
		method.setAccessible(true);
		try{
			return (Fragment) method.invoke(fragment, page);
		}catch(InvocationTargetException e){
			if(e.getCause() instanceof RuntimeException re)
				throw re;
			throw e;
		}
	}
}
