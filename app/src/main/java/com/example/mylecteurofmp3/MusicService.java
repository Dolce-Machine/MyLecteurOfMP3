package com.example.mylecteurofmp3;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import java.util.Random;
import androidx.core.app.NotificationCompat;

import java.util.List;

public class MusicService extends Service {

    private static final String CHANNEL_ID = "MusicChannel";
    private static final int NOTIF_ID = 1;

    // Actions pour les boutons de la notification
    public static final String ACTION_PREV = "com.example.mylecteurofmp3.PREV";
    public static final String ACTION_PLAY_PAUSE = "com.example.mylecteurofmp3.PLAY_PAUSE";
    public static final String ACTION_NEXT = "com.example.mylecteurofmp3.NEXT";

    private MediaPlayer mediaPlayer;
    private boolean isPrepared = false;
    private final IBinder binder = new MusicBinder();

    private List<MainActivity.Musique> playlist;
    private int currentIndex = -1;
    private boolean isShuffle = false;
    private final Random random = new Random();
    public class MusicBinder extends Binder {
        public MusicService getService() {
            return MusicService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mediaPlayer = new MediaPlayer();
        creerCanalNotification();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_PREV:
                    precedent();
                    break;
                case ACTION_PLAY_PAUSE:
                    if (isPlaying()) {
                        pause();
                    } else {
                        resume();
                    }
                    break;
                case ACTION_NEXT:
                    suivant();
                    break;
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    // --- GESTION PLAYLIST ---

    public void setPlaylist(List<MainActivity.Musique> nouvellePlaylist) {
        this.playlist = nouvellePlaylist;
    }

    public void jouerMusiqueALindex(int index) {
        if (playlist == null || playlist.isEmpty()) return;

        if (index >= playlist.size()) {
            index = 0;
        } else if (index < 0) {
            index = playlist.size() - 1;
        }

        currentIndex = index;
        MainActivity.Musique musique = playlist.get(currentIndex);
        jouerMusiqueReelle(musique.cheminFichier, musique.titre);
    }

    private void jouerMusiqueReelle(String cheminFichier, String titre) {
        try {
            isPrepared = false;
            if (mediaPlayer != null) {
                mediaPlayer.reset();
            } else {
                mediaPlayer = new MediaPlayer();
            }

            mediaPlayer.setDataSource(cheminFichier);
            mediaPlayer.prepare();
            isPrepared = true;
            mediaPlayer.start();

            afficherNotification(titre, true);

            mediaPlayer.setOnCompletionListener(mp -> jouerMusiqueALindex(getProchainIndex()));

        } catch (Exception e) {
            isPrepared = false;
            e.printStackTrace();
        }
    }

    // --- CONTRÔLES ---
    public void arreterSiActuelle(String cheminFichier) {
        MainActivity.Musique actuelle = getMusiqueActuelle();
        if (actuelle != null && actuelle.cheminFichier.equals(cheminFichier)) {
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.reset();
            }
            isPrepared = false;
            currentIndex = -1;
            stopForeground(STOP_FOREGROUND_REMOVE);
        }
    }
    public void suivant() {
        jouerMusiqueALindex(getProchainIndex());
    }

    public void precedent() {
        if (isPrepared && mediaPlayer != null && mediaPlayer.getCurrentPosition() > 3000) {
            mediaPlayer.seekTo(0);
        } else {
            jouerMusiqueALindex(currentIndex - 1);
        }
    }

    public void pause() {
        if (isPrepared && mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            MainActivity.Musique actuelle = getMusiqueActuelle();
            if (actuelle != null) {
                afficherNotification(actuelle.titre, false);
            }
        }
    }

    public void resume() {
        if (isPrepared && mediaPlayer != null && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();
            MainActivity.Musique actuelle = getMusiqueActuelle();
            if (actuelle != null) {
                afficherNotification(actuelle.titre, true);
            }
        }
    }

    public boolean isPlaying() {
        return isPrepared && mediaPlayer != null && mediaPlayer.isPlaying();
    }

    public MainActivity.Musique getMusiqueActuelle() {
        if (playlist != null && currentIndex >= 0 && currentIndex < playlist.size()) {
            return playlist.get(currentIndex);
        }
        return null;
    }

    public int getDuration() {
        if (isPrepared && mediaPlayer != null) {
            return mediaPlayer.getDuration();
        }
        return 0;
    }

    public int getCurrentPosition() {
        if (isPrepared && mediaPlayer != null) {
            return mediaPlayer.getCurrentPosition();
        }
        return 0;
    }

    public void seekTo(int position) {
        if (isPrepared && mediaPlayer != null) {
            mediaPlayer.seekTo(position);
        }
    }

    // --- NOTIFICATION & FOREGROUND SERVICE ---

    private void creerCanalNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Lecteur MP3", NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private PendingIntent creerActionIntent(String action, int requestCode) {
        Intent intent = new Intent(this, MusicService.class);
        intent.setAction(action);
        return PendingIntent.getService(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private void afficherNotification(String titre, boolean enLecture) {
        Intent openAppIntent = new Intent(this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        int playPauseIcon = enLecture
                ? android.R.drawable.ic_media_pause
                : android.R.drawable.ic_media_play;
        String playPauseLabel = enLecture ? "Pause" : "Lecture";

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(titre)
                .setContentText(enLecture ? "En lecture" : "En pause")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(contentIntent)
                .setOngoing(enLecture)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(android.R.drawable.ic_media_previous, "Précédent", creerActionIntent(ACTION_PREV, 1))
                .addAction(playPauseIcon, playPauseLabel, creerActionIntent(ACTION_PLAY_PAUSE, 2))
                .addAction(android.R.drawable.ic_media_next, "Suivant", creerActionIntent(ACTION_NEXT, 3))
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIF_ID, notification);
        }
    }
    public boolean isShuffle() {
        return isShuffle;
    }

    public void toggleShuffle() {
        isShuffle = !isShuffle;
    }

    private int getProchainIndex() {
        if (playlist == null || playlist.isEmpty()) return 0;

        if (isShuffle && playlist.size() > 1) {
            int nouvelIndex = currentIndex;
            while (nouvelIndex == currentIndex) {
                nouvelIndex = random.nextInt(playlist.size());
            }
            return nouvelIndex;
        }
        return currentIndex + 1;
    }

    @Override
    public void onDestroy() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }
        super.onDestroy();
    }
}