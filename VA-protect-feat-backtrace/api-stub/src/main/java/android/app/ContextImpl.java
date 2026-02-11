package android.app;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.view.Display;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

class ContextImpl extends Context {
    @Override
    public AssetManager getAssets() {
        throw new RuntimeException("STUB");
    }

    @Override
    public Resources getResources() {
        throw new RuntimeException("STUB");
    }

    @Override
    public PackageManager getPackageManager() {
        throw new RuntimeException("STUB");
    }

    @Override
    public ContentResolver getContentResolver() {
        throw new RuntimeException("STUB");
    }

    @Override
    public Looper getMainLooper() {
        throw new RuntimeException("STUB");
    }

    @Override
    public Context getApplicationContext() {
        throw new RuntimeException("STUB");
    }

    @Override
    public void setTheme(int resid) {

    }

    @Override
    public Resources.Theme getTheme() {
        throw new RuntimeException("STUB");
    }

    @Override
    public ClassLoader getClassLoader() {
        throw new RuntimeException("STUB");
    }

    @Override
    public String getPackageName() {
        throw new RuntimeException("STUB");
    }

    @Override
    public ApplicationInfo getApplicationInfo() {
        throw new RuntimeException("STUB");
    }

    @Override
    public String getPackageResourcePath() {
        throw new RuntimeException("STUB");
    }

    @Override
    public String getPackageCodePath() {
        throw new RuntimeException("STUB");
    }

    @Override
    public SharedPreferences getSharedPreferences(String name, int mode) {
        throw new RuntimeException("STUB");
    }

    @Override
    public boolean moveSharedPreferencesFrom(Context sourceContext, String name) {
        throw new RuntimeException("STUB");
    }

    @Override
    public boolean deleteSharedPreferences(String name) {
        throw new RuntimeException("STUB");
    }

    @Override
    public FileInputStream openFileInput(String name) throws FileNotFoundException {
        throw new RuntimeException("STUB");
    }

    @Override
    public FileOutputStream openFileOutput(String name, int mode) throws FileNotFoundException {
        throw new RuntimeException("STUB");
    }

    @Override
    public boolean deleteFile(String name) {
        throw new RuntimeException("STUB");
    }

    @Override
    public File getFileStreamPath(String name) {
        throw new RuntimeException("STUB");
    }

    @Override
    public File getDataDir() {
        throw new RuntimeException("STUB");
    }

    @Override
    public File getFilesDir() {
        throw new RuntimeException("STUB");
    }

    @Override
    public File getNoBackupFilesDir() {
        throw new RuntimeException("STUB");
    }

    @Override
    public File getExternalFilesDir(String type) {
        throw new RuntimeException("STUB");
    }

    @Override
    public File[] getExternalFilesDirs(String type) {
        throw new RuntimeException("STUB");
    }

    @Override
    public File getObbDir() {
        throw new RuntimeException("STUB");
    }

    @Override
    public File[] getObbDirs() {
        throw new RuntimeException("STUB");
    }

    @Override
    public File getCacheDir() {
        throw new RuntimeException("STUB");
    }

    @Override
    public File getCodeCacheDir() {
        throw new RuntimeException("STUB");
    }

    @Override
    public File getExternalCacheDir() {
        throw new RuntimeException("STUB");
    }

    @Override
    public File[] getExternalCacheDirs() {
        throw new RuntimeException("STUB");
    }

    @Override
    public File[] getExternalMediaDirs() {
        throw new RuntimeException("STUB");
    }

    @Override
    public String[] fileList() {
        throw new RuntimeException("STUB");
    }

    @Override
    public File getDir(String name, int mode) {
        throw new RuntimeException("STUB");
    }

    @Override
    public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory) {
        throw new RuntimeException("STUB");
    }

    @Override
    public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory, DatabaseErrorHandler errorHandler) {
        throw new RuntimeException("STUB");
    }

    @Override
    public boolean moveDatabaseFrom(Context sourceContext, String name) {
        throw new RuntimeException("STUB");
    }

    @Override
    public boolean deleteDatabase(String name) {
        throw new RuntimeException("STUB");
    }

    @Override
    public File getDatabasePath(String name) {
        throw new RuntimeException("STUB");
    }

    @Override
    public String[] databaseList() {
        throw new RuntimeException("STUB");
    }

    @Override
    public Drawable getWallpaper() {
        throw new RuntimeException("STUB");
    }

    @Override
    public Drawable peekWallpaper() {
        throw new RuntimeException("STUB");
    }

    @Override
    public int getWallpaperDesiredMinimumWidth() {
        throw new RuntimeException("STUB");
    }

    @Override
    public int getWallpaperDesiredMinimumHeight() {
        throw new RuntimeException("STUB");
    }

    @Override
    public void setWallpaper(Bitmap bitmap) throws IOException {

    }

    @Override
    public void setWallpaper(InputStream data) throws IOException {

    }

    @Override
    public void clearWallpaper() throws IOException {

    }

    @Override
    public void startActivity(Intent intent) {

    }

    @Override
    public void startActivity(Intent intent, Bundle options) {

    }

    @Override
    public void startActivities(Intent[] intents) {

    }

    @Override
    public void startActivities(Intent[] intents, Bundle options) {

    }

    @Override
    public void startIntentSender(IntentSender intent, Intent fillInIntent, int flagsMask, int flagsValues, int extraFlags) throws IntentSender.SendIntentException {

    }

    @Override
    public void startIntentSender(IntentSender intent, Intent fillInIntent, int flagsMask, int flagsValues, int extraFlags, Bundle options) throws IntentSender.SendIntentException {

    }

    @Override
    public void sendBroadcast(Intent intent) {

    }

    @Override
    public void sendBroadcast(Intent intent, String receiverPermission) {

    }

    @Override
    public void sendOrderedBroadcast(Intent intent, String receiverPermission) {

    }

    @Override
    public void sendOrderedBroadcast(Intent intent, String receiverPermission, BroadcastReceiver resultReceiver, Handler scheduler, int initialCode, String initialData, Bundle initialExtras) {

    }

    @Override
    public void sendBroadcastAsUser(Intent intent, UserHandle user) {

    }

    @Override
    public void sendBroadcastAsUser(Intent intent, UserHandle user, String receiverPermission) {

    }

    @Override
    public void sendOrderedBroadcastAsUser(Intent intent, UserHandle user, String receiverPermission, BroadcastReceiver resultReceiver, Handler scheduler, int initialCode, String initialData, Bundle initialExtras) {

    }

    @Override
    public void sendStickyBroadcast(Intent intent) {

    }

    @Override
    public void sendStickyOrderedBroadcast(Intent intent, BroadcastReceiver resultReceiver, Handler scheduler, int initialCode, String initialData, Bundle initialExtras) {

    }

    @Override
    public void removeStickyBroadcast(Intent intent) {

    }

    @Override
    public void sendStickyBroadcastAsUser(Intent intent, UserHandle user) {

    }

    @Override
    public void sendStickyOrderedBroadcastAsUser(Intent intent, UserHandle user, BroadcastReceiver resultReceiver, Handler scheduler, int initialCode, String initialData, Bundle initialExtras) {

    }

    @Override
    public void removeStickyBroadcastAsUser(Intent intent, UserHandle user) {

    }

    @Override
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
        throw new RuntimeException("STUB");
    }

    @Override
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter, int flags) {
        throw new RuntimeException("STUB");
    }

    @Override
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter, String broadcastPermission, Handler scheduler) {
        throw new RuntimeException("STUB");
    }

    @Override
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter, String broadcastPermission, Handler scheduler, int flags) {
        throw new RuntimeException("STUB");
    }

    @Override
    public void unregisterReceiver(BroadcastReceiver receiver) {

    }

    @Override
    public ComponentName startService(Intent service) {
        throw new RuntimeException("STUB");
    }

    @Override
    public ComponentName startForegroundService(Intent service) {
        throw new RuntimeException("STUB");
    }

    @Override
    public boolean stopService(Intent service) {
        throw new RuntimeException("STUB");
    }

    @Override
    public boolean bindService(Intent service, ServiceConnection conn, int flags) {
        throw new RuntimeException("STUB");
    }

    @Override
    public void unbindService(ServiceConnection conn) {

    }

    @Override
    public boolean startInstrumentation(ComponentName className, String profileFile, Bundle arguments) {
        throw new RuntimeException("STUB");
    }

    @Override
    public Object getSystemService(String name) {
        throw new RuntimeException("STUB");
    }

    @Override
    public String getSystemServiceName(Class<?> serviceClass) {
        throw new RuntimeException("STUB");
    }

    @Override
    public int checkPermission(String permission, int pid, int uid) {
        throw new RuntimeException("STUB");
    }

    @Override
    public int checkCallingPermission(String permission) {
        throw new RuntimeException("STUB");
    }

    @Override
    public int checkCallingOrSelfPermission(String permission) {
        throw new RuntimeException("STUB");
    }

    @Override
    public int checkSelfPermission(String permission) {
        throw new RuntimeException("STUB");
    }

    @Override
    public void enforcePermission(String permission, int pid, int uid, String message) {

    }

    @Override
    public void enforceCallingPermission(String permission, String message) {

    }

    @Override
    public void enforceCallingOrSelfPermission(String permission, String message) {

    }

    @Override
    public void grantUriPermission(String toPackage, Uri uri, int modeFlags) {

    }

    @Override
    public void revokeUriPermission(Uri uri, int modeFlags) {

    }

    @Override
    public void revokeUriPermission(String toPackage, Uri uri, int modeFlags) {

    }

    @Override
    public int checkUriPermission(Uri uri, int pid, int uid, int modeFlags) {
        throw new RuntimeException("STUB");
    }

    @Override
    public int checkCallingUriPermission(Uri uri, int modeFlags) {
        throw new RuntimeException("STUB");
    }

    @Override
    public int checkCallingOrSelfUriPermission(Uri uri, int modeFlags) {
        throw new RuntimeException("STUB");
    }

    @Override
    public int checkUriPermission(Uri uri, String readPermission, String writePermission, int pid, int uid, int modeFlags) {
        throw new RuntimeException("STUB");
    }

    @Override
    public void enforceUriPermission(Uri uri, int pid, int uid, int modeFlags, String message) {

    }

    @Override
    public void enforceCallingUriPermission(Uri uri, int modeFlags, String message) {

    }

    @Override
    public void enforceCallingOrSelfUriPermission(Uri uri, int modeFlags, String message) {

    }

    @Override
    public void enforceUriPermission(Uri uri, String readPermission, String writePermission, int pid, int uid, int modeFlags, String message) {

    }

    @Override
    public Context createPackageContext(String packageName, int flags) throws PackageManager.NameNotFoundException {
        throw new RuntimeException("STUB");
    }

    @Override
    public Context createContextForSplit(String splitName) throws PackageManager.NameNotFoundException {
        throw new RuntimeException("STUB");
    }

    @Override
    public Context createConfigurationContext(Configuration overrideConfiguration) {
        throw new RuntimeException("STUB");
    }

    @Override
    public Context createDisplayContext(Display display) {
        throw new RuntimeException("STUB");
    }

    @Override
    public Context createDeviceProtectedStorageContext() {
        throw new RuntimeException("STUB");
    }

    @Override
    public boolean isDeviceProtectedStorage() {
        throw new RuntimeException("STUB");
    }
}
