package de.lukeslog.trashplay.cloudstorage;

import android.content.SharedPreferences;
import android.util.Log;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.DropboxAPI.Entry;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.exception.DropboxException;
import com.dropbox.client2.session.AccessTokenPair;
import com.dropbox.client2.session.AppKeyPair;
import com.dropbox.client2.session.Session;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import de.lukeslog.trashplay.R;
import de.lukeslog.trashplay.playlist.MusicCollectionManager;
import de.lukeslog.trashplay.service.TrashPlayService;
import de.lukeslog.trashplay.support.SettingsConstants;

public class DropBox extends StorageManager {

    public static final String STORAGE_TYPE = StorageManager.STORAGE_TYPE_DROPBOX;

    final static private Session.AccessType ACCESS_TYPE = Session.AccessType.DROPBOX;
    private static DropboxAPI<AndroidAuthSession> mDBApi;

    private static DropBox instance = null;

    private DropBox() {

    }

    public static synchronized DropBox getInstance() {
        if (instance == null) {
            instance = new DropBox();
        }
        return instance;
    }

    @Override
    protected void synchronizeRemoteFiles(String path) {
        synchronizeDropBox(path);
    }

    @Override
    protected List<String> searchForPlayListFolderInRemoteStorageImplementation() throws Exception {
        Log.d(TAG, "DropBoxsearch...");
        List<String> playListFolders = new ArrayList<String>();
        if (mDBApi != null) {
            Entry dropBoxDir1 = mDBApi.metadata("/", 0, null, true, null);
            if (dropBoxDir1.isDir) {
                for (Entry topFolder : dropBoxDir1.contents) {
                    Log.d(TAG, "" + topFolder.fileName());
                    if (topFolder.isDir) {
                        Entry folder = mDBApi.metadata("/" + topFolder.fileName(), 0, null, true, null);
                        if (isAPlayListFolder(folder)) {
                            playListFolders.add(topFolder.fileName());
                            getRadioStations(topFolder);
                        }
                    }
                }
            }
        }
        return playListFolders;
    }

    private void getRadioStations(Entry folder) throws DropboxException {
        Log.d(TAG, "checkForRadioStations");
        String stationNames = "";
        Entry radioFolder = mDBApi.metadata("/" + folder.fileName() + "/Radio", 0, null, true, null);
        Log.d(TAG, "x");
        List<Entry> possibleStations = radioFolder.contents;
        Log.d(TAG, "poosible Stations " + possibleStations.size());
        for (Entry possibleStation : possibleStations) {
            if (!possibleStation.isDir && possibleStation.fileName().endsWith(".station")) {
                Log.d(TAG, "FOUND A RADIO STATION!");
                stationNames = stationNames + possibleStation.fileName() + " ";
                Log.d(TAG, "Stationnames" + stationNames);
            }
        }
        Log.d(TAG, "Radiostations " + stationNames);
        SharedPreferences settings = TrashPlayService.getDefaultSettings();
        SharedPreferences.Editor edit = settings.edit();
        edit.putString("Radio_" + STORAGE_TYPE + "_" + folder.fileName(), stationNames);
        edit.commit();
    }

    public void updateRadioFileToRemoteStorage(String path) throws Exception {
        if (TrashPlayService.serviceRunning()) {
            if (mDBApi == null) {
                getDropBoxAPI();
            }
            if (mDBApi != null) {
                Log.d(TAG, "update radio thing");
                SharedPreferences settings = TrashPlayService.getDefaultSettings();
                String radioName = settings.getString(SettingsConstants.APP_SETTING_RADIO_NAME, "");
                radioName = radioName.replace("_", "");
                Log.d(TAG, "" + radioName);
                String radioSongs = settings.getString("radioSongs", "");
                if (!radioName.equals("")) {
                    Log.d(TAG, "updating " + radioName);

                    //create File
                    File tempfolder = new File(LOCAL_STORAGE + "/radio/");
                    tempfolder.mkdirs();
                    OutputStream out = null;
                    File file = new File(LOCAL_STORAGE + "/radio/tempradio.txt");

                    //create that file on the harddrive
                    Log.d(TAG, "have new file");
                    try {
                        out = new BufferedOutputStream(new FileOutputStream(file));
                    } finally {
                        if (out != null) {
                            out.close();
                        }
                    }

                    //write Data into File
                    Log.d(TAG, file.getAbsolutePath());
                    try {
                        BufferedWriter writer = new BufferedWriter(new FileWriter(file));
                        writer.write(radioSongs);
                        writer.close();
                    } catch (IOException e) {
                        Log.e("Exception", "File write failed: " + e.toString());
                    }

                    InputStream stream = new FileInputStream(file);

                    Log.d(TAG, path);
                    Entry folder = mDBApi.metadata("/" + path + "/Radio", 0, null, true, null);
                    List<Entry> files = folder.contents;
                    for (Entry entry : files) {
                        Log.d(TAG, entry.fileName());
                        if (entry.fileName().equals(radioName + ".station")) {
                            Log.d(TAG, "exists");

                            mDBApi.putFileOverwrite("/" + path + "/Radio/" + radioName + ".station", stream, file.length(), null);
                            return;
                        }
                    }
                    Log.d(TAG, "/" + path + "/Radio/" + radioName + ".station");
                    Log.d(TAG, "" + file.length());
                    mDBApi.putFile("/" + path + "/Radio/" + radioName + ".station", stream, file.length(), null, null);
                }
            }
        } else {
            Log.d(TAG, "Dropbox wasn't ready yet....");
        }
    }

    @Override
    protected void deleteOldRadioFiles() throws Exception {
        //      if(actualModificationTime.plusDays(1).isBefore(new DateTime()) && folder.fileName().endsWith(".station")) {
        //          deleteOldRadioFile();
        //      }
    }

    //TODO: Users should not be calling this directly.
    @Override
    public ArrayList<String> getFileNameListWithEndingsFromRemoteStorage(List<String> listOfAllowedFileEndings, String folderPath) throws Exception {
        Log.d(TAG, "getListOfFiles With Allowed Ending");
        ArrayList<String> fileList = new ArrayList<String>();
        String givenPath = "/" + folderPath;
        if (mDBApi != null) {
            getDropBoxAPI();
        }
        Entry folder = mDBApi.metadata(givenPath, 0, null, true, null);
        if (folder.isDir) {
            for (Entry file : folder.contents) {
                if (!file.isDir) {
                    String filename = file.fileName();

                    Log.d(TAG, file.path);
                    Log.d(TAG, file.modified);

                    boolean allowed = hasAllowedFileEnding(listOfAllowedFileEndings, filename);
                    if (allowed) {
                        Log.d(TAG, filename);
                        fileList.add(filename);
                    }
                }
            }
        }
        //TODO: Check for Christmas songs
        return fileList;
    }

    @Override
    public String downloadFileFromRemoteStorage(String path, String fileName) throws Exception {
        return downloadFileIfNewerVersionFromRemoteStorage(path, fileName, null);
    }

    public String downloadFileIfNewerVersionFromRemoteStorage(String path, String fileName, DateTime lastChange) throws Exception {
        if (lastChange == null) {
            lastChange = new DateTime();
            lastChange = lastChange.minusYears(100);
        }
        if (mDBApi == null) {
            getDropBoxAPI();
        }
        createTrashPlayFolderIFNotExisting();
        String filePath = "/" + path + "/" + fileName;
        Log.d(TAG, "Download " + filePath);
        Entry folder = mDBApi.metadata(filePath, 0, null, true, null);
        Log.d(TAG, "->");
        String modified = folder.modified;
        Log.d(TAG, "->2");
        DateTime actualModificationTime = getDateTimeFromDropBoxModificationTimeString(modified);
        Log.d(TAG, "->3");
        Log.d(TAG, actualModificationTime.getDayOfMonth() + "." + actualModificationTime.getMonthOfYear() + "." + actualModificationTime.getYear() + " " + actualModificationTime.getHourOfDay() + " " + actualModificationTime.getMinuteOfHour());
        if (actualModificationTime.isAfter(lastChange)) {
            Log.d(TAG, "->4");
            return downloadSpecificFileFromDropBox(fileName, filePath);
        }
        Log.d(TAG, "Return nothing");
        return "";
    }

    @Override
    public int getIconResourceNotConnected() {
        return R.drawable.dropbox;
    }

    @Override
    public int getIconResourceConnected() {
        return R.drawable.dropbox2;
    }

    @Override
    public String returnUniqueReadableName() {
        return "DropBox";
    }

    @Override
    public int menuItem() {
        return R.id.dropbox;
    }

    @Override
    public String getStorageType() {
        return STORAGE_TYPE;
    }

    @Override
    public void resetSyncInProgress() {
        Log.d(TAG, "RESET SYNC");
        syncInProgress = false;
    }

    private String downloadSpecificFileFromDropBox(String fileName, String filePath) throws DropboxException {
        Log.d(TAG, "try to download a file");
        FileOutputStream outputStream = null;
        File file = new File(LOCAL_STORAGE + fileName);
        Log.d(TAG, "have new file");
        try {
            outputStream = new FileOutputStream(file);
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "EXCEPTION");
        }

        Log.d(TAG, "filepath " + filePath);
        mDBApi.getFile(filePath, null, outputStream, null);
        Log.d(TAG, "I guess the download is done...");
        return file.getName();
    }

    protected DateTime getDateTimeFromDropBoxModificationTimeString(String modified) {
        modified = reformateDateString(modified);

        DateTimeFormatter formatter = DateTimeFormat.forPattern("dd MM yyyy HH:mm:ss");
        return formatter.parseDateTime(modified);
    }

    private String reformateDateString(String modifiedDate) {
        modifiedDate = modifiedDate.substring(5);
        modifiedDate = modifiedDate.replace("+0000", "");
        modifiedDate = modifiedDate.replace("Jan", "01").replace("Feb", "02").replace("Mar", "03").
                replace("Apr", "04").replace("May", "05").replace("Jun", "06").replace("Jul", "07").
                replace("Aug", "08").replace("Sep", "09").replace("Oct", "10").replace("Nov", "11").
                replace("Dec", "12");
        modifiedDate = modifiedDate.trim();
        return modifiedDate;
    }

    private void createTrashPlayFolderIFNotExisting() {
        File folder = new File(LOCAL_STORAGE);
        folder.mkdirs();
    }

    private boolean hasAllowedFileEnding(List<String> listOfAllowedFileEndings, String filename) {
        for (String ending : listOfAllowedFileEndings) {
            if (filename.endsWith(ending)) {
                return true;
            }
        }
        return false;
    }


    private boolean isAPlayListFolder(Entry folder) throws DropboxException {
        Log.d(TAG, folder.fileName() + " " + folder.isDir);
        List<Entry> folderContents = folder.contents;
        if (folderContents != null) {
            for (Entry content : folderContents) {
                if (!content.isDir) {
                    Log.d(TAG, ">" + content.fileName());
                    if (content.fileName().startsWith(".trashplay")) {
                        Log.d(TAG, "FOOOUND ONE!");
                        checkAndCreateNecessarySubFolders(folder);
                        return true;
                    }
                    if (content.fileName().matches("^[a-zA-Z].*$") || content.fileName().matches("^\\d.*$")) {
                        Log.d(TAG, "OVER!");
                        return false;
                    }
                }
            }
        }
        return false;
    }

    private void checkAndCreateNecessarySubFolders(Entry folder) throws DropboxException {
        boolean radioFolder = false;
        boolean christmasFolder = false;
        List<Entry> folderContents = folder.contents;
        if (folderContents != null) {
            for (Entry content : folderContents) {
                if (content.isDir) {
                    if (content.fileName().equals("Radio")) {
                        radioFolder = true;
                    }
                    if (content.fileName().equals("Christmas")) {
                        christmasFolder = true;
                    }
                }
            }
        }
        if (!radioFolder) {
            mDBApi.createFolder("/" + folder.fileName() + "/Radio");
        }
        if (!christmasFolder) {
            mDBApi.createFolder("/" + folder.fileName() + "/Christmas");
        }
    }

    private void synchronizeDropBox(String path) {
        File trashPlayMusicFolder = findOrCreateLocalTrashPlayMusicFolder();

    }

    public static DropboxAPI<AndroidAuthSession> getDropBoxAPI() {
        Log.d(TAG, "getDropBoxAPI");
        if (mDBApi == null) {
            getDropboxAPI();
            try {
                MusicCollectionManager.getInstance().syncRemoteStorageWithDevice(false);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return mDBApi;
    }

    private static DropboxAPI<AndroidAuthSession> getDropboxAPI() {
        SharedPreferences settings = null;
        if (TrashPlayService.serviceRunning()) {
            settings = TrashPlayService.getContext().settings;
            Log.d(TAG, "getDPAPI");
            AppKeyPair appKeys = new AppKeyPair(DropBoxConstants.appKey, DropBoxConstants.appSecret);
            AndroidAuthSession session = new AndroidAuthSession(appKeys, ACCESS_TYPE);
            mDBApi = new DropboxAPI<AndroidAuthSession>(session);
            String dbkey = settings.getString("DB_KEY", "");
            String dbsecret = settings.getString("DB_SECRET", "");
            if (dbkey.equals("")) return null;
            AccessTokenPair access = new AccessTokenPair(dbkey, dbsecret);
            mDBApi.getSession().setAccessTokenPair(access);
            return mDBApi;
        } else {
            Log.d(TAG, "TrashPlayService was not running.");
        }
        return null;
    }

    @Override
    public boolean isConnected() {
        if (TrashPlayService.serviceRunning()) {
            return !TrashPlayService.getContext().settings.getString("DB_KEY", "").equals("");
        } else {
            Log.d(TAG, "DropBoxisConnected() sez: TrashPlayService not Running");
        }
        return false;
    }

    public static String disconnect() {
        if (TrashPlayService.serviceRunning()) {
            SharedPreferences.Editor edit = TrashPlayService.getContext().settings.edit();
            edit.putString("DB_KEY", "");
            edit.putString("DB_SECRET", "");
            edit.commit();
            return "Disconnected from DropBox";
        }
        return "";
    }

    public static void storeKeys(String key, String secret) throws Exception {
        if (TrashPlayService.serviceRunning()) {

            Boolean previouslyunconnected = !DropBox.getInstance().isConnected();
            SharedPreferences.Editor edit = TrashPlayService.getContext().settings.edit();
            edit.putString("DB_KEY", key);
            edit.putString("DB_SECRET", secret);
            edit.commit();
            if (previouslyunconnected && DropBox.getInstance().isConnected()) { //TODO: This probably should not be in here...
                MusicCollectionManager.getInstance().syncRemoteStorageWithDevice(false);
            }
        }
    }

    public static void authenticate() {
        Log.d(TAG, "AUTHENTICATE");
        try {
            if (DropBox.getDropBoxAPI().getSession().authenticationSuccessful()) {
                try {
                    DropBox.getDropBoxAPI().getSession().finishAuthentication();
                    Log.d(TAG, "lol");
                    AccessTokenPair tokens = DropBox.getDropBoxAPI().getSession().getAccessTokenPair();
                    Log.d(TAG, "tada");
                    DropBox.storeKeys(tokens.key, tokens.secret);

                    CloudSynchronizationService.registerService(new DropBox());
                } catch (IllegalStateException e) {
                    Log.i("DbAuthLog", " Error authenticating", e);
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "probably a null pointer exception from the dropbox...");
        }
    }
}
