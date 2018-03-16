/*
 * Copyright (C) 2015-2016 Willi Ye <williye97@gmail.com>
 *
 * This file is part of Kernel Adiutor.
 *
 * Kernel Adiutor is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Kernel Adiutor is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Kernel Adiutor.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package com.grarak.kerneladiutor.views;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.AppCompatImageView;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.NativeExpressAdView;
import com.grarak.kerneladiutor.R;
import com.grarak.kerneladiutor.utils.AppSettings;
import com.grarak.kerneladiutor.utils.Utils;
import com.grarak.kerneladiutor.utils.ViewUtils;
import com.grarak.kerneladiutor.views.dialog.Dialog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by willi on 08.08.16.
 */
public class AdNativeExpress extends LinearLayout {

    public static final String ADS_FETCH = "https://raw.githubusercontent.com/Grarak/KernelAdiutor/master/ads/ads.json";
    private static final int MAX_WIDTH = 1200;
    private static final int MIN_HEIGHT = 132;

    private boolean mNativeLoaded;
    private boolean mNativeLoading;
    private boolean mNativeFailedLoading;
    private boolean mGHLoading;
    private boolean mGHLoaded;
    private View mProgress;
    private View mAdText;
    private FrameLayout mNativeAdLayout;
    private NativeExpressAdView mNativeExpressAdView;
    private AppCompatImageView mGHImage;

    public AdNativeExpress(Context context) {
        this(context, null);
    }

    public AdNativeExpress(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AdNativeExpress(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        LayoutInflater.from(context).inflate(R.layout.ad_native_express_view, this);
        mNativeAdLayout = findViewById(R.id.ad_layout);
        mProgress = findViewById(R.id.progress);
        mAdText = findViewById(R.id.ad_text);
        mGHImage = findViewById(R.id.gh_image);

        findViewById(R.id.remove_ad).setOnClickListener(v
                -> ViewUtils.dialogDonate(v.getContext()).show());

        mNativeExpressAdView = new NativeExpressAdView(context);
        mNativeExpressAdView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        mNativeExpressAdView.setAdUnitId(getContext().getString(Utils.DARK_THEME ?
                R.string.native_express_id_dark : R.string.native_express_id_light));
        mNativeExpressAdView.setAdListener(new AdListener() {
            @Override
            public void onAdFailedToLoad(int i) {
                super.onAdFailedToLoad(i);
                mNativeLoading = false;
                mNativeLoaded = false;
                mNativeFailedLoading = true;
                loadGHAd();
            }

            @Override
            public void onAdLoaded() {
                super.onAdLoaded();
                mNativeLoaded = true;
                mNativeLoading = false;
                mNativeFailedLoading = false;
                mProgress.setVisibility(GONE);
                mNativeAdLayout.addView(mNativeExpressAdView);
            }
        });
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        int width;
        if (mNativeLoading || (mNativeLoaded && !mNativeFailedLoading)
                || (!mNativeLoaded && mNativeFailedLoading) ||
                (width = mNativeAdLayout.getWidth()) == 0) {
            return;
        }
        float deviceDensity = getResources().getDisplayMetrics().density;
        if (deviceDensity > 0) {
            loadNativeAd(width, deviceDensity);
        }
    }

    private void loadNativeAd(int width, float deviceDensity) {
        float adWidth = width / deviceDensity;
        if (adWidth > MAX_WIDTH) adWidth = MAX_WIDTH;
        mNativeExpressAdView.setAdSize(new AdSize((int) adWidth, MIN_HEIGHT));
        mNativeLoading = true;
        mNativeExpressAdView.loadAd(new AdRequest.Builder().build());
    }

    private boolean isActivityDestroyed(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            if (context instanceof Activity) {
                return ((Activity) context).isDestroyed();
            } else if (context instanceof ContextWrapper) {
                return isActivityDestroyed(
                        ((ContextWrapper) context).getBaseContext());
            }
        }

        return false;
    }

    public void loadGHAd() {
        if (!mNativeFailedLoading || mGHLoading || mGHLoaded) {
            return;
        }
        mGHLoading = true;

        GHAds ghAds = GHAds.fromCache(getContext());
        List<GHAds.GHAd> ghAdList;
        if (ghAds.readable() && (ghAdList = ghAds.getAllAds()) != null) {
            GHAds.GHAd ad = null;
            int min = -1;
            for (GHAds.GHAd ghAd : ghAdList) {
                int shown = AppSettings.getGHAdShown(ghAd.getName(), getContext());
                if (min < 0 || shown < min) {
                    min = shown;
                    ad = ghAd;
                }
            }

            final String name = ad.getName();
            final String link = ad.getLink();
            final int totalShown = min + 1;

            if (isActivityDestroyed(getContext())) return;
            Glide.with(getContext()).load(ad.getBanner()).into(new SimpleTarget<Drawable>() {
                @Override
                public void onLoadStarted(@Nullable Drawable placeholder) {
                    mGHImage.setVisibility(GONE);
                    mProgress.setVisibility(VISIBLE);
                }

                @Override
                public void onLoadFailed(@Nullable Drawable errorDrawable) {
                    mGHImage.setVisibility(GONE);
                    mProgress.setVisibility(GONE);
                    mAdText.setVisibility(VISIBLE);
                    mGHLoaded = false;
                    mGHLoading = false;
                }

                @Override
                public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                    mGHImage.setVisibility(VISIBLE);
                    mProgress.setVisibility(GONE);
                    mAdText.setVisibility(GONE);
                    mGHImage.setImageDrawable(resource);
                    AppSettings.saveGHAdShown(name, totalShown, getContext());
                    mGHLoaded = true;
                    mGHLoading = false;
                }
            });

            mGHImage.setOnClickListener(v
                    -> new Dialog(getContext()).setTitle(R.string.warning)
                    .setMessage(R.string.gh_ad)
                    .setPositiveButton(R.string.open_ad_anyway,
                            (dialog, which) -> Utils.launchUrl(link, getContext())).show());
        } else {
            mGHImage.setVisibility(GONE);
            mProgress.setVisibility(GONE);
            mAdText.setVisibility(VISIBLE);
        }
    }

    public void resume() {
        mNativeExpressAdView.resume();
    }

    public void pause() {
        mNativeExpressAdView.pause();
    }

    public void destroy() {
        mNativeExpressAdView.destroy();
    }

    public static class GHAds {

        private final String mJson;
        private JSONArray mAds;

        public GHAds(String json) {
            mJson = json;
            if (json == null || json.isEmpty()) return;
            try {
                mAds = new JSONArray(json);
            } catch (JSONException ignored) {
            }
        }

        private static GHAds fromCache(Context context) {
            return new GHAds(Utils.readFile(context.getFilesDir() + "/ghads.json", false));
        }

        public void cache(Context context) {
            Utils.writeFile(context.getFilesDir() + "/ghads.json", mJson, false, false);
        }

        private List<GHAd> getAllAds() {
            List<GHAd> list = new ArrayList<>();
            for (int i = 0; i < mAds.length(); i++) {
                try {
                    list.add(new GHAd(mAds.getJSONObject(i)));
                } catch (JSONException ignored) {
                    return null;
                }
            }
            return list;
        }

        public boolean readable() {
            return mAds != null;
        }

        private static class GHAd {
            private final JSONObject mAd;

            private GHAd(JSONObject ad) {
                mAd = ad;
            }

            private String getLink() {
                return getString("link");
            }

            private String getBanner() {
                return getString("banner");
            }

            private String getName() {
                return getString("name");
            }

            private String getString(String key) {
                try {
                    return mAd.getString(key);
                } catch (JSONException ignored) {
                    return null;
                }
            }
        }

    }

}