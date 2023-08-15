/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License.
 */

package com.microsoft.appcenter.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Surface;

import com.microsoft.appcenter.AppCenter;
import com.microsoft.appcenter.BuildConfig;
import com.microsoft.appcenter.ingestion.models.Device;
import com.microsoft.appcenter.ingestion.models.WrapperSdk;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

import java.util.Locale;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.verifyStatic;
import static org.powermock.api.mockito.PowerMockito.when;

@SuppressWarnings("unused")
@RunWith(PowerMockRunner.class)
@PrepareForTest({Build.class, AppCenterLog.class, TextUtils.class})
public class DeviceInfoHelperTest {

    private static final int SCREEN_WIDTH = 100;

    private static final int SCREEN_HEIGHT = 200;

    @Mock
    Context mContext;

    @Mock
    PackageManager mPackageManager;

    @Mock
    PackageInfo mPackageInfo;

    @Mock
    DisplayManager mDisplayManager;

    @Mock
    Resources mResources;

    @Mock
    TelephonyManager mTelephonyManager;

    @Mock
    Display mDisplay;

    @Mock
    DisplayMetrics mDisplayMetrics;

    @Before
    public void setup() {
        DeviceInfoHelper.setCountryCode(null);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mContext.getPackageName()).thenReturn("package-name");
    }

    @Before
    @After
    public void cleanWrapperSdk() {
        DeviceInfoHelper.setWrapperSdk(null);
    }

    @Test
    public void deviceInfo() throws PackageManager.NameNotFoundException, DeviceInfoHelper.DeviceInfoException {

        /* Mock system calls. */
        //noinspection WrongConstant
        when(mContext.getSystemService(eq(Context.DISPLAY_SERVICE))).thenReturn(mDisplayManager);
        when(mDisplayManager.getDisplay(anyInt())).thenReturn(mDisplay);
        //noinspection WrongConstant
        when(mContext.getResources()).thenReturn(mResources);
        //noinspection WrongConstant
        when(mResources.getDisplayMetrics()).thenReturn(mDisplayMetrics);
        mDisplayMetrics.widthPixels = SCREEN_WIDTH;
        mDisplayMetrics.heightPixels = SCREEN_HEIGHT;

        /* Mock data. */
        final Integer osApiLevel = 21;
        final String appVersion = "1.0";
        final String appBuild = "1";
        final String appNamespace = "com.contoso.app";
        final String carrierCountry = "us";
        final String carrierName = "mock-service";
        final Locale locale = Locale.KOREA;
        final String model = "mock-model";
        final String oemName = "mock-manufacture";
        final String osName = "Android";
        final String osVersion = "mock-version";
        final String osBuild = "mock-os-build";
        final String screenSizeLandscape = "100x200";
        final String screenSizePortrait = "200x100";
        final TimeZone timeZone = TimeZone.getTimeZone("KST");
        final Integer timeZoneOffset = timeZone.getOffset(System.currentTimeMillis());
        Locale.setDefault(locale);
        TimeZone.setDefault(timeZone);

        /* Delegates to mock instances. */
        when(mContext.getPackageName()).thenReturn(appNamespace);
        //noinspection WrongConstant
        when(mContext.getSystemService(eq(Context.TELEPHONY_SERVICE))).thenReturn(mTelephonyManager);
        //noinspection WrongConstant
        when(mPackageManager.getPackageInfo(anyString(), eq(0))).thenReturn(mPackageInfo);
        when(mTelephonyManager.getNetworkCountryIso()).thenReturn(carrierCountry);
        when(mTelephonyManager.getNetworkOperatorName()).thenReturn(carrierName);
        when(mDisplay.getRotation()).thenReturn(Surface.ROTATION_0, Surface.ROTATION_90, Surface.ROTATION_180, Surface.ROTATION_270);

        /* Sets values of fields for static classes. */
        Whitebox.setInternalState(mPackageInfo, "versionName", appVersion);
        Whitebox.setInternalState(mPackageInfo, "versionCode", Integer.parseInt(appBuild));
        Whitebox.setInternalState(Build.class, "MODEL", model);
        Whitebox.setInternalState(Build.class, "MANUFACTURER", oemName);
        Whitebox.setInternalState(Build.VERSION.class, "SDK_INT", osApiLevel);
        Whitebox.setInternalState(Build.class, "ID", osBuild);
        Whitebox.setInternalState(Build.VERSION.class, "RELEASE", osVersion);

        /* First call */
        Device device = DeviceInfoHelper.getDeviceInfo(mContext);

        /* Verify device information. */
        assertNull(device.getWrapperSdkName());
        assertNull(device.getWrapperSdkVersion());
        assertEquals(BuildConfig.VERSION_NAME, device.getSdkVersion());
        assertEquals(appVersion, device.getAppVersion());
        assertEquals(appBuild, device.getAppBuild());
        assertEquals(appNamespace, device.getAppNamespace());
        assertEquals(carrierCountry, device.getCarrierCountry());
        assertEquals(carrierName, device.getCarrierName());
        assertEquals(locale.toString(), device.getLocale());
        assertEquals(model, device.getModel());
        assertEquals(oemName, device.getOemName());
        assertEquals(osApiLevel, device.getOsApiLevel());
        assertEquals(osName, device.getOsName());
        assertEquals(osVersion, device.getOsVersion());
        assertEquals(osBuild, device.getOsBuild());
        assertEquals(screenSizeLandscape, device.getScreenSize());
        assertEquals(timeZoneOffset, device.getTimeZoneOffset());

        /* Verify screen size based on different orientations (Surface.ROTATION_90). */
        device = DeviceInfoHelper.getDeviceInfo(mContext);
        assertEquals(screenSizePortrait, device.getScreenSize());

        /* Verify screen size based on different orientations (Surface.ROTATION_180). */
        device = DeviceInfoHelper.getDeviceInfo(mContext);
        assertEquals(screenSizeLandscape, device.getScreenSize());

        /* Verify screen size based on different orientations (Surface.ROTATION_270). */
        device = DeviceInfoHelper.getDeviceInfo(mContext);
        assertEquals(screenSizePortrait, device.getScreenSize());

        /* Make sure screen size is verified for all orientations. */
        verify(mDisplay, times(4)).getRotation();

        /* Set wrapper sdk information. */
        WrapperSdk wrapperSdk = new WrapperSdk();
        wrapperSdk.setWrapperSdkVersion("1.2.3.4");
        wrapperSdk.setWrapperSdkName("ReactNative");
        wrapperSdk.setWrapperRuntimeVersion("4.13");
        wrapperSdk.setLiveUpdateReleaseLabel("2.0.3-beta2");
        wrapperSdk.setLiveUpdateDeploymentKey("staging");
        wrapperSdk.setLiveUpdatePackageHash("aa896f791b26a7f464c0f62b0ba69f2b");
        DeviceInfoHelper.setWrapperSdk(wrapperSdk);
        Device device2 = DeviceInfoHelper.getDeviceInfo(mContext);
        assertEquals(wrapperSdk.getWrapperSdkVersion(), device2.getWrapperSdkVersion());
        assertEquals(wrapperSdk.getWrapperSdkName(), device2.getWrapperSdkName());
        assertEquals(wrapperSdk.getWrapperRuntimeVersion(), device2.getWrapperRuntimeVersion());
        assertEquals(wrapperSdk.getLiveUpdateReleaseLabel(), device2.getLiveUpdateReleaseLabel());
        assertEquals(wrapperSdk.getLiveUpdateDeploymentKey(), device2.getLiveUpdateDeploymentKey());
        assertEquals(wrapperSdk.getLiveUpdatePackageHash(), device2.getLiveUpdatePackageHash());

        /* Check non wrapped sdk information are still generated correctly. */
        device2.setWrapperSdkVersion(null);
        device2.setWrapperSdkName(null);
        device2.setWrapperRuntimeVersion(null);
        device2.setLiveUpdateReleaseLabel(null);
        device2.setLiveUpdateDeploymentKey(null);
        device2.setLiveUpdatePackageHash(null);
        assertEquals(device, device2);

        /* Remove wrapper SDK information. */
        DeviceInfoHelper.setWrapperSdk(null);
        assertEquals(device, DeviceInfoHelper.getDeviceInfo(mContext));

        /* Verify the right API was called to get a screen size. */
        verify(mContext, atLeastOnce()).getSystemService(eq(Context.DISPLAY_SERVICE));
        verify(mContext, atLeastOnce()).getResources();
        verify(mResources, atLeastOnce()).getDisplayMetrics();
        //noinspection deprecation
        verify(mDisplay, never()).getSize(any(Point.class));
        verify(mContext, never()).getSystemService(eq(Context.WINDOW_SERVICE));
    }

    @Test(expected = DeviceInfoHelper.DeviceInfoException.class)
    public void getDeviceInfoWithException() throws PackageManager.NameNotFoundException, DeviceInfoHelper.DeviceInfoException {

        /* Delegates to mock instances. */
        //noinspection WrongConstant
        when(mPackageManager.getPackageInfo(anyString(), eq(0))).thenThrow(new PackageManager.NameNotFoundException());
        DeviceInfoHelper.getDeviceInfo(mContext);
    }

    @Test
    public void getDeviceInfoMissingCarrierInfo() throws DeviceInfoHelper.DeviceInfoException, PackageManager.NameNotFoundException {

        /* Mocking instances. */
        mockStatic(AppCenterLog.class);

        /* Delegates to mock instances. */
        when(mContext.getSystemService(Context.TELEPHONY_SERVICE)).thenThrow(new RuntimeException());
        when(mContext.getSystemService(Context.DISPLAY_SERVICE)).thenReturn(mDisplayManager);
        when(mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY)).thenReturn(mDisplay);
        when(mContext.getResources()).thenReturn(mResources);
        when(mResources.getDisplayMetrics()).thenReturn(mDisplayMetrics);
        //noinspection WrongConstant
        when(mPackageManager.getPackageInfo(anyString(), anyInt())).thenReturn(mPackageInfo);

        /* Verify. */
        Device device = DeviceInfoHelper.getDeviceInfo(mContext);
        assertNull(device.getCarrierCountry());
        assertNull(device.getCarrierName());
        verifyStatic(AppCenterLog.class);
        AppCenterLog.error(eq(AppCenter.LOG_TAG), anyString(), any(Exception.class));
    }

    @Test
    public void getDeviceInfoEmptyCarrierInfo() throws DeviceInfoHelper.DeviceInfoException, PackageManager.NameNotFoundException {

        /* Delegates to mock instances. */
        //noinspection WrongConstant
        when(mPackageManager.getPackageInfo(anyString(), anyInt())).thenReturn(mPackageInfo);
        when(mTelephonyManager.getNetworkCountryIso()).thenReturn("");
        when(mTelephonyManager.getNetworkOperatorName()).thenReturn("");
        when(mContext.getSystemService(Context.TELEPHONY_SERVICE)).thenReturn(mTelephonyManager);
        mockStatic(TextUtils.class);
        when(TextUtils.isEmpty(anyString())).thenReturn(true);

        /* Verify. */
        Device device = DeviceInfoHelper.getDeviceInfo(mContext);
        assertNull(device.getCarrierCountry());
        assertNull(device.getCarrierName());
    }

    @Test
    public void getDeviceInfoMissingScreenSize() throws DeviceInfoHelper.DeviceInfoException, PackageManager.NameNotFoundException {

        /* Mocking instances. */
        mockStatic(AppCenterLog.class);

        /* Delegates to mock instances. */
        when(mContext.getSystemService(Context.TELEPHONY_SERVICE)).thenReturn(mTelephonyManager);
        when(mContext.getSystemService(Context.DISPLAY_SERVICE)).thenThrow(new RuntimeException());
        //noinspection WrongConstant
        when(mPackageManager.getPackageInfo(anyString(), anyInt())).thenReturn(mPackageInfo);

        /* Verify. */
        Device device = DeviceInfoHelper.getDeviceInfo(mContext);
        assertNull(device.getScreenSize());
        verifyStatic(AppCenterLog.class);
        AppCenterLog.error(eq(AppCenter.LOG_TAG), anyString(), any(Exception.class));
    }

    @Test
    public void setCountryCode() throws DeviceInfoHelper.DeviceInfoException, PackageManager.NameNotFoundException {

        /* Mock system calls. */
        when(mPackageManager.getPackageInfo(anyString(), anyInt())).thenReturn(mPackageInfo);
        when(mTelephonyManager.getNetworkCountryIso()).thenReturn("");
        when(mTelephonyManager.getNetworkOperatorName()).thenReturn("");
        when(mContext.getSystemService(Context.TELEPHONY_SERVICE)).thenReturn(mTelephonyManager);

        /* Set invalid country code. */
        String expectedCountryCode = "aa";
        DeviceInfoHelper.setCountryCode(expectedCountryCode);

        /* Get device info. */
        Device device = DeviceInfoHelper.getDeviceInfo(mContext);
        assertEquals(device.getCarrierCountry(), expectedCountryCode);
    }

    @Test
    public void setNullCountryCode() throws DeviceInfoHelper.DeviceInfoException, PackageManager.NameNotFoundException {

        /* Mock system calls. */
        String expectedCountryCode = "aa";
        when(mPackageManager.getPackageInfo(anyString(), anyInt())).thenReturn(mPackageInfo);
        when(mTelephonyManager.getNetworkCountryIso()).thenReturn(expectedCountryCode);
        when(mTelephonyManager.getNetworkOperatorName()).thenReturn(expectedCountryCode);
        when(mContext.getSystemService(Context.TELEPHONY_SERVICE)).thenReturn(mTelephonyManager);

        /* Set country code. */
        DeviceInfoHelper.setCountryCode(null);

        /* Verify that system method was called. */
        Device device = DeviceInfoHelper.getDeviceInfo(mContext);
        verify(mTelephonyManager).getNetworkCountryIso();
        assertEquals(device.getCarrierCountry(), expectedCountryCode);
    }

    @Test
    public void setShortCountryCode() throws DeviceInfoHelper.DeviceInfoException, PackageManager.NameNotFoundException {
        verifyCountryCodeWithInvalidLength("a");
    }

    @Test
    public void setLongCountryCode() throws DeviceInfoHelper.DeviceInfoException, PackageManager.NameNotFoundException {
        verifyCountryCodeWithInvalidLength("abc");
    }

    public void verifyCountryCodeWithInvalidLength(String countryCode) throws DeviceInfoHelper.DeviceInfoException, PackageManager.NameNotFoundException {

        /* Mock system calls. */
        String expectedCountryCode = "aa";
        when(mPackageManager.getPackageInfo(anyString(), anyInt())).thenReturn(mPackageInfo);
        when(mTelephonyManager.getNetworkCountryIso()).thenReturn(expectedCountryCode);
        when(mTelephonyManager.getNetworkOperatorName()).thenReturn(expectedCountryCode);
        when(mContext.getSystemService(Context.TELEPHONY_SERVICE)).thenReturn(mTelephonyManager);

        /* Mocking instances. */
        mockStatic(AppCenterLog.class);

        /* Set invalid country code. */
        DeviceInfoHelper.setCountryCode(countryCode);

        /* Verify that log was called.*/
        verifyStatic(AppCenterLog.class);
        AppCenterLog.error(eq(AppCenter.LOG_TAG), eq("App Center accepts only the two-letter ISO country code."));

        /* Verify that invalid value wasn't set. */
        Device device = DeviceInfoHelper.getDeviceInfo(mContext);
        assertEquals(device.getCarrierCountry(), expectedCountryCode);
        verify(mTelephonyManager).getNetworkCountryIso();
    }
}
