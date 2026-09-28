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

import java.io.IOException;

import me.grishka.appkit.utils.V;

/**
 * Golden images of the profile QR code screen, the layout this fork changed to survive landscape.
 * The assertions in QrCodeLayoutTest say what has to be true; these catch the rest of it moving.
 * See ForkScreenshot for how to re-record.
 *
 * The screen's particle animation is not drawn here: nothing starts it in a plain measure and
 * layout pass, so the images stay deterministic.
 */
@Config(sdk=35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner.class)
public class QrCodeScreenshotTest{
	private Context context;

	@Before
	public void setUp(){
		context=new ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_Mastodon_Dark);
		V.setApplicationContext(context);
	}

	@Test
	public void qr_code_portrait() throws IOException{
		ForkScreenshot.assertMatchesGolden("qr-code-portrait", render(360, 800));
	}

	@Test
	public void qr_code_landscape() throws IOException{
		ForkScreenshot.assertMatchesGolden("qr-code-landscape", render(800, 360));
	}

	private View render(int w, int h){
		View root=LayoutInflater.from(context).inflate(R.layout.fragment_profile_qr, null);
		// Fixed content: the real screen fills these from the account.
		((TextView) root.findViewById(R.id.username)).setText("playstoretesting");
		((TextView) root.findViewById(R.id.domain)).setText("masto.nyc");
		root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
				View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
		root.layout(0, 0, w, h);
		return root;
	}
}
