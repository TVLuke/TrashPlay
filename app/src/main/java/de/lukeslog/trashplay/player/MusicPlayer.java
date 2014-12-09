package de.lukeslog.trashplay.player;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import org.farng.mp3.MP3File;
import org.farng.mp3.TagException;
import org.farng.mp3.id3.ID3v1;

import java.io.File;
import java.io.IOException;

import de.lukeslog.trashplay.cloudstorage.StorageManager;
import de.lukeslog.trashplay.constants.TrashPlayConstants;
import de.lukeslog.trashplay.lastfm.PersonalLastFM;
import de.lukeslog.trashplay.lastfm.TrashPlayLastFM;
import de.lukeslog.trashplay.playlist.MusicCollectionManager;
import de.lukeslog.trashplay.playlist.Song;
import de.lukeslog.trashplay.service.TrashPlayService;
import de.lukeslog.trashplay.support.Logger;

public class MusicPlayer extends Service implements OnPreparedListener, OnCompletionListener, MediaPlayer.OnErrorListener {
    public static final String ACTION_START_MUSIC = "startmusic";
    public static final String ACTION_STOP_MUSIC = "stopmusic";
    public static final String ACTION_NEXT_SONG = "nextSong";
    public static final String ACTION_PREV_SONG = "prevSong";
    public static final String ACTION_PAUSE_SONG = "pauseSong";

    public static final String TAG = TrashPlayConstants.TAG;


    private static Service ctx;
    private static Song currentlyPlayingSong = null;

    private String title = "";
    private String artist = "";

    static MediaPlayer mp;

    String actionID = "";

    public static Song getCurrentlyPlayingSong() {
        return currentlyPlayingSong;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        //Log.d(TAG, "ClockWorkService onStartCommand()");
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mp = null;
        ctx = this;
        registerIntentFilters();
    }

    private void registerIntentFilters() {
        IntentFilter inf = new IntentFilter(ACTION_START_MUSIC);
        IntentFilter inf2 = new IntentFilter(ACTION_STOP_MUSIC);
        IntentFilter inf3 = new IntentFilter(ACTION_NEXT_SONG);
        IntentFilter inf4 = new IntentFilter(ACTION_PREV_SONG);
        IntentFilter inf5 = new IntentFilter(ACTION_PAUSE_SONG);
        registerReceiver(mReceiver, inf);
        registerReceiver(mReceiver, inf2);
        registerReceiver(mReceiver, inf3);
        registerReceiver(mReceiver, inf4);
        registerReceiver(mReceiver, inf5);

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mp = null;
    }

    private void playmp3(Song song) {
        if (song == null) {
            Log.d(TAG, "playmp3 got null");
            TrashPlayService.getContext().toast("Something went wrong.");
            stop();
        } else {
            boolean mExternalStorageAvailable = false;
            String state = Environment.getExternalStorageState();
            Logger.d(TAG, "Go Play 3");
            if (Environment.MEDIA_MOUNTED.equals(state)) {
                // We can read and write the media
                mExternalStorageAvailable = true;
            } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
                // We can only read the media
                mExternalStorageAvailable = true;
            } else {
                // Something else is wrong. It may be one of many other states, but all we need
                //  to know is we can neither read nor write
                mExternalStorageAvailable = false;
            }
            if (mExternalStorageAvailable) {
                if (needToScrobble()) {
                    scrobbleTrack();
                }
                setTrackInfo(song);
                try {
                    playMusic(song);
                } catch (IOException e) {
                    Log.e(TAG, "error1 in MusicPlayer");
                    e.printStackTrace();
                } catch (Exception e) {
                    Log.e(TAG, "error2 in MusicPlayer");
                    e.printStackTrace();
                }
            }
        }
    }

    private boolean needToScrobble() {
        return (!artist.equals("") && !title.equals(""));
    }

    private void setTrackInfo(Song song) {
        try {
            File file = getFileFromSong(song);
            MP3File mp3 = new MP3File(file);
            ID3v1 id3 = mp3.getID3v1Tag();
            artist = id3.getArtist();
            //Log.d(TAG, "----------->ARTIST:" + artist);
            title = id3.getSongTitle();
        } catch (IOException e1) {
            e1.printStackTrace();
        } catch (TagException e1) {
            e1.printStackTrace();
        } catch (Exception ex) {
            //Log.e(TAG, "There has been an exception while extracting ID3 Tag Information from the MP3");
        }
    }

    private void scrobbleTrack() {
        Log.d(TAG, "scrobble....");
        TrashPlayLastFM.scrobble(artist, title);
        if (TrashPlayService.serviceRunning()) {
            Log.d(TAG, "Service Running...");
            PersonalLastFM.scrobble(artist, title, TrashPlayService.getContext().settings);
        }

    }

    private void playMusic(Song song) throws Exception {
        try {
            File file = getFileFromSong(song);
            String musicPath = file.getAbsolutePath();
            mp = new MediaPlayer();
            mp.setDataSource(musicPath);
            mp.setLooping(false);
            //mp.setVolume(0.99f, 0.99f);
            Logger.d(TAG, "...");
            mp.setOnCompletionListener(this);
            mp.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
            Logger.d(TAG, "....");
            mp.setOnPreparedListener(this);
            Logger.d(TAG, ".....");
            mp.prepareAsync();
            currentlyPlayingSong = song;
        } catch (IllegalStateException e) {
            Log.d(TAG, "illegalStateException");
            mp = null;
            playmp3(MusicCollectionManager.getInstance().getNextSong());
        }
    }

    private File getFileFromSong(Song song) throws IOException {
        if (song != null) {
            return new File(StorageManager.LOCAL_STORAGE + song.getFileName());
        }
        throw new IOException();
    }

    public void stop() {
        Log.d(TAG, "stop Media Player Service");
        try {
            mp.stop();
        } catch (Exception e) {

        }
        try {
            mp.release();
        } catch (Exception e) {

        }
        mp = null;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onPrepared(MediaPlayer mpx) {
        Logger.d(TAG, "on Prepared!");
        mpx.setOnCompletionListener(this);
        int duration = mpx.getDuration();
        if ( duration> 0) {
            if(currentlyPlayingSong.getDurationInSeconds()!=duration) {
                currentlyPlayingSong.setDurationInSeconds(duration);
                try {
                    MusicCollectionManager.getInstance().updateRadioFile();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            TrashPlayService.getContext().toast("time dif "+MusicCollectionManager.timeDifInMillis);
            Log.d(TAG, "timedifstuff");
            boolean c1 = MusicCollectionManager.timeDifInMillis>0;
            Log.d(TAG, "a");
            boolean c2 = MusicCollectionManager.timeDifInMillis<duration;
            Log.d(TAG, "b");
            if(c1 && c2) {
                Log.d(TAG, "aye");
                Log.d(TAG, "seek"+(int)MusicCollectionManager.timeDifInMillis);
                mpx.seekTo((int)MusicCollectionManager.timeDifInMillis);
            }
            Log.d(TAG, "continue");
        }
        Logger.d(TAG, "ok, I'v set the on Completion Listener again...");
        mpx.start();
    }

    @Override
    public void onCompletion(MediaPlayer mpx) {
        Logger.d(TAG, "on Completetion!");
        MusicCollectionManager.getInstance().finishedSong();
        stop();
        try {
            playmp3(MusicCollectionManager.getInstance().getNextSong());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        Logger.d(TAG, "MEDIAPLAYER ON ERROR");
        stop();
        try {
            playmp3(MusicCollectionManager.getInstance().getNextSong());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }

    public static void stopeverything() {
        if (mp != null) {
            mp.stop();
            mp.release();
            mp = null;
        }
        if (MusicPlayer.ctx != null) {
            ctx.stopSelf();
        }
    }

    // Create a BroadcastReceiver for ACTION_FOUND
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(ACTION_START_MUSIC)) {
                Logger.d(TAG, "I GOT THE START MUSIC THING!");
                // actionID = intent.getStringExtra("AmbientActionID");
                Song nextSong = null;
                try {
                    nextSong = MusicCollectionManager.getInstance().getNextSong();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                playmp3(nextSong);
            }
            if (action.equals(ACTION_STOP_MUSIC)) {
                Logger.d(TAG, "STOooooooooP");
                //String newactionID = intent.getStringExtra("AmbientActionID");
                stop();
                actionID = "";
            }
            if(action.equals(ACTION_NEXT_SONG)) {
                Logger.d(TAG, "NEXT SONG REQUESTED");
                if(currentlyPlayingSong!=null) {
                    MusicCollectionManager.getInstance().finishedSong();
                }
                stop();
                Song nextSong = null;
                try {
                    nextSong = MusicCollectionManager.getInstance().getNextSong();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                playmp3(nextSong);
            }
            if(action.equals(ACTION_PREV_SONG)) {

            }
        }
    };

    public static String playPosition() {
        String result = "";
        if (mp != null) {
            try {
                int p = mp.getCurrentPosition();
                result = getStringFromIntInSeconds(result, p);
            } catch (Exception e) {
                if(TrashPlayService.serviceRunning()) {
                    TrashPlayService.getContext().toast("Error1");
                }
                e.printStackTrace();
            }
        }
        return result;
    }

    private static String getStringFromIntInSeconds(String result, int p) {
        int m = 0;
        p = p / 1000;
        if (p > 59) {
            m = p / 60;
            if(m>0) {
                p = p - (60 * m);
            }
        }
        if (m > 9) {
            result = result + m;
        } else {
            result = result + "0" + m;
        }
        result = result + ":";
        if (p > 9) {
            result = result + p;
        } else {
            result = result + "0" + p;
        }
        return result;
    }

    public static String playLength() {
        String result = "";
        if (mp != null) {
            try {
                int p = currentlyPlayingSong.getDurationInSeconds();
                result = getStringFromIntInSeconds(result, p);
            } catch (Exception e) {
                if(TrashPlayService.serviceRunning()) {
                    TrashPlayService.getContext().toast("Error2");
                }
                e.printStackTrace();
            }
        }
        return result;
    }
}
