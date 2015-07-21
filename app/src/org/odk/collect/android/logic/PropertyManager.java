/*
 * Copyright (C) 2009 University of Washington
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.odk.collect.android.logic;

import java.util.HashMap;
import java.util.Vector;

import org.javarosa.core.services.IPropertyManager;
import org.javarosa.core.services.properties.IPropertyRules;

import android.content.Context;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.telephony.TelephonyManager;
import android.util.Log;

/**
 * Used to return device properties to JavaRosa
 * 
 * @author Yaw Anokwa (yanokwa@gmail.com)
 */

public class PropertyManager implements IPropertyManager {

    @NonNull
    private String t = "PropertyManager";

    private Context mContext;

    private TelephonyManager mTelephonyManager;
    private HashMap<String, String> mProperties;

    private final static String DEVICE_ID_PROPERTY = "deviceid"; // imei
    private final static String SUBSCRIBER_ID_PROPERTY = "subscriberid"; // imsi
    private final static String SIM_SERIAL_PROPERTY = "simserial";
    private final static String PHONE_NUMBER_PROPERTY = "phonenumber";


    @NonNull
    public String getName() {
        return "Property Manager";
    }


    public PropertyManager(Context context) {
        Log.i(t, "calling constructor");

        mContext = context;

        mProperties = new HashMap<String, String>();
        mTelephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);

        String deviceId = mTelephonyManager.getDeviceId();
        if (deviceId != null && (deviceId.contains("*") || deviceId.contains("000000000000000"))) {
            deviceId =
                Settings.Secure
                        .getString(mContext.getContentResolver(), Settings.Secure.ANDROID_ID);
        }
        mProperties.put(DEVICE_ID_PROPERTY, deviceId);
        mProperties.put(SUBSCRIBER_ID_PROPERTY, mTelephonyManager.getSubscriberId());
        mProperties.put(SIM_SERIAL_PROPERTY, mTelephonyManager.getSimSerialNumber());
        mProperties.put(PHONE_NUMBER_PROPERTY, mTelephonyManager.getLine1Number());
    }


    /*
     * (non-Javadoc)
     * @see org.javarosa.core.services.IPropertyManager#getProperty(java.lang.String)
     */
    @Nullable
    @Override
    public Vector<String> getProperty(String propertyName) {
        return null;
    }


    /*
     * (non-Javadoc)
     * @see org.javarosa.core.services.IPropertyManager#getSingularProperty(java.lang.String)
     */
    @Override
    public String getSingularProperty(@NonNull String propertyName) {
        return mProperties.get(propertyName.toLowerCase());
    }


    /*
     * (non-Javadoc)
     * @see org.javarosa.core.services.IPropertyManager#setProperty(java.lang.String, java.lang.String)
     */
    @Override
    public void setProperty(String propertyName, String propertyValue) {
    }


    /*
     * (non-Javadoc)
     * @see org.javarosa.core.services.IPropertyManager#setProperty(java.lang.String, java.util.Vector)
     */
    @Override
    public void setProperty(String propertyName, @SuppressWarnings("rawtypes") Vector propertyValue) {

    }


    /*
     * (non-Javadoc)
     * @see org.javarosa.core.services.IPropertyManager#addRules(org.javarosa.core.services.properties.IPropertyRules)
     */
    @Override
    public void addRules(IPropertyRules rules) {

    }


    /*
     * (non-Javadoc)
     * @see org.javarosa.core.services.IPropertyManager#getRules()
     */
    @Nullable
    @Override
    public Vector<IPropertyRules> getRules() {
        return null;
    }

}
