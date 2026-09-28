package org.joinmastodon.android.fork;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import org.joinmastodon.android.R;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import me.grishka.appkit.utils.V;

import static org.junit.Assert.*;

/**
 * The profile QR code screen used to lock itself to portrait, because the code is a square as wide
 * as its container and ran off the bottom in landscape. Android 16 ignores that lock on large
 * screens, so the layout has to fit by itself now. See FORK.md.
 *
 * Robolectric renders at density 1, so the sizes below are both pixels and dp: a phone-shaped
 * window in each orientation.
 */
@Config(sdk=35)
// Native graphics gives real text measurement, which the domain chip test needs.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner.class)
public class QrCodeLayoutTest{
	private static final int LANDSCAPE_W=800, LANDSCAPE_H=360;
	private static final int PORTRAIT_W=360, PORTRAIT_H=800;

	private Context context;

	@Before
	public void setUp(){
		context=new ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_Mastodon_Dark);
		V.setApplicationContext(context);
	}

	@Test
	public void everything_fits_on_screen_in_landscape(){
		View root=laidOut(LANDSCAPE_W, LANDSCAPE_H);
		View code=root.findViewById(R.id.code);
		int buttonsBottom=bottomInRoot(root, root.findViewById(R.id.save_btn));

		assertTrue("the buttons row ends at "+buttonsBottom+"dp in a "+LANDSCAPE_H+
				"dp-tall window, so it is pushed off the bottom. The code square has to be capped by "+
				"the height left over, not just by 400dp.", buttonsBottom<=LANDSCAPE_H);
		assertTrue("the QR code has no height left", code.getMeasuredHeight()>0);
	}

	@Test
	public void everything_fits_on_screen_in_portrait(){
		View root=laidOut(PORTRAIT_W, PORTRAIT_H);
		int buttonsBottom=bottomInRoot(root, root.findViewById(R.id.save_btn));
		assertTrue("the buttons row ends at "+buttonsBottom+"dp in a "+PORTRAIT_H+"dp-tall window",
				buttonsBottom<=PORTRAIT_H);
		assertTrue(root.findViewById(R.id.code).getMeasuredHeight()>0);
	}

	@Test
	public void code_is_capped_at_400dp(){
		View container=laidOut(PORTRAIT_W, PORTRAIT_H).findViewById(R.id.corner_animation_container);
		assertTrue("the code container is "+container.getMeasuredWidth()+"dp wide, over the 400dp cap",
				container.getMeasuredWidth()<=V.dp(400));
		assertTrue(container.getMeasuredWidth()>0);
	}

	@Test
	public void domain_chip_keeps_its_width_when_the_username_is_long(){
		View root=inflate();
		((TextView) root.findViewById(R.id.username)).setText("a-very-long-account-name-that-eats-the-row");
		((TextView) root.findViewById(R.id.domain)).setText("masto.nyc");
		layOut(root, LANDSCAPE_W, LANDSCAPE_H);

		TextView domain=root.findViewById(R.id.domain);
		float wanted=domain.getPaint().measureText("masto.nyc");
		assertTrue("the domain chip is "+domain.getWidth()+"dp wide for text wanting "+wanted+
				"dp, so a long username is squeezing it out. The username should ellipsize instead.",
				domain.getWidth()>=wanted);
	}

	private View laidOut(int w, int h){
		View root=inflate();
		layOut(root, w, h);
		return root;
	}

	private View inflate(){
		return LayoutInflater.from(context).inflate(R.layout.fragment_profile_qr, null);
	}

	private void layOut(View root, int w, int h){
		root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
				View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
		root.layout(0, 0, w, h);
	}

	private int bottomInRoot(View root, View view){
		int top=0;
		for(View v=view; v!=root; v=(View) v.getParent())
			top+=v.getTop();
		return top+view.getHeight();
	}
}
