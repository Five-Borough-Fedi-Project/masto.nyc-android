package org.joinmastodon.android.fork;

import android.app.Fragment;
import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.joinmastodon.android.R;
import org.joinmastodon.android.fragments.HomeFragment;

import me.grishka.appkit.fragments.AppKitFragment;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * The bottom tab bar is wired up in three parallel places: the tabs in {@code tab_bar.xml}, the
 * fragment fields on {@link HomeFragment}, and the mapping between them. Adding the Neighbors tab
 * meant editing all of them, and a miss is silent — the compiler is happy, the app starts, and the
 * tab is just subtly wrong. That shipped twice: once as a tab whose toolbar sat under the status
 * bar because the insets weren't forwarded to it, and once as a tab that loaded the wrong timeline.
 *
 * So these tests make the three agree. They drive HomeFragment through reflection with stub
 * fragments in its fields rather than running its lifecycle, which would want a logged-in account,
 * a network and an image loader; the wiring is all that's under test here.
 */
@Config(sdk=35)
@RunWith(RobolectricTestRunner.class)
public class HomeFragmentWiringTest{
	/** Each fragment field on HomeFragment, holding an instance that can be told from the others. */
	private final Map<String, Fragment> stubs=new LinkedHashMap<>();
	private final HomeFragment fragment=new HomeFragment();

	public HomeFragmentWiringTest() throws Exception{
		for(Field field : HomeFragment.class.getDeclaredFields()){
			if(!AppKitFragment.class.isAssignableFrom(field.getType()))
				continue;
			field.setAccessible(true);
			// A real instance of the field's own type: reflection checks the assignment, and every
			// fragment has the no-argument constructor the framework needs anyway.
			Fragment stub=(Fragment) field.getType().getDeclaredConstructor().newInstance();
			field.set(fragment, stub);
			stubs.put(field.getName(), stub);
		}
		assertFalse("no fragment fields found on HomeFragment; if upstream restructured it, this "
				+"test is looking at the wrong thing and needs rewriting", stubs.isEmpty());
	}

	@Test
	public void every_tab_fragment_receives_window_insets() throws Exception{
		Set<Fragment> reached=new LinkedHashSet<>(tabFragments());
		for(Map.Entry<String, Fragment> entry : stubs.entrySet()){
			assertTrue("HomeFragment."+entry.getKey()+" is a tab fragment, but tabFragments() "
							+"doesn't return it, so onApplyWindowInsets never reaches it and its "
							+"toolbar will be drawn under the status bar",
					reached.contains(entry.getValue()));
		}
	}

	@Test
	public void every_tab_in_the_bar_opens_a_distinct_fragment() throws Exception{
		Map<Fragment, Integer> seen=new IdentityHashMap<>();
		for(int tab : tabIdsInTheBar()){
			Fragment target;
			try{
				target=fragmentForTab(tab);
			}catch(IllegalArgumentException e){
				throw new AssertionError("tab_bar.xml has a tab, "+idName(tab)+", that "
						+"HomeFragment.fragmentForTab() doesn't know about; tapping it throws", e);
			}
			assertTrue(idName(tab)+" opens a fragment that isn't one of HomeFragment's tab "
					+"fragments", tabFragments().contains(target));
			Integer clash=seen.put(target, tab);
			if(clash!=null)
				fail(idName(tab)+" and "+idName(clash)+" both open the same fragment, so one of "
						+"them does nothing");
		}
	}

	@Test
	public void the_bar_has_a_tab_for_every_fragment() throws Exception{
		int tabs=tabIdsInTheBar().size();
		assertEquals("tab_bar.xml has "+tabs+" tabs but HomeFragment keeps "+stubs.size()
						+" fragments ("+String.join(", ", stubs.keySet())+"), so "
						+(tabs<stubs.size()
								? "a fragment is built on every launch and never shown"
								: "a tab in the bar has no fragment behind it"),
				tabs, stubs.size());
	}

	/** The fork opens on Neighbors by default, which is the whole point of the tab. */
	@Test
	public void the_default_tab_follows_the_setting() throws Exception{
		ForkPrefs.setOpensOnNeighbors(true);
		assertEquals("with the setting on, the app should open on Neighbors",
				"tab_neighbors", idName(currentTabOfAFreshFragment()));
		ForkPrefs.setOpensOnNeighbors(false);
		assertEquals("with the setting off, the app should open on Home",
				"tab_home", idName(currentTabOfAFreshFragment()));
	}

	private int currentTabOfAFreshFragment() throws Exception{
		Field field=HomeFragment.class.getDeclaredField("currentTab");
		field.setAccessible(true);
		return field.getInt(new HomeFragment());
	}

	@SuppressWarnings("unchecked")
	private List<? extends Fragment> tabFragments() throws Exception{
		Method method=HomeFragment.class.getDeclaredMethod("tabFragments");
		method.setAccessible(true);
		return (List<? extends Fragment>) method.invoke(fragment);
	}

	private Fragment fragmentForTab(int tab) throws Exception{
		Method method=HomeFragment.class.getDeclaredMethod("fragmentForTab", int.class);
		method.setAccessible(true);
		try{
			return (Fragment) method.invoke(fragment, tab);
		}catch(InvocationTargetException e){
			if(e.getCause() instanceof RuntimeException re)
				throw re;
			throw e;
		}
	}

	/** The ids of the TabBar's direct children, which is what the bar actually shows. */
	private List<Integer> tabIdsInTheBar(){
		Context context=new ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_Mastodon_Light);
		View bar=LayoutInflater.from(context).inflate(R.layout.tab_bar, null).findViewById(R.id.tabbar);
		assertNotNull("tab_bar.xml no longer has a view with id tabbar", bar);
		List<Integer> ids=new ArrayList<>();
		ViewGroup group=(ViewGroup) bar;
		for(int i=0; i<group.getChildCount(); i++)
			ids.add(group.getChildAt(i).getId());
		return ids;
	}

	private String idName(int id){
		return RuntimeEnvironment.getApplication().getResources().getResourceEntryName(id);
	}
}
