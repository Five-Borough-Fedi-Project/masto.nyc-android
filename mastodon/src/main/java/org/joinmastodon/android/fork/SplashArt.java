package org.joinmastodon.android.fork;

import android.content.Context;

import org.joinmastodon.android.R;

import java.util.List;

/**
 * The parallax layers on the welcome screen, and how far each one is allowed to travel.
 *
 * Travel grows with nearness, which is what reads as depth. Every layer is laid out larger than
 * the screen by {@link #overhangPx} on all four sides, and that overhang is the budget: a layer
 * that travels further than its overhang drags its own edge into view.
 *
 * The numbers live here rather than inline in SplashFragment so that SplashArtOverhangTest can
 * hold them against the overhang the layout actually applies. They disagreed once: the layout
 * asked for the overhang with {@code android:layout_margin="-24dp"}, and
 * {@link android.view.ViewGroup.MarginLayoutParams} quietly drops a negative value there, because
 * it reads that attribute with -1 as its "unset" sentinel and only applies it when it is >= 0. So
 * every layer was laid out at exactly screen size with no overhang at all, and any movement at all
 * showed an edge. The per-side attributes have no such check, which is why the layout uses those.
 */
public class SplashArt{
	/** A layer of the art, and the furthest it may move from rest, in dp. */
	public record Layer(int viewId, int travelXDp, int travelYDp){}

	/** Back to front. */
	public static final List<Layer> LAYERS=List.of(
			new Layer(R.id.art_sky, 4, 4),
			new Layer(R.id.art_skyline, 8, 6),
			new Layer(R.id.art_river, 14, 10),
			new Layer(R.id.art_foreground, 22, 16));

	/**
	 * How far past each edge of the screen the layers are drawn. The dimension is negative, since
	 * the layout uses it as a margin; this returns it as a positive distance.
	 */
	public static int overhangPx(Context context){
		return -context.getResources().getDimensionPixelSize(R.dimen.splash_art_overhang);
	}
}
