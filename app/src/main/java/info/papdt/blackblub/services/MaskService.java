package info.papdt.blackblub.services;

import android.Manifest;
import android.animation.Animator;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.Toast;

import info.papdt.blackblub.C;
import info.papdt.blackblub.R;
import info.papdt.blackblub.receiver.TileReceiver;
import info.papdt.blackblub.ui.LaunchActivity;
import info.papdt.blackblub.utils.NightScreenSettings;
import info.papdt.blackblub.utils.Utility;

public class MaskService extends Service {

	private WindowManager mWindowManager;
	private NotificationManager mNotificationManager;

	private Notification mNoti;

	private LinearLayout mLayout;
	private WindowManager.LayoutParams mLayoutParams;

	private NightScreenSettings mNightScreenSettings;
	private boolean enableOverlaySystem = false;

	private boolean isShowing = false;

	private static final int ANIMATE_DURATION_MILES = 250;
	private static final int NOTIFICATION_NO = 1024;
	private static int brightness = 50;
	private static int systemBrightness;
	private static int systemBrightnessMode;
	private ContentResolver mContentResolver;

	private static final String TAG = MaskService.class.getSimpleName();

	@Override
	public void onCreate() {
		super.onCreate();

		mWindowManager = (WindowManager) getApplication().getSystemService(Context.WINDOW_SERVICE);
		mNotificationManager = (NotificationManager) getApplication().getSystemService(Context.NOTIFICATION_SERVICE);

		mNightScreenSettings = NightScreenSettings.getInstance(getApplicationContext());
		enableOverlaySystem = mNightScreenSettings.getBoolean(NightScreenSettings.KEY_OVERLAY_SYSTEM, enableOverlaySystem);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		destroyMaskView();
		Intent broadcastIntent = new Intent();
		broadcastIntent.setAction(LaunchActivity.class.getCanonicalName());
		broadcastIntent.putExtra(C.EXTRA_EVENT_ID, C.EVENT_DESTORY_SERVICE);
		mNightScreenSettings.putBoolean(C.ACTION_PAUSE, false);
		sendBroadcast(broadcastIntent);
	}

	private void createMaskView() {
		mLayoutParams = new WindowManager.LayoutParams();
		mLayoutParams.type = !enableOverlaySystem ? WindowManager.LayoutParams.TYPE_TOAST : WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;
		mLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
				| WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
				| WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
		mLayoutParams.format = PixelFormat.TRANSPARENT;
		// TODO Automatically change by listening to rotating
		int maxSize = Math.max(Utility.getTrueScreenHeight(getApplicationContext()), Utility.getTrueScreenWidth(getApplicationContext()));
		mLayoutParams.height = maxSize + 200;
		mLayoutParams.width = maxSize + 200;
		mLayoutParams.gravity = Gravity.CENTER;
		mContentResolver = getContentResolver();

		setMinSystemBrightness();

		if (mLayout == null) {
			mLayout = new LinearLayout(this);
			mLayout.setLayoutParams(
					new LinearLayout.LayoutParams(
							ViewGroup.LayoutParams.MATCH_PARENT,
							ViewGroup.LayoutParams.MATCH_PARENT
					)
			);
			mLayout.setBackgroundColor(Color.BLACK);
			mLayout.setAlpha(0f);
		}

		try {
			mWindowManager.addView(mLayout, mLayoutParams);
		} catch (Exception e) {
			e.printStackTrace();
			Intent broadcastIntent = new Intent();
			broadcastIntent.setAction(LaunchActivity.class.getCanonicalName());
			broadcastIntent.putExtra(C.EXTRA_EVENT_ID, C.EVENT_CANNOT_START);
			sendBroadcast(broadcastIntent);
		}
	}

	private void setMinSystemBrightness() {
		try {
			systemBrightness = Settings.System.getInt(mContentResolver, Settings.System.SCREEN_BRIGHTNESS);
		} catch (Settings.SettingNotFoundException e) {
			Log.e(TAG, "Can not access to system brightness");
			e.printStackTrace();
			systemBrightness = 255/2;
		}
		try {
			systemBrightnessMode = Settings.System.getInt(mContentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE);
		} catch (Settings.SettingNotFoundException e) {
			Log.e(TAG, "Can not access to system brightness mode");
			e.printStackTrace();
			systemBrightnessMode = Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC;
		}

		try {
			Settings.System.putInt(mContentResolver, Settings.System.SCREEN_BRIGHTNESS, 0);
			Settings.System.putInt(mContentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
		} catch (Exception e){
			Log.e(TAG, "Can not write to system settings");
			e.printStackTrace();
			Toast.makeText(getApplicationContext(),R.string.write_settings_warning,Toast.LENGTH_LONG).show();
			requestWriteSettingsPermission();
		}
	}

	private void requestWriteSettingsPermission() {
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
			Intent enableIntent = new Intent(
                    Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:" + getPackageName()))
					.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(enableIntent);
		}
	}

	private void destroyMaskView() {
		isShowing = false;
		mContentResolver = getContentResolver();
		mNightScreenSettings.putBoolean(NightScreenSettings.KEY_ALIVE, false);
		try {
			Utility.createStatusBarTiles(this, false);
		} catch (Exception e) {
			e.printStackTrace();
		}
		restoreSystemBrightness();
		cancelNotification();
		if (mLayout != null) {
			mLayout.animate()
					.alpha(0f)
					.setDuration(ANIMATE_DURATION_MILES)
					.setListener(new Animator.AnimatorListener() {
						@Override
						public void onAnimationStart(Animator animator) {

						}

						@Override
						public void onAnimationEnd(Animator animator) {
							try {
								mWindowManager.removeViewImmediate(mLayout);
								mLayout = null;
							} catch (Exception e) {

							}
						}

						@Override
						public void onAnimationCancel(Animator animator) {

						}

						@Override
						public void onAnimationRepeat(Animator animator) {

						}
					});
		}
	}

	private void restoreSystemBrightness() {
		try {
			Settings.System.putInt(mContentResolver, Settings.System.SCREEN_BRIGHTNESS, systemBrightness);
			Settings.System.putInt(mContentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, systemBrightnessMode);
		} catch (Exception e){
			Log.e(TAG, "Error when setting system settings");
			e.printStackTrace();
			Toast.makeText(getApplicationContext(),R.string.write_settings_warning,Toast.LENGTH_LONG).show();
			requestWriteSettingsPermission();
		}
	}

	private void createNotification() {
		Log.i(TAG, "Create running notification");
		Intent openIntent = new Intent(this, LaunchActivity.class);
		Intent pauseIntent = new Intent();
		pauseIntent.setAction(TileReceiver.ACTION_UPDATE_STATUS);
		Log.i(TAG, "Create "+C.ACTION_PAUSE+" action");
		pauseIntent.putExtra(C.EXTRA_ACTION, C.ACTION_PAUSE);
		pauseIntent.putExtra(C.EXTRA_BRIGHTNESS, brightness);

		Notification.Action pauseAction = new Notification.Action(
				R.drawable.ic_wb_incandescent_black_24dp,
				getString(R.string.notification_action_turn_off),
				PendingIntent.getBroadcast(getBaseContext(), 0, pauseIntent, Intent.FILL_IN_DATA)
		);

		mNoti = new Notification.Builder(getApplicationContext())
				.setContentTitle(getString(R.string.notification_running_title))
				.setContentText(getString(R.string.notification_running_msg))
				.setSmallIcon(R.drawable.ic_brightness_2_white_36dp)
				.addAction(pauseAction)
				.setContentIntent(PendingIntent.getActivity(getApplicationContext(), 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT))
				.setAutoCancel(false)
				.setOngoing(true)
				.setOnlyAlertOnce(true)
				.setShowWhen(false)
				.build();

	}


	// implement pause notification
	private void createPauseNotification(){
		Log.i(TAG, "Create paused notification");
		Intent openIntent = new Intent(this, LaunchActivity.class);
		Intent resumeIntent = new Intent();
		resumeIntent.setAction(TileReceiver.ACTION_UPDATE_STATUS);
		resumeIntent.putExtra(C.EXTRA_ACTION, C.ACTION_START);
		resumeIntent.putExtra(C.EXTRA_BRIGHTNESS, brightness);

		Intent closeIntent = new Intent(this, MaskService.class);
		closeIntent.putExtra(C.EXTRA_ACTION, C.ACTION_STOP);

		Notification.Action resumeAction = new Notification.Action(R.drawable.ic_wb_incandescent_black_24dp,
				getString(R.string.notification_action_turn_on),
				PendingIntent.getBroadcast(getBaseContext(), 0, resumeIntent, PendingIntent.FLAG_UPDATE_CURRENT));

		mNoti = new Notification.Builder(getApplicationContext())
				.setContentTitle(getString(R.string.notification_paused_title))
				.setContentText(getString(R.string.notification_paused_msg))
				.setSmallIcon(R.drawable.ic_brightness_2_white_36dp)
				.addAction(resumeAction)
				.setContentIntent(PendingIntent.getActivity(getApplicationContext(), 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT))
				.setAutoCancel(true)
				.setOngoing(false)
				.setOnlyAlertOnce(true)
				.setShowWhen(false)
				.setDeleteIntent(PendingIntent.getService(getBaseContext(), 0, closeIntent, PendingIntent.FLAG_UPDATE_CURRENT))
				.build();
	}

	private void showNotification() {
		if (mNoti == null) {
			createNotification();
		}
		mNotificationManager.notify(NOTIFICATION_NO, mNoti);
	}

	private void showPausedNotification(){
		if (mNoti == null) {
			createPauseNotification();
		}
		mNotificationManager.notify(NOTIFICATION_NO, mNoti);
	}

	private void cancelNotification() {
		try {
			mNotificationManager.cancel(NOTIFICATION_NO);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int arg) {
		if (intent != null) {
			String action = intent.getStringExtra(C.EXTRA_ACTION);
			brightness = intent.getIntExtra(C.EXTRA_BRIGHTNESS, 0);
			float targetAlpha = (100 - brightness) * 0.01f;
			boolean temp = intent.getBooleanExtra(C.EXTRA_USE_OVERLAY_SYSTEM, enableOverlaySystem);

			switch (action) {
				case C.ACTION_START:
					Log.i(TAG, "Start Mask");
					//Todo: I request author to check whether it is safe or not
					if(mLayout == null){
						createMaskView();
					}

					if (temp != enableOverlaySystem) {
						mLayoutParams.type = !enableOverlaySystem ? WindowManager.LayoutParams.TYPE_TOAST : WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;
						enableOverlaySystem = temp;
					}
					isShowing = true;
					mNightScreenSettings.putBoolean(NightScreenSettings.KEY_ALIVE, true);
					mNightScreenSettings.putBoolean(C.ACTION_PAUSE, false);
					createNotification();
					startForeground(NOTIFICATION_NO, mNoti);
					try {
						mWindowManager.updateViewLayout(mLayout, mLayoutParams);
						mLayout.animate()
								.alpha(targetAlpha)
								.setDuration(ANIMATE_DURATION_MILES)
								.start();
						Utility.createStatusBarTiles(this, true);
					} catch (Exception e) {
						// do nothing....
						e.printStackTrace();
					}
					Log.i(TAG, "Set alpha:" + String.valueOf(100 - intent.getIntExtra(C.EXTRA_BRIGHTNESS, 0)));
					break;
				case C.ACTION_PAUSE:
					Log.i(TAG, "Pause Mask");
					stopForeground(true);
					destroyMaskView();
					createPauseNotification();
					showPausedNotification();
					isShowing = false;
					mNightScreenSettings.putBoolean(NightScreenSettings.KEY_ALIVE, false);
					mNightScreenSettings.putBoolean(C.ACTION_PAUSE, true);
					break;
				case C.ACTION_STOP:
					Log.i(TAG, "Stop Mask");
					isShowing = false;
					stopSelf();
					break;
				case C.ACTION_UPDATE:
					Log.i(TAG, "Update Mask");
					isShowing = true;
					if (temp != enableOverlaySystem) {
						mLayoutParams.type = !enableOverlaySystem ? WindowManager.LayoutParams.TYPE_TOAST : WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;
						enableOverlaySystem = temp;
						try {
							mWindowManager.updateViewLayout(mLayout, mLayoutParams);
						} catch (Exception e) {
							// do nothing....
						}
					}

					mNightScreenSettings.putBoolean(NightScreenSettings.KEY_ALIVE, true);
					if (Math.abs(targetAlpha - mLayout.getAlpha()) < 0.1f) {
						mLayout.setAlpha(targetAlpha);
					} else {
						mLayout.animate()
								.alpha(targetAlpha)
								.setDuration(100)
								.start();
					}
					Log.i(TAG, "Set alpha:" + String.valueOf(100 - intent.getIntExtra(C.EXTRA_BRIGHTNESS, 0)));
					break;
				case C.ACTION_CHECK:
					Intent broadcastIntent = new Intent();
					broadcastIntent.setAction(LaunchActivity.class.getCanonicalName());
					broadcastIntent.putExtra(C.EXTRA_EVENT_ID, C.EVENT_CHECK);
					broadcastIntent.putExtra("isShowing", isShowing);
					sendBroadcast(broadcastIntent);
					break;
			}
		}
		return START_STICKY;
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	@Override
	public boolean onUnbind(Intent intent) {
		return super.onUnbind(intent);
	}

	private MaskBinder mBinder = new MaskBinder();

	public class MaskBinder extends Binder {

		public boolean isMaskShowing() {
			return isShowing;
		}

	}

}
