package com.att.mobile.android.infra.utils;

import java.lang.reflect.Field;
import java.util.ArrayList;

import android.content.Context;
import android.graphics.Typeface;
import android.util.SparseArray;

/**
 * Includes various utility methods for fonts and text measurements.
 * Manages the custom ClearView fonts provided by at&t
 * 
 * 
 * 
 * @category INFRA
 */
public class FontUtils 
{

    public static ArrayList<Typeface> fonts = new ArrayList<Typeface>();

    public enum FontNames {
        Roboto_Medium,
        Roboto_MediumItalic,
        Roboto_Italic,
        Roboto_Regular,
        OmnesATT_Medium,
        OmnesATT_MediumItalic,
        OmnesATT_RegularItalic,
        OmnesATT_Regular,
        Roboto_LightItalic,
        Roboto_Light,
        OmnesATT_LightItalic,
        OmnesATT_Light
    }


    public static void initFonts(Context context) {
        fonts.add(Typeface.createFromAsset(context.getAssets(), "fonts/Roboto-Medium.ttf"));
        fonts.add(Typeface.createFromAsset(context.getAssets(), "fonts/Roboto-MediumItalic.ttf"));
        fonts.add(Typeface.createFromAsset(context.getAssets(), "fonts/Roboto-Italic.ttf"));
        fonts.add(Typeface.createFromAsset(context.getAssets(), "fonts/Roboto-Regular.ttf"));
        fonts.add(Typeface.createFromAsset(context.getAssets(),  "fonts/OmnesATT-Medium.otf"));
        fonts.add(Typeface.createFromAsset(context.getAssets(),  "fonts/OmnesATT-MediumItalic.otf"));
        fonts.add(Typeface.createFromAsset(context.getAssets(), "fonts/OmnesATT-RegularItalic.otf"));
        fonts.add(Typeface.createFromAsset(context.getAssets(), "fonts/OmnesATT-Regular.otf"));
        fonts.add(Typeface.createFromAsset(context.getAssets(),  "fonts/Roboto-LightItalic.ttf"));
        fonts.add(Typeface.createFromAsset(context.getAssets(), "fonts/Roboto-Light.ttf"));
        fonts.add(Typeface.createFromAsset(context.getAssets(), "fonts/OmnesATT-LightItalic.otf"));
        fonts.add(Typeface.createFromAsset(context.getAssets(), "fonts/OmnesATT-Light.otf"));
    }

    public static Typeface getTypeface(FontNames fontPos){
        return fonts.get(fontPos.ordinal());
    }

}
