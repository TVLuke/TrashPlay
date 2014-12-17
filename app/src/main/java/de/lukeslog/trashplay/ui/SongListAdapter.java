package de.lukeslog.trashplay.ui;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.List;

import de.lukeslog.trashplay.R;
import de.lukeslog.trashplay.player.MusicPlayer;
import de.lukeslog.trashplay.playlist.Song;
import de.lukeslog.trashplay.playlist.SongHelper;
import de.lukeslog.trashplay.support.TrashPlayUtils;

public class SongListAdapter  extends BaseAdapter {

    private List<Song> songlist = new ArrayList<Song>();
    Context context;
    LayoutInflater inflater;

    protected SongListAdapter(Activity ctx, List<Song> songlist) {
        this.context = ctx;
        this.songlist = songlist;
    }


    @Override
    public int getCount() {
        return songlist.size();
    }

    @Override
    public Object getItem(int i) {
        return songlist.get(i);
    }

    @Override
    public long getItemId(int i) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View itemView = inflater.inflate(R.layout.songlistrow, parent, false);
        try {
                setSongInfo(position, 0, itemView);
        } catch (Exception e) {

        }
        return itemView;
    }

    private void setSongInfo(int position, int nextSongInfoRow, View itemView) {
        Song song = songlist.get(position);
        TextView songname = (TextView) itemView.findViewById(R.id.songname);
        TextView artistname = (TextView) itemView.findViewById(R.id.artistname);
        TextView nextInfo = (TextView) itemView.findViewById(R.id.nextinfo);
        TextView additional = (TextView) itemView.findViewById(R.id.additional);

        if(position==nextSongInfoRow) {
            nextInfo.setText("Up Next:");
        } else {
            nextInfo.setVisibility(View.GONE);
        }

        songname.setText(song.getSongName());
        artistname.setText(song.getArtist());
        String additionalinfo = "";
        if(song.getDurationInSeconds()>0){
            additionalinfo=additionalinfo+TrashPlayUtils.getStringFromIntInSeconds(song.getDurationInSeconds());
        }
        additionalinfo=additionalinfo+"|"+song.getPlays()+"|";
        if(song.getLastPlayed()>0){
            DateTime now = new DateTime();
            DateTime then = new DateTime(song.getLastPlayed());
            double millissincelastplays = now.getMillis()-then.getMillis();
            double secondssincelastplay = millissincelastplays/1000;
            double minuestsincelastplay = secondssincelastplay/60;
            double hourssincelastplay = minuestsincelastplay/60;
            int h = (int) hourssincelastplay;
            additionalinfo=additionalinfo+h+" hours since last play.";
        }
        additional.setText(additionalinfo);
    }
}
