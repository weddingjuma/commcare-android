package org.commcare.android.util;

import android.support.annotation.NonNull;

import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.services.locale.Localizer;
import org.javarosa.core.util.NoLocalizedTextException;

public class ChangeLocaleUtil{
    
    @NonNull
    public static String[] removeDefault(@NonNull String[] raw){
        String[] output = new String[raw.length -1];
        int index = 0;
        for(int i=0; i<raw.length; i++){
            String rawInput = raw[i];
            if(!rawInput.equals("default")){
                output[index] = rawInput;
                index++;
            }
        }
        return output;
    }
    
    @NonNull
    public static String[] translateLocales(@NonNull String[] raw){
        String[] translated = new String[raw.length];
        for(int i=0;i<raw.length;i++){
            try{
                translated[i] = Localization.get(raw[i]);
            } catch(NoLocalizedTextException e){
                translated[i] = raw[i];
            }
        }
        return translated;
    }
    
    @NonNull
    public static String[] getLocaleCodes(){
        Localizer lizer = Localization.getGlobalLocalizerAdvanced();
        String[] rawLocales = lizer.getAvailableLocales();
        String[] rawDefaultRemoved = removeDefault(rawLocales);
        return rawDefaultRemoved;
    }
    
    @NonNull
    public static String[] getLocaleNames(){
        String[] rawDefaultRemoved = getLocaleCodes();
        String[] localeNames = translateLocales(rawDefaultRemoved);
        return localeNames;
    }

}
