package org.joinmastodon.android.fork;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.joinmastodon.android.R;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import me.grishka.appkit.utils.V;

import static org.junit.Assert.*;

/**
 * The welcome screen's art is four layers that slide against each other as the phone is tilted or
 * dragged. Each is laid out larger than the screen so that movement never brings an edge into
 * view, and this checks that the overhang the layout actually applies is bigger than the distance
 * the layer is actually allowed to travel.
 *
 * It is checked here rather than by eye because the screen only appears when signed out, which on
 * a test device means giving up the session and signing in again by hand.
 *
 * Robolectric lays out at density 1, so a pixel here is a dp.
 */
@Config(sdk=35)
@RunWith(RobolectricTestRunner.class)
public class SplashArtOverhangTest{
	private static final int W=360, H=800;

	private Context context;

	@Before
	public void setUp(){
		context=new ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_Mastodon_Dark);
		V.setApplicationContext(context);
	}

	private ViewGroup artContainer(){
		View root=LayoutInflater.from(context).inflate(R.layout.fragment_splash, null);
		root.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
				View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
		root.layout(0, 0, W, H);
		return root.findViewById(R.id.art_container);
	}

	private String name(int viewId){
		return context.getResources().getResourceEntryName(viewId);
	}

	@Test
	public void every_layer_covers_the_screen_at_its_furthest(){
		ViewGroup container=artContainer();
		int w=container.getWidth(), h=container.getHeight();
		assertTrue("the art container didn't lay out", w>0 && h>0);

		for(SplashArt.Layer layer : SplashArt.LAYERS){
			View view=container.findViewById(layer.viewId());
			assertNotNull("fragment_splash.xml has no "+name(layer.viewId()), view);
			int travelX=V.dp(layer.travelXDp()), travelY=V.dp(layer.travelYDp());

			// Pushed as far right and down as it goes, the left and top edges have to stay off
			// screen; pushed the other way, the right and bottom edges do.
			check(layer, "left", view.getLeft()+travelX, 0);
			check(layer, "top", view.getTop()+travelY, 0);
			check(layer, "right", w-(view.getRight()-travelX), 0);
			check(layer, "bottom", h-(view.getBottom()-travelY), 0);
		}
	}

	private void check(SplashArt.Layer layer, String edge, int past, int limit){
		assertTrue(name(layer.viewId())+" travels "
						+(edge.equals("left") || edge.equals("right") ? layer.travelXDp() : layer.travelYDp())
						+"dp, which pulls its "+edge+" edge "+past+"dp into view: the layer is not "
						+"drawn far enough past that side of the screen to cover the movement",
				past<=limit);
	}

	/** The budget the layout applies has to beat the furthest any layer is told it may go. */
	@Test
	public void the_overhang_beats_the_longest_travel(){
		int overhang=SplashArt.overhangPx(context);
		int longest=SplashArt.LAYERS.stream()
				.mapToInt(l->Math.max(V.dp(l.travelXDp()), V.dp(l.travelYDp()))).max().orElse(0);
		assertTrue("the layers are drawn "+overhang+"dp past each edge, but the furthest one "
						+"travels "+longest+"dp, so it runs out of art", overhang>longest);
	}
}
