package com.example.mylecteurofmp3;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import java.util.List;

public class MusicService extends Service {
    private MediaPlayer mediaPlayer;
    private final IBinder binder = new MusicBinder();
    private static final String CHANNEL_ID = "MusicChannel";
    private static final int NOTIF_ID = 1;

    // --- NOUVEAU : La playlist et l'index sont gérés par le service ---
    private List<MainActivity.Musique> playlist;
    private int currentIndex = -1;

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
    public IBinder onBind(Intent intent) {
        return binder;
    }

    // --- MÉTHODES DE GESTION DE LA PLAYLIST ET DE L'INDEX ---

    // Permet à l'Activité de transmettre la liste de musiques au Service
    public void setPlaylist(List<MainActivity.Musique> nouvellePlaylist) {
        this.playlist = nouvellePlaylist;
    }

    // Lance la musique en fonction de l'index dans la liste
    public void jouerMusiqueALindex(int index) {
        if (playlist == null || playlist.isEmpty()) return;

        // Gestion des limites (Boucle continue)
        if (index >= playlist.size()) {
            index = 0;
        } else if (index < 0) {
            index = playlist.size() - 1;
        }

        currentIndex = index;
        MainActivity.Musique musique = playlist.get(currentIndex);

        // On appelle la vraie méthode de lecture avec le chemin ET le titre
        jouerMusiqueReelle(musique.cheminFichier, musique.titre);
    }

    // --- LECTURE ET CONTRÔLES ---

    // L'ancienne méthode "jouerMusique" devient privée
    private void jouerMusiqueReelle(String cheminFichier, String titre) {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.reset();
            } else {
                mediaPlayer = new MediaPlayer();
            }

            mediaPlayer.setDataSource(cheminFichier);
            mediaPlayer.prepare();
            mediaPlayer.start();

            // On affiche le VRAI titre de la chanson dans la notification !
            afficherNotification(titre, "Lecteur MP3");

            // NOUVEAU : Le service écoute la fin de la musique et passe à la suivante TOUT SEUL
            mediaPlayer.setOnCompletionListener(mp -> {
                jouerMusiqueALindex(currentIndex + 1);
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Gère le passage à la piste suivante
    public void suivant() {
        jouerMusiqueALindex(currentIndex + 1);
    }

    // Gère le passage à la piste précédente (avec la logique des 3 secondes)
    public void precedent() {
        if (mediaPlayer != null && mediaPlayer.getCurrentPosition() > 3000) {
            mediaPlayer.seekTo(0);
        } else {
            jouerMusiqueALindex(currentIndex - 1);
        }
    }

    public void pause() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
    }

    public void resume() {
        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();
        }
    }

    public boolean isPlaying() {
        if (mediaPlayer != null) {
            return mediaPlayer.isPlaying();
        }
        return false;
    }

    public int getCurrentPosition() {
        if (mediaPlayer != null) {
            return mediaPlayer.getCurrentPosition();
        }
        return 0;
    }

    public void seekTo(int position) {
        if (mediaPlayer != null) {
            mediaPlayer.seekTo(position);
        }
    }

    // --- GESTION DE LA NOTIFICATION ET DU FOREGROUND SERVICE ---

    private void creerCanalNotification() {
        NotificationChannel channel = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            channel = new NotificationChannel(
                    CHANNEL_ID, "Lecteur MP3", NotificationManager.IMPORTANCE_LOW);
        }
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void afficherNotification(String titre, String texte) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(titre)
                .setContentText(texte)
                .setSmallIcon(android.R.drawable.ic_media_play) // Assure-toi que cette icône existe
                .setOngoing(true) // Empêche l'utilisateur de fermer la notification d'un glissement
                .build();

        startForeground(NOTIF_ID, notification);
    }

    @Override
    public void onDestroy() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
        }
        super.onDestroy();
    }
}