/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

 package com.android.car.provision;

 import static android.app.Activity.RESULT_CANCELED;
 import static android.app.Activity.RESULT_FIRST_USER;
 import static android.app.Activity.RESULT_OK;
 import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE;
 import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME;
 import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION;
 import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM;
 import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_TRIGGER;
 import static android.app.admin.DevicePolicyManager.PROVISIONING_TRIGGER_QR_CODE;
 import static android.car.settings.CarSettings.Secure.KEY_ENABLE_INITIAL_NOTICE_SCREEN_TO_USER;
 import static android.car.settings.CarSettings.Secure.KEY_SETUP_WIZARD_IN_PROGRESS;

 import android.app.Activity;
 import android.app.AlertDialog;
 import android.app.Notification;
 import android.app.NotificationChannel;
 import android.app.NotificationManager;
 import android.app.admin.DevicePolicyManager;
 import android.content.BroadcastReceiver;
 import android.content.ComponentName;
 import android.content.Context;
 import android.content.Intent;
 import android.content.IntentFilter;
 import android.content.pm.PackageInfo;
 import android.content.pm.PackageManager;
 import android.os.Bundle;
 import android.os.UserHandle;
 import android.os.UserManager;
 import android.provider.Settings;
 import android.provider.Settings.SettingNotFoundException;
 import android.util.Log;
 import android.view.View;
 import android.widget.ArrayAdapter;
 import android.widget.Button;
 import android.widget.Spinner;
 import android.widget.TextView;

 import com.android.car.setupwizardlib.util.CarDrivingStateMonitor;

 import java.io.FileDescriptor;
 import java.io.PrintWriter;
 import java.util.ArrayList;
 import java.util.List;

 /**
  * Reference implementeation for a Car SetupWizard.
  *
  * <p>Features:
  *
  * <ul>
  *   <li>Shows UI where user can confirm setup.
  *   <li>Listen to UX restriction events, so it exits setup when the car moves.
  *   <li>Add option to setup managed-provisioning mode.
  *   <li>Sets car-specific properties.
  * </ul>
  */
 public final class DefaultActivity extends Activity {

     static final String TAG = "CarProvision";

     // TODO(b/170333009): copied from ManagedProvisioning app, as they're hidden;
     private static final String PROVISION_FINALIZATION_INSIDE_SUW =
             "android.app.action.PROVISION_FINALIZATION_INSIDE_SUW";
     private static final int RESULT_CODE_PROFILE_OWNER_SET = 122;
     private static final int RESULT_CODE_DEVICE_OWNER_SET = 123;


     private static final int REQUEST_CODE_STEP1 = 42;
     private static final int REQUEST_CODE_STEP2_PO = 43;
     private static final int REQUEST_CODE_STEP2_DO = 44;

     private static final int NOTIFICATION_ID = 108;
     private static final String IMPORTANCE_DEFAULT_ID = "importance_default";

     private static final List<DpcInfo> sSupportedDpcApps = new ArrayList<>(2);

     private static final String TEST_DPC_NAME = "TestDPC (downloadable)";
     private static final String TEST_DPC_PACKAGE = "com.afwsamples.testdpc";
     private static final String TEST_DPC_LEGACY_ACTIVITY = TEST_DPC_PACKAGE
             + ".SetupManagementLaunchActivity";
     private static final String TEST_DPC_RECEIVER = TEST_DPC_PACKAGE
             + ".DeviceAdminReceiver";
     private static final String LOCAL_TEST_DPC_NAME = "TestDPC (local only)";

     static {
         DpcInfo testDpc = new DpcInfo(TEST_DPC_NAME,
                 TEST_DPC_PACKAGE,
                 TEST_DPC_LEGACY_ACTIVITY,
                 TEST_DPC_RECEIVER,
                 "gJD2YwtOiWJHkSMkkIfLRlj-quNqG1fb6v100QmzM9w=",
                 "https://testdpc-latest-apk.appspot.com/preview");
         // Locally-built version of the TestDPC
         DpcInfo localTestDpc = new DpcInfo(LOCAL_TEST_DPC_NAME,
                 TEST_DPC_PACKAGE,
                 TEST_DPC_LEGACY_ACTIVITY,
                 TEST_DPC_RECEIVER,
                 /* checkSum= */ null,
                 /* downloadUrl = */ null);
         sSupportedDpcApps.add(testDpc);
         sSupportedDpcApps.add(localTestDpc);
     }

     private CarDrivingStateMonitor mCarDrivingStateMonitor;

     private TextView mErrorsTextView;
     private Button mFinishSetupButton;
     private Button mFactoryResetButton;
     private Spinner mDpcAppsSpinner;
     private Button mLegacyProvisioningWorkflowButton;
     private Button mProvisioningWorkflowButton;

     private final BroadcastReceiver mDrivingStateExitReceiver = new BroadcastReceiver() {
         @Override
         public void onReceive(Context context, Intent intent) {
             Log.d(TAG, "onReceive(): " + intent);
             exitSetup();
         }
     };

     @Override
     protected void onCreate(Bundle icicle) {
         super.onCreate(icicle);

         int userId = getUserId();
         Log.i(TAG, "onCreate() for user " + userId + " Intent: " + getIntent());

         if (userId == UserHandle.USER_SYSTEM && UserManager.isHeadlessSystemUserMode()) {
             // System user will be provisioned together with the first non-system user
             Log.i(TAG, "onCreate(): skipping setup on headless system user");
             disableSelfAndFinish();
             return;
         }

         finishSetup();
     }

     private boolean checkDpcAppExists(String dpcApp) {
         if (!checkAppExists(dpcApp, UserHandle.USER_SYSTEM)) return false;
         if (!checkAppExists(dpcApp, getUserId())) return false;
         return true;
     }

     private boolean checkAppExists(String app, int userId) {
         Log.d(TAG, "Checking if " + app + " exits for user " + userId);
         try {
             PackageInfo info = getPackageManager().getPackageInfoAsUser(app, /* flags= */ 0,
                     userId);
             if (info == null) {
                 Log.i(TAG, "No app " + app + " for user " + userId);
                 return false;
             }
             Log.d(TAG, "Found it: " + info);
             return true;
         } catch (PackageManager.NameNotFoundException e) {
             return false;
         } catch (Exception e) {
             Log.e(TAG, "Error checking if " + app + " exists for user " + userId, e);
             return false;
         }
     }

     private void finishSetup() {
         Log.i(TAG, "finishing setup for user " + getUserId());
         provisionUserAndDevice();
         disableSelfAndFinish();
     }

     private void provisionUserAndDevice() {
         Log.d(TAG, "setting Settings properties");
         // Add a persistent setting to allow other apps to know the device has been provisioned.
         if (!isDeviceProvisioned()) {
             Settings.Global.putInt(getContentResolver(), Settings.Global.DEVICE_PROVISIONED, 1);
         }

         maybeMarkSystemUserSetupComplete();
         Log.v(TAG, "Marking USER_SETUP_COMPLETE for user " + getUserId());
         markUserSetupComplete(this);

         // Set car-specific properties
         setCarSetupInProgress(false);
         Settings.Secure.putInt(getContentResolver(), KEY_ENABLE_INITIAL_NOTICE_SCREEN_TO_USER, 0);
     }

     private boolean isDeviceProvisioned() {
         try {
             return Settings.Global.getInt(getContentResolver(),
                     Settings.Global.DEVICE_PROVISIONED) == 1;
         } catch (SettingNotFoundException e) {
             Log.wtf(TAG, "DEVICE_PROVISIONED is not found.");
             return false;
         }
     }

     private boolean isUserSetupComplete(Context context) {
         return Settings.Secure.getInt(context.getContentResolver(),
                 Settings.Secure.USER_SETUP_COMPLETE, /* default= */ 0) == 1;
     }

     private void maybeMarkSystemUserSetupComplete() {
         Context systemUserContext = getApplicationContext().createContextAsUser(
                 UserHandle.SYSTEM, /* flags= */ 0);
         if (!isUserSetupComplete(systemUserContext) && getUserId() != UserHandle.USER_SYSTEM
                 && UserManager.isHeadlessSystemUserMode()) {
             Log.v(TAG, "Marking USER_SETUP_COMPLETE for system user");
             markUserSetupComplete(systemUserContext);
         }
     }

     private void setCarSetupInProgress(boolean inProgress) {
         Settings.Secure.putInt(getContentResolver(), KEY_SETUP_WIZARD_IN_PROGRESS,
                 inProgress ? 1 : 0);
     }

     private void markUserSetupComplete(Context context) {
         Settings.Secure.putInt(context.getContentResolver(),
                 Settings.Secure.USER_SETUP_COMPLETE, 1);
     }

     private void exitSetup() {
         Log.d(TAG, "exiting setup early for user " + getUserId());
         provisionUserAndDevice();
         notifySetupExited();
         disableSelfAndFinish();
     }

     private void notifySetupExited() {
         Log.d(TAG, "Sending exited setup notification");

         NotificationManager notificationMgr = getSystemService(NotificationManager.class);
         notificationMgr.createNotificationChannel(new NotificationChannel(
                 IMPORTANCE_DEFAULT_ID, "Importance Default",
                 NotificationManager.IMPORTANCE_DEFAULT));
         Notification notification = new Notification
                 .Builder(this, IMPORTANCE_DEFAULT_ID)
                 .setContentTitle(getString(R.string.exited_setup_title))
                 .setContentText(getString(R.string.exited_setup_content))
                 .setCategory(Notification.CATEGORY_CAR_INFORMATION)
                 .setSmallIcon(R.drawable.car_ic_mode)
                 .build();
         notificationMgr.notify(NOTIFICATION_ID, notification);
     }

     private DpcInfo getSelectedDpcInfo() {
         return sSupportedDpcApps.get(mDpcAppsSpinner.getSelectedItemPosition());
     }

     private void launchLegacyProvisioningWorkflow() {
         DpcInfo dpcInfo = getSelectedDpcInfo();
         if (!checkDpcAppExists(dpcInfo.packageName)) {
             showErrorMessage("Cannot provision device because " + dpcInfo.packageName
                     + " is not available.\n Make sure it's installed for both user 0 and user "
                     + getUserId());
             return;
         }

         Intent intent = new Intent();
         intent.setComponent(dpcInfo.getLegacyActivityComponentName());
         Log.i(TAG, "Provisioning device using LEGACY workflow while running as user "
                 + getUserId() + ". DPC: " + dpcInfo + ". Intent: " + intent);
         startActivityForResult(intent, REQUEST_CODE_STEP1);
     }

     private void launchProvisioningWorkflow() {
         DpcInfo dpcInfo = getSelectedDpcInfo();

         Intent intent = new Intent(ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE);
         // TODO(b/170333009): add a UI with options for EXTRA_PROVISIONING_TRIGGER.
         intent.putExtra(EXTRA_PROVISIONING_TRIGGER, PROVISIONING_TRIGGER_QR_CODE);
         intent.putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                 dpcInfo.getAdminReceiverComponentName());
         if (dpcInfo.checkSum != null) {
             intent.putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM, dpcInfo.checkSum);
         }
         if (dpcInfo.downloadUrl != null) {
             intent.putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION,
                     dpcInfo.downloadUrl);
         }

         Log.i(TAG, "Provisioning device using NEW workflow while running as user "
                 + getUserId() + ". DPC: " + dpcInfo + ". Intent: " + intent);

         startActivityForResult(intent, REQUEST_CODE_STEP1);
     }

     private void disableSelfAndFinish() {
         Log.d(TAG, "disableSelfAndFinish()");

         // Remove this activity from the package manager.
         PackageManager pm = getPackageManager();
         ComponentName name = new ComponentName(this, DefaultActivity.class);
         Log.i(TAG, "Disabling itself (" + name + ") for user " + getUserId());
         pm.setComponentEnabledSetting(name, PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                 PackageManager.DONT_KILL_APP);

         finish();
     }

     @Override
     protected void onActivityResult(int requestCode, int resultCode, Intent data) {
         Log.d(TAG, "onActivityResult(): request=" + requestCode + ", result="
                 + resultCodeToString(resultCode) + ", data=" + data);

         switch (requestCode) {
             case REQUEST_CODE_STEP1:
                 onProvisioningStep1Result(resultCode);
                 break;
             case REQUEST_CODE_STEP2_PO:
             case REQUEST_CODE_STEP2_DO:
                 onProvisioningStep2Result(requestCode, resultCode);
                 break;
             default:
                 showErrorMessage("onActivityResult(): invalid request code " + requestCode);

         }
     }

     private void onProvisioningStep1Result(int resultCode) {
         int requestCodeStep2;
         switch (resultCode) {
             case RESULT_CODE_PROFILE_OWNER_SET:
                 requestCodeStep2 = REQUEST_CODE_STEP2_PO;
                 break;
             case RESULT_CODE_DEVICE_OWNER_SET:
                 requestCodeStep2 = REQUEST_CODE_STEP2_DO;
                 break;
             default:
                 showErrorMessage("onProvisioningStep1Result(): invalid result code "
                         + resultCodeToString(resultCode)
                         + getManagedProvisioningFailureWarning());
                 return;
         }
         Intent intent = new Intent(PROVISION_FINALIZATION_INSIDE_SUW)
                 .addCategory(Intent.CATEGORY_DEFAULT);
         Log.i(TAG, "Finalizing DPC with " + intent);
         startActivityForResult(intent, requestCodeStep2);
     }

     private String getManagedProvisioningFailureWarning() {
         return "\n\n" + getString(R.string.provision_failure_message);
     }

     private void onProvisioningStep2Result(int requestCode, int resultCode) {
         boolean doMode = requestCode == REQUEST_CODE_STEP2_DO;
         if (resultCode != RESULT_OK) {
             StringBuilder message = new StringBuilder("onProvisioningStep2Result(): "
                     + "invalid result code ").append(resultCode);
             if (doMode) {
                 message.append(getManagedProvisioningFailureWarning());
             }
             showErrorMessage(message.toString());
             return;
         }

         Log.i(TAG, (doMode ? "Device owner" : "Profile owner") + " mode provisioned!");
         finishSetup();
     }

     private static String resultCodeToString(int resultCode)  {
         StringBuilder result = new StringBuilder();
         switch (resultCode) {
             case RESULT_OK:
                 result.append("RESULT_OK");
                 break;
             case RESULT_CANCELED:
                 result.append("RESULT_CANCELED");
                 break;
             case RESULT_FIRST_USER:
                 result.append("RESULT_FIRST_USER");
                 break;
             case RESULT_CODE_PROFILE_OWNER_SET:
                 result.append("RESULT_CODE_PROFILE_OWNER_SET");
                 break;
             case RESULT_CODE_DEVICE_OWNER_SET:
                 result.append("RESULT_CODE_DEVICE_OWNER_SET");
                 break;
             default:
                 result.append("UNKNOWN_CODE");
         }
         return result.append('(').append(resultCode).append(')').toString();
     }

     private void showErrorMessage(String message) {
         Log.e(TAG, "Error: " + message);
         mErrorsTextView.setText(message);
         findViewById(R.id.errors_container).setVisibility(View.VISIBLE);
     }
 }
